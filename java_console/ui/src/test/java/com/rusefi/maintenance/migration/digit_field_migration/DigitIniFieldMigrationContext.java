package com.rusefi.maintenance.migration.digit_field_migration;

import com.rusefi.maintenance.TestTuneMigrationContext;

import javax.xml.bind.JAXBException;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DigitIniFieldMigrationContext {
    public static final String VEHICLE_NAME_FIELD_NAME = "ppsExpAverageAlpha";


    public static TestTuneMigrationContext load() throws JAXBException {
        final TestTuneMigrationContext result = TestTuneMigrationContext.load(
            "src/test/java/com/rusefi/maintenance/migration/digit_field_migration/test_data"
        );
        /*
        assertEquals(
            PREV_IGNITION_TABLE_VALUE,
            result.getPrevValue(IGNITION_TABLE_FIELD_NAME).getValue()
        );
        assertEquals(
            UPDATED_IGNITION_TABLE_VALUE,
            result.getUpdatedValue(IGNITION_TABLE_FIELD_NAME).getValue()
        );*/
        return result;
    }
}
