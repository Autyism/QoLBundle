"""Generates src/main/resources/assets/qolbundle/pinyin_initials.txt.

The file is one long line: the pinyin initial (a-z) of every Chinese character from U+4E00 to
U+9FFF, in code point order ('?' where there is none). The Multilingual Item Search module uses
it so that typing "zs" finds 钻石.

Only needs to be re-run if the table should change. Needs the pypinyin package (MIT licence):
    pip install pypinyin
    python scripts/gen-pinyin-table.py
"""
import os

from pypinyin import Style, pinyin

FIRST, LAST = 0x4E00, 0x9FFF
out = []
for code in range(FIRST, LAST + 1):
    reading = pinyin(chr(code), style=Style.FIRST_LETTER, errors="ignore")
    letter = reading[0][0][:1].lower() if reading and reading[0] and reading[0][0] else "?"
    out.append(letter if "a" <= letter <= "z" else "?")

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
path = os.path.join(root, "src", "main", "resources", "assets", "qolbundle", "pinyin_initials.txt")
with open(path, "w", encoding="ascii", newline="\n") as f:
    f.write("".join(out))
print(f"wrote {len(out)} letters, {out.count('?')} unknown, to {path}")
