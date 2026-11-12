#include "server.hpp"

#include <arpa/inet.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "client.hpp"
#include "cookies.hpp"
#include "errors.hpp"
#include "jsonutil.hpp"
#include "util.hpp"

namespace {

const char* VERSION = "0.1.0";

std::mutex g_mutex;
std::unique_ptr<MesClient> g_client;

MesClient& client(bool force = false) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_client || force) {
        g_client = std::make_unique<MesClient>(MesClient::from_local());
    }
    return *g_client;
}

template <typename F>
json with_retry(F fn) {
    try {
        return fn(client(false));
    } catch (const MesAuthError&) {
        return fn(client(true));  // one retry with fresh cookies from browser
    }
}

std::optional<std::string> when(const std::optional<std::string>& v) {
    if (!v) return std::nullopt;
    std::string s = util::trim(*v);
    bool digits = !s.empty() &&
                  std::all_of(s.begin(), s.end(), [](unsigned char c) {
                      return c >= '0' && c <= '9';
                  });
    if (digits) s = "+" + s;
    util::DayParse d = util::parse_day(s);
    if (!d.ok)
        throw AppError("не понимаю дату: '" + s +
                       "' (можно today/tmrw/+N/YYYY-MM-DD)");
    return d.iso;
}

std::string iso_or_today(const std::optional<std::string>& iso) {
    return iso ? *iso : util::iso_date(util::today_local());
}

std::pair<std::string, std::string> range(
    const std::map<std::string, std::string>& q, int back = 0, int ahead = -1) {
    util::Date today = util::today_local();
    std::string frm = q.count("from") ? q.at("from") : "";
    std::string to = q.count("to") ? q.at("to") : "";
    if (frm.empty())
        frm = util::iso_date(back > 0 ? util::add_days(today, -(back - 1))
                                      : today);
    if (to.empty())
        to = util::iso_date(ahead >= 0 ? util::add_days(today, ahead) : today);
    if (!util::parse_iso(frm))
        throw AppError("плохая дата from='" + frm + "', нужно YYYY-MM-DD");
    if (!util::parse_iso(to))
        throw AppError("плохая дата to='" + to + "', нужно YYYY-MM-DD");
    return {frm, to};
}

json route(const std::string& path, const std::map<std::string, std::string>& q) {
    static const json endpoints = json::array(
        {"/day?date=today|tmrw|+2|2026-11-10", "/lessons?date=...",
         "/hw?date=...", "/profile", "/homeworks?from=&to=",
         "/marks?from=&to=", "/schedule?date=... (сырой день)",
         "/schedule/short?dates=a,b (сырое)", "/health"});

    if (path == "/") {
        json o = json::object();
        o["service"] = "sch";
        o["version"] = VERSION;
        o["info"] = "локальное МЭШ API — только с этого компьютера";
        o["endpoints"] = endpoints;
        return o;
    }
    if (path == "/health") {
        MesClient& c = client();
        auto exp = token_exp(c.token);
        json o = json::object();
        o["ok"] = true;
        o["source"] = c.source;
        o["token_exp"] = exp ? json(*exp) : json(nullptr);
        o["token_ok"] = exp && *exp > util::now_unix();
        return o;
    }
    if (path == "/profile") {
        return with_retry([](MesClient& c) { return c.profile(); });
    }
    if (path == "/day") {
        auto d = when(q.count("date") ? std::optional<std::string>(q.at("date"))
                                      : std::nullopt);
        return with_retry([&](MesClient& c) { return c.day(d); });
    }
    if (path == "/lessons") {
        auto d = when(q.count("date") ? std::optional<std::string>(q.at("date"))
                                      : std::nullopt);
        return with_retry([&](MesClient& c) { return c.day(d)["lessons"]; });
    }
    if (path == "/hw") {
        auto d = when(q.count("date") ? std::optional<std::string>(q.at("date"))
                                      : std::nullopt);
        return with_retry([&](MesClient& c) { return c.day(d)["homeworks"]; });
    }
    if (path == "/homeworks") {
        auto [frm, to] = range(q, 0, 13);
        return with_retry(
            [&](MesClient& c) { return c.homeworks(frm, to); });
    }
    if (path == "/marks") {
        auto [frm, to] = range(q, 14, -1);
        return with_retry([&](MesClient& c) { return c.marks(frm, to); });
    }
    if (path == "/schedule") {
        auto d = when(q.count("date") ? std::optional<std::string>(q.at("date"))
                                      : std::nullopt);
        return with_retry(
            [&](MesClient& c) { return c.schedule_day(d); });
    }
    if (path == "/schedule/short") {
        std::string dates = q.count("dates") ? q.at("dates") : "today";
        std::vector<std::string> list;
        for (const auto& x : util::split(dates, ',')) {
            util::DayParse d = util::parse_day(x);
            if (!d.ok) throw AppError("не понимаю дату: '" + x + "'");
            list.push_back(iso_or_today(d.iso));
        }
        json payload =
            with_retry([&](MesClient& c) { return c.schedule_short(list); });
        json o = json::object();
        o["payload"] = payload;
        return o;
    }
    throw NotFound("неизвестный путь: " + path);
}

const char* reason(int code) {
    switch (code) {
        case 200:
            return "OK";
        case 400:
            return "Bad Request";
        case 401:
            return "Unauthorized";
        case 404:
            return "Not Found";
        case 405:
            return "Method Not Allowed";
        case 500:
            return "Internal Server Error";
        case 502:
            return "Bad Gateway";
        default:
            return "OK";
    }
}

void send_json(int fd, int code, const json& obj) {
    std::string body = obj.dump(1);
    char head[256];
    std::snprintf(head, sizeof(head),
                  "HTTP/1.1 %d %s\r\n"
                  "Content-Type: application/json; charset=utf-8\r\n"
                  "Content-Length: %zu\r\n"
                  "Connection: close\r\n\r\n",
                  code, reason(code), body.size());
    std::string out = head;
    out += body;
    std::size_t off = 0;
    while (off < out.size()) {
        ssize_t n = ::send(fd, out.data() + off, out.size() - off, MSG_NOSIGNAL);
        if (n <= 0) break;
        off += static_cast<std::size_t>(n);
    }
}

std::map<std::string, std::string> parse_query(const std::string& query) {
    std::map<std::string, std::string> out;
    for (const auto& pair : util::split(query, '&')) {
        if (pair.empty()) continue;
        auto eq = pair.find('=');
        std::string k = eq == std::string::npos ? pair : pair.substr(0, eq);
        std::string v = eq == std::string::npos ? "" : pair.substr(eq + 1);
        out[util::percent_decode(k, true)] = util::percent_decode(v, true);
    }
    return out;
}

void handle_conn(int fd, std::string peer) {
    std::string req;
    char buf[4096];
    while (req.size() < 65536) {
        ssize_t n = ::recv(fd, buf, sizeof(buf), 0);
        if (n <= 0) break;
        req.append(buf, static_cast<std::size_t>(n));
        if (req.find("\r\n\r\n") != std::string::npos) break;
    }

    std::string method = "GET", target = "/", version = "HTTP/1.1";
    auto line_end = req.find("\r\n");
    std::string line = req.substr(0, line_end);
    {
        auto sp1 = line.find(' ');
        auto sp2 = sp1 == std::string::npos ? std::string::npos
                                            : line.find(' ', sp1 + 1);
        if (sp1 != std::string::npos) {
            method = line.substr(0, sp1);
            if (sp2 != std::string::npos) {
                target = line.substr(sp1 + 1, sp2 - sp1 - 1);
                version = line.substr(sp2 + 1);
            } else {
                target = line.substr(sp1 + 1);
            }
        }
    }

    std::string path = target, query;
    auto qpos = target.find('?');
    if (qpos != std::string::npos) {
        path = target.substr(0, qpos);
        query = target.substr(qpos + 1);
    }
    while (path.size() > 1 && path.back() == '/') path.pop_back();
    if (path.empty()) path = "/";

    int code = 200;
    json obj;
    try {
        if (method != "GET") {
            code = 405;
            obj = json::object();
            obj["error"] = "поддерживается только GET";
        } else {
            obj = route(path, parse_query(query));
        }
    } catch (const MesAuthError& e) {
        code = 401;
        obj = json::object();
        obj["error"] = e.what();
        obj["hint"] = "выполни: sch login";
    } catch (const MesApiError& e) {
        code = 502;
        obj = json::object();
        obj["error"] = e.what();
    } catch (const AppError& e) {
        code = 400;
        obj = json::object();
        obj["error"] = e.what();
    } catch (const NotFound& e) {
        code = 404;
        obj = json::object();
        obj["error"] =
            std::string("неизвестный путь или параметр: '") + e.what() + "'";
    } catch (const std::exception& e) {
        code = 500;
        obj = json::object();
        obj["error"] = std::string("ошибка: ") + e.what();
    }

    std::fprintf(stdout, "[sch] %s \"%s %s %s\" %d -\n", peer.c_str(),
                 method.c_str(), target.c_str(), version.c_str(), code);
    std::fflush(stdout);
    send_json(fd, code, obj);
    ::close(fd);
}

}  // namespace

int serve(const std::string& host, int port) {
    struct addrinfo hints {};
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_PASSIVE;
    struct addrinfo* res = nullptr;
    std::string port_s = std::to_string(port);
    if (getaddrinfo(host.c_str(), port_s.c_str(), &hints, &res) != 0) {
        std::fprintf(stderr, "sch: не удалось разрешить %s\n", host.c_str());
        return 1;
    }
    int srv = -1;
    for (auto* p = res; p; p = p->ai_next) {
        srv = ::socket(p->ai_family, p->ai_socktype, p->ai_protocol);
        if (srv < 0) continue;
        int one = 1;
        setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
        if (::bind(srv, p->ai_addr, p->ai_addrlen) == 0) break;
        ::close(srv);
        srv = -1;
    }
    freeaddrinfo(res);
    if (srv < 0) {
        std::fprintf(stderr, "sch: не удалось занять %s:%d: %s\n", host.c_str(),
                     port, std::strerror(errno));
        return 1;
    }
    if (::listen(srv, 64) != 0) {
        std::fprintf(stderr, "sch: listen: %s\n", std::strerror(errno));
        ::close(srv);
        return 1;
    }
    for (;;) {
        struct sockaddr_storage addr {};
        socklen_t alen = sizeof(addr);
        int fd = ::accept(srv, reinterpret_cast<struct sockaddr*>(&addr), &alen);
        if (fd < 0) {
            if (errno == EINTR) continue;
            break;
        }
        char ip[INET6_ADDRSTRLEN] = "?";
        if (addr.ss_family == AF_INET) {
            auto* a = reinterpret_cast<struct sockaddr_in*>(&addr);
            inet_ntop(AF_INET, &a->sin_addr, ip, sizeof(ip));
        } else if (addr.ss_family == AF_INET6) {
            auto* a = reinterpret_cast<struct sockaddr_in6*>(&addr);
            inet_ntop(AF_INET6, &a->sin6_addr, ip, sizeof(ip));
        }
        std::thread(handle_conn, fd, std::string(ip)).detach();
    }
    ::close(srv);
    return 0;
}
