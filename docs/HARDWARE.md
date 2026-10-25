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
- **Observed (2026-10-24, OpenCode session):** a project-scoped sweep found no local primary-source artifact. Checked: full repository tree (including hidden files) and git history on all branches (no APK/smali/dex/class/dump/transcript ever committed); `My Projects` sibling folders (none T60-scoped; `Qwen-Colab` is Engine Room serving infrastructure); `Documents` and `Desktop` top levels matching T60/glory/watch/firmware patterns (none); name-filtered `Downloads` (only an unrelated `watch_face_designer.html`). No watch or phone was touched. Consequence: the `0B -> BB2` mapping stays transcript-reported until Paul makes the APK/class dump available (e.g., an explicitly approved ADB pull from the dedicated phone, or a path to existing local files).
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

## Evidence and leads

- FCC internal photo archive: https://fccid.io/2BEQO-T60/Internal-Photos/Internal-photo-7108448
- Work's read-only `FFF1` results and GloryFit SDK inspection: ChatGPT Work export supplied 2026-10-24; keep a provenance summary in HANDOFF.md.
- Realtek protocol guide: https://www.realmcu.com/img/ipd/zh_638247794120444333.pdf
- Public Realtek SDK and candidate OS comparison: links in "Offline Realtek mapping and OS survey" above.

Store future measurements and their provenance in `knowledge/graph.json`; retain the full source or appropriate external pointer. Avoid checking proprietary APK binaries or private raw dumps into the repository.
