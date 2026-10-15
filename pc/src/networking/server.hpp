#pragma once

#include "executor.hpp"

#include <QByteArray>
#include <QDialog>
#include <QTcpServer>
#include <QTcpSocket>
#include <QTime>
#include <memory>

namespace Ui
{
class Server;
}

/**
 * @brief The server page: opens the port, shows the QR code, and feeds input on.
 *
 * The page stays alive while you browse the rest of the app, so the server keeps
 * running and coming back shows the live state instead of trying to open the
 * same port a second time. The port opens the first time the page is shown and
 * stays open until the user stops the server, which is what the Stop button
 * does: it releases the port and returns to the home page.
 */
class Server : public QWidget
{
	Q_OBJECT

  signals:
	void navigateBack();

  public:
	explicit Server(QWidget *parent = nullptr);
	~Server() override;

	/** True while the port is open for connections. */
	bool isListening() const { return tcpServer != nullptr && tcpServer->isListening(); }

	/** True while a device is connected. */
	bool hasClient() const { return isGamepadConnected; }

	QTcpServer *tcpServer = nullptr;

  protected:
	/** (Re)opens the port and re-reads the settings when the page is shown. */
	void showEvent(QShowEvent *event) override;

  private slots:
	void handleConnection();
	void destroyServer();

	/** Run the bundled ViGEmBus installer, then rebuild the input backend. */
	void install_vigem_bus();

  private:
	void initServer();
	void serveClient();

	/** Build the executor named in the settings, and report what it can do. */
	void buildExecutor();

	/** Fill in the "Input" line: which backend, and whether games will see it. */
	void refreshBackendStatus();

	Ui::Server *ui;
	QTcpSocket *clientConnection;
	bool isGamepadConnected;
	QTime lastRequestTime;
	double averageRequestInterval = 0.0;
	uint_fast32_t requestCount = 0;
	QByteArray dataBuffer; // Buffer to store incoming data
	std::unique_ptr<ExecutorInterface> executor = nullptr;

	/** Executor type the current executor was built for, and the port it opened. */
	int m_executorType = -1;
	int m_listenPort = -1;

	/**
	 * Virtual device the current executor was built for.
	 *
	 * The pad exists only once it is created, so switching between an Xbox 360
	 * and a DualShock 4 needs the executor rebuilt; tracking it here is what lets
	 * that happen without restarting the app.
	 */
	int m_virtualGamepadType = -1;

	/** Set when the port could not be opened, so the page can explain why. */
	QString m_listenError;

	/** The status text without the live "Input:" line, which is appended to it. */
	QString m_statusBase;

	/** Set after the first packet the backend could not deliver, to warn once. */
	bool m_injectionWarningShown = false;
};
