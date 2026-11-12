"""Вывод данных МЭШ по-русски: аккуратно, компактно, с цветом в терминале."""
from __future__ import annotations

import textwrap
from datetime import date as _date

MONTHS = ["января", "февраля", "марта", "апреля", "мая", "июня",
          "июля", "августа", "сентября", "октября", "ноября", "декабря"]
WEEKDAYS = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]

_COLOR = False


def use_color(on: bool) -> None:
    global _COLOR
    _COLOR = bool(on)


def _c(code: str, s: str) -> str:
    return f"\033[{code}m{s}\033[0m" if _COLOR else s


def bold(s: str) -> str:
    return _c("1", s)


def dim(s: str) -> str:
    return _c("2", s)


def green(s: str) -> str:
    return _c("32", s)


def yellow(s: str) -> str:
    return _c("33", s)


def red(s: str) -> str:
    return _c("31", s)


def cyan(s: str) -> str:
    return _c("36", s)


# ------------------------------------------------------------------ утилиты

def human_date(iso: str) -> str:
    d = _date.fromisoformat(iso)
    return f"{WEEKDAYS[d.weekday()]}, {d.day} {MONTHS[d.month - 1]} {d.year}"


def day_suffix(iso: str) -> str:
    delta = (_date.fromisoformat(iso) - _date.today()).days
    return {0: " — сегодня", 1: " — завтра", 2: " — послезавтра",
            -1: " — вчера"}.get(delta, "")


def _grade(v: str) -> str:
    if v == "5":
        return green(v)
    if v == "4":
        return yellow(v)
    if v in ("1", "2", "3"):
        return red(v)
    return cyan(v)


def _one_line(s: str) -> str:
    return " ".join((s or "").split())


def _short(s: str, n: int = 100) -> str:
    s = _one_line(s)
    return s if len(s) <= n else s[: n - 1].rstrip() + "…"


def _md(s: str) -> str:
    """Текст для markdown-таблицы: без переводов строк и без «|»."""
    return _one_line(s).replace("|", "\\|")


# ------------------------------------------------------------------ день

def fmt_day(day: dict, show=("lessons", "hw")) -> str:
    if day.get("error"):
        return red(f"Ошибка: {day['error']}")
    iso = day.get("date") or "?"
    lines = [bold(human_date(iso) + day_suffix(iso))]
    if day.get("summary"):
        lines.append(dim(f"  {day['summary']}"))
    lines.append("")

    lessons = day.get("lessons") or []
    extra = day.get("extra") or []

    if "lessons" in show:
        if lessons:
            lines.append(bold("Уроки:"))
            for l in lessons:
                mark = ""
                if l.get("marks"):
                    mark = "  " + " ".join(_grade(m) for m in l["marks"])
                cancelled = red(" (отменён)") if l.get("cancelled") else ""
                room = f"  ·  каб. {l['room']}" if l.get("room") else ""
                subj = l.get("subject") or l.get("group") or "?"
                lines.append(f" {l.get('n', '')}. {cyan(l['begin'] + '–' + l['end'])}"
                             f"  {subj}{room}{mark}{cancelled}")
                topic = _one_line(l.get("topic"))
                if topic and topic.lower() != subj.lower():
                    wrapped = textwrap.fill(topic, width=86,
                                            initial_indent="      ",
                                            subsequent_indent="      ")
                    lines.append(dim(wrapped))
            lines.append("")
        elif "hw" not in show or not (extra or day.get("homeworks")):
            lines.append("Уроков нет.")
            lines.append("")

        if extra:
            lines.append(bold("Доп. занятия:"))
            for l in extra:
                title = _short(l.get("group") or l.get("subject") or "Занятие",
                               110)
                teacher = l.get("teacher") or ""
                if teacher and teacher.split()[0] not in title:
                    title += f"  ·  {teacher}"
                room = f"  ·  каб. {l['room']}" if l.get("room") else ""
                lines.append(f" {cyan(l['begin'] + '–' + l['end'])}  {title}{room}")
            lines.append("")

    if "hw" in show:
        homeworks = day.get("homeworks") or []
        if homeworks:
            lines.append(bold("ДЗ:"))
            for h in homeworks:
                mark = green("✓") if h.get("done") else "•"
                extra_bits = []
                n_mat = (h.get("attachments") or 0) + (h.get("materials") or 0)
                if n_mat:
                    extra_bits.append(f"материалов: {n_mat}")
                n_files = len(h.get("files") or [])
                if n_files:
                    extra_bits.append(f"файлов: {n_files}")
                suffix = (dim("  (" + ", ".join(extra_bits) + ")")
                          if extra_bits else "")
                prefix = f" {mark} {h.get('subject') or '?'} — "
                body = _one_line(h.get("text")) or "(см. материалы)"
                lines.append(textwrap.fill(
                    body, width=98, initial_indent=prefix,
                    subsequent_indent="    ") + suffix)
            lines.append("")
        else:
            lines.append("Заданий нет.")
            lines.append("")

    return "\n".join(lines).rstrip()


# ------------------------------------------------------------------ день (md)

def fmt_day_md(day: dict, show=("lessons", "hw"), level: int = 1) -> str:
    if day.get("error"):
        return f"> Ошибка: {day['error']}"
    iso = day.get("date") or "?"
    h1 = "#" * level
    h2 = "#" * (level + 1)
    lines = [f"{h1} {human_date(iso)}{day_suffix(iso)}", ""]
    if day.get("summary"):
        lines += [f"*{_md(day['summary'])}*", ""]
    lessons = day.get("lessons") or []
    extra = day.get("extra") or []
    if "lessons" in show:
        lines += [f"{h2} Уроки", ""]
        if lessons:
            lines += ["| № | Время | Предмет | Каб. | Тема |",
                      "|---|-------|---------|------|------|"]
            for l in lessons:
                subj = _md(l.get("subject") or l.get("group") or "?")
                if l.get("cancelled"):
                    subj += " *(отменён)*"
                if l.get("marks"):
                    subj += " " + " ".join(f"**{m}**" for m in l["marks"])
                room = _md(l.get("room") or "—")
                topic = _md(l.get("topic")) or "—"
                lines.append(f"| {l.get('n', '')} | {l['begin']}–{l['end']} | "
                             f"{subj} | {room} | {topic} |")
        else:
            lines.append("Уроков нет.")
        lines.append("")
        if extra:
            lines += [f"{h2} Доп. занятия", ""]
            for l in extra:
                title = _md(l.get("group") or l.get("subject") or "Занятие")
                room = f" · каб. {_md(l['room'])}" if l.get("room") else ""
                lines.append(f"- **{l['begin']}–{l['end']}** — {title}{room}")
            lines.append("")
    if "hw" in show:
        hws = day.get("homeworks") or []
        lines += [f"{h2} Домашние задания", ""]
        if hws:
            for h in hws:
                done = "x" if h.get("done") else " "
                subject = _md(h.get("subject") or "?")
                text = _md(h.get("text")) or "*(см. материалы)*"
                n_mat = (h.get("attachments") or 0) + (h.get("materials") or 0)
                n_files = len(h.get("files") or [])
                bits = []
                if n_mat:
                    bits.append(f"материалов: {n_mat}")
                if n_files:
                    bits.append(f"файлов: {n_files}")
                suffix = f" *({', '.join(bits)})*" if bits else ""
                lines.append(f"- [{done}] **{subject}** — {text}{suffix}")
        else:
            lines.append("Заданий нет.")
        lines.append("")
    return "\n".join(lines).rstrip()


# ------------------------------------------------------------------ неделя

def fmt_week(days: list[dict]) -> str:
    out = []
    for d in days:
        if d.get("error"):
            out.append(bold(human_date(d.get("date", "?"))) + red("  ошибка"))
            continue
        iso = d.get("date") or "?"
        out.append(bold(human_date(iso) + day_suffix(iso)))
        lessons = d.get("lessons") or []
        if not lessons:
            out.append(dim("   уроков нет"))
        for l in lessons:
            mark = ("  " + " ".join(_grade(m) for m in l["marks"])
                    if l.get("marks") else "")
            room = f"  каб. {l['room']}" if l.get("room") else ""
            subj = l.get("subject") or l.get("group") or "?"
            out.append(f"  {l.get('n', '')}. {l['begin']}  {subj}{room}{mark}")
        extra = d.get("extra") or []
        for l in extra:
            title = _short(l.get("group") or l.get("subject") or "Занятие", 100)
            out.append(dim(f"     + {l['begin']}  {title}"))
        hws = d.get("homeworks") or []
        if hws:
            subs = []
            seen = set()
            for h in hws:
                s = h.get("subject") or "?"
                if s not in seen:
                    subs.append(s)
                    seen.add(s)
            out.append(dim(f"     ДЗ: {', '.join(subs)}"))
        out.append("")
    return "\n".join(out).rstrip()


def _range_human(a: str, b: str) -> str:
    da, db = _date.fromisoformat(a), _date.fromisoformat(b)
    if da == db:
        return f"{da.day} {MONTHS[da.month - 1]} {da.year}"
    if da.year == db.year and da.month == db.month:
        return f"{da.day}–{db.day} {MONTHS[db.month - 1]} {db.year}"
    return (f"{da.day} {MONTHS[da.month - 1]} — "
            f"{db.day} {MONTHS[db.month - 1]} {db.year}")


def fmt_week_md(days: list[dict]) -> str:
    if not days:
        return "# МЭШ\n\nНет данных."
    iso_first = days[0].get("date") or ""
    iso_last = days[-1].get("date") or ""
    title = _range_human(iso_first, iso_last) if iso_first and iso_last else ""
    out = [f"# МЭШ: {title}", ""]
    for d in days:
        out.append(fmt_day_md(d, ("lessons", "hw"), level=2))
        out.append("")
    return "\n".join(out).rstrip()


# ------------------------------------------------------------------ оценки

def _dmy(iso: str) -> str:
    return f"{iso[8:10]}.{iso[5:7]}"


def fmt_marks(items: list[dict], frm: str, to: str) -> str:
    if not items:
        return f"Оценок с {_dmy(frm)} по {_dmy(to)} нет."
    lines = [bold(f"Оценки {_dmy(frm)}–{_dmy(to)}: {len(items)} шт.")]
    for m in items:
        dd = (m.get("date") or "")[8:10] + "." + (m.get("date") or "")[5:7]
        val = _grade(str(m.get("value")))
        weight = m.get("weight") or 1
        wtxt = f" ×{weight}" if weight and weight != 1 else ""
        form = m.get("control_form_name") or ""
        comment = _one_line(m.get("comment") or "")
        tail = f"  {form}" if form else ""
        if comment:
            tail += f"  {dim(comment)}"
        lines.append(f" {dd}  {val}{wtxt:<4} {m.get('subject_name') or '?'}{tail}")
    return "\n".join(lines)


def fmt_marks_md(items: list[dict], frm: str, to: str) -> str:
    lines = [f"# Оценки {_dmy(frm)}–{_dmy(to)}: {len(items)} шт.", ""]
    if not items:
        lines.append("Оценок нет.")
        return "\n".join(lines)
    lines += ["| Дата | Оценка | Предмет | Форма | Комментарий |",
              "|------|--------|---------|-------|-------------|"]
    for m in items:
        val = str(m.get("value") or "")
        weight = m.get("weight") or 1
        if weight and weight != 1:
            val += f" ×{weight}"
        lines.append("| " + " | ".join([
            _dmy(m.get("date") or ""),
            val,
            _md(m.get("subject_name") or ""),
            _md(m.get("control_form_name") or ""),
            _md(m.get("comment") or ""),
        ]) + " |")
    return "\n".join(lines)


# ------------------------------------------------------------------ профиль

def fmt_profile_md(p: dict) -> str:
    prof = p.get("profile") or {}
    kids = p.get("children") or []
    kid = kids[0] if kids else {}
    name = " ".join(x for x in (prof.get("last_name"), prof.get("first_name"),
                                prof.get("middle_name")) if x)
    school = (kid.get("school") or {}).get("short_name") or ""
    lines = [f"# {name or 'Без имени'}", ""]
    if kid.get("class_name"):
        lines.append(f"- **Класс:** {kid['class_name']}")
    if school:
        lines.append(f"- **Школа:** {school}")
    if prof.get("birth_date"):
        lines.append(f"- **Дата рождения:** {prof['birth_date']}")
    return "\n".join(lines)


def fmt_profile(p: dict) -> str:
    prof = p.get("profile") or {}
    kids = p.get("children") or []
    kid = kids[0] if kids else {}
    name = " ".join(x for x in (prof.get("last_name"), prof.get("first_name"),
                                prof.get("middle_name")) if x)
    school = (kid.get("school") or {}).get("short_name") or ""
    lines = [bold(name or "Без имени")]
    info = []
    if kid.get("class_name"):
        info.append(f"класс {kid['class_name']}")
    if school:
        info.append(school)
    if info:
        lines.append(" · ".join(info))
    if prof.get("birth_date"):
        lines.append(dim(f"дата рождения: {prof['birth_date']}"))
    return "\n".join(lines)
