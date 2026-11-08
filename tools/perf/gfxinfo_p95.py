#!/usr/bin/env python3
"""Reads `adb shell dumpsys gfxinfo <package>` output and checks the frame-time gate of docs/plans/07-validation-and-sync.md.

Usage:  python3 tools/perf/gfxinfo_p95.py <gfxinfo.txt> [--limit-ms 8.3]
Exit status 0 when the 95th percentile frame time is at or below the limit, 1 when it is above, 2 when the numbers are missing.
"""
import re
import sys


def parse(text):
    """Returns a dict with total_frames, janky_frames and the percentile frame times in ms found in the text."""
    result = {}
    m = re.search(r"Total frames rendered:\s*(\d+)", text)
    if m:
        result["total_frames"] = int(m.group(1))
    m = re.search(r"Janky frames:\s*(\d+)", text)
    if m:
        result["janky_frames"] = int(m.group(1))
    for p in (50, 90, 95, 99):
        m = re.search(r"%dth percentile:\s*(\d+)ms" % p, text)
        if m:
            result["p%d" % p] = float(m.group(1))
    return result


def check(text, limit_ms=8.3):
    """Returns (exit_code, message)."""
    stats = parse(text)
    if "p95" not in stats or stats.get("total_frames", 0) == 0:
        return 2, "no frame statistics found (was the app drawing during the test? run the fling first)"
    line = "frames=%d janky=%s p50=%s p90=%s p95=%s p99=%s (limit p95 <= %.1f ms)" % (
        stats["total_frames"], stats.get("janky_frames", "?"), stats.get("p50", "?"), stats.get("p90", "?"),
        stats["p95"], stats.get("p99", "?"), limit_ms)
    # gfxinfo reports whole milliseconds, so a limit below 9 can only be proven with p95 <= 8
    return (0 if stats["p95"] <= limit_ms else 1), line


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    limit = 8.3
    if "--limit-ms" in argv:
        limit = float(argv[argv.index("--limit-ms") + 1])
    with open(argv[1], encoding="utf-8", errors="replace") as f:
        code, message = check(f.read(), limit)
    print(("PASS: " if code == 0 else "FAIL: ") + message)
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv))
