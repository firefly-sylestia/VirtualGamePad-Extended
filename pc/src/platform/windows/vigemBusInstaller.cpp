#include "vigemBusInstaller.hpp"

#include <QCoreApplication>
#include <QDebug>
#include <QDir>
#include <QFileInfo>
#include <QStringList>

#include <string>

#include <windows.h>
// The target is built with WIN32_LEAN_AND_MEAN, which leaves shellapi.h out of
// windows.h, so ShellExecuteExW has to come from here explicitly.
#include <shellapi.h>

namespace
{
/**
 * Exit code Windows uses when the user dismisses the elevation prompt. It is not
 * a fault in the installer, so it gets its own wording.
 */
constexpr DWORD kCancelledByUser = ERROR_CANCELLED;

/** The file name the CMake download writes, tried first. */
const QString kExpectedFileName = QStringLiteral("ViGEmBus_1.22.0_x64_x86_arm64.exe");
} // namespace

QString ViGEmBusInstaller::bundledInstallerPath()
{
	const QDir appDir(QCoreApplication::applicationDirPath());

	// The exact name first, so a folder that also holds an older copy cannot win.
	const QString expected = appDir.filePath(kExpectedFileName);
	if (QFileInfo::exists(expected))
		return expected;

	// Otherwise take whatever ViGEmBus installer was shipped, which keeps this
	// working when the bundled version is bumped without touching this file.
	const QStringList candidates =
		appDir.entryList(QStringList{QStringLiteral("ViGEmBus*.exe")}, QDir::Files, QDir::Name);
	if (candidates.isEmpty())
		return QString();

	return appDir.filePath(candidates.first());
}

bool ViGEmBusInstaller::isInstalled()
{
	// This is the same probe `vigemClient.cpp` does, so the answer matches what
	// the gamepad backend will find a moment later.
	HMODULE module = LoadLibraryW(L"ViGEmClient.dll");
	if (module == nullptr)
		return false;

	FreeLibrary(module);
	return true;
}

bool ViGEmBusInstaller::run(QString *error)
{
	auto fail = [error](const QString &message)
	{
		if (error != nullptr)
			*error = message;
		return false;
	};

	const QString installer = bundledInstallerPath();
	if (installer.isEmpty())
	{
		return fail(QStringLiteral("this build does not include the ViGEmBus installer. "
								   "Get it from "
								   "https://github.com/nefarius/ViGEmBus/releases"));
	}

	SHELLEXECUTEINFOW info{};
	info.cbSize = sizeof(info);
	// "runas" is what raises the consent prompt; the app keeps its own token.
	info.fMask = SEE_MASK_NOCLOSEPROCESS | SEE_MASK_NOASYNC;
	info.lpVerb = L"runas";
	const std::wstring installerPath = QDir::toNativeSeparators(installer).toStdWString();
	info.lpFile = installerPath.c_str();
	info.nShow = SW_SHOWNORMAL;

	if (ShellExecuteExW(&info) == FALSE)
	{
		const DWORD code = GetLastError();
		if (code == kCancelledByUser)
			return fail(QStringLiteral("the ViGEmBus installer was cancelled before it started."));
		return fail(QStringLiteral("the ViGEmBus installer could not be started (error %1).")
						.arg(code));
	}

	if (info.hProcess == nullptr)
		return fail(QStringLiteral("the ViGEmBus installer started but could not be waited on."));

	WaitForSingleObject(info.hProcess, INFINITE);

	DWORD exitCode = 0;
	const bool readExitCode = GetExitCodeProcess(info.hProcess, &exitCode) != FALSE;
	CloseHandle(info.hProcess);

	if (!readExitCode)
		return fail(QStringLiteral("the ViGEmBus installer finished but its result could not be read."));

	if (exitCode != 0)
	{
		// The driver cannot be installed without a reboot on some machines, and
		// its own updater only installs on the second run; neither is a failure
		// the app can fix, so the user is told what happened.
		return fail(QStringLiteral("the ViGEmBus installer reported failure (code %1). "
								   "Run it again from %2, and restart the app afterwards.")
						.arg(exitCode)
						.arg(QDir::toNativeSeparators(installer)));
	}

	qInfo() << "ViGEmBus installer finished successfully";
	return true;
}
