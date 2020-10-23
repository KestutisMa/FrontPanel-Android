FrontPanel Android Distribution
===============================

The FrontPanel Android distribution provides a cross-compiled shared library
(libokjFrontPanel.so) and Android Java API (okjFrontPanel.jar) for ARM-based
Android devices.

For further information, please refer to the online documentation at:
https://docs.opalkelly.com/display/FPSDK/FrontPanel+Android

Counters Sample
---------------

A sample Android application built around the Counters sample project is
provided with this distribution. This application is designed to mimic the
design and functionality of the Counters XFP file.

### Building the Project

Android Studio is required for building under all platforms. (Tested with
Android Studio 3.0.1 and Android Studio 3.3).

Before building the project, you need to copy the FrontPanel libraries to the
project directory:

1. Copy `okjFrontPanel.jar` to `app/libs` subdirectory of this project.
2. Copy `libokjFrontPanel.so` to `app/src/main/jniLibs/armeabi-v7a`.
3. Copy `libokFrontPanel.so` to `app/src/main/jniLibs/armeabi-v7a`.

After doing this, open the project in Android Studio and build as usual.

### Standard Library Notes

`libokjFrontPanel.so` is compiled with `c++_shared` STL library variant, so
if the main Java project uses any other C++ shared libraries, they **must**
use it too, see
https://developer.android.com/ndk/guides/cpp-support#shared_runtimes

### Bitfile Location

Bitfiles can be placed in the `app\src\main\assets` subdirectory of this project
with the `counters-<product_name>.bit` filename pattern.

