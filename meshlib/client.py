"""HTTP-клиент дневника МЭШ (school.mos.ru) — то самое «МЭШ API»."""
from __future__ import annotations

import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import requests

from . import cookies as cookie_store

BASE = "https://school.mos.ru"
API = BASE + "/api/family/web/v1"

UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36")


class MesAuthError(RuntimeError):
    """Сессия МЭШ недействительна (нужно обновить доступ)."""


class MesApiError(RuntimeError):
    """Ошибка на стороне API МЭШ."""


class MesClient:
    def __init__(self, cks: list[cookie_store.Cookie], source: str = "",
                 timeout: int = 25):
        self.source = source
        self.timeout = timeout
        self._cks = cks
        self.s = requests.Session()
        self.s.headers.update({
            "User-Agent": UA,
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
            "Referer": BASE + "/diary",
            "X-Mes-RoleId": "1",
            "X-mes-subsystem": "familyweb",
            "Profile-Id": "",
            "Profile-Type": "",
        })
        self.token = None
        self._sid = None
        for c in cks:
            if c.name == "aupd_token" and c.host.endswith(".mos.ru"):
                self.token = c.value
            if c.name == "active_student" and c.host.endswith("school.mos.ru"):
                sid = (c.value or "").strip()
                if sid.isdigit():
                    self._sid = sid
            self.s.cookies.set(c.name, c.value,
                               domain=c.host.lstrip(".") if c.host.startswith(".")
                               else c.host,
                               path=c.path or "/", secure=c.secure)
        if not self.token:
            raise MesAuthError(
                "в cookies нет токена aupd_token. Открой school.mos.ru в "
                "Chromium и войди, затем выполни `sch login`.")
        self.s.headers["Authorization"] = "Bearer " + self.token

    # ------------------------------------------------------------ фабрики

    @classmethod
    def from_local(cls) -> "MesClient":
        cks, info = cookie_store.load_best()
        return cls(cks, source=info.get("source", ""))

    # ------------------------------------------------------------ низкий уровень

    def _get(self, path: str, params: dict | None = None, raw: bool = False):
        url = path if path.startswith("http") else API + path
        last_exc = None
        for attempt in (1, 2):
            try:
                r = self.s.get(url, params=params, timeout=self.timeout)
            except requests.RequestException as e:
                last_exc = e
                if attempt == 1:
                    time.sleep(1)
                    continue
                raise MesApiError(f"сеть недоступна: {e}") from e
            if r.status_code in (401, 403):
                raise MesAuthError(
                    "МЭШ отклонил сессию (сессия истекла). "
                    "Выполни `sch login` или открой school.mos.ru в Chromium.")
            if r.status_code >= 500 and attempt == 1:
                time.sleep(1)
                continue
            if r.status_code != 200:
                raise MesApiError(f"HTTP {r.status_code}: {r.text[:200]}")
            return r.json()
        raise MesApiError(f"не дозвонился до МЭШ: {last_exc}")

    def download(self, url: str, dest: Path) -> int:
        """Скачать файл по авторизованной сессии. Вернуть размер в байтах."""
        tmp = dest.with_name(dest.name + ".part")
        last_exc = None
        try:
            dest.parent.mkdir(parents=True, exist_ok=True)
            for attempt in (1, 2):
                try:
                    with self.s.get(url, stream=True,
                                    timeout=self.timeout) as r:
                        if r.status_code in (401, 403):
                            raise MesAuthError(
                                "МЭШ отклонил сессию при скачивании. "
                                "Выполни `sch login`.")
                        if r.status_code >= 500 and attempt == 1:
                            last_exc = MesApiError(f"HTTP {r.status_code}")
                            time.sleep(1)
                            continue
                        if r.status_code != 200:
                            raise MesApiError(
                                f"HTTP {r.status_code} при скачивании {url}")
                        size = 0
                        with open(tmp, "wb") as f:
                            for chunk in r.iter_content(64 * 1024):
                                if chunk:
                                    f.write(chunk)
                                    size += len(chunk)
                    tmp.replace(dest)
                    return size
                except requests.RequestException as e:
                    last_exc = e
                    if attempt == 1:
                        time.sleep(1)
                        continue
                    raise MesApiError(f"сеть недоступна: {e}") from e
            raise MesApiError(f"не скачалось: {url} ({last_exc})")
        finally:
            try:
                if tmp.exists():
                    tmp.unlink()
            except OSError:
                pass

    # ------------------------------------------------------------ файлы

    @staticmethod
    def files_from_homework(hw: dict) -> list[dict]:
        """Прикреплённые к ДЗ файлы: [{'title', 'url', 'kind'}] без повторов."""
        out: list[dict] = []
        seen: set[str] = set()

        def add(url, title, kind=""):
            url = (url or "").strip()
            if not url.startswith(("http://", "https://")) or url in seen:
                return
            seen.add(url)
            out.append({
                "title": (title or "").strip(),
                "url": url,
                "kind": (kind or "").strip(),
            })

        for m in hw.get("materials") or []:
            if not isinstance(m, dict):
                continue
            kind = m.get("type_name") or m.get("type") or ""
            for u in m.get("urls") or []:
                if isinstance(u, dict):
                    if u.get("type") not in (None, "file_link"):
                        continue
                    add(u.get("url"), m.get("title") or u.get("title"), kind)
                elif isinstance(u, str):
                    add(u, m.get("title"), kind)

        def walk(node):
            if isinstance(node, dict):
                add(node.get("url") or node.get("file_url")
                    or node.get("link"),
                    node.get("title") or node.get("name"),
                    node.get("type_name") or "attachment")
                for v in node.values():
                    if isinstance(v, (dict, list)):
                        walk(v)
            elif isinstance(node, list):
                for x in node:
                    walk(x)

        walk(hw.get("attachments") or [])
        return out

    # ------------------------------------------------------------ запросы

    def profile(self) -> dict:
        return self._get("/profile")

    def student_id(self) -> str:
        if self._sid:
            return self._sid
        p = self.profile()
        prof = p.get("profile") or {}
        sid = prof.get("id")
        if not sid:
            kids = p.get("children") or []
            if kids:
                sid = kids[0].get("id")
        if not sid:
            raise MesApiError("не могу определить student_id из профиля")
        self._sid = str(sid)
        return self._sid

    def schedule_day(self, date: str | None = None) -> dict:
        """День как в интерфейсе: уроки, перемены, кабинеты, учителя, ДЗ."""
        params = {"student_id": self.student_id()}
        if date:
            params["date"] = date
        return self._get("/schedule", params)

    def schedule_short(self, dates: list[str]) -> list[dict]:
        data = self._get("/schedule/short",
                         {"student_id": self.student_id(),
                          "dates": ",".join(dates)})
        return data.get("payload") or []

    def homeworks(self, frm: str, to: str) -> list[dict]:
        data = self._get("/homeworks",
                         {"student_id": self.student_id(),
                          "from": frm, "to": to})
        return data.get("payload") or []

    def marks(self, frm: str, to: str) -> list[dict]:
        data = self._get("/marks",
                         {"student_id": self.student_id(),
                          "from": frm, "to": to})
        return data.get("payload") or []

    # ------------------------------------------------------------ день целиком

    def day(self, date: str | None = None) -> dict:
        """Нормализованный день: уроки + доп. занятия + ДЗ.

        date=None → сегодняшний день по мнению сервера МЭШ (надёжнее
        локальных часов на границе суток).
        """
        full = self.schedule_day(date)
        day_date = full.get("date")
        short = []
        try:
            short = self.schedule_short([day_date]) if day_date else []
        except (MesApiError, MesAuthError):
            short = []  # темы не критичны
        hw = self.homeworks(day_date, day_date) if day_date else []
        return self._normalize_day(full, short, hw)

    def week(self, start: str, days: int = 7) -> list[dict]:
        from datetime import date as _d, timedelta
        d0 = _d.fromisoformat(start)
        dates = [(d0 + timedelta(days=i)).isoformat() for i in range(days)]
        short = []
        try:
            short = self.schedule_short(dates)
        except (MesApiError, MesAuthError):
            short = []
        hw = []
        try:
            hw = self.homeworks(dates[0], dates[-1])
        except (MesApiError, MesAuthError):
            hw = []

        def one(d: str):
            c = None
            try:
                c = MesClient(self._cks, source=self.source)
                return self._normalize_day(c.schedule_day(d), short, hw)
            except (MesApiError, MesAuthError) as e:
                return {"date": d, "error": str(e)}
            finally:
                if c is not None:
                    c.close()

        # уроки каждого дня качаем параллельно (у каждого потока свой Session)
        with ThreadPoolExecutor(max_workers=6) as ex:
            return list(ex.map(one, dates))

    def close(self) -> None:
        self.s.close()

    # ------------------------------------------------------------ нормализация

    @staticmethod
    def _normalize_day(full: dict, short_payload: list[dict],
                       hw_payload: list[dict]) -> dict:
        day_date = full.get("date")

        topic_by_id, group_by_id, group_by_time = {}, {}, {}
        for day in short_payload or []:
            for l in day.get("lessons") or []:
                key = l.get("schedule_item_id")
                if key:
                    topic_by_id[key] = (l.get("lesson_name") or "").strip()
                    group_by_id[key] = l.get("group_name")
                gb = l.get("group_name") or l.get("subject_name")
                if gb:
                    group_by_time.setdefault(l.get("begin_time"), gb)

        lessons, extra = [], []
        for a in full.get("activities") or []:
            if a.get("type") != "LESSON":
                continue
            l = a.get("lesson") or {}
            item_id = l.get("schedule_item_id")
            edu = l.get("lesson_education_type")
            topic = topic_by_id.get(item_id) or ""
            row = {
                "begin": a.get("begin_time"),
                "end": a.get("end_time"),
                "subject": l.get("subject_name") or "",
                "group": group_by_id.get(item_id) or group_by_time.get(a.get("begin_time")),
                "topic": topic,
                "room": a.get("room_number"),
                "room_name": a.get("room_name"),
                "building": a.get("building_name"),
                "teacher": MesClient._teacher(l.get("teacher")),
                "marks": [str(m.get("value")) for m in (l.get("marks") or [])
                          if m.get("value") not in (None, "", "точка")],
                "homework": (l.get("homework") or "").strip(),
                "cancelled": bool(l.get("is_cancelled")),
                "replaced": bool(l.get("replaced")),
                "virtual": bool(l.get("is_virtual")),
            }
            if not row["subject"] and row["group"]:
                row["subject"] = row["group"]
            if edu in ("AE", "EC") or not l.get("subject_name"):
                row["kind"] = "extra"
                extra.append(row)
            else:
                row["kind"] = "lesson"
                lessons.append(row)

        for i, row in enumerate(lessons, 1):
            row["n"] = i

        homeworks = []
        for x in hw_payload or []:
            if x.get("date") != day_date:
                continue
            text = (x.get("description") or x.get("homework") or "").strip()
            mats = x.get("materials") or []
            homeworks.append({
                "subject": x.get("subject_name") or "",
                "text": text,
                "done": bool(x.get("is_done")),
                "has_teacher_answer": bool(x.get("has_teacher_answer")),
                "attachments": len(x.get("attachments") or []),
                "materials": len(mats),
                "files": MesClient.files_from_homework(x),
                "lesson_time": (x.get("lesson_date_time") or "")[11:16],
                "type": x.get("type"),
            })
        homeworks.sort(key=lambda h: (h.get("lesson_time") or ""))

        return {
            "date": day_date,
            "summary": full.get("summary") or "",
            "lessons": lessons,
            "extra": extra,
            "homeworks": homeworks,
        }

    @staticmethod
    def _teacher(t: dict | None) -> str:
        if not t:
            return ""
        last = t.get("last_name") or ""
        first = (t.get("first_name") or "")[:1]
        middle = (t.get("middle_name") or "")[:1]
        initials = ".".join(x for x in (first, middle) if x)
        return (last + (" " + initials + "." if initials else "")).strip()
