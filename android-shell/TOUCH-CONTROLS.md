# Touch frontend v2

This is original frontend code. It contains no game resources, screenshots,
translated program, or native game implementation. The public frontend still
requires separately authorized native integration inputs before it can run the
game. Its existing external ZIP import flow is unchanged.

## Controls and editor

The overlay covers the full rendering viewport instead of reserving a fixed
bottom button row. The game activity and layout editor request the same immersive
window and display-cutout policy, so their control coordinates use the same
viewport. The game activity reapplies this policy after SDL window-style changes. It provides a four-way digital joystick, four mapped action
buttons, Coin, Start, and a Menu button. Service and Test are hidden by default
and can be enabled in settings. The overlay's labels and preview are drawn with
Android Canvas; no game artwork is included.

Open **Touch layout settings** from the launcher. Drag a control in the preview,
then choose **Settings** to change its size or the overlay opacity. Choose P1 or
P2, switch between right-handed and left-handed layouts, or reset the settings.
**Save** persists the layout; **Cancel** asks before discarding edits. Layout
changes take effect the next time the game activity starts.

Positions and sizes are proportional to the viewport, and controls are clamped
to its bounds. The default joystick diameter is 28% of the shorter viewport edge
and its horizontal center is at 8% of the width (mirrored for left-handed use).
A model regression checks that its default footprint stays within the outer 16%
on a 16:9 viewport. Narrower aspect ratios can still overlap the game area; use
the editor rather than assuming the default layout fits every device. The editor uses an atomic app-private settings file separate from
the imported game and saves. Resetting touch settings does not reset game saves.

## Default keyboard mapping

The frontend emits the existing default keyboard bindings. A native integration
with customized key bindings may need corresponding changes.

- P1 joystick: arrow keys. B1/B2/B3/B4: Z/X/C/V. Coin: 5. Start: 1. Service: 6.
- P2 joystick: I/K/J/L for up/down/left/right. B1/B2/B3/B4: Q/W/E/R.
  Coin: 7. Start: 2. Service: 8.
- Test: 9 for either selection.

P1/P2 selects which player's keys the single overlay emits; it does not show
two simultaneous player panels. Only four action buttons have default bindings.
No fifth or sixth active button is claimed.

## Momentary input and Back

The pure-Java input model assigns each pointer its pressed key. Its tests verify
that when two pointers hold the same action, releasing one does not release the
other. End-to-end Android multi-pointer behavior remains unresolved as noted
below. Moving off an action button releases it; the
joystick follows its owning finger and has a center dead zone. Cancel, focus
loss, pause, detachment, and viewport-size changes release held touch keys.

Menu or Android Back asks before exiting. Keep playing dismisses the dialog.
Confirming exit sends SDL's quit event; it does not force-kill the launcher or
delete saves. Normal return depends on the omitted native integration processing
that event. Android 13+ Back uses a callback scoped to the game activity. The
layout editor retains its discard-confirmation behavior separately.

## Validation boundary

**Unresolved runtime checks:** simultaneous two-finger input has not passed, and
the final immersive build's Back retest is incomplete. See the
[versioned results and blockers](../docs/port-status.md#touch-frontend-v2-validation-scope)
before treating this frontend as runtime-validated.

The 56 pure-Java model assertions and Android compile/lint checks passed. They
do not demonstrate Android MotionEvent delivery or gameplay compatibility.

In the final private integrated candidate (`5345ade9…`), the saved P2 layout
reopened and both game surface/overlay used the same 640 by 480 viewport with
no status-bar height. **Simultaneous two-finger direction plus rotation was not
verified:** only the first pointer's action was observed, and the cause remains
unresolved. Final-build Back, Home/resume, P1 gameplay and clean-install checks
were not completed after emulator system-service failures blocked further tests.

The earlier pre-immersive candidate (`caff2871…`) passed the editor interaction
checks, Back/Cancel and Menu/confirmed quit. Those passes are not final-build
runtime passes. See [port status](../docs/port-status.md) for the versioned scope.

The public source intentionally cannot assemble a playable game APK without the
omitted native libraries. Physical phone touch feel, ARM64 execution, Android
13+ predictive Back, real 16 KiB kernels, audible audio and broader controller/
lifecycle combinations remain unverified. Existing test-save retention is not
validation of new records, accounts, all game modes or long-term save behavior.
