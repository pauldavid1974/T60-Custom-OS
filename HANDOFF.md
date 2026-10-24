# HANDOFF — T60 custom firmware investigation

**Updated:** 2026-10-24
**Phase:** Read-only feasibility investigation
**Next agent:** Qwen through Engine Room + a local harness (or ChatGPT)
**Owner:** Paul; private personal repo `pauldavid1974/T60-Custom-OS`
**Hard stop:** NO writing to the watch, flashing, factory resets, unpairing, disassembly, or opening debug interfaces without explicit, action-specific approval from Paul.

## Full project repository (established 2026-10-24)

This GitHub repo is now the **entire OS project's source of truth**, not just an AI handoff channel:

- `firmware/`: portable C code and host-only tests, with actual future BSP/OS integration gated on hardware identification.
- `docs/`: requirements, architecture, roadmap, hardware evidence, recovery rules and decisions.
- `knowledge/graph.json`: machine-readable graph connecting facts, sources, blockers, decisions and code.
- `tools/kg.py`: searchable graph CLI. Examples: `python tools/kg.py search BB2`, `python tools/kg.py related fact-bb2`.
- `tests/` and `.github/workflows/verify.yml`: graph and host-only firmware checks.

**Qwen must also read `knowledge/README.md` and use the graph when researching.** When reporting a durable finding, update the relevant docs/graph as well as this handoff. Record both tested and untested results accurately. Do not treat preliminary UI code as flashable firmware.

## Project objective

Build and eventually install **our own watch firmware/interface** on Paul's physical T60 smartwatch, rather than merely replacing the Android companion application or changing a watch face. Feasibility is not yet proven.

## Established evidence

| Confidence | Item | Evidence / limitation |
|---|---|---|
| **Observed** | Watch model **T60**, FCC ID **2BEQO-T60** | Photos of the watch and its information screens provided by Paul |
| **Observed** | Factory firmware **V007108** | Watch information screenshot |
| **Observed** | BLE connection via nRF Connect works | Device's GATT-service screenshots, 2026-10-24 |
| **Observed** | Proprietary GATT services include `0x55FF`, `0x56FF`, `0x60FF`, `0x61FF`, standard `0x180F`, plus `0000d0ff-3c17-d293-8e48-14fe2e4da212` | nRF Connect screenshots |
| **Observed** | A read-only `FFF1` read under the Realtek-like `D0FF` service returned `0B 01 1D 00 51 01 00 F7 00 00 00 00 00` | ChatGPT Work session on 2026-10-24 used phone mirroring / ADB and obtained the read |
| **Documented (app code)** | The Realtek SDK embedded in the installed GloryFit Android app maps leading chip identifier `0B` to `BB2` | Work's examination of installed app classes, reported in its exported transcript |
| **Strongly supported, not complete ID** | Watch uses a **Realtek-family update interface**; `BB2` is the observed SDK identifier | D0FF/FFF1 behavior + app SDK interpretation. **Exact RTL model and board revision remain unknown.** |
| **Observed (offline)** | GloryFit APK contains two firmware images with headers naming **RTL8762C** and **RTL8762D** | Work examined bundled images; **NEITHER is verified as this watch's installed firmware or recovery image** |
| **Observed** | Work found a Windows Phone Link window and successfully used ADB during investigation | Original Work transcript; do not assume future desktop access is automatic |

### Useful references (research leads, not compatibility guarantees)

- FCC listing and internal photos: https://fccid.io/2BEQO-T60/Internal-Photos/Internal-photo-7108448
- Realtek OTA/DFU protocol document reported by Work: https://www.realmcu.com/img/ipd/zh_638247794120444333.pdf
- Realtek development tooling guide: https://docs.realmcu.com/sdk/rtl87x3e/common/en/latest/text/User_Manual/quick_start.html
- Open-source smartwatch protocol research (possible family resemblance, **not** proven firmware compatibility): https://github.com/jam1garner/Haywatch

Work also reported inspecting a GloryFit APK's Realtek SDK and creating local working files (including a classes dump and firmware-documentation copy). Confirm their actual location/availability on Paul's computer; do not assume these files are committed or included here.

## Unknowns and blockers

1. **Exact SoC / flash / RAM / display controller / sensors / board revision.** `BB2` is not a complete chip identification.
2. **Original installed firmware backup.** The APK-bundled firmware files are not a backup.
3. **Firmware signing / encryption / write protections / bootloader restrictions.**
4. **Safe recovery method:** whether factory firmware can be restored after a failed update, and what debugging pads or hardware tools are accessible.
5. **Build target:** applicable Realtek SDK, licensing/access limitations, memory budget, toolchain, and on-watch graphics/input support.

Until these are resolved, **custom OS installation is an unproven possibility, not an executable plan**.

## Last live-device action and pending approval

- Completed: READ on `FFF1`, returned 13 bytes shown above.
- Not completed: proposed read of **`FFF3`** to gather protocol information. Work's automated action was blocked because approval for this specific operation was missing; nothing was written.
- Paul has **not yet approved** the `FFF3` read in this repo conversation. Do not treat a prior request to investigate as blanket permission to execute it.

## NEXT ASSIGNMENT — Qwen (offline, read-only, no approval required)

Investigate **what Realtek SDK `BB2` / IC ID `0B` actually identifies**, using reliable Realtek source code, the installed GloryFit SDK (if available locally), and manufacturer documentation.

Deliver:
1. Exact code paths or documentation mapping `0B` → `BB2`, and whether `BB2` maps uniquely to any named RTL SoC; include evidence, versions, and uncertainty.
2. Document the `D0FF/FFF1` 13-byte response field layout *only where verified*, clearly identifying unknown bytes.
3. Compare any public Realtek SDK flashing/recovery procedure to the T60's observed interfaces; explain limitations and what would be required to prove recovery safely.
4. Recommend **one smallest next diagnostic**, with exact proposed read-only action, expected benefit, risks, and a clear request for Paul's permission if it touches the live watch.
5. Update **this file**, `docs/HARDWARE.md`, and the relevant nodes/edges in `knowledge/graph.json` with concise findings and sources. Do not remove prior confirmed evidence.

**Stop condition:** Once the mapping/recovery research and report are recorded, STOP. Do not initiate any live watch interaction or firmware update.

## Transfer protocol

- At session start: read `README.md`, `AGENTS.md`, `HANDOFF.md`, and `knowledge/README.md`; search the graph for relevant nodes before duplicating research.
- At session end: commit appropriate **source, tests, docs, knowledge graph and this handoff** to `main`. Label **Observed / Documented / Inferred / Unknown**.
- In your final reply to Paul: 3–5 short bullets, commit hash, and an exact next action requiring owner approval (if any).
- ChatGPT and Qwen coordinate by reading and updating the same repository; **no outside reviewers, mandatory independent approval, or added collaborators**.
