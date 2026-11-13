# OGG loader validation

Validation date: 2026-11-13. Host: Linux x86-64, GCC 14.2, FFmpeg 7.1.5.
These checks validate the standalone compatibility loader, not the whole game.

## Reproduce the public tests

Install a C11 compiler, make, Python 3 and FFmpeg with the libvorbis encoder.
No game files, network downloads or credentials are needed for these tests.

```sh
make test
sh run_sanitizers.sh
```

Run both commands in this directory. The sanitizer script builds separately in
`.test-build/sanitize`; set `BUILD_DIR` to select another build directory.

Results on the reviewed source:

- **50 Python unittest cases passed** with the normal host build.
- **The same 50 cases passed under AddressSanitizer and UndefinedBehaviorSanitizer.**
- The suite includes **120 deterministic bounded metadata/container mutations**
  and **48 deterministic OGG payload mutations with repaired page CRCs**. Mutated
  inputs may be rejected or decode successfully, but must not crash or trigger
  sanitizers. This is a small regression exercise, not exhaustive fuzz coverage.
- The fd test opens and closes **200 valid streams and 200 malformed streams in
  one process**, with no file-descriptor count growth. It exercises read/seek/
  close, invalid arguments and the explicit resolved-path API.

Generated fixtures cover mono/stereo PCM16, exact decoded length, `fmt ` after
`data`, odd-sized unknown chunks before/after PCM, and a nonzero `smpl` loop.
All non-PCM bytes must be retained exactly. Other cases cover existing-WAV
precedence, Unicode paths, uppercase WAV extensions, independently resolved
OGG/metadata paths, missing/special files, format/count/cap violations, damaged
CRC, page sequencing, truncated streams, trailing garbage and chained streams.
Successful fallback fds must be seekable, CLOEXEC and sealed against writes and
size changes. These checks do not change the game's own file-handle semantics.

LeakSanitizer could not run in the validation environment: it reports that it
cannot operate under ptrace. The script therefore defaults to
`ASAN_OPTIONS=detect_leaks=0:halt_on_error=1` and
`UBSAN_OPTIONS=halt_on_error=1`. This is **not a heap-leak pass**. The explicit fd
lifecycle test still runs. On a host where LSan is available, its options may be
overridden to enable leak detection.

## Private compatibility sample

Separately, 17 authorized BGM tracks were converted with the repository's
existing conversion tool and decoded by this loader. All 17 reconstructed WAVs
matched their original total byte length, PCM frame count, sample rate and
channel count. Every byte outside the PCM payload matched the original,
including the six tracks with nonzero sample-loop starts. Source hashes were
unchanged. No game audio or other private input is included in this repository.

Vorbis is lossy: reconstructed PCM is not expected to equal the original WAV.
Retaining loop records is not proof of seamless audible loops. These checks did
not measure sound quality, Android audio output, full gameplay, physical ARM
behavior, performance or a real 16 KiB-page Android kernel. Cross-compiling a
module or checking ELF alignment is not a device runtime pass.

## Android module and private interface checks

`build_android.sh NDK_ROOT OUTPUT_DIRECTORY` was run with official Android NDK
r28 (28.0.13004108), API 26, on a Linux x86-64 host. It built `libtgmogg.so` and
the standalone diagnostic executable for `arm64-v8a` and `x86_64`, linked successfully with `--no-undefined`. Every ELF LOAD segment passed the 16 KiB alignment and
offset-congruence checks. Two separate output-directory builds produced
byte-identical files. These are module builds, not an Android execution test.

The resulting shared libraries were:

- ARM64: 70,856 bytes, SHA-256 `d327ab08ac561aefb58e4166dfe9dd3bbf8007da68853766fd49185f0502a415`.
- x86-64: 75,160 bytes, SHA-256 `d19810af1fa4789ea353a83f5332c64a0040ebc712eac4ae9110a5aec59b498a`.

A separate private host check connected the new module to the actual upstream
Win32 file wrapper. A generated synthetic clip exercised case-insensitive
Windows candidate paths, read/seek/file-type/close, and rejected write/truncate.
Ten additional gate checks used a mocked decoder to isolate OPEN_EXISTING,
read-only access, ENOENT, WAV suffix, existing-file and permission-error cases.
The unlicensed wrapper and its private adaptation are not in this repository;
these integration observations are not reproduced by the public standalone
test suite. The shared module cannot simply replace the older game library.

No complete game APK, Android audio run or original-signature upgrade was
produced from this rewrite. Full game integration still needs the omitted
Android graphics, input, memory and entry-point adapters.

## Reviewed production inputs

- `ogg_wav.c`: SHA-256 `2b58b6d2570da9b0b3b688dab8309a57bd19775846131a15bd265dbd7c887384`
- `ogg_wav.h`: SHA-256 `7d111a33b28d2bdf395e8adae18386e14afad536b93cec9a90901ce51c48f33b`
- `verify_loader.c`: SHA-256 `6b89b23c447cbb921a349e0e8db35de89a64869d25704487db89b15a095ce5dc`
- `stb_vorbis.c`: SHA-256 `4c7cb2ff1f7011e9d67950446b7eb9ca044f2e464d76bfbb0b84dd2e23e65636`

The vendored decoder is the exact upstream file at commit
`f58f558c120e9b32c217290b80bad1a0729fbb2c` (Git blob
`3e5c2504c08f76b9d04e4adfbce80818631e835e`). It has not been modified.
