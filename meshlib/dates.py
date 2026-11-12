"""Разбор «человеческих» дат: today/tmrw/+3/пт/2026-11-10."""
from __future__ import annotations

from datetime import date as _date, timedelta

WEEKDAYS = {
    "пн": 0, "вт": 1, "ср": 2, "чт": 3, "пт": 4, "сб": 5, "вс": 6,
    "понедельник": 0, "вторник": 1, "среда": 2, "четверг": 3,
    "пятница": 4, "суббота": 5, "воскресенье": 6,
}


def parse_day(tok: str):
    """False — не дата; None — «сегодня» (пусть решает сервер МЭШ); иначе ISO-дата."""
    t = (tok or "").strip().lower()
    if not t:
        return False
    if t in ("today", "сегодня"):
        return None
    if t in ("tmrw", "tmr", "tomorrow", "завтра"):
        return (_date.today() + timedelta(days=1)).isoformat()
    if t.startswith("+") and t[1:].isdigit():
        return (_date.today() + timedelta(days=int(t[1:]))).isoformat()
    if t in WEEKDAYS:
        today = _date.today()
        delta = (WEEKDAYS[t] - today.weekday()) % 7
        return (today + timedelta(days=delta)).isoformat()
    try:
        return _date.fromisoformat(t).isoformat()
    except ValueError:
        return False
