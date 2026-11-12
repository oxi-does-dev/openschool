#include "download.hpp"

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <map>
#include <set>
#include <string>
#include <vector>

#include "errors.hpp"
#include "render.hpp"
#include "util.hpp"

namespace fs = std::filesystem;

static const char* FORBIDDEN = "<>:\"/\\|?*";

std::string download_root(const std::string& override_dir) {
    if (!override_dir.empty()) return util::expand_tilde(override_dir);
    const char* env = std::getenv("SCH_DZ_DIR");
    if (env && *env) {
        std::string v = util::trim(env);
        if (!v.empty()) return util::expand_tilde(v);
    }
    const char* home = std::getenv("HOME");
    return std::string(home ? home : ".") + "/DZ";
}

std::string week_folder(const util::Date& day) {
    util::Date monday = util::add_days(day, -util::weekday_py(day));
    util::Date sunday = util::add_days(monday, 6);
    char buf[64];
    std::snprintf(buf, sizeof(buf), "week%02d_%02d.%02d-%02d.%02d",
                  util::iso_week(day), monday.d, monday.m, sunday.d, sunday.m);
    return buf;
}

static std::string strip_dots_spaces(const std::string& s) {
    std::size_t b = 0, e = s.size();
    while (b < e && (s[b] == ' ' || s[b] == '.')) ++b;
    while (e > b && (s[e - 1] == ' ' || s[e - 1] == '.')) --e;
    return s.substr(b, e - b);
}

std::string safe_name(const std::string& text_in, std::size_t limit) {
    std::string text = util::percent_decode(text_in);
    std::string cleaned;
    for (unsigned char c : text) {
        if (c < 0x20 || c == 0x7F ||
            std::string(FORBIDDEN).find(static_cast<char>(c)) !=
                std::string::npos) {
            cleaned += '_';
        } else {
            cleaned += static_cast<char>(c);
        }
    }
    cleaned = util::one_line(cleaned);
    cleaned = strip_dots_spaces(cleaned);
    cleaned = strip_dots_spaces(util::truncate_utf8(cleaned, limit));
    return cleaned.empty() ? "файл" : cleaned;
}

static std::string url_path(const std::string& url) {
    std::size_t scheme = url.find("://");
    std::size_t start = scheme == std::string::npos ? 0 : scheme + 3;
    std::size_t slash = url.find('/', start);
    if (slash == std::string::npos) return "";
    std::size_t end = url.find_first_of("?#", slash);
    if (end == std::string::npos) end = url.size();
    return url.substr(slash, end - slash);
}

static std::string url_name(const std::string& url) {
    std::string path = url_path(url);
    std::size_t slash = path.find_last_of('/');
    std::string name =
        slash == std::string::npos ? path : path.substr(slash + 1);
    return util::percent_decode(name);
}

static std::pair<std::string, std::string> splitext(const std::string& name) {
    std::size_t slash = name.find_last_of('/');
    std::size_t base_start = slash == std::string::npos ? 0 : slash + 1;
    std::size_t dot = name.find_last_of('.');
    if (dot == std::string::npos || dot <= base_start)
        return {name, ""};
    return {name.substr(0, dot), name.substr(dot)};
}

std::string build_name(const FileRef& item) {
    util::Date day;
    auto parsed = util::parse_iso(item.date);
    if (parsed)
        day = *parsed;
    else
        day = util::today_local();

    std::string uname = safe_name(url_name(item.url), 160);
    std::string title = safe_name(item.title.empty() ? uname : item.title, 160);
    auto [stem, ext] = splitext(title);
    if (ext.empty()) {
        auto ue = splitext(uname);
        ext = ue.second;
    }
    if (ext.size() > 16 || ext.find('\n') != std::string::npos) ext.clear();
    std::string subject = safe_name(item.subject, 40);

    char prefix[64];
    std::snprintf(prefix, sizeof(prefix), "%02d.%02d_", day.d, day.m);
    std::string pre = std::string(prefix) + subject + "_";

    long long room = 250 - static_cast<long long>(pre.size()) -
                     static_cast<long long>(ext.size());
    std::size_t limit = static_cast<std::size_t>(std::max<long long>(24, room));
    stem = strip_dots_spaces(util::truncate_utf8(stem, limit));
    if (stem.empty()) stem = "файл";
    return pre + stem + ext;
}

std::string human_size(long long n) {
    if (n < 1024) return std::to_string(n) + " Б";
    double value = static_cast<double>(n);
    const char* units[] = {"КБ", "МБ", "ГБ"};
    for (int i = 0; i < 3; ++i) {
        value /= 1024.0;
        if (value < 1024.0 || i == 2) {
            char buf[64];
            std::snprintf(buf, sizeof(buf), "%.1f %s", value, units[i]);
            std::string s = buf;
            auto pos = s.find(".0 ");
            if (pos != std::string::npos) s.replace(pos, 3, " ");
            return s;
        }
    }
    return std::to_string(n) + " Б";
}

std::vector<FileRef> files_from_homeworks(const json& homeworks) {
    std::vector<FileRef> out;
    std::set<std::string> seen;
    for (const auto& hw : homeworks) {
        std::string iso = jstr(hw, "date");
        std::string subject = jstr(hw, "subject_name");
        for (const auto& f : MesClient::files_from_homework(hw)) {
            if (f.url.empty() || seen.count(f.url)) continue;
            seen.insert(f.url);
            FileRef ref;
            ref.title = f.title;
            ref.url = f.url;
            ref.kind = f.kind;
            ref.date = iso;
            ref.subject = subject;
            out.push_back(std::move(ref));
        }
    }
    return out;
}

std::vector<FileRef> files_from_days(const std::vector<json>& days) {
    std::vector<FileRef> out;
    std::set<std::string> seen;
    for (const auto& day : days) {
        std::string iso = jstr(day, "date");
        for (const auto& hw : jarr(day, "homeworks")) {
            std::string subject = jstr(hw, "subject");
            for (const auto& f : jarr(hw, "files")) {
                std::string url = util::trim(jstr(f, "url"));
                if (url.empty() || seen.count(url)) continue;
                seen.insert(url);
                FileRef ref;
                ref.title = jstr(f, "title");
                ref.url = url;
                ref.kind = jstr(f, "kind");
                ref.date = iso;
                ref.subject = subject;
                out.push_back(std::move(ref));
            }
        }
    }
    return out;
}

static std::string unique_path(const std::string& path,
                               std::set<std::string>& used) {
    if (!used.count(path)) {
        used.insert(path);
        return path;
    }
    fs::path p(path);
    std::string stem = p.stem().string();
    std::string ext = p.extension().string();
    int n = 2;
    for (;;) {
        fs::path candidate =
            p.parent_path() / (stem + "_" + std::to_string(n) + ext);
        std::string s = candidate.string();
        if (!used.count(s)) {
            used.insert(s);
            return s;
        }
        ++n;
    }
}

static DownloadResult download_one(MesClient& client, const FileRef& item,
                                   const std::string& root, bool force,
                                   bool dry_run, std::set<std::string>& used) {
    util::Date day;
    auto parsed = util::parse_iso(item.date);
    if (parsed)
        day = *parsed;
    else
        day = util::today_local();

    std::string dest =
        (fs::path(root) / week_folder(day) / build_name(item)).string();
    dest = unique_path(dest, used);

    DownloadResult res;
    res.item = item;
    res.path = dest;

    if (dry_run) {
        if (force || !fs::exists(dest)) {
            res.status = "would";
            return res;
        }
        std::error_code ec;
        auto sz = fs::file_size(dest, ec);
        res.size = ec ? 0 : static_cast<long long>(sz);
        res.status = "exists";
        return res;
    }
    if (fs::exists(dest) && !force) {
        std::error_code ec;
        auto sz = fs::file_size(dest, ec);
        res.size = ec ? 0 : static_cast<long long>(sz);
        res.status = "exists";
        return res;
    }
    try {
        res.size = client.download(item.url, dest);
        res.status = "downloaded";
    } catch (const std::exception& e) {
        res.status = "error";
        res.error = e.what();
    }
    return res;
}

std::vector<DownloadResult> sync_items(MesClient& client,
                                       const std::vector<FileRef>& items,
                                       const std::string& root, bool force,
                                       bool dry_run) {
    std::set<std::string> used;
    std::vector<DownloadResult> out;
    for (const auto& item : items)
        out.push_back(download_one(client, item, root, force, dry_run, used));
    return out;
}

static std::string rel_path(const std::string& path, const std::string& root) {
    std::string prefix = root;
    if (!prefix.empty() && prefix.back() != '/') prefix += '/';
    if (util::starts_with(path, prefix)) return path.substr(prefix.size());
    return path;
}

std::vector<DownloadResult> auto_sync(MesClient& client,
                                      const std::vector<json>& days) {
    std::vector<DownloadResult> results;
    try {
        std::vector<FileRef> items = files_from_days(days);
        if (items.empty()) return {};
        std::string root = download_root();
        results = sync_items(client, items, root);
        for (const auto& res : results) {
            if (res.status == "downloaded") {
                std::fprintf(stderr, "📎 %s  (%s)\n",
                             rel_path(res.path, root).c_str(),
                             human_size(res.size).c_str());
            } else if (res.status == "error") {
                std::fprintf(stderr, "⚠ Материалы ДЗ: %s: %s\n",
                             res.item.subject.c_str(), res.error.c_str());
            }
        }
    } catch (const std::exception& e) {
        std::fprintf(stderr, "⚠ Материалы ДЗ: %s\n", e.what());
        return {};
    }
    return results;
}

static std::map<std::string, int> counts(
    const std::vector<DownloadResult>& results) {
    std::map<std::string, int> c = {
        {"downloaded", 0}, {"exists", 0}, {"would", 0}, {"error", 0}};
    for (const auto& r : results) c[r.status] += 1;
    return c;
}

std::string format_text(const std::vector<DownloadResult>& results,
                        const std::string& root) {
    if (results.empty()) return "Материалов для скачивания нет.";
    std::vector<std::string> lines;
    lines.push_back(render::bold("Материалы ДЗ → " + root));
    for (const auto& res : results) {
        std::string rel = rel_path(res.path, root);
        if (res.status == "downloaded") {
            lines.push_back("  " + render::green("+") + " " + rel + "  (" +
                            human_size(res.size) + ")");
        } else if (res.status == "exists") {
            lines.push_back("  " + render::dim("=") + " " + rel + "  (уже есть, " +
                            human_size(res.size) + ")");
        } else if (res.status == "would") {
            lines.push_back("  " + render::yellow("→") + " " + rel +
                            "  (будет скачано)");
        } else {
            lines.push_back("  " + render::red("!") + " " + rel + "  " +
                            res.error);
        }
    }
    auto c = counts(results);
    std::vector<std::string> parts = {
        "скачано " + std::to_string(c["downloaded"])};
    if (c["exists"]) parts.push_back("уже было " + std::to_string(c["exists"]));
    if (c["would"])
        parts.push_back("к скачиванию " + std::to_string(c["would"]));
    if (c["error"]) parts.push_back("ошибок " + std::to_string(c["error"]));
    lines.push_back("Итог: " + util::join(parts, ", ") + ".");

    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return out;
}

std::string format_md(const std::vector<DownloadResult>& results,
                      const std::string& root) {
    if (results.empty())
        return "# Материалы ДЗ\n\nМатериалов для скачивания нет.";
    std::vector<std::string> lines = {"# Материалы ДЗ", ""};
    for (const auto& res : results) {
        std::string rel = rel_path(res.path, root);
        std::string ddmm = res.item.date;
        auto parsed = util::parse_iso(res.item.date);
        if (parsed) {
            char buf[16];
            std::snprintf(buf, sizeof(buf), "%02d.%02d", parsed->d, parsed->m);
            ddmm = buf;
        }
        std::string status;
        if (res.status == "downloaded")
            status = "скачано, " + human_size(res.size);
        else if (res.status == "exists")
            status = "уже есть";
        else if (res.status == "would")
            status = "будет скачано";
        else if (res.status == "error")
            status = "ошибка: " + res.error;
        else
            status = res.status;
        lines.push_back("- **" + ddmm + ", " + res.item.subject + "** — `" +
                        rel + "` — " + status);
    }
    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return out;
}

json results_json(const std::vector<DownloadResult>& results,
                  const std::string& root) {
    json items = json::array();
    for (const auto& res : results) {
        json o = json::object();
        o["date"] = res.item.date;
        o["subject"] = res.item.subject;
        o["title"] = res.item.title;
        o["url"] = res.item.url;
        o["path"] = res.path;
        o["status"] = res.status;
        o["size"] = res.size;
        o["error"] = res.error.empty() ? json(nullptr) : json(res.error);
        items.push_back(o);
    }
    auto c = counts(results);
    json summary = json::object();
    summary["downloaded"] = c["downloaded"];
    summary["exists"] = c["exists"];
    summary["would"] = c["would"];
    summary["error"] = c["error"];

    json out = json::object();
    out["root"] = root;
    out["items"] = items;
    out["summary"] = summary;
    return out;
}
