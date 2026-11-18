#!/usr/bin/env python3
"""Пересобирает APK с zopfli: тот же deflate, но сжатый плотнее.

aapt2 и zip жмут zlib'ом уровня 9. Zopfli даёт на 4-5% меньше на тех же
данных, а данные тут — шрифты и dex, то есть почти весь вес. Формат
остаётся обычным deflate: Android распаковывает его без изменений.

Непожатые записи (resources.arsc, иконки) переносятся как есть —
zipalign после этого снова выравнивает их по 4 байта.

Если zopfli не установлен, скрипт молча падает на zlib уровня 9:
сборка не должна зависеть от лишнего пакета.

  python3 tools/recompress.py base.apk out.apk [classes.dex]
"""

import os
import sys
import zipfile
import zlib

try:
    from zopfli.zlib import compress as zopfli_compress
except ImportError:  # сборка без zopfli тоже должна работать
    zopfli_compress = None

_default_compressor = zipfile._get_compressor


class _BufferedZopfli:
    """Компрессор для zipfile: копит данные и отдаёт готовый raw deflate."""

    def __init__(self):
        self._buf = bytearray()

    def compress(self, data):
        self._buf += data
        return b""

    def flush(self):
        if not self._buf:
            return b""
        packed = zopfli_compress(bytes(self._buf), numiterations=15)
        # zopfli отдаёт zlib-поток: 2 байта заголовка и 4 байта adler32.
        # Внутри ZIP нужен «сырой» deflate без обёртки.
        if packed[:1] != b"\x78":
            raise ValueError("неожиданный формат zopfli")
        return packed[2:-4]


def _get_compressor(compress_type, compresslevel=None):
    if compress_type == zipfile.ZIP_DEFLATED and zopfli_compress is not None:
        return _BufferedZopfli()
    return _default_compressor(compress_type, compresslevel)


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    base, result = argv[1], argv[2]

    zipfile._get_compressor = _get_compressor

    zlib_total = 0
    with zipfile.ZipFile(base) as src, \
            zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            out = zipfile.ZipInfo(info.filename, info.date_time)
            out.compress_type = info.compress_type
            out.external_attr = info.external_attr
            out.create_system = info.create_system
            out.internal_attr = info.internal_attr
            if info.compress_type == zipfile.ZIP_DEFLATED:
                zlib_total += len(zlib.compress(data, 9))
            else:
                zlib_total += len(data)
            dst.writestr(out, data)

        for path in argv[3:]:
            with open(path, "rb") as fh:
                data = fh.read()
            info = zipfile.ZipInfo(os.path.basename(path))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            zlib_total += len(zlib.compress(data, 9))
            dst.writestr(info, data)

    size = os.path.getsize(result)
    saved = zlib_total - size
    print("  zlib-9 %d → %s %d байт (%s)"
          % (zlib_total, "zopfli" if zopfli_compress else "zlib-9", size,
             "−%d" % saved if saved > 0 else "0"))


if __name__ == "__main__":
    main(sys.argv)
