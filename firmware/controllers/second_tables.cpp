// file second_tables.cpp

#include "pch.h"
#include "second_tables.h"
#include "storage.h"
#include "flash_main.h"

#define PAGE4_DATA_VERSION 1

struct page4_container_s {
	uint32_t version;
	page4_s data;
	uint32_t crc;

	uint32_t getCrc() const {
		return crc32(&data, sizeof(page4_s));
	}
};

static_assert(sizeof(page4_container_s) % 32 == 0,
	"page4_container_s must be 32-byte aligned for STM32H7 flash writes");

static page4_container_s secondTablesContainer;

static void secondTablesSetDefaults() {
	secondTablesContainer.data = {};

	// Start with the primary tables/bins as defaults
	copyTable(secondTablesContainer.data.secondVeTable, config->veTable);
	copyArray(secondTablesContainer.data.secondVeLoadBins, config->veLoadBins);
	copyArray(secondTablesContainer.data.secondVeRpmBins, config->veRpmBins);

	setLinearCurve(secondTablesContainer.data.secondVeBlendBins, 0, 100);
	setLinearCurve(secondTablesContainer.data.secondVeBlendValues, 0, 100);

	copyTable(secondTablesContainer.data.secondIgnitionTable, config->ignitionTable);
	copyArray(secondTablesContainer.data.secondIgnitionLoadBins, config->ignitionLoadBins);
	copyArray(secondTablesContainer.data.secondIgnitionRpmBins, config->ignitionRpmBins);

	setLinearCurve(secondTablesContainer.data.secondIgnitionBlendBins, 0, 100);
	setLinearCurve(secondTablesContainer.data.secondIgnitionBlendValues, 0, 100);
}

void initSecondTables() {
#if EFI_PROD_CODE
	if (storageRead(EFI_SECOND_TABLES_RECORD_ID,
			(uint8_t*)&secondTablesContainer,
			sizeof(secondTablesContainer)) == StorageStatus::Ok
		&& secondTablesContainer.version == PAGE4_DATA_VERSION
		&& secondTablesContainer.crc == secondTablesContainer.getCrc()) {
		// Valid data loaded from storage.
		return;
	}
#endif
	secondTablesSetDefaults();
}

void secondTablesBurn() {
#if EFI_PROD_CODE
#if (EFI_STORAGE_INT_FLASH == TRUE) && (EFI_STORAGE_MFS != TRUE)
	// INT_FLASH boards: page4 shares a flash sector with the main config.
	// A full config burn erases the sector, then burnExtraFlashPages()
	// writes page4 to all backends (INT_FLASH + SD) in one pass.
	// No separate storageWrite() here — that would double-write SD.
	writeToFlashNow();
#else
	// MFS or SD-only boards: write directly to all available backends.
	secondTablesPrepareForStorage();
	storageWrite(EFI_SECOND_TABLES_RECORD_ID,
		(const uint8_t*)&secondTablesContainer, sizeof(secondTablesContainer));
#endif
#endif
}

page4_s* secondTablesGetState() {
	return &secondTablesContainer.data;
}

void* secondTablesGetTsPage() {
	return (void*)&secondTablesContainer.data;
}

size_t secondTablesGetTsPageSize() {
	return sizeof(page4_s);
}

void secondTablesPrepareForStorage() {
	secondTablesContainer.version = PAGE4_DATA_VERSION;
	secondTablesContainer.crc = secondTablesContainer.getCrc();
}

const uint8_t* secondTablesGetStoragePtr() {
	return (const uint8_t*)&secondTablesContainer;
}

size_t secondTablesGetStorageSize() {
	return sizeof(secondTablesContainer);
}
