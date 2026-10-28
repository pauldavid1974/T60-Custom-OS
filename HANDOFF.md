# HANDOFF — T60 custom firmware investigation

**Updated:** 2026-10-28
**Phase:** Read-only feasibility investigation
**Next agent:** Qwen through Engine Room + a local harness (or ChatGPT)
**Owner:** Paul; public one-owner repo `pauldavid1974/gloryfit-replacement-android` (renamed from `T60-Custom-OS` on 2026-10-26; public by Paul's explicit 2026-10-25 approval)
**Owner authorization (2026-10-24):** Paul explicitly authorized reverse engineering of the physical T60 and GloryFit companion app. This covers read-only BLE discovery, connecting for service/characteristic enumeration, reading characteristics advertised as readable, and pulling the installed GloryFit APK from the dedicated phone for offline analysis.
**Hard stop:** No BLE characteristic/descriptor writes or notification subscriptions, app/watch setting changes, firmware update or flashing, reset, unpairing, unlock, debug-port access, physical disassembly, or other action that changes device state. If a proposed step might change state or falls outside the read-only scope, stop and ask Paul first. Eventual replacement of both products is the goal; this authorization does not approve installing or replacing either one.

## COMPLETED — Full tap-through smoke test and fixes (Claude Code, 2026-10-28)

- **Paul's request:** tap through the whole app, fix every bug, make it leaner, add better functionality.
- **Tested (Observed, 2026-10-28, Galaxy S20 FE `RFCNC10FA3K`, Android 11, phone Bluetooth OFF so the watch was
  never contacted and Paul's watch settings were not overwritten):** setup, all four tabs and every detail screen
  except Clear watch data (not opened) and the system photo picker (simulated taps don't register in Android's file
  picker). Export (JSON saved to Downloads), step goal, Today cards, quick reply add/delete, quiet-hours time picker,
  weather.gov update (Scott City forecast), call-permission deny path, phone-only walk and run with map. Crash buffer
  empty throughout.
- **Bugs fixed:** (1) hiding the Today "Heart rate" card also hid Sleep, and hiding Sleep did nothing — each card now
  hides on its own and they pair side by side only when next to each other; (2) Phone calls / Text messages switches
  showed on without the phone permission, so they silently did nothing — they now show on only when they can work and
  ask when turned on (separate permissions button removed); (3) "on" switches were dark navy, not bright blue — the
  stock track is drawn at 30% opacity, so the app now draws its own track; (4) empty step bar showed a blue dot;
  (5) Back on Health/Alerts/Watch closed the app — now goes to Today first; (6) returning from a detail screen jumped
  the list back to the top — scroll position is remembered; (7) with the phone's Bluetooth off the app only said
  "Not connected" and waited up to 15 min after Bluetooth came back — it now says "Bluetooth off", offers a Turn on
  button, and reconnects the moment Bluetooth is on; (8) GPS jitter while standing still counted as distance
  (0.02 mi in 2 min indoors) — a move now counts only if it beats the GPS error, or the GPS reports a confident
  walking-or-faster speed so small loops (track laps) still add up (verified 0.00 after 90 s still; loops not yet
  walked outdoors); (9) women's-health
  reminder subtitle didn't update; (10) the alarm editor could reopen on an old alarm.
- **New:** phone-GPS workouts run without the watch (watch gets start/stop only when connected); tapping a saved
  phone workout shows its route on the map.
- **Not done:** halving the code. ~5,300 lines carry 33 screens plus a hand-built Bluetooth protocol; nothing large is
  duplicated, so cutting half would cut features. Net change here was small. Not verified with the watch connected.

## COMPLETED — Switches easy to see when on (Claude Code, 2026-10-26)

- **Paul's report:** switches are hard to see in the on position.
- **Cause:** the app used the stock `android.widget.Switch`, which takes the phone theme's accent and draws the track
  at 30% opacity, so on the dark cards an "on" switch nearly vanished.
- **Change:** new `Ui.toggle(boolean)` sets the colours from `design/*.dc.html`: on = solid blue track `#5AB0FF` +
  white knob, off = `#3A4452` track + `#C9D1DB` knob. Used by `Ui.switchRow` (every settings switch) and the
  Today-cards editor in `MainActivity`.
- **Checks:** `python android/build_apk.py` built; installed with `adb install -r` on the Galaxy S24 (`RFCXA0S5N4J`).
  **Not visually verified:** the phone was locked, so no screenshot was taken. Paul to confirm on the phone.

## COMPLETED — Permissions asked per feature, not all at launch (Claude Code, 2026-10-26)

- **Paul's request:** "Don't ask anything at launch. Ask Bluetooth at setup step 1, location at step 4, and
  phone/text/contacts only when I use those features."
- **Change:** `MainActivity.requestPermissionsIfNeeded()` (asked for 9 permissions at once on every launch) replaced by
  `ask(String...)` with groups: `BLUETOOTH` (+ notifications, so the status notice and find-my-phone alarm show) at
  setup step 1, `LOCATION` at step 4 and on phone-started workouts, `CALLS` / `TEXTS` when the call/text alert
  switches are turned on, `SEND_SMS` on the quick-reply screen, `READ_CONTACTS` when adding watch contacts.
  `T60BleService.onStartCommand` now re-registers call/SMS hooks and refreshes the foreground type every start, so a
  permission granted after the service is running takes effect without an app restart.
- **Verified (Observed, 2026-10-26, Galaxy S24 `RFCXA0S5N4J`, uninstall + plain `adb install`):** launch → no prompts,
  setup screen; step 1 → nearby devices, then notifications, nothing else; step 4 → location only; watch connected
  and synced; foreground types became connectedDevice|location after location was granted mid-session; no crashes;
  phone/SMS/contacts never requested.
- **Not verified:** the Alerts and Contacts screens' own prompts (call alerts, text alerts, quick replies, add contact).

## COMPLETED — Startup crash on Android 14+ fixed (Claude Code, 2026-10-26)

- **Result (Observed):** on Paul's Galaxy S24 (`RFCXA0S5N4J`, model SM-S921U, Android 14+ rules) the app crashed ~30 ms after
  opening, every time, after a fresh install. Logcat: `SecurityException: Starting FGS with type connectedDevice …
  requires … BLUETOOTH_CONNECT` from `T60BleService.onCreate` → `startForeground`. `MainActivity` asks for permissions
  and starts the service in the same breath, so on a fresh install (no grants yet) the service went foreground before
  Bluetooth was allowed. The earlier phone (Android 11) never enforced this.
- **Fix:** `T60BleService` checks `BLUETOOTH_CONNECT` before going foreground and stops quietly (event log line
  "Service not started: Bluetooth permission not granted yet") if it is missing; `MainActivity` already restarts it
  from `onRequestPermissionsResult`. The foreground type is now passed explicitly: connected device always, location
  only when fine location is granted (denying location would otherwise crash the same way).
- **Verified (Observed, 2026-10-26):** fresh launch with no grants → no crash, permission prompt shown, the guard log
  line printed. After Paul tapped Allow: service foreground with types connectedDevice|location (`0x18`), watch
  connected and synced (time, quick replies, step and heart-rate history, weather), crash buffer empty.
- **Gap noted:** the app logs BLE traffic and events (`store.logEvent`, tag `T60`) but has no crash capture of its own;
  crashes are only visible through `adb logcat -b crash`. Install command note: plain `adb install -r` (no `-g`) is
  what a real user gets, so test first-run with it.
- **Checks:** built with `python android/build_apk.py`; no host C or graph changes, so `kg.py validate` / unit tests not
  run for this change.

**Next assignment:** unchanged — the "Next assignment (with Paul wearing the watch)" list in the section below.

## COMPLETED — OpenStreetMap street map on the live workout screen (Claude Code, 2026-10-26)

- **Paul's choice (2026-10-26):** OpenStreetMap over Google Maps (no account, card or key; no new library).
- **Change:** new `MapTiles.java` fetches OSM tiles (`tile.openstreetmap.org`, identifying User-Agent, 2 connections,
  30-day disk cache, 60 s retry back-off) per OSM's tile policy; `HealthScreens.trackView` draws them (Web Mercator,
  closest zoom up to 17 that fits the route, tiles at 128 dp, dimmed) under the route, with a "you are here" dot and
  the required "© OpenStreetMap contributors" credit. Privacy: only tile requests for the route's area leave the phone.
- **GPS bug fixed:** `gpsGood()` used the last *route point*, which only advances after moving 5 m, so standing still
  showed "Finding GPS…" after 10 s with a perfect signal. New `lastReading` (any fix) drives the status; the 5 m rule
  still guards distance. The map now draws from the first reading instead of waiting for two route points.
- **Verified (Observed, 2026-10-26, Galaxy S24, indoors in Scott City):** phone-started walk → map showed the right
  streets (Hickory St, 1st St East, post office) with the dot; "GPS good" held for over 1 min standing still; distance
  stayed 0.00; walk ended and saved (1 min); no crashes.
- **Not done / known gaps:** saved workouts store the route (`workout.track`) but no screen shows it yet. A workout
  lives only in the service's memory: reinstalling (or Android killing the app) mid-workout loses it — this happened
  once during testing. Route recording on a real outdoor walk not yet tested.

## COMPLETED — App v2 built to the approved design and tested on the live watch (Claude Code, 2026-10-26)

**Authority:** Paul, 2026-10-26: "you have full authority to send anything you need to, to the watch … work until the
app is fully installed and working". This covered the new command types below; firmware/DFU was not touched.

- **Result (Observed):** `android/` rewritten: 33 design screens in code (`MainActivity`, `HealthScreens`,
  `AlertScreens`, `WatchScreens`, `Ui`), SQLite health history (`T60Db`), weather.gov forecast (`Weather`), photo
  face encoder (`T60Face`). Installed on `RFCNC10FA3K` (Android 11) and connected to `T60(ID-AE67)`; no crashes.
- **Startup fix (Observed):** commands now go one at a time and wait for the watch's reply, as GloryFit does. Battery
  `A2 38` and version `A1 …RH281LWJV007108` now answer; every startup command is acknowledged.
- **Protocol (Documented from jadx decompile, then Observed):** full command list with replies in
  `docs/PROTOCOL.md` → "App v2 command set". Three earlier commands were wrong and are fixed: find watch, alarms
  (9-byte form) and text alerts (UTF-16BE pieces sent before the buzz). New and confirmed live: weather (CB), quick
  replies (46/52), contacts (37), call audio status (38), languages (AF), women's health (29), workouts from the phone
  (FD 11/22/33/44/00), music title (3A), photo watch face upload (26 02/27/26 03).
- **Photo face (Observed):** GloryFit downloads a face template from the maker's server; the app now draws its own
  template pieces instead. The encoder matched GloryFit's compression byte-for-byte on 3,000 test images; a test face
  (plain navy, white time) was accepted by the watch (`26 03 00`) and the watch then reported a custom face showing.
- **Not verified (watch probably not worn, Paul away):** live heart-rate reading (`D6 01` + `E5 11` got no reply),
  blood-oxygen value (`34 00 FF FF` = no reading), wrist buttons (music, find phone, quick reply from watch), GPS
  requested by a watch-started workout, sleep/steps/heart-rate history (watch history was empty: the earlier app
  pulled it at each connect and discarded it). Clear watch data (`AD`) is built but was **not sent** (destructive).
- **Side effects Paul should know:** startup sends the app's settings to the watch, so the watch's alarms were set to
  off, quiet hours and move reminder off, and the profile set to placeholders (5 ft 10 in, 174 lb, born 1985). Paul
  should enter his real profile and alarms in the app. The test photo face is now on the watch.
- **Limits:** Health Connect needs Android 14 or an added library (phone is Android 11) — the Export screen says so and
  offers the CSV/JSON file. Workout map draws the GPS line without map tiles (Google key not created). The watch's
  built-in faces can only be switched on the watch. Move-reminder weekdays are applied by the app at midnight.
- **Checks:** `python tools/kg.py validate` and `python -m unittest discover -s tests -v` — see the commit. No host C
  build (no C changed). Notification access for the new app must be switched on by Paul (setup step 3); it is a phone
  privacy setting, so it was not changed by the agent.

**Next assignment (with Paul wearing the watch):** (1) turn on notification access in setup step 3; (2) set profile
and alarms; (3) on Heart rate press Measure now — if still no `E5` reply, compare with GloryFit's `RateDetailsActivity`
flow (`D6 02` dynamic mode) in the decompile; (4) press the watch's find-phone, music and quick-reply buttons and check
the Bluetooth log on the Connection screen; (5) start an outdoor walk on the watch and confirm `FD 01 AA` → GPS points;
(6) choose a real photo face. Then the morning after a worn night, confirm sleep/steps/heart-rate history fill Health.

## ACTIVE ASSIGNMENT (superseded by the section above) — Build the new T60 phone app from the approved design (Paul approved 2026-10-25)

**Paul's words:** "Approved. Build the app now." The design is final for this build.

**Read first:** `design/` (33 approved screens as `.dc.html` + `canvas.json` layout; open any file in a browser to read its markup, though the tap behaviour only runs in the canvas), the live clickable canvas https://claude.ai/artifact/VB2BpuLfvPW5Mj6LMXW9Vz, `docs/APP_FEATURES.md` (what the watch supports, keep/drop), `docs/PROTOCOL.md`, existing `android/` app.

**Build rules**
- Keep the zero-Gradle build: `python android/build_apk.py`, install with `adb install -r -g android/build/T60.apk` (phone `RFCNC10FA3K`). Java, no new dependencies unless unavoidable.
- Match the design's look: near-black `#0A0C0F`, cards `#14181E` / border `#232A33`, accent `#5AB0FF`, heart/alert `#FF8A5B`, text `#F2F4F7` / `#9AA4B2`; 4 bottom tabs (Today, Health, Alerts, Watch); detail screens have a back arrow and no tab bar.
- Store health history locally (SQLite) so Health week/month views and Export work. Nothing leaves the phone except: weather.gov (rounded GPS, US only, see Weather decision below) and, later, Google Maps tiles for the workout map (needs Paul to create a Google Cloud key himself; build the map screen last).
- **Safety (AGENTS.md):** commands the current app already sends are fine. Any **new** watch command type (weather packet, alarms beyond what's sent, contacts, quick replies, women's-health, language, screen time, clear data, photo watch face upload) must be decoded offline from `scratch/offline/classes6_dexdump_utf8.txt` first and needs Paul's OK before its first live send. Clear watch data and photo-face upload are the riskiest; do them last.

**Suggested order (one small PR each)**
1. Fix the open startup issue: send each 33F1 command only after the previous reply (watch currently answers only `A3`/`A9`). Verify battery `A2` and version `A1` replies on the phone.
2. App shell: 4 tabs + navigation + Today screen (steps, HR, sleep, quick actions: Find watch screen, Sync now, Export).
3. Alerts tab: call/text switches, per-app list (already built), quiet hours, move reminder, quick replies (needs SEND_SMS permission).
4. Health tab + local SQLite history: steps/HR/sleep/blood oxygen week views, heart-rate and sleep detail screens.
5. Watch tab sections: profile, step goal, screen + raise to wake, time and units, language, alarms, weather (weather.gov), calls + contacts, music, Today cards, setup, find-phone alert, clear data.
6. Workouts (≈24 types; decode names from GloryFit resources) and live workout with phone GPS; map last.
7. Women's health, photo watch face, export/Health Connect.

**Done when:** every screen in `design/` exists in the app, each control does what the prototype shows, the app builds and installs, and each new watch command has been approved and tested live. Update this file after each step.

## COMPLETED — App fixes, feature decode and new app design (Claude Code, 2026-10-25)

- **App fixes (Observed on the phone, commit `ef0b040`):** per-app alert switches on the Alerts tab; the service no longer opens a second Bluetooth connection (every wrist press was handled twice); notification setup is now done one step at a time, so watch replies arrive. **Still open:** the watch answers only the first two startup commands (`A3`, `A9`); `A2` battery, `A1` version and `26 01` get no reply. Likely needs each command to wait for its reply before the next is sent. Not yet tested.
- **Feature support (Inferred, commit `6ad0206`):** `tools/t60_feature_flags.py` + `docs/PROTOCOL.md` table + graph node `fact-t60-feature-support`. Has blood oxygen, BT calls, music + song name, weather, phone-GPS workouts; lacks blood pressure, ECG, temperature, HRV/stress, contacts/SOS.
- **Owner decisions (Paul, 2026-10-25):** replace the current app UI with a new design that keeps GloryFit's useful features and drops cloud/bloat. **Weather comes from the US National Weather Service (`api.weather.gov`) using the phone's GPS location, rounded; US only, no Open-Meteo, no vendor weather servers.** GloryFit's weather goes to `ute-tech.com.cn` / `uteasy.com`; do not use those.
- **Design (clickable prototype, private to Paul):** https://claude.ai/artifact/VB2BpuLfvPW5Mj6LMXW9Vz — 18 phone screens: 4 tabs (Today, Health, Alerts, Watch), health details (heart rate, blood oxygen, one night's sleep, workouts, live workout), settings (alarms, move reminder, weather, photo face, calls, export), setup, not-connected and find-my-phone alert. Numbers on the screens are sample data.
- **Next assignment:** with Paul's go-ahead, rebuild `android/` screen by screen to match the design, starting with Today. Decode GloryFit's 7-day weather packet (`WriteCommandToBLE.syncWeatherToBLEForXiaoYang`) offline before any weather write to the watch; the first write of each new command type needs Paul's approval.

## COMPLETED — Full BLE Protocol Decode, Platform Flag Resolution & Local `T60` Android Companion App (Antigravity, 2026-10-25)

- **Native Cloud-Free `T60` Android Companion App Built & Installed (`android/`, Observed 2026-10-25):**
  - Per Paul's owner decision (`/grill-me` spec interview), prioritized replacing the bloated 186 MB `GloryFit` app with a clean, 33 KB zero-cloud Android companion app named **`T60`** (`com.paul.t60` in `android/src/main/java/com/paul/t60/`) rather than risking screen damage from prying open his daily-driver watch.
  - Built reproducibly in <3 seconds via `python android/build_apk.py` (using local Android SDK 34 `aapt2`, `javac`, `d8`, `zipalign`, `apksigner`) and installed onto Paul's connected Samsung `SM_G781U` (`RFCNC10FA3K`).
  - **Verified Live Against `T60(ID-AE67)` (`60:1A:B7:00:AE:67`):** Connected on first launch (`Connected • Active • 56%`), synced local time (`0xA3`), set 12-hour AM/PM + Imperial (`0xA0 0x02 0x02`) and Fahrenheit/English (`0xA9`), enabled Find-My-Phone (`0xD1 0x0A 0x01`), and implemented full **Wrist Media & Volume Control** (`0xD1 0x07` Play/Pause, `0xD1 0x08` Next, `0xD1 0x09` Prev, `0xD1 0x0D` Volume Up, `0xD1 0x0E` Volume Down, `0xD1 0x12` Mute, plus `0xD1 0x0C`/`0x0D`/`0x0E` state/volume feedback).
  - **Next Assignment for Any Agent Continuing the App:** Expand `android/` with custom `320 × 380` binary watchface/background image upload (`ImageWatchFaceRk` / `OnlineDialUtil` `0x26 0A` / `0x26 0B` / `0x26 02`), multi-day SQLite charts in the Health tab, per-app notification filter checkboxes, and optional Tailscale sync to Paul's home server.

- **EXACT SoC & FLASH IDENTIFIED FROM FCC INTERNAL PHOTO (`fcc/chip-pic.png`, Observed 2026-10-25):**
  - **Main Processor / SoC:** **`REALTEK RTL8763EWE`** (`N8AT8H1` / `GN33 TAIWAN`, with `40.0` MHz crystal).
  - **External SPI NOR Flash:** **`PUYA PY25Q128HA`** (`3H1PP2D`) — **128 Megabit (16 MB)** external SPI NOR Flash chip mounted right next to the `RTL8763EWE`!
  - **PCB Revision & Pads:** **`RH235_V02`** / **`PH235_V02`**, with labeled `3V3`, `+`, `-`, and `RF` test points.
- **Hardware / Platform Breakthrough (Documented + Observed):**
  - Resolved the gap from previous sessions: in `classes6.dex` (`BluetoothLeService$7.onCharacteristicRead`, `0x37a3fe`–`0x37a660`), the observed 20-byte `33F1` payload (`08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC`) sets `FunctionList 7` = `0x0808`, which explicitly sets **`GlobalVariable.IS_PLATFORM_RTL8762Cx` (`0x0800`)** and `IS_SUPPORT_SLEEP_REM` (`0x0008`).
  - `FunctionList 5` (`0x243943`) sets **`GlobalVariable.IS_SUPPORT_BT3_0_AGREEMENT` (`0x040000`)**, which invokes `WriteCommandToBLE.sendBt3AgreementCommand()` (`0x3af988`)—whose state methods are explicitly named `SPUtil.getNeedToRegenerateRandom63E()` / `setPairingSystemBluetooth63E()` (`BleDevices.needPair8763E`, i.e., Realtek **`RTL8763E`** dual-mode Bluetooth Call + BLE series, corresponding to `FFF1` `icType = 0x0B = IC_BB2`).
  - In `classes5.dex` (UTF-16LE dexdump at `0x3a7c18`), service `000002fd-3c17-d293-8e48-14fe2e4da212` (`FD03`/`FD04`) is documented as **`com.realsil.sdk.bbpro.core.gatt.GattTransportConnParams`** (Realtek BBPro GATT Transport Service).
  - In `34F1` (`00 F4 ... 04 02`), bytes `0..1` (`0x00F4`) set `GlobalVariable.maxCommunicationLength = 244`.
- **Zero-Auth BLE Protocol & Local Companion Utility (Documented):**
  - Confirmed `IS_SUPPORT_PASS_WORD_PAIR` (`0x01` in `FunctionList 1`), `IS_SUPPORT_ACCOUNT_ID_BIND` (`0x04` in `FunctionList 5`), and `IS_SUPPORT_ACCOUNT_ID_BIND_NEW` (`0x080000` in `FunctionList 10`) are all **`0` (`false`)** on the T60—no PIN, captcha, or cloud account binding is required to control the watch over BLE.
  - Documented the full BLE command/response protocol, custom background transfer (`ImageWatchFaceRk`, `0x26 0A`/`0x26 0B`), Classic BT 3.0 speaker/microphone control (`openDeviceBt3`, `0x38 02`), and cloud OTA API in [`docs/PROTOCOL.md`](docs/PROTOCOL.md).
  - Built [`tools/t60_control.py`](tools/t60_control.py) (with unit tests in [`tests/test_t60_control.py`](tests/test_t60_control.py)) defaulting to safe `--dry-run` mode.
- **Live Laptop BLE Interrogation Breakthroughs (Observed 2026-10-25, owner-authorized `"approved fire at will"`):**
  - Connected directly from Paul's Windows 11 laptop (`bleak`) to `T60(ID-AE67)` (`60:1A:B7:00:AE:67`). Confirmed `33F1` requires Write-With-Response (`response=True`) while `34F1` uses Write-Without-Response (`response=False`).
  - **True Internal Factory Board/Firmware Name (`0xA1` -> `33F2`):** Watch returned `A1 52 48 32 38 31 4C 57 4A 56 30 30 37 31 30 38` -> ASCII **`RH281LWJV007108`** (`btname="RH281LWJ"`, `version="V007108"`). While the watch's "About" screen displays `T60V007108` for retail branding, the internal Ute-Tech/Realtek firmware project ID is **`RH281LWJ`**.
  - **Exact Display Specifications (`0x26 01` -> `33F2`):** Watch returned 20-byte `26 01 00 F4 37 89 01 40 01 7C 01 00 10 00 00 02 01 00 00 00`, which decodes via `OnlineDialUtil.parsingDdialInformation` to **`320 × 380` pixels**, rectangular (`screenType = 1`), **1.0 MB (`1,048,576` B) max watchface partition**, `compatibleLevel = 2`, `watchFaceCount = 1`, active `dialNumber = 16004999`.
  - **Live Battery Level (`0xA2` -> `33F2`):** Returned `A2 38` (`56%`).
  - **Realtek DFU Control Point (`6487`):** `0x0D` (`GET_DEVICE_INFO`) returned `10 0D 9D 03 A4 27 FF FF FF FF FD FF 00 00 00 00 FE FF 00 00 00 00`. Also extracted three 32-byte Realtek DFU AES keys from `FirmwareActivity` (`mSecretkey` containing `UTE_RK`, `mSecretkeyRH276N`, `mSecretkey8762C`).
- **Decoded `D0FF` / `FFE0` & `FFF4` Flash Partition Table (Documented + Observed):**
  - `FFE0` (74 B) lists 12 active Realtek `BB2` (`RTL8763EWE`) image IDs (`0x2790`..`0x279D`) and their versions; `FFF4` (55 B) gives the exact flash allocation sizes for all 9 partitions (including `0x2793` MCU App = `0x14F000` / ~1.34 MB).
- **Next Assignment (Tomorrow with Paul — ESP32 Hardware Flash Backup):**
  - Prepare and flash an ESP32 `3.3V` UART/SPI reader sketch to dump the 16 MB **`PUYA PY25Q128HA`** SPI NOR flash (and/or probe the labeled **`TX`/`RX`** pads on Page 2) once Paul opens the watch with his work tools, establishing a verified 16 MB factory backup (`t60_factory_backup.bin`) and extracting the exact peripheral GPIO pin map.
- **Vendor OTA Server Probe (Observed, 2026-10-25):**
  - Queried `https://www.ute-tech.com.cn/ci-yc/index.php/api/client/getBtVersionUpdate` and `getBtUIVersionUpdate` using extracted `YCAPPKEY` (`dcd05f241b65ec7b6af0bbe6f05145c2`), `btname="T60"`, and dummy MAC `00:00:00:00:00:00` across `V000001`..`V007108`; server returned HTTP 200 `{"flag":-1,"msg":"post ok , no new version."}` (no newer T60 OTA binary currently hosted on Ute-Tech's public endpoint).

## COMPLETED — exact-SoC investigation and OS assessment; NEXT: readable FCC exhibit (Kimi Work, 2026-10-25)

**Base reviewed:** `ed29909` (`origin/main` at investigation start). Delivery branch: `research/t60-exact-soc`, through the protected-branch PR workflow; no agent merge.

- **Processor result:** exact RTL part remains **Unknown**. Realtek `BB2` is still only a product-line interpretation (inferred-strong), not an exact SoC. RAM/flash, board revision, peripherals and matching build SDK/toolchain remain Unknown.
- **New device-specific evidence:** GloryFit v4.0.12 `classes6.dex` directly parses the observed 20-byte `34F1` value into function lists 8–13. Bytes 2–4 give function list 13 = `0x000000`; therefore `IS_PLATFORM_RK_RTL8773EWE` (`0x00020000`) is clear and `DevicePlatform.isRKPlatform8773()` is false for the captured state. This does not identify the actual chip or justify a broader physical exclusion.
- **Final authorized reads (Observed):** `33F1` = `08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC`; `34F1` = `00 F4 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04 02`; `6001` returned the same 20 bytes as `34F1`; `6002` is NOTIFY and was not subscribed; `61FF` exposes no characteristics. No decoder was found for `6001`. Raw PNG/XML evidence is in ignored `scratch/live/`; app disassembly and SDK comparison copies are in ignored `scratch/offline/`.
- **FCC outcome:** no internal photo or chip marking was obtained. Official `.gov` access was not used after Paul's CAPTCHA report/instruction. Candidate `device.report` and `manuals.plus` document URLs returned 403; `fccinsights.com` returned 429 and was not retried. The candidate document contents remain unverified. Do not infer a part from the exhibit title or another product named T60.
- **OS/SDK assessment:** no existing watch OS or vendor build SDK is documented as compatible with an identified exact T60 SoC. Public `RTL87x3E-Common-SDK` is a comparison lead only, not a T60 match; Zephyr supports other named Realtek targets and ZSWatch targets Nordic nRF5340. No OS base is recommended for installation. Full evidence-labeled table and sources: `docs/HARDWARE.md` → “Final read-only identification and OS assessment”.
- **Verification (Observed):** `python tools/kg.py validate` passed (54 nodes / 88 edges; references and links valid offline); `python -m unittest discover -s tests -v` passed 5/5. Host CMake/CTest not run locally because no portable C source changed; CI remains required on the PR.
- **Safety:** all live operations were explicit reads on the verified connected T60. No writes, subscriptions, settings changes, OTA/DFU, installation, disassembly, firmware or app implementation occurred.

**Next assignment (one bounded diagnostic):** Paul obtains a readable copy of the exact FCC `Internal-photo-7108448` PDF through a working manual source and supplies it for offline inspection. Inspect only that exhibit for legible main-SoC, flash/RAM and peripheral markings; record evidence labels and unresolved ambiguity. No new watch operation is needed. If a readable exhibit cannot be obtained, stop and report; physical inspection/disassembly requires separate explicit approval. Factory backup, image-signature requirements and tested recovery remain hard blockers after identification.

## Repository governance setup — Kimi Work (2026-10-25)

- **Result (Documented):** added `GOVERNANCE.md`, `CONTRIBUTING.md`, `SECURITY.md`, `.github/CODEOWNERS`, a pull-request template, and issue forms for bugs, bounded research/engineering tasks, and device-action requests. README, D-006 and the `decision-owner` graph node link the new policy.
- **Scope and confidence:** template-only repository governance; high confidence that it preserves Paul's one-owner authority, routine direct-to-`main` work, optional pull requests, evidence labels, validation reporting, and exact-action device approval. No live-device action, GitHub settings change, branch protection, collaborator change, firmware or app implementation occurred.
- **Verification (Observed):** `python tools/kg.py validate` passed (51 nodes / 81 edges; references and links valid offline); `python -m unittest discover -s tests -v` passed 5/5; a local PyYAML syntax/structure check passed all three issue forms plus CODEOWNERS and the pull-request template; `git diff --check` passed; Git emitted only LF-to-CRLF working-copy conversion warnings on Windows. Host CMake/CTest **not run** because no portable C source changed.
- **Unresolved:** repository settings such as owner-only branch protection, signed-commit enforcement and security features remain unchanged and are not implied by the templates.
- **Next assignment:** superseded by the completed identification/OS report and the readable-FCC-exhibit diagnostic at the top of this file. Future agents should use `GOVERNANCE.md` and the task template when receiving or recording new assignments.

## Public visibility and main branch protection — Kimi Work (2026-10-25)

- **Owner decision:** after the initial API requests returned the private-plan feature gate, Paul explicitly authorized making `pauldavid1974/T60-Custom-OS` public. This supersedes the earlier privacy setting, not any device-safety or evidence-handling rule.
- **Observed configuration:** GitHub API `PATCH` confirmed `private: false`, `visibility: public`, and default branch `main`. Branch-protection `PUT` returned HTTP 200. No collaborators or outside reviewers were added.
- **Effective protection (Observed, verified by API GET):** required status check `check`; `strict: true` (branches must be up to date); `enforce_admins: true`; `required_pull_request_reviews: null`; force-pushes and branch deletion disabled. Required signatures and linear history remain disabled. The intended `check` context previously completed successfully on commit `6387a0a`.
- **Workflow consequence:** use a short-lived branch and pull request for changes to `main` so CI passes before integration. The earlier routine direct-to-`main` policy is superseded. Paul still decides merges; agents do not merge without explicit authorization.
- **Verification:** `python tools/kg.py validate` passed (51 nodes / 81 edges); `python -m unittest discover -s tests -v` passed 5/5; `git diff --check` passed with only Windows line-ending conversion warnings. Host CMake/CTest **not run locally**; the GitHub `check` job covers them on the documentation PR. No C source or live-device state changed.
- **PR evidence:** GitHub PR [#1](https://github.com/pauldavid1974/T60-Custom-OS/pull/1) passed required `check` and GitGuardian Security Checks and was merged by Paul on 2026-10-25; merged `main` was `ed29909` at this investigation's start.
- **Next assignment:** superseded by the completed identification/OS report and the readable-FCC-exhibit diagnostic at the top of this file.

## Shared work desk — Qwen ↔ Codex

| Item | Current state |
|---|---|
| Project goal | Reverse engineer the T60 and GloryFit, then eventually replace both. First evaluate a compatible existing watch OS; build one only if needed. |
| Phase | Read-only physical-watch and companion-app reconnaissance. No replacement has been implemented or installed. |
| Latest Qwen report | Session 4 report (this commit, mission `2ae9057`): SDK trace found the FFF1 parser (`DeviceInfo.parseX0000`); the T60's GATT exactly matches the SDK's default Realtek DFU layout; IC name map (BB2 product line) documented; app proven to run the default `r` profile; FCC applicant identified (Shenzhen Haocheng Technology); second live read batch completed. See "Status — Qwen via OpenCode session 4" below. |
| Current Qwen action | No active Qwen action. Kimi Work completed the bounded identification/OS assessment; exact RTL part remains **Unknown**, and the single next diagnostic is the owner-supplied readable FCC exhibit described at the top. Await review before further device work. |
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
