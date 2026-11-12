#include <unistd.h>

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <map>
#include <optional>
#include <string>
#include <vector>

#include "client.hpp"
#include "cookies.hpp"
#include "download.hpp"
#include "errors.hpp"
#include "login.hpp"
#include "render.hpp"
#include "server.hpp"
#include "util.hpp"

static const char* VERSION = "0.1.0";

static const char* HELP = R"HELP(sch — дневник МЭШ в терминале (личный доступ с этого компьютера)

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
)HELP";

static int fail(const std::string& msg, int code = 1) {
    std::printf("%s\n", render::red(msg).c_str());
    return code;
}

static bool remove_flag(std::vector<std::string>& args,
                        const std::string& flag) {
    bool found = false;
    args.erase(std::remove_if(args.begin(), args.end(),
                              [&](const std::string& a) {
                                  if (a == flag) {
                                      found = true;
                                      return true;
                                  }
                                  return false;
                              }),
               args.end());
    return found;
}

static bool all_digits(const std::string& s) {
    return !s.empty() && std::all_of(s.begin(), s.end(), [](unsigned char c) {
        return c >= '0' && c <= '9';
    });
}

static bool has(const std::vector<std::string>& v, const std::string& x) {
    return std::find(v.begin(), v.end(), x) != v.end();
}

static MesClient local_client() { return MesClient::from_local(); }

// ------------------------------------------------------------------ день

static int cmd_day(std::vector<std::string> args, const std::string& fmt,
                   bool no_download) {
    static const std::map<std::string, std::string> aliases = {
        {"lessons", "lessons"}, {"lesson", "lessons"}, {"уроки", "lessons"},
        {"урок", "lessons"},    {"hw", "hw"},          {"homework", "hw"},
        {"дз", "hw"},           {"домашка", "hw"},     {"задания", "hw"}};

    std::optional<std::string> day_iso;
    bool day_given = false, section_given = false;
    std::string section;

    for (const auto& tok : args) {
        std::string t = util::lower(tok);
        auto it = aliases.find(t);
        if (it != aliases.end()) {
            if (section_given) return fail("раздел указан дважды: " + tok);
            section = it->second;
            section_given = true;
            continue;
        }
        util::DayParse d = util::parse_day(tok);
        if (d.ok) {
            if (day_given) return fail("лишний аргумент: " + tok);
            day_iso = d.iso;
            day_given = true;
            continue;
        }
        return fail("не понимаю аргумент: " + tok +
                    "\nПримеры: sch today lessons | sch tmrw hw | sch +2 | sch week");
    }

    MesClient client = local_client();
    json day = client.day(day_iso);

    if (fmt == "json") {
        std::printf("%s\n", day.dump(1).c_str());
        return 0;
    }
    std::vector<std::string> show;
    if (section == "lessons")
        show = {"lessons"};
    else if (section == "hw")
        show = {"hw"};
    else
        show = {"lessons", "hw"};

    if (fmt == "md")
        std::printf("%s\n", render::fmt_day_md(day, show).c_str());
    else
        std::printf("%s\n", render::fmt_day(day, show).c_str());

    if (has(show, "hw") && !no_download) {
        std::vector<json> days = {day};
        auto_sync(client, days);
    }
    return 0;
}

// ------------------------------------------------------------------ неделя

static int cmd_week(std::vector<std::string> args, const std::string& fmt,
                    bool no_download) {
    int n = 7;
    if (!args.empty()) {
        if (!all_digits(args[0]))
            return fail("sch week [дней] — например: sch week 10");
        n = std::stoi(args[0]);
        args.erase(args.begin());
    }
    if (!args.empty()) return fail("лишний аргумент: " + args[0]);
    if (n <= 0) return fail("число дней должно быть больше нуля");

    MesClient client = local_client();
    std::string start = util::iso_date(util::today_local());
    std::vector<json> days = client.week(start, n);

    if (fmt == "json") {
        json arr = json::array();
        for (const auto& d : days) arr.push_back(d);
        std::printf("%s\n", arr.dump(1).c_str());
        return 0;
    }
    if (fmt == "md")
        std::printf("%s\n", render::fmt_week_md(days).c_str());
    else
        std::printf("%s\n", render::fmt_week(days).c_str());

    if (!no_download) auto_sync(client, days);
    return 0;
}

// ------------------------------------------------------------------ оценки

static int cmd_marks(std::vector<std::string> args, const std::string& fmt) {
    int n = 14;
    if (!args.empty()) {
        if (!all_digits(args[0]))
            return fail("sch marks [дней] — например: sch marks 30");
        n = std::stoi(args[0]);
        args.erase(args.begin());
    }
    if (!args.empty()) return fail("лишний аргумент: " + args[0]);

    util::Date today = util::today_local();
    std::string frm = util::iso_date(util::add_days(today, -(n - 1)));
    std::string to = util::iso_date(today);

    MesClient client = local_client();
    json raw = client.marks(frm, to);

    json items = json::array();
    for (const auto& m : raw) {
        std::string v = json_scalar_to_string(jref(m, "value"));
        if (v.empty()) continue;
        items.push_back(m);
    }
    auto key = [](const json& m) {
        return std::make_pair(jstr(m, "date"), jstr(m, "created_at"));
    };
    std::stable_sort(items.begin(), items.end(),
                     [&](const json& a, const json& b) {
                         return key(a) > key(b);
                     });

    if (fmt == "json") {
        std::printf("%s\n", items.dump(1).c_str());
        return 0;
    }
    if (fmt == "md")
        std::printf("%s\n",
                    render::fmt_marks_md(std::vector<json>(items.begin(),
                                                           items.end()),
                                         frm, to)
                        .c_str());
    else
        std::printf("%s\n",
                    render::fmt_marks(std::vector<json>(items.begin(),
                                                        items.end()),
                                      frm, to)
                        .c_str());
    return 0;
}

// ------------------------------------------------------------------ материалы

static int cmd_dz(std::vector<std::string> args, const std::string& fmt) {
    std::string out_dir;
    bool force = false, dry_run = false;
    std::vector<std::string> rest;

    for (std::size_t i = 0; i < args.size();) {
        const std::string& a = args[i];
        if (a == "--out" || a == "-o") {
            if (i + 1 >= args.size()) return fail("--out требует каталог");
            out_dir = args[i + 1];
            i += 2;
            continue;
        }
        if (util::starts_with(a, "--out=")) {
            out_dir = a.substr(6);
            ++i;
            continue;
        }
        if (a == "--force" || a == "-f") {
            force = true;
            ++i;
            continue;
        }
        if (a == "--dry-run" || a == "-n") {
            dry_run = true;
            ++i;
            continue;
        }
        rest.push_back(a);
        ++i;
    }

    MesClient client = local_client();
    json homeworks = json::array();

    if (!rest.empty() && (util::lower(rest[0]) == "week" ||
                          util::lower(rest[0]) == "неделя")) {
        int n = 7;
        if (rest.size() > 1) {
            if (!all_digits(rest[1]))
                return fail("sch dz week [дней] — например: sch dz week 14");
            n = std::stoi(rest[1]);
        }
        if (rest.size() > 2) return fail("лишний аргумент: " + rest[2]);
        if (n <= 0) return fail("число дней должно быть больше нуля");
        util::Date start = util::today_local();
        util::Date end = util::add_days(start, n - 1);
        homeworks = client.homeworks(util::iso_date(start), util::iso_date(end));
    } else {
        std::optional<std::string> day_iso;
        bool day_given = false;
        for (const auto& tok : rest) {
            util::DayParse d = util::parse_day(tok);
            if (!d.ok)
                return fail("не понимаю аргумент: " + tok +
                            "\nПримеры: sch dz | sch dz tmrw | "
                            "sch dz 2026-11-10 | sch dz week 7");
            if (day_given) return fail("лишний аргумент: " + tok);
            day_iso = d.iso;
            day_given = true;
        }
        if (!day_iso) {
            std::string server_date =
                jstr(client.schedule_day(std::nullopt), "date");
            day_iso = server_date.empty()
                          ? util::iso_date(util::today_local())
                          : server_date;
        }
        homeworks = client.homeworks(*day_iso, *day_iso);
    }

    std::vector<FileRef> items = files_from_homeworks(homeworks);
    std::string root = download_root(out_dir);
    std::vector<DownloadResult> results =
        sync_items(client, items, root, force, dry_run);

    if (fmt == "json") {
        std::printf("%s\n",
                    results_json(results, root).dump(1).c_str());
    } else if (fmt == "md") {
        std::printf("%s\n", format_md(results, root).c_str());
    } else {
        std::printf("%s\n", format_text(results, root).c_str());
    }

    for (const auto& r : results)
        if (r.status == "error") return 1;
    return 0;
}

// ------------------------------------------------------------------ профиль

static int cmd_profile(const std::string& fmt) {
    MesClient client = local_client();
    json p = client.profile();
    if (fmt == "json") {
        std::printf("%s\n", p.dump(1).c_str());
        return 0;
    }
    if (fmt == "md")
        std::printf("%s\n", render::fmt_profile_md(p).c_str());
    else
        std::printf("%s\n", render::fmt_profile(p).c_str());
    return 0;
}

// ------------------------------------------------------------------ доктор

static std::string fmt_timestamp(long long ts) {
    std::time_t t = static_cast<std::time_t>(ts);
    std::tm tm{};
    localtime_r(&t, &tm);
    char buf[64];
    std::snprintf(buf, sizeof(buf), "%02d.%02d.%04d %02d:%02d", tm.tm_mday,
                  tm.tm_mon + 1, tm.tm_year + 1900, tm.tm_hour, tm.tm_min);
    return buf;
}

static int cmd_doctor(bool json_out) {
    json report = json::object();
    report["version"] = VERSION;

    LoadedCookies lc;
    try {
        lc = load_best();
    } catch (const std::exception& e) {
        if (json_out) {
            json o = json::object();
            o["ok"] = false;
            o["error"] = e.what();
            std::printf("%s\n", o.dump(1).c_str());
        } else {
            std::printf("%s\n", render::red(e.what()).c_str());
        }
        return 1;
    }

    report["cookies"] = static_cast<long long>(lc.cookies.size());
    report["source"] = lc.source;
    report["profile"] = lc.profile.empty() ? json(nullptr) : json(lc.profile);
    report["token_exp"] = lc.exp ? json(*lc.exp) : json(nullptr);
    report["token_ok"] = lc.exp && *lc.exp > util::now_unix();

    try {
        MesClient c(lc.cookies, lc.source);
        json p = c.profile();
        json prof = jref(p, "profile");
        std::string who;
        for (const char* key : {"last_name", "first_name"}) {
            std::string x = jstr(prof, key);
            if (x.empty()) continue;
            if (!who.empty()) who += " ";
            who += x;
        }
        report["api_ok"] = true;
        report["who"] = who;
    } catch (const MesAuthError& e) {
        report["api_ok"] = false;
        report["api_error"] = e.what();
    } catch (const MesApiError& e) {
        report["api_ok"] = false;
        report["api_error"] = e.what();
    }

    bool api_ok = report.value("api_ok", false);
    if (json_out) {
        std::printf("%s\n", report.dump(1).c_str());
        return api_ok ? 0 : 1;
    }

    std::printf("%s\n", render::bold("Диагностика sch").c_str());
    std::printf("  cookies: %zu шт. из %s\n", lc.cookies.size(),
                lc.source.c_str());
    if (!lc.profile.empty())
        std::printf("  профиль: %s\n", lc.profile.c_str());
    if (lc.exp)
        std::printf("  токен до: %s\n", fmt_timestamp(*lc.exp).c_str());
    if (api_ok) {
        std::printf("  %s — %s\n", render::green("API: работает").c_str(),
                    jstr(report, "who").c_str());
        return 0;
    }
    std::printf("  %s — %s\n", render::red("API: не работает").c_str(),
                jstr(report, "api_error").c_str());
    std::printf("  Подсказка: открой school.mos.ru в Chromium и выполни "
                "`sch login`.\n");
    return 1;
}

// ------------------------------------------------------------------ логин

static int cmd_login(std::vector<std::string> /*args*/) {
    std::printf("Проверяю текущий доступ…\n");
    try {
        MesClient c = local_client();
        c.profile();
        std::printf("%s (источник: %s)\n",
                    render::green("Доступ уже работает.").c_str(),
                    c.source.c_str());
        return 0;
    } catch (const std::exception& e) {
        std::printf("Текущий доступ не работает: %s\n", e.what());
    }

    std::optional<long long> old_exp;
    try {
        LoadedCookies lc = load_best();
        auto tok = find_token(lc.cookies);
        if (tok) old_exp = token_exp(*tok);
    } catch (...) {
    }

    std::printf("Пробую молча обновить сессию через headless-копию Chromium "
                "(до минуты)…\n");
    auto cks = refresh_via_headless(old_exp);
    if (!cks) {
        return fail(
            "Не получилось автоматически. Открой school.mos.ru\n"
            "в Chromium, войди (если просит), затем выполни `sch login`.");
    }
    try {
        MesClient c(*cks);
        c.profile();
    } catch (const std::exception& e) {
        return fail(std::string("Свежие cookies не подошли: ") + e.what() +
                    "\nОткрой school.mos.ru в Chromium, войди ещё раз, "
                    "потом `sch login`.");
    }
    save_cache(*cks);
    std::printf("%s доступ обновлён (кэш в ~/.cache/sch).\n",
                render::green("Готово:").c_str());
    return 0;
}

// ------------------------------------------------------------------ сервер

static int cmd_serve(std::vector<std::string> args) {
    std::string host = "127.0.0.1";
    int port = 8787;
    for (std::size_t i = 0; i < args.size();) {
        const std::string& a = args[i];
        if (a == "--port" && i + 1 < args.size()) {
            port = std::stoi(args[i + 1]);
            i += 2;
            continue;
        }
        if (util::starts_with(a, "--port=")) {
            port = std::stoi(a.substr(7));
            ++i;
            continue;
        }
        if (a == "--host" && i + 1 < args.size()) {
            host = args[i + 1];
            i += 2;
            continue;
        }
        if (util::starts_with(a, "--host=")) {
            host = a.substr(7);
            ++i;
            continue;
        }
        return fail("не понимаю аргумент: " + a);
    }
    const char* allow = std::getenv("SCH_ALLOW_REMOTE");
    bool local = host == "127.0.0.1" || host == "localhost" || host == "::1";
    if (!local && (!allow || std::string(allow) != "1")) {
        return fail(
            "Из соображений безопасности слушаю только 127.0.0.1.\n"
            "Если правда нужно наружу: SCH_ALLOW_REMOTE=1 sch serve …");
    }
    std::printf("%s\n",
                render::bold("МЭШ API запущен: http://" + host + ":" +
                             std::to_string(port) + "/")
                    .c_str());
    std::printf("  /day?date=today|tmrw|+2|2026-11-10   /lessons   /hw\n");
    std::printf("  /profile   /homeworks?from=&to=   /marks?from=&to=\n");
    std::printf("  /schedule?date=…  /schedule/short?dates=…   /health\n");
    std::printf("%s\n", render::dim("Ctrl+C — остановить.").c_str());
    return serve(host, port);
}

// ------------------------------------------------------------------ вход

int main(int argc, char** argv) {
    std::vector<std::string> args;
    for (int i = 1; i < argc; ++i) args.emplace_back(argv[i]);

    bool no_color = remove_flag(args, "--no-color");
    bool no_download = remove_flag(args, "--no-download");
    const char* env_nd = std::getenv("SCH_NO_DOWNLOAD");
    if (env_nd && std::string(env_nd) == "1") no_download = true;

    std::vector<std::string> formats;
    auto take = [&](const std::string& flag, const std::string& mode) {
        if (remove_flag(args, flag)) formats.push_back(mode);
    };
    take("--json", "json");
    take("-j", "json");
    take("--raw-md", "md");
    take("--md", "md");
    take("--no-md", "plain");

    std::vector<std::string> uniq;
    for (const auto& f : formats)
        if (!has(uniq, f)) uniq.push_back(f);
    if (uniq.size() > 1)
        return fail("выбери один формат: --json, --raw-md (--md) или --no-md");
    std::string fmt = uniq.empty() ? "text" : uniq[0];

    bool tty = isatty(STDOUT_FILENO) != 0;
    render::use_color(fmt == "text" && tty && !no_color &&
                      std::getenv("NO_COLOR") == nullptr);

    if (args.empty()) args = {"today"};
    std::string cmd = util::lower(args[0]);
    std::vector<std::string> rest(args.begin() + 1, args.end());

    try {
        if (cmd == "help" || cmd == "-h" || cmd == "--help" ||
            cmd == "справка") {
            std::printf("%s\n", HELP);
            return 0;
        }
        if (cmd == "version" || cmd == "--version" || cmd == "-v") {
            std::printf("sch %s\n", VERSION);
            return 0;
        }
        if (cmd == "login") return cmd_login(rest);
        if (cmd == "doctor") return cmd_doctor(fmt == "json");
        if (cmd == "profile") return cmd_profile(fmt);
        if (cmd == "week") return cmd_week(rest, fmt, no_download);
        if (cmd == "marks") return cmd_marks(rest, fmt);
        if (cmd == "dz" || cmd == "materials" || cmd == "files" ||
            cmd == "материалы" || cmd == "скачать")
            return cmd_dz(rest, fmt);
        if (cmd == "serve") return cmd_serve(rest);
        return cmd_day(args, fmt, no_download);
    } catch (const MesAuthError& e) {
        return fail(std::string(e.what()) +
                    "\nПодсказка: выполни `sch login`.");
    } catch (const MesApiError& e) {
        return fail(std::string("Ошибка МЭШ: ") + e.what());
    } catch (const std::exception& e) {
        return fail(e.what());
    }
}
