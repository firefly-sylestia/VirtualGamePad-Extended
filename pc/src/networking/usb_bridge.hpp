/**
 * @file usb_bridge.hpp
 * @brief Connects the phone over the USB cable using adb port forwarding.
 *
 * When USB debugging is enabled, `adb` can mirror a port on the PC onto the
 * phone's own localhost. The phone then reaches the server at 127.0.0.1 with no
 * Wi-Fi involved at all, which avoids router isolation, guest networks and
 * firewall prompts. This class watches for a device and sets that forwarding up
 * automatically.
 */
#pragma once

#include <QList>
#include <QObject>
#include <QString>
#include <QStringList>
#include <functional>

class QProcess;
class QTimer;

/** A device as reported by `adb devices -l`. */
struct UsbDevice
{
	QString serial;
	QString state; // "device", "unauthorized", "offline", ...
	QString model;
};

class UsbBridge : public QObject
{
	Q_OBJECT

  public:
	explicit UsbBridge(QObject *parent = nullptr);
	~UsbBridge() override;

	/** Begin watching for devices. Safe to call more than once. */
	void start();
	void stop();

	bool adbAvailable() const { return m_adbAvailable; }
	QString adbPath() const { return m_adbPath; }
	QList<UsbDevice> devices() const { return m_devices; }
	quint16 forwardedPort() const { return m_forwardedPort; }

	/** Set to true to forward automatically when exactly one device shows up. */
	void setAutoConnect(bool enabled) { m_autoConnect = enabled; }
	bool autoConnect() const { return m_autoConnect; }

	/** The port the user wants mirrored onto the phone. */
	void setDesiredPort(quint16 port) { m_desiredPort = port; }

  public slots:
	/** Mirror @p port onto the phone's localhost. */
	void setForward(quint16 port);
	/** Remove the mirror. */
	void clearForward();

  signals:
	/** Device list, adb availability or the forward state changed. */
	void changed();
	/** A human-readable line for the page log. */
	void logMessage(const QString &message);

  private slots:
	void poll();

  private:
	using Callback = std::function<void(int, const QString &)>;

	void locateAdb();
	void run(const QStringList &arguments, const Callback &done);
	void handleDevicesOutput(const QString &output);

	QString m_adbPath;
	bool m_adbAvailable = false;
	bool m_autoConnect = true;
	quint16 m_desiredPort = 30000;
	quint16 m_forwardedPort = 0;
	QList<UsbDevice> m_devices;
	QTimer *m_pollTimer = nullptr;
	bool m_busy = false;
};
