#include "homepage.hpp"

#include "../networking/controller_test_state.hpp"
#include "../networking/input_backend_status.hpp"
#include "../settings/settings_singleton.hpp"
#include "../update/update_checker.hpp"
#include "theme.hpp"

#include <QFrame>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QTimer>
#include <QVBoxLayout>

namespace
{
/** A small "label over value" block used inside the status cards. */
QFrame *statCard(const QString &caption, QLabel **valueOut, QWidget *parent)
{
	auto *card = Theme::card(parent);
	auto *layout = static_cast<QVBoxLayout *>(card->layout());
	layout->setContentsMargins(16, 14, 16, 14);
	layout->setSpacing(4);

	auto *captionLabel = new QLabel(caption.toUpper(), card);
	captionLabel->setProperty("role", "section");
	layout->addWidget(captionLabel);

	auto *value = new QLabel(QStringLiteral("-"), card);
	value->setStyleSheet(QStringLiteral("font-size: 17px; font-weight: 700; color: #ffffff;"));
	value->setWordWrap(true);
	layout->addWidget(value);

	*valueOut = value;
	return card;
}
} // namespace

HomePage::~HomePage() = default;

HomePage::HomePage(QWidget *parent) : QWidget(parent)
{
	auto *root = new QVBoxLayout(this);
	root->setContentsMargins(28, 24, 28, 24);
	root->setSpacing(18);

	root->addWidget(Theme::heading(tr("Virtual GamePad")));
	auto *tagline = Theme::caption(
		tr("Turn your phone into a controller for this PC. Start the server, scan the QR code, "
		   "then pick a layout that matches the game you are playing."));
	tagline->setStyleSheet(QStringLiteral("color: #98a0b8; font-size: 13px;"));
	root->addWidget(tagline);

	// ------------------------------------------------------------- status --
	auto *statusRow = new QHBoxLayout();
	statusRow->setSpacing(14);
	statusRow->addWidget(statCard(tr("Connection"), &m_connectionValue, this), 1);
	statusRow->addWidget(statCard(tr("Active profile"), &m_profileValue, this), 1);
	statusRow->addWidget(statCard(tr("Input mode"), &m_executorValue, this), 1);
	statusRow->addWidget(statCard(tr("Port"), &m_portValue, this), 1);
	root->addLayout(statusRow);

	m_connectionHint = Theme::caption(QString(), this);
	root->addWidget(m_connectionHint);

	// ------------------------------------------------------------ actions --
	auto *actionsCard = Theme::card(this);
	auto *actionsLayout = static_cast<QVBoxLayout *>(actionsCard->layout());
	actionsLayout->setContentsMargins(20, 18, 20, 18);
	actionsLayout->setSpacing(12);
	actionsLayout->addWidget(Theme::cardTitle(tr("Quick actions"), actionsCard));

	auto *grid = new QGridLayout();
	grid->setHorizontalSpacing(12);
	grid->setVerticalSpacing(12);

	auto *start = Theme::primaryButton(tr("Start server"), actionsCard);
	auto *test = Theme::ghostButton(tr("Control test"), actionsCard);
	auto *layouts = Theme::ghostButton(tr("Controller layouts"), actionsCard);
	auto *keymap = Theme::ghostButton(tr("Key mapping"), actionsCard);
	auto *prefs = Theme::ghostButton(tr("Preferences"), actionsCard);
	auto *about = Theme::ghostButton(tr("About"), actionsCard);
	auto *usb = Theme::ghostButton(tr("Connect over USB"), actionsCard);
	auto *updates = Theme::ghostButton(tr("Check for updates"), actionsCard);

	grid->addWidget(start, 0, 0);
	grid->addWidget(test, 0, 1);
	grid->addWidget(layouts, 0, 2);
	grid->addWidget(keymap, 1, 0);
	grid->addWidget(prefs, 1, 1);
	grid->addWidget(about, 1, 2);
	grid->addWidget(usb, 2, 0);
	grid->addWidget(updates, 2, 1);
	actionsLayout->addLayout(grid);

	// A check the user asks for answers either way; the one at startup stays quiet
	// unless there is something to install, and waits long enough not to interrupt
	// the app opening.
	m_updater = std::make_unique<UpdateChecker>();
	connect(updates, &QPushButton::clicked, this, [this]() { m_updater->check(this, true); });
	m_updateCheckTimer = new QTimer(this);
	m_updateCheckTimer->setSingleShot(true);
	connect(m_updateCheckTimer, &QTimer::timeout, this, [this]() { m_updater->check(this, false); });
	m_updateCheckTimer->start(4000);

	connect(start, &QPushButton::clicked, this, &HomePage::startServerRequested);
	connect(test, &QPushButton::clicked, this, &HomePage::openControlTestRequested);
	connect(layouts, &QPushButton::clicked, this, &HomePage::openLayoutsRequested);
	connect(keymap, &QPushButton::clicked, this, &HomePage::openKeymapRequested);
	connect(prefs, &QPushButton::clicked, this, &HomePage::openPreferencesRequested);
	connect(usb, &QPushButton::clicked, this, &HomePage::openUsbRequested);
	connect(about, &QPushButton::clicked, this, &HomePage::openAboutRequested);

	root->addWidget(actionsCard);
	root->addStretch();    connect(&ControllerTestState::instance(), &ControllerTestState::updated, this, &HomePage::refreshStatus);
	// The backend is announced when its executor is built, which can be after this
	// page appears.
	connect(&InputBackendStatus::instance(),
			&InputBackendStatus::changed,
			this,
			&HomePage::refreshSummary);
	refreshStatus();
	refreshSummary();
}

void HomePage::refreshSummary()
{
	auto &settings = SettingsSingleton::instance();
	m_profileValue->setText(settings.activeProfileName().isEmpty() ? tr("Default")
																  : settings.activeProfileName());
	// The live backend label carries whether games will actually see it, which is
	// the difference between "the controller does nothing" and "the backend
	// could not start on this machine".
	const auto backend = InputBackendStatus::instance().snapshot();
	if (!backend.backendName.isEmpty())
	{
		m_executorValue->setText(backend.available
									 ? tr("%1 (active)").arg(backend.backendName)
									 : tr("%1 (unavailable)").arg(backend.backendName));
		m_executorValue->setStyleSheet(
			QStringLiteral("font-size: 17px; font-weight: 700; color: %1;")
				.arg(backend.available ? "#ffffff" : "#ff8ba0"));
	}
	else
	{
		// No backend is alive — this page can be shown before the server page is
		// built, or after the server was stopped — so name the configured one
		// without claiming a device exists.
		switch (settings.executorType())
		{
		case ExecutorType::GamepadExecutor:
			m_executorValue->setText(tr("Virtual gamepad (not started)"));
			break;
		case ExecutorType::GamepadAndKeyboardMouse:
			m_executorValue->setText(tr("Virtual gamepad + keyboard/mouse (not started)"));
			break;
		case ExecutorType::KeyboardMouseExecutor:
		default:
			m_executorValue->setText(tr("Keyboard + mouse (not started)"));
			break;
		}
		m_executorValue->setStyleSheet(
			QStringLiteral("font-size: 17px; font-weight: 700; color: #98a0b8;"));
	}
	m_portValue->setText(QString::number(settings.port()));
}

void HomePage::refreshStatus()
{
	refreshSummary();

	const auto snapshot = ControllerTestState::instance().snapshot();
	if (snapshot.connected)
	{
		m_connectionValue->setText(tr("Connected"));
		m_connectionValue->setStyleSheet(
			QStringLiteral("font-size: 17px; font-weight: 700; color: #37d67a;"));
		m_connectionHint->setText(snapshot.hasData
									  ? tr("Receiving input. Open the control test to see every button.")
									  : tr("A device is connected and waiting for input."));
	}
	else
	{
		m_connectionValue->setText(tr("Not connected"));
		m_connectionValue->setStyleSheet(
			QStringLiteral("font-size: 17px; font-weight: 700; color: #ff8ba0;"));
		m_connectionHint->setText(
			tr("Start the server, then scan the QR code with the mobile app to connect."));
	}

	// A backend that cannot deliver input is the reason a connected phone does
	// nothing in a game, so it belongs next to the connection state.
	const auto backend = InputBackendStatus::instance().snapshot();
	if (!backend.available && !backend.detail.isEmpty())
	{
		m_connectionHint->setText(m_connectionHint->text() +
								  QStringLiteral("\n") +
								  tr("Input backend problem: %1").arg(backend.detail));
	}
}
