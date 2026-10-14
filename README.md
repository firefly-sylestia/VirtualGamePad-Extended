# VirtualGamePad-Extended

A combined workspace for developing an extended version of [VirtualGamePad](https://github.com/kitswas/VirtualGamePad), keeping the Android client and desktop server in separate projects.

## Source projects

- **Android client:** [kitswas/VirtualGamePad-Mobile](https://github.com/kitswas/VirtualGamePad-Mobile) — checked out at the upstream `main` commit recorded by this repository's `mobile` submodule.
- **PC server:** [kitswas/VirtualGamePad-PC](https://github.com/kitswas/VirtualGamePad-PC) — checked out at the upstream `main` commit recorded by this repository's `pc` submodule.

Both upstream projects are licensed under GPL-3.0. Preserve their license and attribution notices when modifying or redistributing their source.

## Clone the workspace

Clone with submodules enabled:

```bash
git clone --recurse-submodules https://github.com/firefly-sylestia/VirtualGamePad-Extended.git
cd VirtualGamePad-Extended
```

If you already cloned without submodules:

```bash
git submodule update --init --recursive
```

The projects remain separate build units:

- Build the Android client from `mobile/` using its existing Gradle wrapper and instructions.
- Build the desktop server from `pc/` using its existing CMake configuration and instructions.

## Important workflow note

The upstream GitHub Actions workflow files are retained inside their respective submodules, but GitHub does **not** automatically run workflows stored inside a submodule as workflows of this parent repository. Root-level CI workflows will be added after the source import is converted to a vendored layout or the workflows are deliberately adapted to build the submodule checkouts.

## Current status

This repository currently links the two upstream repositories as Git submodules to preserve their independent histories and avoid rewriting source before design work begins. The source files are not yet vendored directly into this repository.

No UI redesign or new button behavior has been implemented yet.
