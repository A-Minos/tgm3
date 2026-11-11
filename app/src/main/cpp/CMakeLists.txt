cmake_minimum_required(VERSION 3.22.1)
project(android_port_lab LANGUAGES C)

if(NOT ANDROID)
    message(FATAL_ERROR "This target requires the Android NDK toolchain.")
endif()
if(NOT DEFINED ANDROID_NDK_MAJOR OR ANDROID_NDK_MAJOR LESS 28)
    message(FATAL_ERROR "NDK r28 or newer is required for this 16 KiB page-size probe.")
endif()
get_filename_component(LAB_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
add_library(portlab SHARED
    "${LAB_ROOT}/native/android_probe.c"
    "${LAB_ROOT}/native/guest_pages.c"
    "${LAB_ROOT}/native/guest_pages_selftest.c")
target_include_directories(portlab PRIVATE "${LAB_ROOT}/include")
target_compile_features(portlab PRIVATE c_std_11)
target_compile_options(portlab PRIVATE -Wall -Wextra -Werror)
# Explicit ELF alignment in addition to NDK r28's default. ZIP alignment is AGP's job.
target_link_options(portlab PRIVATE "-Wl,-z,max-page-size=16384")
