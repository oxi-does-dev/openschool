#pragma once
#include <string>
#include <vector>

#include "client.hpp"
#include "jsonutil.hpp"
#include "util.hpp"

struct DownloadResult {
    FileRef item;
    std::string path;
    std::string status;  // downloaded | exists | would | error
    long long size = 0;
    std::string error;
};

std::string download_root(const std::string& override_dir = "");
std::string week_folder(const util::Date& day);
std::string safe_name(const std::string& text, std::size_t limit = 120);
std::string build_name(const FileRef& item);
std::string human_size(long long n);

std::vector<FileRef> files_from_homeworks(const json& homeworks);
std::vector<FileRef> files_from_days(const std::vector<json>& days);

std::vector<DownloadResult> sync_items(MesClient& client,
                                       const std::vector<FileRef>& items,
                                       const std::string& root,
                                       bool force = false,
                                       bool dry_run = false);
std::vector<DownloadResult> auto_sync(MesClient& client,
                                      const std::vector<json>& days);

std::string format_text(const std::vector<DownloadResult>& results,
                        const std::string& root);
std::string format_md(const std::vector<DownloadResult>& results,
                      const std::string& root);
json results_json(const std::vector<DownloadResult>& results,
                  const std::string& root);
