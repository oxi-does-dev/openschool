#pragma once
#include <string>
#include <vector>

#include "jsonutil.hpp"

namespace render {

void use_color(bool on);
std::string bold(const std::string& s);
std::string dim(const std::string& s);
std::string green(const std::string& s);
std::string yellow(const std::string& s);
std::string red(const std::string& s);
std::string cyan(const std::string& s);

std::string human_date(const std::string& iso);
std::string day_suffix(const std::string& iso);

std::string fmt_day(const json& day, const std::vector<std::string>& show);
std::string fmt_day_md(const json& day, const std::vector<std::string>& show,
                       int level = 1);
std::string fmt_week(const std::vector<json>& days);
std::string fmt_week_md(const std::vector<json>& days);
std::string fmt_marks(const std::vector<json>& items, const std::string& frm,
                      const std::string& to);
std::string fmt_marks_md(const std::vector<json>& items,
                         const std::string& frm, const std::string& to);
std::string fmt_profile(const json& p);
std::string fmt_profile_md(const json& p);

}  // namespace render
