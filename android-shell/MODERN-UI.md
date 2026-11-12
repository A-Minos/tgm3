# Modern native front end

The launcher, touch-settings panel and game-exit dialog now share a dark palette,
mint accent, rounded surfaces and consistent action sizing. This is Android
native UI, not a WebView; it adds no dependency or native runtime code.

- Launcher sections separate game status, touch customization and resource import.
  Content scrolls on small windows and has a bounded width on large windows.
- The layout editor keeps drag-to-move behavior and puts Cancel/Save in a floating,
  horizontally scrollable toolbar. Settings group appearance, player input and
  handedness presets; percentages remain visible beside sliders.
- Reset now asks for confirmation. Reset and presets remain edits to the unsaved
  preview. Only Save writes the layout; Cancel/Back retain the last saved layout.
- Save failure, discard, reset and game-exit confirmations use the same dialog
  surface. Repeated taps do not stack editor dialogs.
- Player mappings, input release behavior, import validation, game paths and save
  files are unchanged. The launcher still describes the external-ZIP source
  edition. It does not claim this tree contains a bundled playable game.

## Validation boundary

The November 12 UI revision is a **source-only change**, not a newly delivered
playable APK. The previously delivered private v2 APK is unchanged. Its native
libraries, bundled resources and private signing key are not in this repository
or the current validation workspace. The native-library assembly guard remains.

Checks run against this revision:

- JDK 17 / AGP 8.9.2 / SDK 35 Java compile and Android lint: passed with
  0 errors and 62 warnings. Warnings are not a runtime UI pass.
- Resource XML compilation/linking and `git diff --check`: passed.
- Existing touch-layout/input model: 56 assertions passed.
- Root host checks: normal and ASan/UBSan memory tests, deterministic randomized
  operations, failure injection/protection checks and 17 PE/ELF tests passed.
- Independent source review caught an API-29-only slider method and fixed-width
  rotation clipping; both were corrected before final compile/lint.
- No new APK, emulator screenshots, device UI run, audio check or native-game
  regression run was produced. The manual flows below remain unverified.

Before producing a device-tested build, check the following on a stable emulator
or device with authorized integration inputs:

1. Launcher in portrait, landscape, small multiwindow and large font settings;
   all buttons, scroll content and system-bar insets remain reachable.
2. Settings: select each control, drag, change size/opacity, switch P1/P2, toggle
   system keys and use both presets. Save and reopen; settings must persist.
3. Cancel and Back discard edits. Reset → Cancel keeps edits; Reset → Reset then
   Cancel on the canvas keeps the last saved layout. Repeated taps do not stack
   dialogs. Activity recreation must retain the unsaved layout.
4. In game, Back/Menu opens one exit dialog and releases held inputs. Cancel
   returns focus; confirmation sends SDL_QUIT once. Check modern predictive Back
   and older key-based Back separately.
5. Import cancellation, failed import, busy import and completed import preserve
   the previous gating and status behavior. Do not use an empty native build as
   a game validation substitute.
