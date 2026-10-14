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

- [ ] **Switch 2 (first / acceptance target):** two sticks with L3/R3, D-pad, A/B/X/Y in Nintendo label order, L/R and ZL/ZR, +/−, Home, Capture, and optional SL/SR side buttons for a Joy-Con-inspired variant. Clearly distinguish standard Switch 2 layout from split Joy-Con-inspired layout.
- [ ] **Original Nintendo Switch:** equivalent familiar control family and button ordering, with the appropriate Home/Capture and +/− controls.
- [ ] **Xbox:** Xbox A/B/X/Y order, D-pad, dual sticks, L3/R3, LB/RB, LT/RT, View/Menu, and optional remappable auxiliary controls.
- [ ] **PlayStation DualSense-inspired:** Cross/Circle/Square/Triangle, D-pad, L3/R3, L1/R1/L2/R2, Create/Options, touchpad surface/click, and Home/PS-style system button.
- [ ] **Steam Deck-inspired:** dual sticks, D-pad, ABXY, bumpers/triggers, View/Menu, and four rear paddles as configurable actions. Clearly mark trackpad and gyro support as software-mapped features rather than hardware emulation.
- [ ] **Generic / fully custom:** start from a blank or minimal layout and add controls from the control library.
- [ ] Add a layout registry so further controller presets can be introduced without rewriting the renderer or settings model.
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

- [ ] Implement a data-driven Switch 2 preset using the reusable control model.
- [ ] Add the separate +, −, Home, Capture, ZL and ZR controls.
- [ ] Reuse the existing stick-click semantics for L3/R3; do not create duplicate press events.
- [ ] Add optional SL/SR side controls for a split/Joy-Con-inspired layout.
- [ ] Use the correct Nintendo face-button label positions, and make label ordering explicit in preset data.
- [ ] Add a live preview and test landscape and smaller phone layouts for clipping, overlap, and touch-target size.
- [ ] Make layout switching preserve user edits by creating a custom copy rather than mutating the built-in preset.

### Phase D — Analog triggers and configurable actions

- [ ] Replace binary-only trigger touch behavior with a pressure-capable interaction model (for example, vertical travel/press depth) and a configurable digital fallback.
- [ ] Define calibration, dead zone, sensitivity curve, maximum value, and release-to-zero behavior for LT/RT and ZL/ZR where applicable.
- [ ] Ensure touch cancellation, app backgrounding, rotation, disconnect, and navigation release every held input and reset axes/triggers.
- [ ] Provide an action-binding editor so controls can map to supported gamepad buttons/axes or supported keyboard/mouse inputs through the existing PC mapping system.
- [ ] Add safe input lifecycle handling so press/release events cannot get stuck during fast taps or multi-touch use.

### Phase E — New control types and protocol

- [ ] Add explicit semantic inputs for Plus, Minus, Home, Capture, ZL/ZR, and optional SL/SR in the shared protocol.
- [ ] Update the Colfer schema and regenerate the Kotlin/C protocol artifacts consistently.
- [ ] Extend PC parsing and native gamepad translation only for controls that can be represented on the target backend; define documented fallback mappings for the rest.
- [ ] Add gyro/motion data to a versioned protocol extension only after confirming encoding, units, sampling cadence, compatibility, and a safe disabled-by-default behavior.
- [ ] Implement Android sensor permission/lifecycle handling where required, orientation calibration, sensitivity, recentering, and a user-visible toggle.
- [ ] Define touch-surface semantics: touch position, movement, tap/click, and release; map to a supported axis or mouse action instead of inventing a nonexistent standard gamepad touchpad input.
- [ ] Add a mapping table for unsupported host-specific controls so they remain configurable without falsely advertising native emulation.
- [ ] Add protocol round-trip tests and old/new fixture tests for buttons, analog axes, triggers, and motion data.

### Phase F — Macros

- [ ] Add a local macro editor with named sequences of button presses/releases, trigger/axis values, durations, gaps, and simultaneous/chord actions.
- [ ] Add macro assignment to any supported button or paddle, plus explicit stop/cancel behavior.
- [ ] Enforce sensible bounds on duration, step count, and repeat behavior; prevent runaway loops.
- [ ] Ensure macro cancellation, disconnect, app backgrounding, and layout changes release all active inputs.
- [ ] Persist macros locally and include them in validated export/import without executing imported macros automatically.

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
- [ ] Users can choose a preset, duplicate it, and add/remove/reposition/resize controls without modifying the built-in preset.
- [ ] Existing saved layouts load or migrate safely; invalid imports show a clear error and do not corrupt saved settings.
- [ ] L3/R3 continue to generate the existing stick-click input exactly once.
- [ ] Triggers support continuous values when the interaction provides pressure and a reliable digital fallback otherwise.
- [ ] New controls that require PC support are represented in the shared schema, parsed on PC, and mapped to supported outputs or clearly documented fallbacks.
- [ ] Gyro and touch-surface features are never shown as working native outputs until their full data path is implemented and tested.
- [ ] Macros are configurable, cancellable, bounded, and always release active inputs when stopped.
- [ ] Switch, Xbox, PlayStation, Steam Deck-inspired, and generic layouts can be added through data/configuration rather than duplicated renderer implementations.
- [ ] Build/test results are recorded before the work is declared complete.

## 6. Non-goals for this iteration

- [ ] No PC-side layout editor redesign.
- [ ] No claim that a mobile layout can create hardware capabilities absent from the host's virtual-device backend.
- [ ] No removal of the original controller components or existing keyboard/mouse mapping behavior.
- [ ] No overwriting built-in presets when users customize them.
