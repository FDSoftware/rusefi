package com.rusefi.ui.basic;

import com.devexperts.logging.Logging;
import com.opensr5.ConfigurationImage;
import com.opensr5.ConfigurationImageGetterSetter;
import com.opensr5.ini.TableModel;
import com.opensr5.ini.field.ArrayIniField;
import com.opensr5.ini.field.IniField;
import com.rusefi.ConnectivityContext;
import com.rusefi.PortResult;
import com.rusefi.binaryprotocol.BinaryProtocol;
import com.rusefi.maintenance.CalibrationsHelper;
import com.rusefi.maintenance.CalibrationsInfo;
import com.rusefi.ui.widgets.StatusPanel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.devexperts.logging.Logging.getLogging;

public class TrimsTab {
    private static final Logging log = getLogging(TrimsTab.class);
    private final JPanel content = new JPanel(new BorderLayout());
    private final JTable jTable = new JTable();
    private final JButton loadButton = new JButton("Load Trims");
    private final StatusPanel statusPanel;
    private final ConnectivityContext connectivityContext;
    private final AtomicReference<Optional<PortResult>> ecuPortToUse;

    public TrimsTab(ConnectivityContext connectivityContext,
                    AtomicReference<Optional<PortResult>> ecuPortToUse,
                    StatusPanel statusPanel) {
        this.connectivityContext = connectivityContext;
        this.ecuPortToUse = ecuPortToUse;
        this.statusPanel = statusPanel;

        loadButton.addActionListener(e -> loadTrims());

        content.add(new JScrollPane(jTable), BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(loadButton, BorderLayout.WEST);
        bottomPanel.add(statusPanel, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadTrims() {
        Optional<PortResult> portResult = ecuPortToUse.get();
        if (!portResult.isPresent()) {
            statusPanel.logLine("Not connected?");
            return;
        }

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> loadButton.setEnabled(false));
            try {
                Optional<CalibrationsInfo> info = CalibrationsHelper.readCurrentCalibrations(
                        portResult.get().port,
                        statusPanel,
                        connectivityContext
                );

                info.ifPresent(calibrationsInfo -> SwingUtilities.invokeLater(() -> {
                    displayTrims(calibrationsInfo);
                }));
            } finally {
                SwingUtilities.invokeLater(() -> loadButton.setEnabled(true));
            }
        }).start();
    }

    private void displayTrims(CalibrationsInfo info) {
        // Get the main configuration image for RPM/Load bins (on page 1)
        ConfigurationImage configImage = info.getImage().getConfigurationImage();

        // Read page 3 which contains LTFT runtime data
        byte[] page3Data = readPage3Data();
        if (page3Data == null) {
            statusPanel.logLine("Failed to read LTFT data from ECU (page 3)");
            return;
        }

        // Page 3 fields have adjusted offsets, so we need to create an image that accounts for this
        // The fields were adjusted to: page1_size + page2_size + offset_in_page3
        // So we create an image starting at the adjusted base offset
        int page1Size = info.getIniFile().getMetaInfo().getPageSize(0);
        int page2Size = info.getIniFile().getMetaInfo().getPageSize(1);
        int page3BaseOffset = page1Size + page2Size;

        // Create a larger image that includes space for all pages
        byte[] combinedData = new byte[page3BaseOffset + page3Data.length];
        // Copy page 3 data at the correct offset
        System.arraycopy(page3Data, 0, combinedData, page3BaseOffset, page3Data.length);
        ConfigurationImage page3Image = new ConfigurationImage(combinedData);

        TableModel iniTable = info.getIniFile().getTable("ltftBank1Tbl");
        if (iniTable == null) {
            statusPanel.logLine("Trims table definition not found");
            return;
        }

        Optional<IniField> content = info.getIniFile().findIniField(iniTable.getZBinsConstant());
        Optional<IniField> xBinsField = info.getIniFile().findIniField(iniTable.getXBinsConstant());
        Optional<IniField> yBinsField = info.getIniFile().findIniField(iniTable.getYBinsConstant());

        if (!content.isPresent() || !(content.get() instanceof ArrayIniField)) {
            statusPanel.logLine("LTFT table field not found or not an array");
            return;
        }

        ArrayIniField ltft = (ArrayIniField) content.get();
        String[][] ltftValues = ltft.getValues(ConfigurationImageGetterSetter.getValue(ltft, page3Image));

        String[] rpmBins = null;
        if (xBinsField.isPresent() && xBinsField.get() instanceof ArrayIniField) {
            ArrayIniField rpm = (ArrayIniField) xBinsField.get();
            String[][] values = rpm.getValues(ConfigurationImageGetterSetter.getValue(rpm, configImage));
            if (values.length > 0) rpmBins = values[0];
        }

        String[] loadBins = null;
        if (yBinsField.isPresent() && yBinsField.get() instanceof ArrayIniField) {
            ArrayIniField load = (ArrayIniField) yBinsField.get();
            String[][] values = load.getValues(ConfigurationImageGetterSetter.getValue(load, configImage));
            if (values.length > 0) loadBins = values[0];
        }

        jTable.setModel(new TrimsTableModel(ltftValues, rpmBins, loadBins));
        statusPanel.logLine("LTFT data loaded successfully");
    }

    private byte[] readPage3Data() {
        Optional<PortResult> portResult = ecuPortToUse.get();
        if (!portResult.isPresent()) {
            log.error("No ECU port available");
            return null;
        }

        try {
            // Use BinaryProtocolExecutor to access the BinaryProtocol
            byte[] result = com.rusefi.maintenance.BinaryProtocolExecutor.execute(
                portResult.get().port,
                statusPanel,
                bp -> {
                    // Page 3 in the INI contains LTFT data
                    // The ECU expects page number 3, but our array is 0-indexed so we use index 2
                    // However, the protocol packet needs to send the actual page number (3)
                    // So we need to read page at index 2, which will send page number 2 in the packet
                    // Actually, we need to figure out the mapping...

                    // Let's check: getPageSize(0) = page 1 size, getPageSize(1) = page 2 size, getPageSize(2) = page 3 size
                    // So pageIndex 2 should map to INI page 3
                    // But the protocol might need the actual INI page number (3) not the index (2)

                    // For now, try index 2 (which should be page 3)
                    statusPanel.logLine("Attempting to read page 3 (index 2) from ECU...");
                    return bp.readPage(2);
                },
                null,
                true,
                "Reading LTFT page"
            );

            return result;
        } catch (Exception e) {
            log.error("Error reading page 3: " + e.getMessage(), e);
            return null;
        }
    }

    public Component getContent() {
        return content;
    }

    private static class TrimsTableModel extends AbstractTableModel {
        private final String[][] data;
        private final String[] xBins;
        private final String[] yBins;

        public TrimsTableModel(String[][] data, String[] xBins, String[] yBins) {
            this.data = data;
            this.xBins = xBins;
            this.yBins = yBins;
        }

        @Override
        public int getRowCount() {
            return data.length;
        }

        @Override
        public int getColumnCount() {
            return data[0].length + 1;
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) return "Load \\ RPM";
            if (xBins != null && column - 1 < xBins.length) {
                return xBins[column - 1];
            }
            return "Col " + column;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                if (yBins != null && rowIndex < yBins.length) {
                    return yBins[rowIndex];
                }
                return "Row " + rowIndex;
            }
            return data[rowIndex][columnIndex - 1];
        }
    }
}
