# List of all the board related files.

# F429 and F767 Nucleo are indeed the same board with a different chip fitted - so recycle the F429 config
BOARDCPPSRC = $(BOARDS_DIR)/nucleo_f429/board_configuration.cpp

# reducing flash consumption for EFI_ETHERNET to fit
DDEFS += -DEFI_FILE_LOGGING=FALSE -DEFI_ALTERNATOR_CONTROL=FALSE -DEFI_LOGIC_ANALYZER=FALSE -DEFI_ENABLE_ASSERTS=FALSE

DDEFS += -DLED_CRITICAL_ERROR_BRAIN_PIN=Gpio::B14

# Enable ethernet
EFI_ETHERNET = yes
ifeq (,$(findstring EFI_BOOTLOADER,$(DDEFS)))
	LWIP = yes
	DDEFS += -DCH_CFG_USE_DYNAMIC=TRUE
	# After OpenBLT exits, the Ethernet DMA is still running with the bootloader's
	# descriptor ring. STM32_MAC_DMABMR_SR issues a full DMA software reset in
	# mac_lld_start() before new descriptors are programmed, ensuring a clean state.
	# Only needed in the main app; the bootloader always starts from a clean DMA state.
	DDEFS += -DSTM32_MAC_DMABMR_SR=TRUE
endif

# Avoid TX-flush hang in mac_lld_start() on F767 silicon.
DDEFS += -DSTM32_MAC_DISABLE_TX_FLUSH=TRUE

# Prevent bootloader's mac_lld_init() from powering down the PHY.
# If the PHY is powered down, it stops generating the 50 MHz RMII reference clock,
# causing the main app's mac_lld_start() to hang forever waiting for ETH_DMABMR_SR
# to self-clear (DMA reset requires the RMII clock).
DDEFS += -DSTM32_MAC_ETH1_CHANGE_PHY_STATE=FALSE

# Both LWIP and uIP cause few shadow errors
ALLOW_SHADOW = yes

# We need early init for ethernet in OpenBLT
DDEFS += -DOPENBLT_BOARD_EARLY_INIT=TRUE

DDEFS += -DEFI_ETHERNET=TRUE
DDEFS += -DEFI_STORAGE_SD=FALSE

BUNDLE_OPENOCD = yes

DDEFS += -DHW_NUCLEO_F767=1

DDEFS += -DFIRMWARE_ID=\"nucleo_f767\"
DDEFS += -DDEFAULT_ENGINE_TYPE=engine_type_e::MINIMAL_PINS
DDEFS += -DSTATIC_BOARD_ID=STATIC_BOARD_ID_NUCLEO_F767

# this board is equiped with STM32F767ZI which has 2Mb of flash.
include $(PROJECT_DIR)/hw_layer/ports/stm32/2mb_flash.mk

# So we can have ini storage enabled
DDEFS += -DEFI_EMBED_INI_MSD=TRUE -DEFI_USE_COMPRESSED_INI_MSD=FALSE
