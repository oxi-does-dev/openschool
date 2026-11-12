#include "client.hpp"

#include <curl/curl.h>

#include <algorithm>
#include <atomic>
#include <cstdio>
#include <filesystem>
#include <functional>
#include <map>
#include <set>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include "errors.hpp"
#include "util.hpp"

static const char* BASE = "https://school.mos.ru";
static const char* API = "https://school.mos.ru/api/family/web/v1";
static const char* UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36";

namespace {

struct CurlGlobal {
    CurlGlobal() { curl_global_init(CURL_GLOBAL_DEFAULT); }
    ~CurlGlobal() { curl_global_cleanup(); }
};

CurlGlobal& curl_global() {
    static CurlGlobal g;
    return g;
}

size_t write_string_cb(char* ptr, size_t size, size_t nmemb,
                       void* userdata) {
    auto* s = static_cast<std::string*>(userdata);
    s->append(ptr, size * nmemb);
    return size * nmemb;
}

size_t write_file_cb(char* ptr, size_t size, size_t nmemb, void* userdata) {
    auto* f = static_cast<FILE*>(userdata);
    return std::fwrite(ptr, size, nmemb, f);
}

}  // namespace

MesClient::MesClient(std::vector<Cookie> cks, std::string src)
    : source(std::move(src)), cks_(std::move(cks)) {
    curl_global();
    for (const auto& c : cks_) {
        if (c.name == "aupd_token" && util::ends_with(c.host, ".mos.ru"))
            token = c.value;
        if (c.name == "active_student" &&
            util::ends_with(c.host, "school.mos.ru")) {
            std::string sid = util::trim(c.value);
            bool digits = !sid.empty() &&
                          std::all_of(sid.begin(), sid.end(),
                                      [](unsigned char ch) {
                                          return ch >= '0' && ch <= '9';
                                      });
            if (digits) sid_ = sid;
        }
    }
    if (token.empty()) {
        throw MesAuthError(
            "в cookies нет токена aupd_token. Открой school.mos.ru в "
            "Chromium и войди, затем выполни `sch login`.");
    }
    if (!token_usable(token)) {
        throw MesAuthError(
            "в cookies МЭШ повреждённый токен aupd_token (в нём двоичный "
            "мусор вместо текста). Похоже, база cookies прочитана неверно. "
            "Открой school.mos.ru в Chromium, затем выполни `sch login`.");
    }

    // Deduplicate by name, last one wins, like a browser would for one host.
    std::map<std::string, std::string> by_name;
    for (const auto& c : cks_) by_name[c.name] = c.value;
    for (const auto& [name, value] : by_name) {
        if (!cookie_header_.empty()) cookie_header_ += "; ";
        cookie_header_ += name + "=" + value;
    }
}

MesClient MesClient::from_local() {
    LoadedCookies lc = load_best();
    return MesClient(lc.cookies, lc.source);
}

json MesClient::get(
    const std::string& path,
    const std::vector<std::pair<std::string, std::string>>& params) {
    std::string url = util::starts_with(path, "http") ? path : API + path;
    if (!params.empty()) {
        url += url.find('?') == std::string::npos ? '?' : '&';
        bool first = true;
        for (const auto& [k, v] : params) {
            if (!first) url += '&';
            first = false;
            url += util::url_encode(k) + "=" + util::url_encode(v);
        }
    }

    std::string last_err;
    for (int attempt = 1; attempt <= 2; ++attempt) {
        CURL* h = curl_easy_init();
        if (!h) throw MesApiError("не удалось инициализировать libcurl");
        std::string body;
        struct curl_slist* headers = nullptr;
        headers = curl_slist_append(headers, "Accept: application/json, text/plain, */*");
        headers = curl_slist_append(headers, "Accept-Language: ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
        std::string referer = std::string("Referer: ") + BASE + "/diary";
        headers = curl_slist_append(headers, referer.c_str());
        headers = curl_slist_append(headers, "X-Mes-RoleId: 1");
        headers = curl_slist_append(headers, "X-mes-subsystem: familyweb");
        headers = curl_slist_append(headers, "Profile-Id: ");
        headers = curl_slist_append(headers, "Profile-Type: ");
        std::string auth = "Authorization: Bearer " + token;
        headers = curl_slist_append(headers, auth.c_str());
        if (!cookie_header_.empty()) {
            std::string cookie = "Cookie: " + cookie_header_;
            headers = curl_slist_append(headers, cookie.c_str());
        }

        curl_easy_setopt(h, CURLOPT_URL, url.c_str());
        curl_easy_setopt(h, CURLOPT_USERAGENT, UA);
        curl_easy_setopt(h, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(h, CURLOPT_FOLLOWLOCATION, 1L);
        curl_easy_setopt(h, CURLOPT_TIMEOUT, 25L);
        curl_easy_setopt(h, CURLOPT_NOSIGNAL, 1L);
        curl_easy_setopt(h, CURLOPT_ACCEPT_ENCODING, "");
        curl_easy_setopt(h, CURLOPT_WRITEFUNCTION, write_string_cb);
        curl_easy_setopt(h, CURLOPT_WRITEDATA, &body);

        CURLcode res = curl_easy_perform(h);
        long code = 0;
        curl_easy_getinfo(h, CURLINFO_RESPONSE_CODE, &code);
        curl_slist_free_all(headers);
        curl_easy_cleanup(h);

        if (res != CURLE_OK) {
            last_err = curl_easy_strerror(res);
            if (attempt == 1) {
                sleep(1);
                continue;
            }
            throw MesApiError("сеть недоступна: " + last_err);
        }
        if (code == 401 || code == 403) {
            throw MesAuthError(
                "МЭШ отклонил сессию (сессия истекла). Выполни `sch login` "
                "или открой school.mos.ru в Chromium.");
        }
        if (code >= 500 && attempt == 1) {
            sleep(1);
            continue;
        }
        if (code != 200) {
            throw MesApiError("HTTP " + std::to_string(code) + ": " +
                              body.substr(0, 200));
        }
        try {
            return json::parse(body);
        } catch (const std::exception& e) {
            throw MesApiError(std::string("ответ МЭШ не JSON: ") + e.what());
        }
    }
    throw MesApiError("не дозвонился до МЭШ: " + last_err);
}

long long MesClient::download(const std::string& url,
                              const std::string& dest) {
    std::string tmp = dest + ".part";
    std::error_code ec;
    auto slash = dest.find_last_of('/');
    if (slash != std::string::npos) {
        std::filesystem::create_directories(dest.substr(0, slash), ec);
    }

    std::string last_err;
    try {
        for (int attempt = 1; attempt <= 2; ++attempt) {
            FILE* f = std::fopen(tmp.c_str(), "wb");
            if (!f) throw MesApiError("не открыть файл: " + tmp);
            CURL* h = curl_easy_init();
            if (!h) {
                std::fclose(f);
                throw MesApiError("не удалось инициализировать libcurl");
            }
            struct curl_slist* headers = nullptr;
            std::string auth = "Authorization: Bearer " + token;
            headers = curl_slist_append(headers, auth.c_str());
            if (!cookie_header_.empty()) {
                std::string cookie = "Cookie: " + cookie_header_;
                headers = curl_slist_append(headers, cookie.c_str());
            }
            curl_easy_setopt(h, CURLOPT_URL, url.c_str());
            curl_easy_setopt(h, CURLOPT_USERAGENT, UA);
            curl_easy_setopt(h, CURLOPT_HTTPHEADER, headers);
            curl_easy_setopt(h, CURLOPT_FOLLOWLOCATION, 1L);
            curl_easy_setopt(h, CURLOPT_TIMEOUT, 25L);
            curl_easy_setopt(h, CURLOPT_NOSIGNAL, 1L);
            curl_easy_setopt(h, CURLOPT_ACCEPT_ENCODING, "");
            curl_easy_setopt(h, CURLOPT_WRITEFUNCTION, write_file_cb);
            curl_easy_setopt(h, CURLOPT_WRITEDATA, f);

            CURLcode res = curl_easy_perform(h);
            long code = 0;
            curl_easy_getinfo(h, CURLINFO_RESPONSE_CODE, &code);
            curl_slist_free_all(headers);
            curl_easy_cleanup(h);
            long long size = std::ftell(f);
            std::fclose(f);

            if (res != CURLE_OK) {
                last_err = curl_easy_strerror(res);
                if (attempt == 1) {
                    sleep(1);
                    continue;
                }
                throw MesApiError("сеть недоступна: " + last_err);
            }
            if (code == 401 || code == 403) {
                throw MesAuthError(
                    "МЭШ отклонил сессию при скачивании. Выполни `sch login`.");
            }
            if (code >= 500 && attempt == 1) {
                last_err = "HTTP " + std::to_string(code);
                sleep(1);
                continue;
            }
            if (code != 200) {
                throw MesApiError("HTTP " + std::to_string(code) +
                                  " при скачивании " + url);
            }
            std::filesystem::rename(tmp, dest, ec);
            if (ec) {
                std::filesystem::remove(dest, ec);
                std::filesystem::rename(tmp, dest, ec);
            }
            return size;
        }
        throw MesApiError("не скачалось: " + url + " (" + last_err + ")");
    } catch (...) {
        std::error_code e2;
        std::filesystem::remove(tmp, e2);
        throw;
    }
}

json MesClient::profile() { return get("/profile"); }

std::string MesClient::student_id() {
    if (sid_) return *sid_;
    json p = profile();
    std::string sid = jstr(jref(p, "profile"), "id");
    if (sid.empty()) {
        json kids = jarr(p, "children");
        if (!kids.empty()) sid = jstr(kids[0], "id");
    }
    if (sid.empty())
        throw MesApiError("не могу определить student_id из профиля");
    sid_ = sid;
    return sid;
}

json MesClient::schedule_day(std::optional<std::string> date) {
    std::vector<std::pair<std::string, std::string>> params = {
        {"student_id", student_id()}};
    if (date) params.emplace_back("date", *date);
    return get("/schedule", params);
}

json MesClient::schedule_short(const std::vector<std::string>& dates) {
    std::string joined;
    for (std::size_t i = 0; i < dates.size(); ++i) {
        if (i) joined += ",";
        joined += dates[i];
    }
    json data = get("/schedule/short",
                    {{"student_id", student_id()}, {"dates", joined}});
    json payload = jarr(data, "payload");
    return payload;
}

json MesClient::homeworks(const std::string& frm, const std::string& to) {
    json data = get("/homeworks", {{"student_id", student_id()},
                                   {"from", frm},
                                   {"to", to}});
    return jarr(data, "payload");
}

json MesClient::marks(const std::string& frm, const std::string& to) {
    json data = get("/marks", {{"student_id", student_id()},
                               {"from", frm},
                               {"to", to}});
    return jarr(data, "payload");
}

json MesClient::day(std::optional<std::string> date) {
    json full = schedule_day(date);
    std::string day_date = jstr(full, "date");
    json short_payload = json::array();
    if (!day_date.empty()) {
        try {
            short_payload = schedule_short({day_date});
        } catch (const MesApiError&) {
            short_payload = json::array();  // темы не критичны
        } catch (const MesAuthError&) {
            short_payload = json::array();
        }
    }
    json hw = json::array();
    if (!day_date.empty()) hw = homeworks(day_date, day_date);
    return normalize_day(full, short_payload, hw);
}

std::vector<json> MesClient::week(const std::string& start, int days) {
    auto d0 = util::parse_iso(start);
    if (!d0) throw AppError("плохая дата: " + start);
    std::vector<std::string> dates;
    for (int i = 0; i < days; ++i)
        dates.push_back(util::iso_date(util::add_days(*d0, i)));

    json short_payload = json::array();
    try {
        short_payload = schedule_short(dates);
    } catch (const MesApiError&) {
        short_payload = json::array();
    } catch (const MesAuthError&) {
        short_payload = json::array();
    }
    json hw = json::array();
    try {
        hw = homeworks(dates.front(), dates.back());
    } catch (const MesApiError&) {
        hw = json::array();
    } catch (const MesAuthError&) {
        hw = json::array();
    }

    std::vector<json> out(dates.size());
    std::atomic<std::size_t> next{0};
    auto worker = [&]() {
        for (;;) {
            std::size_t i = next.fetch_add(1);
            if (i >= dates.size()) break;
            try {
                MesClient c(cks_, source);
                out[i] = normalize_day(c.schedule_day(dates[i]),
                                       short_payload, hw);
            } catch (const std::exception& e) {
                json o = json::object();
                o["date"] = dates[i];
                o["error"] = e.what();
                out[i] = o;
            }
        }
    };
    std::size_t nthreads = std::min<std::size_t>(6, dates.size());
    std::vector<std::thread> threads;
    for (std::size_t i = 0; i < nthreads; ++i) threads.emplace_back(worker);
    for (auto& t : threads) t.join();
    return out;
}

std::vector<FileRef> MesClient::files_from_homework(const json& hw) {
    std::vector<FileRef> out;
    std::set<std::string> seen;

    auto add = [&](const std::string& url_in, const std::string& title_in,
                   const std::string& kind_in) {
        std::string url = util::trim(url_in);
        if (!util::starts_with(url, "http://") &&
            !util::starts_with(url, "https://"))
            return;
        if (seen.count(url)) return;
        seen.insert(url);
        FileRef ref;
        ref.title = util::trim(title_in);
        ref.url = url;
        ref.kind = util::trim(kind_in);
        out.push_back(std::move(ref));
    };

    for (const auto& m : jarr(hw, "materials")) {
        if (!m.is_object()) continue;
        std::string kind = jstr(m, "type_name");
        if (kind.empty()) kind = jstr(m, "type");
        for (const auto& u : jarr(m, "urls")) {
            if (u.is_object()) {
                std::string type = jstr(u, "type");
                if (!type.empty() && type != "file_link") continue;
                std::string title = jstr(m, "title");
                if (title.empty()) title = jstr(u, "title");
                add(jstr(u, "url"), title, kind);
            } else if (u.is_string()) {
                add(u.get<std::string>(), jstr(m, "title"), kind);
            }
        }
    }

    std::function<void(const json&)> walk = [&](const json& node) {
        if (node.is_object()) {
            std::string url = jstr(node, "url");
            if (url.empty()) url = jstr(node, "file_url");
            if (url.empty()) url = jstr(node, "link");
            std::string title = jstr(node, "title");
            if (title.empty()) title = jstr(node, "name");
            std::string kind = jstr(node, "type_name");
            if (kind.empty()) kind = "attachment";
            add(url, title, kind);
            for (auto it = node.begin(); it != node.end(); ++it) {
                if (it->is_object() || it->is_array()) walk(*it);
            }
        } else if (node.is_array()) {
            for (const auto& x : node) walk(x);
        }
    };
    walk(jref(hw, "attachments"));
    return out;
}

static bool truthy_json(const json& v) {
    if (v.is_null()) return false;
    if (v.is_string()) return !v.get<std::string>().empty();
    if (v.is_boolean()) return v.get<bool>();
    if (v.is_number()) return v.get<double>() != 0;
    if (v.is_array() || v.is_object()) return !v.empty();
    return true;
}

json MesClient::normalize_day(const json& full, const json& short_payload,
                              const json& hw_payload) {
    std::string day_date = jstr(full, "date");

    std::map<std::string, std::string> topic_by_id;
    std::map<std::string, json> group_by_id;
    std::map<std::string, std::string> group_by_time;

    if (short_payload.is_array()) {
        for (const auto& day : short_payload) {
            for (const auto& l : jarr(day, "lessons")) {
                std::string key = jstr(l, "schedule_item_id");
                if (!key.empty()) {
                    topic_by_id[key] = util::trim(jstr(l, "lesson_name"));
                    group_by_id[key] = jref(l, "group_name");
                }
                std::string gb = jstr(l, "group_name");
                if (gb.empty()) gb = jstr(l, "subject_name");
                if (!gb.empty()) group_by_time.emplace(jstr(l, "begin_time"), gb);
            }
        }
    }

    json lessons = json::array();
    json extra = json::array();

    for (const auto& a : jarr(full, "activities")) {
        if (jstr(a, "type") != "LESSON") continue;
        const json& l = jref(a, "lesson");
        std::string item_id = jstr(l, "schedule_item_id");
        std::string edu = jstr(l, "lesson_education_type");

        std::string topic;
        auto tit = topic_by_id.find(item_id);
        if (tit != topic_by_id.end()) topic = tit->second;

        json group = nullptr;
        auto git = group_by_id.find(item_id);
        if (git != group_by_id.end() && truthy_json(git->second))
            group = git->second;
        else {
            auto gt = group_by_time.find(jstr(a, "begin_time"));
            if (gt != group_by_time.end()) group = gt->second;
        }

        json row = json::object();
        row["begin"] = jref(a, "begin_time");
        row["end"] = jref(a, "end_time");
        row["subject"] = jstr(l, "subject_name");
        row["group"] = group;
        row["topic"] = topic;
        row["room"] = jref(a, "room_number");
        row["room_name"] = jref(a, "room_name");
        row["building"] = jref(a, "building_name");
        row["teacher"] = teacher_name(jref(l, "teacher"));

        json marks = json::array();
        for (const auto& m : jarr(l, "marks")) {
            std::string v = json_scalar_to_string(jref(m, "value"));
            if (v.empty() || v == "точка") continue;
            marks.push_back(v);
        }
        row["marks"] = marks;
        row["homework"] = util::trim(jstr(l, "homework"));
        row["cancelled"] = jbool(l, "is_cancelled");
        row["replaced"] = jbool(l, "replaced");
        row["virtual"] = jbool(l, "is_virtual");

        if (jstr(row, "subject").empty() && group.is_string())
            row["subject"] = group.get<std::string>();

        bool is_extra = (edu == "AE" || edu == "EC") ||
                        jstr(l, "subject_name").empty();
        if (is_extra) {
            row["kind"] = "extra";
            extra.push_back(row);
        } else {
            row["kind"] = "lesson";
            lessons.push_back(row);
        }
    }

    for (std::size_t i = 0; i < lessons.size(); ++i)
        lessons[i]["n"] = static_cast<long long>(i + 1);

    json homeworks = json::array();
    for (const auto& x : hw_payload) {
        if (jstr(x, "date") != day_date) continue;
        std::string text = util::trim(jstr(x, "description"));
        if (text.empty()) text = util::trim(jstr(x, "homework"));

        json files = json::array();
        for (const auto& f : files_from_homework(x)) {
            json fo = json::object();
            fo["title"] = f.title;
            fo["url"] = f.url;
            fo["kind"] = f.kind;
            files.push_back(fo);
        }

        std::string ldt = jstr(x, "lesson_date_time");
        std::string lesson_time = ldt.size() >= 16 ? ldt.substr(11, 5) : "";

        json h = json::object();
        h["subject"] = jstr(x, "subject_name");
        h["text"] = text;
        h["done"] = jbool(x, "is_done");
        h["has_teacher_answer"] = jbool(x, "has_teacher_answer");
        h["attachments"] = static_cast<long long>(jarr(x, "attachments").size());
        h["materials"] = static_cast<long long>(jarr(x, "materials").size());
        h["files"] = files;
        h["lesson_time"] = lesson_time;
        h["type"] = jref(x, "type");
        homeworks.push_back(h);
    }
    std::stable_sort(homeworks.begin(), homeworks.end(),
                     [](const json& a, const json& b) {
                         return jstr(a, "lesson_time") < jstr(b, "lesson_time");
                     });

    json out = json::object();
    out["date"] = day_date;
    out["summary"] = jstr(full, "summary");
    out["lessons"] = lessons;
    out["extra"] = extra;
    out["homeworks"] = homeworks;
    return out;
}

std::string MesClient::teacher_name(const json& t) {
    if (!t.is_object()) return "";
    std::string last = jstr(t, "last_name");
    std::string first = util::utf8_first_char(jstr(t, "first_name"));
    std::string middle = util::utf8_first_char(jstr(t, "middle_name"));
    std::vector<std::string> ini;
    if (!first.empty()) ini.push_back(first);
    if (!middle.empty()) ini.push_back(middle);
    std::string initials = util::join(ini, ".");
    return util::trim(last + (initials.empty() ? "" : " " + initials + "."));
}
