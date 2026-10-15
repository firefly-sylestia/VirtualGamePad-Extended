#pragma once

#include <QWidget>

class QCheckBox;
class QLabel;
class QListWidget;
class QPushButton;
class QSpinBox;
class UsbBridge;

/**
 * @brief Connect the phone over the USB cable instead of Wi-Fi.
 *
 * Watches for an Android device with `adb`, forwards the server port onto the
 * phone's localhost, and tells the user what to tap on the phone.
 */
class UsbPage final : public QWidget
{
	Q_OBJECT

  public:
	explicit UsbPage(QWidget *parent = nullptr);
	~UsbPage() override;

  signals:
	void navigateBack();
	/** Ask the shell to open the server page, e.g. when the phone link is ready. */
	void startServerRequested();

  private slots:
	void refresh();
	void appendLog(const QString &message);
	void applyPort(int port);
	void setUpLink();
	void removeLink();

  private:
	UsbBridge *m_bridge = nullptr;
	QLabel *m_status = nullptr;
	QLabel *m_adbPath = nullptr;
	QListWidget *m_devices = nullptr;
	QSpinBox *m_port = nullptr;
	QCheckBox *m_auto = nullptr;
	QPushButton *m_setUp = nullptr;
	QPushButton *m_remove = nullptr;
	QLabel *m_hint = nullptr;
	QListWidget *m_log = nullptr;
};
