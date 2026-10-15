#include "server.hpp"

#include "../../third-party-libs/QR-Code-generator/cpp/qrcodegen.hpp"
#include "../settings/settings_singleton.hpp"
#include "ui_server.h"

#include <QByteArray>
#include <QDataStream>
#include <QHostAddress>
#include <QList>
#include <QMessageBox>
#include <QNetworkInterface>
#include <QThread>
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
	ui->IPList->viewport()->setAutoFillBackground(false);
	// delete this when stop button is clicked
	connect(ui->stopButton, &QPushButton::clicked, this, &Server::destroyServer);
	clientConnection = nullptr;
	tcpServer = new QTcpServer(this);
	isGamepadConnected = false;

	// Initialize the executor with try-catch for better error handling
	switch (SettingsSingleton::instance().executorType())
	{
	case ExecutorType::GamepadExecutor:
		executor = std::make_unique<GamepadExecutor>();
		qInfo() << "GamepadExecutor initialized successfully";
		break;
	case ExecutorType::KeyboardMouseExecutor:
		executor = std::make_unique<KeyboardMouseExecutor>();
		qInfo() << "KeyboardMouseExecutor initialized successfully";
		break;
	default:
		qCritical() << "Unknown executor type";
	}

	initServer();

	qDebug() << "Server widget initialized";
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
	qInfo() << "Server stopped.";
	tcpServer->deleteLater();
	delete ui;
}

void Server::initServer()
{
	qInfo() << "Starting TCP server initialization";

	// Keep a real backlog. A backlog of 0 lets the kernel drop a SYN that
	// arrives while the server is between accept() calls (for example during
	// the pause/resume window when a device reconnects), which showed up as
	// "sometimes the phone just will not connect".
	tcpServer->setListenBacklogSize(8);
	auto port = SettingsSingleton::instance().port();

	if (!tcpServer->listen(QHostAddress::AnyIPv4, port))
	{
		QMessageBox::critical(this,
							  tr("VGamepad Server"),
							  tr("Unable to start the server: %1.").arg(tcpServer->errorString()));
		close(); // Close the error dialog
		tcpServer->close();
		return;
	}
	QString message = tr("**Warning:** The server will stop if you close this window.\n\n");
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
	ui->statusLabel->setText(message);
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
	// A phone that reconnects can leave the previous socket half-open. Adopt the
	// new connection and drop the old one instead of leaking it, otherwise the
	// stale socket keeps the server paused and the app talks into a dead pipe.
	if (clientConnection != nullptr)
	{
		qInfo() << "Replacing the previous client connection";
		clientConnection->disconnect(this);
		clientConnection->abort();
		clientConnection->deleteLater();
		clientConnection = nullptr;
	}

	clientConnection = tcpServer->nextPendingConnection();
	// disable Nagle's algorithm to avoid delay and bunching of small packages
	clientConnection->setSocketOption(QAbstractSocket::LowDelayOption, 1);
	// Detect a phone that walked out of range or went to sleep much sooner than
	// the default keep-alive timers would.
	clientConnection->setSocketOption(QAbstractSocket::KeepAliveOption, 1);
	isGamepadConnected = true;
	QString connectionMessage;
	connectionMessage =
		tr("Connected to %1 at `%2 : %3`")
			.arg(clientConnection->peerName().isEmpty() ? "Unknown device" : clientConnection->peerName(),
				 clientConnection->peerAddress().toString(),
				 QString::number(clientConnection->peerPort()));
	qInfo().noquote() << connectionMessage;
	ui->clientLabel->setText(connectionMessage);
	// Stop accepting extra devices while one is connected, but keep the backlog
	// above so a retry is queued rather than refused.
	tcpServer->pauseAccepting();
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
				tcpServer->resumeAccepting();
			});
	connect(clientConnection, &QAbstractSocket::readyRead, this, &Server::serveClient);

	qInfo() << "New client connection received";
}

void Server::serveClient()
{
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
		{
			switch (result.failure_reason)
			{
				using enum ParseResult::FailureReason;
			[[likely]] case IncompleteData:
				// Wait for more data
				break;
			case SchemaMismatch:
				qWarning() << "Schema mismatch detected in client data";
				dataBuffer.remove(0, result.bytes_consumed); // Remove the processed data
				break;
			case DataTooLarge:
				qWarning() << "Client sent data that is too large to process";
				dataBuffer.remove(0, result.bytes_consumed); // Remove the processed data
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
		// Calculate performance metrics
		if (lastRequestTime.isValid())
		{
			int elapsed = lastRequestTime.msecsTo(currentTime);
			// Update average request interval, using running average
			averageRequestInterval += (elapsed - averageRequestInterval) / requestCount;
		}
		lastRequestTime = currentTime;

		executor->inject_gamepad_state(result.reading);

		// Remove the processed data from the buffer
		dataBuffer.remove(0, result.bytes_consumed);

#ifdef QT_DEBUG
		qDebug() << "Consumed" << result.bytes_consumed
				 << "bytes, remaining buffer size:" << dataBuffer.size();
#endif
	}
}

void Server::destroyServer()
{
	emit navigateBack();
	this->deleteLater();
}
