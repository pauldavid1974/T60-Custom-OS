#!/usr/bin/env python3
"""
Local, zero-cloud companion and protocol tool for the T60 smartwatch (T60V007108).

Safety note (AGENTS.md):
- By default, all commands run in offline/dry-run mode (--dry-run) and print the exact
  BLE GATT characteristic and payload bytes without touching the watch.
- Live BLE connections/writes require explicit owner approval and the --live flag.
"""

from __future__ import annotations

import argparse
import asyncio
import datetime as dt
import struct
import sys
import zlib
from typing import List, Sequence

# T60 GATT UUIDs (Documented in GloryFit v4.0.12 classes5.dex & classes6.dex)
UUID_SERVICE_MAIN = "000055ff-0000-1000-8000-00805f9b34fb"
UUID_CHAR_WRITE = "000033f1-0000-1000-8000-00805f9b34fb"
UUID_CHAR_NOTIFY = "000033f2-0000-1000-8000-00805f9b34fb"

UUID_SERVICE_EXT = "000056ff-0000-1000-8000-00805f9b34fb"
UUID_CHAR_EXT_WRITE = "000034f1-0000-1000-8000-00805f9b34fb"
UUID_CHAR_EXT_NOTIFY = "000034f2-0000-1000-8000-00805f9b34fb"

UUID_CHAR_BATTERY = "00002a19-0000-1000-8000-00805f9b34fb"
DEFAULT_T60_MAC = "60:1A:B7:00:AE:67"


def build_sync_time(when: dt.datetime | None = None) -> bytes:
    """Build 8-byte syncBLETime (0xA3) payload for 33F1."""
    now = when or dt.datetime.now()
    return bytes([
        0xA3,
        (now.year >> 8) & 0xFF,
        now.year & 0xFF,
        now.month & 0xFF,
        now.day & 0xFF,
        now.hour & 0xFF,
        now.minute & 0xFF,
        now.second & 0xFF,
    ])


def build_read_version() -> bytes:
    """Build 1-byte sendToReadBLEVersion (0xA1) payload for 33F1."""
    return bytes([0xA1])


def build_read_ui_version() -> bytes:
    """Build 2-byte sendToReadUIResoureVersion (0xCD 0x01) payload for 33F1."""
    return bytes([0xCD, 0x01])


def build_read_battery() -> bytes:
    """Build 1-byte sendToReadBLEBattery (0xA2) payload for 33F1."""
    return bytes([0xA2])


def build_find_watch(start: bool = True) -> bytes:
    """Build 8-byte findBand (0xAB) payload for 33F1."""
    return bytes([0xAB, 0x00, 0x00, 0x00, 0xAB, 0x01 if start else 0x00, 0x07, 0xAB])


def build_heart_rate_test(start: bool = True) -> bytes:
    """Build 2-byte sendRateTestCommand (0xE5) payload for 33F1."""
    return bytes([0xE5, 0x11 if start else 0x00])


def build_sync_steps() -> bytes:
    """Build 2-byte syncAllStepData (0xB2 0xFA) payload for 33F1."""
    return bytes([0xB2, 0xFA])


def build_sync_sleep() -> bytes:
    """Build 2-byte syncAllSleepData (0x31 0x01) payload for 33F1 (T60 band sleep algorithm)."""
    return bytes([0x31, 0x01])


def build_notification_packets(text: str, msg_type: int = 0x01) -> List[bytes]:
    """
    Build notification header (sendSmsCommand 0xAB) + UTF-8 text chunks (sendTextSectionKey 0xC5)
    + end marker (0xC5 0xFD) for 33F1.
    """
    packets: List[bytes] = []
    # Header: sendSmsCommand(type)
    packets.append(bytes([0xAB, 0x00, 0x00, 0x00, 0x01, msg_type & 0xFF, 0x00, 0x00]))
    raw = text.encode("utf-8")[:180]
    chunk_size = 18
    for idx, offset in enumerate(range(0, len(raw), chunk_size)):
        chunk = raw[offset : offset + chunk_size]
        packets.append(bytes([0xC5, idx & 0xFF]) + chunk)
    packets.append(bytes([0xC5, 0xFD]))
    return packets


def build_bt3_audio_switch(enable: bool = True) -> bytes:
    """Build 3-byte openDeviceBt3 (0x38 0x02) payload for 34F1 (Classic BT speaker/mic)."""
    return bytes([0x38, 0x02, 0x01 if enable else 0x00])


def build_query_dial_config() -> bytes:
    """Build 2-byte readDeviceOnlineDialConfiguration (0x26 0x01) payload for 33F1."""
    return bytes([0x26, 0x01])


def build_query_custom_bg_params() -> bytes:
    """Build 2-byte getImageWatchFaceParamsRk (0x26 0x0A) payload for 33F1."""
    return bytes([0x26, 0x0A])


def build_custom_bg_config(
    style_index: int,
    position_index: int,
    preview_size: int,
    bg_size: int,
    crc32_val: int,
) -> bytes:
    """Build 20-byte setImageWatchFaceConfigRk (0x26 0x0B ...) payload for 33F1."""
    return struct.pack(
        ">BBBBIIII",
        0x26,
        0x0B,
        style_index & 0xFF,
        0x00,
        position_index & 0xFFFFFFFF,
        preview_size & 0xFFFFFFFF,
        bg_size & 0xFFFFFFFF,
        crc32_val & 0xFFFFFFFF,
    )


def decode_notification(char_uuid: str, data: bytes) -> str:
    """Decode incoming 33F2 / 34F2 notification bytes into human-readable summary."""
    hex_str = data.hex(" ").upper()
    if not data:
        return f"[{char_uuid}] <empty>"
    op = data[0]
    if op == 0xA1 and len(data) > 1:
        ascii_ver = data[1:].decode("ascii", errors="replace").strip("\x00")
        return f"[{char_uuid}] Firmware Version (0xA1): {ascii_ver!r} ({hex_str})"
    if op == 0xA2 and len(data) == 2:
        pct = data[1]
        return f"[{char_uuid}] Battery (0xA2): {pct}% ({hex_str})"
    if op == 0xA2 and len(data) >= 3:
        status, pct = data[1], data[2]
        return f"[{char_uuid}] Battery (0xA2): {pct}% (status={status}) ({hex_str})"
    if op == 0xE5 and len(data) >= 3:
        return f"[{char_uuid}] Heart Rate (0xE5): {data[2]} BPM (state=0x{data[1]:02X}) ({hex_str})"
    if op == 0xD1 and len(data) >= 2 and data[1] == 0x0A:
        return f"[{char_uuid}] Find-Phone Triggered by Watch (0xD1 0x0A)! ({hex_str})"
    if op == 0x26 and len(data) >= 17 and data[1] == 0x01:
        dial_num = (data[2] << 24) | (data[3] << 16) | (data[4] << 8) | data[5]
        width = (data[6] << 8) | data[7]
        height = (data[8] << 8) | data[9]
        screen_type = data[10]
        max_size = (data[11] << 24) | (data[12] << 16) | (data[13] << 8) | data[14]
        compat = data[15]
        slots = data[16]
        return (
            f"[{char_uuid}] Dial Config (0x26 0x01): {width}x{height}px, "
            f"screenType={screen_type}, maxDataSize={max_size}B, "
            f"compat={compat}, slots={slots}, dialNumber={dial_num} ({hex_str})"
        )
    if op == 0x26 and len(data) >= 2 and data[1] == 0x0A:
        return f"[{char_uuid}] Custom WatchFace BG Params (0x26 0x0A): {hex_str}"
    return f"[{char_uuid}] Packet (0x{op:02X}): {hex_str}"


def format_hex(data: bytes) -> str:
    return data.hex(" ").upper()


async def run_scan(timeout: float = 5.0) -> int:
    try:
        from bleak import BleakScanner  # type: ignore
    except ImportError:
        print("Error: 'bleak' is not installed. Run: pip install bleak", file=sys.stderr)
        return 2

    print(f"Scanning for nearby BLE devices for {timeout:.1f}s ...")
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
    found_t60 = False
    for addr, (dev, adv) in sorted(devices.items()):
        name = dev.name or adv.local_name or "<unnamed>"
        marker = "  <-- YOUR T60 WATCH!" if ("T60" in name.upper() or addr.upper() == DEFAULT_T60_MAC) else ""
        if marker:
            found_t60 = True
        print(f"  {addr}  RSSI={adv.rssi:4d} dBm  Name={name!r}{marker}")
    if not found_t60:
        print("\nNote: T60 was not seen broadcasting yet. Make sure Bluetooth is turned OFF on the phone so the watch lets go of the phone and starts advertising!")
    return 0


async def run_live(address: str, char_uuid: str, payloads: Sequence[bytes], listen_sec: float) -> int:
    try:
        from bleak import BleakClient  # type: ignore
    except ImportError:
        print("Error: 'bleak' is not installed. Run: pip install bleak", file=sys.stderr)
        return 2

    print(f"Connecting to {address} ...")
    async with BleakClient(address) as client:
        def on_notify(sender, data: bytearray) -> None:
            print(decode_notification(str(sender), bytes(data)))

        await client.start_notify(UUID_CHAR_NOTIFY, on_notify)
        await client.start_notify(UUID_CHAR_EXT_NOTIFY, on_notify)

        for pkt in payloads:
            print(f"-> Writing to {char_uuid}: {format_hex(pkt)}")
            await client.write_gatt_char(char_uuid, pkt, response=(char_uuid == UUID_CHAR_WRITE))
            await asyncio.sleep(0.08)

        if listen_sec > 0:
            print(f"Listening for {listen_sec:.1f}s for watch responses ...")
            await asyncio.sleep(listen_sec)

    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Local, cloud-free T60 smartwatch command & protocol utility."
    )
    parser.add_argument("--address", default=DEFAULT_T60_MAC, help="Watch BLE MAC address")
    parser.add_argument(
        "--live",
        action="store_true",
        help="Execute live BLE connection/write (requires owner approval per AGENTS.md). Default is dry-run.",
    )
    parser.add_argument("--listen", type=float, default=3.0, help="Seconds to listen for responses in --live mode")

    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("scan", help="Scan for nearby BLE devices (including T60) from this laptop")
    sub.add_parser("sync-time", help="Sync current local date and time to watch (0xA3)")
    sub.add_parser("battery", help="Query watch battery level (0xA2)")
    sub.add_parser("version", help="Query watch firmware version (0xA1)")
    sub.add_parser("find-watch", help="Vibrate/ring watch (0xAB)")
    sub.add_parser("stop-find-watch", help="Stop watch vibration (0xAB)")
    sub.add_parser("hr-start", help="Start live heart-rate sensor reading (0xE5 0x11)")
    sub.add_parser("hr-stop", help="Stop live heart-rate sensor reading (0xE5 0x00)")
    sub.add_parser("sync-steps", help="Request step history sync (0xB2 0xFA)")
    sub.add_parser("sync-sleep", help="Request sleep history sync (0x31 0x01)")
    sub.add_parser("dial-config", help="Query watch display resolution and dial parameters (0x26 0x01)")
    sub.add_parser("query-bg-params", help="Query custom watchface background parameters (0x26 0x0A)")
    sub.add_parser("bt3-audio-on", help="Enable watch Classic BT speaker/microphone (0x38 0x02 0x01)")
    sub.add_parser("bt3-audio-off", help="Disable watch Classic BT speaker/microphone (0x38 0x02 0x00)")

    p_notify = sub.add_parser("notify", help="Push a custom text popup notification to the watch screen")
    p_notify.add_argument("text", help="Notification message text to display on watch")

    args = parser.parse_args(argv)

    if args.cmd == "scan":
        return asyncio.run(run_scan(max(args.listen, 5.0)))

    target_char = UUID_CHAR_WRITE
    if args.cmd == "sync-time":
        payloads = [build_sync_time()]
    elif args.cmd == "battery":
        payloads = [build_read_battery()]
    elif args.cmd == "version":
        payloads = [build_read_version()]
    elif args.cmd == "find-watch":
        payloads = [build_find_watch(True)]
    elif args.cmd == "stop-find-watch":
        payloads = [build_find_watch(False)]
    elif args.cmd == "hr-start":
        payloads = [build_heart_rate_test(True)]
    elif args.cmd == "hr-stop":
        payloads = [build_heart_rate_test(False)]
    elif args.cmd == "sync-steps":
        payloads = [build_sync_steps()]
    elif args.cmd == "sync-sleep":
        payloads = [build_sync_sleep()]
    elif args.cmd == "dial-config":
        payloads = [build_query_dial_config()]
    elif args.cmd == "query-bg-params":
        payloads = [build_query_custom_bg_params()]
    elif args.cmd == "bt3-audio-on":
        target_char = UUID_CHAR_EXT_WRITE
        payloads = [build_bt3_audio_switch(True)]
    elif args.cmd == "bt3-audio-off":
        target_char = UUID_CHAR_EXT_WRITE
        payloads = [build_bt3_audio_switch(False)]
    elif args.cmd == "notify":
        payloads = build_notification_packets(args.text)
    else:
        parser.error(f"Unknown command: {args.cmd}")
        return 2

    if not args.live:
        print(f"[DRY-RUN] Target Watch : {args.address}")
        print(f"[DRY-RUN] Target Char  : {target_char}")
        for idx, pkt in enumerate(payloads):
            print(f"[DRY-RUN] Packet #{idx + 1:02d}   : {format_hex(pkt)} ({len(pkt)} bytes)")
        print("[DRY-RUN] No BLE connection made. Pass --live only with explicit owner approval.")
        return 0

    return asyncio.run(run_live(args.address, target_char, payloads, args.listen))


if __name__ == "__main__":
    raise SystemExit(main())
