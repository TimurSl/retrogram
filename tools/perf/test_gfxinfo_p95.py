import unittest

import gfxinfo_p95

SAMPLE = """
Applications Graphics Acceleration Info:
Uptime: 1234 Realtime: 5678

** Graphics info for pid 4242 [app.retrogram.messenger] **

Stats since: 1000ns
Total frames rendered: 3600
Janky frames: 120 (3.33%)
50th percentile: 5ms
90th percentile: 7ms
95th percentile: 8ms
99th percentile: 19ms
Number Missed Vsync: 3
"""


class GfxinfoTest(unittest.TestCase):
    def test_parse(self):
        s = gfxinfo_p95.parse(SAMPLE)
        self.assertEqual(3600, s["total_frames"])
        self.assertEqual(120, s["janky_frames"])
        self.assertEqual(8.0, s["p95"])
        self.assertEqual(19.0, s["p99"])

    def test_pass_and_fail(self):
        self.assertEqual(0, gfxinfo_p95.check(SAMPLE, 8.3)[0])
        self.assertEqual(1, gfxinfo_p95.check(SAMPLE.replace("95th percentile: 8ms", "95th percentile: 11ms"), 8.3)[0])

    def test_missing_numbers(self):
        self.assertEqual(2, gfxinfo_p95.check("nothing here")[0])
        self.assertEqual(2, gfxinfo_p95.check(SAMPLE.replace("Total frames rendered: 3600", "Total frames rendered: 0"))[0])


if __name__ == "__main__":
    unittest.main()
