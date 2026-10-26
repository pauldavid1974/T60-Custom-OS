# Hardware evidence — exact model T60

| Item | Status |
|---|---|
| Model | Observed: T60 |
| FCC ID | Observed: 2BEQO-T60 |
| Factory firmware | **Observed:** UI displays `T60V007108`; live BLE query `0xA1` (`33F1` -> `33F2`, 2026-10-25) reports internal factory project/firmware ID **`RH281LWJV007108`** (`btname="RH281LWJ"`, `version="V007108"`) |
| BLE GATT | Observed: 10 services — 1801, 1800, 55FF, 56FF, 60FF, 61FF, standard 180F (Battery), D0FF (10 characteristics incl. FFF1/FFF3), `00006287-3c17-d293-8e48-14fe2e4da212`, `000002fd-3c17-d293-8e48-14fe2e4da212` (full tree captured in-app 2026-10-25, see below) |
| D0FF characteristic FFF1 | Observed read (re-confirmed live 2026-10-25 via nRF Connect): `0B 01 1D 00 51 01 00 F7 00 00 00 00 00` |
| D0FF characteristic FFF3 | Observed read 2026-10-25 via nRF Connect: `14 00` (2 bytes; meaning unknown) |
| Vendor chip-family string | Documented in GloryFit's Realtek SDK as `0B` -> `BB2`, per Work session analysis |
| Exact SoC, board revision, memory | **Observed (FCC Internal-photo-7108448 Page 3, 2026-10-25):** Main SoC = **Realtek `RTL8763EWE`** (`N8AT8H1` / `GN33 TAIWAN`, 40.0 MHz crystal); External SPI NOR Flash = **Puya `PY25Q128HA`** (`3H1PP2D`, 128 Mbit = **16 MB**); PCB silkscreen = **`RH235_V02`** / **`PH235_V02`** with labeled `3V3`, `+`, `-`, and `RF` pads |
| Display geometry & controller | **Observed (Live BLE `0x26 01` read on `33F2`, 2026-10-25):** Resolution = **`320 × 380` pixels** (`0x0140` × `0x017C`), rectangular (`screenType = 1`), max watchface partition = **1.0 MB (`1,048,576` bytes, `0x00100000`)**, `compatibleLevel = 2`, `watchFaceCount = 1`, active `dialNumber = 16004999` (`0x00F43789`). Display controller IC/pinout = Unknown (pending flash `0x2797` dump) |
| UART/SWD pad access | **Observed (FCC Internal-photo-7108448 Pages 1–3, 2026-10-25):** Labeled **`TX`** and **`RX`** UART pads (plus `-BAT+`, `+SPK-`, `RF`) on the display-facing side of the PCB; labeled **`3V3`** pad and 8-pin **`PUYA PY25Q128HA`** SPI NOR flash on the opposite side |
| Firmware signing/encryption requirements | **Documented (GloryFit `FirmwareActivity`, `classes6.dex`):** Three 32-byte Realtek DFU AES/secret keys extracted (`mSecretkey` containing `UTE_RK`, `mSecretkeyRH276N`, `mSecretkey8762C`); hardware bootloader enforcement remains Unknown until flash dump |
| True factory firmware backup | Not acquired or verified |
| Dedicated phone (ADB) | Observed 2026-10-24: Samsung SM_G781U (serial RFCNC10FA3K), connected over USB, ADB authorized |
| Phone pairing with T60 | Observed 2026-10-24 via read-only `dumpsys bluetooth_manager`: bonded **and connected** as "T60(ID-AE67)", address `60:1A:B7:00:AE:67` (DUAL BR/EDR+LE). Advertised services include standard 1101, HSP, Handsfree, AVRCP and a custom UUID `6a24eeab-4b65-4693-986b-3c26c352264f`. The address may be a random/resolvable LE identifier, not a factory MAC |
| Companion apps on phone | Observed 2026-10-24: GloryFit `com.yc.gloryfit` v4.0.12 (versionCode 279) installed. Observed 2026-10-25: nRF Connect for Mobile (`no.nordicsemi.android.mcp`) present and connected to the T60 as the phone's GATT client (see "Live GATT capture" section) |

## Offline Realtek mapping and OS survey (2026-10-24)

- **Observed in supplied Work transcript, not independently reproduced from source:** GloryFit's bundled Realtek SDK reportedly maps the first FFF1 byte `0x0B` to the SDK label `BB2`. The APK, class dump and exact source path are not in this repository, so this establishes only a reported SDK enumeration, not a verified vendor definition.
- **Observed (2026-10-24, OpenCode session):** a project-scoped sweep found no local primary-source artifact. Checked repository tree/history and project-scoped folders; no APK/class dump was found, and no watch or phone was touched. The app remains installed on the dedicated phone as recorded above. Paul has now authorized a read-only ADB pull of GloryFit for offline reverse engineering; until that evidence is examined, the `0B -> BB2` mapping stays transcript-reported.
- **Unknown:** no public Realtek source located in this pass maps `BB2` uniquely to an RTL part number. The RTL8762C and RTL8762D firmware images found in the APK are not evidence that either is installed in this T60.
- **Observed:** the 13-byte FFF1 payload remains recorded verbatim above. (Superseded 2026-10-25: an explicit field layout was located in the app's SDK — see "Processor identification — SDK trace and live reads" below.)
- **Documented / inferred distinction (corrected 2026-10-25, SDK trace for mission `2ae9057`):** the earlier statement "no explicit 13-byte FFF1 parser was found" is **superseded** — the SDK contains the device-info parser `com.realsil.sdk.dfu.model.DeviceInfo.parseX0000:([BI)V` (classes5.dex), whose byte 0 is the IC type. FFF1 byte 0 is **observed** as `0x0B` and **documented** by that parser as the IC type `BB2`; decoding the *observed* 13-byte payload with that parser (and the end-to-end invocation path for this specific characteristic) remains **inferred (strong)**, because the T60's live GATT layout exactly matches the SDK's default profile. Decoded field values are listed in the section below; byte 7 (`F7`) is not read on the specVersion=1 path.
- **Documented:** Realtek's publicly indexed development material for other, named RTL87x families describes vendor toolkits, image/header generation and programming or OTA workflows. The current Zephyr Realtek board list names RTL87x2G Model A and RTL8752H evaluation boards, not a BB2 target. Neither source proves this T60 uses those targets or supports those workflows.
- **Inferred:** a vendor SDK application for the exact SoC may be the most plausible customization base if Realtek access and the exact SDK are available. No existing open-source watch OS was found that is documented for `BB2`; Zephyr support for other Realtek evaluation boards and Nordic-based ZSWatch are not drop-in candidates.
- **Unknown:** recovery interoperability, image signing, flash protection and a restorable factory backup. A BLE OTA-like service is not a recovery route. No write or update was attempted.

Research pointers: [Realtek RTL87x2G SDK](https://github.com/rtkconnectivity/rtl87x2g_sdk), [Zephyr Realtek boards](https://docs.zephyrproject.org/latest/boards/realtek/index.html), [Realtek SDK quick start](https://docs.realmcu.com/sdk/rtl87x3e/common/en/latest/text/User_Manual/quick_start.html), [ZSWatch](https://github.com/ZSWatch/ZSWatch). These cover other known targets and are comparison leads only.

| Candidate | Fit to evidence | License / access | Assessment |
|---|---|---|---|
| Realtek vendor SDK for the eventual exact RTL part | Potentially best hardware match, but `BB2` is not yet mapped to a part and SDK applicability is unproven | Realtek RTL87x2G SDK repository is publicly visible; other SDK packages and tools may require Realtek's Anchor account/toolkit distribution. License terms must be checked for the actual target package | Keep as the leading conditional candidate; first obtain exact SoC and matching SDK documentation/toolchain |
| Zephyr Realtek targets (currently RTL8752H and RTL87x2G Model A boards) | Same vendor family only; no evidence these boards/SoCs match `BB2` | Zephyr is open source; board-level Realtek support still depends on vendor-provided images and target-specific setup | Useful only if exact part and required vendor images are supported; no T60 port established |
| ZSWatch | Built for a Nordic nRF5340 and its own board/peripherals, not Realtek BB2 | Firmware repository is GPL-3.0; public source | Not a porting base for this watch. UI/product ideas may be studied, subject to license and attribution |

This is a compatibility shortlist, not a recommendation to install or flash. No candidate currently meets the Stage 2 suitability gate.

Do not identify the exact SoC based on other products also sold under the name "T60."

## GloryFit APK primary-source verification (2026-10-24, owner-authorized read-only)

- **Observed:** APK pulled read-only via ADB from the dedicated phone (RFCNC10FA3K). `com.yc.gloryfit` v4.0.12 (versionCode 279); `base.apk` SHA-256 `470B7EAE5C79F9CBF26B59AC651832A70143A1A4DC6C849C8204DFB2D72159AB`; `split_config.arm64_v8a.apk` SHA-256 `4EC08F3D753D300F243B8A1ABDA9B54D76AE439ACFBD10BC1B91369A99F593F2`. Stored outside Git at `C:\Users\pdo74\AppData\Local\Temp\opencode\t60-gloryfit\` (APKs remain uncommitted per repo policy).
- **Documented (vendor SDK in app):** `classes5.dex` embeds the RealSil SDK, package `com.realsil.sdk` (RealSil is Realtek's MCU brand; native libs `libRtkAesJni.so`, `libRtkMediaCodec.so`). `com.realsil.sdk.dfu.DfuConstants` maps IC IDs to product lines: `IC_BB2 = 0x0B` (also IC_BEE1=0x03, IC_BBPRO=0x04, IC_BEE2=0x05, IC_BBLITE_ANC=0x06, IC_BBPRO2=0x07, IC_BBLITE=0x08, IC_SBEE2=0x09, IC_BBPRO3=0x0A, IC_BEE3=0x0C) and `MANUFACTURER_ID_REALTEK = 0x5D`. This **confirms** the transcript-reported `0x0B -> BB2` mapping from primary source. `BB2` is a product-line label; the SDK contains **no RTL part number** for it, so the exact SoC stays **Unknown**.
- **Documented (GATT map in app):** DFU GATT defaults: service `0000d0ff-3c17-d293-8e48-14fe2e4da212` (exactly the observed T60 service), characteristics `00006287/6387/6487` on the same 128-bit base, MTU 256. Per-profile classes (`com.realsil.sdk.dfu.m.o`, `n/h`, `o/e`, `p/g`, `q/g`, `r/h`, `s/j`) define 16-bit characteristics `0000fff1` and `0000fff2`; `dfu.m.o` pairs the d0ff service with `0000fff3`; `ExtendedBluetoothDevice` filters scans on `0000fff6`. `classes6.dex` defines services `000055ff`, `000056ff`, `000057ff` and characteristics `0000fff0`, `0000ffd0..0000ffd5`, `0000ffd8`. The observed 128-bit service `6a24eeab-4b65-4693-986b-3c26c352264f` equals the SDK's `WELL_KNOWN_SPP_UUID` (SPP transport) in `bbpro.core.peripheral.ConnectionParameters`, `bbpro.core.spp.SppTransportConnParams`, `core.bluetooth.connection.legacy.SppConnParameters` and `core.d.f`. Observed `60FF`/`61FF` services are not present as SDK string constants (**Unknown**).
- **Documented (FFF1 semantics, corrected 2026-10-25):** the SDK's `DeviceInfo` model carries an `icType` byte plus specVersion/secure/mode/bank/buffer/imageVersion fields, and the explicit parser `com.realsil.sdk.dfu.model.DeviceInfo.parseX0000:([BI)V` (LITTLE_ENDIAN) reads byte 0 = icType, byte 1 & `0x0F` = specVersion; on the specVersion=1 path byte 2 = secureVersion, byte 3 = mode, bytes 4–5 = maxBufferCheckSize (LE16), byte 6 = otaTempBufferSize, bytes 8–11 = imageVersionIndicator (LE32). The OTA command dispatcher `com.realsil.sdk.dfu.v.a` sets `icId` from the 0x108 command response (other command IDs: 0x102, 0x107, 0x109, 0x600, 0x601, 0x60C, 0x60D). Consequence: **byte 0 of the observed FFF1 response = IC type (0x0B = BB2) is documented by the parser; decoding the observed payload as a whole remains Inferred (strong)** — see the dedicated section below.
- **Observed (negative):** this APK version's assets contain **no** watch firmware blobs (no RTL8762C/D images found); the earlier Work-transcript claim of two bundled firmware images was not reproduced in v4.0.12. `assets/ble_analyze.js` is a parser for a different (non-D0FF) protocol.
- **Observed (phone state):** nRF Connect for Mobile (`no.nordicsemi.android.mcp`) installed by Paul and connected to the T60 as the phone's GATT client (read-only `dumpsys bluetooth_manager`, 2026-10-24 22:34:42). Android's stack dump does not expose the remote GATT DB, so the service tree and `FFF1`/`FFF3` values require capture in the app (or a laptop GATT client, which is not installed).

## Live GATT capture via nRF Connect (2026-10-25, owner-authorized read-only)

- **Method (Observed):** nRF Connect for Mobile CLIENT view on the dedicated phone (`RFCNC10FA3K`), watch shown as `T60(ID-AE67)` `60:1A:B7:00:AE:67`, CONNECTED + BONDED. Each screen state was verified with screenshots plus `uiautomator` XML dumps (element bounds); only the explicit per-characteristic **Read** control (app resource `action_read`, content-desc "Read") was invoked, and only after the target row and control were visually confirmed. No writes, subscriptions, or state changes. Phone clock during capture: ~11:34–11:38.
- **Observed (top-level GATT service list, CLIENT view, list order):** 10 services: `0x1801` Generic Attribute; `0x1800` Generic Access; `0x55FF`; `0x56FF`; `0x60FF`; `0x61FF`; `0x180F` Battery Service (contains Battery Level `0x2A19`, properties READ+NOTIFY, descriptor CCCD `0x2902`); `0000d0ff-3c17-d293-8e48-14fe2e4da212`; `00006287-3c17-d293-8e48-14fe2e4da212`; `000002fd-3c17-d293-8e48-14fe2e4da212`. The last two services stayed collapsed and were not enumerated. The 128-bit service `6a24eeab-4b65-4693-986b-3c26c352264f` is **not** present in the GATT database (it is the SPP/RFCOMM UUID, consistent with the SDK's `WELL_KNOWN_SPP_UUID`).
- **Observed (D0FF service characteristics, list order):** `0xFFD1` (WRITE NO RESPONSE — the only write capability in this service), `0xFFD2` READ, `0xFFD3` READ, `0xFFD4` READ, `0xFFF1` READ, `0xFFE0` READ, `0xFFE1` READ, `0xFFF3` READ, `0xFFF4` READ, `0xFFF5` READ. Ten characteristics; none advertise NOTIFY or INDICATE.
- **Observed (FFF1 live read):** `Value: (0x) 0B-01-1D-00-51-01-00-F7-00-00-00-00-00` — 13 bytes, byte-identical to the earlier Work-session payload; independently re-confirmed 2026-10-25. Byte 0 `0x0B` equals the SDK constant `IC_BB2`; the role of byte 0 as an IC-ID field remains **Inferred** (no parser links FFF1 to the SDK `icId` model). Bytes 1–12 remain **Unknown**.
- **Observed (FFF3 live read):** `Value: (0x) 14-00` — 2 bytes `14 00`. Field meaning is **Unknown**; the SDK profile `m.o` pairs `fff3` with the d0ff service, but no payload definition was found.
- **Evidence storage:** screenshots and `uiautomator` dumps (`01`–`10`, `ui*.xml`) are stored outside Git at `C:\Users\pdo74\AppData\Local\Temp\opencode\t60-gloryfit\screens\`. They contain phone UI only; no secrets were captured.
- **Completed 2026-10-25 (second read batch, same verified nRF method):** expansion and enumeration of `00006287-…` and `000002fd-…`; READ of `FFD2`–`FFD4`, `FFE0`, `FFE1`, `FFF4`, `FFF5` and Battery `0x2A19`. Values and properties are recorded in the next section. No writes or subscriptions.

## Processor identification — SDK trace and live reads (2026-10-25, mission `2ae9057`)

All code citations below are from `classes5.dex` of GloryFit v4.0.12 (`base.apk` SHA-256 `470B7EAE…2159AB`), disassembled with `dexdump`; APK and dumps remain stored outside Git as before.

- **Documented (device-info parser):** `com.realsil.sdk.dfu.model.DeviceInfo.parseX0000:([BI)V` (dex addr `3dd98c`) parses a LITTLE_ENDIAN byte buffer at an offset: byte 0 → `icType`; byte 1 & `0x0F` → `specVersion`; then three version paths:
  - `specVersion == 0`: byte 2 → appFreeBank (low nibble) / patchFreeBank (high nibble), byte 3 → mode, bytes 4–5 → maxBufferCheckSize (LE16), bytes 6–13 → appData0–3 (LE16 each).
  - `specVersion == 1` (**the T60's path**): byte 2 → secureVersion, byte 3 → mode, bytes 4–5 → maxBufferCheckSize (LE16), byte 6 → otaTempBufferSize, bytes 8–11 → imageVersionIndicator (LE32). Byte 7 is not read on this path.
  - `specVersion >= 2`: as path 1, with bytes 6–7 and 8–9 read into two further 16-bit fields.
  - Decoding the observed T60 FFF1 payload `0B 01 1D 00 51 01 00 F7 00 00 00 00 00` under this layout: icType `0x0B` (BB2), specVersion 1, secureVersion `0x1D`, mode 0, maxBufferCheckSize `0x0151` (337), otaTempBufferSize 0, imageVersionIndicator 0. **Inferred (strong)** — supported by the exact GATT layout match below, not by a traced end-to-end call for this characteristic.
- **Documented (IC table and names):** `com.realsil.sdk.dfu.DfuConstants`: `IC_BEE1=0x03, IC_BBPRO=0x04, IC_BEE2=0x05, IC_BBLITE_ANC=0x06, IC_BBPRO2=0x07, IC_BBLITE=0x08, IC_SBEE2=0x09, IC_BBPRO3=0x0A, IC_BB2=0x0B, IC_BEE3=0x0C` and `MANUFACTURER_ID_REALTEK=0x5D` (Realtek's Bluetooth SIG company ID). The name mapper `com.realsil.sdk.dfu.l.a.a(I)` extends the line to newer chips: 13→BBLITE_2, 14→BEE3_PRO, 15→BEE4, 16→BEE3_PLUS, 17→BB2_PLUS, 18→BEE5, 19→BB2_ULTRA, 20→BB3. So `BB2` sits in a multi-generation vendor line (BBPro1–3, BBLite/ANC/2, BB2/BB2_PLUS/BB2_ULTRA, BB3) with `BEE*` siblings.
- **Documented (default GATT layout == observed T60 layout):** `com.realsil.sdk.dfu.m.o.<clinit>` (SDK defaults): OTA service `0000d0ff-3c17-d293-8e48-14fe2e4da212` with OTA characteristic `0000fff3-0000-1000-8000-00805f9b34fb` (16-bit base) plus FFD0; DFU service `00006287-3c17-…` with `00006387-3c17-…` (write) and `00006487-3c17-…` (notify); CCCD `0x2902`. Default profile `r` declares the same 6287-3c17 service (`r.g`: 6287/6387/6487/6587) and the D0FF-family 16-bit characteristics (`r.h`: FFD1–FFD5, FFD8, FFF1, FFF2). The observed T60 GATT — 128-bit D0FF-3c17 and 6287-3c17 services carrying 16-bit `0xFFD1` (write-no-response) and `0xFFD2–FFD4/FFF1/FFE0–FFE1/FFF3–FFF5` (read) characteristics — is an exact subset of this layout. The `3c17-d293-8e48-14fe2e4da212` base is Realtek's vendor 128-bit UUID base.
- **Documented (chip/protocol selection):** the sub-adapter factory `com.realsil.sdk.dfu.utils.a.c(I)` selects the DFU profile by 16-bit `protocolType`: `0x10`→s, `0x12`→n, `0x13`→o, `0x14`→p, `0x15`→q, **default→r** (plus a local-name `"BeeTgt02"` flag). `DeviceInfo.setProtocolType` is **never invoked in any of the 8 dex files** (string and reference scan), so the protocol type stays 0 and this app runs the **default r profile** — whose UUID set is exactly the observed T60 layout. The image loader `com.realsil.sdk.dfu.image.FirmwareLoaderX.loadImageBinInfo` dispatches per-chip image parsers by the same keys (n/c, p/c, q/c, v/c, s/e|s/f, x/h|i|j) with **default `k/f`**; the OTA dispatcher `com.realsil.sdk.dfu.v.a` sets `icId` from the 0x108 command response, image versions from 0x60C/0x60D, image features from 0x109, battery from 0x107.
- **Documented (negative, image provenance):** no watch firmware blobs exist in this APK version's assets; update images are loaded at runtime (SDK log format keys selection by `protocolType=%04X, specVersion=%02X, icType=%02X, imageId=0x%02X`), not shipped in the app.
- **Observed (second live read batch, 2026-10-25, same verified nRF method; screenshots `11`–`37`, `ui8.xml`–`ui29.xml` outside Git):**
  - `FFD2` = `60 1A B7 00 AE 67 60 1A B7 00 AE 67` — 16 bytes: the connected device address `60:1A:B7:00:AE:67` repeated twice (the SDK's `DeviceInfo` carries a device-MAC parse path; role **inferred**).
  - `FFD3` = empty, `FFD4` = empty (each read twice, no value returned), `FFE1` = empty, `FFF5` = empty (visible `Value:` rows with 0 bytes).
  - `FFE0` = 74-byte code-like blob (`00 0C 90 27 … FE FF 00 00 00 00`); `FFF4` = 55-byte code-like blob (pattern resembles ARM Thumb instruction words). No SDK payload definition found for either; meaning **Unknown**.
  - Battery `0x2A19` = `100%` (raw `0x64`).
  - `00006287-3c17-…` enumerated: `0x6387` WRITE NO RESPONSE; `0x6487` NOTIFY+WRITE with CCCD `0x2902` — the profile-r write/notify pair; no 6587 on this device.
  - `000002fd-3c17-…` enumerated: `0xFD03` WRITE + WRITE NO RESPONSE; `0xFD04` NOTIFY with CCCD `0x2902`; no READ characteristics.
  - `0x1801` and `0x1800` carry no characteristics; `0x55FF` = `0x33F1` READ+WRITE and `0x33F2` NOTIFY+CCCD; `0x56FF` = `0x34F1` READ+WRITE NO RESPONSE and `0x34F2` NOTIFY+CCCD. At this checkpoint `0x60FF`/`0x61FF` had only been expanded; their final enumeration and reads are recorded below.
- **Documented (FCC filing, retrieved 2026-10-25 from fccid.io):** FCC ID `2BEQO-T60` — applicant **SHENZHEN HAOCHENG TECHNOLOGY CO.,LTD** (grantee 2BEQO), product `T60` SmartWatch, filed 2024-02-06, tested by SHENZHEN TONGCE TESTING LAB, 2402–2480 MHz. Exhibits include internal photos (`Internal-photo-7108448`), external photos, label and user manual. Retrieval attempts and their limits are recorded below.

## Final read-only identification and OS assessment — Kimi Work (2026-10-25)

### Remaining authorized reads

Using the same verified nRF Connect method on `T60(ID-AE67)`, only explicit Read controls were invoked. No writes, notification subscriptions, settings changes, OTA/DFU commands or other state changes occurred.

| Target | Observed result | Interpretation |
|---|---|---|
| `55FF` / `33F1` (READ+WRITE) | 20 bytes: `08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC` | GloryFit (`BluetoothLeService$7`) parses this 20-byte value from right to left into `FunctionList 1..7`. Bytes `0..1` (`0x0808`) = `FunctionList 7`, which sets **`IS_PLATFORM_RTL8762Cx` (`0x0800`)** and `IS_SUPPORT_SLEEP_REM` (`0x0008`). Bytes `5..7` (`0x243943`) = `FunctionList 5`, which sets **`IS_SUPPORT_BT3_0_AGREEMENT` (`0x040000`)**, invoking the `RTL8763E` (`63E`) Classic BT 3.0 audio pairing path (`sendBt3AgreementCommand`). |
| `56FF` / `34F1` (READ+WRITE NO RESPONSE) | 20 bytes: `00 F4 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04 02` | Directly linked to GloryFit's function-list 8–13 parser; RTL8773EWE result below. |
| `60FF` / `6001` (READ+WRITE) | Same 20 bytes as `34F1` | No documented decoder or exact-part identifier found; the shared value alone does not establish equivalence. |
| `60FF` / `6002` | NOTIFY with CCCD | Not subscribed or read. |
| `61FF` | No characteristics exposed | No further read available. |

Local raw evidence is in ignored `scratch/live/33f1-read.png`/`.xml`, `34f1-read.png`/`.xml`, `6001-read.png`/`.xml`, and `61ff-target.png`/`.xml`. These are working evidence copies, not Git deliverables.

### Device-specific platform, BBPro GATT transport & RTL8763E capability trace (Updated 2026-10-25)

- **Documented + Observed (`33F1` `FunctionList 7` and `FunctionList 5`, `classes6.dex`):**
  - `BluetoothLeService$7.onCharacteristicRead` (`0x37a3fe`–`0x37a660`) slices the 20-byte `33F1` payload (`08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC`) into seven bitmasks (`FunctionList 1..7`).
  - `FunctionList 7` (bytes `0..1`) is `0x0808` (`2056 = 0x0800 | 0x0008`), which explicitly sets **`GlobalVariable.IS_PLATFORM_RTL8762Cx = 2048` (`0x0800`)** and `IS_SUPPORT_SLEEP_REM = 8` (`0x0008`). Other platform bits in `FunctionList 7` (`IS_PLATFORM_RTL8762Ax = 0x0200`, `IS_PLATFORM_NRF = 0x0400`, `IS_PLATFORM_FRK = 0x2000`, `IS_PLATFORM_JX_3085L = 0x8000`) are `0`.
  - `FunctionList 5` (bytes `5..7`) is `0x243943`, which sets **`GlobalVariable.IS_SUPPORT_BT3_0_AGREEMENT = 262144` (`0x040000`)**. When set, GloryFit invokes `WriteCommandToBLE.sendBt3AgreementCommand()` (`0x3af988`), which manages Classic Bluetooth (BT 3.0 Handsfree/Audio) one-key pairing using `SPUtil.getNeedToRegenerateRandom63E()` / `setPairingSystemBluetooth63E()` (`BleDevices.needPair8763E`, i.e., Realtek `RTL8763E` series).
- **Documented (`000002fd-3c17-d293-8e48-14fe2e4da212`, `classes5.dex` UTF-16LE dexdump):**
  - `com.realsil.sdk.bbpro.core.gatt.GattTransportConnParams.<clinit>` (`0x3a7c18`) defines `000002FD-3C17-D293-8E48-14FE2E4DA212` as Realtek's **BBPro GATT Transport Service** (`0xFD03` Write, `0xFD04` Notify), used alongside `00006287-3c17-d293-8e48-14fe2e4da212` (`OTA_SERVICE_RK_SLIENT_MODE_UUID`) by Realtek's BBPro/BB2 (`icType = 0x0B = IC_BB2`) DFU handlers (`com.realsil.sdk.dfu.p.d`, `q.d`, `s.h`).
- **Documented + Observed (`34F1` `FunctionList 8..13` and `maxCommunicationLength`, `classes6.dex`):**
  - `BluetoothLeService$7.onCharacteristicRead` (`0x37a6f8`–`0x37a940`) parses `34F1` bytes `0..1` (`0x00F4`) as `GlobalVariable.maxCommunicationLength = 244` bytes, bytes `17..19` (`0x000402`) as `FunctionList 8`, and bytes `2..16` (`0x000000`) as `FunctionList 9..13`. Thus `GlobalVariable.IS_PLATFORM_RK_RTL8773EWE = 131072` (`0x00020000` in `FunctionList 13`) is `0` (`false`).
- **Observed (`res/raw/rh266fp*.bin` in unpacked APK):**
  - Unpacked `scratch/offline/base/res/raw/` contains `rh266fp.bin` (`521,896` bytes, ASCII string `RTL8762C`) and `rh266fp_ui.bin` (`644,672` bytes, ASCII strings `RTL8762D` and `RH266FPV000682_U0001`). These are bundled factory test binaries for board `RH266FP`, not `T60V007108`.
- **Full BLE Protocol & Zero-Auth Reference:** See [docs/PROTOCOL.md](PROTOCOL.md) and local CLI tool [tools/t60_control.py](../tools/t60_control.py).

### FCC internal photo verification (Observed 2026-10-25, supplied by Paul in `fcc/chip-pic.png`)

Paul retrieved Page 3 of 4 of FCC exhibit `Internal-photo-7108448` (`fcc/chip-pic.png`, file SHA-256 of PDF listed on `fcc/fcc-details.png` as `16b9a43326915378dc00f59793dfa321f45feec79ecd07390f899901c55165bf`). Visible hardware markings on the T60 mainboard:

1. **Main SoC (Observed):** **`REALTEK RTL8763EWE`** (package markings: `REALTEK` / `RTL8763EWE` / `N8AT8H1` / `GN33 TAIWAN`), paired with a `40.0` MHz crystal (`T 3L 40.0`).
   - This directly confirms our code trace from GloryFit's `FFF1` (`0x0B` = `IC_BB2`) and `FunctionList 5` (`IS_SUPPORT_BT3_0_AGREEMENT` -> `SPUtil.getNeedToRegenerateRandom63E` / `setPairingSystemBluetooth63E`).
2. **External Flash Memory (Observed):** **`PUYA PY25Q128HA`** (package markings: `PUYA` / `PY25Q128HA` / `3H1PP2D`).
   - Standard **128-Megabit (16 MB) SPI NOR Flash** chip mounted adjacent to the `RTL8763EWE`.
3. **PCB Revision, UART Pads & Mechanical Access (Observed, Pages 1–3):**
   - **Front/Screen-Side Opening (Page 1):** Lifting the front glass/display assembly exposes the top side of the mainboard without removing the PCB from the housing.
   - **Top Side of PCB (Page 2):** Exposes the display FPC connector, solder pads **`-BAT+`** and **`+SPK-`**, and clearly labeled gold test pads **`RX`**, **`TX`**, and **`RF`**.
   - **Bottom/Chip Side of PCB (Page 3):** Silkscreen **`RH235_V02`** (or `PH235_V02`), carrying the **`REALTEK RTL8763EWE`** SoC, **`PUYA PY25Q128HA`** (16 MB SPI NOR Flash), `40.0` MHz crystal, and labeled **`3V3`** test pad.
4. **Decoded `FFE0` (Active Image Versions) & `FFF4` (Flash Partition Sizes) from `D0FF` Reads (Documented + Observed):**
   - `FFE0` (74 bytes) is a 2-byte count (`0x000C` = 12 entries) followed by twelve 6-byte `(uint16_le image_id, uint32_le version)` records (`0x2790` Boot/OCCD, `0x2791` Patch, `0x2793` MCU App, `0x2794` DSP Sys, `0x2795` DSP App, `0x2797` MCU Config, `0x2796` DSP Config, `0x2798` Ext, `0x279C`/`0x279D` UI/VP, `0xFFFD`/`0xFFFE`).
   - `FFF4` (55 bytes) is a 1-byte count (`0x09` = 9 entries) followed by nine 6-byte `(uint16_le image_id, uint32_le partition_bytes)` records: `0x2791` = 8 KB (`0x2000`), `0x2793` (MCU App) = **1,372,160 B (~1.34 MB, `0x14F000`)**, `0x2794` = 128 KB (`0x20000`), `0x2795` = 384 KB (`0x60000`), `0x2797` = 40 KB (`0xA000`), `0x2796` = 8 KB (`0x2000`), `0x2798` = 200 KB (`0x32000`), `0x279C` = 104 KB (`0x1A000`), `0x279D` = 164 KB (`0x29000`).

### Processor / SDK / OS decision

| Item | Evidence-labeled conclusion |
|---|---|
| SoC vendor and product line | **Documented + inferred-strong:** Realtek; FFF1 byte 0 `0x0B` maps to `BB2` under the SDK parser and exact GATT-layout match. This is not an exact part number. |
| Exact RTL part number | **Observed: `Realtek RTL8763EWE`** (`N8AT8H1` / `GN33 TAIWAN`) on FCC exhibit `Internal-photo-7108448` Page 3 (`fcc/chip-pic.png`). Matches `FFF1` `icType = 0x0B` (`IC_BB2`) and GloryFit's `63E` (`RTL8763E`) BT 3.0 pairing code path. |
| External Flash / RAM | **Observed (External Flash): 16 MB (128 Mbit) SPI NOR Flash — `Puya PY25Q128HA`** (`3H1PP2D`) on the mainboard next to the `RTL8763EWE`. On-die SRAM size of `RTL8763EWE` is documented in Realtek's `RTL87x3E` series specifications. |
| Board / peripherals | **Unknown:** board revision, display/controller, touch/buttons, sensors, power, RTC, UART/SWD and flash geometry. |
| Matching build SDK / toolchain | **Unknown for the T60.** The app's RealSil SDK documents a transport/protocol, not a watch build target. Realtek's public `RTL87x3E-Common-SDK` identifies only the RTL87x3E series and links RTL8763E-series documentation; its GCC sample uses Cortex-M3/Thumb and Realtek post-build/header tools. With the T60's RTL8773EWE app capability clear and no exact-part evidence, this is a comparison lead, **not documented compatibility**. |
| Zephyr | **Not documented as compatible.** Listed Realtek support is for other named evaluation targets; no T60/BB2 board or exact-SoC port is established. |
| ZSWatch | **Not a compatible base:** it targets Nordic nRF5340 and different board/peripherals. |
| Vendor SDK for the eventual exact part | **Leading conditional route only.** Compatibility, access, license, memory budget and board support all remain unproven. No OS base is recommended for installation. |

**Assessment complete to the available evidence.** There is currently no existing watch OS or vendor build SDK documented as compatible with an identified exact T60 SoC. The smallest next diagnostic is for Paul to obtain a readable copy of the exact FCC internal-photo PDF through a working manual source and supply it for offline inspection; this needs no new watch operation. If that remains unavailable, any physical inspection/disassembly requires a separate explicit approval and is not part of this completed read-only pass. Factory backup, signature requirements and recovery remain separate hard blockers even after chip identification.

## Evidence and leads

- FCC internal photo archive: https://fccid.io/2BEQO-T60/Internal-Photos/Internal-photo-7108448
- Work's read-only `FFF1` results and GloryFit SDK inspection: ChatGPT Work export supplied 2026-10-24; keep a provenance summary in HANDOFF.md.
- Realtek protocol guide: https://www.realmcu.com/img/ipd/zh_638247794120444333.pdf
- Public Realtek SDK and candidate OS comparison: links in "Offline Realtek mapping and OS survey" above.

Store future measurements and their provenance in `knowledge/graph.json`; retain the full source or appropriate external pointer. Avoid checking proprietary APK binaries or private raw dumps into the repository.
