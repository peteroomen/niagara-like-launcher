"""Builds src/main/assets/emoji.txt from Unicode's emoji-test.txt.

    curl -sSO https://unicode.org/Public/emoji/16.0/emoji-test.txt
    python3 scripts/build_emoji.py emoji-test.txt > src/main/assets/emoji.txt

One line per category, in Gboard's order: its name, a tab, then its emoji separated by spaces, in
CLDR order (the order Unicode recommends for keyboards). Only fully-qualified emoji, and none with
a skin tone: those would come from holding an emoji, which the keyboard doesn't do yet. The
keyboard drops any the phone's font can't draw.
"""
import sys

GROUPS = {
    "Smileys & Emotion": "Smileys and people",
    "People & Body": "Smileys and people",
    "Animals & Nature": "Animals and nature",
    "Food & Drink": "Food and drink",
    "Travel & Places": "Travel and places",
    "Activities": "Activities",
    "Objects": "Objects",
    "Symbols": "Symbols",
    "Flags": "Flags",
}
SKIN_TONES = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}

categories = {}
group = None
for line in open(sys.argv[1], encoding="utf-8"):
    if line.startswith("# group:"):
        group = GROUPS.get(line.split(":", 1)[1].strip())
        continue
    if not group or line.startswith("#") or ";" not in line:
        continue
    codes, rest = line.split(";", 1)
    if not rest.strip().startswith("fully-qualified"):
        continue
    points = [int(c, 16) for c in codes.split()]
    if SKIN_TONES & set(points):
        continue
    categories.setdefault(group, []).append("".join(chr(p) for p in points))

for name in dict.fromkeys(GROUPS.values()):
    print(name + "\t" + " ".join(categories[name]))
