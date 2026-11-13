# Port status and reproducibility boundary

The playable private experiment uses static x86-to-C translation compiled into
ARM64/x86_64 code, a Win32/JVS high-level compatibility layer, SDL 2.32.4 and a
GL4ES GLES2 graphics bridge. The game EXE's data and external assets are loaded
at runtime; this is not on-device dynamic recompilation or a full Windows VM.

## Verified privately, not reproducible from this export alone

These bullets describe the earlier pre-v2 baseline, not the final touch-v2
candidate documented below.

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

## Touch frontend v2 validation scope

These are private integrated-build observations, not a playable build reproduced
from this source-only repository. The tests used an API 30 x86_64 TCG/SwiftShader
emulator, not a physical phone.

### Before the shared immersive-window correction (APK `caff2871…`)

- Editor dragging, size/opacity changes, handed presets and reset were exercised.
- Back opened the exit dialog; Cancel retained the game process.
- Menu confirmation sent SDL_QUIT/window closed, ended the game process, and
  retained the launcher process.

### Final shared-window candidate (APK `5345ade9…`)

- Upgrade preserved the hashes of 2,004 existing test save files; all 226 device
  resource hashes matched. These files and resources are not published here.
- The saved P2 editor layout reopened successfully.
- The game SDLSurface and touch overlay both measured 640 by 480 pixels, with a
  zero-height status bar in this emulator configuration.
- Injected P2 left/right presses and releases reached Java and native input.
  ACTION_CANCEL and Home/focus loss released the held direction; return to
  gameplay after Home was not verified. Native launch/render is not evidence of
  a started P2 game or successful rotation.
- Simultaneous direction plus rotation was **not verified**. The attempted
  multi-pointer test only observed the first pointer's action. The cause remains
  unresolved; it was not established as an application or injection-helper bug.
- Final-build Back retest, Home/resume, P1 gameplay, a 16:9 viewport screenshot
  and clean-install checks were not completed. Do not carry forward the preceding build's passes as final-
  candidate runtime passes.

The emulator developed system_server/Scudo and network-stack failures. After
one cold restart, Android system services still failed while the game was not
running, blocking the remaining checks. This explains the test interruption;
it does not establish the cause of the multi-pointer result.

Save checks covered existing test-save retention through the observed update,
resource-rebuild and relaunch paths, plus the earlier normal-exit checks. They
are not evidence that new records, accounts, every game mode or long-term save
behavior work. Emulator layout checks do not establish comfortable controls or
responsiveness on a physical phone. Android 13+ predictive Back, real ARM64
execution, audible audio, real 16 KiB kernels and broader lifecycle/controller
behavior remain unverified.

## Rewritten OGG loader (November 13)

The lost audio compatibility module has been rewritten as an independently
buildable original component under `native/ogg_wav/`. Its decoder dependency,
host tests and Android module build recipe are now included in this repository.
See [its validation record](../native/ogg_wav/VALIDATION.md) for exact scope.

Fifty synthetic tests passed normally and under ASan/UBSan, including bounded
mutations, original-metadata reconstruction, strict frame counts and descriptor
lifetime checks. LSan was unavailable under ptrace, so this is not a heap-leak
pass. A private 17-track sample also preserved exact non-PCM bytes and frame
counts. NDK r28/API 26 compiled both ARM64 and x86-64 modules, and checked ELF
16 KiB alignment. These checks did not run Android or the game.

This does **not** recover the missing complete native game build or convert the
November 12 UI preview into an OGG-integrated APK. Original graphics/input/
memory/entry-point adaptations remain outside the public source. No new game
APK or original-signature upgrade is claimed by this source update.
