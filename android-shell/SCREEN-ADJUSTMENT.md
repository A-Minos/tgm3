# Screen adjustment

The game activity can follow the user's screen rotation preference, including
portrait. The SDL surface fits the native window's requested aspect ratio; only
the picture is scaled and moved. Touch controls stay in screen coordinates.

## Use

1. Open the game Menu (or Back), then **Adjust picture**.
2. Use exactly two fingers to zoom and drag the picture. For a one-player
   portrait layout, enlarge and move the wanted half into view.
3. **Save** keeps the current orientation's view. **Cancel** or Back restores
   the saved view. **Reset** previews the full centered picture; it becomes
   persistent only after Save.

Portrait and landscape have independent settings. Rotation, window resize or
backgrounding cancels unfinished adjustments. The game continues running while
editing; do this before starting a timed game. Gameplay touch input is isolated during editing, and held overlay inputs are
released. Physical keyboard/controller dispatch is unchanged.
Zoom is limited to 1–6 times the fitted picture. Pan bounds keep part of the
picture reachable; Reset is always available in the editor toolbar.

The native orientation callback is overridden so it cannot relock landscape.
The activity uses `fullUser`, not a portrait-only orientation: SDL's stock
surface-ready guard would otherwise reject a landscape-shaped game buffer in
a portrait window. SurfaceView scaling/translation avoids allocating enormous
render buffers for high zoom. Android 26 is the minimum supported version, but
compositor behavior still needs device validation.

## Validation boundary

The standalone geometry test checks normalized panning, focal-point zoom, zoom
limits, invalid state and pan bounds. `run_touch_checks.sh` runs it alongside the
existing touch-layout/input checks. These tests do not execute Android gesture
dispatch, SurfaceView composition, SDL rendering or the game itself.

This public repository still omits native game libraries and game resources.
A separately assembled test package can reuse authorized older runtime inputs,
but is not equivalent to the later OGG-bundled build. Use a separate application
ID and signing key for such a test; do not uninstall or clear the existing game
to try it, because that can delete saves.

Device checks still required: portrait/landscape rendering and aspect ratio,
two-finger zoom/pan, third-finger/pointer replacement, Save/reopen, Cancel/Back,
Reset/Cancel, rotation/background mid-edit, menu repetition, game-touch isolation,
and restoration of touch controls. No screenshot or playable-device pass is
claimed by compile, lint, assembly or geometry-test success.

Checks on the November 12 source revision: 56 existing touch assertions plus
12 viewport model checks passed; SDK 35 Java compilation and Android lint passed
with 0 errors / 64 warnings; `git diff --check` passed. A private independent-ID
ZIP-import preview assembled and passed APK signature and 16 KB alignment
checks. No device/emulator execution was available, and the full OGG-bundled
runtime was not recovered for this revision.
