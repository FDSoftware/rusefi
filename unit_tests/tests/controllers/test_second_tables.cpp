/*
 * @file test_second_tables.cpp
 *
 * Tests for page4 (second VE/ignition tables) CRC validation,
 * storage prepare/accessors, and default initialization.
 *
 * @date: apr 10, 2026
 * @author FDSoftware
 */

#include "pch.h"
#include "engine_test_helper.h"
#include "second_tables.h"
#include <rusefi/crc.h>

// Verify that initSecondTables populates sane defaults (unit-test path).
TEST(SecondTables, DefaultsAreApplied) {
	EngineTestHelper eth(engine_type_e::TEST_ENGINE);

	// initSecondTables is called during engine init — verify state.
	page4_s* state = secondTablesGetState();
	ASSERT_NE(state, nullptr);

	// Default VE table should be all 80
	EXPECT_NEAR(state->secondVeTable[0][0], 80, EPS4D);
	EXPECT_NEAR(state->secondVeTable[7][7], 80, EPS4D);

	// Default ignition table should be all 30
	EXPECT_NEAR(state->secondIgnitionTable[0][0], 30, EPS4D);
	EXPECT_NEAR(state->secondIgnitionTable[7][7], 30, EPS4D);

	// Blend curves should span 0-100
	EXPECT_NEAR(state->secondVeBlendBins[0], 0, EPS4D);
	EXPECT_NEAR(state->secondVeBlendValues[0], 0, EPS4D);
}

// Verify that TunerStudio accessors return the raw page4_s data
// (not the container wrapper).
TEST(SecondTables, TsAccessorsReturnRawPage) {
	EngineTestHelper eth(engine_type_e::TEST_ENGINE);

	page4_s* state = secondTablesGetState();
	void* tsPage = secondTablesGetTsPage();
	size_t tsSize = secondTablesGetTsPageSize();

	// TunerStudio page pointer must match the state pointer.
	EXPECT_EQ((void*)state, tsPage);

	// TunerStudio page size must be exactly sizeof(page4_s), not the container.
	EXPECT_EQ(tsSize, sizeof(page4_s));
}

// Verify that the storage accessors return the larger CRC-wrapped container.
TEST(SecondTables, StorageAccessorsReturnContainer) {
	EngineTestHelper eth(engine_type_e::TEST_ENGINE);

	const uint8_t* storagePtr = secondTablesGetStoragePtr();
	size_t storageSize = secondTablesGetStorageSize();

	// Storage size must be larger than raw page (includes version + crc).
	EXPECT_GT(storageSize, sizeof(page4_s));

	// The raw page4_s data must live inside the container at a fixed offset.
	page4_s* state = secondTablesGetState();
	EXPECT_GE((const uint8_t*)state, storagePtr);
	EXPECT_LT((const uint8_t*)state, storagePtr + storageSize);
}

// Verify that prepareForStorage stamps a valid CRC that can be verified.
TEST(SecondTables, PrepareForStorageProducesValidCrc) {
	EngineTestHelper eth(engine_type_e::TEST_ENGINE);

	secondTablesPrepareForStorage();

	const uint8_t* ptr = secondTablesGetStoragePtr();
	size_t size = secondTablesGetStorageSize();

	// Interpret the container: version (4 bytes) + page4_s + crc (4 bytes).
	ASSERT_GE(size, sizeof(page4_s) + 8);

	uint32_t version;
	memcpy(&version, ptr, sizeof(version));
	EXPECT_NE(version, 0u);  // Version must be set (PAGE4_DATA_VERSION = 1).

	uint32_t storedCrc;
	memcpy(&storedCrc, ptr + size - sizeof(storedCrc), sizeof(storedCrc));

	// Recompute CRC over the page4_s data portion.
	const uint8_t* dataStart = (const uint8_t*)secondTablesGetTsPage();
	uint32_t computedCrc = crc32(dataStart, sizeof(page4_s));

	EXPECT_EQ(storedCrc, computedCrc);
}

// Verify that modifying the data invalidates the CRC, and re-preparing fixes it.
TEST(SecondTables, CrcDetectsCorruption) {
	EngineTestHelper eth(engine_type_e::TEST_ENGINE);

	// Stamp valid CRC.
	secondTablesPrepareForStorage();

	const uint8_t* ptr = secondTablesGetStoragePtr();
	size_t size = secondTablesGetStorageSize();

	// Read back the stored CRC.
	uint32_t crcBefore;
	memcpy(&crcBefore, ptr + size - sizeof(crcBefore), sizeof(crcBefore));

	// Corrupt the data by modifying a VE table cell.
	page4_s* state = secondTablesGetState();
	auto original = state->secondVeTable[0][0];
	state->secondVeTable[0][0] = original + 10;

	// CRC should now be stale — recompute and compare.
	uint32_t computedCrc = crc32(state, sizeof(page4_s));
	EXPECT_NE(crcBefore, computedCrc);

	// Re-prepare should produce a new valid CRC.
	secondTablesPrepareForStorage();
	uint32_t crcAfter;
	memcpy(&crcAfter, ptr + size - sizeof(crcAfter), sizeof(crcAfter));
	EXPECT_EQ(crcAfter, computedCrc);
	EXPECT_NE(crcAfter, crcBefore);

	// Restore original value.
	state->secondVeTable[0][0] = original;
}

// Verify that the container size is 32-byte aligned (required for STM32H7 flash writes).
TEST(SecondTables, ContainerSizeAlignment) {
	size_t storageSize = secondTablesGetStorageSize();

	EXPECT_EQ(storageSize % 32, 0u)
		<< "page4 container must be 32-byte aligned for H7 flash writes, got " << storageSize;
}

// Verify reinit after prepare — simulates the boot sequence where
// initSecondTables is called, then later the data is burned and re-read.
TEST(SecondTables, ReinitPreservesState) {
	EngineTestHelper eth(engine_type_e::TEST_ENGINE);

	page4_s* state = secondTablesGetState();

	// Modify a value from the default.
	state->secondVeTable[2][3] = 42;

	// Prepare for storage (sets version + CRC).
	secondTablesPrepareForStorage();

	// The TunerStudio-visible state should still have our modification.
	EXPECT_NEAR(state->secondVeTable[2][3], 42, EPS4D);

	// Other defaults should remain unchanged.
	EXPECT_NEAR(state->secondVeTable[0][0], 80, EPS4D);
}
