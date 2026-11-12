#pragma once
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include "cookies.hpp"
#include "jsonutil.hpp"

struct FileRef {
    std::string date;
    std::string subject;
    std::string title;
    std::string url;
    std::string kind;
};

class MesClient {
public:
    MesClient(std::vector<Cookie> cks, std::string source = "");

    static MesClient from_local();

    std::string source;
    std::string token;

    const std::vector<Cookie>& cookies() const { return cks_; }

    json get(const std::string& path,
             const std::vector<std::pair<std::string, std::string>>& params =
                 {});
    long long download(const std::string& url, const std::string& dest);

    json profile();
    std::string student_id();
    json schedule_day(std::optional<std::string> date);
    json schedule_short(const std::vector<std::string>& dates);
    json homeworks(const std::string& frm, const std::string& to);
    json marks(const std::string& frm, const std::string& to);

    json day(std::optional<std::string> date);
    std::vector<json> week(const std::string& start, int days);

    static std::vector<FileRef> files_from_homework(const json& hw);
    static json normalize_day(const json& full, const json& short_payload,
                              const json& hw_payload);
    static std::string teacher_name(const json& t);

private:
    std::vector<Cookie> cks_;
    std::optional<std::string> sid_;
    std::string cookie_header_;
};
