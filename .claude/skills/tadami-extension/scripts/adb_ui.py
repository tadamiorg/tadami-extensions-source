#!/usr/bin/env python3
"""
Reads and drives the Tadami app's screen over adb, without Espresso (uiautomator dumps).

    adb_ui.py texts                       Visible texts and content descriptions, with bounds
    adb_ui.py tap "<regex>"               Taps the first node whose text or description matches
    adb_ui.py tap-xy <x> <y>              Taps a point, in screenshot pixels (for icons without a label)
    adb_ui.py type "<text>"               Types into the focused field (tap the field first)
    adb_ui.py clear                       Empties the focused field
    adb_ui.py wait "<regex>" [--timeout 30]
                                          Waits until a node matches (exit 1 on timeout)
    adb_ui.py player [--advance 5]        Shows the player controls and prints position / duration.
                                          With --advance N, reads again N seconds later and exits 1
                                          if the position didn't move
    adb_ui.py shot <file.png>             Screenshot

The device comes from --serial or ANDROID_SERIAL (needed when several are connected). adb is found
through ANDROID_HOME / ANDROID_SDK_ROOT / LOCALAPPDATA / PATH. Python lists are passed to subprocess,
so Git Bash path mangling doesn't apply.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def find_adb():
    exe = "adb.exe" if os.name == "nt" else "adb"
    roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    if os.environ.get("LOCALAPPDATA"):
        roots.append(os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk"))
    roots.append(os.path.expanduser("~/Library/Android/sdk"))
    roots.append(os.path.expanduser("~/Android/Sdk"))
    for root in filter(None, roots):
        candidate = os.path.join(root, "platform-tools", exe)
        if os.path.isfile(candidate):
            return candidate
    found = shutil.which("adb")
    if found:
        return found
    sys.exit("adb not found: set ANDROID_HOME or put platform-tools on PATH")


class Device:
    def __init__(self, serial):
        self.adb = find_adb()
        self.base = [self.adb] + (["-s", serial] if serial else [])

    def run(self, *args, binary=False, timeout=30, check=True):
        result = subprocess.run(self.base + list(args), capture_output=True, timeout=timeout)
        if not check:
            return result
        if result.returncode != 0:
            message = result.stderr.decode(errors="replace").strip()
            if "more than one device" in message:
                message += " (pass --serial or set ANDROID_SERIAL; `adb devices` lists them)"
            sys.exit(f"adb {' '.join(args)} failed: {message}")
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

    def nodes(self):
        # Dumps can fail transiently while the screen animates
        for _ in range(3):
            self.run("shell", "uiautomator", "dump", "/sdcard/tadami_ui.xml")
            xml = self.run("exec-out", "cat", "/sdcard/tadami_ui.xml")
            if xml.lstrip().startswith("<?xml"):
                root = ET.fromstring(xml[xml.index("<?xml"):].strip())
                return list(root.iter("node"))
            time.sleep(1)
        sys.exit("Could not dump the screen (is the device awake and unlocked?)")


def describe(node):
    return node.get("text", ""), node.get("content-desc", "")


def center(node):
    match = BOUNDS.match(node.get("bounds", ""))
    if not match:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


# Player position and duration labels: 00:34, 1:02:15
TIME_LABEL = re.compile(r"^\d{1,2}:\d{2}(:\d{2})?$")
# The device shell would interpret these in `input text`
SHELL_SPECIAL = set("\\'\"`$&|;<>()[]{}*?!~#")


def find(device, pattern):
    regex = re.compile(pattern)
    for node in device.nodes():
        text, desc = describe(node)
        if (text and regex.search(text)) or (desc and regex.search(desc)):
            return node
    return None


def seconds(label):
    total = 0
    for part in label.split(":"):
        total = total * 60 + int(part)
    return total


def player_times(device):
    """(position, duration) labels of the player, showing its controls first when they're hidden."""
    def times():
        return [describe(n)[0] for n in device.nodes() if TIME_LABEL.match(describe(n)[0])]

    labels = times()
    if len(labels) < 2:
        # A single tap in the middle shows the controls (a double tap would seek)
        size = re.search(r"(\d+)x(\d+)", device.run("shell", "wm", "size"))
        width, height = (int(size.group(1)), int(size.group(2))) if size else (1080, 1920)
        device.run("shell", "input", "tap", str(width // 2), str(height // 3))
        time.sleep(1)
        labels = times()
    if len(labels) < 2:
        sys.exit("No position / duration labels on screen: is the player open?")
    return labels[0], labels[1]


def type_text(device, text):
    escaped = "".join("%s" if c == " " else ("\\" + c if c in SHELL_SPECIAL else c) for c in text)
    device.run("shell", "input", "text", escaped)


def clear_field(device):
    # Select all + delete (Android 13+); older versions get a burst of deletes from the end
    if device.run("shell", "input", "keycombination", "113", "29", check=False).returncode == 0:
        device.run("shell", "input", "keyevent", "67")
    else:
        device.run("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        device.run("shell", "input", "keyevent", *["67"] * 120)


def main():
    # Anime titles aren't ASCII, and the Windows console isn't UTF-8 by default
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("texts")
    tap = sub.add_parser("tap")
    tap.add_argument("pattern")
    tap_xy = sub.add_parser("tap-xy")
    tap_xy.add_argument("x", type=int)
    tap_xy.add_argument("y", type=int)
    type_parser = sub.add_parser("type")
    type_parser.add_argument("text")
    sub.add_parser("clear")
    player = sub.add_parser("player")
    player.add_argument("--advance", type=float, default=0,
                        help="read again after this many seconds and check the position moved")
    wait = sub.add_parser("wait")
    wait.add_argument("pattern")
    wait.add_argument("--timeout", type=float, default=30)
    shot = sub.add_parser("shot")
    shot.add_argument("file")
    args = parser.parse_args()
    device = Device(args.serial)

    if args.command == "texts":
        for node in device.nodes():
            text, desc = describe(node)
            if text or desc:
                print(f"{text!r} desc={desc!r} clickable={node.get('clickable')} "
                      f"enabled={node.get('enabled')} {node.get('bounds')}")
    elif args.command == "tap":
        node = find(device, args.pattern)
        point = center(node) if node is not None else None
        if point is None:
            sys.exit(f"Nothing matches {args.pattern!r} on screen")
        device.run("shell", "input", "tap", str(point[0]), str(point[1]))
        print(f"tapped {describe(node)[0] or describe(node)[1]!r} at {point[0]},{point[1]}")
    elif args.command == "tap-xy":
        device.run("shell", "input", "tap", str(args.x), str(args.y))
        print(f"tapped {args.x},{args.y}")
    elif args.command == "type":
        type_text(device, args.text)
        print(f"typed {args.text!r}")
    elif args.command == "clear":
        clear_field(device)
        print("cleared")
    elif args.command == "player":
        position, duration = player_times(device)
        print(f"position {position} / {duration}")
        if args.advance:
            time.sleep(args.advance)
            later, _ = player_times(device)
            moved = seconds(later) - seconds(position)
            print(f"position {later} / {duration} ({moved:+d} s)")
            if moved <= 0:
                sys.exit("The position didn't advance: playback is stalled or paused")
    elif args.command == "wait":
        deadline = time.time() + args.timeout
        while time.time() < deadline:
            node = find(device, args.pattern)
            if node is not None:
                print(f"found {describe(node)[0] or describe(node)[1]!r}")
                return
            time.sleep(2)
        sys.exit(f"{args.pattern!r} didn't appear within {args.timeout:.0f}s")
    elif args.command == "shot":
        with open(args.file, "wb") as f:
            f.write(device.run("exec-out", "screencap", "-p", binary=True))
        print(args.file)


if __name__ == "__main__":
    main()
