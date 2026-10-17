#pragma once

#include <QString>
#include <QVector>
#include <QtGlobal>

/**
 * Which window keyboard and mouse injection is allowed to reach.
 *
 * SendInput has no destination parameter: it delivers to whichever window is in
 * front. That is right for a game the player has just clicked, but it also means
 * a phone that is still connected types into whatever happens to have focus — a
 * browser, a chat window, or this application's own settings page. Locking input
 * to a chosen window makes the phone's keyboard and mouse controls belong to
 * that program and to nothing else.
 *
 * The virtual gamepad backend is not affected: a virtual device is plugged into
 * the system and every program that reads controllers sees it, so there is no
 * window to lock it to.
 *
 * A lock that was saved from a previous run is resolved again by title and
 * process. Window handles are reused by the system, so restoring one directly
 * could aim input at a completely different program; when the saved window
 * cannot be found, injection is paused rather than allowed to fall back.
 */
namespace input_target
{
/** One top-level window a player can choose. */
struct WindowInfo
{
	quintptr handle = 0;
	QString title;
	QString process;
};

/** Whether this platform can enumerate and lock to windows. */
bool isSupported();

/**
 * Visible top-level windows, excluding this application's own, sorted by title.
 *
 * Empty where [isSupported] is false.
 */
QVector<WindowInfo> listWindows();

/** Describes a window that still exists, or returns an empty info. */
WindowInfo describeWindow(quintptr handle);

/**
 * Finds a window again after the application restarted.
 *
 * Matches title and process first, then a process with exactly one top-level
 * window, so a game whose title changes with the current level still resolves.
 * Returns 0 when the program cannot be identified unambiguously.
 */
quintptr findWindow(const QString &title, const QString &process);

/** The window input is locked to, or 0 when no window is locked. */
quintptr lockedWindow();

/**
 * Whether a window lock is configured at all.
 *
 * A configured lock whose window is not running still refuses injection: it
 * must not quietly widen to every program.
 */
bool isLocked();

/**
 * Locks input to @p handle, or removes the lock when [locked] is false.
 *
 * A true @p locked with a zero @p handle means "a program was chosen but is not
 * running", which refuses injection until it is resolved again.
 */
void setLockedWindow(quintptr handle, bool locked);

/**
 * Whether a keyboard or mouse event may be injected right now.
 *
 * With no lock this keeps the old behaviour: events go to the focused window,
 * except while this application itself is in front, because there they would
 * land in the layout editor instead of a game. With a lock, events are sent only
 * while the chosen window is in front and still exists.
 */
bool acceptsInjection();
} // namespace input_target
