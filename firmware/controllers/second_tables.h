// file second_tables.h

#pragma once

#include "page_4_generated.h"

void initSecondTables();
void secondTablesBurn();

page4_s* secondTablesGetState();

void* secondTablesGetTsPage();
size_t secondTablesGetTsPageSize();
