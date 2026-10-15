#include "usbpage.hpp"

#include "../networking/usb_bridge.hpp"
#include "../settings/settings_singleton.hpp"
#include "theme.hpp"

#include <QCheckBox>
#include <QFrame>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QListWidget>
#include <QPushButton>
#include <QShowEvent>
#include <QSpinBox>
#include <QVBoxLayout>

namespace
{
/** The port mirrored onto the phone by default. */
constexpr int kDefaultUsbPort = 30000;

QString statusStyle(const QColor &colour)
{
	return QStringLiteral("QLabel { background-color: #1b2030; color: %1; border: 1px solid %1;"
						  " border-radius: 10px; padding: 4px 12px; font-weight: 700;"
						  " font-size: 11px; }")
		.arg(colour.name());
}
} // namespace

UsbPage::UsbPage(QWidget *parent) : QWidget(parent)
{
	auto *root = new QVBoxLayout(this);
	root->setContentsMargins(28, 24, 28, 24);
	root->setSpacing(14);

	// -------------------------------------------------------------- header --
	auto *header = new QHBoxLayout();
	header->setSpacing(12);
	auto *back = Theme::ghostButton(tr("Back"), this);
	connect(back, &QPushButton::clicked, this, &UsbPage::navigateBack);
	header->addWidget(back);
	header->addWidget(Theme::heading(tr("USB connection"), this));
	header->addStretch();
	m_status = new QLabel(tr("Checking..."), this);
	m_status->setStyleSheet(statusStyle(QColor("#98a0b8")));
	header->addWidget(m_status);
	root->addLayout(header);

	root->addWidget(Theme::caption(
		tr("Plug the phone in with USB debugging on and this page sets the link up for you. "
		   "The phone then talks to the server over the cable, so Wi-Fi, guest networks and "
		   "firewalls are out of the picture.")));

	auto *columns = new QHBoxLayout();
	columns->setSpacing(16);

	// ------------------------------------------------------------- left -----
	auto *left = new QVBoxLayout();
	left->setSpacing(14);

	auto *setupCard = Theme::card(this);
	auto *setupLayout = static_cast<QVBoxLayout *>(setupCard->layout());
	setupLayout->setContentsMargins(18, 16, 18, 16);
	setupLayout->setSpacing(10);
	setupLayout->addWidget(Theme::cardTitle(tr("Steps"), setupCard));

	auto *steps = new QLabel(tr("1.  On the phone: Settings > About phone > tap Build number "
								"seven times.\n"
								"2.  Developer options > enable USB debugging.\n"
								"3.  Plug the phone in and accept the \"Allow USB debugging\" "
								"prompt.\n"
								"4.  Start the server on the port below, then tap Set up USB link."),
							 setupCard);
	steps->setWordWrap(true);
	steps->setStyleSheet(QStringLiteral("color: #c3c9dd; background: transparent;"));
	setupLayout->addWidget(steps);

	auto *portRow = new QHBoxLayout();
	portRow->setSpacing(10);
	portRow->addWidget(new QLabel(tr("Port:"), setupCard));
	m_port = new QSpinBox(setupCard);
	m_port->setRange(1, 65535);
	m_port->setValue(kDefaultUsbPort);
	connect(m_port, &QSpinBox::valueChanged, this, &UsbPage::applyPort);
	portRow->addWidget(m_port);
	portRow->addStretch();
	setupLayout->addLayout(portRow);

	m_auto = new QCheckBox(tr("Set the link up automatically when a phone appears"), setupCard);
	m_auto->setChecked(true);
	connect(m_auto,
			&QCheckBox::toggled,
			this,
			[this](bool enabled) { m_bridge->setAutoConnect(enabled); });
	setupLayout->addWidget(m_auto);

	auto *buttons = new QHBoxLayout();
	buttons->setSpacing(10);
	m_setUp = Theme::primaryButton(tr("Set up USB link"), setupCard);
	connect(m_setUp, &QPushButton::clicked, this, &UsbPage::setUpLink);
	buttons->addWidget(m_setUp);

	m_remove = Theme::ghostButton(tr("Remove link"), setupCard);
	connect(m_remove, &QPushButton::clicked, this, &UsbPage::removeLink);
	buttons->addWidget(m_remove);

	auto *openServer = Theme::ghostButton(tr("Open server"), setupCard);
	connect(openServer, &QPushButton::clicked, this, &UsbPage::startServerRequested);
	buttons->addWidget(openServer);
	buttons->addStretch();
	setupLayout->addLayout(buttons);

	m_hint = Theme::caption(QString(), setupCard);
	setupLayout->addWidget(m_hint);

	left->addWidget(setupCard);

	auto *deviceCard = Theme::card(this);
	auto *deviceLayout = static_cast<QVBoxLayout *>(deviceCard->layout());
	deviceLayout->setContentsMargins(18, 16, 18, 16);
	deviceLayout->setSpacing(10);
	deviceLayout->addWidget(Theme::cardTitle(tr("Detected over USB"), deviceCard));
	m_adbPath = Theme::caption(QString(), deviceCard);
	deviceLayout->addWidget(m_adbPath);
	m_devices = new QListWidget(deviceCard);
	m_devices->setMinimumHeight(90);
	deviceLayout->addWidget(m_devices);
	left->addWidget(deviceCard, 2);

	columns->addLayout(left, 3);

	// ------------------------------------------------------------ right -----
	auto *logCard = Theme::card(this);
	auto *logLayout = static_cast<QVBoxLayout *>(logCard->layout());
	logLayout->setContentsMargins(18, 16, 18, 16);
	logLayout->setSpacing(10);
	logLayout->addWidget(Theme::cardTitle(tr("Activity"), logCard));
	m_log = new QListWidget(logCard);
	logLayout->addWidget(m_log, 1);
	columns->addWidget(logCard, 2);

	root->addLayout(columns, 1);

	// --------------------------------------------------------------- model --
	m_bridge = new UsbBridge(this);
	connect(m_bridge, &UsbBridge::changed, this, &UsbPage::refresh);
	connect(m_bridge, &UsbBridge::logMessage, this, &UsbPage::appendLog);

	applyPort(m_port->value());
	m_bridge->start();
	refresh();
}

UsbPage::~UsbPage() = default;

void UsbPage::applyPort(int port)
{
	m_bridge->setDesiredPort(static_cast<quint16>(port));
	// The link only works when the server listens on this exact port, so keep
	// the setting in step with what the page shows.
	SettingsSingleton::instance().setPort(static_cast<quint16>(port));
	refresh();
}

void UsbPage::setUpLink()
{
	applyPort(m_port->value());
	m_bridge->setForward(static_cast<quint16>(m_port->value()));
}

void UsbPage::removeLink()
{
	m_bridge->clearForward();
}

void UsbPage::appendLog(const QString &message)
{
	m_log->addItem(message);
	m_log->scrollToBottom();
}

void UsbPage::refresh()
{
	if (!m_bridge->adbAvailable())
	{
		m_status->setText(tr("adb missing"));
		m_status->setStyleSheet(statusStyle(QColor("#ff5c7a")));
		m_adbPath->setText(
			tr("adb was not found. Install Android platform-tools and make sure adb is on your "
			   "PATH, then reopen this page."));
	}
	else
	{
		m_adbPath->setText(tr("Using %1").arg(m_bridge->adbPath()));
	}

	const QList<UsbDevice> devices = m_bridge->devices();
	m_devices->clear();
	for (const UsbDevice &device : devices)
	{
		m_devices->addItem(tr("%1   (%2)   -   %3")
							   .arg(device.model.isEmpty() ? device.serial : device.model,
									device.serial,
									device.state));
	}
	if (devices.isEmpty() && m_bridge->adbAvailable())
		m_devices->addItem(tr("No device yet. Plug the phone in and accept the prompt."));

	if (m_bridge->adbAvailable())
	{
		if (m_bridge->forwardedPort() != 0)
		{
			m_status->setText(tr("Link ready on %1").arg(m_bridge->forwardedPort()));
			m_status->setStyleSheet(statusStyle(QColor("#37d67a")));
		}
		else if (devices.isEmpty())
		{
			m_status->setText(tr("No device"));
			m_status->setStyleSheet(statusStyle(QColor("#facc15")));
		}
		else
		{
			m_status->setText(tr("%1 device(s)").arg(devices.size()));
			m_status->setStyleSheet(statusStyle(QColor("#facc15")));
		}
	}

	if (m_bridge->forwardedPort() != 0)
	{
		m_hint->setText(tr("On the phone open Connect, choose USB, and connect to "
						   "127.0.0.1:%1. Keep this link while you play.")
							.arg(m_bridge->forwardedPort()));
	}
	else
	{
		m_hint->setText(tr("The server must be listening on port %1 for the link to work.")
							.arg(m_port->value()));
	}

	m_remove->setEnabled(m_bridge->forwardedPort() != 0);
	m_setUp->setEnabled(m_bridge->adbAvailable());
}
