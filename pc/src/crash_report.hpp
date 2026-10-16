/**
 * @file crash_report.hpp
 * @brief Turns an unexpected exit into a report the user can send.
 *
 * A crash on Windows takes the process down with no dialog and no chance to
 * explain itself — the user only sees the window vanish. This module makes the
 * *next* launch say what happened: a small marker file marks a session as
 * running and is removed only on a clean exit, so a marker that is still there
 * means the previous run never got to finish. The crash handlers installed on
 * Windows append the exception code to that marker as raw Win32 writes, so they
 * work even when the Qt machinery that normally logs is what just broke.
 *
 * On the next start the marker is consumed once, the report is written
 * automatically with the tail of the crashed session's log attached, and the
 * app offers to send it — prefilled into a GitHub issue, or as a file the user
 * can attach anywhere.
 */
#pragma once

#include <QString>
#include <QUrl>

namespace CrashReport
{
/** What is known about a run that ended without being asked to. */
struct PreviousCrash
{
	/** The version that session was started with. */
	QString version;
	/** When that session started, ISO 8601. */
	QString startedAt;
	/**
	 * The exception code the crash handlers recorded, such as `0xC0000005`.
	 * Empty when the process died without a handler getting a chance to run —
	 * a marker alone still means the exit was not clean.
	 */
	QString exception;
	/** The log file that session wrote, so its tail can be attached. */
	QString logPath;
};

/**
 * Mark this session as running and install the crash handlers.
 *
 * Call this once, as early as possible: everything written to the marker after
 * this point is what the next launch needs. @p logPath is the file this
 * session's messages go to; the marker lives beside it, which is also where
 * the finished report is written.
 *
 * @param logPath absolute path of the current session's log file
 * @param appVersion the version recorded in the marker and the report
 */
void beginSession(const QString &logPath, const QString &appVersion);

/** Called on a normal exit so the next launch reports nothing. */
void endSession();

/**
 * Consume the marker an earlier session left behind, if there is one.
 *
 * The marker is deleted here, so a crash is reported exactly once no matter
 * how often the app starts afterwards.
 *
 * @param anyLogPath a log path from this run; the marker is looked for beside
 *        it, because every log slot and the marker share one directory
 * @param out filled with what the marker and its file recorded
 * @return true when the previous run did not exit cleanly
 */
bool takePreviousCrash(const QString &anyLogPath, PreviousCrash &out);

/**
 * Build the report text for @p crash and write it beside the log.
 *
 * The text always contains the session header and the tail of the crashed
 * session's log (bounded, so a huge file cannot produce a huge report). The
 * file is written as `crash-report-<timestamp>.txt`; when it cannot be — a
 * read-only install directory, say — the text is still returned and
 * @p writtenTo stays empty.
 *
 * @param crash the crash to describe
 * @param writtenTo receives the report file's path, or stays empty on failure
 * @return the full report text
 */
QString buildReport(const PreviousCrash &crash, QString *writtenTo = nullptr);

/**
 * The GitHub new-issue URL with title and body prefilled, so "report a crash"
 * is one click from the dialog to a filled-in form. The body is the report,
 * shortened in the middle when a URL cannot carry it all; the full text lives
 * in the report file the dialog points at.
 */
QUrl issueUrl(const QString &report);
} // namespace CrashReport
