cmake_minimum_required(VERSION 3.18.1)
project(native-lib)

add_library(native-lib SHARED
    native-lib.cpp
)

find_library(GLES_LIB GLESv3)
find_library(EGL_LIB EGL)
find_library(LOG_LIB log)

target_link_libraries(native-lib
    ${GLES_LIB}
    ${EGL_LIB}
    ${LOG_LIB}
)

target_compile_options(native-lib PRIVATE
    -Wall
    -Wextra
    -std=c++17
)
