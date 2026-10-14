# VirtualGamePad Extended

Combined source tree for the Android mobile app and desktop/PC app.

## Projects

- Android / Mobile: `mobile/` (from https://github.com/kitswas/VirtualGamePad-Mobile)
- Desktop / PC: `pc/` (from https://github.com/kitswas/VirtualGamePad-PC)

Both projects are stored here as actual source files, not Git submodule pointers. Upstream source files, build configuration, assets, and workflow definitions are retained in each project's directory. GitHub only automatically runs workflow files under the repository-root `.github/workflows/`; the root CI workflow builds/tests the Android app and builds the Linux PC app.

## Hybrid controller work

The current mobile layout editor provides starting presets for:

- Nintendo Switch 2
- Nintendo Switch
- Xbox
- PlayStation DualSense-inspired
- Steam Deck-inspired
- Custom

The Switch-first layout adds independent Plus/Minus and Home/Capture button events, optional Joy-Con-style SL/SR controls, optional rear paddles, a gyroscope aiming toggle, a software touch surface, analog-like trigger drag input, and configurable M1–M4 macro sequences. Existing L3/R3 stick-click events are reused instead of duplicated. Presets can be selected in the mobile customization screen; the component editor still supports visibility, size, anchor, and offset customization.

### Host and input limitations

- Gyroscope motion and touch-surface drags currently feed the existing right-stick axes. They do not create a native motion sensor or HID touchpad on the PC.
- Plus/Minus, Home/Capture, side buttons, and paddles have distinct mobile/protocol flags. The PC keyboard/mouse executor maps these flags to default key actions; exposing the new actions in the PC keymap editor for user remapping remains planned. The native virtual-gamepad executor maps them onto standard buttons supported by the target backend, using aliases (for example, Plus/Home to Menu and Minus/Capture to View). A standard Xbox-style virtual gamepad cannot expose every Nintendo or PlayStation-specific system control as a distinct native hardware button.
- The mobile editor supports per-control digital action remapping, and the existing component groups can be shown/hidden/repositioned/resized. Arbitrary duplicate instances and a fully dynamic control library remain planned. The PC keymap UI has not been redesigned to expose the new default keyboard mappings.
- Macros are local to the mobile app and support sequential button, trigger, and axis steps with bounded durations. Simultaneous/chord steps and macro inclusion in the controller-layout JSON export are not implemented yet.

See [`plan.md`](plan.md) for the detailed checklist, compatibility notes, and remaining work.

## Licensing

The upstream projects are licensed under GPL-3.0. Refer to the respective project license files for applicable terms.
