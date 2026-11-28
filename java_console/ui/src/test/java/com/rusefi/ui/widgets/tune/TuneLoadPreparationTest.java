package com.rusefi.ui.widgets.tune;

import com.opensr5.ConfigurationImage;
import com.opensr5.ConfigurationImageGetterSetter;
import com.opensr5.ini.IniFileMetaInfo;
import com.opensr5.ini.IniFileModel;
import com.opensr5.ini.IniFileMetaInfoImpl;
import com.opensr5.ini.RawIniFile;
import com.opensr5.ini.field.ArrayIniField;
import com.opensr5.ini.field.IniField;
import com.opensr5.ini.field.EnumIniField;
import com.opensr5.ini.field.StringIniField;
import com.opensr5.ini.field.ScalarIniField;
import com.opensr5.ini.field.PcVariableArrayIniField;
import com.rusefi.config.FieldType;
import com.rusefi.io.UpdateOperationCallbacks;
import com.rusefi.ini.reader.IniFileReaderUtil;
import com.rusefi.ini.reader.IniParsingException;
import com.rusefi.tune.xml.Constant;
import com.rusefi.tune.xml.Msq;
import com.rusefi.tune.xml.Page;
import com.rusefi.tune.xml.MsqFactory;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TuneLoadPreparationTest {
    @Test
    void offlineOpenHistoricalAfrTune() throws Exception, IniParsingException {
        IniFileModel ini = historicalLambdaIni();
        Msq tune = Msq.readTune(new File(getClass().getResource("/26october.msq").toURI()).getPath());
        assertEquals(ini.getSignature(), tune.getVersionInfo().getSignature());
        assertEquals("\"AFR\"", tune.getConstantsAsMap().get("useLambdaOnInterface").getValue());
        assertEquals("14.7", tune.getConstantsAsMap().get("lambdaTable").getValue().trim().split("\\s+")[0]);

        // #10741: both loaders must preserve AFR values without wrapping or rejecting valid table cells.
        assertEquals(147, Byte.toUnsignedInt(tune.asImage(ini).getContent()[9508]));
        assertEquals(147, Byte.toUnsignedInt(TuneLoadPreparation.openOffline(tune, ini).get(0).getContent()[9508]));
    }

    @Test
    void offlineOpenLegacyTableWithoutUnits() {
        ArrayIniField field = new ArrayIniField("lambdaTable", 0, FieldType.UINT8, 1, 1,
            "lambda", 1.0 / 147, "0.6", "1.5", "2");
        IniFileModel ini = mock(IniFileModel.class);
        when(ini.getSignature()).thenReturn("same");
        IniFileMetaInfo meta = metaForMainPage(1);
        when(ini.getMetaInfo()).thenReturn(meta);
        when(ini.getByteOrder()).thenReturn(ByteOrder.LITTLE_ENDIAN);
        when(ini.findIniField("lambdaTable")).thenReturn(Optional.of(field));
        Msq tune = Msq.create(1, "same");
        tune.findPage().constant.add(new Constant("lambdaTable", null, "1.0", "2", "1", "1"));

        // #10741: omitted units metadata is not an incompatible physical unit.
        assertEquals(147, Byte.toUnsignedInt(TuneLoadPreparation.openOffline(tune, ini).get(0).getContent()[0]));
    }

    @Test
    void consoleGeneratedLambdaValuesRoundTripEvenWithAfrDisplaySelected() throws IniParsingException {
        IniFileModel ini = historicalLambdaIni();
        ConfigurationImage image = new ConfigurationImage(16816, ByteOrder.LITTLE_ENDIAN);
        Arrays.fill(image.getContent(), 9508, 9508 + 256, (byte) 147);
        Msq tune = MsqFactory.valueOf(image, ini);
        assertEquals("\"AFR\"", tune.getConstantsAsMap().get("useLambdaOnInterface").getValue());
        assertEquals("1.0", tune.getConstantsAsMap().get("lambdaTable").getValue().trim().split("\\s+")[0]);
        assertArrayEquals(image.getContent(), TuneLoadPreparation.openOffline(tune, ini).get(0).getContent());
    }

    @Test
    void displayedTableUsesIncomingModeRegardlessOfConstantOrder() throws IniParsingException {
        IniFileModel ini = historicalLambdaIni();
        Msq tune = Msq.create(16816, ini.getSignature());
        Constant table = new Constant("lambdaTable", "afr", String.join(" ", Collections.nCopies(256, "14.7")),
            "1", "16", "16");
        tune.findPage().constant.add(table);
        tune.findPage().constant.add(new Constant("useLambdaOnInterface", null, "\"AFR\"", null));
        ConfigurationImage current = new ConfigurationImage(16816, ByteOrder.LITTLE_ENDIAN);
        current.setBitValue((EnumIniField) ini.findIniField("useLambdaOnInterface").get(), 1);

        assertEquals(147, Byte.toUnsignedInt(tune.asImage(ini).getContent()[9508]));
        assertEquals(147, Byte.toUnsignedInt(tune.applyOnto(current, ini).getContent()[9508]));
        assertEquals(147, Byte.toUnsignedInt(TuneLoadPreparation.openOffline(tune, ini).get(0).getContent()[9508]));
        assertEquals("afr", table.getUnits());
        assertEquals("14.7", table.getValue().split(" ")[0]);
        assertEquals(1.0 / 147, ((ArrayIniField) ini.findIniField("lambdaTable").get()).getMultiplier());

        table.setUnits("lambda");
        assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.openOffline(tune, ini));
        table.setUnits("afr");
        table.setValue(String.join(" ", Collections.nCopies(256, "26")));
        assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.openOffline(tune, ini));
        table.setValue(String.join(" ", Collections.nCopies(256, "NaN")));
        assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.openOffline(tune, ini));
        table.setValue(String.join(" ", Collections.nCopies(256, "14.7")));
        Msq withoutSelector = Msq.create(16816, ini.getSignature());
        withoutSelector.findPage().constant.add(table);
        assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.openOffline(withoutSelector, ini));

        table.setUnits("lambda");
        table.setValue(String.join(" ", Collections.nCopies(256, "1.0")));
        tune.findPage().constant.get(1).setValue("\"Lambda\"");
        assertEquals(147, Byte.toUnsignedInt(TuneLoadPreparation.openOffline(tune, ini).get(0).getContent()[9508]));
    }

    @Test
    void resolvedTableRetainsItsSecondaryPageIdentifier() throws IniParsingException {
        IniFileModel ini = historicalLambdaIni();
        Msq tune = Msq.create(16816, ini.getSignature());
        tune.findPage().constant.add(new Constant("useLambdaOnInterface", null, "\"AFR\"", null));
        ArrayIniField field = (ArrayIniField) ini.findIniField("lambdaTable").get();
        field.setPageIndex(0x0300);
        IniField resolved = tune.resolveSerializationField(ini, field,
            new Constant("lambdaTable", "afr", "14.7", "1", "16", "16"));
        assertEquals(0x0300, resolved.getPageIndex());
        assertEquals(0.1, ((ArrayIniField) resolved).getMultiplier());
        PcVariableArrayIniField pcArray = new PcVariableArrayIniField(field);
        assertSame(pcArray, tune.resolveSerializationField(ini, pcArray,
            new Constant("lambdaTable", "afr", "14.7", "1", "16", "16")));
        ArrayIniField translated = new ArrayIniField(field.getName(), field.getOffset(), field.getType(),
            field.getCols(), field.getRows(), field.getUnits(), field.getMultiplier(), field.getMin(), field.getMax(),
            field.getDigits(), field.getScaleExpression(), 1, null);
        assertThrows(IllegalArgumentException.class, () -> tune.resolveSerializationField(ini, translated,
            new Constant("lambdaTable", "afr", "14.7", "1", "16", "16")));
    }

    @Test
    void tableScaleRejectsInvalidDependenciesAndUsesScalarSerializationOffset() throws IniParsingException {
        IniFileModel ini = readIni("signature = \"same\"\n"
            + "nPages = 1\npageSize = 2\npageReadCommand = \"R\"\nochBlockSize = 1\n"
            + "[Constants]\npage = 1\n"
            + "selector = scalar, U08, 0, \"\", 1, 10, 0, 255, 0\n"
            + "table = array, U08, 1, [1], {selector ? \"a\" : \"b\"}, "
            + "{selector ? 2 : 1}, 0, 0, 255, {selector ? 0 : 1}\n");
        Msq tune = Msq.create(2, "same");
        tune.findPage().constant.add(new Constant("table", "b", "1", "1", "1", "1"));
        Constant selector = new Constant("selector", "", "10", "0");
        tune.findPage().constant.add(selector);
        assertArrayEquals(new byte[]{0, 1}, TuneLoadPreparation.openOffline(tune, ini).get(0).getContent());
        for (String invalid : new String[]{null, "NaN", "Infinity", "bad"}) {
            selector.setValue(invalid);
            assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.openOffline(tune, ini));
        }
    }

    @Test
    void omittedUnitsDoNotDisableValueOrExplicitUnitsValidation() {
        ScalarIniField field = new ScalarIniField("value", 0, "kPa", FieldType.UINT8, 1, "0", 0);
        ConfigurationImage image = new ConfigurationImage(1, ByteOrder.LITTLE_ENDIAN);
        for (String invalid : new String[]{"NaN", "Infinity", "-1", "256"}) {
            assertThrows(IllegalArgumentException.class,
                () -> TuneLoadPreparation.applyTuneConstant(new Constant("value", null, invalid, "0"), field, image));
        }
        assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.applyTuneConstant(
            new Constant("value", "rpm", "1", "0"), field, image));
        ScalarIniField unitless = new ScalarIniField("value", 0, "", FieldType.UINT8, 1, "0", 0);
        assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.applyTuneConstant(
            new Constant("value", "kPa", "1", "0"), unitless, image));
    }

    @Test
    void pcMetadataNeverWritesToTheEcuPageImage() {
        EnumIniField labels = new EnumIniField("unitsLabels", 0, FieldType.UINT8,
            new EnumIniField.EnumKeyValueMap(Map.of(0, "F", 1, "C")), 0, 0);
        IniFileModel ini = mock(IniFileModel.class);
        when(ini.getSignature()).thenReturn("same");
        when(ini.getPcVariableNames()).thenReturn(Set.of("unitsLabels"));
        when(ini.findIniField("unitsLabels")).thenReturn(Optional.of(labels));
        IniFileMetaInfo meta = metaForMainPage(1);
        when(ini.getMetaInfo()).thenReturn(meta);
        when(ini.getByteOrder()).thenReturn(ByteOrder.LITTLE_ENDIAN);
        Msq tune = new Msq();
        tune.getVersionInfo().setSignature("same");
        Page page = new Page(0, 1);
        page.constant.add(new Constant("unitsLabels", null, "\"C\"", null));
        tune.page.add(page);
        assertArrayEquals(new byte[]{0}, TuneLoadPreparation.openOffline(tune, ini).get(0).getContent());
    }

    @Test
    void offlineOpenRejectsInvalidKnownValuesButRetainsLegacyBlankEnumBehavior() {
        EnumIniField mode = new EnumIniField("mode", 0, FieldType.UINT8,
            new EnumIniField.EnumKeyValueMap(Map.of(0, "\"Off\"", 1, "\"On\"")), 0, 0);
        IniFileModel ini = mock(IniFileModel.class);
        when(ini.getSignature()).thenReturn("same");
        when(ini.getAllIniFields()).thenReturn(Collections.singletonMap("mode", mode));
        when(ini.findIniField("mode")).thenReturn(Optional.of(mode));
        IniFileMetaInfo meta = metaForMainPage(1);
        when(ini.getMetaInfo()).thenReturn(meta);
        when(ini.getByteOrder()).thenReturn(ByteOrder.LITTLE_ENDIAN);

        Msq blankEnum = new Msq();
        blankEnum.getVersionInfo().setSignature("same");
        Page blankPage = new Page(0, 1);
        blankPage.constant.add(new Constant("mode", null, "", null));
        blankEnum.page.add(blankPage);
        Map<Integer, ConfigurationImage> opened = TuneLoadPreparation.openOffline(blankEnum, ini);
        assertEquals(0, opened.get(0).getContent()[0]);

        blankPage.constant.get(0).setValue("\"Missing\"");
        assertThrows(IllegalArgumentException.class, () -> TuneLoadPreparation.openOffline(blankEnum, ini));
    }

    @Test
    void migratesSecondaryPageLuaToItsNewSecondaryPage() {
        String script = "function onTick() end";
        IniField oldLua = new StringIniField("luaScript", 4, 32);
        oldLua.setPageIndex(0x0200);
        IniFileModel sourceIni = mock(IniFileModel.class);
        when(sourceIni.getSignature()).thenReturn("old-signature");
        when(sourceIni.getAllIniFields()).thenReturn(Collections.emptyMap());
        when(sourceIni.getSecondaryIniFields()).thenReturn(Collections.singletonMap("luaScript", oldLua));
        IniFileMetaInfo sourceMeta = mock(IniFileMetaInfo.class);
        when(sourceMeta.getnPages()).thenReturn(2);
        when(sourceMeta.getPageIdentifier(0)).thenReturn(0);
        when(sourceMeta.getPageIdentifier(1)).thenReturn(0x0200);
        when(sourceMeta.getPageSize(0)).thenReturn(64);
        when(sourceMeta.getPageSize(1)).thenReturn(32);
        when(sourceMeta.getBurnCommand(1)).thenReturn("B");
        when(sourceIni.getMetaInfo()).thenReturn(sourceMeta);

        StringIniField targetLua = new StringIniField("luaScript", 0, 32);
        targetLua.setPageIndex(0x0400);
        IniFileModel targetIni = mock(IniFileModel.class);
        when(targetIni.getSignature()).thenReturn("new-signature");
        when(targetIni.getAllIniFields()).thenReturn(Collections.emptyMap());
        when(targetIni.getSecondaryIniFields()).thenReturn(Collections.singletonMap("luaScript", targetLua));
        when(targetIni.findIniField(anyString())).thenAnswer(invocation ->
            "luaScript".equalsIgnoreCase(invocation.getArgument(0))
                ? Optional.of(targetLua) : Optional.empty());
        when(targetIni.getByteOrder()).thenReturn(ByteOrder.LITTLE_ENDIAN);
        IniFileMetaInfo meta = metaForTwoPages(64, 32, 0x0400);
        when(meta.getBurnCommand(1)).thenReturn("B");
        when(targetIni.getMetaInfo()).thenReturn(meta);

        Msq oldTune = new Msq();
        oldTune.getVersionInfo().setSignature("old-signature");
        Page oldSecondary = new Page(1, 32);
        oldSecondary.constant.add(new Constant("luaScript", null, script, null));
        oldTune.page.add(oldSecondary);
        ConfigurationImage main = new ConfigurationImage(64, ByteOrder.LITTLE_ENDIAN);
        Arrays.fill(main.getContent(), (byte) 0x5a);
        ConfigurationImage secondary = new ConfigurationImage(32, ByteOrder.LITTLE_ENDIAN);

        TuneLoadPreparation prepared = TuneLoadPreparation.prepare(
            oldTune, sourceIni, targetIni,
            Map.of(0, main, 0x0400, secondary),
            UpdateOperationCallbacks.DUMMY);

        assertArrayEquals(main.getContent(), prepared.incoming.get(0).getContent());
        assertEquals(script, ConfigurationImageGetterSetter.getStringValue(targetLua, prepared.incoming.get(0x0400)));
        assertTrue(prepared.warnings.stream().noneMatch(warning -> warning.contains("luaScript")));

        for (String invalid : new String[]{"non-ASCII \u00e9", "embedded\u0000NUL"}) {
            oldSecondary.constant.get(0).setValue(invalid);
            TuneLoadPreparation rejected = TuneLoadPreparation.prepare(
                oldTune, sourceIni, targetIni,
                Map.of(0, main, 0x0400, secondary),
                UpdateOperationCallbacks.DUMMY);
            assertArrayEquals(secondary.getContent(), rejected.incoming.get(0x0400).getContent());
            assertTrue(rejected.warnings.stream().anyMatch(warning -> warning.contains("NUL-free ASCII")));
        }
        oldSecondary.constant.get(0).setValue(script);
        oldTune.getVersionInfo().setSignature("wrong-signature");
        TuneLoadPreparation wrongSource = TuneLoadPreparation.prepare(oldTune, sourceIni, targetIni,
            Map.of(0, main, 0x0400, secondary), UpdateOperationCallbacks.DUMMY);
        assertArrayEquals(secondary.getContent(), wrongSource.incoming.get(0x0400).getContent());
        assertTrue(wrongSource.warnings.stream().anyMatch(warning -> warning.contains("does not match")));
    }

    private static IniFileModel historicalLambdaIni() throws IniParsingException {
        // Unmodified field definitions from the source INI matching the TunerStudio fixture's signature.
        String text = "signature = \"rusEFI master.2026.09.24.uaefi_pro.22860091\"\n"
            + "nPages = 1\npageSize = 16816\npageReadCommand = \"R\"\nochBlockSize = 1\n"
            + "[Constants]\npage = 1\n"
            + "useLambdaOnInterface = bits, U32, 12, [2:2], \"AFR\", \"Lambda\"\n"
            + "lambdaTable = array, U08, 9508, [16x16], {useLambdaOnInterface ? \"lambda\" : \"afr\"}, "
            + "{ useLambdaOnInterface ? 1/147 : 1/10 }, 0, {useLambdaOnInterface ? 0.6 : 0}, "
            + "{useLambdaOnInterface ? 1.5 : 25}, {useLambdaOnInterface ? 2 : 1}\n";
        return readIni(text);
    }

    private static IniFileModel readIni(String text) throws IniParsingException {
        RawIniFile lines = IniFileReaderUtil.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return IniFileReaderUtil.readIniFile(lines, "historical-lambda.ini", new IniFileMetaInfoImpl(lines));
    }

    private static IniFileMetaInfo metaForMainPage(int pageSize) {
        IniFileMetaInfo meta = mock(IniFileMetaInfo.class);
        when(meta.getnPages()).thenReturn(1);
        when(meta.getPageIdentifier(0)).thenReturn(0);
        when(meta.getPageSize(0)).thenReturn(pageSize);
        return meta;
    }

    private static IniFileMetaInfo metaForTwoPages(int mainSize, int secondarySize, int secondaryId) {
        IniFileMetaInfo meta = mock(IniFileMetaInfo.class);
        when(meta.getnPages()).thenReturn(2);
        when(meta.getPageIdentifier(0)).thenReturn(0);
        when(meta.getPageIdentifier(1)).thenReturn(secondaryId);
        when(meta.getPageSize(0)).thenReturn(mainSize);
        when(meta.getPageSize(1)).thenReturn(secondarySize);
        return meta;
    }
}
