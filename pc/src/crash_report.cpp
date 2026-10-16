#include "crash_report.hpp"

#include <QDateTime>
#include <QDebug>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QStringList>
#include <QSysInfo>
#include <QTextStream>
#include <QUrlQuery>

#include <atomic>
#include <string>

#ifdef _WIN32
// Exclude rarely-used stuff from Windows headers, as the rest of the app does.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#endif

namespace
{
/** The marker's fixed name; it lives in the directory the logs are written to. */
const QString kMarkerName = QStringLiteral("virtualgamepad.crash");

/** How much of the log's tail a report may carry, and its line cap. */
constexpr qint64 kTailBytes = 64 * 1024;
constexpr int kTailLines = 400;

/** The marker this session owns, so endSession and takePreviousCrash find it. */
QString g_markerPath;

#ifdef _WIN32
/** The same path as a wide string: the crash handlers must not touch Qt. */
std::wstring g_markerPathNative;
/** First writer wins, so the nested terminate/abort/SIGABRT chain logs once. */
std::atomic<bool> g_reported{false};

/**
 * Append one line to the marker with plain Win32, safe to call while the
 * process is dying: no Qt, no streams, no heap the crash may have corrupted.
 * Best effort by design — if even this fails, Windows reports the crash as it
 * normally would and the marker alone still says the exit was not clean.
 */
void appendMarkerLine(const char *line)
{
	if (g_markerPathNative.empty() || g_reported.exchange(true))
		return;

	HANDLE marker = CreateFileW(g_markerPathNative.c_str(),
								FILE_APPEND_DATA,
								FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
								nullptr,
								OPEN_ALWAYS,
								FILE_ATTRIBUTE_NORMAL,
								nullptr);
	if (marker == INVALID_HANDLE_VALUE)
		return;
	DWORD written = 0;
	WriteFile(marker, line, static_cast<DWORD>(strlen(line)), &written, nullptr);
	CloseHandle(marker);
}

/** Records the faulting exception code, then lets Windows crash as usual. */
LONG WINAPI onUnhandledException(EXCEPTION_POINTERS *info)
{
	const DWORD code =
		(info != nullptr && info->ExceptionRecord != nullptr) ? info->ExceptionRecord->ExceptionCode
															  : 0;
	char line[64];
	snprintf(line, sizeof(line), "exception=0x%08lX\n", static_cast<unsigned long>(code));
	appendMarkerLine(line);
	// Continue the search so the normal crash dialog and WER still run: this
	// module only annotates the crash, it never changes how it ends.
	return EXCEPTION_CONTINUE_SEARCH;
}

/** An uncaught C++ exception never reaches the SEH filter; this covers it. */
void onTerminate()
{
	appendMarkerLine("exception=uncaught C++ exception\n");
	std::abort();
}

/** `abort()` and `qFatal` end here before the fail-fast path takes over. */
void onAbort(int)
{
	appendMarkerLine("exception=abort()\n");
}
#endif
} // namespace

void CrashReport::beginSession(const QString &logPath, const QString &appVersion)
{
	g_markerPath = QFileInfo(logPath).absoluteDir().filePath(kMarkerName);

	// The marker first: from here until endSession, its presence means "still
	// running", and the handlers below fill in the reason when that ends early.
	QFile marker(g_markerPath);
	if (marker.open(QIODevice::WriteOnly | QIODevice::Truncate | QIODevice::Text))
	{
		QTextStream out(&marker);
		out << "version=" << appVersion << '\n'
			<< "started=" << QDateTime::currentDateTime().toString(Qt::ISODate) << '\n'
			<< "log=" << logPath << '\n';
		marker.close();
	}
	else
	{
		qWarning() << "The crash marker could not be written to" << g_markerPath
				   << "- an unexpected exit will not be reported";
	}

#ifdef _WIN32
	g_markerPathNative = g_markerPath.toStdWString();
	SetUnhandledExceptionFilter(onUnhandledException);
	std::set_terminate(onTerminate);
	std::signal(SIGABRT, onAbort);
#endif
}

void CrashReport::endSession()
{
	// Reached only when the event loop returned: the next start must not be
	// told about a crash that did not happen.
	if (!g_markerPath.isEmpty())
		QFile::remove(g_markerPath);
}

bool CrashReport::takePreviousCrash(const QString &anyLogPath, PreviousCrash &out)
{
	const QString markerPath = QFileInfo(anyLogPath).absoluteDir().filePath(kMarkerName);
	QFile marker(markerPath);
	if (!marker.exists() || !marker.open(QIODevice::ReadOnly | QIODevice::Text))
		return false;

	// Consumed here, so later starts of the same crash report nothing: one
	// crash, one report, no matter how many times the app is reopened. The
	// content is read while the file is still open — closing first would leave
	// the stream below reading a closed device and produce an empty report.
	QString content;
	{
		QTextStream in(&marker);
		content = in.readAll();
	}
	marker.close();
	QFile::remove(markerPath);

	const QStringList lines = content.split(QLatin1Char('\n'));
	for (const QString &line : lines)
	{
		const int split = line.indexOf(QLatin1Char('='));
		if (split <= 0)
			continue;
		const QString key = line.left(split);
		const QString value = line.mid(split + 1);
		if (key == QLatin1String("version"))
			out.version = value;
		else if (key == QLatin1String("started"))
			out.startedAt = value;
		else if (key == QLatin1String("log"))
			out.logPath = value;
		else if (key == QLatin1String("exception"))
			out.exception = value;
	}
	return true;
}

namespace
{
/** The last @p maxLines lines of @p path, from at most the last 64 KiB. */
QString logTail(const QString &path, int maxLines)
{
	QFile file(path);
	if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
		return QStringLiteral("(the log file could not be read: %1)\n").arg(path);

	// Seeking keeps a multi-megabyte log from being read whole; the partial
	// line under the seek point is dropped so the tail starts on a line start.
	if (file.seek(qMax<qint64>(0, file.size() - kTailBytes)))
		file.readLine();

	QStringList lines;
	QTextStream in(&file);
	while (!in.atEnd())
		lines << in.readLine();
	if (lines.size() > maxLines)
		lines = lines.mid(lines.size() - maxLines);
	return lines.join(QLatin1Char('\n')) + QLatin1Char('\n');
}
} // namespace

QString CrashReport::buildReport(const PreviousCrash &crash, QString *writtenTo)
{
	const QString exitReason = crash.exception.isEmpty()
								   ? QStringLiteral("the process ended without a recorded reason")
								   : QStringLiteral("unhandled exception %1").arg(crash.exception);

	QString report;
	report += QStringLiteral("VirtualGamePad Extended crash report\n");
	report += QStringLiteral("App version: %1\n").arg(crash.version.isEmpty() ? QStringLiteral("unknown")
																			  : crash.version);
	report += QStringLiteral("Session started: %1\n").arg(crash.startedAt.isEmpty()
															   ? QStringLiteral("unknown")
															   : crash.startedAt);
	report += QStringLiteral("Detected at: %1\n")
				  .arg(QDateTime::currentDateTime().toString(Qt::ISODate));
	report += QStringLiteral("System: %1 (%2)\n")
				  .arg(QSysInfo::prettyProductName(), QSysInfo::buildCpuArchitecture());
	report += QStringLiteral("Exit: %1\n").arg(exitReason);
	report += QStringLiteral("Log: %1\n\n").arg(crash.logPath);
	report += QStringLiteral("----- log tail -----\n");
	report += logTail(crash.logPath, kTailLines);
	report += QStringLiteral("----- end of log tail -----\n");

	const QString reportPath =
		QFileInfo(crash.logPath).absoluteDir().filePath(
		 QStringLiteral("crash-report-%1.txt")
			 .arg(QDateTime::currentDateTime().toString(QStringLiteral("yyyyMMdd-HHmmss"))));
	QFile file(reportPath);
	if (file.open(QIODevice::WriteOnly | QIODevice::Text | QIODevice::Truncate))
	{
		QTextStream out(&file);
		out << report;
		file.close();
		if (writtenTo != nullptr)
			*writtenTo = reportPath;
	}
	else
	{
		qWarning() << "The crash report could not be written to" << reportPath;
	}
	return report;
}

QUrl CrashReport::issueUrl(const QString &report)
{
	QString body = report;
	// A URL is not a file: past a few kilobytes browsers and GitHub start
	// refusing it, so the middle gives way first — the header and the end of
	// the log are the parts that identify the crash. The full text is in the
	// report file the dialog points at.
	constexpr int kUrlSafeBody = 5500;
	if (body.size() > kUrlSafeBody)
	{
		body = body.left(kUrlSafeBody / 3) +
			   QStringLiteral("\n[… shortened for the link; the full report is in the "
							  "attached crash-report file …]\n") +
			   body.right(kUrlSafeBody - kUrlSafeBody / 3);
	}

	QUrl url(QStringLiteral("https://github.com/firefly-sylestia/VirtualGamePad-Extended/issues/new"));
	QUrlQuery query;
	query.addQueryItem(QStringLiteral("title"),
					   QStringLiteral("The app closed unexpectedly"));
	query.addQueryItem(QStringLiteral("body"), body);
	url.setQuery(query);
	return url;
}
