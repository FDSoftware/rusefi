package com.rusefi.io.commands;

import com.devexperts.logging.Logging;
import com.rusefi.binaryprotocol.BinaryProtocol;
import com.rusefi.config.generated.Integration;

import static com.rusefi.binaryprotocol.IoHelper.checkResponseCode;
import static com.rusefi.config.generated.Integration.TS_RESPONSE_BURN_OK;

public class BurnCommand {
    private static final Logging log = Logging.getLogging(BurnCommand.class);
    private static final byte[] PAGE_0 = new byte[2];

    public static boolean execute(BinaryProtocol bp) {
        return executePage(bp, 0);
    }

    /**
     * Burn a specific page.  pageId is the firmware TS_PAGE_* constant
     * (0x0000 main, 0x0100 scatter, ..., 0x0400 lua), encoded little-endian
     * to match firmware's data16[0] read.
     */
    public static boolean executePage(BinaryProtocol bp, int pageId) {
        byte[] pageBytes;
        if (bp.isSinglePageController()) {
            pageBytes = null;
        } else if (pageId == 0) {
            pageBytes = PAGE_0;
        } else {
            // Encode pageId little-endian so firmware's data16[0] matches the value:
            // e.g. pageId=0x0400 -> bytes [0x00, 0x04] -> firmware sees 0x0400.
            pageBytes = new byte[]{(byte) (pageId & 0xff), (byte) ((pageId >> 8) & 0xff)};
        }
        byte[] response = bp.executeCommand(Integration.TS_BURN_COMMAND, pageBytes, "burn page=0x" + Integer.toHexString(pageId));
        boolean isExpectedBurnResponseCode = checkResponseCode(response, (byte) TS_RESPONSE_BURN_OK);
        if (response == null) {
            log.warn("empty BURN response on " + bp.getStream());
            return false;
        }
        boolean isExpectedBurnResponseLength = response.length == 1;
        return isExpectedBurnResponseCode && isExpectedBurnResponseLength;
    }
}
