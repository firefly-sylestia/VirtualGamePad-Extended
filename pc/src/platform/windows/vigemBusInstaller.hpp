#pragma once

#include <QString>

/**
 * Installs the ViGEmBus driver from the copy that ships next to the executable.
 *
 * Without the driver there is no virtual gamepad at all: `vigemClient.cpp`
 * loads its library but the bus refuses to connect, the app falls back to the
 * Windows input-injection API, and games ignore that, so a phone that is
 * clearly sending input looks like "no controller connected". Making the user
 * go and find the driver themselves is the difference between the feature
 * working and not.
 *
 * The installer is therefore carried in the distribution next to the app (see
 * the `VGP_BUNDLE_VIGEMBUS` block in `CMakeLists.txt`), and this is the only
 * place that knows where it lives and how to start it. The driver is a kernel
 * component, so starting it needs elevation: Windows shows its own consent
 * prompt and the app itself stays unelevated.
 */
namespace ViGEmBusInstaller
{
/**
 * The bundled installer, or an empty string when this build did not ship one.
 *
 * An empty result is normal: the download is skipped on an offline machine, and
 * the caller is expected to point the user at the release page instead.
 */
QString bundledInstallerPath();

/**
 * Whether the driver the virtual pad is created through is present.
 *
 * This probes the driver itself — the `ViGEmBus` kernel service — not the
 * client library, because `ViGEmClient.dll` ships beside the app on every
 * machine: a library probe would answer "installed" on a machine that has never
 * seen the driver and would hide the button that installs it.
 */
bool isInstalled();

/**
 * Runs the bundled installer and waits for it to finish.
 *
 * Elevation is requested through the shell, so the user sees the standard
 * consent prompt; declining it is reported as a failure, not an error to crash
 * on. On success the driver may not be usable until Windows restarts, so the
 * caller should re-check rather than assume.
 *
 * @param error filled with a user-facing reason when the result is false
 * @return true when the installer ran and reported success
 */
bool run(QString *error);
} // namespace ViGEmBusInstaller
