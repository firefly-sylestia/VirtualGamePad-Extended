#pragma once

#include "executor.hpp"

#include <QByteArray>
#include <QDialog>
#include <QTcpServer>
#include <QTcpSocket>
#include <QTime>
#include <QUdpSocket>
#include <memory>

class QTimer;

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

	/**
	 * Let go of everything the backend is holding.
	 *
	 * Called when the stream from the phone ends. A key or mouse button left down
	 * is not something the user can undo from the phone: a held left button turns
	 * every later click into a drag, and a held Shift or Ctrl changes every
	 * keystroke, which is what made the PC look frozen until it was restarted.
	 */
	void releaseHeldInput();

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

	/** Set once the current connection has delivered a packet, for the log. */
	bool m_packetSeen = false;

	/**
	 * How long a connected device may go quiet before its input is let go.
	 *
	 * The phone sends at its own polling interval for as long as it is connected,
	 * even while nothing is being touched, so silence for this long means the link
	 * or the app on the other end is gone.
	 */
	static constexpr int HOLD_TIMEOUT_MS = 1500;

	/** Fires when the packet stream stops, to release whatever is held. */
	QTimer *m_holdTimeoutTimer = nullptr;

	/**
	 * Answers a phone looking for this PC, so a tap on Find replaces a typed IP.
	 *
	 * The phone broadcasts a short probe on the discovery port; this socket
	 * replies with the app name and the TCP port the server is listening on. The
	 * phone then uses the address the reply came from, so no address has to be
	 * typed or carried in the packet. Bound only while the TCP server is up, so a
	 * stopped server is not advertised.
	 */
	QUdpSocket *m_discoverySocket = nullptr;

	/** Reply to one discovery probe with this server's name and port. */
	void answerDiscoveryProbe();

	/**
	 * Send one rumble to the connected phone.
	 *
	 * Runs on the server's own thread: a game's rumble arrives on a ViGEm worker
	 * thread, which must not touch the socket, so it is bounced here through a
	 * queued call. Writes a single self-delimiting line, because the PC-to-phone
	 * direction carries no Colfer frames and this is all it carries today. A
	 * phone that never reads it is unaffected — the write is simply buffered and
	 * dropped with the socket.
	 */
	void sendRumbleToPhone(quint8 large, quint8 small);
};
