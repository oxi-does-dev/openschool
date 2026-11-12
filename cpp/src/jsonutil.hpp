#pragma once
#include <nlohmann/json.hpp>
#include <string>

// ordered_json keeps the key order of the original API responses / our own
// inserts, just like Python dicts.
using json = nlohmann::ordered_json;

std::string jstr(const json& j, const char* key);
long long jint(const json& j, const char* key, long long def = 0);
bool jbool(const json& j, const char* key);
const json& jref(const json& j, const char* key);
json jarr(const json& j, const char* key);
std::string json_scalar_to_string(const json& v);
