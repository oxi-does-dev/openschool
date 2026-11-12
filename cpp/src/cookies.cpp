#include "cookies.hpp"

#include <openssl/evp.h>
#include <sqlite3.h>

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

#include "jsonutil.hpp"
#include "util.hpp"
#include "errors.hpp"

namespace fs = std::filesystem;

static const double WEBKIT_EPOCH = 11644473600.0;

static std::string cache_dir() {
    const char* home = std::getenv("HOME");
    return std::string(home ? home : ".") + "/.cache/sch";
}

static std::string cache_cookies_path() {
    return cache_dir() + "/cookies.json";
}

// ---------------------------------------------------------------- профили

std::vector<Profile> find_profiles() {
    static const std::vector<std::pair<std::string, std::string>> browsers = {
        {"chromium", "~/.config/chromium"},
        {"chrome", "~/.config/google-chrome"},
        {"brave", "~/.config/BraveSoftware/Brave-Browser"},
        {"vivaldi", "~/.config/vivaldi"},
    };
    std::vector<Profile> out;
    for (const auto& [app, base_raw] : browsers) {
        std::string base = util::expand_tilde(base_raw);
        std::error_code ec;
        if (!fs::is_directory(base, ec)) continue;
        std::vector<std::string> cands = {"Default"};
        std::vector<std::string> others;
        for (auto it = fs::directory_iterator(base, ec);
             !ec && it != fs::directory_iterator(); it.increment(ec)) {
            std::string name = it->path().filename().string();
            if (util::starts_with(name, "Profile ")) others.push_back(name);
        }
        std::sort(others.begin(), others.end());
        for (auto& o : others) cands.push_back(o);
        for (const auto& cand : cands) {
            fs::path db = fs::path(base) / cand / "Cookies";
            std::error_code ec2;
            if (!fs::exists(db, ec2)) continue;
            auto mt = fs::last_write_time(db, ec2);
            double mtime = 0;
            if (!ec2) {
                mtime = static_cast<double>(
                    mt.time_since_epoch().count());
            }
            out.push_back(Profile{app, (fs::path(base) / cand).string(),
                                  mtime});
        }
    }
    std::stable_sort(out.begin(), out.end(),
                     [](const Profile& a, const Profile& b) {
                         return a.mtime > b.mtime;
                     });
    return out;
}

// ---------------------------------------------------------------- ключ

static std::string key_from_secret_tool() {
    for (const char* app : {"chromium", "chrome", "brave", "vivaldi"}) {
        std::string cmd =
            "secret-tool lookup xdg:schema "
            "chrome_libsecret_os_crypt_password_v2 application ";
        cmd += app;
        cmd += " 2>/dev/null";
        FILE* p = popen(cmd.c_str(), "r");
        if (!p) continue;
        std::string out;
        char buf[512];
        while (fgets(buf, sizeof(buf), p)) out += buf;
        pclose(p);
        out = util::trim(out);
        if (!out.empty()) return out;
    }
    return "";
}

std::string get_encryption_key() {
    std::string key = key_from_secret_tool();
    if (!key.empty()) return key;
    return "peanuts";  // Chromium without a keyring
}

// ---------------------------------------------------------------- AES

static std::string decrypt_value(const std::string& enc,
                                 const unsigned char* derived) {
    if (enc.size() < 3) return enc;
    bool v10 = enc.compare(0, 3, "v10") == 0;
    bool v11 = enc.compare(0, 3, "v11") == 0;
    if (!v10 && !v11) return enc;

    static const unsigned char iv[16] = {' ', ' ', ' ', ' ', ' ', ' ', ' ',
                                         ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                                         ' ', ' '};
    std::string body = enc.substr(3);
    std::string out(body.size() + 16, '\0');
    int outl = 0, tmpl = 0;
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return enc;
    bool ok = EVP_DecryptInit_ex(ctx, EVP_aes_128_cbc(), nullptr, derived,
                                 iv) == 1;
    if (ok)
        ok = EVP_DecryptUpdate(
                 ctx, reinterpret_cast<unsigned char*>(out.data()), &outl,
                 reinterpret_cast<const unsigned char*>(body.data()),
                 static_cast<int>(body.size())) == 1;
    if (ok)
        ok = EVP_DecryptFinal_ex(
                 ctx, reinterpret_cast<unsigned char*>(out.data()) + outl,
                 &tmpl) == 1;
    EVP_CIPHER_CTX_free(ctx);
    if (!ok) return enc;
    out.resize(static_cast<std::size_t>(outl + tmpl));

    if (!out.empty()) {
        unsigned char pad = static_cast<unsigned char>(out.back());
        if (pad > 0 && pad < 16 && pad <= out.size())
            out.resize(out.size() - pad);
    }
    // Newer Chromium prepends a 32-byte hash of the domain.
    if (out.size() > 32) {
        bool printable = true;
        for (std::size_t i = 0; i < 32; ++i) {
            unsigned char c = static_cast<unsigned char>(out[i]);
            if (c < 0x20 || c > 0x7E) {
                printable = false;
                break;
            }
        }
        if (!printable) out = out.substr(32);
    }
    return out;
}

// ---------------------------------------------------------------- база

static bool copy_file_quiet(const fs::path& src, const fs::path& dst) {
    std::error_code ec;
    fs::copy_file(src, dst, fs::copy_options::overwrite_existing, ec);
    return !ec;
}

std::vector<Cookie> extract_cookies(
    const std::string& profile_dir,
    const std::vector<std::string>& hosts_suffix,
    const std::optional<std::string>& key) {
    fs::path src = fs::path(profile_dir) / "Cookies";
    std::error_code ec;
    if (!fs::exists(src, ec)) return {};

    std::string enc_key = key ? *key : get_encryption_key();
    unsigned char derived[16];
    if (PKCS5_PBKDF2_HMAC(enc_key.data(), static_cast<int>(enc_key.size()),
                          reinterpret_cast<const unsigned char*>("saltysalt"),
                          9, 1, EVP_sha1(), 16, derived) != 1) {
        return {};
    }

    char tmpl[] = "/tmp/sch-cookies-XXXXXX";
    char* dir = mkdtemp(tmpl);
    if (!dir) return {};
    fs::path tmpdir(dir);
    fs::path dst = tmpdir / "Cookies";

    std::vector<Cookie> out;
    if (!copy_file_quiet(src, dst)) {
        fs::remove_all(tmpdir);
        return {};
    }
    for (const char* ext : {"-wal", "-shm"}) {
        fs::path s = src.string() + ext;
        if (fs::exists(s, ec)) copy_file_quiet(s, dst.string() + ext);
    }

    sqlite3* db = nullptr;
    if (sqlite3_open_v2(dst.c_str(), &db, SQLITE_OPEN_READONLY, nullptr) !=
        SQLITE_OK) {
        if (db) sqlite3_close(db);
        fs::remove_all(tmpdir);
        return {};
    }
    sqlite3_busy_timeout(db, 2000);

    sqlite3_stmt* st = nullptr;
    const char* sql =
        "select host_key, name, value, path, expires_utc, is_secure,"
        " encrypted_value from cookies";
    if (sqlite3_prepare_v2(db, sql, -1, &st, nullptr) == SQLITE_OK) {
        double now = static_cast<double>(util::now_unix());
        while (sqlite3_step(st) == SQLITE_ROW) {
            const unsigned char* host_p = sqlite3_column_text(st, 0);
            std::string host = host_p
                                   ? reinterpret_cast<const char*>(host_p)
                                   : "";
            bool host_ok = false;
            for (const auto& suffix : hosts_suffix) {
                if (util::ends_with(host, suffix)) {
                    host_ok = true;
                    break;
                }
            }
            if (!host_ok) continue;

            const unsigned char* name_p = sqlite3_column_text(st, 1);
            std::string name = name_p
                                   ? reinterpret_cast<const char*>(name_p)
                                   : "";
            const unsigned char* value_p = sqlite3_column_text(st, 2);
            std::string value = value_p
                                    ? reinterpret_cast<const char*>(value_p)
                                    : "";
            const unsigned char* path_p = sqlite3_column_text(st, 3);
            std::string cpath = path_p
                                    ? reinterpret_cast<const char*>(path_p)
                                    : "/";
            if (cpath.empty()) cpath = "/";

            sqlite3_int64 exp_utc = sqlite3_column_int64(st, 4);
            bool secure = sqlite3_column_int(st, 5) != 0;

            std::optional<double> expires;
            if (exp_utc) {
                expires = static_cast<double>(exp_utc) / 1'000'000.0 -
                          WEBKIT_EPOCH;
                if (*expires < now) continue;  // expired
            }

            const void* blob = sqlite3_column_blob(st, 6);
            int blob_len = sqlite3_column_bytes(st, 6);
            std::string enc;
            if (blob && blob_len > 0)
                enc.assign(static_cast<const char*>(blob),
                           static_cast<std::size_t>(blob_len));

            std::string val;
            if (enc.size() >= 3 &&
                (enc.compare(0, 3, "v10") == 0 ||
                 enc.compare(0, 3, "v11") == 0)) {
                val = decrypt_value(enc, derived);
            } else {
                val = !value.empty() ? value : enc;
            }
            out.push_back(Cookie{host, name, val, cpath, secure, expires});
        }
    }
    sqlite3_finalize(st);
    sqlite3_close(db);
    fs::remove_all(tmpdir);
    return out;
}

// ---------------------------------------------------------------- токен

static std::string b64url_decode(std::string s) {
    for (char& c : s) {
        if (c == '-')
            c = '+';
        else if (c == '_')
            c = '/';
    }
    while (s.size() % 4) s += '=';
    std::string out;
    int val = 0, bits = 0;
    for (char c : s) {
        int d;
        if (c >= 'A' && c <= 'Z')
            d = c - 'A';
        else if (c >= 'a' && c <= 'z')
            d = c - 'a' + 26;
        else if (c >= '0' && c <= '9')
            d = c - '0' + 52;
        else if (c == '+')
            d = 62;
        else if (c == '/')
            d = 63;
        else
            continue;  // '='
        val = (val << 6) | d;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out += static_cast<char>((val >> bits) & 0xFF);
        }
    }
    return out;
}

std::optional<long long> token_exp(const std::string& token) {
    if (token.empty()) return std::nullopt;
    auto parts = util::split(token, '.');
    if (parts.size() < 3) return std::nullopt;
    std::string payload = b64url_decode(parts[1]);
    try {
        json data = json::parse(payload);
        if (!data.is_object()) return std::nullopt;
        auto it = data.find("exp");
        if (it == data.end() || !it->is_number()) return std::nullopt;
        return it->get<long long>();
    } catch (...) {
        return std::nullopt;
    }
}

bool token_usable(const std::string& token) {
    if (token.size() < 10) return false;
    for (unsigned char c : token) {
        if (c < 0x21 || c > 0x7E) return false;  // control/space/non-ASCII
    }
    return true;
}

std::optional<std::string> find_token(const std::vector<Cookie>& cookies) {
    for (const auto& c : cookies) {
        if (c.name == "aupd_token" && util::ends_with(c.host, ".mos.ru"))
            return c.value;
    }
    return std::nullopt;
}

// ---------------------------------------------------------------- кэш

std::vector<Cookie> load_cache() {
    std::ifstream f(cache_cookies_path());
    if (!f) return {};
    std::stringstream ss;
    ss << f.rdbuf();
    try {
        json data = json::parse(ss.str());
        std::vector<Cookie> out;
        for (const auto& c : jarr(data, "cookies")) {
            Cookie ck;
            ck.host = jstr(c, "host");
            ck.name = jstr(c, "name");
            ck.value = jstr(c, "value");
            ck.path = jstr(c, "path");
            if (ck.path.empty()) ck.path = "/";
            ck.secure = jbool(c, "secure");
            if (c.contains("expires") && c["expires"].is_number())
                ck.expires = c["expires"].get<double>();
            out.push_back(std::move(ck));
        }
        return out;
    } catch (...) {
        return {};
    }
}

void save_cache(const std::vector<Cookie>& cookies) {
    std::error_code ec;
    fs::create_directories(cache_dir(), ec);
    json data = json::object();
    data["saved_at"] = util::now_unix();
    json arr = json::array();
    for (const auto& c : cookies) {
        json o = json::object();
        o["host"] = c.host;
        o["name"] = c.name;
        o["value"] = c.value;
        o["path"] = c.path;
        o["secure"] = c.secure;
        o["expires"] = c.expires ? json(*c.expires) : json(nullptr);
        arr.push_back(o);
    }
    data["cookies"] = arr;
    std::string path = cache_cookies_path();
    FILE* f = std::fopen(path.c_str(), "w");
    if (!f) return;
    std::string s = data.dump();
    std::fwrite(s.data(), 1, s.size(), f);
    std::fclose(f);
    chmod(path.c_str(), 0600);
}

// ---------------------------------------------------------------- выбор

static long long exp_of(const std::vector<Cookie>& cks) {
    auto tok = find_token(cks);
    if (!tok || !token_usable(*tok)) return 0;
    auto e = token_exp(*tok);
    return e ? *e : 0;
}

LoadedCookies load_best() {
    std::vector<Cookie> live;
    std::string live_source, live_profile;
    std::vector<std::string> errors;

    for (const auto& prof : find_profiles()) {
        std::vector<Cookie> cks;
        try {
            cks = extract_cookies(prof.dir);
        } catch (const std::exception& e) {
            errors.push_back(prof.app + ": " + e.what());
            continue;
        }
        auto tok = find_token(cks);
        if (tok) {
            live = std::move(cks);
            live_source = prof.app + ":" + fs::path(prof.dir).filename().string();
            live_profile = prof.dir;
            break;
        }
        if (!cks.empty() && live.empty()) {
            live = std::move(cks);
            live_source = prof.app + ":" + fs::path(prof.dir).filename().string();
            live_profile = prof.dir;
        }
    }

    std::vector<Cookie> cached = load_cache();

    LoadedCookies best;
    if (!live.empty()) {
        best.cookies = live;
        best.source = live_source;
        best.profile = live_profile;
    }
    if (!cached.empty() &&
        (live.empty() || exp_of(cached) > exp_of(live) + 60)) {
        best.cookies = cached;
        best.source = "кэш sch (~/.cache/sch)";
        best.profile.clear();
    }
    if (best.cookies.empty()) {
        throw AppError(
            "не удалось получить cookies. Не нашёл cookies МЭШ в браузере. "
            "Открой school.mos.ru в Chromium и войди, затем повтори.");
    }
    long long e = exp_of(best.cookies);
    best.exp = e ? std::optional<long long>(e) : std::nullopt;
    best.has_token = find_token(best.cookies).has_value();
    best.errors = errors;
    return best;
}

// ---------------------------------------------------------------- профиль

void copy_profile_for_headless(const std::string& profile_dir,
                               const std::string& dest) {
    std::error_code ec;
    fs::create_directories(dest, ec);
    std::string src = profile_dir;
    if (!src.empty() && src.back() != '/') src += '/';
    std::string dst = dest;
    if (!dst.empty() && dst.back() != '/') dst += '/';
    std::vector<std::string> args = {
        "rsync", "-a", "--quiet", "--exclude", "*Cache*", "--exclude",
        "*GPUCache*", "--exclude", "Media Cache*", "--exclude",
        "Storage/ext", "--exclude", "DawnGraphiteCache", "--exclude",
        "Crashpad", src, dst};
    std::string cmd;
    for (const auto& a : args) {
        if (!cmd.empty()) cmd += ' ';
        cmd += '\'' + util::replace_all(a, "'", "'\\''") + '\'';
    }
    if (std::system(cmd.c_str()) != 0) return;
    for (auto it = fs::directory_iterator(dest, ec);
         !ec && it != fs::directory_iterator(); it.increment(ec)) {
        std::string name = it->path().filename().string();
        if (util::starts_with(name, "Singleton")) {
            std::error_code e2;
            fs::remove(it->path(), e2);
        }
    }
}
