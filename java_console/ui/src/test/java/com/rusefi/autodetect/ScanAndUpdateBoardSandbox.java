package com.rusefi.autodetect;

import com.fazecast.jSerialComm.SerialPort;
import com.rusefi.UiProperties;
import com.rusefi.autoupdate.Autoupdate;
import com.rusefi.core.FileUtil;
import com.rusefi.core.RusEfiSignature;
import com.rusefi.core.SignatureHelper;
import com.rusefi.core.io.BoardCompatibility;
import com.rusefi.core.io.BundleInfo;
import com.rusefi.core.io.BundleInfoStrategy;
import com.rusefi.core.io.ConnectedEcuTarget;
import com.rusefi.core.net.ConnectionAndMeta;
import com.rusefi.core.net.PropertiesHolder;
import com.rusefi.io.IoStream;
import com.rusefi.io.UpdateOperationCallbacks;
import com.rusefi.io.serial.BufferedSerialIoStream;
import com.rusefi.maintenance.ProgramSelector;
import com.rusefi.updater.OpenbltDetectorStrategy;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manual sandbox: full "scan and update" cycle against a real ECU.
 * <p>
 * Scans serial ports for a rusEFI ECU, extracts the board name from its signature, and if that
 * board is on the {@code board_compatibility} allowlist (shared_io.properties) downloads the
 * matching bundle from the build server, unzips the firmware .srec, reboots the ECU into the
 * OpenBLT bootloader and flashes the fresh firmware over serial.
 *
 * see PortDetectorSandbox
 * see AutoupdateSandbox
 * see BltSwitchSandbox
 */
public class ScanAndUpdateBoardSandbox {
    private static final int OPENBLT_PORT_WAIT_SECONDS = 90;

    public static void main(String[] args) throws IOException, InterruptedException {
        // the sandbox does its own compatibility gate and stages its own .srec, so skip the
        // universal-bundle logic inside sendBootloaderRebootCommand (it would download a second
        // bundle and unpack firmware into ".." before rebooting), same as BltSwitchSandbox
        ConnectionAndMeta.getProperties().setProperty(UiProperties.SKIP_ECU_TYPE_DETECTION, "true");

        SerialAutoChecker.AutoDetectResult autoDetectResult = PortDetector.autoDetectSerial(null);
        String ecuPort = autoDetectResult.getSerialPort();
        if (ecuPort == null) {
            System.out.println("No ECU detected on any serial port");
            System.exit(-1);
        }
        System.out.println("Port detected " + ecuPort + ", " + autoDetectResult.getSignature());

        RusEfiSignature signature = SignatureHelper.parse(autoDetectResult.getSignature());
        if (signature == null) {
            System.out.println("Failed to parse signature [" + autoDetectResult.getSignature() + "]");
            System.exit(-1);
        }
        String boardName = signature.getBundleTarget();
        System.out.println("Board name " + boardName);

        if (!BoardCompatibility.matchesCompatibility(boardName)) {
            System.out.println("Board [" + boardName + "] is not on the board_compatibility list ["
                + BoardCompatibility.getBoardCompatibility() + "], not updating");
            return;
        }

        BundleInfo bundleInfo = signature.asBundleInfo();
        // same branch-aware URL as ensureFirmwareForTarget, without the interactive branch dialog
        String baseUrl = BundleInfoStrategy.getDownloadUrl(bundleInfo, PropertiesHolder.getBaseUrl(), BundleInfo::getBranchName);
        System.out.println("Downloading bundle for " + bundleInfo.getTarget() + " from " + baseUrl);
        String zipFileName = Autoupdate.downloadZipForTarget(bundleInfo, baseUrl,
            percentage -> {}, System.out::println);
        if (zipFileName == null) {
            System.out.println("Bundle for " + bundleInfo.getTarget() + " is not available on the build server");
            System.exit(-1);
        }
        System.out.println("Bundle at " + zipFileName);

        String srecFile = unzipSrec(zipFileName);
        System.out.println("Firmware image " + srecFile);

        System.out.println("Rebooting ECU on " + ecuPort + " to OpenBLT...");
        ProgramSelector.rebootToOpenblt(null, ecuPort, UpdateOperationCallbacks.LOGGER);

        String openBltPort = waitForOpenBltPort();
        if (openBltPort == null) {
            System.out.println("No OpenBLT port appeared within " + OPENBLT_PORT_WAIT_SECONDS + " seconds");
            System.exit(-1);
        }
        System.out.println("OpenBLT bootloader on " + openBltPort + ", flashing...");

        ConnectedEcuTarget connectedEcuTarget = new ConnectedEcuTarget();
        connectedEcuTarget.set(boardName);
        boolean success = ProgramSelector.flashOpenbltSerial(null, openBltPort,
            UpdateOperationCallbacks.LOGGER, connectedEcuTarget, srecFile);
        System.out.println(success ? "Firmware update SUCCESS" : "Firmware update FAILED");
        System.exit(success ? 0 : -1);
    }

    private static String unzipSrec(String zipFileName) throws IOException {
        File outDir = new File("temp");
        // FileUtil.unzip does not create the destination directory (production callers unzip into existing ones)
        outDir.mkdirs();
        AtomicReference<String> srecName = new AtomicReference<>();
        FileUtil.unzip(zipFileName, outDir, zipEntry -> {
            boolean isSrec = zipEntry.getName().endsWith(".srec");
            if (isSrec) {
                srecName.set(zipEntry.getName());
            }
            return isSrec;
        });
        if (srecName.get() == null) {
            System.out.println("No .srec found in " + zipFileName);
            System.exit(-1);
        }
        return new File(outDir, srecName.get()).getAbsolutePath();
    }

    /**
     * Same direct-probe approach as ProgramSelector.waitForNewOpenBltPortAppeared: scan all system
     * serial ports and XCP-probe each one until the bootloader answers, since OpenBLT may enumerate
     * on a different port than the original ECU port.
     */
    private static String waitForOpenBltPort() throws InterruptedException {
        long start = System.currentTimeMillis();
        long deadline = start + OPENBLT_PORT_WAIT_SECONDS * 1_000L;
        long lastStatus = 0;
        while (System.currentTimeMillis() < deadline) {
            for (SerialPort sp : SerialPort.getCommPorts()) {
                String portName = sp.getSystemPortName();
                long now = System.currentTimeMillis();
                if (now - lastStatus >= 3_000) {
                    System.out.println("Waiting for OpenBLT port, " + (now - start) / 1_000
                        + "s elapsed, probing " + portName + "...");
                    lastStatus = now;
                }
                try (IoStream stream = BufferedSerialIoStream.openPort(portName)) {
                    if (OpenbltDetectorStrategy.isPortOpenblt(stream)) {
                        return portName;
                    }
                } catch (Exception e) {
                    // port busy or device still transitioning - keep polling
                }
            }
            Thread.sleep(1000);
        }
        return null;
    }
}
