# Third-party source notice

The `org/libsdl/app` Java files originate from SDL 2.32.4:
https://github.com/libsdl-org/SDL/releases/tag/release-2.32.4

SDL uses the zlib license; its complete notice is retained in SDL2-LICENSE.txt.
The original authorship is not claimed by this project.

Local changes: HIDDeviceManager.java adds a scoped receiver lint annotation and registers the USB broadcast receiver with
RECEIVER_NOT_EXPORTED on Android 13/API 33 and later, retaining the older overload
on older systems. The change is marked at its source location. Other SDL Java
files are retained from the official release. The scoped lint configuration
does not request optional microphone/Bluetooth/vibration permissions.

No GL4ES or SDL native implementation/binary is included in this source export.
