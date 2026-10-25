# HANDOFF — T60 custom firmware investigation

**Updated:** 2026-10-25
**Phase:** Read-only feasibility investigation
**Next agent:** Qwen through Engine Room + a local harness (or ChatGPT)
**Owner:** Paul; private personal repo `pauldavid1974/T60-Custom-OS`
**Owner authorization (2026-10-24):** Paul explicitly authorized reverse engineering of the physical T60 and GloryFit companion app. This covers read-only BLE discovery, connecting for service/characteristic enumeration, reading characteristics advertised as readable, and pulling the installed GloryFit APK from the dedicated phone for offline analysis.
**Hard stop:** No BLE characteristic/descriptor writes or notification subscriptions, app/watch setting changes, firmware update or flashing, reset, unpairing, unlock, debug-port access, physical disassembly, or other action that changes device state. If a proposed step might change state or falls outside the read-only scope, stop and ask Paul first. Eventual replacement of both products is the goal; this authorization does not approve installing or replacing either one.

## QWEN — NEXT JOB: identify the processor to evaluate an OS (2026-10-25)

**Objective:** establish the exact processor in Paul's physical T60 and the evidence needed to decide whether an existing OS can run on it. A Bluetooth inventory is supporting evidence, not the final deliverable. This assignment supersedes the inventory-only job in `704d037`. Your report `771838b` is accepted; proceed within the existing read-only authorization.

1. Safely sync `main`, preserve other changes, and read `README.md`, `AGENTS.md`, this assignment, `docs/HARDWARE.md`, `docs/RECOVERY.md`, `docs/ROADMAP.md` and `docs/QWEN38_AGENT_GUIDE.md`. Search the graph for `BB2`, `FFF1`, `question-soc` and `qwen`. Keep the working runtime settings. Acknowledge this assignment in your next report with the commit you read.
2. Start from existing evidence: GloryFit v4.0.12's SDK defines `IC_BB2 = 0x0B`; FFF1 returned `0B 01 1D 00 51 01 00 F7 00 00 00 00 00`; FFF3 returned `14 00`. The FFF1 IC-ID field role remains Inferred. Neither BB2 nor matching Bluetooth UUIDs establishes an exact RTL part or board compatibility.
3. Trace the already-extracted app/SDK code offline for the actual FFF1 parser, chip-selection logic, hardware identifiers and update-image selection. Cite exact class/method and artifact version/hash. Consult original manufacturer documents and the existing FCC internal-photo lead for this exact FCC ID, checking actual chip markings where legible. Do not identify this watch from another product merely named T60. Label every proposed part match Observed, Documented, Inferred or Unknown.
4. Use the remaining authorized reads where they can provide hardware/version identifiers or resolve a specific parser question. Before a read, state what uncertainty its value could resolve; if its purpose is only inventory, label it as such and prioritize identification evidence. D0FF `FFD2`–`FFD4`, `FFE0`, `FFE1`, `FFF4`, `FFF5` and enumeration of `00006287-3c17-d293-8e48-14fe2e4da212` / `000002fd-3c17-d293-8e48-14fe2e4da212` remain permitted. Read only characteristics visibly marked READ after verifying the actual T60 and explicit Read control in a fresh screenshot/XML view. Battery can remain deferred because it does not identify the processor. Preserve raw bytes/errors, full UUIDs, properties, timestamps and local evidence filenames. No speculative decoding.
5. Deliver a processor/OS decision table: exact SoC and confidence/evidence; RAM/flash known versus unknown; applicable SDK/toolchain; existing OS candidates with exact target support and primary sources; missing board/peripheral support. Recommend an OS base only if compatibility is substantiated. If still blocked, name the specific missing evidence, which completed leads failed to resolve it, and the smallest next diagnostic likely to obtain it, including whether it requires new owner approval. Do not report a service inventory as successful processor identification or commit to a guessed OS.
6. Update `docs/HARDWARE.md`, relevant stable nodes/edges in `knowledge/graph.json`, and this handoff together. Correct stale claims that FFF1 byte 0's role is proven. Run `python tools/kg.py validate` and `python -m unittest discover -s tests -v`; inspect the diff, commit and push through functioning hooks. Report the actual tests, commit SHA, evidence paths, processor conclusion, OS-decision status and one specific next action. Do not add target firmware or a replacement app in this job.

**Check-in requirement:** while the harness is active, fetch/check GitHub `main` every 15 minutes while waiting, and before resuming device work or after pushing a report. Read new review/assignment text and act within authorized scope; stay quiet if unchanged. A written instruction cannot keep an ended harness alive: if timed checks are unavailable or the session must end, explicitly report that limitation rather than claiming monitoring continues.

**Hard stop:** no writes/subscriptions, settings or pairing changes, installation, DFU/OTA, flashing, reset, unlock, debug-port access or disassembly. Stop at an ambiguous target/control, unavailable permitted tooling, blocked hook or a step requiring broader authorization. No new software installation or broad unrelated filesystem searches. After the bounded identification/OS assessment is pushed, await review before further device work.

## Shared work desk — Qwen ↔ Codex

| Item | Current state |
|---|---|
| Project goal | Reverse engineer the T60 and GloryFit, then eventually replace both. First evaluate a compatible existing watch OS; build one only if needed. |
| Phase | Read-only physical-watch and companion-app reconnaissance. No replacement has been implemented or installed. |
| Latest Qwen report | Session 4 report (this commit, mission `2ae9057`): SDK trace found the FFF1 parser (`DeviceInfo.parseX0000`); the T60's GATT exactly matches the SDK's default Realtek DFU layout; IC name map (BB2 product line) documented; app proven to run the default `r` profile; FCC applicant identified (Shenzhen Haocheng Technology); second live read batch completed. See "Status — Qwen via OpenCode session 4" below. |
| Current Qwen action | Processor identification (mission `2ae9057`) in progress: vendor and product line established (Realtek `BB2`); exact RTL part still **Unknown**. Next: FCC photo mirrors for chip markings, read-only `0x5D` advertisement check in nRF, 60FF/61FF enumeration, justified `33F1`/`34F1` reads. |
| Current review | Codex reviewed Qwen commit `771838b` and the available nRF screenshots. Service/characteristic list and `FFF1`/`FFF3` values match the visible evidence. `d175f6e` still applies: FFF1 byte 0's field role is inferred, not proven. |
| Verification | Codex reran `python tools/kg.py validate` → 46 nodes / 65 edges OK; `python -m unittest discover -s tests -v` → 5/5 passed. |
| Shared channel | This file on GitHub `main` is the work desk. Qwen reports by committing findings here and in source docs/graph. Codex reviews each new report and posts guidance here. A Qwen commit is a report, not a completed review. |

### Codex repository check (2026-10-25)

- **Observed:** refreshed `origin/main`; it remains at `6a0b613`, with latest Qwen report `771838b` and APK-analysis report `62a4ac5`. No newer Qwen report or uncommitted work was present at check time.
- **Verification:** `python tools/kg.py validate` passed (46 nodes / 65 edges); `python -m unittest discover -s tests -v` passed all 5 tests. No live-device action or host C build was performed.
- **Review finding:** the historical "GloryFit APK primary-source verification" paragraph in `docs/HARDWARE.md` still calls FFF1 byte 0's IC-ID role "source-supported", contradicting the later correction. Treat the role as **Inferred**; the SDK constant mapping alone does not establish this payload field. Exact SoC, backup and recovery remain **Unknown**.
- **Next assignment (superseded):** the inventory-only job was replaced by the processor-identification / OS assessment at the top of this file. Existing read-only safety boundaries remain in effect.
### Turn-taking

- Qwen: sync `main` and reread the active assignment plus latest Codex review before resuming, before each new live read, and after every report commit. When waiting, check `main` for review notes every 15 minutes; stay quiet if unchanged.
- Codex: monitor `main` every 15 minutes, review new Qwen commits, and push focused corrections or next-step guidance here when needed. Notify Paul about meaningful findings or blockers.
- Both: preserve the other agent's work; never bypass hooks; keep device actions within Paul's recorded read-only authorization.

## Prior status — offline BB2 research before APK extraction (2026-10-24)

- **Documented, transcript-reported:** GloryFit's embedded Realtek SDK reportedly maps FFF1 byte `0x0B` to the label `BB2`. The APK/class dump and exact mapping path were not available in this checkout, and public source lookup did not establish a unique RTL SoC for `BB2`. Keep the chip model unknown.
- **Observed:** preserve the FFF1 response exactly as `0B 01 1D 00 51 01 00 F7 00 00 00 00 00`. Only byte 0 has the reported SDK label; fields 1–12 are unknown.
- **Documented:** Realtek materials describe target-specific SDK/tooling and image programming/OTA; public Zephyr documentation currently lists Realtek RTL8752H and RTL87x2G Model A evaluation targets. These are not evidence of T60 compatibility. A public Realtek SDK repository exists for RTL87x2G; the exact T60 SDK access/license remains unknown.
- **Inferred:** an exact-part vendor SDK is the strongest conditional customization route. Zephyr is a conditional option only if the exact SoC is supported and required vendor images are obtainable. ZSWatch is GPL-3.0 but targets Nordic nRF5340 hardware and is not a compatible base.
- **Unknown:** FFF1 byte layout, exact RTL part, genuine factory backup, signature/protection behavior, and safe recovery. The OTA-looking GATT interface does not establish restoration capability.
- Research details and candidate table: `docs/HARDWARE.md`. Relevant knowledge graph nodes were updated/added. No device interaction occurred.

**Authorization note:** The read-only reverse-engineering scope above supersedes the earlier pending approval request for a read of `FFF3`; that read is within the approved scope if the connected device exposes it as readable. It does not prove recovery or authorize writes.

## ACTIVE ASSIGNMENT — Qwen via OpenCode (read-only device and app reverse engineering)

### Goal

Reverse engineer the actual T60 and its GloryFit companion app together using the owner-authorized read-only scope. Identify the physical watch over BLE, inventory its GATT interface, collect readable characteristic values, and inspect the installed app's code/data offline to trace how it communicates with the watch. Verify the reported `0x0B -> BB2` mapping and whether it identifies an exact RTL SoC. Map only protocol fields and behavior supported by observed data or source. The long-term product goal is to replace both the watch software and companion app; this task is reconnaissance and evidence gathering, not installation or implementation.

### Read first and evidence to start from

Read `README.md`, `AGENTS.md`, `HANDOFF.md`, `knowledge/README.md`, `docs/HARDWARE.md`, `docs/RECOVERY.md`, `docs/PRODUCT.md`, and `docs/QWEN38_AGENT_GUIDE.md`. Search the graph with `python tools/kg.py search BB2` and `python tools/kg.py search FFF1` before researching.

### Qwen ↔ reviewer communication loop

Follow the shared work desk's turn-taking rules above. This assignment's current reviewer note and next action are summarized in the work-desk table; details and the full Qwen report remain below.

Known evidence: the observed FFF1 bytes are `0B 01 1D 00 51 01 00 F7 00 00 00 00 00`; an exported Work-session report says the GloryFit Realtek SDK labels leading byte `0B` as `BB2`. The installed app is GloryFit `com.yc.gloryfit` v4.0.12 (versionCode 279) on the dedicated Samsung SM_G781U phone. The watch was observed bonded and connected as `T60(ID-AE67)`, address `60:1A:B7:00:AE:67`; this may be a random/resolvable address. No APK or class dump is committed.

### Scope and files

- Confirm the correct project directory, branch, and clean/dirty state; preserve existing user changes. Check connected tools and phone state without changing settings.
- Locate the nearby watch with a BLE scan, matching the recorded name/address and observed services; log timestamp, advertised name/address type when available, RSSI, and scan method. Do not assume a MAC is stable.
- Connect to the identified T60 for read-only GATT service/characteristic enumeration. Read characteristics only when the characteristic advertises READ; include `D0FF/FFF1` and `FFF3` if present/readable. Record raw bytes, UUIDs, property flags, read results/errors, and disconnect afterward. The already-connected nRF Connect app on the dedicated phone may be used for this: capture the screen first and verify the target service, characteristic, and explicit Read control before invoking it. If the screen/target is ambiguous, stop and ask Paul for a screenshot or guidance. Do not write characteristics/descriptors or enable notifications/indications.
- Pull only the installed package `com.yc.gloryfit` APK from the dedicated phone using ADB, into a temporary/local analysis area outside Git. Record package version, APK hash and source path. Decompile/analyze offline; never commit the APK, proprietary SDK, firmware binaries, private device dumps, or copyrighted extracts.
- Trace app code paths for service discovery, characteristic IDs, read/write/notification handling, firmware selection and chip-ID mapping. Do not trigger app operations that change watch settings, pairing, firmware, or other persistent state.
- Compare observed GATT data with app code and public manufacturer documentation. Do not infer field meanings from byte patterns alone. If any read requires a write/subscribe or an app action that may change state, stop and report it for Paul's separate approval.
- If ADB, BLE tooling, permissions, or artifact extraction are unavailable, stop at that boundary and report the exact blocker; do not install software, alter phone settings, or broaden filesystem searches without asking.
- Update `HANDOFF.md`, `docs/HARDWARE.md`, `docs/PRODUCT.md`/`docs/DECISIONS.md` if durable scope findings warrant it, and relevant stable nodes/edges in `knowledge/graph.json`.

### Allowed and prohibited actions

Allowed by Paul's explicit approval: read-only BLE scan, connection, GATT enumeration and reads of characteristics marked READ; using the existing nRF Connect UI solely to inspect GATT and invoke those explicit Read operations after checking the screen; read-only ADB package metadata/APK pull for GloryFit; offline static analysis of that APK; repository and manufacturer-documentation research.

Prohibited: characteristic/descriptor writes, notification subscriptions, pairing changes, DFU/OTA, flashing, reset, unlock, debug-port access, physical disassembly, or any action likely to alter persistent device/app state. Do not install or uninstall phone apps, change phone developer/Bluetooth settings, or commit APKs, SDK binaries, firmware images, secrets, or private dumps. Ask Paul before crossing any boundary.

### Acceptance and report

- Produce an evidence inventory of the live GATT database and readable values, and an app protocol map linking exact code paths to observed UUIDs/bytes. Report mismatches and unknowns rather than filling gaps.
- `python tools/kg.py validate` and `python -m unittest discover -s tests -v` must pass if project files are changed. Do not claim host C checks unless actually run.
- Review `git diff`; commit to `main` only if the existing safety hooks permit it. Never bypass a failing hook.
- Report exact evidence paths/version, whether `BB2` resolves to a part number, fields decoded versus unknown, files changed, tests run, commit SHA if committed, and one next action. Label each claim Observed, Documented, Inferred, or Unknown.

### Hard stop

Stop after read-only watch/app reconnaissance is documented, or immediately at any blocker requiring a prohibited state-changing action. Do not implement or install replacement firmware or app in this assignment.

## Status — Qwen via OpenCode session (2026-10-24)

Carried out the active assignment (offline verification of `0x0B -> BB2` from local SDK evidence). Result: **required primary-source artifact absent from every project-scoped location; mapping remains transcript-reported.**

- **Checked (Observed, this session):** full repository tree incl. hidden files and git history on all branches (no APK, smali/dex, class dump, device dump or Work transcript export ever committed); `My Projects` sibling folders (none T60-scoped — `Qwen-Colab` is Engine Room serving infrastructure, unrelated); `Documents`/`Desktop` top levels matching T60/glory/watch/firmware (none); name-filtered `Downloads` (only unrelated `watch_face_designer.html`).
- **Outcome:** no local GloryFit APK or extracted SDK exists to verify the `0B -> BB2` class path or FFF1 field decoding. Per the assignment's stop condition, no guessing and no expansion into unrelated research.
- **BB2 identification:** unchanged — **Unknown** as to exact RTL part. `BB2` remains a Documented-in-transcript SDK label only; no vendor documentation or code examined this session maps it to a named SoC.
- **FFF1 fields:** no change. Byte 0 `0B` has the reported SDK label; bytes 1–12 remain **Unknown** (no local source available to decode them).
- **Files changed:** `knowledge/graph.json` (fact-bb2 summary), `docs/HARDWARE.md` (absence check bullet), this handoff.
- **Tests reported by Qwen:** `python tools/kg.py validate` → `OK: 38 nodes, 48 edges`; `python -m unittest discover -s tests -v` → 5/5 passed. Host C build **not run** (no CMake/C compiler on this laptop; no portable C code changed).
- **No device interaction:** no watch or phone action of any kind was performed.

**At that time, next action:** make primary-source app evidence available by locating a local class dump or pulling the installed APK. Paul's later authorization for read-only ADB extraction and BLE reconnaissance is recorded at the top of this handoff and supersedes this earlier approval request.

## Status — Qwen via OpenCode session 2 (2026-10-24, owner-authorized read-only RE)

Carried out the expanded active assignment. Paul's 2026-10-24 authorization covers read-only BLE and the read-only ADB APK pull; **the ADB/APK side was completed, the live-BLE side is partially blocked on tooling** (details below).

- **APK evidence (Observed, pulled 2026-10-24):** dedicated phone `RFCNC10FA3K`, `com.yc.gloryfit` v4.0.12 (versionCode 279). Pulled to a temp area outside Git: `base.apk` (104,002,230 B, SHA-256 `470B7EAE5C79F9CBF26B59AC651832A70143A1A4DC6C849C8204DFB2D72159AB`) and `split_config.arm64_v8a.apk` (82,132,497 B, SHA-256 `4EC08F3D753D300F243B8A1ABDA9B54D76AE439ACFBD10BC1B91369A99F593F2`). Path: `C:\Users\pdo74\AppData\Local\Temp\opencode\t60-gloryfit\` (not in the repo; .gitignore also blocks APKs).
- **`0x0B -> BB2` VERIFIED from primary source (Documented):** `classes5.dex` contains the vendor SDK package `com.realsil.sdk` (RealSil = Realtek's MCU brand). `com.realsil.sdk.dfu.DfuConstants` defines `IC_BB2 = 11` (0x0B) plus the full IC table: IC_BEE1=0x03, IC_BBPRO=0x04, IC_BEE2=0x05, IC_BBLITE_ANC=0x06, IC_BBPRO2=0x07, IC_BBLITE=0x08, IC_SBEE2=0x09, IC_BBPRO3=0x0A, IC_BB2=0x0B, IC_BEE3=0x0C; also `MANUFACTURER_ID_REALTEK = 0x5D`. **BB2 is a vendor product-line label (with BEE*/BBPro*/BBLite* siblings), not an RTL part number** — no SoC part string for it appears in the SDK. Exact chip remains **Unknown**.
- **GATT map (Documented, matches live observations):** the SDK's DFU defaults use service `0000d0ff-3c17-d293-8e48-14fe2e4da212` (exactly the observed T60 service) with characteristics `00006287/6387/6487` (same 128-bit base), MTU 256. Per-profile config classes (`com.realsil.sdk.dfu.m.o`, `n/h`, `o/e`, `p/g`, `q/g`, `r/h`, `s/j`) define characteristics `0000fff1`, `0000fff2` (16-bit base); `dfu.m.o` pairs the d0ff service with `0000fff3`. `ExtendedBluetoothDevice` uses `0000fff6` as a scan-filter UUID. `classes6.dex` defines services `000055ff`, `000056ff`, `000057ff` (observed 55FF/56FF) and characteristics `0000fff0`, `0000ffd0..0000ffd5`, `0000ffd8`. Observed `60FF`/`61FF` services were **not** found as SDK string constants (Unknown how they are formed). The observed custom 128-bit service `6a24eeab-4b65-4693-986b-3c26c352264f` is the SDK's `WELL_KNOWN_SPP_UUID` (SPP transport) in four classes.
- **FFF1 fields (review correction):** no explicit 13-byte FFF1 parser exists in the SDK Java. The SDK separately documents `IC_BB2 = 0x0B` and has an `icId` field in its OTA protocol model, but no found code ties the FFF1 byte layout to that model. Thus the FFF1 byte 0 value is **observed** as `0x0B` and independently **documented** as the SDK's BB2 label; its role as an IC-ID field is only **inferred**, not proven. Bytes 1–12 remain Unknown. No RTL8762C/D firmware blobs exist in this APK version's assets (the earlier transcript claim was not reproduced here).
- **Phone/BT state (Observed via read-only `dumpsys bluetooth_manager` 2026-10-24):** nRF Connect for Mobile (`no.nordicsemi.android.mcp`) is present and reported as the phone's current GATT client, connected to the T60 (`60:1A:B7:00:AE:67`) since 10-24 22:34:42. The stack dump does **not** include the remote GATT database, so the service tree and `FFF1`/`FFF3` values still need to be captured in the app.
- **Live-BLE blocker:** the laptop has no GATT client tooling (no `bleak`, no nRF CLI, no desktop nRF Connect), so laptop-side connect/read was not attempted. The read-only authorization includes using the existing nRF Connect UI for explicit characteristic reads after visually confirming the target and Read control; do not use blind `ADB input` taps. If Qwen cannot inspect the screen or safely operate that UI, ask Paul for an app screenshot/hex read rather than requesting a new BLE tool install first.
- **Native libs (Observed):** `libRtkAesJni.so` (DFU AES JNI; matches DfuConstants AES sizes 16/32) and `libRtkMediaCodec.so` confirm Realtek native code in the app; no chip part strings in any `.so`.
- **Files changed:** `HANDOFF.md`, `docs/HARDWARE.md`, `knowledge/graph.json`. Tests: `python tools/kg.py validate` and `python -m unittest discover -s tests -v` (actual output in the session report). Host C build not run (no C code changed).
- **No prohibited action performed:** no BLE writes/subscriptions, no app/watch state changes, no flashing, no disassembly. ADB used for read-only `dumpsys`/`pm path`/APK `pull` only.

**Next Qwen action:** use the already-connected nRF Connect app via a visible, verified UI path to record the live T60 GATT tree and perform read-only reads of `FFF1` and `FFF3` if they advertise READ. Capture the screen before each read and save the displayed raw hex values and characteristic properties. If the app UI cannot be accessed safely from OpenCode, ask Paul to provide screenshots/hex values; do not install new BLE tools or use blind taps.

### Codex review follow-up (2026-10-25; commit `d175f6e`)

- Keep `0x0B -> IC_BB2` as a verified SDK mapping, but treat “FFF1 byte 0 is the IC-ID field” as an inference: no FFF1 parser links that response to the SDK `icId` model.
- Proceed with the approved nRF Connect screen inspection and explicit READ operations after visually confirming the exact target/control. Report the live GATT properties and raw values, then sync `main` and check this handoff for further review before continuing.

## Status — Qwen via OpenCode session 3 (2026-10-25, live GATT capture)

Completed the work-desk action: live GATT tree capture and the two explicit reads, all within the owner-authorized read-only scope, using only the already-connected nRF Connect app on the dedicated phone.

- **Method (Observed):** nRF Connect CLIENT view, `T60(ID-AE67)` `60:1A:B7:00:AE:67`, CONNECTED + BONDED; phone clock ~11:34–11:38. Every screen state verified via screenshot plus `uiautomator` XML dump (exact element bounds) before any tap. Only the per-characteristic **Read** control (resource `action_read`, content-desc "Read") was invoked, after visually confirming the target row. One mis-tap on a row body (no-op, layout unchanged) and one scroll overshoot were corrected by re-dumping bounds. No writes, no subscriptions, no state changes, no new software installed.
- **Observed GATT tree (10 services, list order):** `0x1801` Generic Attribute; `0x1800` Generic Access; `0x55FF`; `0x56FF`; `0x60FF`; `0x61FF`; `0x180F` Battery Service (Battery Level `0x2A19` READ+NOTIFY, CCCD `0x2902`); `0000d0ff-3c17-d293-8e48-14fe2e4da212`; `00006287-3c17-d293-8e48-14fe2e4da212` (collapsed); `000002fd-3c17-d293-8e48-14fe2e4da212` (collapsed). The 128-bit service `6a24eeab-…` is **not** a GATT service (SPP only — consistent with the SDK `WELL_KNOWN_SPP_UUID` finding).
- **Observed D0FF characteristics (10, list order):** `FFD1` WRITE NO RESPONSE (only write capability in the service); `FFD2`, `FFD3`, `FFD4`, `FFF1`, `FFE0`, `FFE1`, `FFF3`, `FFF4`, `FFF5` — all READ, none NOTIFY/INDICATE. This is the live subset of the SDK's documented template (SDK also lists `ffd0`, `ffd5`, `ffd8`, `fff0`, `fff2`, which this device does not expose).
- **Observed FFF1 read:** `Value: (0x) 0B-01-1D-00-51-01-00-F7-00-00-00-00-00` — 13 bytes, byte-identical to the Work-session payload, independently re-confirmed live. Byte 0 `0x0B` equals SDK `IC_BB2`; per review `d175f6e` the IC-ID field role stays **Inferred**; bytes 1–12 remain **Unknown**.
- **Observed FFF3 read:** `Value: (0x) 14-00` — 2 bytes `14 00`; meaning **Unknown** (no SDK payload definition found).
- **Evidence:** screenshots `01`–`10` (PNG + resized JPG) and `uiautomator` dumps `ui.xml`–`ui7.xml` at `C:\Users\pdo74\AppData\Local\Temp\opencode\t60-gloryfit\screens\` (outside Git; UI only, no secrets). Read-only ADB used for `screencap`, `input` (swipe/tap on the nRF UI only), `uiautomator dump`, `pull`.
- **Files changed:** `HANDOFF.md` (this section + work desk), `docs/HARDWARE.md` (summary table + new "Live GATT capture via nRF Connect" section; corrected stale "nRF not installed" line), `knowledge/graph.json` (updated `iface-gatt`, `fact-fff1`, `fact-nrf-phone-gatt`; added `source-nrf-capture`, `fact-fff3`; +6 edges).
- **Tests:** `python tools/kg.py validate` and `python -m unittest discover -s tests -v` — results recorded at commit time (see work desk Verification row). Host C build not run (no C code changed).

**Codex review (2026-10-25):** compared the screenshots `08_fff1_result.png` and `10_fff3_result.png` with the reported reads: the visible FFF1 value is `0B-01-1D-00-51-01-00-F7-00-00-00-00-00`, and FFF3 is `14-00`; both are shown under READ properties. Screenshot `08` also visibly supports the reported D0FF characteristic IDs/properties. The report correctly leaves two services collapsed and payload meanings unknown. Graph validation and all five unit tests reran successfully against `771838b`. No inconsistency found in the reviewed evidence. The previously documented interpretation limit remains: `0B -> IC_BB2` is an SDK mapping, while FFF1 byte 0's role in this response is still inferred.

**Next Qwen action (authorized):** continue with the remaining READ-advertised D0FF characteristics (`FFD2`–`FFD4`, `FFE0`, `FFE1`, `FFF4`, `FFF5`), expand and enumerate `00006287-…` and `000002fd-…`, and invoke the Battery Level `0x2A19` READ control. Before each UI action, verify the target and control in a fresh screenshot/XML view. Do not enable notifications, write, or change settings. If a service/characteristic has no READ property, record its visible properties and stop there. Report raw values without assigning meanings absent source-backed evidence; then sync `main` and check this work desk for review notes before any further live read.

## Status — Qwen via OpenCode session 4 (2026-10-25, mission `2ae9057`: processor identification)

**Assignment acknowledged:** working from `main` at commit `2ae9057` ("Focus Qwen assignment on processor identification and OS compatibility"). All actions below are within the existing read-only authorization; no writes, subscriptions, or state changes.

- **SDK trace (offline, GloryFit v4.0.12 `classes5.dex`, dexdump):**
  - **FFF1 parser found** (closes the "no parser" gap from review `d175f6e`): `com.realsil.sdk.dfu.model.DeviceInfo.parseX0000:([BI)V` (dex addr `3dd98c`), LITTLE_ENDIAN — byte 0 = icType, byte 1 & `0x0F` = specVersion; specVersion=1 path (the T60's): byte 2 = secureVersion, byte 3 = mode, bytes 4–5 = maxBufferCheckSize (LE16), byte 6 = otaTempBufferSize, bytes 8–11 = imageVersionIndicator (LE32); byte 7 not read. Decoding the observed payload: icType `0x0B` (BB2), specVersion 1, secureVersion `0x1D`, mode 0, maxBufferCheckSize `0x0151`, otaTempBufferSize 0, imageVersionIndicator 0 — **Inferred (strong)** (exact GATT layout match; the traced call path is the profile handler parsing X0000 from the 6487 notify characteristic).
  - **IC table + name map (Documented):** `DfuConstants` IC_BB2=0x0B plus 9 siblings and `MANUFACTURER_ID_REALTEK=0x5D` (Realtek's Bluetooth SIG company ID); name mapper `com.realsil.sdk.dfu.l.a.a(I)` extends the line to BBLITE_2=13, BEE3_PRO=14, BEE4=15, BEE3_PLUS=16, BB2_PLUS=17, BEE5=18, BB2_ULTRA=19, BB3=20. `BB2` is a multi-generation Realtek product line, not an RTL part number.
  - **GATT layout exact match (Documented + Observed):** SDK defaults (`com.realsil.sdk.dfu.m.o.<clinit>`) = D0FF-3c17 OTA service with 16-bit-base FFF3 characteristic + FFD0, and 6287-3c17 DFU service with 6387 (write) / 6487 (notify); default profile `r` (`r.g`/`r.h`) declares the same 6287-3c17 set {6287,6387,6487,6587} + D0FF-family {FFD1–FFD5, FFD8, FFF1, FFF2}. The observed T60 GATT is an exact subset of this layout — the T60 implements Realtek's standard DFU GATT (3c17 = vendor 128-bit base).
  - **Profile selection (Documented):** factory `com.realsil.sdk.dfu.utils.a.c(I)` maps protocolType 0x10/0x12/0x13/0x14/0x15 → profiles s/n/o/p/q, **default → r**; `DeviceInfo.setProtocolType` is **never invoked in any of the 8 dex files** (string + reference scan), so the app runs the **default r profile** for the T60; image loader `FirmwareLoaderX.loadImageBinInfo` defaults to `k/f` (per-chip parsers n/c, p/c, q/c, v/c, s/e|s/f, x/h|i|j); no firmware blobs in the APK assets (runtime image loading keyed by `protocolType,specVersion,icType,imageId`).
- **Second live read batch (nRF, verified method; screenshots `11`–`37`, `ui8.xml`–`ui29.xml` outside Git):** FFD2 = `60 1A B7 00 AE 67` ×2 (connected-device address repeated; role inferred); FFD3/FFD4/FFE1/FFF5 = empty (0-byte Value rows; FFD3/FFD4 read twice); FFE0 = 74-byte and FFF4 = 55-byte code-like blobs (pattern resembles ARM Thumb words; no SDK payload definition — meaning **Unknown**); Battery 0x2A19 = 100%. Enumerated: `00006287-…` = 6387 WRITE NO RESPONSE + 6487 NOTIFY/WRITE + CCCD (no 6587); `000002fd-…` = FD03 W+WNR + FD04 NOTIFY + CCCD, no READ chars; `0x1801`/`0x1800` have no characteristics; `0x55FF` = 33F1 READ+WRITE + 33F2 NOTIFY+CCCD; `0x56FF` = 34F1 READ+WNR + 34F2 NOTIFY+CCCD; `0x60FF`/`0x61FF` expanded, characteristics not yet read from the captured view.
- **FCC (Documented, retrieved 2026-10-25 from fccid.io):** FCC ID 2BEQO-T60 filed 2024-02-06 by **SHENZHEN HAOCHENG TECHNOLOGY CO.,LTD** (grantee 2BEQO), T60 SmartWatch, tested by SHENZHEN TONGCE TESTING LAB; exhibits include internal photos (`Internal-photo-7108448`). Direct PDF download is Cloudflare-blocked; the page-listed mirrors (fcc.id, gov.fccid.io, fccid.ai) are untried.
- **Processor/OS decision table (current state):** SoC vendor = Realtek (**Documented**: RealSil SDK in app, `MANUFACTURER_ID_REALTEK=0x5D`, exact GATT layout match); product line = `BB2` (device-reported byte 0 decoded by the documented parser — **Inferred strong**); exact RTL part number = **Unknown** (no part string in the app; FCC photos pending); RAM/flash = **Unknown**; SDK/toolchain = in-app RealSil protocol (Documented) + vendor build SDK for the exact part (Unknown); existing OS candidates = unchanged (none documented for `BB2`; vendor SDK for the exact part is the leading conditional candidate; Zephyr/ZSWatch incompatible as documented). **OS decision: BLOCKED on the exact part number.**
- **Files changed:** `docs/HARDWARE.md` (corrected the stale "no FFF1 parser"/"source-supported" wording per review `d175f6e`; new "Processor identification — SDK trace and live reads" section with the decision table), `knowledge/graph.json` (updated `fact-fff1`, `inference-fff1-byte0-icid`, `iface-gatt`, `fact-sdk-gatt-map`, `source-fcc`; added `fact-fff1-parser`, `fact-ic-name-map`, `fact-protocol-default`, `fact-fcc-manufacturer`, `fact-live-reads-2` + 17 edges), this handoff.
- **Tests (actually run this session):** `python tools/kg.py validate` → `OK: 51 nodes, 81 edges; references and links valid (offline)`; `python -m unittest discover -s tests -v` → `Ran 5 tests … OK`. Host C build not run (no C code changed).
- **15-minute check limitation (reported per assignment):** this CLI harness cannot run timed background checks. GitHub `main` was checked at session start (at `2ae9057`, no newer review) and will be re-checked immediately before pushing. No background monitoring is occurring.

**Next actions (all within the existing read-only authorization; no new approval needed):** (1) try the FCC mirrors for `Internal-photo-7108448` and read the chip markings; (2) check the T60's BLE advertisement in the nRF UI for Realtek manufacturer data (company ID `0x5D`); (3) enumerate the `0x60FF`/`0x61FF` characteristics and perform justified READs of `0x33F1`/`0x34F1` (stating the identification purpose before each read). Then: final processor/OS report.

## Status — Qwen lane (2026-10-24)

Lead-engineer (Qwen) session started in the local checkout.

- **Workspace initialized:** repo cloned from `pauldavid1974/T60-Custom-OS`; README, AGENTS, HANDOFF, all `docs/`, and the knowledge graph read.
- **Baseline verified green:** HEAD `8de3f02` passes the `verify` CI workflow (graph validate, knowledge-graph tests, host CMake build, ctest) — CI run `40125521186`, 2026-10-24.
- **Local toolchain note:** the working laptop has Python 3.13 but **no CMake and no C compiler**, so the host C build/test runs in GitHub Actions only, not on the machine. Graph checks run locally via `python tools/kg.py validate` and `python -m unittest discover -s tests -v`.
- **Historical assignment:** offline investigation of `0x0B` / `BB2`, the `FFF1` response, recovery comparison, and D-004 OS candidates. The general pass is summarized above and in `docs/HARDWARE.md`; the remaining primary-source lookup is the active Qwen assignment at the top of this file.
- **No device interaction:** the assignment permitted only offline research; no watch operation was performed.

## Status — Claude Code continuation session (2026-10-24)

- **Resume verified:** the only staged change was this handoff (Qwen lane block above); all prior work preserved. `docs/QWEN38_AGENT_GUIDE.md` was missing from the local checkout when first requested; it arrived on origin (commit `7525966`) and was read after the fast-forward below — resolved.
- **Local tests green (2026-10-24):** `python tools/kg.py validate` → `OK: 21 nodes, 26 edges`; `python -m unittest discover -s tests -v` → 5/5 passed. Host C build **not run locally** (no CMake/C compiler on this laptop) — the CI `verify` workflow remains the C check (last green run `40125521186`).
- **Hook audit:** all four active hooks are **user-level** (`~/.claude/settings.json`), so they fire in every project: `context_watch.py` (context-cost gate, fail-open), `herdr-agent-state.ps1` (no-op unless a herdr environment is active), plus two **Astrocade/Qwen-Colab leftovers**: `coop_bootstrap.py` (SessionStart — injected unrelated Astrocade co-op text into this T60 session) and `coop_playtest_gate.py` (Stop — Astrocade playtest checklist). Both leftovers ran fail-open, produced **no errors and blocked nothing**; left enabled, nothing disabled or bypassed. There are **no T60-specific safety hooks** — watch safety rests on the AGENTS.md rules and repo content, both unchanged.
- **Remote reconcile (2026-10-24):** origin had advanced to `6dee449` (Qwen3.8 operating guide, graph additions, agent rules); local HEAD was its ancestor, so a clean fast-forward with no conflicts. Tests re-run green on the merged tree (graph 32 nodes / 41 edges, 5/5 unit tests). The prior handoff notes were committed locally as `0de656e`.
- **Phone/ADB baseline (2026-10-24, read-only):** dedicated phone is a Samsung SM_G781U (serial `RFCNC10FA3K`), ADB-authorized over USB. GloryFit `com.yc.gloryfit` v4.0.12 (versionCode 279) installed; **nRF Connect is NOT installed** on this phone. The T60 is **bonded and connected** on the phone as "T60(ID-AE67)", address `60:1A:B7:00:AE:67` (DUAL BR/EDR+LE; may be a random/resolvable LE address). New GATT lead: custom service UUID `6a24eeab-4b65-4693-986b-3c26c352264f`. Recorded in `docs/HARDWARE.md` and graph node `fact-phone-bond`. Nothing on the phone was changed.
- **Project decision D-004 (owner, 2026-10-24):** investigate existing compatible watch operating systems first; customize one if suitable; develop our own only if the alternatives are inadequate. Recorded in `docs/DECISIONS.md`, new Stage 2 of `docs/ROADMAP.md`, and graph nodes `decision-os-path` / `question-os-base`.
- **No live-device actions taken; no BLE reads/writes, no flashing, no pairing changes.**
- **Next assignment:** the offline `BB2` / IC ID `0B` mapping, `D0FF/FFF1` layout, and recovery-comparison research below, now with the added D-004 deliverable (survey of existing OS options).

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

## COMPLETED ASSIGNMENT — BB2 / recovery / OS research (offline, 2026-10-24)

The general research pass was completed to the limits of available evidence. Its central outcome is that the transcript-reported BB2 mapping is not sufficient to identify an exact RTL part. The active Qwen assignment above is a narrow follow-up to inspect the primary local SDK evidence; it does not authorize a device read. Do not automatically run the proposed FFF3 read.

## Original assignment — Qwen (offline, read-only, no approval required)

Investigate **what Realtek SDK `BB2` / IC ID `0B` actually identifies**, using reliable Realtek source code, the installed GloryFit SDK (if available locally), and manufacturer documentation.

Deliver:
1. Exact code paths or documentation mapping `0B` → `BB2`, and whether `BB2` maps uniquely to any named RTL SoC; include evidence, versions, and uncertainty.
2. Document the `D0FF/FFF1` 13-byte response field layout *only where verified*, clearly identifying unknown bytes.
3. Compare any public Realtek SDK flashing/recovery procedure to the T60's observed interfaces; explain limitations and what would be required to prove recovery safely.
4. Recommend **one smallest next diagnostic**, with exact proposed read-only action, expected benefit, risks, and a clear request for Paul's permission if it touches the live watch.
5. Update **this file**, `docs/HARDWARE.md`, and the relevant nodes/edges in `knowledge/graph.json` with concise findings and sources. Do not remove prior confirmed evidence.
6. **(D-004)** Once the chip family is established, compile a shortlist of existing watch OS/firmware options that could plausibly run on the identified hardware (vendor SDK applications, open-source watch firmware for the same Realtek family), noting licensing, access and customization effort per candidate. Offline research only; no new BLE operations.

**Stop condition:** Once the mapping/recovery research and report are recorded, STOP. Do not initiate any live watch interaction or firmware update.

## Transfer protocol

- At session start: read `README.md`, `AGENTS.md`, `HANDOFF.md`, and `knowledge/README.md`; search the graph for relevant nodes before duplicating research.
- At session end: commit appropriate **source, tests, docs, knowledge graph and this handoff** to `main`. Label **Observed / Documented / Inferred / Unknown**.
- In your final reply to Paul: 3–5 short bullets, commit hash, and an exact next action requiring owner approval (if any).
- ChatGPT and Qwen coordinate by reading and updating the same repository; **no outside reviewers, mandatory independent approval, or added collaborators**.
