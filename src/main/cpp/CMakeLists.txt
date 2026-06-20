cmake_minimum_required(VERSION 3.10.2)

project("perfoverlay")
set(CMAKE_CXX_STANDARD 11)

add_definitions(-D_USE_MATH_DEFINES)

add_library(perfoverlay SHARED
    perf_overlay.cpp
)

target_link_libraries(perfoverlay
    log
)
