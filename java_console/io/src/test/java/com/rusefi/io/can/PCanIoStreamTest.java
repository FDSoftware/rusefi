package com.rusefi.io.can;

import com.rusefi.config.generated.VariableRegistryValues;
import com.rusefi.ui.StatusConsumer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PCanIoStreamTest {
    @Test
    void createOpensStandardEcuResponseFilterAndCloseReleasesPort() throws Exception {
        BlockingRawCanPort port = new BlockingRawCanPort();

        PCanIoStream stream = PCanIoStream.createStream(StatusConsumer.VOID, port);

        assertEquals(new CanAddress(VariableRegistryValues.CAN_ECU_SERIAL_TX_ID, false), port.openAddress);
        assertTrue(port.receiveStarted.await(1, TimeUnit.SECONDS));
        stream.close();
        stream.close();
        assertEquals(1, port.closeCalls);
    }

    @Test
    void createFailureReturnsNullWithoutStartingReader() {
        BlockingRawCanPort port = new BlockingRawCanPort();
        port.openFailure = new IOException("not available");
        List<String> messages = new ArrayList<>();

        assertNull(PCanIoStream.createStream(messages::add, port));

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("not available"));
        assertEquals(0, port.closeCalls);
    }

    @Test
    void writeUsesStandardIsoTpRequestAndPropagatesFailure() throws Exception {
        BlockingRawCanPort port = new BlockingRawCanPort();
        PCanIoStream stream = PCanIoStream.createStream(StatusConsumer.VOID, port);
        assertTrue(port.receiveStarted.await(1, TimeUnit.SECONDS));

        stream.write(new byte[]{1, 2});

        assertEquals(1, port.sent.size());
        ClassicCanFrame sent = port.sent.get(0);
        assertEquals(new CanAddress(VariableRegistryValues.CAN_ECU_SERIAL_RX_ID, false), sent.getAddress());
        assertArrayEquals(new byte[]{2, 1, 2}, sent.getPayload());

        port.sendFailure = new IOException("queue full");
        IOException failure = assertThrows(IOException.class, () -> stream.write(new byte[]{3}));
        assertEquals("queue full", failure.getMessage());
        stream.close();
    }

    private static class BlockingRawCanPort implements RawCanPort {
        final CountDownLatch receiveStarted = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final List<ClassicCanFrame> sent = new ArrayList<>();
        CanAddress openAddress;
        IOException openFailure;
        IOException sendFailure;
        int closeCalls;

        @Override
        public void open(CanAddress receiveAddress) throws IOException {
            if (openFailure != null) {
                throw openFailure;
            }
            openAddress = receiveAddress;
        }

        @Override
        public void send(ClassicCanFrame frame) throws IOException {
            sent.add(frame);
            if (sendFailure != null) {
                throw sendFailure;
            }
        }

        @Override
        public Optional<ClassicCanFrame> receive(int timeoutMs) throws IOException {
            receiveStarted.countDown();
            try {
                closed.await();
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        @Override
        public void close() {
            closeCalls++;
            closed.countDown();
        }
    }
}
