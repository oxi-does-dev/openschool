#include "render.hpp"

#include <algorithm>
#include <cstdio>
#include <set>
#include <string>
#include <vector>

#include "util.hpp"

namespace render {

static bool g_color = false;

static const char* MONTHS[] = {"января", "февраля", "марта",  "апреля",
                               "мая",    "июня",    "июля",   "августа",
                               "сентября", "октября", "ноября", "декабря"};
static const char* WEEKDAYS[] = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

void use_color(bool on) { g_color = on; }

static std::string color(const char* code, const std::string& s) {
    if (!g_color) return s;
    return std::string("\033[") + code + "m" + s + "\033[0m";
}

std::string bold(const std::string& s) { return color("1", s); }
std::string dim(const std::string& s) { return color("2", s); }
std::string green(const std::string& s) { return color("32", s); }
std::string yellow(const std::string& s) { return color("33", s); }
std::string red(const std::string& s) { return color("31", s); }
std::string cyan(const std::string& s) { return color("36", s); }

std::string human_date(const std::string& iso) {
    auto d = util::parse_iso(iso);
    if (!d) return iso;
    return std::string(WEEKDAYS[util::weekday_py(*d)]) + ", " +
           std::to_string(d->d) + " " + MONTHS[d->m - 1] + " " +
           std::to_string(d->y);
}

std::string day_suffix(const std::string& iso) {
    auto d = util::parse_iso(iso);
    if (!d) return "";
    int delta = util::days_between(util::today_local(), *d);
    switch (delta) {
        case 0:
            return " — сегодня";
        case 1:
            return " — завтра";
        case 2:
            return " — послезавтра";
        case -1:
            return " — вчера";
        default:
            return "";
    }
}

static std::string grade(const std::string& v) {
    if (v == "5") return green(v);
    if (v == "4") return yellow(v);
    if (v == "1" || v == "2" || v == "3") return red(v);
    return cyan(v);
}

static std::string pad_right(const std::string& s, std::size_t width) {
    std::size_t n = util::utf8_len(s);
    if (n >= width) return s;
    return s + std::string(width - n, ' ');
}

static std::string join_grades(const json& marks) {
    std::string out;
    bool first = true;
    for (const auto& m : marks) {
        if (!first) out += " ";
        first = false;
        out += grade(json_scalar_to_string(m));
    }
    return out;
}

static bool show_has(const std::vector<std::string>& show,
                     const std::string& what) {
    return std::find(show.begin(), show.end(), what) != show.end();
}

// ------------------------------------------------------------------ день

std::string fmt_day(const json& day, const std::vector<std::string>& show) {
    std::string err = jstr(day, "error");
    if (!err.empty()) return red("Ошибка: " + err);

    std::string iso = jstr(day, "date");
    if (iso.empty()) iso = "?";

    std::vector<std::string> lines;
    lines.push_back(bold(human_date(iso) + day_suffix(iso)));
    std::string summary = jstr(day, "summary");
    if (!summary.empty()) lines.push_back(dim("  " + summary));
    lines.push_back("");

    json lessons = jarr(day, "lessons");
    json extra = jarr(day, "extra");
    json homeworks = jarr(day, "homeworks");
    bool show_lessons = show_has(show, "lessons");
    bool show_hw = show_has(show, "hw");

    if (show_lessons) {
        if (!lessons.empty()) {
            lines.push_back(bold("Уроки:"));
            for (const auto& l : lessons) {
                std::string mark;
                if (!jarr(l, "marks").empty())
                    mark = "  " + join_grades(jarr(l, "marks"));
                std::string cancelled =
                    jbool(l, "cancelled") ? red(" (отменён)") : "";
                std::string room = jstr(l, "room").empty()
                                       ? ""
                                       : "  ·  каб. " + jstr(l, "room");
                std::string subj = jstr(l, "subject");
                if (subj.empty()) subj = jstr(l, "group");
                if (subj.empty()) subj = "?";
                lines.push_back(" " + jstr(l, "n") + ". " +
                                cyan(jstr(l, "begin") + "–" + jstr(l, "end")) +
                                "  " + subj + room + mark + cancelled);
                std::string topic = util::one_line(jstr(l, "topic"));
                if (!topic.empty() && util::lower(topic) != util::lower(subj)) {
                    lines.push_back(dim(util::wrap(topic, 86, "      ", "      ")));
                }
            }
            lines.push_back("");
        } else if (!show_hw || (extra.empty() && homeworks.empty())) {
            lines.push_back("Уроков нет.");
            lines.push_back("");
        }

        if (!extra.empty()) {
            lines.push_back(bold("Доп. занятия:"));
            for (const auto& l : extra) {
                std::string title = jstr(l, "group");
                if (title.empty()) title = jstr(l, "subject");
                if (title.empty()) title = "Занятие";
                title = util::shorten(title, 110);
                std::string teacher = jstr(l, "teacher");
                if (!teacher.empty()) {
                    std::string first = teacher.substr(0, teacher.find(' '));
                    if (title.find(first) == std::string::npos)
                        title += "  ·  " + teacher;
                }
                std::string room = jstr(l, "room").empty()
                                       ? ""
                                       : "  ·  каб. " + jstr(l, "room");
                lines.push_back(" " +
                                cyan(jstr(l, "begin") + "–" + jstr(l, "end")) +
                                "  " + title + room);
            }
            lines.push_back("");
        }
    }

    if (show_hw) {
        if (!homeworks.empty()) {
            lines.push_back(bold("ДЗ:"));
            for (const auto& h : homeworks) {
                std::string mark = jbool(h, "done") ? green("✓") : "•";
                std::vector<std::string> bits;
                long long n_mat = jint(h, "attachments") + jint(h, "materials");
                if (n_mat) bits.push_back("материалов: " + std::to_string(n_mat));
                std::size_t n_files = jarr(h, "files").size();
                if (n_files)
                    bits.push_back("файлов: " + std::to_string(n_files));
                std::string suffix =
                    bits.empty() ? "" : dim("  (" + util::join(bits, ", ") + ")");
                std::string subject = jstr(h, "subject");
                if (subject.empty()) subject = "?";
                std::string prefix = " " + mark + " " + subject + " — ";
                std::string body = util::one_line(jstr(h, "text"));
                if (body.empty()) body = "(см. материалы)";
                lines.push_back(
                    util::wrap(body, 98, prefix, "    ") + suffix);
            }
            lines.push_back("");
        } else {
            lines.push_back("Заданий нет.");
            lines.push_back("");
        }
    }

    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return util::rtrim(out);
}

// ------------------------------------------------------------------ день (md)

std::string fmt_day_md(const json& day, const std::vector<std::string>& show,
                       int level) {
    std::string err = jstr(day, "error");
    if (!err.empty()) return "> Ошибка: " + err;

    std::string iso = jstr(day, "date");
    if (iso.empty()) iso = "?";
    std::string h1(static_cast<std::size_t>(level), '#');
    std::string h2(static_cast<std::size_t>(level + 1), '#');

    std::vector<std::string> lines;
    lines.push_back(h1 + " " + human_date(iso) + day_suffix(iso));
    lines.push_back("");
    std::string summary = jstr(day, "summary");
    if (!summary.empty()) {
        lines.push_back("*" + util::md_escape(summary) + "*");
        lines.push_back("");
    }

    json lessons = jarr(day, "lessons");
    json extra = jarr(day, "extra");
    if (show_has(show, "lessons")) {
        lines.push_back(h2 + " Уроки");
        lines.push_back("");
        if (!lessons.empty()) {
            lines.push_back("| № | Время | Предмет | Каб. | Тема |");
            lines.push_back("|---|-------|---------|------|------|");
            for (const auto& l : lessons) {
                std::string subj = jstr(l, "subject");
                if (subj.empty()) subj = jstr(l, "group");
                if (subj.empty()) subj = "?";
                subj = util::md_escape(subj);
                if (jbool(l, "cancelled")) subj += " *(отменён)*";
                json marks = jarr(l, "marks");
                if (!marks.empty()) {
                    for (const auto& m : marks)
                        subj += " **" + json_scalar_to_string(m) + "**";
                }
                std::string room = jstr(l, "room");
                room = room.empty() ? "—" : util::md_escape(room);
                std::string topic = util::md_escape(jstr(l, "topic"));
                if (topic.empty()) topic = "—";
                lines.push_back("| " + jstr(l, "n") + " | " +
                                jstr(l, "begin") + "–" + jstr(l, "end") +
                                " | " + subj + " | " + room + " | " + topic +
                                " |");
            }
        } else {
            lines.push_back("Уроков нет.");
        }
        lines.push_back("");
        if (!extra.empty()) {
            lines.push_back(h2 + " Доп. занятия");
            lines.push_back("");
            for (const auto& l : extra) {
                std::string title = jstr(l, "group");
                if (title.empty()) title = jstr(l, "subject");
                if (title.empty()) title = "Занятие";
                title = util::md_escape(title);
                std::string room = jstr(l, "room").empty()
                                       ? ""
                                       : " · каб. " + util::md_escape(jstr(l, "room"));
                lines.push_back("- **" + jstr(l, "begin") + "–" +
                                jstr(l, "end") + "** — " + title + room);
            }
            lines.push_back("");
        }
    }

    if (show_has(show, "hw")) {
        json hws = jarr(day, "homeworks");
        lines.push_back(h2 + " Домашние задания");
        lines.push_back("");
        if (!hws.empty()) {
            for (const auto& h : hws) {
                std::string done = jbool(h, "done") ? "x" : " ";
                std::string subject = jstr(h, "subject");
                if (subject.empty()) subject = "?";
                subject = util::md_escape(subject);
                std::string text = util::md_escape(jstr(h, "text"));
                if (text.empty()) text = "*(см. материалы)*";
                long long n_mat = jint(h, "attachments") + jint(h, "materials");
                std::size_t n_files = jarr(h, "files").size();
                std::vector<std::string> bits;
                if (n_mat) bits.push_back("материалов: " + std::to_string(n_mat));
                if (n_files)
                    bits.push_back("файлов: " + std::to_string(n_files));
                std::string suffix =
                    bits.empty() ? "" : " *(" + util::join(bits, ", ") + ")*";
                lines.push_back("- [" + done + "] **" + subject + "** — " +
                                text + suffix);
            }
        } else {
            lines.push_back("Заданий нет.");
        }
        lines.push_back("");
    }

    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return util::rtrim(out);
}

// ------------------------------------------------------------------ неделя

std::string fmt_week(const std::vector<json>& days) {
    std::vector<std::string> out;
    for (const auto& d : days) {
        std::string err = jstr(d, "error");
        if (!err.empty()) {
            std::string iso = jstr(d, "date");
            if (iso.empty()) iso = "?";
            out.push_back(bold(human_date(iso)) + red("  ошибка"));
            continue;
        }
        std::string iso = jstr(d, "date");
        if (iso.empty()) iso = "?";
        out.push_back(bold(human_date(iso) + day_suffix(iso)));
        json lessons = jarr(d, "lessons");
        if (lessons.empty()) out.push_back(dim("   уроков нет"));
        for (const auto& l : lessons) {
            std::string mark;
            if (!jarr(l, "marks").empty())
                mark = "  " + join_grades(jarr(l, "marks"));
            std::string room = jstr(l, "room").empty()
                                   ? ""
                                   : "  каб. " + jstr(l, "room");
            std::string subj = jstr(l, "subject");
            if (subj.empty()) subj = jstr(l, "group");
            if (subj.empty()) subj = "?";
            out.push_back("  " + jstr(l, "n") + ". " + jstr(l, "begin") + "  " +
                          subj + room + mark);
        }
        for (const auto& l : jarr(d, "extra")) {
            std::string title = jstr(l, "group");
            if (title.empty()) title = jstr(l, "subject");
            if (title.empty()) title = "Занятие";
            title = util::shorten(title, 100);
            out.push_back(dim("     + " + jstr(l, "begin") + "  " + title));
        }
        json hws = jarr(d, "homeworks");
        if (!hws.empty()) {
            std::vector<std::string> subs;
            std::set<std::string> seen;
            for (const auto& h : hws) {
                std::string s = jstr(h, "subject");
                if (s.empty()) s = "?";
                if (seen.insert(s).second) subs.push_back(s);
            }
            out.push_back(dim("     ДЗ: " + util::join(subs, ", ")));
        }
        out.push_back("");
    }
    std::string joined;
    for (std::size_t i = 0; i < out.size(); ++i) {
        if (i) joined += '\n';
        joined += out[i];
    }
    return util::rtrim(joined);
}

static std::string range_human(const std::string& a, const std::string& b) {
    auto da = util::parse_iso(a);
    auto db = util::parse_iso(b);
    if (!da || !db) return "";
    if (*da == *db)
        return std::to_string(da->d) + " " + MONTHS[da->m - 1] + " " +
               std::to_string(da->y);
    if (da->y == db->y && da->m == db->m)
        return std::to_string(da->d) + "–" + std::to_string(db->d) + " " +
               MONTHS[db->m - 1] + " " + std::to_string(db->y);
    return std::to_string(da->d) + " " + MONTHS[da->m - 1] + " — " +
           std::to_string(db->d) + " " + MONTHS[db->m - 1] + " " +
           std::to_string(db->y);
}

std::string fmt_week_md(const std::vector<json>& days) {
    if (days.empty()) return "# МЭШ\n\nНет данных.";
    std::string iso_first = jstr(days.front(), "date");
    std::string iso_last = jstr(days.back(), "date");
    std::string title =
        (!iso_first.empty() && !iso_last.empty())
            ? range_human(iso_first, iso_last)
            : "";
    std::vector<std::string> out;
    out.push_back("# МЭШ: " + title);
    out.push_back("");
    for (const auto& d : days) {
        out.push_back(fmt_day_md(d, {"lessons", "hw"}, 2));
        out.push_back("");
    }
    std::string joined;
    for (std::size_t i = 0; i < out.size(); ++i) {
        if (i) joined += '\n';
        joined += out[i];
    }
    return util::rtrim(joined);
}

// ------------------------------------------------------------------ оценки

static std::string dmy(const std::string& iso) {
    if (iso.size() < 10) return iso;
    return iso.substr(8, 2) + "." + iso.substr(5, 2);
}

static std::string weight_text(const json& m) {
    if (!m.is_object()) return "";
    auto it = m.find("weight");
    if (it == m.end() || it->is_null()) return "";
    if (it->is_number() && it->get<double>() == 1.0) return "";
    if (it->is_number() && it->get<double>() == 0.0) return "";
    if (it->is_string() && (it->get<std::string>() == "1" ||
                            it->get<std::string>().empty()))
        return "";
    return " ×" + json_scalar_to_string(*it);
}

std::string fmt_marks(const std::vector<json>& items, const std::string& frm,
                      const std::string& to) {
    if (items.empty())
        return "Оценок с " + dmy(frm) + " по " + dmy(to) + " нет.";
    std::vector<std::string> lines;
    lines.push_back(bold("Оценки " + dmy(frm) + "–" + dmy(to) + ": " +
                         std::to_string(items.size()) + " шт."));
    for (const auto& m : items) {
        std::string date = jstr(m, "date");
        std::string dd = date.size() >= 10
                             ? date.substr(8, 2) + "." + date.substr(5, 2)
                             : date;
        std::string val = grade(jstr(m, "value"));
        std::string wtxt = weight_text(m);
        std::string form = jstr(m, "control_form_name");
        std::string comment = util::one_line(jstr(m, "comment"));
        std::string tail = form.empty() ? "" : "  " + form;
        if (!comment.empty()) tail += "  " + dim(comment);
        std::string subject = jstr(m, "subject_name");
        if (subject.empty()) subject = "?";
        lines.push_back(" " + dd + "  " + val + pad_right(wtxt, 4) + " " +
                        subject + tail);
    }
    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return out;
}

std::string fmt_marks_md(const std::vector<json>& items,
                         const std::string& frm, const std::string& to) {
    std::vector<std::string> lines;
    lines.push_back("# Оценки " + dmy(frm) + "–" + dmy(to) + ": " +
                    std::to_string(items.size()) + " шт.");
    lines.push_back("");
    if (items.empty()) {
        lines.push_back("Оценок нет.");
        std::string out;
        for (std::size_t i = 0; i < lines.size(); ++i) {
            if (i) out += '\n';
            out += lines[i];
        }
        return out;
    }
    lines.push_back("| Дата | Оценка | Предмет | Форма | Комментарий |");
    lines.push_back("|------|--------|---------|-------|-------------|");
    for (const auto& m : items) {
        std::string val = jstr(m, "value");
        val += weight_text(m);
        lines.push_back("| " + dmy(jstr(m, "date")) + " | " + val + " | " +
                        util::md_escape(jstr(m, "subject_name")) + " | " +
                        util::md_escape(jstr(m, "control_form_name")) + " | " +
                        util::md_escape(jstr(m, "comment")) + " |");
    }
    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return out;
}

// ------------------------------------------------------------------ профиль

static std::string full_name(const json& p) {
    std::string last = jstr(p, "last_name");
    std::string first = jstr(p, "first_name");
    std::string middle = jstr(p, "middle_name");
    std::string out;
    for (const auto& x : {last, first, middle}) {
        if (x.empty()) continue;
        if (!out.empty()) out += " ";
        out += x;
    }
    return out;
}

std::string fmt_profile(const json& p) {
    json prof = jref(p, "profile");
    json kids = jarr(p, "children");
    json kid = kids.empty() ? json::object() : kids[0];
    std::string name = full_name(prof);
    std::string school = jstr(jref(kid, "school"), "short_name");
    std::vector<std::string> lines;
    lines.push_back(bold(name.empty() ? "Без имени" : name));
    std::vector<std::string> info;
    std::string cls = jstr(kid, "class_name");
    if (!cls.empty()) info.push_back("класс " + cls);
    if (!school.empty()) info.push_back(school);
    if (!info.empty()) lines.push_back(util::join(info, " · "));
    std::string birth = jstr(prof, "birth_date");
    if (!birth.empty()) lines.push_back(dim("дата рождения: " + birth));
    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return out;
}

std::string fmt_profile_md(const json& p) {
    json prof = jref(p, "profile");
    json kids = jarr(p, "children");
    json kid = kids.empty() ? json::object() : kids[0];
    std::string name = full_name(prof);
    std::string school = jstr(jref(kid, "school"), "short_name");
    std::vector<std::string> lines;
    lines.push_back("# " + (name.empty() ? std::string("Без имени") : name));
    lines.push_back("");
    std::string cls = jstr(kid, "class_name");
    if (!cls.empty()) lines.push_back("- **Класс:** " + cls);
    if (!school.empty()) lines.push_back("- **Школа:** " + school);
    std::string birth = jstr(prof, "birth_date");
    if (!birth.empty()) lines.push_back("- **Дата рождения:** " + birth);
    std::string out;
    for (std::size_t i = 0; i < lines.size(); ++i) {
        if (i) out += '\n';
        out += lines[i];
    }
    return out;
}

}  // namespace render
