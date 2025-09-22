package com.rusefi.maintenance.migration.digit_field_migration;

import com.rusefi.maintenance.TestTuneMigrationContext;
import com.rusefi.maintenance.migration.ComposedTuneMigrator;
import com.rusefi.maintenance.migration.digit_field_migration.DigitIniFieldMigrationContext;
import com.rusefi.tune.xml.Constant;
import org.junit.jupiter.api.Test;

import javax.xml.bind.JAXBException;
import java.util.Map;

import static javax.management.ObjectName.quote;
import static org.junit.jupiter.api.Assertions.*;

public class DigitIniFieldMigratorTest {

    @Test
    void checkDigitMigrations() throws JAXBException {
        final TestTuneMigrationContext testContext = DigitIniFieldMigrationContext.load();
        ComposedTuneMigrator.INSTANCE.migrateTune(testContext);
        final Map<String, Constant> migratedConstants = testContext.getMigratedConstants();

        // replicate this code block for all the migrated fields on DigitIniFieldMigrator
        {
            final Constant migratedPpsExpAverageName = migratedConstants.get("ppsExpAverageAlpha");
            assertNotNull(migratedPpsExpAverageName);
            assertEquals(quote("100.0"), migratedPpsExpAverageName.getValue());
        }
    }
}
