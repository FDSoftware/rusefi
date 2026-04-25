package com.rusefi.binaryprotocol;

import com.rusefi.config.generated.Integration;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;

public class BinaryProtocolTest {
    @Test
    public void test() {
        byte[] packet = BinaryProtocol.smartPacketPrefix2(2000, 1000, false);
        byte[] fullRequest = BinaryProtocol.getFullRequest((byte) Integration.TS_READ_COMMAND, packet);
        assertArrayEquals(new byte[]{
                'R',
                0, 0, // page
                (byte) 0xD0, 0x07, // offset 2000
                (byte) 0xE8, 0x03 // size 1000
        }, fullRequest);
    }

    @Test
    public void testSinglePage() {
        byte[] packet = BinaryProtocol.smartPacketPrefix2(2000, 1000, true);
        byte[] fullRequest = BinaryProtocol.getFullRequest((byte) Integration.TS_READ_COMMAND, packet);
        assertArrayEquals(new byte[]{
                'R',
                (byte) 0xD0, 0x07, // offset 2000
                (byte) 0xE8, 0x03 // size 1000
        }, fullRequest);
    }

    @Test
    public void testCrcCheck() {
        byte[] packet = BinaryProtocol.smartPacketPrefix2(0, 21000, false);
        byte[] fullRequest = BinaryProtocol.getFullRequest((byte) Integration.TS_CRC_CHECK_COMMAND, packet);
        assertArrayEquals(new byte[]{
                'k',
                0, 0, // page
                0, 0, // offset
                (byte) 0x08, 0x52 // size 21000
        }, fullRequest);
    }

    @Test
    public void testBurnCommand() {
        byte[] fullRequest = BinaryProtocol.getFullRequest((byte) Integration.TS_BURN_COMMAND, new byte[]{0, 0});
        assertArrayEquals(new byte[]{
                'B',
                0, 0,
        }, fullRequest);
    }

    @Test
    public void testChunkWrite() {
        int offset = 123;
        byte[] content = new byte[]{0x11, 0x22};
        byte[] header = BinaryProtocol.smartPacketPrefix2(offset, content.length, false);
        byte[] packet = new byte[header.length + content.length];
        System.arraycopy(header, 0, packet, 0, header.length);
        System.arraycopy(content, 0, packet, header.length, content.length);

        byte[] fullRequest = BinaryProtocol.getFullRequest((byte) Integration.TS_CHUNK_WRITE_COMMAND, packet);
        assertArrayEquals(new byte[]{
                'C',
                0, 0, // page
                0x7B, 0, // offset 123
                0x02, 0, // size 2
                0x11, 0x22 // content
        }, fullRequest);
    }

    /**
     * Wire-format regression test for non-zero pages.  pageId must be encoded little-endian
     * (high byte at packet[0] is 0) so that the firmware's `data16[0]` LE read returns the
     * value that matches its `case TS_PAGE_*:` constants (0x0400 for the lua page).
     * If this test fails, lua-script writes from java_console will go to the wrong page on
     * the firmware and either get rejected or land on page 0 — corrupting main config.
     */
    @Test
    public void testNonZeroPageEncoding() {
        // Lua page: TS_PAGE_LUA_SCRIPT = 0x0400.  Firmware reads page-bytes LE.
        byte[] packet = BinaryProtocol.smartPacketPrefix2(0x0400, 0, 8000, false);
        assertArrayEquals(new byte[]{
                0x00, 0x04, // page id 0x0400 little-endian
                0x00, 0x00, // offset 0
                0x40, 0x1F, // size 8000 = 0x1F40 little-endian
        }, packet);
    }

    /**
     * Wire-format regression for the secondTables page (already used by TS itself).
     */
    @Test
    public void testSecondTablesPageEncoding() {
        byte[] packet = BinaryProtocol.smartPacketPrefix2(0x0300, 0, 1268, false);
        assertArrayEquals(new byte[]{
                0x00, 0x03, // page id 0x0300 little-endian
                0x00, 0x00, // offset 0
                (byte) 0xF4, 0x04, // size 1268 = 0x04F4 little-endian
        }, packet);
    }
}
