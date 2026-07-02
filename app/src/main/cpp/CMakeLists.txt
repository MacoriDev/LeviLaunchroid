cmake_minimum_required(VERSION 3.22)
project(levi LANGUAGES C CXX)

set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "Build type" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared libraries" FORCE)

if(ANDROID)
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    add_link_options("-Wl,-z,max-page-size=16384")
endif()

include(FetchContent)

FetchContent_Declare(
    preloader
    GIT_REPOSITORY https://github.com/LiteLDev/preloader-android.git
    GIT_TAG 95a40b1
)

FetchContent_Declare(
    libHttpClient
    GIT_REPOSITORY https://github.com/microsoft/libHttpClient.git
    GIT_TAG f8ae1d9cf931d9d55542865eee395adca5bdbe41
)

FetchContent_MakeAvailable(preloader)
FetchContent_MakeAvailable(libHttpClient)

add_subdirectory(inbuiltmods)
