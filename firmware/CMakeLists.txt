cmake_minimum_required(VERSION 3.16)
project(t60_portable_core LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Intentional host-only build: no watch target, linker script, DFU, or flashing.
add_library(t60_ui STATIC src/ui.c)
target_include_directories(t60_ui PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

enable_testing()
add_executable(t60_ui_test tests/ui_test.c)
target_link_libraries(t60_ui_test PRIVATE t60_ui)
add_test(NAME t60_ui_state COMMAND t60_ui_test)
