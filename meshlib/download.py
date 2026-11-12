"""Скачивание прикреплённых материалов ДЗ МЭШ в ~/DZ.

Файлы раскладываются по ISO-неделям, как уже принято в ~/DZ:

    ~/DZ/week45_02.11-08.11/06.11_Физика_Задачи 02.11docx.pdf

Имя начинается с даты и предмета, поэтому файлы разных уроков не
пересекаются.  Повторный запуск не скачивает уже сохранённое; ``--force``
перекачивает заново.
"""
from __future__ import annotations

import os
import re
import sys
from dataclasses import dataclass
from datetime import date as _date, timedelta
from pathlib import Path
from urllib.parse import unquote, urlparse

from . import render
from .client import MesApiError, MesAuthError, MesClient

DEFAULT_ROOT = Path.home() / "DZ"
FORBIDDEN = re.compile(r'[<>:"/\\|?*\x00-\x1f\x7f]')


# ------------------------------------------------------------------ пути

def download_root(override: str | os.PathLike[str] | None = None) -> Path:
    if override:
        return Path(override).expanduser()
    env = os.environ.get("SCH_DZ_DIR", "").strip()
    if env:
        return Path(env).expanduser()
    return DEFAULT_ROOT


def week_folder(day: _date) -> str:
    monday = day - timedelta(days=day.weekday())
    sunday = monday + timedelta(days=6)
    return (f"week{day.isocalendar().week:02d}_"
            f"{monday:%d.%m}-{sunday:%d.%m}")


def _truncate_utf8(text: str, limit: int) -> str:
    data = text.encode("utf-8")
    if len(data) <= limit:
        return text
    data = data[:max(0, limit)]
    while data:
        try:
            return data.decode("utf-8")
        except UnicodeDecodeError:
            data = data[:-1]
    return ""


def safe_name(text: str, limit: int = 120) -> str:
    text = unquote(text or "")
    text = FORBIDDEN.sub("_", text)
    text = " ".join(text.split())
    text = text.strip(" .")
    text = _truncate_utf8(text, limit).strip(" .")
    return text or "файл"


def _url_name(url: str) -> str:
    try:
        return unquote(Path(urlparse(url).path).name)
    except Exception:
        return ""


def build_name(item: "FileRef") -> str:
    """Имя файла: ``06.11_Физика_Задачи 02.11docx.pdf``."""
    try:
        day = _date.fromisoformat(item.date)
    except ValueError:
        day = _date.today()
    url_name = safe_name(_url_name(item.url), 160)
    title = safe_name(item.title or url_name, 160)
    stem, ext = os.path.splitext(title)
    if not ext:
        _, ext = os.path.splitext(url_name)
    if len(ext) > 16 or "\n" in ext:
        ext = ""
    subject = safe_name(item.subject, 40)
    prefix = f"{day:%d.%m}_{subject}_"
    # 250, а не 255: оставляем место под суффикс .part при скачивании.
    room = 250 - len(prefix.encode("utf-8")) - len(ext.encode("utf-8"))
    stem = _truncate_utf8(stem, max(24, room)).strip(" .")
    return f"{prefix}{stem or 'файл'}{ext}"


def human_size(n: int) -> str:
    if n < 1024:
        return f"{n} Б"
    value = float(n)
    for unit in ("КБ", "МБ", "ГБ"):
        value /= 1024
        if value < 1024 or unit == "ГБ":
            return f"{value:.1f} {unit}".replace(".0 ", " ")
    return f"{n} Б"


# ------------------------------------------------------------------ данные

@dataclass(frozen=True)
class FileRef:
    date: str
    subject: str
    title: str
    url: str
    kind: str = ""


@dataclass
class DownloadResult:
    item: FileRef
    path: Path
    status: str          # downloaded | exists | would | error
    size: int = 0
    error: str = ""


def files_from_homeworks(homeworks: list[dict]) -> list[FileRef]:
    """Достать файлы из сырых ДЗ МЭШ."""
    out: list[FileRef] = []
    seen: set[str] = set()
    for hw in homeworks:
        iso = hw.get("date") or ""
        for f in MesClient.files_from_homework(hw):
            url = (f.get("url") or "").strip()
            if not url or url in seen:
                continue
            seen.add(url)
            out.append(FileRef(
                date=iso,
                subject=hw.get("subject_name") or "",
                title=f.get("title") or "",
                url=url,
                kind=f.get("kind") or "",
            ))
    return out


def files_from_days(days: list[dict]) -> list[FileRef]:
    """Достать файлы из уже нормализованных дней ``client.day()/week()``."""
    out: list[FileRef] = []
    seen: set[str] = set()
    for day in days:
        iso = day.get("date") or ""
        for hw in day.get("homeworks") or []:
            for f in hw.get("files") or []:
                url = (f.get("url") or "").strip()
                if not url or url in seen:
                    continue
                seen.add(url)
                out.append(FileRef(
                    date=iso,
                    subject=hw.get("subject") or "",
                    title=f.get("title") or "",
                    url=url,
                    kind=f.get("kind") or "",
                ))
    return out


# ------------------------------------------------------------------ скачивание

def _unique_path(path: Path, used: set[Path]) -> Path:
    if path not in used:
        return path
    stem, ext = os.path.splitext(path.name)
    n = 2
    while True:
        candidate = path.with_name(f"{stem}_{n}{ext}")
        if candidate not in used:
            return candidate
        n += 1


def download_one(client: MesClient, item: FileRef, root: Path,
                 force: bool = False, dry_run: bool = False,
                 used: set[Path] | None = None) -> DownloadResult:
    try:
        day = _date.fromisoformat(item.date)
    except ValueError:
        day = _date.today()
    dest = root / week_folder(day) / build_name(item)
    if used is not None:
        dest = _unique_path(dest, used)
        used.add(dest)

    if dry_run:
        if force or not dest.exists():
            return DownloadResult(item, dest, "would")
        try:
            size = dest.stat().st_size
        except OSError:
            size = 0
        return DownloadResult(item, dest, "exists", size)
    if dest.exists() and not force:
        try:
            size = dest.stat().st_size
        except OSError:
            size = 0
        return DownloadResult(item, dest, "exists", size)
    try:
        size = client.download(item.url, dest)
        return DownloadResult(item, dest, "downloaded", size)
    except (MesAuthError, MesApiError, OSError) as exc:
        return DownloadResult(item, dest, "error", 0, str(exc))


def sync_items(client: MesClient, items: list[FileRef], root: Path,
               force: bool = False, dry_run: bool = False) -> list[DownloadResult]:
    used: set[Path] = set()
    return [download_one(client, item, root, force, dry_run, used)
            for item in items]


def auto_sync(client: MesClient, days: list[dict],
              stream=None) -> list[DownloadResult]:
    """Тихо скачать материалы показанных дней; отчёт — в stderr.

    Ошибки скачивания не должны ломать показ дневника, поэтому здесь
    ловим всё: главное — чтобы `sch today`/`sch week` отработали.
    """
    stream = stream or sys.stderr
    try:
        items = files_from_days(days)
        if not items:
            return []
        root = download_root()
        results = sync_items(client, items, root)
    except Exception as exc:  # noqa: BLE001 - фоновое скачивание
        print(f"⚠ Материалы ДЗ: {exc}", file=stream)
        return []
    for res in results:
        if res.status == "downloaded":
            print(f"📎 {_rel(res.path, root)}  ({human_size(res.size)})",
                  file=stream)
        elif res.status == "error":
            print(f"⚠ Материалы ДЗ: {res.item.subject}: {res.error}",
                  file=stream)
    return results


# ------------------------------------------------------------------ вывод

def _rel(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def _counts(results: list[DownloadResult]) -> dict:
    counts = {"downloaded": 0, "exists": 0, "would": 0, "error": 0}
    for res in results:
        counts[res.status] = counts.get(res.status, 0) + 1
    return counts


def format_text(results: list[DownloadResult], root: Path) -> str:
    if not results:
        return "Материалов для скачивания нет."
    lines = [render.bold(f"Материалы ДЗ → {root}")]
    for res in results:
        rel = _rel(res.path, root)
        if res.status == "downloaded":
            lines.append(f"  {render.green('+')} {rel}  "
                         f"({human_size(res.size)})")
        elif res.status == "exists":
            lines.append(f"  {render.dim('=')} {rel}  "
                         f"(уже есть, {human_size(res.size)})")
        elif res.status == "would":
            lines.append(f"  {render.yellow('→')} {rel}  (будет скачано)")
        else:
            lines.append(f"  {render.red('!')} {rel}  {res.error}")
    c = _counts(results)
    parts = [f"скачано {c['downloaded']}"]
    if c["exists"]:
        parts.append(f"уже было {c['exists']}")
    if c["would"]:
        parts.append(f"к скачиванию {c['would']}")
    if c["error"]:
        parts.append(f"ошибок {c['error']}")
    lines.append("Итог: " + ", ".join(parts) + ".")
    return "\n".join(lines)


def format_md(results: list[DownloadResult], root: Path) -> str:
    if not results:
        return "# Материалы ДЗ\n\nМатериалов для скачивания нет."
    lines = ["# Материалы ДЗ", ""]
    for res in results:
        rel = _rel(res.path, root)
        try:
            ddmm = _date.fromisoformat(res.item.date).strftime("%d.%m")
        except ValueError:
            ddmm = res.item.date
        status = {
            "downloaded": f"скачано, {human_size(res.size)}",
            "exists": "уже есть",
            "would": "будет скачано",
            "error": f"ошибка: {res.error}",
        }.get(res.status, res.status)
        lines.append(f"- **{ddmm}, {res.item.subject}** — `{rel}` — {status}")
    return "\n".join(lines)


def results_json(results: list[DownloadResult], root: Path) -> dict:
    return {
        "root": str(root),
        "items": [{
            "date": res.item.date,
            "subject": res.item.subject,
            "title": res.item.title,
            "url": res.item.url,
            "path": str(res.path),
            "status": res.status,
            "size": res.size,
            "error": res.error or None,
        } for res in results],
        "summary": _counts(results),
    }
