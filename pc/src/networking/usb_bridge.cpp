#include "usb_bridge.hpp"

#include <QDebug>
#include <QDir>
#include <QFileInfo>
#include <QProcess>
#include <QRegularExpression>
#include <QStandardPaths>
#include <QTimer>

namespace
{
/** How often `adb devices` is polled while the page is open. */
constexpr int kPollIntervalMs = 2500;
} // namespace

UsbBridge::UsbBridge(QObject *parent) : QObject(parent)
{
	m_pollTimer = new QTimer(this);
	m_pollTimer->setInterval(kPollIntervalMs);
	connect(m_pollTimer, &QTimer::timeout, this, &UsbBridge::poll);
}

UsbBridge::~UsbBridge() = default;

void UsbBridge::start()
{
	locateAdb();
	if (!m_adbAvailable)
	{
		emit changed();
		return;
	}
	if (!m_pollTimer->isActive())
		m_pollTimer->start();
	poll();
}

void UsbBridge::stop()
{
	m_pollTimer->stop();
}

void UsbBridge::locateAdb()
{
	// Prefer adb from the Android SDK when the user has one, because the copy in
	// a package manager is often older than the phone's protocol level.
	QStringList candidates;
	const QString fromPath = QStandardPaths::findExecutable(QStringLiteral("adb"));
	if (!fromPath.isEmpty())
		candidates.append(fromPath);

	const char *const envNames[] = {"ANDROID_HOME", "ANDROID_SDK_ROOT"};
	for (const char *name : envNames)
	{
		const QString root = qEnvironmentVariable(name);
		if (!root.isEmpty())
			candidates.append(QDir(root).filePath(QStringLiteral("platform-tools/adb")));
	}

	if (const QString home = QDir::homePath(); !home.isEmpty())
	{
		candidates.append(home + QStringLiteral("/Android/Sdk/platform-tools/adb"));
		candidates.append(home + QStringLiteral("/Library/Android/sdk/platform-tools/adb"));
	}

	for (const QString &candidate : candidates)
	{
		if (QFileInfo::exists(candidate))
		{
			m_adbPath = candidate;
			m_adbAvailable = true;
			emit logMessage(tr("Using adb at %1").arg(candidate));
			return;
		}
	}

	m_adbAvailable = false;
	m_adbPath.clear();
	emit logMessage(tr("adb was not found. Install Android platform-tools, or add adb to your PATH."));
}

void UsbBridge::run(const QStringList &arguments, const Callback &done)
{
	if (!m_adbAvailable)
	{
		if (done)
			done(-1, tr("adb is not available"));
		return;
	}

	auto *process = new QProcess(this);
	connect(process,
			&QProcess::finished,
			this,
			[process, done](int exitCode, QProcess::ExitStatus)
			{
				const QString standardOutput = QString::fromUtf8(process->readAllStandardOutput());
				const QString standardError = QString::fromUtf8(process->readAllStandardError());
				process->deleteLater();
				if (done)
					done(exitCode, standardOutput.isEmpty() ? standardError : standardOutput);
			});
	connect(process,
			&QProcess::errorOccurred,
			this,
			[this, process, done](QProcess::ProcessError)
			{
				const QString message = process->errorString();
				process->deleteLater();
				emit logMessage(tr("adb failed to start: %1").arg(message));
				if (done)
					done(-1, message);
			});

	process->setProgram(m_adbPath);
	process->setArguments(arguments);
	process->start();
}

void UsbBridge::poll()
{
	if (m_busy)
		return;

	m_busy = true;
	run({QStringLiteral("devices"), QStringLiteral("-l")},
		[this](int exitCode, const QString &output)
		{
			m_busy = false;
			if (exitCode != 0)
			{
				emit logMessage(tr("adb devices reported an error: %1").arg(output.trimmed()));
				return;
			}
			handleDevicesOutput(output);
		});
}

void UsbBridge::handleDevicesOutput(const QString &output)
{
	QList<UsbDevice> found;
	const QStringList lines = output.split(QLatin1Char('\n'), Qt::SkipEmptyParts);
	for (const QString &line : lines)
	{
		const QString trimmed = line.trimmed();
		if (trimmed.isEmpty() || trimmed.startsWith(QStringLiteral("List of devices")))
			continue;

		const QStringList parts = trimmed.split(QRegularExpression(QStringLiteral("\\s+")),
											   Qt::SkipEmptyParts);
		if (parts.size() < 2)
			continue;

		UsbDevice device;
		device.serial = parts.at(0);
		device.state = parts.at(1);
		for (const QString &part : parts)
		{
			if (part.startsWith(QStringLiteral("model:")))
				device.model = part.mid(6).replace(QLatin1Char('_'), QLatin1Char(' '));
		}
		found.append(device);
	}

	const bool changedList = found.size() != m_devices.size();
	m_devices = found;

	if (changedList)
	{
		if (m_devices.isEmpty())
			emit logMessage(tr("Waiting for a phone. Enable USB debugging, then plug it in."));
		for (const UsbDevice &device : m_devices)
			emit logMessage(tr("Found %1 (%2) - %3")
								.arg(device.model.isEmpty() ? device.serial : device.model,
									 device.serial,
									 device.state));
		emit changed();
	}

	// Auto-forward as soon as exactly one ready device is present.
	if (m_autoConnect && m_forwardedPort == 0 && m_devices.size() == 1 &&
		m_devices.first().state == QStringLiteral("device"))
	{
		setForward(m_desiredPort);
	}
}

void UsbBridge::setForward(quint16 port)
{
	if (!m_adbAvailable)
	{
		emit logMessage(tr("Install adb before using the USB connection."));
		return;
	}
	if (port == 0)
	{
		emit logMessage(tr("Choose a port before setting up the USB link."));
		return;
	}

	m_busy = true;
	const QString mapping = QStringLiteral("tcp:%1").arg(port);
	run({QStringLiteral("reverse"), mapping, mapping},
		[this, port](int exitCode, const QString &output)
		{
			m_busy = false;
			if (exitCode == 0)
			{
				m_forwardedPort = port;
				emit logMessage(
					tr("USB link ready. On the phone choose USB and connect to 127.0.0.1:%1").arg(port));
				emit changed();
			}
			else
			{
				emit logMessage(tr("Could not set up the USB link: %1").arg(output.trimmed()));
			}
		});
}

void UsbBridge::clearForward()
{
	if (m_forwardedPort == 0)
		return;

	const QString mapping = QStringLiteral("tcp:%1").arg(m_forwardedPort);
	run({QStringLiteral("reverse"), QStringLiteral("--remove"), mapping},
		[this](int exitCode, const QString &output)
		{
			if (exitCode == 0)
			{
				emit logMessage(tr("USB link removed."));
			}
			else
			{
				emit logMessage(tr("Could not remove the USB link: %1").arg(output.trimmed()));
			}
			m_forwardedPort = 0;
			emit changed();
		});
}
