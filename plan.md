# VirtualGamePad Extended: Hybrid Controller Expansion Plan

> Status: planning first, implementation follows this checklist. The Switch 2 layout is the first complete preset; additional controller families follow after the shared control model is stable.
>
> Scope: Android/mobile UI and behavior are the user-facing focus. The PC application must receive and interpret any new input data that is necessary for the mobile controls to work, but this project does **not** include a PC controller-layout editor redesign.

## 1. Verified baseline

### Existing mobile controls

The current mobile code already contains these component types in `mobile/app/src/main/java/io/github/kitswas/virtualgamepadmobile/data/ButtonComponent.kt`:

- Left and right analog sticks, including clickable L3/R3 stick presses.
- D-pad.
- A/B/X/Y face-button cluster.
- LT/RT triggers. They currently send either 0.0 or 1.0 while held, not a continuously variable pressure value.
- LB/RB shoulder buttons.
- Select/View and Start/Menu.
- Per-component visibility, anchor position, scale and X/Y offsets.
- A configuration preview plus JSON import/export.
- Settings persistence through Android DataStore.

### Existing wire protocol and PC behavior

The current Colfer schema is `mobile/VGP_Data_Exchange/GamePadReading.colf`. It contains a 32-bit `ButtonsUp` mask, a 32-bit `ButtonsDown` mask, two float triggers, and four float stick axes. The generated C enum in `mobile/VGP_Data_Exchange/C/GameButtons.h` currently defines Menu, View, A/B/X/Y, four D-pad directions, left/right shoulders, and left/right stick clicks.

The PC side already consumes that schema and has keyboard/mouse mappings for ordinary buttons, sticks and threshold-based triggers. Native gamepad injection is implemented separately for Windows and Linux.

### Verified gaps

- Plus/minus, Home, Capture, ZL/ZR as separate inputs, Joy-Con SL/SR, gyro, and a touch surface are not separate controls in the current shared button enum/schema.
- L3/R3 already exist; do not duplicate their underlying input events. Improve their layout/presentation only where a preset needs it.
- The current customization screen edits a fixed enum of ten component types. Users cannot add arbitrary button instances, choose a control from a library, reorder layers, or save named layout profiles.
- Current triggers are binary press/release despite being represented as floats on the wire.
- The current wire format has no motion-sensor fields. Gyro data cannot be sent to the PC without a compatible protocol extension.
- A visual button alone is insufficient: every new control must have a defined input meaning and must not silently claim support if the host cannot handle it.

## 2. Product goals and boundaries

- Make **Nintendo Switch 2** the first complete and polished layout.
- Then add the original Nintendo Switch, Xbox, PlayStation DualSense, Steam Deck-inspired, and generic/custom layouts.
- Let users switch layouts and customize any preset without overwriting the built-in preset.
- Provide a control library for adding/removing multiple instances of supported controls, repositioning and resizing them, changing labels/appearance, and adjusting opacity where practical.
- Persist custom layouts and macro definitions locally on the device.
- Keep the PC UI and its settings screens out of scope. Make only the PC protocol/translation changes required to support newly transmitted controls.
- Preserve compatibility with existing saved JSON layouts and existing mobile/PC versions where technically possible. Document any unavoidable compatibility boundary.
- Never represent a platform-specific feature as native PC gamepad functionality unless the Windows/Linux backend actually supports it. For controls with no standard native gamepad equivalent, allow configurable mapping to supported buttons, axes, keyboard/mouse actions, or macros where feasible.

## 3. Controller layouts and order

- [x] **Switch 2 (first / acceptance target):** two sticks with L3/R3, D-pad, A/B/X/Y in Nintendo label order, L/R and ZL/ZR, +/−, Home, Capture, and optional SL/SR side buttons for a Joy-Con-inspired variant. Clearly distinguish standard Switch 2 layout from split Joy-Con-inspired layout.
- [x] **Original Nintendo Switch:** equivalent familiar control family and button ordering, with the appropriate Home/Capture and +/− controls.
- [x] **Xbox:** Xbox A/B/X/Y order, D-pad, dual sticks, L3/R3, LB/RB, LT/RT, View/Menu, and optional remappable auxiliary controls.
- [x] **PlayStation DualSense-inspired:** Cross/Circle/Square/Triangle, D-pad, L3/R3, L1/R1/L2/R2, Create/Options, touchpad surface/click, and Home/PS-style system button.
- [x] **Steam Deck-inspired:** dual sticks, D-pad, ABXY, bumpers/triggers, View/Menu, and four rear paddles as configurable actions. Clearly mark trackpad and gyro support as software-mapped features rather than hardware emulation.
- [ ] **Generic / fully custom:** a Custom preset is selectable, but a blank-layout control library with arbitrary add/remove/duplicate instances remains planned.
- [x] Add a layout registry so further controller presets can be introduced without rewriting the renderer or settings model.
- [ ] Keep button labels, positions, shapes, colours, and left/right handedness in preset data rather than scattering controller-specific conditions through UI code.

## 4. Detailed implementation checklist

### Phase A — Plan, architecture and compatibility

- [x] Inspect current mobile control components, customization editor, DataStore settings, shared Colfer schema, generated button enum, and PC input/virtual-gamepad layers.
- [x] Record existing controls and gaps before changing code.
- [ ] Document the new control model, persistence format, protocol extension, host behavior, migration path, and acceptance tests.
- [ ] Identify the Colfer generation workflow and regenerate all generated artifacts from the schema source instead of hand-editing generated files.
- [ ] Decide and test wire compatibility for old/new mobile and PC builds. Do not assume appended schema fields are automatically backward-compatible; verify with fixtures or an integration test.

### Phase B — Reusable mobile control model

- [ ] Replace the fixed one-instance-per-enum assumption with stable control IDs and typed control definitions while retaining a migration path from existing `ButtonComponent` settings.
- [ ] Define control types: digital button, D-pad, analog stick, analog trigger, touch surface, motion/gyro input, and macro action.
- [ ] Store layout ID, display name, preset origin, orientation, control order/layer, position, size, visibility, labels/skin, action binding, and per-control settings.
- [ ] Allow multiple instances of a control where it makes sense, while preventing accidental duplicate IDs.
- [ ] Implement a control library to add, duplicate, remove, hide/show, drag, resize, and configure controls.
- [ ] Add named custom layouts, duplicate-preset flow, reset-to-preset, import/export, and validation for malformed or unsupported configurations.
- [ ] Keep the existing configuration JSON import/export usable through a versioned migration path.

### Phase C — Switch 2 first preset

- [x] Implement a data-driven Switch 2 preset using the existing component renderer and preset configuration model.
- [x] Add separate +, −, Home and Capture events; the existing analog triggers are relabelled ZL/ZR for Switch layouts.
- [x] Reuse the existing stick-click semantics for L3/R3; do not create duplicate press events.
- [ ] Add optional SL/SR side controls for a split/Joy-Con-inspired layout.
- [x] Use the Nintendo face-button label positions; preset style data selects Nintendo, Xbox, or PlayStation face layout.
- [x] Wire the preset into the live preview. Landscape/small-screen overlap still needs device-level verification.
- [x] Layout selection creates editable saved configuration data without modifying built-in preset definitions.

### Phase D — Analog triggers and configurable actions

- [x] Replace binary-only trigger touch behavior with a vertical position-to-value interaction; analog value is transmitted through the existing trigger fields. Configurable action fallback remains planned.
- [x] Pull the trigger from where the finger lands, so a tap is a full pull and dragging down eases it off. Measuring the value from the button's top edge meant a tap in the lower half stayed under the PC's press threshold and did nothing, which is what made ZL/ZR look broken.
- [x] Add a stick sensitivity setting (0.5×-2.0×), applied to the travel before the dead zone and response curve so both sticks and the whole-screen camera area share it.
- [ ] Define calibration, dead zone, sensitivity curve, maximum value, and release-to-zero behavior for LT/RT and ZL/ZR where applicable.
- [ ] Ensure touch cancellation, app backgrounding, rotation, disconnect, and navigation release every held input and reset axes/triggers. (Trigger cancellation path still needs a final safety pass.)
- [x] Provide a mobile action-binding editor so existing digital controls can be remapped to supported gamepad button actions. Direct keyboard/mouse action selection and analog-trigger-to-button remapping remain planned.
- [ ] Add safe input lifecycle handling so press/release events cannot get stuck during fast taps or multi-touch use.

### Phase E — New control types and protocol

- [x] Add explicit semantic flags for Plus, Minus, Home, Capture, SL/SR, and rear paddles within the existing 32-bit button masks; ZL/ZR reuse the existing analog trigger fields.
- [ ] Update the Colfer schema and regenerate the Kotlin/C protocol artifacts consistently.
- [x] Extend PC keyboard/mouse parsing and native gamepad translation with documented aliases for controls that have no dedicated Xbox-style native equivalent.
- [ ] Add gyro/motion data to a versioned protocol extension only after confirming encoding, units, sampling cadence, compatibility, and a safe disabled-by-default behavior.
- [x] Implement an optional Android gyroscope toggle with sensor lifecycle handling and a user-visible unavailable state. Calibration and adjustable sensitivity remain planned.
- [x] Define touch-surface drag mapping to the existing right-stick axes and reset on drag end/cancel; tap/click behavior remains planned.
- [x] Add an optional whole-screen right-stick camera area, drawn behind the controls so every button keeps working, with the gamepad background taking the accent colour while it is on.
- [x] Add an optional sliding mode for the D-pad and A/B/X/Y clusters: one surface per cluster, where the pressed direction or button follows the finger and diagonals are the finger between two of them.
- [x] Add controller background choices (theme, five dark presets, and a custom colour) plus keep-screen-on, both saved with the rest of the settings.
- [ ] Add a mapping table for unsupported host-specific controls so they remain configurable without falsely advertising native emulation.
- [ ] Add protocol round-trip tests and old/new fixture tests for buttons, analog axes, triggers, and motion data.

### Phase F — Macros

- [x] Add a local macro editor with named sequential button/trigger/axis steps, durations, and gaps. Simultaneous/chord actions remain planned.
- [x] Add four M1–M4 macro trigger buttons and cancellation cleanup when playback is cancelled.
- [x] Bound macro sequences to 32 steps and each hold/gap to 5 seconds; repeat loops are not supported.
- [ ] Ensure macro cancellation, disconnect, app backgrounding, and layout changes release all active inputs.
- [ ] Persist macros locally and include them in validated export/import without executing imported macros automatically. (Local persistence is implemented; layout JSON export/import integration remains planned.)

### Phase G — Additional presets

- [ ] Add original Switch preset using the shared renderer and control model.
- [ ] Add Xbox preset.
- [ ] Add PlayStation DualSense-inspired preset.
- [ ] Add Steam Deck-inspired preset, including configurable rear paddles and explicitly labelled mapped trackpad/gyro features.
- [ ] Add blank generic layout and an easy “create another layout” path.
- [ ] Verify that changing a preset changes only its visual/control arrangement and semantic bindings, not the shared network protocol unexpectedly.

### Phase H — Tests, accessibility and documentation

- [ ] Add unit tests for layout serialization, migration from legacy config, duplicate IDs, default layouts, action binding, and macro validation.
- [ ] Add UI tests for add/remove/drag/resize/visibility, preset switching, reset, import/export, and orientation changes where supported by the project test setup.
- [ ] Add protocol tests for old and new clients/hosts and verify the existing controls still work unchanged.
- [ ] Test simultaneous touches, rapid press/release, trigger partial pressure, stick-click, and release-on-disconnect.
- [ ] Test Android sensor unavailable/denied states and verify the app continues working without gyro.
- [ ] Run Android Gradle checks/build and PC CMake/build/tests using the repository's supported environments; report any environment-limited checks honestly.
- [ ] Update mobile README and root README with layout support, limitations, setup, and migration notes.
- [ ] Verify GitHub Actions build workflows; the original upstream workflows live under `mobile/.github/workflows` and `pc/.github/workflows`, so root-level CI integration may be needed for automatic execution.

## 5. Acceptance criteria

- [ ] Switch 2 is the first fully usable preset and includes all requested standard controls.
- [ ] Users can choose a preset and reposition/resize/show/hide provided component groups without modifying built-in presets. Duplicating controls and per-button action remapping remain planned.
- [ ] Existing saved layouts load or migrate safely; invalid imports show a clear error and do not corrupt saved settings.
- [ ] L3/R3 continue to generate the existing stick-click input exactly once.
- [ ] Triggers support continuous values when the interaction provides pressure and a reliable digital fallback otherwise.
- [ ] New controls that require PC support are represented in the shared schema, parsed on PC, and mapped to supported outputs or clearly documented fallbacks.
- [ ] Gyro and touch-surface features are never shown as working native outputs until their full data path is implemented and tested.
- [ ] Macros are configurable, cancellable, bounded, and always release active inputs when stopped.
- [ ] Switch, Xbox, PlayStation, Steam Deck-inspired, and generic layouts can be added through data/configuration rather than duplicated renderer implementations.
- [ ] Build/test results are recorded before the work is declared complete.

## 6. Minecraft and keyboard-only games

Minecraft reads a keyboard and a mouse, so a virtual pad can drive it only by
sending keys. This is what was built for it:

- [x] A **Minecraft** mobile preset: Xbox labels, both sticks, D-pad, face
  buttons, shoulders, triggers, and the `+`/`−`/HOME/CAPTURE cluster the PC's
  Minecraft layout binds to chat, HUD and debug keys.
- [x] **Custom key buttons** on the phone: four buttons whose key names travel in
  `GamepadReading.KeysDown`, resolved on the PC with the same `KeyNames` table
  the layouts use. Unknown names are ignored, never guessed.
- [x] Letter, digit, function, numpad, navigation, modifier, mouse and
  punctuation names (`/`, `;`, `,`, `.`, `-`, `=`, `[`, `]`, `` ` ``, `\`), with
  single-character shorthand on the PC (`/` is Slash, `1` is Digit1).
- [x] PC right-stick camera: mouse look is the default for the right stick, one
  migration carries an old arrow-key profile over once, and the pixel dead zone
  that required a half push before the camera moved is gone.
- [x] **Recorder on the pad**: a REC control next to HOLD opens a non-modal panel
  (the pad stays touchable) with icon buttons — record/stop-and-save, repeat, and
  play — and a list of saved takes. A take is the *whole* input of the moment
  (buttons, custom keys, sticks, triggers) on a 30 Hz clock, and playback walks
  that clock through the same dispatchers the controls use, so a chord and a
  gesture come back exactly as performed. Up to six takes, persisted on the phone.
- [x] **Any digital control can be bound to a PC key** from the Remap editor, and
  a bound control sends only that key.
- [x] **Remap is per key, from the customisation screen**: tapping a control opens
  its editor with a *Sends* row per button that component stands for (each face
  button, each D-pad direction, both shoulders, view/menu, plus/minus,
  home/capture, sides, paddles, stick clicks, both triggers, each custom-key
  button), each edit saved straight to settings. The whole-pad Remap table still
  edits everything at once.
- [x] **Two-key chords**: a bound value may hold two names joined with `+`, in the
  order picked. Both go down in the same packet (first name first), are latched
  until a packet carries the press, and release together — the press/released
  latching of a single key, applied to both halves. Covered by `ControlKeysTest`
  and `ControlPressTest`.
- [x] **Triggers accept a key binding**: bound, a pull past half sends the key or
  chord instead of the axis; with no key the axis behaves exactly as before.
- [x] The M1–M4 macro editor was removed from the layout customisation screen: the
  recorder replaced it, and the recorded take carries what the stepped macro could
  not (simultaneous buttons and real timing).
- [ ] Per-game automatic layout selection on the PC (the Minecraft layout is
  still selected by hand on the layouts page).

## 7. Non-goals for this iteration

- [ ] No PC-side layout editor redesign.
- [ ] No claim that a mobile layout can create hardware capabilities absent from the host's virtual-device backend.
- [ ] No removal of the original controller components or existing keyboard/mouse mapping behavior.
- [ ] No overwriting built-in presets when users customize them.
