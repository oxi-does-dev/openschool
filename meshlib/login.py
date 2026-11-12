"""Обновление доступа к МЭШ через одноразовую headless-копию браузера.

Если токен в браузере истёк, sometimes МЭШ сам молча обновляет его через
SSO login.mos.ru, пока сессия входа жива. Мы запускаем невидимый Chromium
с копией профиля, открываемschool.mos.ru, ждём свежий aupd_token в базе
копии и возвращаем cookies.
"""
from __future__ import annotations

import json
import os
import shutil
import socket
import subprocess
import time
import urllib.request

from . import cookies as cookie_store

CHROME_CANDIDATES = [
    "/usr/lib/chromium/chromium",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "/usr/bin/google-chrome",
    "/usr/bin/google-chrome-stable",
]

WORK_DIR = "/tmp/opencode/sch-login"


def _chrome_bin() -> str:
    for c in CHROME_CANDIDATES:
        if os.path.exists(c):
            return c
    raise RuntimeError("не нашёл Chromium/Chrome")


def _free_port(start: int = 9330) -> int:
    for port in range(start, start + 80):
        with socket.socket() as s:
            try:
                s.bind(("127.0.0.1", port))
                return port
            except OSError:
                continue
    raise RuntimeError("нет свободного порта")


def _http_json(url: str, timeout: float = 5):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return json.loads(r.read())


def refresh_via_headless(older_than: int | None = None,
                         timeout: int = 75) -> list[cookie_store.Cookie] | None:
    """Запускает копию браузера и пытается получить свежие cookies МЭШ.

    older_than — unix-время: считаем успехом токен со сроком больше этого.
    """
    prof = None
    for app, p, _mt in cookie_store.find_profiles():
        if cookie_store.find_token(cookie_store.extract_cookies(p)):
            prof = p
            break
    if not prof:
        for app, p, _mt in cookie_store.find_profiles():
            if cookie_store.extract_cookies(p):
                prof = p
                break
    if not prof:
        return None

    shutil.rmtree(WORK_DIR, ignore_errors=True)
    os.makedirs(WORK_DIR, exist_ok=True)
    cookie_store.copy_profile_for_headless(prof, WORK_DIR)

    port = _free_port()
    log = open(os.path.join(WORK_DIR, "chrome.log"), "w")
    chrome = subprocess.Popen([
        _chrome_bin(), "--headless=new",
        f"--user-data-dir={WORK_DIR}",
        "--disable-gpu", "--disable-software-rasterizer",
        "--no-first-run", "--remote-allow-origins=*",
        f"--remote-debugging-port={port}",
        "about:blank",
    ], stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.time() + 40
        while time.time() < deadline:
            try:
                _http_json(f"http://127.0.0.1:{port}/json/version", timeout=2)
                break
            except Exception:
                time.sleep(0.5)
        else:
            return None

        # открываем дневник в новой вкладке (PUT /json/new сработает и без тела)
        req = urllib.request.Request(
            f"http://127.0.0.1:{port}/json/new", method="PUT",
            headers={"Content-Length": "0"})
        try:
            tgt = _http_json(req.full_url)
            ws_url = tgt["webSocketDebuggerUrl"]
        except Exception:
            ws_url = None

        if ws_url:
            try:
                import websocket
                ws = websocket.create_connection(ws_url, timeout=15)
                ws.send(json.dumps({
                    "id": 1, "method": "Page.navigate",
                    "params": {"url": "https://school.mos.ru/diary/"}}))
                time.sleep(1)
                ws.close()
            except Exception:
                pass

        deadline = time.time() + timeout
        best = None
        old = older_than or (time.time() + 120)
        while time.time() < deadline:
            time.sleep(3)
            try:
                cks = cookie_store.extract_cookies(WORK_DIR)
            except Exception:
                continue
            tok = cookie_store.find_token(cks)
            exp = cookie_store.token_exp(tok)
            if tok and exp and exp > old + 60:
                return cks
            if tok:
                best = cks
        return best
    finally:
        chrome.terminate()
        try:
            chrome.wait(timeout=10)
        except Exception:
            chrome.kill()
        log.close()
        shutil.rmtree(WORK_DIR, ignore_errors=True)
