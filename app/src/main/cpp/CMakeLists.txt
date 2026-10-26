cmake_minimum_required(VERSION 3.22.1)
project(faphousepro LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if (CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -fvisibility=hidden -fvisibility-inlines-hidden -fdata-sections -ffunction-sections -fstack-protector-strong -D_FORTIFY_SOURCE=2")
    set(CMAKE_C_FLAGS_RELEASE   "-O3 -DNDEBUG -fvisibility=hidden -fdata-sections -ffunction-sections -fstack-protector-strong -D_FORTIFY_SOURCE=2")
    set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--strip-all")
else ()
    set(CMAKE_CXX_FLAGS_DEBUG "-O0 -g -DDEBUG -fno-omit-frame-pointer")
endif ()

add_library(native-lib SHARED native-lib.cpp)

target_compile_options(native-lib PRIVATE
    -Wall -Wextra -Wno-unused-parameter -Wno-deprecated-declarations
    -fno-exceptions -fno-rtti
)

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(native-lib ${log-lib} ${android-lib} ${jnigraphics-lib})