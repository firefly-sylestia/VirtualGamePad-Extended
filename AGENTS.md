# AGENTS.md

## Project Overview

**VirtualGamePad Extended** is a combined source tree for two projects that talk over a shared Colfer binary protocol:

- **Mobile / Android**: `mobile/` — Kotlin + Jetpack Compose controller app.
- **Desktop / PC**: `pc/` — C++ with Qt6 server that translates input into OS-level events.

Both projects are vendored as real source trees (not submodules). Upstream workflow files kept under `mobile/.github/workflows` and `pc/.github/workflows` are source copies; the workflows that actually run live at the repository root in `.github/workflows/`.

## Build policy

1. **Never build locally.** Do not run Android Gradle builds/tests/lint, PC CMake/compiler builds, or install toolchains just to verify a change. Do not start background build daemons.
2. **Builds happen in CI on push.** Verification is done by the GitHub Actions workflows (`.github/workflows/`), which run on push and pull requests. Make the source change, push it, and let CI build and test.
3. If a check cannot be run locally, say so honestly instead of substituting an unverified claim. Static review and inspecting existing artifacts is fine; compiling and packaging is not.

## Repository conventions

- Do not edit `mobile/VGP_Data_Exchange` or `pc/VGP_Data_Exchange` generated protocol artifacts by hand unless the change is explicitly about the protocol definition.
- The mobile and PC protocol copies of `GameButtons` (C header and Java enum) must stay in sync; the root CI workflow guards against drift.
- Do not modify `pc/third-party-libs` or `pc/doxygen-awesome-css`.
