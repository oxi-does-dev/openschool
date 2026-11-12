#pragma once
#include <cstddef>
#include <optional>
#include <string>
#include <vector>

namespace util {

std::string trim(const std::string& s);
std::string ltrim(const std::string& s);
std::string rtrim(const std::string& s);
std::string lower(const std::string& s);
bool iequals(const std::string& a, const std::string& b);
std::string one_line(const std::string& s);
std::string wrap(const std::string& text, std::size_t width,
                 const std::string& initial_indent = "",
                 const std::string& subsequent_indent = "");
std::string shorten(const std::string& s, std::size_t n = 100);
std::string md_escape(const std::string& s);

std::size_t utf8_len(const std::string& s);
std::size_t utf8_char_bytes(unsigned char lead);
std::string utf8_prefix_chars(const std::string& s, std::size_t chars);
std::string utf8_first_char(const std::string& s);
std::string truncate_utf8(const std::string& s, std::size_t bytes);

std::string percent_decode(const std::string& s, bool plus_as_space = false);
std::string url_encode(const std::string& s);
std::string expand_tilde(const std::string& s);
std::string int_to_str(long long v);
long long now_unix();

bool starts_with(const std::string& s, const std::string& p);
bool ends_with(const std::string& s, const std::string& p);
std::string replace_all(std::string s, const std::string& from,
                        const std::string& to);
std::vector<std::string> split(const std::string& s, char sep);
std::string join(const std::vector<std::string>& v, const std::string& sep);

struct Date {
    int y = 1970;
    int m = 1;
    int d = 1;
};
bool operator==(const Date& a, const Date& b);
std::optional<Date> parse_iso(const std::string& s);
std::string iso_date(const Date& d);
Date today_local();
Date add_days(const Date& d, int n);
int weekday_py(const Date& d);  // Monday = 0 ... Sunday = 6
int iso_week(const Date& d);
int days_between(const Date& a, const Date& b);  // b - a

struct DayParse {
    bool ok = false;
    std::optional<std::string> iso;  // nullopt means "today, server decides"
};
DayParse parse_day(const std::string& token);

}  // namespace util
