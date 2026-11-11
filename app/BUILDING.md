# Build and validate the resource-free probe

Use JDK 17, Gradle 8.11.1, AGP 8.9.2, Android SDK 35, Build Tools 35.0.0,
NDK 28.0.13004108 and CMake 3.22.1. Configure `ANDROID_HOME`, or set `sdk.dir`
in an untracked `local.properties`. Install official tools and accept their
required terms yourself. No wrapper or toolchain binaries are bundled.

From the repository root:

```sh
bash run_host_checks.sh
gradle --no-daemon :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /your/output/port-lab-arm64.apk
gradle --no-daemon :app:assembleDebug -PprobeAbi=x86_64
cp app/build/outputs/apk/debug/app-debug.apk /your/output/port-lab-x86_64.apk
```

Both ABI configurations overwrite the same Gradle output path. These are
diagnostic apps, not the playable game. Debug signing is generated locally.

Check each native library and the packaged APK independently:

```sh
unzip -j /your/output/port-lab-arm64.apk 'lib/arm64-v8a/*.so' -d /your/output/elf
python3 tools/check_elf_alignment.py /your/output/elf/libportlab.so
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -v -c -P 16 4 /your/output/port-lab-arm64.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose /your/output/port-lab-arm64.apk
```

On the target Android 8+ device, confirm the title `Port Lab: no game included`,
the green triangle and `Native smoke: PASS`. Record the actual kernel page size
and the 4 GiB reservation result. Rotate, background/resume and relaunch.
Repeat on real 4 KiB and 16 KiB ARM64 devices. An x86_64 emulator does not validate
ARM execution or real 16 KiB support. The static triangle is not a benchmark.

There are no Internet/storage permissions or game inputs. NDK r28's default
ELF alignment and the explicit linker setting do not replace artifact checks.
See [Android page sizes](https://developer.android.com/guide/practices/page-sizes).
