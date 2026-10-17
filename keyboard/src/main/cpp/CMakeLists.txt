# The suggestion engine: AOSP LatinIME's native dictionary and decoder (latinime/, Apache 2.0),
# built as libjni_latinime as in AOSP. See latinime/README.md for what was changed.
cmake_minimum_required(VERSION 3.22.1)
project(latinime CXX)

file(GLOB_RECURSE ENGINE_SOURCES CONFIGURE_DEPENDS latinime/src/*.cpp)

add_library(jni_latinime SHARED
    latinime/com_android_inputmethod_keyboard_ProximityInfo.cpp
    latinime/com_android_inputmethod_latin_BinaryDictionary.cpp
    latinime/com_android_inputmethod_latin_BinaryDictionaryUtils.cpp
    latinime/com_android_inputmethod_latin_DicTraverseSession.cpp
    latinime/jni_common.cpp
    ${ENGINE_SOURCES}
)

target_include_directories(jni_latinime PRIVATE latinime/src)
# AOSP builds it with -Wall -Wextra -Werror and more; here warnings are left as warnings, since
# a newer compiler than AOSP's shouldn't break the build over code we don't change
target_compile_options(jni_latinime PRIVATE -Wall -Wno-unused-parameter -Wno-unused-function -fvisibility=hidden)
target_link_libraries(jni_latinime log)
# 16 KB pages (Android 15+ devices may use them)
target_link_options(jni_latinime PRIVATE "-Wl,-z,max-page-size=16384")
