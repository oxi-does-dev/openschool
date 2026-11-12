#include "util.hpp"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <ctime>

namespace util {

static bool is_ws(unsigned char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' ||
           c == '\v';
}

std::string ltrim(const std::string& s) {
    std::size_t i = 0;
    while (i < s.size() && is_ws(static_cast<unsigned char>(s[i]))) ++i;
    return s.substr(i);
}

std::string rtrim(const std::string& s) {
    std::size_t i = s.size();
    while (i > 0 && is_ws(static_cast<unsigned char>(s[i - 1]))) --i;
    return s.substr(0, i);
}

std::string trim(const std::string& s) { return rtrim(ltrim(s)); }

std::string lower(const std::string& s) {
    std::string o = s;
    for (std::size_t i = 0; i < o.size(); ++i) {
        unsigned char c = static_cast<unsigned char>(o[i]);
        if (c >= 'A' && c <= 'Z') {
            o[i] = static_cast<char>(c + 32);
        } else if (c == 0xD0 && i + 1 < o.size()) {
            unsigned char n = static_cast<unsigned char>(o[i + 1]);
            if (n >= 0x90 && n <= 0xAF) {
                o[i + 1] = static_cast<char>(n + 0x20);  // А-Я -> а-я
            } else if (n == 0x81) {
                o[i] = static_cast<char>(0xD1);  // Ё -> ё
                o[i + 1] = static_cast<char>(0x91);
                ++i;
            }
        }
    }
    return o;
}

bool iequals(const std::string& a, const std::string& b) {
    return lower(a) == lower(b);
}

std::size_t utf8_char_bytes(unsigned char lead) {
    if ((lead & 0xE0) == 0xC0) return 2;
    if ((lead & 0xF0) == 0xE0) return 3;
    if ((lead & 0xF8) == 0xF0) return 4;
    return 1;
}

std::size_t utf8_len(const std::string& s) {
    std::size_t n = 0;
    for (unsigned char c : s) {
        if ((c & 0xC0) != 0x80) ++n;
    }
    return n;
}

std::string utf8_prefix_chars(const std::string& s, std::size_t chars) {
    std::size_t i = 0, seen = 0;
    while (i < s.size() && seen < chars) {
        std::size_t len =
            utf8_char_bytes(static_cast<unsigned char>(s[i]));
        if (i + len > s.size()) len = 1;
        i += len;
        ++seen;
    }
    return s.substr(0, i);
}

std::string utf8_first_char(const std::string& s) {
    if (s.empty()) return "";
    std::size_t len = utf8_char_bytes(static_cast<unsigned char>(s[0]));
    if (len > s.size()) len = s.size();
    return s.substr(0, len);
}

std::string truncate_utf8(const std::string& s, std::size_t limit) {
    std::size_t i = 0;
    while (i < s.size()) {
        std::size_t len = utf8_char_bytes(static_cast<unsigned char>(s[i]));
        if (len == 0 || i + len > s.size() || i + len > limit) break;
        i += len;
    }
    return s.substr(0, i);
}

static std::vector<std::string> tokenize(const std::string& s) {
    std::vector<std::string> out;
    std::string cur;
    auto flush = [&]() {
        if (!cur.empty()) {
            out.push_back(cur);
            cur.clear();
        }
    };
    for (std::size_t i = 0; i < s.size();) {
        unsigned char c = static_cast<unsigned char>(s[i]);
        if (is_ws(c)) {
            flush();
            ++i;
            continue;
        }
        // NBSP, THIN SPACE, LINE/PARAGRAPH SEPARATOR, NARROW NBSP
        if (c == 0xC2 && i + 1 < s.size() &&
            static_cast<unsigned char>(s[i + 1]) == 0xA0) {
            flush();
            i += 2;
            continue;
        }
        if (c == 0xE2 && i + 2 < s.size() &&
            static_cast<unsigned char>(s[i + 1]) == 0x80) {
            unsigned char t = static_cast<unsigned char>(s[i + 2]);
            if (t == 0x89 || t == 0xA8 || t == 0xA9 || t == 0xAF) {
                flush();
                i += 3;
                continue;
            }
        }
        std::size_t len = utf8_char_bytes(c);
        if (i + len > s.size()) len = 1;
        cur.append(s, i, len);
        i += len;
    }
    flush();
    return out;
}

std::string one_line(const std::string& s) {
    auto words = tokenize(s);
    return join(words, " ");
}

std::string wrap(const std::string& text, std::size_t width,
                 const std::string& initial_indent,
                 const std::string& subsequent_indent) {
    auto words = tokenize(text);
    if (words.empty()) return rtrim(initial_indent);

    std::string out;
    std::string line = initial_indent;
    bool has_word = false;

    for (std::size_t wi = 0; wi < words.size(); ++wi) {
        std::string w = words[wi];
        std::size_t cur = utf8_len(line);
        std::size_t wlen = utf8_len(w);
        std::size_t need = cur + (has_word ? 1 : 0) + wlen;

        if (has_word && need > width) {
            out += rtrim(line);
            out += '\n';
            line = subsequent_indent;
            has_word = false;
            cur = utf8_len(line);
            need = cur + wlen;
        }

        // A single word longer than the line: split it like Python does.
        while (need > width && !has_word && wlen > 0) {
            std::size_t room = width > cur ? width - cur : 1;
            if (room == 0) room = 1;
            std::string head = utf8_prefix_chars(w, room);
            out += line + head;
            out += '\n';
            w = w.substr(head.size());
            wlen = utf8_len(w);
            line = subsequent_indent;
            cur = utf8_len(line);
            need = cur + wlen;
        }

        if (has_word) line += ' ';
        line += w;
        has_word = true;
    }
    out += rtrim(line);
    return out;
}

std::string shorten(const std::string& s, std::size_t n) {
    std::string t = one_line(s);
    if (utf8_len(t) <= n) return t;
    return rtrim(utf8_prefix_chars(t, n > 0 ? n - 1 : 0)) + "…";
}

std::string md_escape(const std::string& s) {
    return replace_all(one_line(s), "|", "\\|");
}

std::string percent_decode(const std::string& s, bool plus_as_space) {
    std::string out;
    out.reserve(s.size());
    for (std::size_t i = 0; i < s.size(); ++i) {
        char c = s[i];
        if (plus_as_space && c == '+') {
            out += ' ';
            continue;
        }
        if (c == '%' && i + 2 < s.size()) {
            auto hex = [](char h) -> int {
                if (h >= '0' && h <= '9') return h - '0';
                if (h >= 'a' && h <= 'f') return h - 'a' + 10;
                if (h >= 'A' && h <= 'F') return h - 'A' + 10;
                return -1;
            };
            int hi = hex(s[i + 1]), lo = hex(s[i + 2]);
            if (hi >= 0 && lo >= 0) {
                out += static_cast<char>(hi * 16 + lo);
                i += 2;
                continue;
            }
        }
        out += c;
    }
    return out;
}

std::string url_encode(const std::string& s) {
    static const char* hex = "0123456789ABCDEF";
    std::string out;
    for (unsigned char c : s) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' ||
            c == '~') {
            out += static_cast<char>(c);
        } else {
            out += '%';
            out += hex[c >> 4];
            out += hex[c & 15];
        }
    }
    return out;
}

std::string expand_tilde(const std::string& s) {
    if (s.empty() || s[0] != '~') return s;
    const char* home = std::getenv("HOME");
    if (!home) return s;
    if (s.size() == 1) return home;
    if (s[1] == '/') return std::string(home) + s.substr(1);
    return s;
}

std::string int_to_str(long long v) { return std::to_string(v); }

long long now_unix() {
    return static_cast<long long>(std::time(nullptr));
}

bool starts_with(const std::string& s, const std::string& p) {
    return s.size() >= p.size() && s.compare(0, p.size(), p) == 0;
}

bool ends_with(const std::string& s, const std::string& p) {
    return s.size() >= p.size() &&
           s.compare(s.size() - p.size(), p.size(), p) == 0;
}

std::string replace_all(std::string s, const std::string& from,
                        const std::string& to) {
    if (from.empty()) return s;
    std::size_t pos = 0;
    while ((pos = s.find(from, pos)) != std::string::npos) {
        s.replace(pos, from.size(), to);
        pos += to.size();
    }
    return s;
}

std::vector<std::string> split(const std::string& s, char sep) {
    std::vector<std::string> out;
    std::string cur;
    for (char c : s) {
        if (c == sep) {
            out.push_back(cur);
            cur.clear();
        } else {
            cur += c;
        }
    }
    out.push_back(cur);
    return out;
}

std::string join(const std::vector<std::string>& v, const std::string& sep) {
    std::string out;
    for (std::size_t i = 0; i < v.size(); ++i) {
        if (i) out += sep;
        out += v[i];
    }
    return out;
}

// ---------------------------------------------------------------- даты

using namespace std::chrono;

static sys_days to_sd(const Date& d) {
    return sys_days{year{d.y} / month{static_cast<unsigned>(d.m)} /
                    day{static_cast<unsigned>(d.d)}};
}

static Date from_sd(sys_days sd) {
    year_month_day ymd{sd};
    return Date{int(ymd.year()), int(static_cast<unsigned>(ymd.month())),
                int(static_cast<unsigned>(ymd.day()))};
}

bool operator==(const Date& a, const Date& b) {
    return a.y == b.y && a.m == b.m && a.d == b.d;
}

std::optional<Date> parse_iso(const std::string& s) {
    if (s.size() != 10 || s[4] != '-' || s[7] != '-') return std::nullopt;
    auto num = [&](std::size_t pos, std::size_t len) -> int {
        int v = 0;
        for (std::size_t i = 0; i < len; ++i) {
            char c = s[pos + i];
            if (c < '0' || c > '9') return -1;
            v = v * 10 + (c - '0');
        }
        return v;
    };
    int y = num(0, 4), m = num(5, 2), d = num(8, 2);
    if (y < 0 || m < 0 || d < 0) return std::nullopt;
    year_month_day ymd{year{y}, month{static_cast<unsigned>(m)},
                       day{static_cast<unsigned>(d)}};
    if (!ymd.ok()) return std::nullopt;
    return Date{y, m, d};
}

std::string iso_date(const Date& d) {
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%04d-%02d-%02d", d.y, d.m, d.d);
    return buf;
}

Date today_local() {
    std::time_t t = std::time(nullptr);
    std::tm tm{};
    localtime_r(&t, &tm);
    return Date{tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday};
}

Date add_days(const Date& d, int n) {
    return from_sd(to_sd(d) + days(n));
}

int weekday_py(const Date& d) {
    return static_cast<int>(weekday(to_sd(d)).iso_encoding()) - 1;
}

int iso_week(const Date& d) {
    sys_days sd = to_sd(d);
    int iso_wd = static_cast<int>(weekday(sd).iso_encoding());  // 1..7
    sys_days thursday = sd + days(4 - iso_wd);
    year_month_day th{thursday};
    sys_days jan4{year{th.year()} / January / 4};
    int j4wd = static_cast<int>(weekday(jan4).iso_encoding());
    sys_days w1thu = jan4 + days(4 - j4wd);
    return static_cast<int>((thursday - w1thu).count() / 7) + 1;
}

int days_between(const Date& a, const Date& b) {
    return static_cast<int>((to_sd(b) - to_sd(a)).count());
}

DayParse parse_day(const std::string& token) {
    std::string t = lower(trim(token));
    DayParse res;
    if (t.empty()) return res;
    if (t == "today" || t == "сегодня") {
        res.ok = true;
        return res;
    }
    if (t == "tmrw" || t == "tmr" || t == "tomorrow" || t == "завтра") {
        res.ok = true;
        res.iso = iso_date(add_days(today_local(), 1));
        return res;
    }
    if (t.size() > 1 && t[0] == '+' &&
        std::all_of(t.begin() + 1, t.end(),
                    [](unsigned char c) { return c >= '0' && c <= '9'; })) {
        res.ok = true;
        res.iso = iso_date(add_days(today_local(), std::stoi(t.substr(1))));
        return res;
    }
    static const std::vector<std::pair<std::string, int>> weekdays = {
        {"пн", 0},      {"вт", 1},          {"ср", 2},
        {"чт", 3},      {"пт", 4},          {"сб", 5},
        {"вс", 6},      {"понедельник", 0}, {"вторник", 1},
        {"среда", 2},   {"четверг", 3},     {"пятница", 4},
        {"суббота", 5}, {"воскресенье", 6}};
    for (const auto& [name, wd] : weekdays) {
        if (t == name) {
            Date today = today_local();
            int delta = (wd - weekday_py(today) + 7) % 7;
            res.ok = true;
            res.iso = iso_date(add_days(today, delta));
            return res;
        }
    }
    if (auto d = parse_iso(t)) {
        res.ok = true;
        res.iso = iso_date(*d);
    }
    return res;
}

}  // namespace util
