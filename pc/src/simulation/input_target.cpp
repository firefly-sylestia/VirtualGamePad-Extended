#include "input_target.hpp"

#include <atomic>

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
// windows.h defines min/max as macros, which breaks <algorithm>.
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>

#include <QFileInfo>
#include <algorithm>
#include <iterator>
#include <utility>

namespace
{
std::atomic<quintptr> g_lockedWindow{0};
std::atomic<bool> g_locked{false};

/** The file name of a process, for telling two windows apart in the picker. */
QString processName(DWORD pid)
{
	HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
	if (process == nullptr)
		return {};

	wchar_t path[MAX_PATH] = {};
	DWORD length = MAX_PATH;
	QString name;
	if (QueryFullProcessImageNameW(process, 0, path, &length))
		name = QFileInfo(QString::fromWCharArray(path, static_cast<int>(length))).fileName();
	CloseHandle(process);
	return name;
}

struct EnumState
{
	QVector<input_target::WindowInfo> windows;
};

BOOL CALLBACK collectWindow(HWND hwnd, LPARAM lparam)
{
	auto *state = reinterpret_cast<EnumState *>(lparam);

	if (!IsWindowVisible(hwnd))
		return TRUE;
	if (GetWindow(hwnd, GW_OWNER) != nullptr)
		return TRUE;
	if ((GetWindowLongPtrW(hwnd, GWL_EXSTYLE) & WS_EX_TOOLWINDOW) != 0)
		return TRUE;

	const int titleLength = GetWindowTextLengthW(hwnd);
	if (titleLength <= 0)
		return TRUE;

	DWORD pid = 0;
	GetWindowThreadProcessId(hwnd, &pid);
	if (pid == 0 || pid == GetCurrentProcessId())
		return TRUE;

	wchar_t title[512] = {};
	GetWindowTextW(hwnd, title, static_cast<int>(std::size(title)));

	input_target::WindowInfo info;
	info.handle = reinterpret_cast<quintptr>(hwnd);
	info.title = QString::fromWCharArray(title);
	info.process = processName(pid);
	state->windows.push_back(std::move(info));
	return TRUE;
}
} // namespace

namespace input_target
{
bool isSupported()
{
	return true;
}

QVector<WindowInfo> listWindows()
{
	EnumState state;
	EnumWindows(&collectWindow, reinterpret_cast<LPARAM>(&state));
	std::sort(state.windows.begin(),
			  state.windows.end(),
			  [](const WindowInfo &a, const WindowInfo &b)
			  { return a.title.localeAwareCompare(b.title) < 0; });
	return state.windows;
}

WindowInfo describeWindow(quintptr handle)
{
	WindowInfo info;
	if (handle == 0)
		return info;

	const HWND hwnd = reinterpret_cast<HWND>(handle);
	if (!IsWindow(hwnd))
		return info;

	const int titleLength = GetWindowTextLengthW(hwnd);
	if (titleLength <= 0)
		return info;

	wchar_t title[512] = {};
	GetWindowTextW(hwnd, title, static_cast<int>(std::size(title)));

	DWORD pid = 0;
	GetWindowThreadProcessId(hwnd, &pid);
	if (pid == 0 || pid == GetCurrentProcessId())
		return info;

	info.handle = handle;
	info.title = QString::fromWCharArray(title);
	info.process = processName(pid);
	return info;
}

quintptr findWindow(const QString &title, const QString &process)
{
	if (title.isEmpty() && process.isEmpty())
		return 0;

	const QVector<WindowInfo> windows = listWindows();

	// The exact window is the best answer.
	for (const WindowInfo &window : windows)
	{
		if (window.title == title && window.process == process)
			return window.handle;
	}

	// A game that puts the level or map in its title gets a new title every time.
	// Remembering the process is enough when it owns exactly one window; two
	// windows with that process would be ambiguous, and guessing could aim input
	// at the wrong one.
	if (process.isEmpty())
		return 0;

	quintptr match = 0;
	int matches = 0;
	for (const WindowInfo &window : windows)
	{
		if (window.process == process)
		{
			match = window.handle;
			++matches;
		}
	}
	return matches == 1 ? match : 0;
}

quintptr lockedWindow()
{
	return g_lockedWindow.load(std::memory_order_relaxed);
}

bool isLocked()
{
	return g_locked.load(std::memory_order_relaxed);
}

void setLockedWindow(quintptr handle, bool locked)
{
	g_lockedWindow.store(handle, std::memory_order_relaxed);
	g_locked.store(locked, std::memory_order_relaxed);
}

bool acceptsInjection()
{
	if (g_locked.load(std::memory_order_relaxed))
	{
		const quintptr locked = g_lockedWindow.load(std::memory_order_relaxed);
		if (locked == 0)
			return false; // The chosen program is not running.

		const HWND target = reinterpret_cast<HWND>(locked);
		// A closed window must not fall back to the foreground one: that is
		// exactly the leak the lock exists to stop.
		if (!IsWindow(target))
			return false;
		return GetForegroundWindow() == target;
	}

	const HWND foreground = GetForegroundWindow();
	if (foreground == nullptr)
		return true; // Nothing is focused, so nothing can receive the event.

	DWORD ownerPid = 0;
	GetWindowThreadProcessId(foreground, &ownerPid);
	return ownerPid != GetCurrentProcessId();
}
} // namespace input_target

#else

namespace input_target
{
bool isSupported()
{
	return false;
}

QVector<WindowInfo> listWindows()
{
	return {};
}

WindowInfo describeWindow(quintptr handle)
{
	(void)handle;
	return {};
}

quintptr findWindow(const QString &title, const QString &process)
{
	(void)title;
	(void)process;
	return 0;
}

quintptr lockedWindow()
{
	return 0;
}

bool isLocked()
{
	return false;
}

void setLockedWindow(quintptr handle, bool locked)
{
	(void)handle;
	(void)locked;
}

bool acceptsInjection()
{
	return true;
}
} // namespace input_target

#endif
