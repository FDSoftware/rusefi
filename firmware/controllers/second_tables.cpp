// file second_tables.cpp

#include "pch.h"
#include "second_tables.h"
#include "storage.h"
#include "flash_main.h"

static page4_s secondTablesState;

static void secondTablesSetDefaults() {
	secondTablesState = {};
	setRpmTableBin(secondTablesState.secondVeRpmBins);
	setLinearCurve(secondTablesState.secondVeLoadBins, 20, 120, 1);
	setTable(secondTablesState.secondVeTable, 80);

	setLinearCurve(secondTablesState.secondVeBlendBins, 0, 100);
	setLinearCurve(secondTablesState.secondVeBlendValues, 0, 100);

	setRpmTableBin(secondTablesState.secondIgnitionRpmBins);
	setLinearCurve(secondTablesState.secondIgnitionLoadBins, 20, 120, 3);
	setTable(secondTablesState.secondIgnitionTable, 30);

	setLinearCurve(secondTablesState.secondIgnitionBlendBins, 0, 100);
	setLinearCurve(secondTablesState.secondIgnitionBlendValues, 0, 100);
}

void initSecondTables() {
#if EFI_PROD_CODE
	if (storageRead(EFI_SECOND_TABLES_RECORD_ID, (uint8_t*)&secondTablesState, sizeof(secondTablesState)) != StorageStatus::Ok) {
		secondTablesSetDefaults();
	}
#else
	secondTablesSetDefaults();
#endif
}

void secondTablesBurn() {
#if EFI_PROD_CODE
	storageWrite(EFI_SECOND_TABLES_RECORD_ID,
		(const uint8_t*)&secondTablesState, sizeof(secondTablesState));
#if (EFI_STORAGE_INT_FLASH == TRUE) && (EFI_STORAGE_MFS != TRUE)
	// Page 4 shares a sector with the main config, so INT_FLASH can only accept
	// a direct write immediately after a sector erase.  Always trigger a forced
	// full-config burn so the sector is erased and page 4 is piggybacked in
	// writeToFlashNowImpl() — regardless of whether SD also wrote it.
	// This keeps INT_FLASH in sync even when an SD card is present, so that
	// removing the card never causes page 4 to revert to stale or default data.
	writeToFlashNow();
#endif
#endif
}

page4_s* secondTablesGetState() {
	return &secondTablesState;
}

void* secondTablesGetTsPage() {
	return (void*)&secondTablesState;
}

size_t secondTablesGetTsPageSize() {
	return sizeof(secondTablesState);
}
