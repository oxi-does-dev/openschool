#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""sch — дневник МЭШ в терминале. Запускай `sch help`."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)))

from meshlib.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
