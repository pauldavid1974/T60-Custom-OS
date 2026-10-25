import datetime as dt
import unittest

from tools import t60_control


class TestT60Control(unittest.TestCase):
    def test_build_sync_time(self) -> None:
        when = dt.datetime(2026, 10, 25, 18, 15, 30)
        pkt = t60_control.build_sync_time(when)
        self.assertEqual(pkt, bytes([0xA3, 0x07, 0xEA, 10, 25, 18, 15, 30]))

    def test_build_basic_queries(self) -> None:
        self.assertEqual(t60_control.build_read_version(), bytes([0xA1]))
        self.assertEqual(t60_control.build_read_ui_version(), bytes([0xCD, 0x01]))
        self.assertEqual(t60_control.build_read_battery(), bytes([0xA2]))
        self.assertEqual(
            t60_control.build_find_watch(True),
            bytes([0xAB, 0x00, 0x00, 0x00, 0xAB, 0x01, 0x07, 0xAB]),
        )
        self.assertEqual(t60_control.build_heart_rate_test(True), bytes([0xE5, 0x11]))
        self.assertEqual(t60_control.build_heart_rate_test(False), bytes([0xE5, 0x00]))
        self.assertEqual(t60_control.build_sync_steps(), bytes([0xB2, 0xFA]))
        self.assertEqual(t60_control.build_sync_sleep(), bytes([0x31, 0x01]))
        self.assertEqual(t60_control.build_bt3_audio_switch(True), bytes([0x38, 0x02, 0x01]))

    def test_build_notification_packets(self) -> None:
        pkts = t60_control.build_notification_packets("Hello Paul!")
        self.assertEqual(len(pkts), 3)
        self.assertEqual(pkts[0], bytes([0xAB, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00]))
        self.assertEqual(pkts[1], bytes([0xC5, 0x00]) + b"Hello Paul!")
        self.assertEqual(pkts[2], bytes([0xC5, 0xFD]))

    def test_decode_notification(self) -> None:
        ver_str = t60_control.decode_notification("33f2", b"\xa1T60V007108")
        self.assertIn("T60V007108", ver_str)
        bat_str = t60_control.decode_notification("33f2", bytes([0xA2, 0x00, 85]))
        self.assertIn("85%", bat_str)


if __name__ == "__main__":
    unittest.main()
