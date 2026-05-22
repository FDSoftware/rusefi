# Ethernet + Bootloader: Progress & Developer Guide

**Board:** STM32F767ZI (Nucleo-F767ZI)  
**Goal:** OpenBLT bootloader → jump to rusefi main app → Ethernet works at `192.168.10.239:29001`

---

## Overview

The rusefi firmware uses the OpenBLT open-source bootloader. The bootloader lives in the
first 32 KB of flash (`0x08000000`). The main app lives from `0x08008000` onward.

On Nucleo F767ZI the board has an on-chip LAN8742A PHY connected in RMII mode.
The goal of this work is to make Ethernet work reliably after the bootloader hands
off control to the main app.

---

## Files Modified

### `hw_layer/openblt/netdev.c`
Ethernet device driver used by OpenBLT's uIP network stack.

**Changes:**
- Added `static bool netdev_started = false;` flag.
- In `netdev_init()`: set `netdev_started = true` after `macStart()`.
- Added `netdev_deinit()` function: calls `macStop()` only when `netdev_started == true`.

**Why:** The bootloader uses deferred/lazy network init. If no XCP firmware update is
requested, `netdev_init()` is never called, so `ETHD1.state == MAC_UNINIT`. Calling
`macStop()` unconditionally on an uninitialised MAC caused a Bus Fault → HardFault.

### `hw_layer/openblt/netdev.h`
Added declaration: `void netdev_deinit(void);`

### `bootloader/openblt_chibios/openblt_chibios.cpp`
In `CpuStartUserProgram()`, added a call to `netdev_deinit()` before jumping to the app:

```cpp
#if (BOOT_COM_NET_ENABLE > 0)
  netdev_deinit();   // stop MAC DMA before main app resets the peripheral
#endif
```

This ensures that when the bootloader DID start Ethernet (XCP update scenario),
the MAC DMA is cleanly stopped before the main app's `rccResetETH()` runs.

### `bootloader/Makefile`
Two compiler flag additions to fix build errors with GCC 15.2.0:

```makefile
# uIP Protothread macros use switch/case tricks that trigger -Wimplicit-fallthrough
CWARN = -Wall -Wextra -Wstrict-prototypes -Wno-implicit-fallthrough

# GCC 15.2.0 LTO is stricter about ODR violations in mixed cm4/cm7 headers
USE_OPT += ... -Wno-error=odr
```

### `ext/openblt/Target/Source/third_party/uip/uip/lc-switch.h`
Fixed typo in header guard (`__LC_SWTICH_H__` → `__LC_SWITCH_H__`).
Without this fix, GCC 15.2.0 emitted `-Werror=header-guard`.

### `config/boards/nucleo_f767/board.mk`
Ethernet-related build flags for the main app (not bootloader):

```makefile
EFI_ETHERNET = yes
ifeq (,$(findstring EFI_BOOTLOADER,$(DDEFS)))
  LWIP = yes
  DDEFS += -DCH_CFG_USE_DYNAMIC=TRUE
  # After OpenBLT exits with DMA running, SR resets DMA before new descriptors
  DDEFS += -DSTM32_MAC_DMABMR_SR=TRUE
endif
# Avoid TX-flush hang in mac_lld_start() on F767 silicon
DDEFS += -DSTM32_MAC_DISABLE_TX_FLUSH=TRUE
```

---

## Build Instructions

### Prerequisites

```bash
# ARM toolchain
sudo dnf install arm-none-eabi-gcc arm-none-eabi-newlib   # Fedora
# or
sudo apt install gcc-arm-none-eabi                          # Ubuntu/Debian

# OpenOCD
sudo dnf install openocd   # or apt install openocd
```

Verify: `arm-none-eabi-gcc --version` (tested with GCC 15.2.0)

### Build the Bootloader

```bash
cd firmware/bootloader

SHORT_BOARD_NAME=stm32f767_nucleo \
BOARD_DIR=$(pwd)/../config/boards/nucleo_f767 \
EXTRA_PARAMS="-DSTM32F767xx -DEFI_INJECTOR_PIN3=Gpio::Unassigned -DSTM32_HSE_BYPASS=TRUE" \
PROJECT_CPU=ARCH_STM32F7 \
make -j$(nproc) SUBMAKE=yes
```

Output: `blbuild/openblt_stm32f767_nucleo.bin` (~28 KB)

> **Note:** The `SUBMAKE=yes` flag skips the Java-based code-generation step
> (which generates `rusefi_generated_stm32f767_nucleo.h`). This is fine for
> bootloader-only rebuilds if the generated header already exists from a previous
> full build.

### Build the Main Firmware

From the repo root or `firmware/`:

```bash
cd firmware
make -j$(nproc) BOARD=nucleo_f767
```

Or using the rusefi build script if available. Outputs:
- `build/rusefi.bin` — raw binary (no CRC)
- `build/rusefi_crc32.bin` — binary with embedded CRC32 checksum at offset `0x1C`

The bootloader's `FlashVerifyChecksum()` requires the `_crc32.bin` variant.

---

## Flash Instructions

Connect the Nucleo board via ST-Link USB. Both binaries are flashed via OpenOCD.

### Flash Bootloader (0x08000000)

```bash
openocd -f interface/stlink.cfg -f target/stm32f7x.cfg \
  -c "init" \
  -c "reset halt" \
  -c "flash write_image erase firmware/bootloader/blbuild/openblt_stm32f767_nucleo.bin 0x08000000" \
  -c "reset run" \
  -c "exit"
```

### Flash Main Firmware (0x08008000)

```bash
openocd -f interface/stlink.cfg -f target/stm32f7x.cfg \
  -c "init" \
  -c "reset halt" \
  -c "flash write_image erase firmware/build/rusefi_crc32.bin 0x08008000" \
  -c "reset run" \
  -c "exit"
```

### Flash Both at Once

```bash
openocd -f interface/stlink.cfg -f target/stm32f7x.cfg \
  -c "init" \
  -c "reset halt" \
  -c "flash write_image erase firmware/bootloader/blbuild/openblt_stm32f767_nucleo.bin 0x08000000" \
  -c "flash write_image erase firmware/build/rusefi_crc32.bin 0x08008000" \
  -c "reset run" \
  -c "exit"
```

---

## Boot Sequence

1. CPU starts at `0x08000000` (bootloader reset vector, ITCM alias `0x00200000`).
2. Bootloader runs `halInit()` → `mac_lld_init()` → PHY soft-reset → (PHY power-down
   if `STM32_MAC_ETH1_CHANGE_PHY_STATE=TRUE`).
3. Bootloader checks **shared params** (SRAM at `0x20020000`) for a firmware-update
   request flag (`SharedParamsReadByIndex(0)`) and a watchdog-reset counter
   (`SharedParamsReadByIndex(1)`).
4. If `stayInBootloader == false` AND no XCP connection within 500 ms, calls
   `CpuStartUserProgram()`.
5. `CpuStartUserProgram()` calls `netdev_deinit()` (stops MAC if it was started),
   sets `VTOR = 0x08008000`, then jumps to the app's reset handler.
6. Main app starts: `halInit()` → `mac_lld_init()` → lwIP starts → `macStart()` →
   `mac_lld_start()`.

### Boot Timeout

The bootloader waits **500 ms** (`BOOT_BACKDOOR_ENTRY_TIMEOUT_MS = 500`) before
jumping. This is defined in `hw_layer/openblt/blt_conf.h`.

---

## Diagnostics

### Check if the app is running (not stuck in bootloader)

```bash
openocd -f interface/stlink.cfg -f target/stm32f7x.cfg \
  -c "init" -c "reset run" -c "exit"

sleep 5

openocd -f interface/stlink.cfg -f target/stm32f7x.cfg \
  -c "init" -c "halt" -c "reg pc" -c "exit"
```

If PC ≥ `0x00208000` (ITCM alias) or `0x08008000` (AXI), the app is running.
If PC < `0x00208000`, the bootloader is still resident.

### Check ETH MAC register state

```bash
openocd -f interface/stlink.cfg -f target/stm32f7x.cfg \
  -c "init" -c "halt" \
  -c "mdw 0x40028000 1" \   # ETH_MACCR  (expect 0xC if started: RE|TE)
  -c "mdw 0x40029000 1" \   # ETH_DMABMR (expect 0x20100 if started; 0x20101 = SR stuck)
  -c "mdw 0x40029018 1" \   # ETH_DMAOMR (expect nonzero ST|SR if running)
  -c "exit"
```

| Register | Healthy value | Stuck/Bad value |
|----------|---------------|-----------------|
| MACCR    | `0x0000000C` (RE+TE set) | `0x00008000` (reset default) |
| DMABMR   | `0x00020100` (SR=0) | `0x00020101` (SR=1, reset stuck) |
| DMAOMR   | nonzero (ST+SR bits) | `0x00000000` |

### Clear shared params (force bootloader to jump to app)

If the bootloader is stuck in update mode (watchdog counter > 10 from crash loops):

```bash
openocd -f interface/stlink.cfg -f target/stm32f7x.cfg \
  -c "init" -c "halt" \
  -c "mww 0x20020000 0x00000000" \   # Invalidate shared params buffer
  -c "reset run" -c "exit"
```

The `.shared` section lives at `0x20020000` (see bootloader linker map).
Writing 0 to the first word invalidates the `CAFEBBABE` identifier so
`SharedParamsInit()` re-initializes the buffer with a fresh zero counter.

### Verify app CRC32 checksum (host-side)

```python
import struct, binascii

with open('firmware/build/rusefi_crc32.bin', 'rb') as f:
    data = f.read()

CHECKSUM_OFFSET = 0x1C
stored = struct.unpack_from('<I', data, CHECKSUM_OFFSET)[0]
size   = struct.unpack_from('<I', data, CHECKSUM_OFFSET + 4)[0]

crc = binascii.crc32(data[:CHECKSUM_OFFSET]) & 0xFFFFFFFF
crc = binascii.crc32(data[CHECKSUM_OFFSET+4:size], crc) & 0xFFFFFFFF

print(f"stored=0x{stored:08x}  calc=0x{crc:08x}  match={crc==stored}")
```

---

## Network Configuration

| Parameter | Value |
|-----------|-------|
| ECU IP    | `192.168.10.239` (configured in `console/lwipopts.h`) |
| Port      | `29001` |
| ECU MAC   | `08:00:27:69:5B:45` (bootloader `netdev.c`; main app may differ) |
| PC IP     | Set statically to `192.168.10.x` on the Ethernet interface |
| Gateway   | `192.168.10.1` |

Test connectivity:
```bash
arping -c 4 -I enp34s0 192.168.10.239
ping -c 4 192.168.10.239
nc -zv 192.168.10.239 29001
```

---

## Known Issues / Open Items

### 1. DMABMR SR hang — ETH does not start after bootloader jump (**open**)

See `BUG_REPORT_ETH_DMABMR_HANG.md` for full analysis.

**Short version:** `STM32_MAC_ETH1_CHANGE_PHY_STATE=TRUE` (ChibiOS default) causes
`mac_lld_init()` to power down the LAN8742A PHY. The PHY then stops supplying the
50 MHz RMII reference clock. When the main app calls `mac_lld_start()` and asserts
`ETH->DMABMR |= ETH_DMABMR_SR`, the DMA reset bit never self-clears because the
RMII clock is absent. The lwIP thread hangs forever in `while (ETH->DMABMR & SR)`.

**Next step to fix:** Add to `config/boards/nucleo_f767/board.mk`:
```makefile
DDEFS += -DSTM32_MAC_ETH1_CHANGE_PHY_STATE=FALSE
```
Rebuild main firmware, reflash, retest.

### 2. Shared params watchdog counter

After repeated crash-loops, `checkIfResetLoop()` sets `stayInBootloader = true`
and the bootloader stays resident indefinitely. Clear the shared params as described
above whenever flashing a new build after a HardFault run.

---

## Flash Memory Map

| Region | Address | Size |
|--------|---------|------|
| Bootloader | `0x08000000` | 32 KB (`0x8000`) |
| Main app   | `0x08008000` | up to ~1.97 MB |
| App end (current build) | `0x080AC5A8` | — |

---

## Relevant Source Files

| File | Purpose |
|------|---------|
| `bootloader/Makefile` | Bootloader build config, compiler flags |
| `bootloader/bootloader_main.cpp` | Bootloader main loop, shared-params check, jump logic |
| `bootloader/openblt_chibios/openblt_chibios.cpp` | `CpuStartUserProgram()`, netdev_deinit call |
| `bootloader/openblt_chibios/openblt_flash.cpp` | `FlashVerifyChecksum()` — CRC32 check |
| `hw_layer/openblt/netdev.c` | ChibiOS MAC wrapper for OpenBLT's uIP |
| `hw_layer/openblt/netdev.h` | netdev API |
| `hw_layer/openblt/blt_conf.h` | OpenBLT config (timeouts, comm enables) |
| `hw_layer/openblt/hooks.c` | `BackDoorEntryHook()` — always BLT_TRUE |
| `hw_layer/openblt/shared_params.c` | Shared SRAM params (bootloader ↔ app) |
| `config/boards/nucleo_f767/board.mk` | Board-level build flags (ETH, LWIP, MAC flags) |
| `console/lwipopts.h` | `LWIP_IPADDR`, gateway, lwIP stack configuration |
| `ChibiOS/os/hal/ports/STM32/LLD/MACv1/hal_mac_lld.c` | ChibiOS MAC low-level driver |
