# Source and licensing scope

This export intentionally separates independently written work from code and
content whose redistribution rights have not been established.

## Included

- Root probe, JNI page model, synthetic tests, PE/ELF inspection tools, Gradle
  files and documentation: written for this project. No game instructions or
  binary fixtures are embedded. Test PE/ELF inputs are synthesized at runtime.
- `tools/bgm_ogg/`: original Python preparation/validation tools and synthetic
  tests. FFmpeg is invoked as a separately installed program; no FFmpeg binary
  or actual audio is included.
- `native/ogg_wav/`: the newly written standalone sidecar/WAV adapter, synthetic
  tests and reproducible host/Android module build scripts. Its pinned
  `stb_vorbis.c` is an exact upstream file with its full MIT/public-domain notice;
  provenance and hashes are in `DEPENDENCIES.md`. This addition does not include
  any TGM3 HLE implementation, generated game code, private kernel32 patch,
  real-audio fixtures, prebuilt binaries or signing material.
- `android-shell/app/src/main/java/org/aminos/tgm3/privateport/`: project-specific
  Java importer, launcher and touch UI. The supported executable hash and size
  are validation metadata, not executable content. Import requires an external
  file and never downloads one.
- `android-shell/app/src/main/java/org/libsdl/app/`: SDL 2.32.4 Java sources,
  distributed under SDL's zlib license retained in
  `android-shell/SDL2-LICENSE.txt`. Local modification is identified in
  `android-shell/NOTICE.md` and in the changed source.

No blanket license has been selected for the new project-specific files.
The repository being public is not a statement that every file is covered by
SDL's license. Choose a project license only after the owner has decided it.

## Excluded

- Original game EXE, data, saves, game screenshots and archives.
- Generated C, object files or shared libraries derived from the game program.
- Hri7566/tgm3 native/HLE/recompiler implementation and modified copies.
- Private graphics/input/memory adapters that incorporate that implementation.
- APKs, binary toolchains, signing material, credentials, private logs and paths.

At review, [Hri7566/tgm3](https://github.com/Hri7566/tgm3) main was
`59bc92d67cf829e5a9191b41712e1ba0c87dad2f`; its complete tree had no license file,
and GitHub's license field was null. Public read access does not establish a
right to redistribute modified runtime code. Game-derived code requires a
separate rights assessment. Removing graphics/audio assets alone does not
remove the translated game program from a playable APK.

Build dependencies are obtained separately from their official sources and
retain their own licenses. This repository does not accept agreements or install
software automatically, publish releases or run external CI on push.
