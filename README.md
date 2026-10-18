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
- Minecraft (keyboard-style: camera on the right stick, quick keys for chat/inventory)
- Custom

The Switch-first layout adds independent Plus/Minus and Home/Capture button events, optional Joy-Con-style SL/SR controls, optional rear paddles, a gyroscope aiming toggle, a software touch surface, analog-like trigger drag input, and configurable M1–M4 macro sequences. Existing L3/R3 stick-click events are reused instead of duplicated. Presets can be selected in the mobile customization screen; the component editor still supports visibility, size, anchor, and offset customization.

Settings also cover how the controller itself behaves:

- **Stick sensitivity** scales how far a push has to travel before the PC sees full
  deflection, for both sticks.
- **Whole Screen Camera** makes the whole gamepad screen aim the right stick, so
  games whose camera sits on the right stick (Pokémon Legends Z-A, and most
  third-person games) can be played by dragging anywhere. It sits behind the
  controls, so every button keeps working, and the background takes the accent
  colour while it is on. The background itself is selectable — matching the theme,
  the five dark presets, or a colour picked with red/green/blue sliders.
- **HOLD** is a phone-only button. Tapping it while controls are down keeps
  exactly those controls held — a stick pushed forward, a trigger half-pulled —
  so the finger can be lifted; tapping it again releases everything it captured.
  It is not sent to the PC and does not appear as a controller button there.
- **Custom Key Buttons** (the **Keys** button in the customisation screen) send a
  PC key by name while held, for games that read a keyboard and no controller.
  Any letter, digit, F-key, numpad key, editing/navigation key, mouse button or
  punctuation key works (type it, or pick it from the list); a single character is
  shorthand, so `/` sends Slash and `1` sends Digit1. The Minecraft preset shows
  the four quick keys on a 2x2 block above the right stick. A name the PC does not
  know is ignored there rather than pressing a different key, and the keys are sent
  by the PC's **Keyboard + mouse** backend: the virtual-pad backend has no keyboard
  to type into.
- **Right stick camera**: the right stick is mouse look by default (a profile
  saved before this defaults to the arrow keys once, then stays where it is put),
  and the PC applies no second dead zone to its pixels, so small pushes turn the
  camera instead of being swallowed.
- **Slide Between Buttons** turns the D-pad and the A/B/X/Y cluster into one
  surface each: the direction or button follows the finger, so a press rolls from
  one into the next without lifting it, and a diagonal is the finger between two.
- **Large Left/Right Stick Area** widens what catches a thumb around each stick
  without changing how far the stick has to be pushed.
- **Keep Screen On** holds the display awake for the whole session.
- The **trigger buttons** are pulled, not positioned: a touch is a full pull and
  sliding down from where the finger landed eases it off, so a tap always
  registers instead of depending on where on the button it landed.

### Host and input limitations

- Gyroscope motion and touch-surface drags currently feed the existing right-stick axes. They do not create a native motion sensor or HID touchpad on the PC.
- Plus/Minus, Home/Capture, side buttons, and paddles have distinct mobile/protocol flags. The PC keyboard/mouse executor maps these flags to default key actions; exposing the new actions in the PC keymap editor for user remapping remains planned. The native virtual-gamepad executor maps them onto standard buttons supported by the target backend, using aliases (for example, Plus/Home to Menu and Minus/Capture to View). A standard Xbox-style virtual gamepad cannot expose every Nintendo or PlayStation-specific system control as a distinct native hardware button.
- On Windows the virtual gamepad can present an Xbox 360 pad, a DualShock 4 pad, or both, chosen in Preferences. Both is the default: a program builds its input-device list from either XInput or HID/DirectInput, and a single pad is missing from one of those lists — an Xbox 360 pad never appears in a HID/DirectInput list (emulators and launchers), and a DualShock 4 is not an XInput device. Presenting one of each means whichever list is open has a controller in it, and the user picks between two devices there as they would with two real pads. A program that merges both APIs itself may see a press twice; the single-device choices are there for that case. Both are created through the ViGEmBus driver, whose installer ships with the PC application and can be run from the app when the driver is missing.
- The PC app has three input backends, chosen in Preferences: the virtual gamepad alone, keyboard/mouse simulation alone, or both at once. The default is both, because a single one is invisible to half the programs on a PC — a pad does nothing on the desktop and in programs that only read keys, while keys and pointer are ignored by anything that reads a controller. A program that reads both may see one press twice; the single modes are there for that case.
- Preferences has an **Input target** picker. Choosing a window makes keyboard and
  mouse injection go only to that program, and only while its window is in front;
  choosing “Any program” follows whichever window has focus. This closes the gap
  where a connected phone could type into a browser or chat window. The virtual
  gamepad is a system device and cannot be locked to a window. With no target
  selected the old behaviour is kept: input follows whichever window is focused.
  The choice is remembered by window title and process rather than by a raw
  window handle, so a recycled handle cannot aim input at another program; if the
  chosen program is not running, input is paused instead of being sent elsewhere.
  The list is built from the top-level windows open on Windows; other platforms
  do not offer the lock. Press **Refresh** after starting or restarting the game.
- B (the back/cancel button) defaults to Backspace, not Escape: Escape drops most
  full-screen games out of full screen. Profiles saved with the old default are
  migrated when they are loaded; a B the user deliberately bound to Escape is
  kept.
- The virtual pad is plugged in as soon as the PC application starts and the device chosen in Preferences is applied immediately, so the controller is registered with the system without the user having to open the server page first. The server page states which device is presenting and what the system itself reports for it (the XInput slot it answers on, or how many controllers the system's DirectInput list shows), because an Xbox 360 pad is XInput-class and therefore never appears in Windows' own "Set up USB game controllers" list, while a DualShock 4 does.
- The mobile editor supports per-control digital action remapping, and the existing component groups can be shown/hidden/repositioned/resized. Arbitrary duplicate instances and a fully dynamic control library remain planned. The PC keymap UI has not been redesigned to expose the new default keyboard mappings.
- Macros are local to the mobile app and support sequential button, trigger, and axis steps with bounded durations. Simultaneous/chord steps and macro inclusion in the controller-layout JSON export are not implemented yet.

See [`plan.md`](plan.md) for the detailed checklist, compatibility notes, and remaining work.

### Device visibility

A program builds its input-device list from one API: XInput, or HID/DirectInput.
An Xbox 360 pad answers only the first and a DualShock 4 only the second, so a
single virtual device is missing from half the lists — which is what "the
emulator/launcher does not show my controller" comes down to. The PC app
therefore presents one of each by default, and the server page states what the
system itself reports for them (the XInput slots and how many pads the DirectInput
list shows). A stored choice of a single device from an earlier version is carried
over to both once; after that, the choice in Preferences is kept as it is set.

### Input safety

The phone sends its whole control state many times a second, so a packet is a
*state*, not a list of events. The keyboard/mouse backend therefore builds the
set of outputs one reading asks for and compares it against what it is already
holding: new outputs are pressed, and anything the reading no longer names is
released. That is what makes a held control one press and one release, and it
cannot leave something stuck — a control that is not named in the current packet
is not held, whatever happened to earlier packets.

Two earlier shapes of this were wrong in ways worth remembering. Re-sending the
press for every packet flooded the PC with key and mouse-button-down events,
which turned real clicks into drags and made the desktop appear frozen. Counting
press *edges* instead looked right and was worse: a held control is named on every
packet, so the count climbed for as long as it was held while only the single
release edge ever brought it back down. The mapped key was then never lifted — a
stuck Shift or Ctrl made typing come out as shortcuts, a stuck mouse button turned
every click into a drag (both of which look like the keyboard and mouse have
stopped working) — and, because the count never returned to zero, the next press
of that same control did nothing at all, which is why the triggers (ZL/ZR, mapped
to Shift and Ctrl by default) appeared to work once and then never again.

Anything that goes down is also guaranteed to come up. Every input is released
when the stream stops — the link drops, no packet arrives for 1.5 seconds, the
server is stopped, another backend is chosen, or the app closes — and the virtual
pad is left reporting nothing held with both sticks at rest. A key or mouse
button can therefore not stay pressed after the phone is gone.

## Windows packaging and updates

The Windows build is published as a single installer executable, not as an
archive: the build job deploys the application with `cmake --install` (which runs
`windeployqt`, so the Qt DLLs sit beside the executable) and packs that folder
with Inno Setup into `VirtualGamePad-Extended-Setup-<version>.exe`. Pushing a
`v*` tag publishes that installer as a GitHub Release, and the release asset is
a direct download — no archive to extract.

The PC app checks that release for a newer version: silently a few seconds after
it starts, and on demand through **Check for updates** on the home page. When a
newer one exists it asks before downloading it, saves the installer to the
temporary folder, and starts it once the download is complete — the app closes at
that point, because the installer replaces the files it is running from.

## Licensing

The upstream projects are licensed under GPL-3.0. Refer to the respective project license files for applicable terms.
