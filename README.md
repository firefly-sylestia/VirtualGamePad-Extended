# VirtualGamePad Extended

Combined source tree for the Android mobile app and desktop/PC app.

## Projects

- **Android / Mobile:** `mobile/` (from [VirtualGamePad-Mobile](https://github.com/kitswas/VirtualGamePad-Mobile))
- **Desktop / PC:** `pc/` (from [VirtualGamePad-PC](https://github.com/kitswas/VirtualGamePad-PC))

Both projects are vendored as actual files, not Git submodule pointers. Upstream source files, build configuration, assets, and workflow definitions are retained in each project's directory. GitHub only automatically runs workflow files under the repository-root `.github/workflows/`; workflows retained under `mobile/` and `pc/` are source copies and may need root-level wrappers to run in this combined repository.

The upstream projects are licensed under GPL-3.0. Refer to the respective project license files for applicable terms.
