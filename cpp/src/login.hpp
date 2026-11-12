#pragma once
#include <optional>
#include <vector>

#include "cookies.hpp"

std::optional<std::vector<Cookie>> refresh_via_headless(
    std::optional<long long> older_than = std::nullopt, int timeout = 75);
