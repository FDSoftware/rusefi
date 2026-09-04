package com.rusefi.io.can;

import com.devexperts.logging.Logging;
import com.opensr5.io.DataListener;
import com.rusefi.binaryprotocol.IncomingDataBuffer;
import com.rusefi.config.generated.VariableRegistryValues;
import com.rusefi.io.can.isotp.DefaultFlowControl;
import com.rusefi.util.HexBinary;
import com.rusefi.io.can.isotp.IsoTpCanDecoder;
import com.rusefi.io.can.isotp.IsoTpConnector;
import com.rusefi.io.serial.AbstractIoStream;
import com.rusefi.io.serial.RateCounter;
import com.rusefi.io.tcp.BinaryProtocolServer;
import com.rusefi.ui.StatusConsumer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.devexperts.logging.Logging.getLogging;
import static com.rusefi.config.generated.VariableRegistryValues.CAN_ECU_SERIAL_TX_ID;

public class PCanIoStream extends AbstractIoStream {
    private static final int INFO_SKIP_RATE = 3-00;
    static Logging log = getLogging(PCanIoStream.class);

    private final IncomingDataBuffer dataBuffer;
    private final RawCanPort can;
    private final StatusConsumer statusListener;

    private final RateCounter totalCounter = new RateCounter();
    private final RateCounter isoTpCounter = new RateCounter();
    private final IsoTpCanDecoder canDecoder = new IsoTpCanDecoder() {
        @Override
        protected void onTpFirstFrame() {
            sendCanPacket(DefaultFlowControl.FLOW_CONTROL);
        }
    };

    private final IsoTpConnector isoTpConnector = new IsoTpConnector(VariableRegistryValues.CAN_ECU_SERIAL_RX_ID) {
        @Override
        public void sendCanData(byte[] total) {
            sendCanPacket(total);
        }
    };
    private int logSkipRate;

    @Nullable
    public static PCanIoStream createStream() {
        return createStream((message) -> log.info(message));
    }

    public static PCanIoStream createStream(StatusConsumer statusListener) {
        return createStream(statusListener, new PCanRawPort());
    }

    static PCanIoStream createStream(StatusConsumer statusListener, RawCanPort can) {
        try {
            can.open(new CanAddress(CAN_ECU_SERIAL_TX_ID, false));
        } catch (IOException e) {
            statusListener.logLine("Error initializing PCAN: " + e.getMessage());
            return null;
        }
        statusListener.logLine("Creating PCAN stream...");
        return new PCanIoStream(can, statusListener);
    }

    private void sendCanPacket(byte[] payLoad) {
        if (log.debugEnabled()) {
            log.debug("-------sendIsoTp " + payLoad.length + " byte(s):");
        }

        if (log.debugEnabled()) {
            log.debug("Sending " + HexBinary.printHexBinary(payLoad));
        }

        try {
            can.send(new ClassicCanFrame(new CanAddress(isoTpConnector.canId(), false), payLoad));
        } catch (IOException e) {
            statusListener.logLine("Unable to write the CAN message: " + e.getMessage());
            throw new UncheckedIOException(e);
        }
//        log.info("Send OK! length=" + payLoad.length);
    }

    private PCanIoStream(RawCanPort can, StatusConsumer statusListener) {
        this.can = can;
        this.statusListener = statusListener;
        dataBuffer = createDataBuffer();
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        try {
            IsoTpConnector.sendStrategy(bytes, isoTpConnector);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public void setInputListener(DataListener listener) {
        Executor threadExecutor = Executors.newSingleThreadExecutor(BinaryProtocolServer.getThreadFactory("PCAN reader"));
        threadExecutor.execute(() -> {
            while (!isClosed()) {
                try {
                    readOnePacket(listener);
                } catch (IOException e) {
                    if (!isClosed()) {
                        statusListener.logLine("Unable to read the CAN message: " + e.getMessage());
                        log.error("PCAN read failed", e);
                        close();
                    }
                }
            }
        });
    }

    private void readOnePacket(DataListener listener) throws IOException {
        Optional<ClassicCanFrame> received = can.receive(100);
        if (received.isPresent()) {
            ClassicCanFrame frame = received.get();
            totalCounter.add();
            if (frame.getAddress().isExtended() || frame.getAddress().getId() != CAN_ECU_SERIAL_TX_ID) {
//                if (log.debugEnabled())
                logSkipRate ++;
                if (logSkipRate % INFO_SKIP_RATE == 0) {
                    log.info("Skipping non " + String.format("%X", CAN_ECU_SERIAL_TX_ID)
                        + " packet: " + frame.getAddress());
                    log.info("Total rate " + totalCounter.getCurrentRate() + ", isotp rate " + isoTpCounter.getCurrentRate());
                }
                return;
            }
            isoTpCounter.add();
            final byte[] decode;
            try {
                decode = canDecoder.decodePacket(frame.getPayload());
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            listener.onDataArrived(decode);
        }
    }

    @Override
    public synchronized void close() {
        if (isClosed()) {
            return;
        }
        super.close();
        try {
            can.close();
        } catch (IOException e) {
            statusListener.logLine("Unable to close PCAN: " + e.getMessage());
            log.error("PCAN close failed", e);
        }
    }

    @Override
    public IncomingDataBuffer getDataBuffer() {
        return dataBuffer;
    }

    @Override
    public String toString() {
        return "PCanIoStream{" +
            "totalCounter=" + totalCounter +
            '}';
    }
}
