"""Команда `sch` — дневник МЭШ в терминале."""
from __future__ import annotations

import json
import os
import sys
import time
from datetime import date as _date, datetime, timedelta

from . import __version__, download, render
from . import cookies as cookie_store
from .client import MesApiError, MesAuthError, MesClient
from .dates import parse_day

SECTION_ALIASES = {
    "lessons": "lessons", "lesson": "lessons", "уроки": "lessons",
    "урок": "lessons",
    "hw": "hw", "homework": "hw", "дз": "hw", "домашка": "hw",
    "задания": "hw",
}

HELP = """sch — дневник МЭШ в терминале (личный доступ с этого компьютера)

День:
  sch [день] [раздел]
    sch today              сегодня: уроки + ДЗ
    sch today lessons      только уроки на сегодня
    sch tmrw hw            только ДЗ на завтра
    День:  today | tmrw (завтра) | +2 | 2026-11-10 | пн..вс
    Раздел: (пусто) = всё | lessons = уроки | hw = ДЗ

Ещё:
  sch week [дней]        сводка на ближайшие N дней (по умолчанию 7)
  sch marks [дней]       оценки за последние N дней (по умолчанию 14)
  sch profile            кто я в МЭШ
  sch login              обновить доступ из сессии Chromium
  sch doctor             диагностика доступа
  sch serve              локальный JSON API на 127.0.0.1:8787

Материалы ДЗ (скачиваются в ~/DZ):
  sch dz                 файлы ДЗ за сегодня
  sch dz tmrw            за завтра
  sch dz 2026-11-10      за конкретный день
  sch dz week [дней]     за ближайшие N дней (по умолчанию 7)
  Флаги: --out DIR | --force | --dry-run

Флаги:
  --json, -j      машинный JSON
  --raw-md, --md  вывод в Markdown (для сохранения в .md-файлы)
  --no-md         обычный текст без разметки и цвета
  --no-color      отключить ANSI-цвета
  --no-download   не скачивать материалы автоматически при показе ДЗ
"""


def _fail(msg: str, code: int = 1) -> int:
    print(render.red(msg))
    return code


def _client() -> MesClient:
    return MesClient.from_local()


# ------------------------------------------------------------------ день

def cmd_day(args: list[str], fmt: str, no_download: bool = False) -> int:
    day_iso, day_given, section = None, False, None
    for tok in args:
        t = tok.lower()
        if t in SECTION_ALIASES:
            if section:
                return _fail(f"раздел указан дважды: {tok}")
            section = SECTION_ALIASES[t]
            continue
        d = parse_day(tok)
        if d is not False:
            if day_given:
                return _fail(f"лишний аргумент: {tok}")
            day_iso, day_given = d, True
            continue
        return _fail(f"не понимаю аргумент: {tok}\n"
                     f"Примеры: sch today lessons | sch tmrw hw | sch +2 | sch week")
    client = _client()
    try:
        day = client.day(day_iso)
        if fmt == "json":
            print(json.dumps(day, ensure_ascii=False, indent=1))
            return 0
        show = {"lessons": ("lessons",), "hw": ("hw",)}.get(
            section, ("lessons", "hw"))
        if fmt == "md":
            print(render.fmt_day_md(day, show))
        else:
            print(render.fmt_day(day, show))
        if "hw" in show and not no_download:
            download.auto_sync(client, [day])
        return 0
    finally:
        client.close()


# ------------------------------------------------------------------ неделя

def cmd_week(args: list[str], fmt: str, no_download: bool = False) -> int:
    n = 7
    if args:
        if not args[0].isdigit():
            return _fail("sch week [дней] — например: sch week 10")
        n = int(args[0])
        args = args[1:]
    if args:
        return _fail(f"лишний аргумент: {args[0]}")
    if n <= 0:
        return _fail("число дней должно быть больше нуля")
    client = _client()
    try:
        days = client.week(_date.today().isoformat(), n)
        if fmt == "json":
            print(json.dumps(days, ensure_ascii=False, indent=1))
            return 0
        if fmt == "md":
            print(render.fmt_week_md(days))
        else:
            print(render.fmt_week(days))
        if not no_download:
            download.auto_sync(client, days)
        return 0
    finally:
        client.close()


# ------------------------------------------------------------------ оценки

def cmd_marks(args: list[str], fmt: str) -> int:
    n = 14
    if args:
        if not args[0].isdigit():
            return _fail("sch marks [дней] — например: sch marks 30")
        n = int(args[0])
        args = args[1:]
    if args:
        return _fail(f"лишний аргумент: {args[0]}")
    today = _date.today()
    frm = (today - timedelta(days=n - 1)).isoformat()
    to = today.isoformat()
    items = _client().marks(frm, to)
    items = [m for m in items if m.get("value") not in (None, "")]
    items.sort(key=lambda m: (m.get("date") or "", m.get("created_at") or ""),
               reverse=True)
    if fmt == "json":
        print(json.dumps(items, ensure_ascii=False, indent=1))
        return 0
    if fmt == "md":
        print(render.fmt_marks_md(items, frm, to))
        return 0
    print(render.fmt_marks(items, frm, to))
    return 0


# ------------------------------------------------------------------ материалы

def cmd_dz(args: list[str], fmt: str) -> int:
    """Скачать прикреплённые файлы ДЗ в ~/DZ (или --out DIR)."""
    out_dir, force, dry_run, rest = None, False, False, []
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("--out", "-o"):
            if i + 1 >= len(args):
                return _fail("--out требует каталог")
            out_dir = args[i + 1]
            i += 2
            continue
        if a.startswith("--out="):
            out_dir = a.split("=", 1)[1]
            i += 1
            continue
        if a in ("--force", "-f"):
            force = True
            i += 1
            continue
        if a in ("--dry-run", "-n"):
            dry_run = True
            i += 1
            continue
        rest.append(a)
        i += 1

    client = _client()
    try:
        if rest and rest[0].lower() in ("week", "неделя"):
            n = 7
            if len(rest) > 1:
                if not rest[1].isdigit():
                    return _fail("sch dz week [дней] — например: sch dz week 14")
                n = int(rest[1])
            if len(rest) > 2:
                return _fail(f"лишний аргумент: {rest[2]}")
            if n <= 0:
                return _fail("число дней должно быть больше нуля")
            start = _date.today()
            end = start + timedelta(days=n - 1)
            homeworks = client.homeworks(start.isoformat(), end.isoformat())
        else:
            day_iso, day_given = None, False
            for tok in rest:
                d = parse_day(tok)
                if d is False:
                    return _fail(f"не понимаю аргумент: {tok}\n"
                                 f"Примеры: sch dz | sch dz tmrw | "
                                 f"sch dz 2026-11-10 | sch dz week 7")
                if day_given:
                    return _fail(f"лишний аргумент: {tok}")
                day_iso, day_given = d, True
            if day_iso is None:
                day_iso = (client.schedule_day(None).get("date")
                           or _date.today().isoformat())
            homeworks = client.homeworks(day_iso, day_iso)

        items = download.files_from_homeworks(homeworks)
        root = download.download_root(out_dir)
        results = download.sync_items(client, items, root,
                                      force=force, dry_run=dry_run)
        if fmt == "json":
            print(json.dumps(download.results_json(results, root),
                             ensure_ascii=False, indent=1))
        elif fmt == "md":
            print(download.format_md(results, root))
        else:
            print(download.format_text(results, root))
        return 1 if any(r.status == "error" for r in results) else 0
    finally:
        client.close()


# ------------------------------------------------------------------ профиль

def cmd_profile(fmt: str) -> int:
    p = _client().profile()
    if fmt == "json":
        print(json.dumps(p, ensure_ascii=False, indent=1))
        return 0
    if fmt == "md":
        print(render.fmt_profile_md(p))
        return 0
    print(render.fmt_profile(p))
    return 0


# ------------------------------------------------------------------ доктор

def cmd_doctor(json_out: bool) -> int:
    report: dict = {"version": __version__}
    try:
        cks, info = cookie_store.load_best()
    except RuntimeError as e:
        if json_out:
            print(json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False,
                             indent=1))
        else:
            print(_fail(str(e)))
        return 1
    exp = info.get("exp")
    report.update({
        "cookies": len(cks),
        "source": info.get("source"),
        "profile": info.get("profile"),
        "token_exp": exp,
        "token_ok": bool(exp and exp > time.time()),
    })
    try:
        c = MesClient(cks, source=info.get("source", ""))
        p = c.profile()
        prof = p.get("profile") or {}
        report["api_ok"] = True
        report["who"] = " ".join(x for x in (prof.get("last_name"),
                                             prof.get("first_name")) if x)
    except (MesAuthError, MesApiError) as e:
        report["api_ok"] = False
        report["api_error"] = str(e)

    if json_out:
        print(json.dumps(report, ensure_ascii=False, indent=1))
        return 0 if report.get("api_ok") else 1

    print(render.bold("Диагностика sch"))
    print(f"  cookies: {len(cks)} шт. из {info.get('source')}")
    if info.get("profile"):
        print(f"  профиль: {info['profile']}")
    if exp:
        print(f"  токен до: {datetime.fromtimestamp(exp):%d.%m.%Y %H:%M}")
    if report.get("api_ok"):
        print(f"  {render.green('API: работает')} — {report.get('who', '')}")
        return 0
    print(f"  {render.red('API: не работает')} — {report.get('api_error')}")
    print("  Подсказка: открой school.mos.ru в Chromium и выполни `sch login`.")
    return 1


# ------------------------------------------------------------------ логин

def cmd_login(args: list[str]) -> int:
    print("Проверяю текущий доступ…")
    try:
        c = _client()
        c.profile()
        print(render.green("Доступ уже работает."), f"(источник: {c.source})")
        return 0
    except (MesAuthError, MesApiError, RuntimeError) as e:
        print(f"Текущий доступ не работает: {e}")

    old_exp = None
    try:
        cks, _info = cookie_store.load_best()
        old_exp = cookie_store.token_exp(cookie_store.find_token(cks))
    except Exception:
        pass

    print("Пробую молча обновить сессию через headless-копию Chromium "
          "(до минуты)…")
    from .login import refresh_via_headless
    cks = refresh_via_headless(older_than=old_exp)
    if not cks:
        return _fail("Не получилось автоматически. Открой school.mos.ru\n"
                     "в Chromium, войди (если просит), затем выполни `sch login`.")
    try:
        c = MesClient(cks)
        c.profile()
    except (MesAuthError, MesApiError) as e:
        return _fail(f"Свежие cookies не подошли: {e}\n"
                     f"Открой school.mos.ru в Chromium, войди ещё раз, "
                     f"потом `sch login`.")
    cookie_store.save_cache(cks)
    print(render.green("Готово:"), "доступ обновлён (кэш в ~/.cache/sch).")
    return 0


# ------------------------------------------------------------------ сервер

def cmd_serve(args: list[str]) -> int:
    host, port = "127.0.0.1", 8787
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--port" and i + 1 < len(args):
            port = int(args[i + 1]); i += 2; continue
        if a.startswith("--port="):
            port = int(a.split("=", 1)[1]); i += 1; continue
        if a == "--host" and i + 1 < len(args):
            host = args[i + 1]; i += 2; continue
        if a.startswith("--host="):
            host = a.split("=", 1)[1]; i += 1; continue
        return _fail(f"не понимаю аргумент: {a}")
    if host not in ("127.0.0.1", "localhost", "::1") and \
            os.environ.get("SCH_ALLOW_REMOTE") != "1":
        return _fail("Из соображений безопасности слушаю только 127.0.0.1.\n"
                     "Если правда нужно наружу: SCH_ALLOW_REMOTE=1 sch serve …")
    from .server import serve
    print(render.bold(f"МЭШ API запущен: http://{host}:{port}/"))
    print("  /day?date=today|tmrw|+2|2026-11-10   /lessons   /hw")
    print("  /profile   /homeworks?from=&to=   /marks?from=&to=")
    print("  /schedule?date=…  /schedule/short?dates=…   /health")
    print(render.dim("Ctrl+C — остановить."))
    try:
        serve(host, port)
    except KeyboardInterrupt:
        print()
    return 0


# ------------------------------------------------------------------ вход

def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    no_color = "--no-color" in argv
    argv = [a for a in argv if a != "--no-color"]
    no_download = ("--no-download" in argv
                   or os.environ.get("SCH_NO_DOWNLOAD") == "1")
    argv = [a for a in argv if a != "--no-download"]

    fmt_flags = {
        "--json": "json", "-j": "json",
        "--raw-md": "md", "--md": "md",
        "--no-md": "plain",
    }
    formats = []
    for flag, mode in fmt_flags.items():
        if flag in argv:
            argv = [a for a in argv if a != flag]
            formats.append(mode)
    formats = list(dict.fromkeys(formats))
    if len(formats) > 1:
        return _fail("выбери один формат: --json, --raw-md (--md) или --no-md")
    fmt = formats[0] if formats else "text"

    render.use_color(fmt == "text" and sys.stdout.isatty() and not no_color
                     and "NO_COLOR" not in os.environ)

    if not argv:
        argv = ["today"]
    cmd, rest = argv[0].lower(), argv[1:]
    try:
        if cmd in ("help", "-h", "--help", "справка"):
            print(HELP)
            return 0
        if cmd in ("version", "--version", "-v"):
            print(f"sch {__version__}")
            return 0
        if cmd == "login":
            return cmd_login(rest)
        if cmd == "doctor":
            return cmd_doctor(fmt == "json")
        if cmd == "profile":
            return cmd_profile(fmt)
        if cmd == "week":
            return cmd_week(rest, fmt, no_download)
        if cmd == "marks":
            return cmd_marks(rest, fmt)
        if cmd in ("dz", "materials", "files", "материалы", "скачать"):
            return cmd_dz(rest, fmt)
        if cmd == "serve":
            return cmd_serve(rest)
        return cmd_day(argv, fmt, no_download)
    except MesAuthError as e:
        return _fail(f"{e}\nПодсказка: выполни `sch login`.")
    except MesApiError as e:
        return _fail(f"Ошибка МЭШ: {e}")
    except RuntimeError as e:
        return _fail(str(e))
    except KeyboardInterrupt:
        return 130
