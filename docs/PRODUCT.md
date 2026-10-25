# Product direction — T60 Custom OS

**Goal:** Eventually replace both the factory operating software on Paul's actual T60 and the GloryFit companion app with independently controlled software, if feasible. Prefer adapting a compatible existing watch OS before building one from scratch.

## User-visible goals (proposed; not yet approved specifications)

- Simple, responsive watch display and navigation.
- Reliable time and date, plus useful battery indication.
- Watch-side settings and Bluetooth functions without requiring GloryFit.
- A replacement companion app that communicates with the customized watch software and does not depend on GloryFit.
- Preserve hardware battery life and avoid disabling essential protections.
- Add health, notification, and sensor features only after chip and sensor capabilities are identified and verified.

## Non-goals for now

- Replacing only the Android app while leaving the factory watch software is not the complete goal.
- Do not copy proprietary vendor firmware.
- Do not assume Android, Wear OS, Linux, LVGL, touchscreen support, or any specific RTOS fits this hardware.
- Do not promise custom flashing until recovery and signing restrictions are understood.

## Acceptance gates

1. Hardware identified with source-backed evidence.
2. Firmware toolchain compiles a harmless host-only core without watch access.
3. Boot/update constraints and original-image recovery are documented and verified.
4. The companion app protocol and replacement path are documented independently from watch firmware feasibility.
5. Hardware adaptation is tested without risking the only watch.
6. The owner explicitly approves any device-writing step.

**Status: exploratory.** Specific feature requirements should be set with Paul after hardware feasibility is established.
