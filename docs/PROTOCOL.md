# T60 BLE & Audio Protocol Reference (GloryFit v4.0.12 Offline Reverse-Engineering)

All findings in this document are **Documented** from offline disassembly of `com.yc.gloryfit` v4.0.12 (`classes5.dex` and `classes6.dex`) and cross-referenced with **Observed** read-only GATT captures from Paul's `T60(ID-AE67)` (`60:1A:B7:00:AE:67`, firmware `T60V007108`).

---

## 1. GATT Service & Characteristic Map

| Service UUID | Characteristic UUID | Properties | Role in GloryFit / Realtek SDK |
| :--- | :--- | :--- | :--- |
| `0x55FF` (`000055ff-0000-1000-8000-00805f9b34fb`) | `0x33F1` | READ, WRITE | Primary command channel (`WriteCommandToBLE.writeChara`) & capability bitmasks `FunctionList 1..7` (20-byte read) |
| `0x55FF` | `0x33F2` | NOTIFY (`0x2902`) | Primary response/telemetry channel (`DataProcessing`) |
| `0x56FF` (`000056ff-0000-1000-8000-00805f9b34fb`) | `0x34F1` | READ, WRITE NO RESPONSE | High-throughput BLE5/dial channel (`WriteCommandToBLE.writeCharaBle5`), `maxCommunicationLength` (`0x00F4` = 244 bytes), & `FunctionList 8..13` |
| `0x56FF` | `0x34F2` | NOTIFY (`0x2902`) | High-throughput response/notification channel |
| `0x60FF` | `0x6001` / `0x6002` | READ+WRITE / NOTIFY | Secondary companion service (`6001` mirrors `34F1` 20-byte capability payload) |
| `0x180F` | `0x2A19` | READ, NOTIFY | Standard BLE Battery Level (`0..100%`) |
| `0000d0ff-3c17-d293-8e48-14fe2e4da212` | `0xFFD1..0xFFF5` | READ / WRITE NO RESP | Realtek DFU / DeviceInfo service (`FFF1` = 13-byte `DeviceInfo`, `icType = 0x0B = IC_BB2`; sets `GlobalVariable.isRKPlatform = true`) |
| `00006287-3c17-d293-8e48-14fe2e4da212` | `0x6387` / `0x6487` | WRITE NO RESP / NOTIFY | Realtek Silent OTA DFU service (`UUIDUtils.OTA_SERVICE_RK_SLIENT_MODE_UUID`; sets `GlobalVariable.isRKOTASlientMode = true`) |
| `000002fd-3c17-d293-8e48-14fe2e4da212` | `0xFD03` / `0xFD04` | WRITE / NOTIFY | Realtek BBPro GATT Transport Service (`com.realsil.sdk.bbpro.core.gatt.GattTransportConnParams`) used on BB2/BBPro dual-mode audio+BLE SoCs |

---

## 2. Capability Bitmasks (`33F1` and `34F1`) & Zero-Auth Proof

### 2.1 `33F1` 20-Byte Read (`08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC`)
Parsed in `BluetoothLeService$7.onCharacteristicRead` (`classes6.dex` `0x37a3fe`–`0x37a660`) from right to left:

- **`FunctionList 1` (bytes `17..19` = `0x4BE1DC`):**
  - `IS_SUPPORT_PASS_WORD_PAIR` (`0x000001`) = **`0` (clear)**
- **`FunctionList 2` (bytes `14..16` = `0x005F78`)**
- **`FunctionList 3` (bytes `11..13` = `0xFED921`)**
- **`FunctionList 4` (bytes `8..10` = `0x756FFF`):**
  - `IS_SUPPORT_BAND_SLEEP_ALGORITHM` (`0x040000`) = **`1` (set)**
- **`FunctionList 5` (bytes `5..7` = `0x243943`):**
  - `IS_SUPPORT_ACCOUNT_ID_BIND` (`0x000004`) = **`0` (clear)**
  - `IS_SUPPORT_BT3_0_AGREEMENT` (`0x040000`) = **`1` (set)** (enables Classic BT 3.0 speaker/mic one-key pairing via `sendBt3AgreementCommand`, which tracks `RTL8763E` state via `SPUtil.getNeedToRegenerateRandom63E`)
- **`FunctionList 6` (bytes `2..4` = `0x442A01`)**
- **`FunctionList 7` (bytes `0..1` = `0x000808`):**
  - `IS_SUPPORT_SLEEP_REM` (`0x0008`) = **`1` (set)**
  - **`IS_PLATFORM_RTL8762Cx` (`0x0800`) = `1` (set)**
  - `IS_PLATFORM_RTL8762Ax` (`0x0200`), `IS_PLATFORM_NRF` (`0x0400`), `IS_PLATFORM_FRK` (`0x2000`), `IS_PLATFORM_JX_3085L` (`0x8000`) = **`0` (clear)**

### 2.2 `34F1` 20-Byte Read (`00 F4 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04 02`)
Parsed in `BluetoothLeService$7.onCharacteristicRead` (`classes6.dex` `0x37a6f8`–`0x37a940`):
- **Bytes `0..1` (`0x00F4`):** `GlobalVariable.maxCommunicationLength = 244` bytes.
- **`FunctionList 8` (bytes `17..19` = `0x000402`)**
- **`FunctionList 9..13` (bytes `2..16` = `0x000000`):**
  - `IS_SUPPORT_ACCOUNT_ID_BIND_NEW` (`0x080000` in `FunctionList 10`) = **`0` (clear)**
  - `IS_PLATFORM_RK_RTL8773EWE` (`0x020000` in `FunctionList 13`) = **`0` (clear)**

### 2.3 Zero-Auth Connection Flow
In `BluetoothLeService` (`0x38acc8`–`0x38ae3c`), because `IS_SUPPORT_PASS_WORD_PAIR`, `IS_SUPPORT_ACCOUNT_ID_BIND`, and `IS_SUPPORT_ACCOUNT_ID_BIND_NEW` are all `false`, **no password, PIN, or cloud account ID binding is required**. Any local client can connect, subscribe to `33F2` and `34F2`, and send commands to `33F1` and `34F1`.

---

## 3. Core Command Payloads (`WriteCommandToBLE`)

Commands below are written to `0x33F1` unless noted as `writeCharaBle5` (`0x34F1`):

| Command | Method | Payload (Hex) | Notes |
| :--- | :--- | :--- | :--- |
| Sync Date/Time | `syncBLETime` (`0x3b5bb8`) | `A3 <YY_HI> <YY_LO> <MM> <DD> <HH> <Min> <SS>` | Big-endian year (e.g. `2026` = `07 EA`) |
| Read FW Version | `sendToReadBLEVersion` (`0x3b3b38`) | `A1` | **Observed live (2026-10-25):** Returns `A1 52 48 32 38 31 4C 57 4A 56 30 30 37 31 30 38` -> ASCII **`RH281LWJV007108`** on `33F2` |
| Read DSP Version | `queryDeviceDspVersion` (`0x3af168`) | `A1 01` | **Observed live:** Returns `A1` + `RH281LWJV007108` on `33F2` |
| Read UI Version | `sendToReadUIResoureVersion` (`0x3b3b6c`) | `CD 01` | Queries UI pack version |
| Read Battery | `sendToReadBLEBattery` (`0x3b3afc`) | `A2` | **Observed live:** Returns 2-byte `A2 <pct>` (e.g. `A2 38` = `56%`) on `33F2` |
| Find Watch | `findBand` (`0x3ad768`) | `AB 00 00 00 AB <01=start\|00=stop> 07 AB` | Vibrates/rings watch |
| Find Phone Toggle | `openFindPhone` (`0x3aed88`) | `D1 0A <01=on\|00=off>` | Watch sends `D1 0A` when triggered |
| Wrist Media RX (`33F2`) | `BluetoothLeService` (`0x380d60`) | `D1 07` (Play/Pause), `D1 08` (Next), `D1 09` (Prev), `D1 0C` (Music Open), `D1 0D` (Vol+), `D1 0E` (Vol-), `D1 12` (Mute) | Watch -> Phone media & volume control triggers |
| Wrist Media TX (`33F1`) | `controlBandMusic` / `Volume` (`0x3ad36c`) | `D1 0C <01=Play\|02=Pause>`, `D1 0D <volPct>`, `D1 0E <volPct>` | Phone -> Watch playback & volume % sync |
| Unit & Clock Format | `sendUnitToBLE` (`0x3b4040`) | `A0 <01=Metric\|02=Imperial> <01=24h\|02=12h>` | Sets Imperial/Metric and 12h/24h clock |
| Raise Wrist to Wake | `start/stopTurnWristCalibration` | `FC 01` (enable) / `FC 00` (disable) | Turn wrist to light screen |
| Call Alert | `sendIncallCommand` (`0x3b03b4`) | `AB 00 00 00 01 <01=ring\|02=ans\|03=rej> 02 01` | Follow with text chunks (`C5`) |
| SMS / App Alert | `sendSmsCommand` (`0x3b2508`) | `AB 00 00 00 01 <type> 00 00` | Follow with text chunks (`C5`) |
| Push Text Chunks | `sendTextSectionKey` (`0x3b3668`) | `C5 <idx> <up to 18 UTF-8 bytes>` then `C5 FD` | Displays popup text on watch |
| Step/Sleep Status | `sendToGetStepOrSleepStatus` (`0x3b3aa4`) | `AA` | Current step/sleep summary |
| Sync All Steps | `syncAllStepData` (`0x3b5a14`) | `B2 FA` | Streams `B2 ...` ending with `B2 FD` |
| Sync All Sleep | `syncAllSleepData` (`0x3b58a8`) | `31 01` | Band sleep algorithm stream |
| Live HR Test | `sendRateTestCommand` (`0x3b0dd8`) | `E5 11` (start) / `E5 00` (stop) | Streams live BPM via `E5 ...` |
| Sync All HR | `syncAllRateData` (`0x3b56dc`) | `E6 FA ...` | Historical HR stream |
| Read Dial Config | `readDeviceOnlineDialConfiguration` (`0x3af4f0`) | `26 01` | **Observed live (2026-10-25):** Returns 20-byte `26 01 00 F4 37 89 01 40 01 7C 01 00 10 00 00 02 01 00 00 00` -> **`320×380` px**, `screenType=1`, `maxDataSize=1048576` (1 MB), `compat=2`, `slots=1`, `dialNumber=16004999` |
| Prepare Dial Send | `prepareSendOnlineDialData` (`0x3aef28`) | `26 02` | Prepares custom watchface transfer |
| Read Custom BG | `getImageWatchFaceParamsRk` (`0x3ad7cc`) | `26 0A` | Reads custom photo background parameters (`ImageWatchFaceRk`) |
| Set Custom BG | `setImageWatchFaceConfigRk` (`0x3b440c`) | `26 0B <18-byte config>` | Configures custom background image transfer |
| Open Classic BT3 | `openDeviceBt3` (`0x3aed48`) | `38 02 <01=on\|00=off>` *(written to `34F1`)* | Enables/disables Classic BT speaker+mic (`Handsfree`/`HSP`) |
| BT3 One-Key Pair | `sendBt3AgreementCommand` (`0x3af988`) | `38 01 01 00 <4-byte random>` *(written to `34F1`)* | Pairs Classic BT3 audio (`RTL8763E` / `63E` path) |
| Power Off / Reboot | `devicePowerOff` / `deviceRestart` | `E8 11` (off) / `E8 12` (reboot) | Device power state control |
| Wipe Data | `deleteDevicesAllData` (`0x3ad5c8`) | `AD` | **Destructive** factory reset — do not send |

---

## 4. Vendor Cloud & OTA Update API (`ute-tech.com.cn`)

GloryFit's `com.yc.server.yc_sdk.Agent` and `com.yc.pedometer.update.Updates` check for watch firmware updates via unencrypted `application/x-www-form-urlencoded` HTTP POST requests:

- **Extracted App Key (`YCAPPKEY` in `AndroidManifest.xml`):** `dcd05f241b65ec7b6af0bbe6f05145c2`
- **Endpoints:**
  - `https://www.ute-tech.com.cn/ci-yc/index.php/api/client/getBtVersionUpdate`
  - `https://www.ute-tech.com.cn/ci-yc/index.php/api/client/getPatchlicationUpdate`
  - `https://www.ute-tech.com.cn/ci-yc/index.php/api/client/getBtUIVersionUpdate`
- **POST Body:**
  `content={"appkey":"dcd05f241b65ec7b6af0bbe6f05145c2","btname":"RH281LWJ","versionname":"V007108","mac":"00:00:00:00:00:00","language":"en"}`
- **Observed Probe Result (2026-10-25, using both `btname="T60"` and true internal `btname="RH281LWJ"` with dummy MAC `00:00:00:00:00:00`):**
  - `getBtVersionUpdate` and `getBtUIVersionUpdate` returned HTTP 200 `{"flag":-1,"msg":"post ok , no new version."}` across `V000001`..`V007108`, indicating Ute-Tech does not currently host a newer OTA binary for `RH281LWJ` or `T60` on their public update server.
- **Extracted Realtek DFU Secret Keys (`FirmwareActivity.<init>`, `classes6.dex` at `0x20ff4c`..`0x20ff9c`):**
  - `mSecretkey` (32 bytes): `4e46f8c5095554455f524b0cd1f610fb1f6763df807a7e70960d4cd3118e601a` (embeds ASCII `UTE_RK`)
  - `mSecretkeyRH276N` (32 bytes): `4e46f8c5092b29e29a971a0cd1f610fb1f6763df807a7e70960d4cd3118e601a`
  - `mSecretkey8762C` (32 bytes): `1a608e11d34c0d96707e7a80df63671ffb10f6d10c1a979ae2292b09c5f8464e` (byte-reversed `mSecretkeyRH276N`)

## Feature support decoded from the FunctionList bits (Inferred, 2026-10-25)

`tools/t60_feature_flags.py <classes6 dexdump>` lists every `GetFunctionList.isSupportFunction*(bit)` check in GloryFit with the class/method that asks, and marks whether the bit is set in the observed `33F1`/`34F1` values. Feature meanings come from the caller names, so they are **Inferred**, not documented.

| Watch reports it | Feature (list, bit) |
|---|---|
| Yes | Heart rate + all-day/high-rate alarm (L1 `0x080000`, L1 `0x80`, L4 `0x800`) |
| Yes | Blood oxygen (L5 `0x40`) |
| Yes | REM sleep (L7 `0x08`) |
| Yes | Weather (L1 `0x2000`, L6 `0x800`) |
| Yes | Workouts with phone GPS (L5 `0x02`, L6 `0x2000`) |
| Yes | Calls through the watch (L5 `0x2000`) |
| Yes | Music control (L5 `0x01`) and song name (L5 `0x1000`) |
| Yes | Call / SMS / app alerts (L1 `0x400000`, L4 `0x010000`, L3 and L5 `0x800`) |
| Yes | Move (sedentary) reminder (L2 `0x10`), alarms (L6 `0x01`), step goal (L5 `0x200000`) |
| No | Blood pressure (L1 `0x100000`, L5 `0x100000`), ECG (L3 `0x2000`, L8 `0x1000`), temperature (L5 `0x08`) |
| No | HRV (L11 `0x20`), mood/stress/fatigue (L8 `0x400000`) |
| Yes | Contacts on watch (Smart Play `rl_contacts`: L5 `0x2000` + L6 `0x40000`); quick text replies (L6 `0x200`) |
| No | SOS call (L7 `0x02`) |
| Yes | Raise wrist to wake (Smart Play L1 `0x40`), quiet hours / Do Not Disturb (Smart Play L1 `0x08`). The L2 `0x040000` and L11 `0x080000` checks are secondary variants. |

Full menu-by-menu audit: [`docs/APP_FEATURES.md`](APP_FEATURES.md) (`tools/t60_menu_audit.py`).
