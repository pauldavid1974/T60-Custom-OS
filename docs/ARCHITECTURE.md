# Architecture — provisional

This document describes the intended structure, **not a verified target firmware architecture**.

```text
Watch UI / app logic (portable, host-tested)
                |
      platform-independent interfaces
                |
     [board support package — TBD]
       /      |        |       \
   display   input   storage   BLE/clock
                |
       [real SoC / SDK — UNKNOWN]
```

## Development layers

- `firmware/include` and `firmware/src`: portable firmware logic that is useful independently of the target hardware.
- `firmware/tests`: host-executable unit tests. Host compilation proves only logic, **not watch compatibility**.
- Future `firmware/platform/<verified-board>`: board-specific drivers, boot entry and linker configuration **only after identification**.
- Future `simulator/`: on-PC interface simulator; optional once device input/display requirements are confirmed.
- `knowledge/graph.json`: searchable evidence/dependency map linking decisions, hardware facts, source files and open questions.
- `docs/`: design intent, risks, bring-up and recovery plans.

## Constraints

- IC-family evidence: Work obtained a BLE FFF1 information read beginning with `0B`, mapped to `BB2` in a GloryFit Realtek SDK. This does **not** identify an exact MCU or board.
- Exact flash geometry, display controller, RAM/ROM, SDK availability, boot signature policy, debug pads and all sensors remain unknown.
- No board-specific linker script, bootloader replacement, OTA package, or `flash` target is included.
- Do not give the impression that host test executables can be installed on the physical T60.

## Next architecture decision

After identifying the *exact* SoC and supported SDK/toolchain, choose the actual firmware language/runtime and board-support approach. Record this as a decision with supporting evidence in the knowledge graph.
