# Port status and reproducibility boundary

The playable private experiment uses static x86-to-C translation compiled into
ARM64/x86_64 code, a Win32/JVS high-level compatibility layer, SDL 2.32.4 and a
GL4ES GLES2 graphics bridge. The game EXE's data and external assets are loaded
at runtime; this is not on-device dynamic recompilation or a full Windows VM.

## Verified privately, not reproducible from this export alone

- Host desktop OpenGL and GLES2/GL4ES paths reached gameplay and frame 3000.
- The same final Android build ran scripted gameplay to level 3 through 3000
  frames on an API 30 x86_64 TCG emulator with SwiftShader and dummy audio.
- Normal game exit returned to the surviving launcher process.
- Actual Android touchscreen Coin and Start entered player selection when
  pressed after the visible title readiness prompt. Earlier ignored Start
  timing also reproduced on the host; no key remapping was needed.
- Touch Z reached the native input and JVS switch during gameplay, with piece
  rotation observed. Menu advancement before that test was automatic.
- Both ABI libraries compiled; final APK signing, ELF LOAD alignment and ZIP
  alignment were checked. Actual ZIP importer and repeated-import/save tests
  passed privately. Emulator resources were preloaded, so the system picker
  was not validated end-to-end.

Physical ARM64 execution, audible audio, phone performance, real 16 KiB kernels,
all touch/controller combinations, long gameplay and lifecycle/context-loss
recovery remain unverified. Dummy audio is not an audio pass. Software-emulator
frame rates are not phone benchmarks. Selected framebuffer screenshots and
frame counts are not every-frame complete-state equivalence tests.

## What this repository reproduces

The root Port Lab app is complete for its narrow diagnostic purpose: a synthetic
GLES2 triangle and an independently written page-grouping self-test. It does not
include the real game runtime or the private adapted guest memory implementation.

The `android-shell` sources allow the front-end Java to be compiled and reviewed.
Its game startup requires ABI-matched SDL and game native libraries which are
not supplied. Therefore this repository cannot recreate the private playable
APK, even if a reader provides game assets. It deliberately does not replace
the omitted native libraries with misleading stubs.

Before a full reproducible game release, resolve rights for the upstream-derived
runtime and game-derived output, then publish an authorized build path and
complete physical-device validation. No such release is claimed here.
