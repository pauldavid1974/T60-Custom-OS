# T60 app feature checklist (GloryFit audit, 2026-10-25)

Every device-related menu item in GloryFit v4.0.12, checked against the feature list Paul's T60
reports when it connects (`33F1`/`34F1` FunctionList bits, observed). Item-to-bit pairing comes from
`FragmentSmartPlay.isSupportFunction`, `GeneralSettingActivity.isSupportFunction`,
`FragmentMine.updateSupportFunction` and `SportModeSupList.setSportSupList` in `classes6.dex`.
Meanings are **Inferred** from GloryFit's view and class names.

Owner rule (Paul): include everything the watch can do; drop bloat and anything that talks to
GloryFit's servers.

## Keep: watch supports it

| Feature | GloryFit item | Bit | In design? |
|---|---|---|---|
| Heart rate all day + high-rate alert | `rl_heart_rate_monitoring`, `Rate24HourSetting` | L2 `0x4000`, L1 `0x80` | Yes |
| Blood oxygen | `rl_oxygen_setting` | L5 `0x40` | Yes |
| Sleep with REM | `FragmentSleepDetailsDay.isSupportSleepRem` | L7 `0x08` | Yes |
| Steps, distance, calories | `PedometerUtils` | L4 `0x400000`, L2 `0x1000` | Yes |
| Workouts, about 24 types, phone GPS | `SportModeSupList` (types 2–24), `SportMapActivity` | L5 `0x02`, L6 `0x2000` | Yes (type names still to decode) |
| Call alerts | `rl_call_remind` | L1 `0x400000` | Yes |
| Text alerts | `rl_sms_remind` | L4 `0x010000` | Yes |
| Per-app alerts (many apps) | `rl_app_remind` | L3/L5 `0x800` | Yes |
| Quick replies to texts from the watch | `rl_sms_reply` | L6 `0x200` | **No — add** |
| Calls through the watch | `rl_device_bt_call` | L6 `0x40000`, L5 `0x2000` | Yes |
| Contacts on the watch | `rl_contacts` | L5 `0x2000` + L6 `0x40000` (newer format bit L6 `0x80000` clear) | **No — add** |
| Quiet hours (do not disturb) | `rl_do_not_disturb` | L1 `0x08` | Row only — **add screen** |
| Raise wrist to wake | `rl_raise_hand_bright_screen` | L1 `0x40` | **No — add back** |
| Move reminder | `rl_sit_remind` | L2 `0x10` | Yes |
| Alarms | `rl_alarm_remind` | L6 `0x01` (extended alarm list L6 `0x20000` clear) | Yes |
| Watch faces: switch + photo face | `rl_dial_setting` | L3 `0x100` | Yes (store dropped) |
| Weather | `GetWeather`, `syncWeatherToBLEForXiaoYang` | L1 `0x2000`, L6 `0x800` | Yes (weather.gov) |
| Music control + song name | `controlBandMusic`, `MusicControl` | L5 `0x01`, L5 `0x1000` | Yes |
| Find my phone | `rl_find_phone` | L2 `0x20` | Yes |
| Find my watch | `rl_find_band` | always shown | Yes |
| Watch language | `rl_switch_bracelet_language` | L2 `0x08` | **No — add** |
| Screen-on time | `rl_off_screen_time` | always shown | Row only — **add screen** |
| 12/24-hour, units | `rl_hour_system`, `rl_unit_settings` | always shown | Row only — **add screen** |
| Your profile (height, weight, age, sex) | `rl_personal_setting` | always shown | **No — add** |
| Step goal | `rl_sport_goal`, `GoalActivity` | L5 `0x200000` | **No — add** |
| Women's health (cycle tracking) | `rl_physiological` | L3 `0x8000` | **No — add** |
| Choose and order Today cards | `HomeItemManageActivity` | always shown | **No — add** |
| Wireless headset pairing for calls | `rl_headset`, `ScanHeadsetGuiActivity` | BT3 `0x040000` path | Covered by Calls screen |
| Clear watch data | `rl_clear_data` | always shown | **No — add (with confirm)** |
| Export / Health Connect | `HealthConnectActivity` | phone-side | Yes |

## Watch does not support it (hidden in GloryFit too)

Blood pressure (L4 `0x1000`), ECG, temperature (L5 `0x08`), HRV (L11 `0x20`), mood/stress
(L6 `0x20`), drink-water reminder (L5 `0x80000`), wash-hands reminder (L6 `0x04`), event
reminders (L1 `0x40000`), world clock / time zone (L6 `0x10`), SOS call (L7 `0x02`), wrist
detection (L5 `0x10000`), camera shutter (L11 `0x80000`/`0x100000`), screen rotation (L1 `0x20`),
watch menu layout (L2 `0x8000`), heart-rate sleep assist (L1 `0x200`), AI watch / voice (L10).

## Drop: bloat or server-side

Account and login, friends, health reports and weekly report (web), diet plan, training plan
(web-driven), AI agent / ChatGPT / translate, WeChat, Alipay, Tmall Genie, Strava, QR codes, push
messages, online watch-face store and purchases, online firmware update (`rl_ble_update`), feedback
upload, log sharing, help center and user guide (web pages), third-party programs, GloryFit's
weather servers.
