#pragma once
#include <optional>
#include <string>
#include <vector>

struct Cookie {
    std::string host;
    std::string name;
    std::string value;
    std::string path = "/";
    bool secure = true;
    std::optional<double> expires;
};

struct Profile {
    std::string app;
    std::string dir;
    double mtime = 0;
};

struct LoadedCookies {
    std::vector<Cookie> cookies;
    std::string source;
    std::string profile;
    std::optional<long long> exp;
    bool has_token = false;
    std::vector<std::string> errors;
};

std::vector<Profile> find_profiles();
std::vector<Cookie> extract_cookies(
    const std::string& profile_dir,
    const std::vector<std::string>& hosts_suffix = {"mos.ru"},
    const std::optional<std::string>& key = std::nullopt);

std::string get_encryption_key();
std::optional<long long> token_exp(const std::string& token);
std::optional<std::string> find_token(const std::vector<Cookie>& cookies);
bool token_usable(const std::string& token);

std::vector<Cookie> load_cache();
void save_cache(const std::vector<Cookie>& cookies);
LoadedCookies load_best();

void copy_profile_for_headless(const std::string& profile_dir,
                               const std::string& dest);
