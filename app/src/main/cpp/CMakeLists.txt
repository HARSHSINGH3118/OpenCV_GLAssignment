cmake_minimum_required(VERSION 3.22)
project(native_pipeline)
set(CMAKE_CXX_STANDARD 17)

add_library(native-lib SHARED native-lib.cpp)

# 👇 Path to your extracted OpenCV SDK
set(OpenCV_DIR "C:/Users/harsh/Downloads/opencv-4.12.0-android-sdk/OpenCV-android-sdk/sdk/native/jni")

find_package(OpenCV REQUIRED core imgproc imgcodecs)

target_include_directories(native-lib PRIVATE ${OpenCV_INCLUDE_DIRS})

target_link_libraries(native-lib
        ${OpenCV_LIBS}
        log
        android)
