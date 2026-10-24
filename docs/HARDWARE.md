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

Do not identify the exact SoC based on other products also sold under the name "T60."

## Evidence and leads

- FCC internal photo archive: https://fccid.io/2BEQO-T60/Internal-Photos/Internal-photo-7108448
- Work's read-only `FFF1` results and GloryFit SDK inspection: ChatGPT Work export supplied 2026-10-24; keep a provenance summary in HANDOFF.md.
- Realtek protocol guide: https://www.realmcu.com/img/ipd/zh_638247794120444333.pdf

Store future measurements and their provenance in `knowledge/graph.json`; retain the full source or appropriate external pointer. Avoid checking proprietary APK binaries or private raw dumps into the repository.
