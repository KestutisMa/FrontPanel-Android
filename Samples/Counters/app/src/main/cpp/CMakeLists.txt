cmake_minimum_required(VERSION 3.4.1)

# Copy shared STL file to Android Studio output directory so it is packaged in
# the APK.
#
# Note that this is only necessary because libokjFrontPanel.so is not built by
# Android Studio itself but is included as "external" library using Gradle
# jniLibs.srcDirs. If this changes in the future, i.e. if we actually build
# libokjFrontPanel.so in this project too, STL library would be included in
# the APK automatically and this code could be completely removed.

if("${ANDROID_STL}" STREQUAL "c++_shared")
  message("Configuring STL c++_shared for ${ANDROID_ABI}")
  configure_file(
    "${ANDROID_NDK}/sources/cxx-stl/llvm-libc++/libs/${ANDROID_ABI}/libc++_shared.so"
    "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libc++_shared.so"
    COPYONLY)
else()
   message(FATAL_ERROR "STL configuration ANDROID_STL=${ANDROID_STL} is not supported")
endif()
