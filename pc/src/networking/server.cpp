#include "server.hpp"

#include "../../third-party-libs/QR-Code-generator/cpp/qrcodegen.hpp"
#include "../settings/settings_singleton.hpp"
#include "controller_test_state.hpp"
#include "input_backend_status.hpp"
#include "ui_server.h"

#ifdef _WIN32
#include "../platform/windows/vigemBusInstaller.hpp"
#endif

#include <QByteArray>
#include <QDataStream>
#include <QHostAddress>
#include <QList>
#include <QMessageBox>
#include <QNetworkInterface>
#include <QShowEvent>
#include <QThread>
#include <QTimer>
#include <algorithm>

/**
 * @brief Creates a QR code from a string
 *
 * This function uses [Nayuki's QR Code Generator
 * library](https://github.com/nayuki/QR-Code-generator/tree/master/cpp).\n Guided by:
 * https://stackoverflow.com/a/39951669/8659747
 * @param data The string to encode
 * @param border The padding around the QR code (in pixels)
 * @param scalingFactor The scaling factor for the final image
 * @return QImage
 */
QImage createQR(const QString &data, const int border = 1, const uint scalingFactor = 10)
{
	QByteArray dataUtf8 = data.toUtf8();
	const char *str = dataUtf8.constData();
	qrcodegen::QrCode qr = qrcodegen::QrCode::encodeText(str, qrcodegen::QrCode::Ecc::HIGH);
	const int s = qr.getSize(); // s is the length of a side of the QR code
	qDebug() << "QR code generated with size: " << s;
	QImage image(s + 2 * border, s + 2 * border, QImage::Format_Mono);
	image.setColor(1, QColor("black").rgb());
	image.setColor(0, QColor("white").rgb());
	image.fill(0); // Whitewash
	for (int y = 0; y < s; y++)
	{
		for (int x = 0; x < s; x++)
		{
			const int color = qr.getModule(x, y); // 0 for white, 1 for black
			image.setPixel(x + border, y + border, color);
		}
	}
	return image.scaled(image.size() * scalingFactor);
}

namespace
{
/**
 * @brief An address a phone might reach the PC on, best candidate first.
 */
struct AddressCandidate
{
	QHostAddress address;
	QString interfaceName;
	int rank = 99;
};

/**
 * @brief Score an address so the most likely reachable one is shown first.
 *
 * The QR code that gets scanned is the one currently selected, so an address
 * from a VPN or a container bridge must not sort above the plain Wi-Fi one.
 */
int rankForAddress(quint32 ip, const QString &interfaceName)
{
	const QString lower = interfaceName.toLower();
	static const char *const virtualHints[] = {"docker", "veth",  "virbr", "vmnet",  "tun",
													  "tap",	  "tailscale", "wg", "zte",
													  "wsl",	  "br-",   "zt"};
	for (const char *hint : virtualHints)
	{
		if (lower.contains(QString::fromUtf8(hint)))
			return 40;
	}

	const quint32 first = (ip >> 24) & 0xFF;
	const quint32 second = (ip >> 16) & 0xFF;

	if (first == 192 && second == 168)
		return 0;
	if (first == 172 && second >= 16 && second <= 31)
		return 1;
	if (first == 10)
		return 2;
	if (first == 100 && second >= 64 && second <= 127)
		return 30; // Carrier-grade NAT, rarely reachable from the phone.
	return 10;
}

/** Addresses worth offering to a phone, best first. */
QList<AddressCandidate> rankedAddresses()
{
	QList<AddressCandidate> candidates;
	const QList<QNetworkInterface> interfaces = QNetworkInterface::allInterfaces();

	for (const QNetworkInterface &iface : interfaces)
	{
		const QString name = iface.humanReadableName();
		for (const QNetworkAddressEntry &entry : iface.addressEntries())
		{
			const QHostAddress address = entry.ip();
			if (address.protocol() != QAbstractSocket::IPv4Protocol)
				continue;
			if (address.isLoopback() || address.isNull() || address.isMulticast() ||
				address.isLinkLocal())
				continue;

			candidates.append(
				{address, name, rankForAddress(address.toIPv4Address(), name)});
		}
	}

	std::sort(candidates.begin(),
			  candidates.end(),
			  [](const AddressCandidate &a, const AddressCandidate &b)
			  {
				  return a.rank < b.rank;
			  });
	return candidates;
}
} // namespace

Server::Server(QWidget *parent) : QWidget(parent), ui(new Ui::Server)
{
	qInfo() << "Initializing TCP server";

	ui->setupUi(this);

	// The port is opened in showEvent(), so simply having this page in the shell
	// does not hold a port the user never asked for.
	ui->IPList->viewport()->setAutoFillBackground(false);
	// delete this when stop button is clicked
	connect(ui->stopButton, &QPushButton::clicked, this, &Server::destroyServer);
	connect(ui->installVigemBusButton,
			&QPushButton::clicked,
			this,
			&Server::install_vigem_bus);
	clientConnection = nullptr;
	tcpServer = new QTcpServer(this);
	isGamepadConnected = false;

	connect(&InputBackendStatus::instance(),
			&InputBackendStatus::changed,
			this,
			&Server::refreshBackendStatus);

	// A phone that stops sending — out of range, killed, asleep, or its app closed —
	// must not leave anything held on this PC, so the stream going quiet releases
	// whatever the backend is holding.
	m_holdTimeoutTimer = new QTimer(this);
	m_holdTimeoutTimer->setSingleShot(true);
	m_holdTimeoutTimer->setInterval(HOLD_TIMEOUT_MS);
	connect(m_holdTimeoutTimer, &QTimer::timeout, this, &Server::releaseHeldInput);

	// A device chosen in Preferences has to take effect there and then: the pad is
	// part of the backend, not of the phone connection, so waiting for the next
	// visit to this page left the user with a controller that never showed up.
	connect(&SettingsSingleton::instance(),
			&SettingsSingleton::inputBackendChanged,
			this,
			[this]() { buildExecutor(); });

	// Build the configured backend now, not the first time this page is shown.
	// The virtual gamepad is a device the rest of the PC can see, so it has to be
	// plugged in as soon as the app runs rather than only once the user happens to
	// find the server page.
	buildExecutor();

	qDebug() << "Server widget initialized";
}

/**
 * Build the configured executor, without ever letting it take the app down.
 *
 * A virtual gamepad that cannot be created (no /dev/uinput access on Linux, or
 * a refused injection API on Windows) used to throw out of this constructor.
 * Now it falls back to keyboard and mouse, and the reason is shown on the page.
 */
void Server::buildExecutor()
{
	// The previous backend lets go of whatever it is holding before it is torn
	// down: nothing may outlive the backend that pressed it.
	releaseHeldInput();

	// The previous backend is about to go away, and the virtual device it holds
	// open with it, so the UI stops calling it active before its replacement is
	// created.
	executor.reset();
	InputBackendStatus::instance().clear();
	// A new backend deserves its own warning: the old one's may not apply.
	m_injectionWarningShown = false;

	const ExecutorType type = SettingsSingleton::instance().executorType();
	try
	{
		switch (type)
		{
		case ExecutorType::GamepadExecutor:
			executor = std::make_unique<GamepadExecutor>();
			qInfo() << "GamepadExecutor initialized successfully";
			break;
		case ExecutorType::KeyboardMouseExecutor:
			executor = std::make_unique<KeyboardMouseExecutor>();
			qInfo() << "KeyboardMouseExecutor initialized successfully";
			break;
		case ExecutorType::GamepadAndKeyboardMouse:
			executor = std::make_unique<CombinedExecutor>();
			qInfo() << "CombinedExecutor initialized successfully";
			break;
		default:
			qCritical() << "Unknown executor type";
		}
	}
	catch (const std::exception &ex)
	{
		qCritical() << "Could not start the configured input backend:" << ex.what();
		m_listenError = tr("The configured input backend could not start: %1")
							.arg(QString::fromUtf8(ex.what()));
	}

	if (executor == nullptr)
	{
		// Never leave the app with no way to send input.
		executor = std::make_unique<KeyboardMouseExecutor>();
		qWarning() << "Falling back to keyboard and mouse input";
	}

	m_executorType = static_cast<int>(type);
	m_virtualGamepadType = static_cast<int>(SettingsSingleton::instance().virtualGamepadType());
	refreshBackendStatus();
}

void Server::refreshBackendStatus()
{
	if (m_statusBase.isEmpty())
		return;

	const auto status = InputBackendStatus::instance().snapshot();
	QString line = tr("\n\n**Input:** %1").arg(status.backendName);
	if (status.available)
		line += tr(" — ready (%1).").arg(status.detail);
	else
		line += tr(" — **not available**: %1").arg(status.detail);

	ui->statusLabel->setText(m_statusBase + line);

	// When the virtual pad could not be created and this build carries the
	// driver installer, offering to run it turns a dead end into one click.
	// There is nothing to offer on the platforms whose backends need no driver.
#ifdef _WIN32
	ui->installVigemBusButton->setVisible(!status.available &&
										  !ViGEmBusInstaller::bundledInstallerPath().isEmpty());
#else
	ui->installVigemBusButton->setVisible(false);
#endif
}

void Server::install_vigem_bus()
{
#ifdef _WIN32
	ui->installVigemBusButton->setEnabled(false);
	const QString originalText = ui->installVigemBusButton->text();
	ui->installVigemBusButton->setText(tr("Installing ViGEmBus..."));

	QString error;
	if (!ViGEmBusInstaller::run(&error))
	{
		QMessageBox::warning(this, tr("ViGEmBus"), error);
	}
	else if (!ViGEmBusInstaller::isInstalled())
	{
		// The installer can exit cleanly with the driver still not usable until
		// Windows restarts, and its own updater only takes effect on a second
		// run. Saying so is better than leaving the user to wonder.
		QMessageBox::information(this,
								 tr("ViGEmBus"),
								 tr("The driver was installed. Windows may need to restart before it "
									"can be used: run the installer once more, or restart this app or "
									"the PC, if the virtual gamepad is still unavailable."));
	}

	// Rebuild either way: when the driver is live this creates the pad now, and
	// when it is not the status line is refreshed with the current truth.
	buildExecutor();

	ui->installVigemBusButton->setText(originalText);
	ui->installVigemBusButton->setEnabled(true);
#else
	// The button is never shown where the input backend needs no driver.
#endif
}

void Server::showEvent(QShowEvent *event)
{
	QWidget::showEvent(event);

	// Preferences may have changed while this page was hidden. The virtual device
	// counts as well: it is baked into the pad when the executor creates it, so a
	// new choice only takes effect once the executor is rebuilt.
	if (m_executorType != static_cast<int>(SettingsSingleton::instance().executorType()) ||
		m_virtualGamepadType !=
			static_cast<int>(SettingsSingleton::instance().virtualGamepadType()))
		buildExecutor();

	const int configuredPort = SettingsSingleton::instance().port();
	if (m_listenPort != configuredPort && !isGamepadConnected)
	{
		// A different port was chosen: release the old one so listen() can move.
		tcpServer->close();
		m_listenPort = -1;
	}

	if (!isListening() && !isGamepadConnected)
		initServer();

	refreshBackendStatus();
}

Server::~Server()
{
	// IMPORTANT: clientConnection should not be accessed when the server is closed
	if (isGamepadConnected && clientConnection != nullptr)
	{
		clientConnection->close(); // Close clientConnection gracefully
		clientConnection = nullptr;
		isGamepadConnected = false;
	}
	tcpServer->close(); // And then close the server
	// Drop the input backend here, while this page still exists, so the virtual
	// pad unplugs and the UI stops reporting it as the active backend.
	releaseHeldInput();
	executor.reset();
	InputBackendStatus::instance().clear();
	ControllerTestState::instance().setConnected(false);
	qInfo() << "Server stopped.";
	tcpServer->deleteLater();
	delete ui;
}

void Server::initServer()
{
	if (isListening())
		return;

	qInfo() << "Starting TCP server initialization";

	// Keep a real backlog. A backlog of 0 lets the kernel drop a SYN that
	// arrives while the server is between accept() calls (for example during
	// the pause/resume window when a device reconnects), which showed up as
	// "sometimes the phone just will not connect".
	tcpServer->setListenBacklogSize(8);
	auto port = SettingsSingleton::instance().port();

	if (!tcpServer->listen(QHostAddress::AnyIPv4, port))
	{
		// Stay on the page and say what happened. Closing the page here left the
		// user staring at an empty screen with no way to retry.
		m_listenError = tcpServer->errorString();
		m_statusBase = tr("**The server could not start.**\n\n"
						  "Port `%1`: %2\n\n"
						  "Another program may already be using that port. Choose a different "
						  "port under Preferences, then press Start server again, or stop the "
						  "other server first.")
						   .arg(port)
						   .arg(m_listenError);
		ui->statusLabel->setText(m_statusBase);
		refreshBackendStatus();
		qWarning() << "Unable to start the server on port" << port << ":" << m_listenError;
		return;
	}

	m_listenError.clear();
	m_listenPort = tcpServer->serverPort();
	// The address list and QR codes are rebuilt for this run, never appended to
	// a previous one.
	ui->IPList->clear();
	while (ui->QRViewer->count() > 0)
	{
		auto *page = ui->QRViewer->widget(0);
		ui->QRViewer->removeWidget(page);
		page->deleteLater();
	}

	QString message = tr("**Warning:** The server stops when you stop it or close the app.\n\n");
	message += tr("The server is running on\n\nPort: `%1`\n\nAt the following IP Address(es):\n\n")
				   .arg(tcpServer->serverPort());
	const QList<AddressCandidate> candidates = rankedAddresses();
	for (const AddressCandidate &candidate : candidates)
	{
		const QString address = candidate.address.toString();
		ui->IPList->addItem(candidate.rank == 0
								? tr("%1   (recommended)").arg(address)
								: tr("%1   (%2)").arg(address, candidate.interfaceName));

		auto *QRWidget = new QLabel();
		QRWidget->setAlignment(Qt::AlignCenter);
		QRWidget->setPixmap(QPixmap::fromImage(
			createQR(QStringLiteral("%1:%2").arg(address).arg(tcpServer->serverPort()))));
		ui->QRViewer->addWidget(QRWidget);
	}
	if (ui->IPList->count() > 0)
	{
		// Select the first row
		ui->IPList->setCurrentRow(0);
		// And grab the focus
		ui->IPList->setFocus(Qt::OtherFocusReason);
	}
	else
	{
		message += tr("No local IPv4 address was found, so there is no QR code to scan. "
					  "Connect this PC to the same Wi-Fi network as your phone, or use the "
					  "USB connection instead.");
		qWarning() << "No usable IPv4 address found; server started without a QR code";
	}
	m_statusBase = message;
	ui->statusLabel->setText(m_statusBase);
	refreshBackendStatus();
	connect(ui->IPList,
			&QListWidget::currentItemChanged,
			this,
			[this](QListWidgetItem *current, QListWidgetItem *)
			{
				if (current != nullptr)
				{
					ui->QRViewer->setCurrentIndex(ui->IPList->row(current));
				}
			});
	connect(tcpServer, &QTcpServer::newConnection, this, &Server::handleConnection);
	qInfo() << "Server started successfully on port:" << tcpServer->serverPort();
}

void Server::handleConnection()
{
	// Whatever the previous device was holding is let go first: its releases would
	// have arrived on the socket being dropped here, so nothing may stay down.
	releaseHeldInput();

	// The byte buffer belongs to the stream, not to the page: bytes left by the
	// previous device — half a packet when it vanished — would sit at the head of
	// this connection's stream and make every following parse fail.
	dataBuffer.clear();

	// A phone that reconnects can leave the previous socket half-open. Adopt the
	// new connection and drop the old one, so the device always ends up on the
	// socket it is actually writing to instead of on a stale one that only looks
	// connected.
	if (clientConnection != nullptr)
	{
		qInfo() << "Replacing the previous client connection";
		clientConnection->disconnect(this);
		clientConnection->abort();
		clientConnection->deleteLater();
		clientConnection = nullptr;
	}

	clientConnection = tcpServer->nextPendingConnection();
	if (clientConnection == nullptr)
		return; // Nothing to adopt; the next newConnection will try again.
	// disable Nagle's algorithm to avoid delay and bunching of small packages
	clientConnection->setSocketOption(QAbstractSocket::LowDelayOption, 1);
	// Detect a phone that walked out of range or went to sleep much sooner than
	// the default keep-alive timers would.
	clientConnection->setSocketOption(QAbstractSocket::KeepAliveOption, 1);
	isGamepadConnected = true;
	// This connection starts with a clean slate, so the first packet it delivers
	// is announced in the log.
	m_packetSeen = false;
	// Tell every page that watches the connection state, so the control test and
	// the home page stop saying "Not connected" the moment a device arrives.
	ControllerTestState::instance().setConnected(true);
	QString connectionMessage;
	connectionMessage =
		tr("Connected to %1 at `%2 : %3`")
			.arg(clientConnection->peerName().isEmpty() ? "Unknown device" : clientConnection->peerName(),
				 clientConnection->peerAddress().toString(),
				 QString::number(clientConnection->peerPort()));
	qInfo().noquote() << connectionMessage;
	ui->clientLabel->setText(connectionMessage);
	// Keep accepting. Pausing here used to strand a reconnecting phone: its
	// connect() completed into the listen backlog, so the app moved on and
	// started sending, while the PC still held the previous socket and never
	// read the new one. Nothing ever registered until the app was restarted.
	// Staying open lets the branch at the top of this function adopt the next
	// connection as it arrives.
	connect(clientConnection, &QAbstractSocket::disconnected, clientConnection, &QObject::deleteLater);
	connect(clientConnection,
			&QAbstractSocket::disconnected,
			this,
			[this]()
			{
				ui->clientLabel->setText(tr("No device connected"));
				qInfo() << "Device disconnected.";
				qDebug() << "Average Request Interval" << averageRequestInterval
						 << "ms, Request Count:" << requestCount;
				isGamepadConnected = false;
				// The socket schedules its own deletion above, so this member must
				// stop pointing at it right now. It used to keep the dangling
				// pointer, and the next connect — or a Stop press — called methods
				// on freed memory, which killed the whole app at the moment the
				// phone reconnected.
				clientConnection = nullptr;
				// The stream is over; what is left of it would corrupt the next one.
				dataBuffer.clear();
				ControllerTestState::instance().setConnected(false);
				// The device is gone, so its holds go with it rather than staying
				// pressed on this PC until someone notices.
				m_holdTimeoutTimer->stop();
				releaseHeldInput();
			});
	connect(clientConnection, &QAbstractSocket::readyRead, this, &Server::serveClient);

	qInfo() << "New client connection received";
}

void Server::serveClient()
{
	// A stray readyRead from a socket that has already been dropped would
	// otherwise be read through a member that no longer points anywhere.
	if (clientConnection == nullptr)
		return;

#ifdef QT_DEBUG
	qDebug() << "Received: " << clientConnection->bytesAvailable() << "bytes";
#endif

	// Append new data to our buffer
	dataBuffer.append(clientConnection->readAll());

#ifdef QT_DEBUG
	qDebug() << "Buffer contents: " << dataBuffer;
#endif

	QTime currentTime = QTime::currentTime();

	// Process as many complete packets as we have in the buffer
	while (!dataBuffer.isEmpty())
	{
		ParseResult result = parse_gamepad_state(dataBuffer.constData(), dataBuffer.size());

		if (!result.success)
		{				switch (result.failure_reason)
				{
					using enum ParseResult::FailureReason;
				[[likely]] case IncompleteData:
					// Wait for more data
					break;
				case SchemaMismatch:
					qWarning() << "Schema mismatch detected in client data";
					// A failed parse consumes nothing (`bytes_consumed` is 0 here),
					// so removing that many bytes used to leave the offending bytes
					// at the head of the buffer forever: every later packet failed
					// against them and the connection delivered no input at all.
					// The stream restarts from the next complete packet instead.
					dataBuffer.clear();
					break;
				case DataTooLarge:
					qWarning() << "Client sent data that is too large to process";
					// Same reasoning as SchemaMismatch: the boundary of the bad
					// record cannot be found, so the buffer is dropped whole.
					dataBuffer.clear();
					break;
				[[unlikely]] default:
					qWarning() << "Unknown error occurred while parsing client data";
					dataBuffer.clear(); // Clear buffer to avoid cascading errors
					break;
				}
				break; // Exit the processing loop
		}

		// Process the gamepad reading
		requestCount++;
		if (!m_packetSeen)
		{
			// Said out loud once per connection: when a phone is connected but the
			// PC shows nothing, this line is what tells the two apart.
			m_packetSeen = true;
			qInfo().noquote() << "Input is arriving from the connected device";
		}
		// The stream is alive, so nothing that is held gets let go.
		m_holdTimeoutTimer->start();
		// Calculate performance metrics
		if (lastRequestTime.isValid())
		{
			int elapsed = lastRequestTime.msecsTo(currentTime);
			// Update average request interval, using running average
			averageRequestInterval += (elapsed - averageRequestInterval) / requestCount;
		}
		lastRequestTime = currentTime;

		// Feed the live views (control test, layouts preview, home page status)
		// before handing the packet to the input backend.
		ControllerTestState::instance().updateFromReading(result.reading);

		if (!executor->inject_gamepad_state(result.reading) && !m_injectionWarningShown)
		{
			m_injectionWarningShown = true;
			const auto status = InputBackendStatus::instance().snapshot();
			qWarning() << "Input backend cannot deliver input:" << status.detail;
			ui->clientLabel->setText(
				tr("Connected, but **%1** cannot send input: %2")
					.arg(status.backendName, status.detail));
		}

		// Remove the processed data from the buffer
		dataBuffer.remove(0, result.bytes_consumed);

#ifdef QT_DEBUG
		qDebug() << "Consumed" << result.bytes_consumed
				 << "bytes, remaining buffer size:" << dataBuffer.size();
#endif
	}
}

void Server::releaseHeldInput()
{
	if (executor != nullptr)
		executor->releaseAll();
}

void Server::destroyServer()
{
	// Release the port and everything the backend holds, then go home. A later
	// visit builds a fresh page, so stopping and starting again works.
	ControllerTestState::instance().setConnected(false);
	if (clientConnection != nullptr)
	{
		clientConnection->disconnect(this);
		clientConnection->abort();
		clientConnection->deleteLater();
		clientConnection = nullptr;
	}
	isGamepadConnected = false;
	tcpServer->close();
	m_listenPort = -1;
	emit navigateBack();
	this->deleteLater();
}
