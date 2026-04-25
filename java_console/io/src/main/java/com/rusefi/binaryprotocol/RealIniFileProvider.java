package com.rusefi.binaryprotocol;

import com.devexperts.logging.Logging;
import com.opensr5.ini.*;
import com.rusefi.ini.reader.IniFileReader;
import com.rusefi.core.SignatureHelper;
import com.rusefi.ini.reader.IniFileReaderUtil;
import com.rusefi.ini.reader.IniParsingException;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;

import static com.devexperts.logging.Logging.getLogging;

public class RealIniFileProvider implements IniFileProvider {
    private static final Logging log = getLogging(RealIniFileProvider.class);
    @Override
    @NotNull
    public IniFileModel provide(String signature) throws IniNotFoundException {
        /**
         * first we look at {@link SignatureHelper#LOCAL_INI_CACHE_FOLDER}
         * second we attempt downloading
         * third we look via {@link SignatureHelper#EXTRA_INI_SOURCE} environment variable
         */
        String localIniFile = SignatureHelper.downloadIfNotAvailable(SignatureHelper.getUrl(signature));
        if (localIniFile == null) {
            log.info("Failed to download " + signature + " maybe custom board?");
            // 4th option: current folder
            localIniFile = IniLocator.findIniFile(".");
        }
        if (localIniFile == null) {
            // 5th option: one level up or environment variable direction
            localIniFile = IniLocator.findIniFile(IniFileReader.INI_FILE_PATH);
        }
        if (localIniFile == null)
            throw new IniNotFoundException("Failed to locate .ini file in five different places!");
        IniFileModel iniFileModel;
        try {
                iniFileModel = IniFileReaderUtil.readIniFileChecked(localIniFile);
        } catch (IniParsingException e) {
            throw new IniNotFoundException("Parsing error: " + e, e);
        } catch (FileNotFoundException e) {
            throw new IniNotFoundException(e.toString());
        }
        // Refuse a .ini whose signature does not match the firmware's: every field
        // offset is then a lie, and writes to those offsets will land on the wrong
        // memory region — silently corrupting unrelated config (idle VE, fueling,
        // ignition, ...).  This was observed in real life when an old cached ini
        // (e.g. uaefi @ 2026-03-13 with luaScript offset 5764) was loaded against
        // a newer firmware (luaScript offset 5832) — the 68-byte shift overwrote
        // idleVeRpmBins/idleVeLoadBins/idleVeTable.
        // The `signature` parameter is the firmware-reported signature; we only
        // check non-null because some test paths invoke provide(null).
        // The firmware sends the signature as `strlen + 1` bytes — the trailing
        // NUL terminator survives into the Java string.  Other paths normalize
        // (e.g. SignatureHelper.parse uses .trim()), so trim both sides here.
        if (signature != null) {
            String iniSignature = iniFileModel.getMetaInfo().getSignature();
            String firmwareTrimmed = signature.trim();
            if (iniSignature != null && !iniSignature.trim().equals(firmwareTrimmed)) {
                throw new IniNotFoundException(
                    "Refusing to use mismatched .ini.\n" +
                    "  Firmware signature: " + firmwareTrimmed + "\n" +
                    "  .ini signature:     " + iniSignature.trim() + "\n" +
                    "  .ini path:          " + localIniFile + "\n" +
                    "Re-flash matching firmware, or download/install the correct .ini bundle. " +
                    "Writing through this .ini would corrupt config because field offsets disagree."
                );
            }
        }
        PrimeTunerStudioCache.prime(iniFileModel, localIniFile);
        return iniFileModel;
    }
}
