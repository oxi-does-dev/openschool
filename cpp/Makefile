CXX ?= g++
CXXFLAGS ?= -O2 -std=c++20 -Wall -Wextra -Wno-unused-parameter
LDLIBS = -lcurl -lsqlite3 -lcrypto -lpthread

SRC = $(wildcard src/*.cpp)
OBJ = $(SRC:src/%.cpp=build/%.o)

build/sch: $(OBJ)
	$(CXX) $(CXXFLAGS) -o $@ $(OBJ) $(LDLIBS)

build/%.o: src/%.cpp | build
	$(CXX) $(CXXFLAGS) -c $< -o $@

build:
	mkdir -p build

clean:
	rm -rf build

install: build/sch
	install -m 0755 build/sch $(HOME)/.local/bin/sch-cpp

.PHONY: clean install
