#include "controllertest.hpp"

#include "../../VGP_Data_Exchange/C/GameButtons.h"
#include "../networking/controller_test_state.hpp"
#include "../settings/settings_singleton.hpp"
#include "gamepaddiagram.hpp"
#include "theme.hpp"

#include <QGridLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QPainter>
#include <QPainterPath>
#include <QPushButton>
#include <QShowEvent>
#include <QVBoxLayout>
#include <algorithm>
#include <cmath>

/**
 * A centred, rounded bar for analog values.
 *
 * Bipolar ranges (sticks, -100..100) grow from the middle; one-sided ranges
 * (triggers, 0..100) grow from the left.
 */
class MeterBar final : public QWidget
{
  public:
	explicit MeterBar(QWidget *parent = nullptr) : QWidget(parent)
	{
		setMinimumHeight(18);
		setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
	}

	void configure(int minimum, int maximum)
	{
		m_min = minimum;
		m_max = maximum;
		update();
	}

	void setValue(int value)
	{
		value = std::clamp(value, m_min, m_max);
		if (value == m_value)
			return;
		m_value = value;
		update();
	}

  protected:
	void paintEvent(QPaintEvent *) override
	{
		QPainter p(this);
		p.setRenderHint(QPainter::Antialiasing, true);

		const QRectF track = QRectF(rect()).adjusted(0.5, 0.5, -0.5, -0.5);
		QPainterPath rounded;
		rounded.addRoundedRect(track, 8, 8);

		p.fillPath(rounded, QColor("#12151f"));
		p.setPen(QPen(QColor("#2a2f40"), 1.0));
		p.drawPath(rounded);

		p.save();
		p.setClipPath(rounded);
		p.setPen(Qt::NoPen);
		p.setBrush(QColor("#6c7cff"));

		const double span = static_cast<double>(m_max - m_min);
		const double fraction = span > 0.0 ? (static_cast<double>(m_value - m_min) / span) : 0.0;

		if (m_min < 0)
		{
			const double centreX = track.center().x();
			const double dx = (fraction - 0.5) * track.width();
			QRectF fill = track;
			if (dx >= 0.0)
				fill.setLeft(centreX);
			else
				fill.setRight(centreX);
			fill.setWidth(std::abs(dx));
			p.drawRect(fill);
			p.setPen(QPen(QColor("#3a4056"), 1.0));
			p.drawLine(QPointF(centreX, track.top()), QPointF(centreX, track.bottom()));
		}
		else
		{
			QRectF fill = track;
			fill.setWidth(track.width() * std::clamp(fraction, 0.0, 1.0));
			p.drawRect(fill);
		}
		p.restore();
	}

  private:
	int m_min = 0;
	int m_max = 100;
	int m_value = 0;
};

namespace
{
/** Styling for the button chips in their two states. */
QString chipStyle(bool pressed)
{
	if (pressed)
	{
		const QColor accent = Theme::accent();
		return QStringLiteral("QLabel { background-color: %1; color: #ffffff; border: 1px solid %2;"
							  " border-radius: 10px; padding: 5px 4px; font-weight: 700; }")
			.arg(accent.name(), accent.lighter(120).name());
	}
	return QStringLiteral("QLabel { background-color: #252a3d; color: #c3c9dd;"
						  " border: 1px solid #3a4056; border-radius: 10px;"
						  " padding: 5px 4px; font-weight: 600; }");
}

/** Styling for the status pill in the header. */
QString statusStyle(const QColor &colour)
{
	return QStringLiteral("QLabel { background-color: #1b2030; color: %1; border: 1px solid %1;"
						  " border-radius: 10px; padding: 4px 12px; font-weight: 700;"
						  " font-size: 11px; }")
		.arg(colour.name());
}
} // namespace

ControllerTest::ControllerTest(QWidget *parent) : QWidget(parent)
{
	auto *root = new QVBoxLayout(this);
	root->setContentsMargins(28, 24, 28, 24);
	root->setSpacing(16);

	// -------------------------------------------------------------- header --
	auto *header = new QHBoxLayout();
	header->setSpacing(12);
	auto *back = Theme::ghostButton(tr("Back"), this);
	connect(back, &QPushButton::clicked, this, &ControllerTest::navigateBack);
	header->addWidget(back);
	header->addWidget(Theme::heading(tr("Control test"), this));
	header->addStretch();
	m_status = new QLabel(tr("Not connected"), this);
	m_status->setStyleSheet(statusStyle(QColor("#ff8ba0")));
	header->addWidget(m_status);
	root->addLayout(header);

	root->addWidget(Theme::caption(
		tr("Press anything on the connected device. Every control lights up here, and the chip grid "
		   "shows which key it sends to the PC.")));

	// ------------------------------------------------------------- diagram --
	auto *diagramCard = Theme::card(this);
	auto *diagramLayout = static_cast<QVBoxLayout *>(diagramCard->layout());
	diagramLayout->setContentsMargins(14, 12, 14, 12);
	diagramLayout->setSpacing(8);
	diagramLayout->addWidget(Theme::cardTitle(tr("Live controller"), diagramCard));
	m_diagram = new GamepadDiagram(diagramCard);
	diagramLayout->addWidget(m_diagram, 1);
	root->addWidget(diagramCard, 3);

	// --------------------------------------------------------------- chips --
	auto *chipCard = Theme::card(this);
	auto *chipLayout = static_cast<QVBoxLayout *>(chipCard->layout());
	chipLayout->setContentsMargins(16, 14, 16, 16);
	chipLayout->setSpacing(8);
	chipLayout->addWidget(Theme::cardTitle(tr("Buttons"), chipCard));
	chipLayout->addWidget(Theme::caption(tr("Each chip shows the button and the key it fires."),
										 chipCard));

	auto *chipGrid = new QGridLayout();
	chipGrid->setHorizontalSpacing(8);
	chipGrid->setVerticalSpacing(8);

	addChip(chipGrid, tr("A"), GamepadButtons_A, 0, 0);
	addChip(chipGrid, tr("B"), GamepadButtons_B, 0, 1);
	addChip(chipGrid, tr("X"), GamepadButtons_X, 0, 2);
	addChip(chipGrid, tr("Y"), GamepadButtons_Y, 0, 3);
	addChip(chipGrid, tr("LB"), GamepadButtons_LeftShoulder, 0, 4);
	addChip(chipGrid, tr("RB"), GamepadButtons_RightShoulder, 0, 5);

	addChip(chipGrid, tr("Up"), GamepadButtons_DPadUp, 1, 0);
	addChip(chipGrid, tr("Down"), GamepadButtons_DPadDown, 1, 1);
	addChip(chipGrid, tr("Left"), GamepadButtons_DPadLeft, 1, 2);
	addChip(chipGrid, tr("Right"), GamepadButtons_DPadRight, 1, 3);
	addChip(chipGrid, tr("L3"), GamepadButtons_LeftThumbstick, 1, 4);
	addChip(chipGrid, tr("R3"), GamepadButtons_RightThumbstick, 1, 5);

	addChip(chipGrid, tr("View"), GamepadButtons_View, 2, 0);
	addChip(chipGrid, tr("Menu"), GamepadButtons_Menu, 2, 1);
	addChip(chipGrid, tr("Home"), GamepadButtons_Home, 2, 2);
	addChip(chipGrid, tr("Capture"), GamepadButtons_Capture, 2, 3);
	addChip(chipGrid, tr("+"), GamepadButtons_Plus, 2, 4);
	addChip(chipGrid, tr("-"), GamepadButtons_Minus, 2, 5);

	addChip(chipGrid, tr("SL"), GamepadButtons_LeftSide, 3, 0);
	addChip(chipGrid, tr("SR"), GamepadButtons_RightSide, 3, 1);
	addChip(chipGrid, tr("P1"), GamepadButtons_Paddle1, 3, 2);
	addChip(chipGrid, tr("P2"), GamepadButtons_Paddle2, 3, 3);
	addChip(chipGrid, tr("P3"), GamepadButtons_Paddle3, 3, 4);
	addChip(chipGrid, tr("P4"), GamepadButtons_Paddle4, 3, 5);

	chipLayout->addLayout(chipGrid);
	root->addWidget(chipCard, 2);

	// -------------------------------------------------------------- analog --
	auto *analogCard = Theme::card(this);
	auto *analogLayout = static_cast<QVBoxLayout *>(analogCard->layout());
	analogLayout->setContentsMargins(16, 14, 16, 16);
	analogLayout->setSpacing(8);
	analogLayout->addWidget(Theme::cardTitle(tr("Triggers and sticks"), analogCard));

	auto *analogGrid = new QGridLayout();
	analogGrid->setHorizontalSpacing(12);
	analogGrid->setVerticalSpacing(8);

	const QString names[6] = {tr("Left trigger"), tr("Right trigger"), tr("Left stick X"),
							  tr("Left stick Y"), tr("Right stick X"), tr("Right stick Y")};
	const bool bipolar[6] = {false, false, true, true, true, true};

	for (int i = 0; i < 6; ++i)
	{
		auto *name = new QLabel(names[i], analogCard);
		name->setStyleSheet(QStringLiteral("color: #98a0b8; font-size: 12px;"));
		name->setFixedWidth(110);
		analogGrid->addWidget(name, i, 0);

		auto *bar = new MeterBar(analogCard);
		bar->configure(bipolar[i] ? -100 : 0, 100);
		analogGrid->addWidget(bar, i, 1);
		m_meters[i] = bar;

		auto *value = new QLabel(QStringLiteral("0.00"), analogCard);
		value->setStyleSheet(QStringLiteral("color: #c3c9dd; font-family: monospace;"));
		value->setFixedWidth(56);
		value->setAlignment(Qt::AlignRight | Qt::AlignVCenter);
		analogGrid->addWidget(value, i, 2);
		m_meterValues[i] = value;
	}

	analogLayout->addLayout(analogGrid);
	root->addWidget(analogCard);
	root->addStretch();

	connect(&ControllerTestState::instance(),
			&ControllerTestState::updated,
			this,
			&ControllerTest::refresh);

	refreshBindings();
	refresh();
}

ControllerTest::~ControllerTest() = default;

void ControllerTest::showEvent(QShowEvent *event)
{
	QWidget::showEvent(event);
	// The active profile may have changed while this page was hidden.
	refreshBindings();
	refresh();
}

void ControllerTest::addChip(QGridLayout *grid, const QString &name, uint32_t mask, int row, int col)
{
	auto *label = new QLabel(name, this);
	label->setAlignment(Qt::AlignCenter);
	label->setMinimumSize(78, 42);
	label->setStyleSheet(chipStyle(false));
	grid->addWidget(label, row, col);

	Chip chip;
	chip.label = label;
	chip.mask = mask;
	chip.name = name;
	chip.text = name;
	m_chips.append(chip);
}

void ControllerTest::refreshBindings()
{
	QHash<uint32_t, QString> bindings;
	const auto &profile = SettingsSingleton::instance().activeKeymapProfile();

	for (const auto &chip : m_chips)
		bindings.insert(chip.mask, profile.buttonDisplayName(static_cast<GamepadButtons>(chip.mask)));

	m_diagram->setBindings(bindings);

	// Force the text to be rebuilt on the next refresh.
	for (auto &chip : m_chips)
		chip.text.clear();
}

void ControllerTest::refresh()
{
	const ControllerTestSnapshot snapshot = ControllerTestState::instance().snapshot();

	// The server can push well over 60 packets per second. Skip work when
	// nothing a user can see has changed.
	if (m_haveSnapshot && snapshot.connected == m_last.connected &&
		snapshot.hasData == m_last.hasData && snapshot.buttonsDown == m_last.buttonsDown &&
		snapshot.leftTrigger == m_last.leftTrigger && snapshot.rightTrigger == m_last.rightTrigger &&
		snapshot.leftX == m_last.leftX && snapshot.leftY == m_last.leftY &&
		snapshot.rightX == m_last.rightX && snapshot.rightY == m_last.rightY)
		return;
	m_last = snapshot;
	m_haveSnapshot = true;

	if (snapshot.connected)
	{
		const QColor colour = snapshot.hasData ? QColor("#37d67a") : QColor("#facc15");
		m_status->setText(snapshot.hasData ? tr("Connected") : tr("Waiting for input"));
		m_status->setStyleSheet(statusStyle(colour));
	}
	else
	{
		m_status->setText(tr("Not connected"));
		m_status->setStyleSheet(statusStyle(QColor("#ff5c7a")));
	}

	const auto &profile = SettingsSingleton::instance().activeKeymapProfile();
	for (auto &chip : m_chips)
	{
		const bool pressed = (snapshot.buttonsDown & chip.mask) != 0;
		const QString action = profile.buttonDisplayName(static_cast<GamepadButtons>(chip.mask));

		QString text = chip.name;
		if (!action.isEmpty())
			text += QStringLiteral("\n") + action;

		if (text != chip.text)
		{
			chip.text = text;
			chip.label->setText(text);
		}
		if (pressed != chip.pressed)
		{
			chip.pressed = pressed;
			chip.label->setStyleSheet(chipStyle(pressed));
		}
	}

	m_diagram->setSnapshot(snapshot);

	const float values[6] = {snapshot.leftTrigger,
							 snapshot.rightTrigger,
							 snapshot.leftX,
							 snapshot.leftY,
							 snapshot.rightX,
							 snapshot.rightY};
	for (int i = 0; i < 6; ++i)
	{
		m_meters[i]->setValue(static_cast<int>(values[i] * 100.0f));
		m_meterValues[i]->setText(QString::number(values[i], 'f', 2));
	}
}
