"""Чтение cookies из локального Chromium/Chrome (Linux).

Cookies Chromium зашифрованы AES-128-CBC; ключ лежит в gnome-keyring
("Chromium Safe Storage"). Забираем его через `secret-tool` (или secretstorage
как запасной путь), расшифровываем только куки доменов *.mos.ru и отдаём наружу.

Ничего не пишем в профиль браузера — работаем с копией базы.
"""
from __future__ import annotations

import base64
import copy
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import tempfile
import time
from dataclasses import dataclass

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

CACHE_DIR = os.path.expanduser("~/.cache/sch")
CACHE_COOKIES = os.path.join(CACHE_DIR, "cookies.json")

BROWSERS = [
    ("chromium", "~/.config/chromium"),
    ("chrome", "~/.config/google-chrome"),
    ("brave", "~/.config/BraveSoftware/Brave-Browser"),
    ("vivaldi", "~/.config/vivaldi"),
]

WEBKIT_EPOCH = 11644473600  # секунды между 1601-01-01 и 1970-01-01


@dataclass
class Cookie:
    host: str
    name: str
    value: str
    path: str = "/"
    secure: bool = True
    expires: float | None = None


# ---------------------------------------------------------------- профили

def find_profiles() -> list[tuple[str, str, float]]:
    """Список (браузер, каталог профиля, mtime базы cookies), свежие первыми."""
    out = []
    for app, base in BROWSERS:
        base = os.path.expanduser(base)
        if not os.path.isdir(base):
            continue
        cands = ["Default"] + sorted(
            d for d in os.listdir(base) if d.startswith("Profile "))
        for cand in cands:
            db = os.path.join(base, cand, "Cookies")
            if os.path.exists(db):
                out.append((app, os.path.join(base, cand), os.path.getmtime(db)))
    out.sort(key=lambda x: x[2], reverse=True)
    return out


# ---------------------------------------------------------------- ключ

def _key_from_secret_tool() -> bytes | None:
    for app in ("chromium", "chrome", "brave", "vivaldi"):
        try:
            p = subprocess.run(
                ["secret-tool", "lookup", "xdg:schema",
                 "chrome_libsecret_os_crypt_password_v2", "application", app],
                capture_output=True, text=True, timeout=10)
        except Exception:
            continue
        if p.returncode == 0 and p.stdout.strip():
            return p.stdout.strip().encode()
    return None


def _key_from_secretstorage() -> bytes | None:
    try:
        import secretstorage
    except ImportError:
        return None
    for _ in range(3):
        try:
            conn = secretstorage.dbus_init()
            for c in secretstorage.get_all_collections(conn):
                if c.is_locked():
                    continue
                try:
                    items = list(c.get_all_items())
                except Exception:
                    continue  # битые/устаревшие элементы — пропускаем коллекцию
                for i in items:
                    try:
                        label = i.get_label()
                    except Exception:
                        continue
                    if "Safe Storage" in label and (
                            "Chromium" in label or "Chrome" in label):
                        return i.get_secret()
        except Exception:
            pass
        time.sleep(1)
    return None


def get_encryption_key() -> bytes:
    key = _key_from_secret_tool() or _key_from_secretstorage()
    if key:
        return key
    # Chromium, запущенный без связки ключей, шифрует ключом "peanuts"
    return b"peanuts"


def _derive(key: bytes) -> bytes:
    return hashlib.pbkdf2_hmac("sha1", key, b"saltysalt", 1, 16)


# ---------------------------------------------------------------- расшифровка

_IV = b" " * 16


def _printable(b: bytes) -> bool:
    return all(0x20 <= x <= 0x7E for x in b)


def decrypt_value(enc: bytes, derived: bytes) -> str:
    if enc[:3] not in (b"v10", b"v11"):
        return enc.decode("utf-8", "replace")
    dec = Cipher(algorithms.AES(derived), modes.CBC(_IV)).decryptor()
    pt = dec.update(enc[3:]) + dec.finalize()
    pad = pt[-1] if pt else 0
    if 0 < pad < 16:
        pt = pt[:-pad]
    # новые Chromium добавляют 32-байтовый "хэш-префикс" к значению
    if len(pt) > 32 and not _printable(pt[:32]):
        pt = pt[32:]
    return pt.decode("utf-8", "replace")


# ---------------------------------------------------------------- база cookies

def _backup_db(src: str, dst: str) -> bool:
    """Копия sqlite-базы (вместе с -wal/-shm), максимально аккуратно."""
    try:
        shutil.copy2(src, dst)
        for ext in ("-wal", "-shm"):
            if os.path.exists(src + ext):
                shutil.copy2(src + ext, dst + ext)
        return True
    except Exception:
        return False


def extract_cookies(profile_dir: str, hosts_suffix: tuple[str, ...] = ("mos.ru",),
                    key: bytes | None = None) -> list[Cookie]:
    """Достаёт cookies нужных доменов из профиля браузера."""
    src = os.path.join(profile_dir, "Cookies")
    if not os.path.exists(src):
        return []
    key = key or get_encryption_key()
    derived = _derive(key)

    tmpdir = tempfile.mkdtemp(prefix="sch-cookies-")
    dst = os.path.join(tmpdir, "Cookies")
    try:
        if not _backup_db(src, dst):
            return []
        con = sqlite3.connect(f"file:{dst}?mode=ro", uri=True)
        try:
            now = time.time()
            out: list[Cookie] = []
            for host, name, value, path, exp, secure, enc in con.execute(
                    "select host_key, name, value, path, expires_utc, is_secure,"
                    " encrypted_value from cookies"):
                if not host.endswith(hosts_suffix):
                    continue
                expires = (exp / 1_000_000) - WEBKIT_EPOCH if exp else None
                if expires and expires < now:
                    continue  # просроченные не нужны (сессионные: exp=0)
                if enc and enc[:3] in (b"v10", b"v11"):
                    val = decrypt_value(bytes(enc), derived)
                else:
                    val = value or (bytes(enc).decode("utf-8", "replace") if enc else "")
                out.append(Cookie(host=host, name=name, value=val,
                                  path=path or "/", secure=bool(secure),
                                  expires=expires))
            return out
        finally:
            con.close()
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


def token_exp(token: str | None) -> int | None:
    """exp из JWT-подобного токена (без проверки подписи)."""
    if not token or token.count(".") < 2:
        return None
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload))
        exp = data.get("exp")
        return int(exp) if exp else None
    except Exception:
        return None


def find_token(cookies: list[Cookie]) -> str | None:
    for c in cookies:
        if c.name == "aupd_token" and c.host.endswith(".mos.ru"):
            return c.value
    return None


# ---------------------------------------------------------------- кэш

def save_cache(cookies: list[Cookie], path: str = CACHE_COOKIES) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = {
        "saved_at": int(time.time()),
        "cookies": [
            {"host": c.host, "name": c.name, "value": c.value,
             "path": c.path, "secure": c.secure, "expires": c.expires}
            for c in cookies
        ],
    }
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(data, f, ensure_ascii=False)
    os.chmod(path, 0o600)


def load_cache(path: str = CACHE_COOKIES) -> list[Cookie]:
    try:
        with open(path) as f:
            data = json.load(f)
    except Exception:
        return []
    out = []
    for c in data.get("cookies", []):
        out.append(Cookie(host=c["host"], name=c["name"], value=c["value"],
                          path=c.get("path", "/"), secure=c.get("secure", True),
                          expires=c.get("expires")))
    return out


# ---------------------------------------------------------------- выбор источника

def load_best() -> tuple[list[Cookie], dict]:
    """Лучший доступный набор cookies + сведения об источнике.

    Порядок: живой профиль Chromium → кэш sch. Из двух наборов предпочитаем
    тот, у которого срок жизни токена (aupd_token) больше.
    """
    live, live_info = [], {}
    errors = []
    for app, prof, _mtime in find_profiles():
        try:
            cks = extract_cookies(prof)
        except Exception as e:  # noqa: BLE001
            errors.append(f"{app}: {e}")
            continue
        if find_token(cks):
            live, live_info = cks, {"source": f"{app}:{os.path.basename(prof)}",
                                    "profile": prof}
            break
        if cks and not live:
            live, live_info = cks, {"source": f"{app}:{os.path.basename(prof)}",
                                    "profile": prof}
    cached = load_cache()
    cached_info = {"source": "кэш sch (~/.cache/sch)"}

    now = time.time()

    def exp_of(cks):
        e = token_exp(find_token(cks))
        return e or 0

    best, info = None, {}
    if live:
        best, info = live, live_info
    if cached and (not live or exp_of(cached) > exp_of(live) + 60):
        best, info = copy.copy(cached), cached_info
    if not best:
        hint = (" Не нашёл cookies МЭШ в браузере. Открой school.mos.ru "
                "в Chromium и войди, затем повтори.")
        raise RuntimeError("не удалось получить cookies." + hint)
    info = dict(info)
    info["exp"] = exp_of(best) or None
    info["has_token"] = bool(find_token(best))
    if errors:
        info["errors"] = errors
    return best, info


# ---------------------------------------------------------------- копия профиля

def copy_profile_for_headless(profile_dir: str, dest: str) -> str:
    """Копия профиля для одноразового headless-запуска (без кэшей)."""
    os.makedirs(dest, exist_ok=True)
    subprocess.run(
        ["rsync", "-a", "--quiet",
         "--exclude", "*Cache*", "--exclude", "*GPUCache*",
         "--exclude", "Media Cache*", "--exclude", "Storage/ext",
         "--exclude", "DawnGraphiteCache", "--exclude", "Crashpad",
         profile_dir + "/", dest + "/"],
        check=True, timeout=120)
    for f in os.listdir(dest):
        if f.startswith("Singleton"):
            try:
                os.unlink(os.path.join(dest, f))
            except OSError:
                pass
    return dest
