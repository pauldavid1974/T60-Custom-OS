# Firmware source

This directory is the beginning of the **actual watch OS codebase**, not just coordination notes.

Currently the only implementation is a small **portable UI state machine** compiled and tested on a development PC. It does not access a watch, Bluetooth, flash, sensors, or display hardware. It is intentionally target-neutral while the exact T60 SoC remains unknown.

## On a development PC

```sh
cmake -S firmware -B build/host
cmake --build build/host
ctest --test-dir build/host --output-on-failure
```

Build products are HOST PROGRAMS ONLY. Never attempt to upload them to the T60. There is deliberately no flash/update target or target toolchain config.

## Future source layout

- `include/t60/`: portable component interfaces
- `src/`: implementation independent of hardware
- `tests/`: host unit tests
- `platform/`: future verified SoC/board support (not created until hardware evidence exists)
- `app/`: later user-facing watch applications and shell, after requirements/hardware are confirmed

Do not import proprietary GloryFit source or vendor binaries into this repository without an applicable license.
