cmake_minimum_required(VERSION 3.20)
project(oc2slirp C)

if (NOT DEFINED JAVA_HOME AND DEFINED ENV{JAVA_HOME})
    set(JAVA_HOME "$ENV{JAVA_HOME}")
endif()
find_path(JNI_INCLUDE_DIR jni.h
    HINTS "${JAVA_HOME}/include" "$ENV{JAVA_HOME}/include")
if (WIN32)
    set(JNI_PLATFORM win32)
elseif (APPLE)
    set(JNI_PLATFORM darwin)
else()
    set(JNI_PLATFORM linux)
endif()
find_path(JNI_PLATFORM_INCLUDE_DIR jni_md.h
    HINTS "${JAVA_HOME}/include/${JNI_PLATFORM}" "$ENV{JAVA_HOME}/include/${JNI_PLATFORM}")
if (NOT JNI_INCLUDE_DIR OR NOT JNI_PLATFORM_INCLUDE_DIR)
    message(FATAL_ERROR "JNI headers not found; set JAVA_HOME to a JDK")
endif()

add_library(oc2slirp SHARED oc2_slirp.c oc2_slirp_jni.c)
target_include_directories(oc2slirp PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")
target_include_directories(oc2slirp PRIVATE "${JNI_INCLUDE_DIR}" "${JNI_PLATFORM_INCLUDE_DIR}")

find_path(SLIRP_INCLUDE_DIR slirp/libslirp.h)
find_library(SLIRP_LIBRARY NAMES slirp libslirp)
if (NOT SLIRP_INCLUDE_DIR OR NOT SLIRP_LIBRARY)
    message(FATAL_ERROR "libslirp headers/library not found; set SLIRP_INCLUDE_DIR and SLIRP_LIBRARY")
endif()

target_include_directories(oc2slirp PRIVATE "${SLIRP_INCLUDE_DIR}")
target_link_libraries(oc2slirp PRIVATE "${SLIRP_LIBRARY}")
target_compile_features(oc2slirp PRIVATE c_std_11)

if (WIN32)
    target_link_libraries(oc2slirp PRIVATE ws2_32)
endif()

add_executable(oc2-slirp-smoke smoke_test.c)
target_link_libraries(oc2-slirp-smoke PRIVATE oc2slirp)
