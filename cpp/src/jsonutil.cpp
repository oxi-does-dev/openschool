#include "jsonutil.hpp"

#include <string>

std::string json_scalar_to_string(const json& v) {
    if (v.is_null()) return "";
    if (v.is_string()) return v.get<std::string>();
    if (v.is_boolean()) return v.get<bool>() ? "True" : "False";
    if (v.is_number_integer()) return std::to_string(v.get<long long>());
    if (v.is_number_unsigned())
        return std::to_string(v.get<unsigned long long>());
    if (v.is_number_float()) return v.dump();
    return v.dump();
}

std::string jstr(const json& j, const char* key) {
    if (!j.is_object()) return "";
    auto it = j.find(key);
    if (it == j.end()) return "";
    return json_scalar_to_string(*it);
}

long long jint(const json& j, const char* key, long long def) {
    if (!j.is_object()) return def;
    auto it = j.find(key);
    if (it == j.end() || !it->is_number()) return def;
    return it->get<long long>();
}

bool jbool(const json& j, const char* key) {
    if (!j.is_object()) return false;
    auto it = j.find(key);
    if (it == j.end() || !it->is_boolean()) return false;
    return it->get<bool>();
}

const json& jref(const json& j, const char* key) {
    static const json null = nullptr;
    if (!j.is_object()) return null;
    auto it = j.find(key);
    if (it == j.end()) return null;
    return *it;
}

json jarr(const json& j, const char* key) {
    if (!j.is_object()) return json::array();
    auto it = j.find(key);
    if (it == j.end() || !it->is_array()) return json::array();
    return *it;
}
