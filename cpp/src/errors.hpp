#pragma once
#include <stdexcept>
#include <string>

struct MesAuthError : std::runtime_error {
    using std::runtime_error::runtime_error;
};

struct MesApiError : std::runtime_error {
    using std::runtime_error::runtime_error;
};

struct AppError : std::runtime_error {
    using std::runtime_error::runtime_error;
};

struct NotFound : std::runtime_error {
    using std::runtime_error::runtime_error;
};
