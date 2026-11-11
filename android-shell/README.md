# Android front-end source only

These are the Java importer, launcher and touch controls used in the private
experiment, plus licensed SDL 2.32.4 Java glue. The native game runtime is omitted.
This directory is an independent Gradle project, separate from the root Port Lab.

The v2 touch frontend adds a configurable translucent overlay, a layout editor,
pointer-owned momentary input and a Back/quit confirmation. See
[Touch controls](TOUCH-CONTROLS.md) for mappings, editing and validation limits.
The external ZIP importer and missing-native-library assembly guard are retained.
Final integrated-build multi-pointer input remains unresolved; several final
runtime checks were blocked by emulator failures. See the validation section in
the touch-control document before treating compile/model passes as runtime passes.

## Compile checks

Use JDK 17, Gradle 8.11.1, AGP 8.9.2 and SDK 35:

```sh
cd android-shell
gradle --no-daemon :app:compileDebugJavaWithJavac :app:lintDebug
```

The assembly tasks explicitly fail if the required native libraries are absent.
This is intentional: Java compile/lint success does not produce a playable app.
Required integration inputs would be `libSDL2.so` and `libtgm3recomp.so` for both
`arm64-v8a` and `x86_64` under `app/src/main/jniLibs/<abi>/`. They are ignored by
Git and are not provided by this repository. No download or generation path for
game-derived libraries is included.

The importer consumes a compatible local ZIP, verifies an exact executable hash,
extracts only the required EXE/data and preserves app saves across reimport.
It rejects unsafe paths, symlinks, duplicate entries, excessive sizes/counts and
unsupported ZIP formats. Imported data is not a replacement for the omitted
compiled runtime. Uninstalling/clearing app data removes saves.

## Resource-free touch model tests

With JDK 17, run the original layout/input tests without Android or game files:

```sh
bash run_touch_checks.sh
```

The checks cover settings parsing and round-trip, bounds, hit targets, handedness,
four-way joystick behavior, multiple pointers sharing a key, and release/cancel.
They do not execute Android MotionEvent dispatch, Back callbacks or native input.

## Optional local importer test

On a JDK, with your own authorized compatible archive:

```sh
mkdir -p build/importer-tests
javac -d build/importer-tests app/src/main/java/org/aminos/tgm3/privateport/GameImporter.java tests/ImporterTest.java
java -cp build/importer-tests org.aminos.tgm3.privateport.ImporterTest /path/to/your/authorized.zip
```

The test checks a real supplied ZIP, reimport/save retention, persisted markers,
save symlinks and generated unsafe path cases. Additional malformed ZIP paths
may be passed as arguments. No archive or binary fixture is bundled.

See [NOTICE](NOTICE.md), [SDL license](SDL2-LICENSE.txt) and
[repository source scope](../docs/source-scope.md). Original package names are
retained for source correspondence; this is not an official Android game release.
