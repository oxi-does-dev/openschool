"""Локальный JSON API МЭШ — только для этого компьютера (127.0.0.1)."""
from __future__ import annotations

import json
import threading
import time
from datetime import date as _date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from . import __version__
from .client import MesApiError, MesAuthError, MesClient
from .dates import parse_day

_state = {"client": None}
_lock = threading.Lock()

ENDPOINTS = [
    "/day?date=today|tmrw|+2|2026-11-10",
    "/lessons?date=...", "/hw?date=...",
    "/profile", "/homeworks?from=&to=", "/marks?from=&to=",
    "/schedule?date=... (сырой день)", "/schedule/short?dates=a,b (сырое)",
    "/health",
]


def _client(force: bool = False) -> MesClient:
    with _lock:
        if _state["client"] is None or force:
            _state["client"] = MesClient.from_local()
        return _state["client"]


def _with_retry(fn):
    try:
        return fn(_client())
    except MesAuthError:
        return fn(_client(force=True))  # один повтор со свежими cookies из браузера


def _when(v: str | None):
    if v is None:
        return None
    v = v.strip()
    if v.isdigit():  # в URL «+2» превращается в пробел: date=%202
        v = "+" + v
    d = parse_day(v)
    if d is False:
        raise ValueError(f"не понимаю дату: {v!r} (можно today/tmrw/+N/YYYY-MM-DD)")
    return d


def _range(q: dict, default_days_back: int | None = None,
           default_days_ahead: int | None = None) -> tuple[str, str]:
    today = _date.today()
    frm = q.get("from") or (
        (today - timedelta(days=default_days_back - 1)).isoformat()
        if default_days_back else today.isoformat())
    to = q.get("to") or (
        (today + timedelta(days=default_days_ahead)).isoformat()
        if default_days_ahead is not None else today.isoformat())
    for name, value in (("from", frm), ("to", to)):
        try:
            _date.fromisoformat(value)
        except ValueError:
            raise ValueError(f"плохая дата {name}={value!r}, нужно YYYY-MM-DD")
    return frm, to


class Handler(BaseHTTPRequestHandler):
    server_version = "sch/" + __version__

    def log_message(self, fmt, *args):  # noqa: A003
        print(f"[sch] {self.address_string()} {fmt % args}", flush=True)

    def _send(self, code: int, obj) -> None:
        body = json.dumps(obj, ensure_ascii=False, indent=1).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = u.path.rstrip("/") or "/"
        try:
            obj = self._route(path, q)
        except MesAuthError as e:
            return self._send(401, {"error": str(e), "hint": "выполни: sch login"})
        except MesApiError as e:
            return self._send(502, {"error": str(e)})
        except ValueError as e:
            return self._send(400, {"error": str(e)})
        except KeyError as e:
            return self._send(404, {"error": f"неизвестный путь или параметр: {e}"})
        except Exception as e:  # noqa: BLE001
            return self._send(500, {"error": f"{type(e).__name__}: {e}"})
        self._send(200, obj)

    def _route(self, path: str, q: dict):
        if path == "/":
            return {"service": "sch", "version": __version__,
                    "info": "локальное МЭШ API — только с этого компьютера",
                    "endpoints": ENDPOINTS}
        if path == "/health":
            c = _client()
            exp = None
            try:
                from .cookies import find_token, token_exp
                exp = token_exp(getattr(c, "token", None))
            except Exception:
                pass
            return {"ok": True, "source": c.source, "token_exp": exp,
                    "token_ok": bool(exp and exp > time.time())}
        if path == "/profile":
            return _with_retry(lambda c: c.profile())
        if path == "/day":
            d = _when(q.get("date"))
            return _with_retry(lambda c: c.day(d))
        if path == "/lessons":
            d = _when(q.get("date"))
            return _with_retry(lambda c: c.day(d))["lessons"]
        if path == "/hw":
            d = _when(q.get("date"))
            return _with_retry(lambda c: c.day(d))["homeworks"]
        if path == "/homeworks":
            frm, to = _range(q, default_days_ahead=13)
            return _with_retry(lambda c: c.homeworks(frm, to))
        if path == "/marks":
            frm, to = _range(q, default_days_back=14)
            return _with_retry(lambda c: c.marks(frm, to))
        if path == "/schedule":
            d = _when(q.get("date"))
            return _with_retry(lambda c: c.schedule_day(d))
        if path == "/schedule/short":
            dates = q.get("dates") or "today"
            lst = []
            for x in dates.split(","):
                d = parse_day(x)
                if d is False:
                    raise ValueError(f"не понимаю дату: {x!r}")
                lst.append(d or _date.today().isoformat())
            return _with_retry(lambda c: {"payload": c.schedule_short(lst)})
        raise KeyError(f"неизвестный путь: {path}")


def serve(host: str = "127.0.0.1", port: int = 8787) -> None:
    httpd = ThreadingHTTPServer((host, port), Handler)
    httpd.serve_forever()
