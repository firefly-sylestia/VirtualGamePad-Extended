#include "appdir.hpp"
#include "crash_report.hpp"
#include "platform/windows/console.hpp"
#include "ui/mainwindow.hpp"
#include "ui/theme.hpp"

#include <QApplication>
#include <QDesktopServices>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QLockFile>
#include <QMessageBox>
#include <QMutex>
#include <QPushButton>
#include <QStandardPaths>
#include <QStyleFactory>
#include <QUrl>
#include <memory>

#if defined(QT_DEBUG)
const QString logFilePath = "virtualgamepad.log";
#elif defined(_WIN32)
const QString logFilePath = "virtualgamepad.log";
#elif defined(__linux__)
const QString logFilePath =
	QStandardPaths::writableLocation(QStandardPaths::TempLocation) + "/virtualgamepad.log";
#endif

/**
 * @brief Maximum number of log files to try to create.
 * If all are in use, logging to file will be disabled.
 */
const int8_t max_log_files = 5;

static QFile logFile(logFilePath);

void customMessageHandler(QtMsgType type, const QMessageLogContext &context, const QString &msg)
{
	static QMutex logFileMutex;
	QString txt = qFormatLogMessage(type, context, msg);

	QMutexLocker locker(&logFileMutex);
	QTextStream textStream(&logFile);
	textStream << txt << Qt::endl;

	if (type == QtFatalMsg)
		abort();
}

int main(int argc, char *argv[])
{
#ifdef _WIN32
	// Attach to parent console if launched from terminal
	attachToParentConsole();
#endif

	// Try to find a free log file slot (virtualgamepad.1.log, virtualgamepad.2.log, etc.)
	static std::unique_ptr<QLockFile> lockFile;
	QFileInfo baseInfo(logFilePath);
	QString baseName = baseInfo.completeBaseName();
	QString suffix = baseInfo.suffix();
	// Use the directory from the configured path, or current dir if none specified
	QDir logDir = baseInfo.dir();

	for (int i = 1; i <= max_log_files; ++i)
	{
		QString fileName = QString("%1.%2.%3").arg(baseName).arg(i).arg(suffix);
		QString fullPath = logDir.filePath(fileName);

		auto l = std::make_unique<QLockFile>(fullPath + ".lock");
		l->setStaleLockTime(0);
		if (l->tryLock())
		{
			lockFile = std::move(l);
			logFile.setFileName(fullPath);
			break;
		}
	}

	qSetMessagePattern("[%{time yyyy-MM-ddTHH:mm:ss.zzz} %{pid} %{type}] %{message}");

	// A crash marker from an earlier run is read before this session's log is
	// opened: after a crash the next run can land on the very same log slot, and
	// opening it would truncate the very tail the report is made of.
	CrashReport::PreviousCrash previousCrash;
	const bool crashedLastRun =
		CrashReport::takePreviousCrash(QFileInfo(logFile).absoluteFilePath(), previousCrash);

	bool logFileOpened = false;
	if (lockFile)
	{
		logFileOpened = logFile.open(QIODevice::WriteOnly | QIODevice::Truncate | QIODevice::Unbuffered |
									 QIODevice::Text);
	}
	else
	{
		qWarning() << "Failed to acquire lock for any log file slot (1-" << max_log_files
				   << "). "
					  "Logging to file disabled.";
	}

	qInfo() << "Log file path:" << QFileInfo(logFile).absoluteFilePath();
	QtMessageHandler oldMessageHandler;
	if (logFileOpened)
	{
		oldMessageHandler = qInstallMessageHandler(customMessageHandler);
	}
	else
	{
		qWarning() << "Failed to open log file.";
	}

	qInfo() << "Launching app...";

	// Mark this session as running and arm the crash handlers before anything
	// that can fail: from here on, an exit that never reaches endSession leaves
	// the marker behind, and the next start turns it into a report.
	CrashReport::beginSession(QFileInfo(logFile).absoluteFilePath(),
							  QStringLiteral(APP_VERSION));

	QApplication a(argc, argv);
	// Fusion plus the dark palette and the stylesheet from the theme module.
	Theme::apply(a);
	QApplication::setOrganizationName("firefly-sylestia");
	QApplication::setOrganizationDomain("com.fireflysylestia");
	QApplication::setApplicationName("VirtualGamePad Extended");
	QApplication::setApplicationVersion(APP_VERSION);
	qInfo() << "Build mode:" << (isPortableMode() ? "PORTABLE" : "INSTALLABLE");
	qInfo() << "Config directory:" << getConfigDir();
	qInfo() << "Data directory:" << getDataDir();

	MainWindow w;
	w.show();
	qInfo() << "Application initialized successfully. Version:" << QApplication::applicationVersion();

	// The report is written automatically; the dialog only decides where it
	// goes. Running before exec() is fine — a message box spins its own loop.
	if (crashedLastRun)
	{
		QString reportPath;
		const QString report = CrashReport::buildReport(previousCrash, &reportPath);

		QMessageBox crashBox;
		crashBox.setIcon(QMessageBox::Warning);
		crashBox.setWindowTitle(QStringLiteral("The app closed unexpectedly"));
		crashBox.setText(reportPath.isEmpty()
						  ? QStringLiteral("The previous run of the app ended unexpectedly, and the "
										 "report could not be saved to disk. The details are below.")
						  : QStringLiteral("The previous run of the app ended unexpectedly.\n\n"
										 "A report with the log tail was saved to:\n%1")
										.arg(QDir::toNativeSeparators(reportPath)));
		crashBox.setInformativeText(QStringLiteral(
			"Send it so the crash can be fixed — the report is already filled "
			"into the issue form."));
		crashBox.setDetailedText(report);

		QPushButton *reportButton =
			crashBox.addButton(QStringLiteral("Report on GitHub"), QMessageBox::AcceptRole);
		// Every button closes the box: clickedButton() is compared after exec()
		// returns, and a role that kept it open would swallow the choice.
		QPushButton *fileButton =
			reportPath.isEmpty()
				? nullptr
				: crashBox.addButton(QStringLiteral("Open report file"), QMessageBox::YesRole);
		QPushButton *closeButton =
			crashBox.addButton(QStringLiteral("Close"), QMessageBox::RejectRole);
		crashBox.setDefaultButton(closeButton);
		crashBox.exec();

		if (crashBox.clickedButton() == reportButton)
			QDesktopServices::openUrl(CrashReport::issueUrl(report));
		else if (fileButton != nullptr && crashBox.clickedButton() == fileButton)
			QDesktopServices::openUrl(QUrl::fromLocalFile(reportPath));
	}

	int result = QApplication::exec();
	qInfo() << "Application shutting down with exit code:" << result;
	// A clean exit: remove the marker so the next launch reports nothing.
	CrashReport::endSession();

	if (logFileOpened)
	{
		qInstallMessageHandler(oldMessageHandler); // Reset message handler
		logFile.close();
		// Set permissions (allow non-admin users to read/write if run as admin)
		//! This does not modify ACLs, so the effectiveness can be limited
		// Needs more testing
		logFile.setPermissions(QFileDevice::ReadOwner | QFileDevice::WriteOwner | QFileDevice::ReadGroup |
							   QFileDevice::WriteGroup | QFileDevice::ReadUser | QFileDevice::WriteUser);
	}

	return result;
}
