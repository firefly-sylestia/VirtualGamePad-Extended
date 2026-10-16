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
	// The driver itself, never the client library: `ViGEmClient.dll` now ships
	// beside the application, so a library probe would answer "installed" on a
	// machine that has never seen the driver and would hide the button that
	// installs it. The bus registers the kernel service "ViGEmBus", and asking
	// the service manager about a service needs no elevation. When the service
	// manager cannot be asked at all, the driver file is the next best evidence;
	// a service known to be absent is a real answer, even if a leftover file is
	// still lying around.
	SC_HANDLE manager = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT);
	if (manager != nullptr)
	{
		SC_HANDLE service = OpenServiceW(manager, L"ViGEmBus", SERVICE_QUERY_STATUS);
		if (service != nullptr)
		{
			CloseServiceHandle(service);
			CloseServiceHandle(manager);
			return true;
		}
		const bool knownMissing = GetLastError() == ERROR_SERVICE_DOES_NOT_EXIST;
		CloseServiceHandle(manager);
		if (knownMissing)
			return false;
	}

	wchar_t systemRoot[MAX_PATH]{};
	if (GetEnvironmentVariableW(L"SystemRoot", systemRoot, MAX_PATH) == 0)
		return false;
	const std::wstring driverFile =
		std::wstring(systemRoot) + L"\\System32\\drivers\\ViGEmBus.sys";
	return GetFileAttributesW(driverFile.c_str()) != INVALID_FILE_ATTRIBUTES;
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
