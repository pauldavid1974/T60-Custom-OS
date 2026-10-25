# Hardware evidence — exact model T60

| Item | Status |
|---|---|
| Model | Observed: T60 |
| FCC ID | Observed: 2BEQO-T60 |
| Factory firmware | Observed: V007108 |
| BLE GATT | Observed: services 55FF, 56FF, 60FF, 61FF, standard 180F, proprietary D0FF |
| D0FF characteristic FFF1 | Observed read: `0B 01 1D 00 51 01 00 F7 00 00 00 00 00` |
| Vendor chip-family string | Documented in GloryFit's Realtek SDK as `0B` -> `BB2`, per Work session analysis |
| Exact SoC, board revision, memory | Unknown |
| Display controller, touch/button inputs, sensors | Unknown |
| UART/SWD pad access | Unknown |
| Firmware signing/encryption requirements | Unknown |
| True factory firmware backup | Not acquired or verified |
| Dedicated phone (ADB) | Observed 2026-10-24: Samsung SM_G781U (serial RFCNC10FA3K), connected over USB, ADB authorized |
| Phone pairing with T60 | Observed 2026-10-24 via read-only `dumpsys bluetooth_manager`: bonded **and connected** as "T60(ID-AE67)", address `60:1A:B7:00:AE:67` (DUAL BR/EDR+LE). Advertised services include standard 1101, HSP, Handsfree, AVRCP and a custom UUID `6a24eeab-4b65-4693-986b-3c26c352264f`. The address may be a random/resolvable LE identifier, not a factory MAC |
| Companion apps on phone | Observed 2026-10-24: GloryFit `com.yc.gloryfit` v4.0.12 (versionCode 279) installed; nRF Connect **not** installed (no `no.nrfconnect`/Nordic package found) |

## Offline Realtek mapping and OS survey (2026-10-24)

- **Observed in supplied Work transcript, not independently reproduced from source:** GloryFit's bundled Realtek SDK reportedly maps the first FFF1 byte `0x0B` to the SDK label `BB2`. The APK, class dump and exact source path are not in this repository, so this establishes only a reported SDK enumeration, not a verified vendor definition.
- **Observed (2026-10-24, OpenCode session):** a project-scoped sweep found no local primary-source artifact. Checked repository tree/history and project-scoped folders; no APK/class dump was found, and no watch or phone was touched. The app remains installed on the dedicated phone as recorded above. Paul has now authorized a read-only ADB pull of GloryFit for offline reverse engineering; until that evidence is examined, the `0B -> BB2` mapping stays transcript-reported.
- **Unknown:** no public Realtek source located in this pass maps `BB2` uniquely to an RTL part number. The RTL8762C and RTL8762D firmware images found in the APK are not evidence that either is installed in this T60.
- **Observed:** the 13-byte FFF1 payload remains recorded verbatim above. No field layout beyond byte 0's reported SDK label is verified; bytes 1–12 remain undecoded.
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
- **Documented (FFF1 semantics):** the SDK's `DeviceInfo` model carries an `icId` byte plus protocol/app/pnp/bank/OTA fields; the OTA command dispatcher `com.realsil.sdk.dfu.v.a` parses a single `icId` byte from the 0x108 command response (other observed command IDs: 0x102, 0x60C, 0x60D). No explicit 13-byte `FFF1` parser was found in the Java code. Consequence: **byte 0 of the observed FFF1 response = IC ID (0x0B = BB2) is source-supported; bytes 1–12 remain Unknown** and must not be decoded speculatively.
- **Observed (negative):** this APK version's assets contain **no** watch firmware blobs (no RTL8762C/D images found); the earlier Work-transcript claim of two bundled firmware images was not reproduced in v4.0.12. `assets/ble_analyze.js` is a parser for a different (non-D0FF) protocol.
- **Observed (phone state):** nRF Connect for Mobile (`no.nordicsemi.android.mcp`) installed by Paul and connected to the T60 as the phone's GATT client (read-only `dumpsys bluetooth_manager`, 2026-10-24 22:34:42). Android's stack dump does not expose the remote GATT DB, so the service tree and `FFF1`/`FFF3` values require capture in the app (or a laptop GATT client, which is not installed).

## Evidence and leads

- FCC internal photo archive: https://fccid.io/2BEQO-T60/Internal-Photos/Internal-photo-7108448
- Work's read-only `FFF1` results and GloryFit SDK inspection: ChatGPT Work export supplied 2026-10-24; keep a provenance summary in HANDOFF.md.
- Realtek protocol guide: https://www.realmcu.com/img/ipd/zh_638247794120444333.pdf
- Public Realtek SDK and candidate OS comparison: links in "Offline Realtek mapping and OS survey" above.

Store future measurements and their provenance in `knowledge/graph.json`; retain the full source or appropriate external pointer. Avoid checking proprietary APK binaries or private raw dumps into the repository.
