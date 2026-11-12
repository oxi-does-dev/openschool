#include "login.hpp"

#include <arpa/inet.h>
#include <curl/curl.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <string>
#include <vector>

#include "errors.hpp"
#include "util.hpp"

namespace fs = std::filesystem;

static const char* WORK_DIR = "/tmp/opencode/sch-login";

static std::string chrome_bin() {
    for (const char* c :
         {"/usr/lib/chromium/chromium", "/usr/bin/chromium",
          "/usr/bin/chromium-browser", "/usr/bin/google-chrome",
          "/usr/bin/google-chrome-stable"}) {
        if (fs::exists(c)) return c;
    }
    throw AppError("не нашёл Chromium/Chrome");
}

static int free_port(int start = 9330) {
    for (int port = start; port < start + 80; ++port) {
        int s = ::socket(AF_INET, SOCK_STREAM, 0);
        if (s < 0) continue;
        struct sockaddr_in a {};
        a.sin_family = AF_INET;
        a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        a.sin_port = htons(static_cast<unsigned short>(port));
        int rc = ::bind(s, reinterpret_cast<struct sockaddr*>(&a), sizeof(a));
        ::close(s);
        if (rc == 0) return port;
    }
    throw AppError("нет свободного порта");
}

static size_t write_string_cb(char* ptr, size_t size, size_t nmemb,
                              void* userdata) {
    auto* s = static_cast<std::string*>(userdata);
    s->append(ptr, size * nmemb);
    return size * nmemb;
}

static bool http_ok(const std::string& url, int timeout_sec) {
    CURL* h = curl_easy_init();
    if (!h) return false;
    std::string body;
    curl_easy_setopt(h, CURLOPT_URL, url.c_str());
    curl_easy_setopt(h, CURLOPT_TIMEOUT, static_cast<long>(timeout_sec));
    curl_easy_setopt(h, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(h, CURLOPT_WRITEFUNCTION, write_string_cb);
    curl_easy_setopt(h, CURLOPT_WRITEDATA, &body);
    CURLcode res = curl_easy_perform(h);
    long code = 0;
    curl_easy_getinfo(h, CURLINFO_RESPONSE_CODE, &code);
    curl_easy_cleanup(h);
    return res == CURLE_OK && code == 200;
}

std::optional<std::vector<Cookie>> refresh_via_headless(
    std::optional<long long> older_than, int timeout) {
    std::string prof;
    for (const auto& p : find_profiles()) {
        std::vector<Cookie> cks;
        try {
            cks = extract_cookies(p.dir);
        } catch (...) {
            continue;
        }
        if (find_token(cks)) {
            prof = p.dir;
            break;
        }
    }
    if (prof.empty()) {
        for (const auto& p : find_profiles()) {
            std::vector<Cookie> cks;
            try {
                cks = extract_cookies(p.dir);
            } catch (...) {
                continue;
            }
            if (!cks.empty()) {
                prof = p.dir;
                break;
            }
        }
    }
    if (prof.empty()) return std::nullopt;

    std::error_code ec;
    fs::remove_all(WORK_DIR, ec);
    fs::create_directories(WORK_DIR, ec);
    try {
        copy_profile_for_headless(prof, WORK_DIR);
    } catch (const std::exception&) {
        return std::nullopt;
    }

    int port = free_port();
    std::string log_path = std::string(WORK_DIR) + "/chrome.log";
    std::string chrome = chrome_bin();
    std::string user_dir = std::string("--user-data-dir=") + WORK_DIR;
    std::string debug_port =
        std::string("--remote-debugging-port=") + std::to_string(port);

    pid_t pid = fork();
    if (pid < 0) return std::nullopt;
    if (pid == 0) {
        int fd = ::open(log_path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (fd >= 0) {
            dup2(fd, STDOUT_FILENO);
            dup2(fd, STDERR_FILENO);
            if (fd > 2) ::close(fd);
        }
        const char* argv[] = {chrome.c_str(),
                              "--headless=new",
                              user_dir.c_str(),
                              "--disable-gpu",
                              "--disable-software-rasterizer",
                              "--no-first-run",
                              "--remote-allow-origins=*",
                              debug_port.c_str(),
                              "https://school.mos.ru/diary/",
                              nullptr};
        execv(chrome.c_str(), const_cast<char* const*>(argv));
        _exit(127);
    }

    auto stop = [&]() {
        kill(pid, SIGTERM);
        int status = 0;
        for (int i = 0; i < 50; ++i) {
            pid_t r = waitpid(pid, &status, WNOHANG);
            if (r == pid) return;
            usleep(100000);
        }
        kill(pid, SIGKILL);
        waitpid(pid, &status, 0);
    };

    std::string version_url =
        "http://127.0.0.1:" + std::to_string(port) + "/json/version";
    bool up = false;
    for (int i = 0; i < 80; ++i) {  // ~40 s
        if (http_ok(version_url, 2)) {
            up = true;
            break;
        }
        usleep(500000);
    }
    if (!up) {
        stop();
        fs::remove_all(WORK_DIR, ec);
        return std::nullopt;
    }

    long long old = older_than ? *older_than : (util::now_unix() + 120);
    std::optional<std::vector<Cookie>> best;
    for (int i = 0; i < timeout / 3; ++i) {
        sleep(3);
        std::vector<Cookie> cks;
        try {
            cks = extract_cookies(WORK_DIR);
        } catch (...) {
            continue;
        }
        auto tok = find_token(cks);
        if (!tok) continue;
        auto exp = token_exp(*tok);
        if (tok && exp && *exp > old + 60) {
            stop();
            fs::remove_all(WORK_DIR, ec);
            return cks;
        }
        best = cks;
    }
    stop();
    fs::remove_all(WORK_DIR, ec);
    return best;
}
