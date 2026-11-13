# Experimental BGM-to-OGG preparation tool

This original Python tool copies a local `game.exe` and `data/` tree into a new
directory, converting `data/bgm/*.wav` to Vorbis plus metadata sidecars. It does
not contain, download, execute, or upload a game. Use only inputs you are
authorized to copy and convert. No FFmpeg binary or codec implementation is
included.

The output requires a loader that understands the `.ogg.wavmeta` sidecar
convention. A rewritten standalone native module is now supplied in
[`native/ogg_wav/`](../../native/ogg_wav/README.md). Its integration into the
complete game runtime is separate. This tool does not create an APK, recreate
the private playable build, or make the output compatible with an ordinary
WAV-only loader.

## Requirements and use

- Python 3.8 or later.
- An independently installed `ffmpeg` on `PATH`, with the `libvorbis` encoder.
  Install official tools and comply with their licenses separately.
- A source directory containing `game.exe` and `data/bgm/`.

```sh
python3 convert_bgm.py /path/to/authorized/source /path/to/new-output
```

The output directory must not exist. Source and output must not contain each
other. Paths containing `..`, symlink components, source symlinks, or special
files are rejected. Do not modify the source tree concurrently while conversion
is running. The tool does not validate the game executable or run it.

Only root `game.exe` and the complete `data/` tree are copied. Other root files
are omitted. Non-BGM files inside `data/` are copied unchanged. The source tree
is not modified, and existing output directories are never overwritten.

## WAV and sidecar scope

The validator accepts a narrow input shape: RIFF PCM16, 44,100 Hz, one or two
channels, one 16-byte `fmt` chunk, and one nonempty `data` chunk. PCM data is
limited to 64 MiB per track; the non-PCM bytes retained in the sidecar are limited
to 1 MiB. Format compatibility alone does not guarantee successful conversion.

Each BGM WAV is replaced in the output with a same-named `.ogg` and
`.ogg.wavmeta`. Encoding uses Vorbis quality 4. The sidecar preserves all original
WAV bytes except the PCM payload, in their original order, including any loop or
cue metadata. Its original length fields remain unchanged, so the sidecar is
**not a playable WAV** and must not simply be renamed to `.wav`.

The tool checks the Vorbis identification header and counts the actual PCM16
bytes produced by an FFmpeg decode. A frame-count mismatch is rejected rather
than silently accepted. Vorbis remains lossy: equal frame counts and preserved
metadata do not prove equal audio, seamless loops, or compatibility with a
specific runtime.

## Reports and failures

Successful conversion writes `conversion_report.json` with `status: complete`,
relative filenames, sizes, sidecar SHA-256 hashes, and per-track frame checks.
If conversion fails after output creation, the tool returns a nonzero exit code
and retains the partial directory with `status: incomplete`. Treat it as failed,
not as a finished game directory. Preflight failures can occur before any output
or report exists. Use a different new directory if you retry.

## Tests and known limitation

```sh
python3 test_convert_bgm.py
```

These 16 synthetic tests cover input rejection, path/symlink handling, format
validation, and size limits. They do not themselves exercise successful FFmpeg
encoding.

Additional synthetic checks with FFmpeg 7.1.5/libvorbis found:

- Two-second mono and stereo sine waves, and a three-second stereo sine wave,
  completed with matching decoded frame counts and exact non-PCM sidecars.
- One-second mono and stereo sine waves decoded 128 frames shorter and were
  correctly rejected as incomplete. The original inputs remained unchanged.

Earlier private validation matched decoded frame counts for 17 actual BGM
tracks. Those inputs and converted outputs are not included, and that result
only covers those particular tracks.

This is an experimental strict-validation tool, not a guarantee that every WAV
with the listed format will convert successfully. Short clips, different input
lengths, and other FFmpeg/libvorbis versions may behave differently. The tool
does not relax the frame check or add padding to force a match. Validate playback, looping,
audio quality, and performance with your authorized target loader/device. No
game-derived test fixtures or converted game resources are included.

No project-wide license has been selected; see the [repository source-scope
document](../../docs/source-scope.md). The separate dependencies retain their own licenses.
