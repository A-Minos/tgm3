# OGG-to-WAV compatibility loader

This is a new implementation of the lost native audio compatibility component.
It matches the binary sidecar format produced by
[`tools/bgm_ogg/convert_bgm.py`](../../tools/bgm_ogg/convert_bgm.py).
It is not the complete game runtime, a game download, or a ready-to-install APK.

## Build and test

On 64-bit Linux, with GCC, Make, Python 3 and FFmpeg (including libvorbis):

```sh
make
make test
./verify_loader /path/to/missing-track.wav --probe
./verify_loader /path/to/missing-track.wav > reconstructed.wav
```

`verify_loader` first opens an existing WAV read-only. Only `ENOENT` triggers
the OGG fallback. An existing WAV always wins, and other open failures are
preserved. This command is a diagnostic harness, not the Windows API adapter.

The production sources are `ogg_wav.c`, `ogg_wav.h`, `vorbis_decoder.c` and
the pinned `stb_vorbis.c`. Link with `-lm`. No FFmpeg executable is needed by
the runtime; FFmpeg is only used by the converter and tests.

## Native API and integration

`tgm_ogg_wav_fallback("path/name.wav")` tries `path/name.ogg` and
`path/name.ogg.wavmeta`. It accepts a case-insensitive WAV extension but does
not search for differently cased POSIX filenames.

For a Windows compatibility layer, use
`tgm_ogg_wav_from_paths(resolved_ogg_path, resolved_metadata_path)` instead.
Resolve both candidate paths through the layer's existing case-insensitive
path resolver. Do not assume a failed lookup of `NAME.WAV` resolved the case
of a sibling `name.ogg` that exists.

The caller must enforce all three conditions before falling back:

1. The original ordinary open failed with `ENOENT`, not a permission error.
2. The request is read-only and uses `OPEN_EXISTING`; never intercept writes,
   truncation, creation, read/write handles, or non-WAV requests.
3. The returned POSIX descriptor is passed through the runtime's ordinary
   file-handle registration, read, seek, close and error-mapping paths.

Success returns an owned, seekable, `CLOEXEC` descriptor at byte zero. It is an
anonymous memfd sealed against write, resize and further seal changes. Close
it normally. Failure returns `-1` and sets `errno`. No source files are changed.
Linux/Android `memfd_create` and file sealing are required. Unsupported systems
fail rather than writing temporary decoded music to a guessed directory.

This repository deliberately does not contain the unlicensed upstream game's
Win32 implementation or its modified copy. Integrating this module and building
its two C objects is not proof that the entire Android game can be rebuilt.

## Exact sidecar and audio behavior

The sidecar consists of the original WAV with just the `data` payload removed.
The RIFF and data sizes still describe the full original WAV. All other bytes,
including chunk ordering, odd-sized chunk padding, unknown chunks and loop
metadata before or after the data chunk, are retained exactly.

The loader requires one 16-byte PCM `fmt ` chunk and one nonempty `data` chunk,
PCM16, one or two channels, 44,100 Hz, valid block/byte rates, and exact lengths.
The `fmt ` chunk may occur after `data`. It parses physical sidecar offsets
separately from the original WAV's declared lengths.

It validates every Ogg page's checksum, sequence, framing and serial number,
rejects chained streams/trailing bytes, and requires a final EOS granule equal
to the WAV frame count. The Vorbis decoder must independently produce exactly
that many samples with the same channel count and rate. It never pads missing
samples, silently truncates, invents loop points or falls back to a different
format. Vorbis audio is lossy: PCM sample equality with the source WAV is not
expected. Retaining loop metadata is not an audible loop-quality guarantee.

## Bounds and resource use

- PCM payload: at most 64 MiB.
- Sidecar: at most 1 MiB.
- Compressed Ogg input: at most 64 MiB; only regular files are accepted.
- Decoder arena: fixed 8 MiB. Streams that exceed it are rejected.
- Decoding uses a small PCM buffer and writes into a sealed memfd. The complete
  decoded WAV still occupies memory-backed pages, up to PCM + sidecar size.

Disk-size reduction does not imply equivalent RAM savings. The runtime still
reads an uncompressed WAV through the returned descriptor.

## Validation limits

See `VALIDATION.md` for the checks actually run against this revision. Host
decoder checks and Android cross-compilation are not a phone, audio-output,
gameplay, or genuine 16 KiB-kernel validation. No game assets are in these tests.

The upstream decoder version, SHA-256 and license are in `DEPENDENCIES.md`.
