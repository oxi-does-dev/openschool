#!/usr/bin/env python3
"""Собирает компактные шрифты из полных в fonts-src/.

Полные Source Serif 4, Onest и Caveat весят около 1,6 МБ. Но
приложение никогда не покажет большинство их глифов: греческий, армянский,
латинские диакритики Ext-B, старые лигатуры. Субсеттинг выкидывает только
то, чего на экране не будет, и не меняет ни одного оставшегося контура.

Что делается:

  * Source Serif 4 Regular/SemiBold — кириллица, латиница (Basic +
    Latin-1 + Latin Extended-A), пунктуация, валюты, стрелки, математика.
    Это заголовки и пользовательский текст из МЭШ. Ось opsz фиксируется
    на 20, чтобы не тащить дельты оптического размера.
  * Onest — переменный шрифт с осью wght. Приложение просит 500 и 700,
    поэтому делаются два статических начертания без gvar.
  * Caveat — только цифры и знаки для оценок. Буквенные оценки («осв.»,
    «н») tvHand() рисует Onest Bold, поэтому кириллица в Caveat не нужна.

Запуск:  python3 tools/subset-fonts.py
Нужен fonttools (pip install fonttools).
"""

import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "fonts-src")
DST = os.path.join(ROOT, "assets", "fonts")


def code_range(start, end):
    return set(range(start, end + 1))


BASIC_LATIN = code_range(0x20, 0x7E)
LATIN_1 = code_range(0xA0, 0xFF)
LATIN_EXT_A = code_range(0x100, 0x17F)
CYRILLIC = code_range(0x400, 0x4FF)
CYRILLIC_SUP = code_range(0x500, 0x52F)
PUNCTUATION = code_range(0x2000, 0x206F)
CURRENCY = code_range(0x20A0, 0x20BF)
SYMBOLS = {
    0x00B7,  # ·
    0x00D7,  # ×
    0x2022,  # •
    0x2026,  # …
    0x2116,  # №
    0x2190, 0x2191, 0x2192, 0x2193,  # стрелки
    0x2212,  # −
    0x25B8, 0x25BE,  # ▸ ▾
    0x2713, 0x2714,  # ✓ ✔
}

TEXT = (BASIC_LATIN | LATIN_1 | LATIN_EXT_A | CYRILLIC | CYRILLIC_SUP
        | PUNCTUATION | CURRENCY | SYMBOLS)

# Всё, что tvHand() может показать «от руки»: оценки, средние баллы,
# проценты, прочерк, вопросительный знак, плюс/минус, дробная черта.
HAND = {ord(c) for c in "0123456789 .,+-−—–/%×?:!()№"}

# ID 13/14 — полный текст лицензии OFL внутри name-таблицы (12 КБ).
# Лицензия и так лежит рядом файлом assets/fonts/OFL-*.txt.
NAME_IDS = [0, 1, 2, 3, 4, 5, 6]


def build(src_name, unicodes, out_name, weight=None, opsz=None):
    path = os.path.join(SRC, src_name)
    font = TTFont(path)

    # legacy kern дублирует GPOS/kern, а Android шейпит через HarfBuzz+GPOS.
    if "GPOS" in font:
        features = {r.FeatureTag for r in font["GPOS"].table.FeatureList.FeatureRecord}
        if "kern" in features and "kern" in font:
            del font["kern"]

    options = subset.Options()
    options.layout_features = ["*"]      # лигатуры, кернинг, локали
    options.name_IDs = NAME_IDS
    options.name_legacy = True
    options.notdef_outline = True
    options.hinting = True

    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=sorted(unicodes))
    subsetter.subset(font)

    for tag in ("DSIG", "hdmx", "VDMX", "LTSH", "gasp"):
        if tag in font:
            del font[tag]

    if (weight is not None or opsz is not None) and "fvar" in font:
        axes = {}
        if weight is not None:
            axes["wght"] = weight
        if opsz is not None:
            axes["opsz"] = opsz
        instantiateVariableFont(font, axes, inplace=True,
                                updateFontNames=False)

    out = os.path.join(DST, out_name)
    font.save(out)
    print("  %-24s %7d байт  (%.0f%% от %d)"
          % (out_name, os.path.getsize(out),
             os.path.getsize(out) * 100.0 / os.path.getsize(path),
             os.path.getsize(path)))
    return out


def coverage(path):
    font = TTFont(path, lazy=True)
    codes = set()
    for table in font["cmap"].tables:
        if table.isUnicode():
            codes.update(table.cmap.keys())
    return codes


def main():
    if not os.path.isdir(SRC):
        sys.exit("нет каталога %s" % SRC)
    os.makedirs(DST, exist_ok=True)

    print("== шрифты")
    build("SourceSerif4.ttf", TEXT, "SourceSerif4-Regular.ttf",
          weight=400, opsz=20)
    build("SourceSerif4.ttf", TEXT, "SourceSerif4-SemiBold.ttf",
          weight=600, opsz=20)
    build("Onest.ttf", TEXT, "Onest-Medium.ttf", weight=500)
    build("Onest.ttf", TEXT, "Onest-Bold.ttf", weight=700)
    build("Caveat.ttf", HAND, "Caveat.ttf", weight=600)

    # Проверка: ни один символ, который раньше рисовался этими шрифтами,
    # не должен пропасть. Символы, которых в них не было и раньше, и так
    # берутся из системного fallback — их трогать не нужно.
    text_codes = coverage(os.path.join(DST, "SourceSerif4-Regular.ttf"))
    text_codes |= coverage(os.path.join(DST, "Onest-Medium.ttf"))
    hand_codes = coverage(os.path.join(DST, "Caveat.ttf"))

    old_codes = coverage(os.path.join(SRC, "SourceSerif4.ttf"))
    old_codes |= coverage(os.path.join(SRC, "Onest.ttf"))

    used = set()
    for base, _, files in os.walk(ROOT):
        if os.sep + "build" in base or "fonts-src" in base:
            continue
        for name in files:
            if name.endswith((".java", ".xml", ".js")):
                with open(os.path.join(base, name), encoding="utf-8",
                          errors="replace") as fh:
                    used.update(fh.read())

    missing = sorted(c for c in used
                     if ord(c) >= 0x20 and ord(c) in old_codes
                     and ord(c) not in text_codes)
    if missing:
        sys.exit("потеряны символы, которые были в шрифтах: %s"
                 % " ".join("U+%04X" % ord(c) for c in missing))
    lost_hand = sorted(c for c in HAND if c not in hand_codes)
    if lost_hand:
        sys.exit("Caveat потерял: %s" % "".join(map(chr, lost_hand)))
    print("== проверка: все %d символов исходников на месте" % len(used))


if __name__ == "__main__":
    main()
