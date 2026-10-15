#include "layoutspage.hpp"

#include "../../VGP_Data_Exchange/C/GameButtons.h"
#include "../networking/controller_test_state.hpp"
#include "../settings/game_layout.hpp"
#include "../settings/key_names.hpp"
#include "../settings/settings_singleton.hpp"
#include "gamepaddiagram.hpp"
#include "theme.hpp"

#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QListWidget>
#include <QPainter>
#include <QPainterPath>
#include <QPushButton>
#include <QStackedWidget>
#include <QVBoxLayout>
#include <algorithm>

namespace
{
/** Human-readable name of a gamepad button mask, for the binding summary. */
const char *gamepadButtonName(uint32_t mask)
{
	switch (mask)
	{
	case GamepadButtons_A:
		return "A";
	case GamepadButtons_B:
		return "B";
	case GamepadButtons_X:
		return "X";
	case GamepadButtons_Y:
		return "Y";
	case GamepadButtons_DPadUp:
		return "D-pad Up";
	case GamepadButtons_DPadDown:
		return "D-pad Down";
	case GamepadButtons_DPadLeft:
		return "D-pad Left";
	case GamepadButtons_DPadRight:
		return "D-pad Right";
	case GamepadButtons_LeftShoulder:
		return "LB";
	case GamepadButtons_RightShoulder:
		return "RB";
	case GamepadButtons_LeftThumbstick:
		return "L3";
	case GamepadButtons_RightThumbstick:
		return "R3";
	case GamepadButtons_Menu:
		return "Menu";
	case GamepadButtons_View:
		return "View";
	case GamepadButtons_Plus:
		return "+";
	case GamepadButtons_Minus:
		return "-";
	case GamepadButtons_Home:
		return "Home";
	case GamepadButtons_Capture:
		return "Capture";
	case GamepadButtons_LeftSide:
		return "SL";
	case GamepadButtons_RightSide:
		return "SR";
	case GamepadButtons_Paddle1:
		return "Paddle 1";
	case GamepadButtons_Paddle2:
		return "Paddle 2";
	case GamepadButtons_Paddle3:
		return "Paddle 3";
	case GamepadButtons_Paddle4:
		return "Paddle 4";
	default:
		return "Unknown";
	}
}

/** Build the "control -> key" label map the preview controllers draw. */
QHash<uint32_t, QString> previewBindings(const GameLayout &layout)
{
	QHash<uint32_t, QString> bindings;
	for (const auto &binding : layout.buttons)
	{
		if (binding.key.isEmpty())
			continue;
		const auto resolved = KeyNames::resolve(binding.key);
		bindings.insert(static_cast<uint32_t>(binding.button),
						resolved ? resolved->display : binding.key);
	}
	for (const auto &binding : layout.customKeys)
	{
		const auto resolved = KeyNames::resolve(binding.key);
		bindings.insert(static_cast<uint32_t>(binding.button),
						resolved ? resolved->display : binding.key);
	}
	return bindings;
}

/** A readable summary of everything a layout binds. */
QString bindingSummary(const GameLayout &layout)
{
	QStringList lines;
	lines << QStringLiteral("Left stick: %1")
				 .arg(layout.leftStick.mouseScroll
						  ? QStringLiteral("mouse wheel")
						  : (layout.leftStick.mouseMove ? QStringLiteral("mouse movement")
														: QStringLiteral("%1 / %2 / %3 / %4")
															  .arg(layout.leftStick.up.isEmpty()
																	   ? QStringLiteral("-")
																	   : layout.leftStick.up,
																   layout.leftStick.down.isEmpty()
																	   ? QStringLiteral("-")
																	   : layout.leftStick.down,
																   layout.leftStick.left.isEmpty()
																	   ? QStringLiteral("-")
																	   : layout.leftStick.left,
																   layout.leftStick.right.isEmpty()
																	   ? QStringLiteral("-")
																	   : layout.leftStick.right)));
	lines << QStringLiteral("Right stick: %1")
				 .arg(layout.rightStick.mouseScroll
						  ? QStringLiteral("mouse wheel")
						  : (layout.rightStick.mouseMove
								 ? QStringLiteral("mouse movement (look)")
								 : QStringLiteral("%1 / %2 / %3 / %4")
									   .arg(layout.rightStick.up.isEmpty() ? QStringLiteral("-")
																		   : layout.rightStick.up,
											layout.rightStick.down.isEmpty()
												? QStringLiteral("-")
												: layout.rightStick.down,
											layout.rightStick.left.isEmpty()
												? QStringLiteral("-")
												: layout.rightStick.left,
											layout.rightStick.right.isEmpty()
												? QStringLiteral("-")
												: layout.rightStick.right)));
	lines << QStringLiteral("Triggers: %1 / %2  (threshold %3%)")
				 .arg(layout.leftTriggerKey.isEmpty() ? QStringLiteral("-") : layout.leftTriggerKey,
					  layout.rightTriggerKey.isEmpty() ? QStringLiteral("-")
													   : layout.rightTriggerKey)
				 .arg(static_cast<int>(layout.triggerThreshold * 100.0f));

	for (const auto &binding : layout.buttons)
	{
		if (binding.key.isEmpty())
			continue;
		lines << QStringLiteral("%1  ->  %2   (%3)")
					 .arg(QString::fromUtf8(
							  gamepadButtonName(static_cast<uint32_t>(binding.button))),
						  binding.key,
						  binding.label);
	}

	for (const auto &binding : layout.customKeys)
	{
		lines << QStringLiteral("%1  ->  %2   (added key: %3)")
					 .arg(QString::fromUtf8(
							  gamepadButtonName(static_cast<uint32_t>(binding.button))),
						  binding.key,
						  binding.label);
	}

	return lines.join(QLatin1Char('\n'));
}
} // namespace

/**
 * @brief A blocky, Minecraft-flavoured controller preview.
 *
 * Drawn with hard pixel edges: a grass-topped board, stone buttons and a live
 * nine-slot hotbar. Pressing the hotbar controls on the device moves the
 * selection, which is exactly the "scroll the hotbar with the controller"
 * behaviour the Minecraft layout sets up.
 */
class MinecraftPanel final : public QWidget
{
  public:
	explicit MinecraftPanel(QWidget *parent = nullptr) : QWidget(parent)
	{
		setMinimumSize(420, 260);
		setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
	}

	void setSnapshot(const ControllerTestSnapshot &snapshot)
	{
		const uint32_t fresh = snapshot.buttonsDown & ~m_previous;
		m_previous = snapshot.buttonsDown;

		if (fresh & (GamepadButtons_DPadUp | GamepadButtons_Plus | GamepadButtons_LeftSide))
			m_activeSlot = (m_activeSlot + 8) % 9;
		if (fresh & (GamepadButtons_DPadDown | GamepadButtons_Minus | GamepadButtons_RightSide))
			m_activeSlot = (m_activeSlot + 1) % 9;

		const uint32_t slotButtons[4] = {GamepadButtons_Paddle1, GamepadButtons_Paddle2,
										 GamepadButtons_Paddle3, GamepadButtons_Paddle4};
		for (int i = 0; i < 4; ++i)
		{
			if (fresh & slotButtons[i])
				m_activeSlot = i;
		}

		m_snapshot = snapshot;
		update();
	}

	void setBindings(const QHash<uint32_t, QString> &bindings)
	{
		m_bindings = bindings;
		update();
	}

  protected:
	void paintEvent(QPaintEvent *) override
	{
		QPainter p(this);
		p.setRenderHint(QPainter::Antialiasing, false);

		// Board: grass over dirt, like a Minecraft block.
		const QRect board = rect().adjusted(1, 1, -1, -1);
		p.fillRect(board, QColor("#6b4a24"));
		p.fillRect(QRect(board.x(), board.y(), board.width(), 18), QColor("#4fa03d"));
		p.fillRect(QRect(board.x(), board.y() + 14, board.width(), 4), QColor("#3d7d30"));
		p.setPen(QPen(QColor("#2b1d0f"), 2));
		p.drawRect(board);

		auto key = [this](uint32_t mask)
		{
			const auto it = m_bindings.constFind(mask);
			return it != m_bindings.constEnd() ? it.value() : QString();
		};
		auto down = [this](uint32_t mask) { return (m_snapshot.buttonsDown & mask) != 0; };

		// A blocky button: dark bevel, light face, pressed inverts the bevel.
		auto block = [&](const QRect &r, const QString &text, bool pressed, const QColor &face)
		{
			const QColor body = pressed ? QColor("#f6d365") : face;
			p.setPen(Qt::NoPen);
			p.fillRect(r, QColor("#1e1e1e"));
			p.fillRect(r.adjusted(2, 2, -2, -2), body);
			p.fillRect(QRect(r.x() + 2, r.y() + 2, r.width() - 4, 3), QColor("#ffffff60"));
			if (!text.isEmpty())
			{
				QFont f = p.font();
				f.setPixelSize(11);
				f.setBold(true);
				p.setFont(f);
				p.setPen(QColor("#14110c"));
				p.drawText(r, Qt::AlignCenter, text);
			}
		};

		// Triggers along the top edge.
		auto trigger = [&](const QRect &r, float value, uint32_t mask, const QString &name)
		{
			p.setPen(Qt::NoPen);
			p.fillRect(r, QColor("#1e1e1e"));
			p.fillRect(r.adjusted(2, 2, -2, -2), QColor("#3a3a3a"));
			const int filled = static_cast<int>((r.width() - 4) * std::clamp(value, 0.0f, 1.0f));
			p.fillRect(QRect(r.x() + 2, r.y() + 2, filled, r.height() - 4),
					   down(mask) ? QColor("#f6d365") : QColor("#4aedd9"));
			QFont f = p.font();
			f.setPixelSize(10);
			f.setBold(true);
			p.setFont(f);
			p.setPen(QColor("#f0f0f0"));
			p.drawText(r, Qt::AlignCenter, key(mask).isEmpty() ? name : key(mask));
		};

		trigger(QRect(24, 30, 130, 24), m_snapshot.leftTrigger, GamepadButtons_LeftShoulder,
				QStringLiteral("ZL"));
		trigger(QRect(rect().width() - 154, 30, 130, 24), m_snapshot.rightTrigger,
				GamepadButtons_RightShoulder, QStringLiteral("ZR"));

		// Shoulders below the triggers.
		block(QRect(24, 60, 130, 22), key(GamepadButtons_LeftShoulder).isEmpty()
										  ? QStringLiteral("LB")
										  : key(GamepadButtons_LeftShoulder),
			  down(GamepadButtons_LeftShoulder), QColor("#8b8b8b"));
		block(QRect(rect().width() - 154, 60, 130, 22),
			  key(GamepadButtons_RightShoulder).isEmpty() ? QStringLiteral("RB")
														  : key(GamepadButtons_RightShoulder),
			  down(GamepadButtons_RightShoulder), QColor("#8b8b8b"));

		// Left stick as a stone block with a sliding square.
		const QRect leftStick(48, 100, 78, 78);
		p.fillRect(leftStick, QColor("#1e1e1e"));
		p.fillRect(leftStick.adjusted(3, 3, -3, -3), QColor("#8b8b8b"));
		{
			const int inset = 22;
			const int dx = static_cast<int>(m_snapshot.leftX * 12.0f);
			const int dy = static_cast<int>(m_snapshot.leftY * 12.0f);
			const QRect knob(leftStick.center().x() - inset / 2 + dx,
							 leftStick.center().y() - inset / 2 + dy,
							 inset,
							 inset);
			p.fillRect(knob, down(GamepadButtons_LeftThumbstick) ? QColor("#f6d365")
																 : QColor("#5a5a5a"));
		}

		// Right stick.
		const QRect rightStick(rect().width() - 138, 150, 74, 74);
		p.fillRect(rightStick, QColor("#1e1e1e"));
		p.fillRect(rightStick.adjusted(3, 3, -3, -3), QColor("#8b8b8b"));
		{
			const int inset = 20;
			const int dx = static_cast<int>(m_snapshot.rightX * 11.0f);
			const int dy = static_cast<int>(m_snapshot.rightY * 11.0f);
			const QRect knob(rightStick.center().x() - inset / 2 + dx,
							 rightStick.center().y() - inset / 2 + dy,
							 inset,
							 inset);
			p.fillRect(knob, down(GamepadButtons_RightThumbstick) ? QColor("#f6d365")
																  : QColor("#5a5a5a"));
		}

		// D-pad, drawn as four stone blocks.
		{
			const QPoint c(190, 178);
			block(QRect(c.x() - 16, c.y() - 48, 32, 32), QString(), down(GamepadButtons_DPadUp),
				  QColor("#a8a8a8"));
			block(QRect(c.x() - 16, c.y() + 16, 32, 32), QString(), down(GamepadButtons_DPadDown),
				  QColor("#a8a8a8"));
			block(QRect(c.x() - 48, c.y() - 16, 32, 32), QString(), down(GamepadButtons_DPadLeft),
				  QColor("#a8a8a8"));
			block(QRect(c.x() + 16, c.y() - 16, 32, 32), QString(), down(GamepadButtons_DPadRight),
				  QColor("#a8a8a8"));
		}

		// Face buttons.
		{
			const QPoint c(rect().width() - 232, 128);
			block(QRect(c.x() - 17, c.y() + 17, 34, 34), QStringLiteral("A"), down(GamepadButtons_A),
				  QColor("#7cc95c"));
			block(QRect(c.x() + 17, c.y() - 17, 34, 34), QStringLiteral("B"), down(GamepadButtons_B),
				  QColor("#d1594f"));
			block(QRect(c.x() - 51, c.y() - 17, 34, 34), QStringLiteral("X"), down(GamepadButtons_X),
				  QColor("#4f8fd1"));
			block(QRect(c.x() + 17, c.y() - 51, 34, 34), QStringLiteral("Y"), down(GamepadButtons_Y),
				  QColor("#e0c341"));
		}

		// Centre buttons.
		block(QRect(rect().width() - 236, 66, 44, 22),
			  key(GamepadButtons_View).isEmpty() ? QStringLiteral("Tab") : key(GamepadButtons_View),
			  down(GamepadButtons_View), QColor("#9c7a4a"));
		block(QRect(rect().width() - 186, 66, 44, 22),
			  key(GamepadButtons_Menu).isEmpty() ? QStringLiteral("Esc") : key(GamepadButtons_Menu),
			  down(GamepadButtons_Menu), QColor("#9c7a4a"));

		// Hotbar.
		const int slotSize = 34;
		const int slots = 9;
		const int hotbarWidth = slots * slotSize;
		const int hotbarX = (rect().width() - hotbarWidth) / 2;
		const int hotbarY = rect().height() - slotSize - 26;

		QFont f = p.font();
		f.setPixelSize(10);
		f.setBold(true);
		p.setFont(f);
		p.setPen(QColor("#f0f0f0"));
		p.drawText(QRect(hotbarX, hotbarY - 22, hotbarWidth, 18), Qt::AlignCenter,
				   tr("Hotbar slot %1 - scroll with the left stick or D-pad").arg(m_activeSlot + 1));

		for (int i = 0; i < slots; ++i)
		{
			const QRect slot(hotbarX + i * slotSize, hotbarY, slotSize, slotSize);
			p.setPen(Qt::NoPen);
			p.fillRect(slot, QColor("#1e1e1e"));
			p.fillRect(slot.adjusted(2, 2, -2, -2), QColor("#3a3a3a"));
			p.fillRect(slot.adjusted(6, 6, -6, -6),
					   i == m_activeSlot ? QColor("#7cc95c") : QColor("#5c5c5c"));
			if (i == m_activeSlot)
			{
				p.setPen(QPen(QColor("#f6f6f6"), 3));
				p.setBrush(Qt::NoBrush);
				p.drawRect(slot.adjusted(1, 1, -1, -1));
			}
		}
	}

  private:
	ControllerTestSnapshot m_snapshot;
	QHash<uint32_t, QString> m_bindings;
	int m_activeSlot = 0;
	uint32_t m_previous = 0;
};

LayoutsPage::LayoutsPage(QWidget *parent) : QWidget(parent)
{
	auto *root = new QVBoxLayout(this);
	root->setContentsMargins(28, 24, 28, 24);
	root->setSpacing(16);

	auto *header = new QHBoxLayout();
	header->setSpacing(12);
	auto *back = Theme::ghostButton(tr("Back"), this);
	connect(back, &QPushButton::clicked, this, &LayoutsPage::navigateBack);
	header->addWidget(back);
	header->addWidget(Theme::heading(tr("Controller layouts"), this));
	header->addStretch();
	root->addLayout(header);

	root->addWidget(Theme::caption(
		tr("Pick a layout to fit the game you are playing, then press Apply. Applying rewrites "
		   "every button, stick, trigger and extra key of the active profile.")));

	auto *columns = new QHBoxLayout();
	columns->setSpacing(16);

	// ---------------------------------------------------------------- list --
	auto *listCard = Theme::card(this);
	auto *listLayout = static_cast<QVBoxLayout *>(listCard->layout());
	listLayout->setContentsMargins(14, 14, 14, 14);
	listLayout->setSpacing(8);
	listLayout->addWidget(Theme::cardTitle(tr("Layouts"), listCard));

	m_list = new QListWidget(listCard);
	m_list->setMinimumWidth(280);
	for (const auto &layout : allGameLayouts())
	{
		auto *item = new QListWidgetItem(
			QStringLiteral("%1\n%2").arg(layout.name, layout.tagline), m_list);
		item->setData(Qt::UserRole, layout.id);
	}
	listLayout->addWidget(m_list, 1);
	listCard->setFixedWidth(330);
	columns->addWidget(listCard);

	// -------------------------------------------------------------- detail --
	auto *detailCard = Theme::card(this);
	auto *detailLayout = static_cast<QVBoxLayout *>(detailCard->layout());
	detailLayout->setContentsMargins(18, 16, 18, 16);
	detailLayout->setSpacing(10);

	m_name = Theme::cardTitle(QString(), detailCard);
	m_name->setStyleSheet(QStringLiteral("font-size: 20px; font-weight: 800; color: #ffffff;"));
	detailLayout->addWidget(m_name);

	m_tagline = Theme::caption(QString(), detailCard);
	detailLayout->addWidget(m_tagline);

	m_description = Theme::caption(QString(), detailCard);
	detailLayout->addWidget(m_description);

	m_preview = new QStackedWidget(detailCard);
	m_diagram = new GamepadDiagram(m_preview);
	m_preview->addWidget(m_diagram);
	m_specialPanel = new MinecraftPanel(m_preview);
	m_preview->addWidget(m_specialPanel);
	m_preview->setMinimumHeight(300);
	detailLayout->addWidget(m_preview, 3);

	m_bindings = new QLabel(detailCard);
	m_bindings->setWordWrap(true);
	m_bindings->setTextInteractionFlags(Qt::TextSelectableByMouse);
	m_bindings->setStyleSheet(QStringLiteral("color: #b6bccf; font-family: monospace;"
											 " font-size: 11px; background: transparent;"));
	detailLayout->addWidget(m_bindings, 2);

	auto *footer = new QHBoxLayout();
	footer->setSpacing(12);
	m_apply = Theme::primaryButton(tr("Apply layout"), detailCard);
	connect(m_apply, &QPushButton::clicked, this, &LayoutsPage::applySelected);
	footer->addWidget(m_apply);
	m_status = Theme::caption(QString(), detailCard);
	footer->addWidget(m_status, 1);
	detailLayout->addLayout(footer);

	columns->addWidget(detailCard, 1);
	root->addLayout(columns, 1);

	connect(m_list, &QListWidget::currentRowChanged, this, &LayoutsPage::selectLayout);

	// Live preview while a device is connected.
	connect(&ControllerTestState::instance(),
			&ControllerTestState::updated,
			this,
			[this]
			{
				const auto snapshot = ControllerTestState::instance().snapshot();
				m_diagram->setSnapshot(snapshot);
				static_cast<MinecraftPanel *>(m_specialPanel)->setSnapshot(snapshot);
			});

	m_list->setCurrentRow(0);
}

LayoutsPage::~LayoutsPage() = default;

void LayoutsPage::showPreview(bool minecraft)
{
	m_preview->setCurrentIndex(minecraft ? 1 : 0);
}

void LayoutsPage::selectLayout(int index)
{
	if (index < 0 || index >= allGameLayouts().size())
		return;

	const GameLayout &layout = allGameLayouts().at(index);

	m_name->setText(layout.name);
	m_tagline->setText(layout.tagline);
	m_description->setText(layout.description);

	const QHash<uint32_t, QString> bindings = previewBindings(layout);
	m_diagram->setBindings(bindings);
	m_diagram->setAccent(QColor(layout.accent));
	auto *minecraft = static_cast<MinecraftPanel *>(m_specialPanel);
	minecraft->setBindings(bindings);
	minecraft->setSnapshot(ControllerTestState::instance().snapshot());

	m_bindings->setText(bindingSummary(layout));

	m_status->setText(QString());
	m_status->setStyleSheet(QString());

	showPreview(layout.minecraftThemed);

	// Flag which layout is currently active in the profile, if any.
	if (SettingsSingleton::instance().activeProfileName() == layout.name)
		m_status->setText(tr("Currently active."));
}

void LayoutsPage::applySelected()
{
	const int index = m_list->currentRow();
	if (index < 0 || index >= allGameLayouts().size())
		return;

	const GameLayout &layout = allGameLayouts().at(index);
	QString error;
	if (applyGameLayout(layout, &error))
	{
		m_status->setText(tr("Applied \"%1\". Connect a device and open the control test to try it.")
							  .arg(layout.name));
		m_status->setStyleSheet(QStringLiteral("color: #37d67a;"));
		m_diagram->setBindings(previewBindings(layout));
		static_cast<MinecraftPanel *>(m_specialPanel)->setBindings(previewBindings(layout));
		emit layoutApplied(layout.name);
	}
	else
	{
		m_status->setText(error.isEmpty() ? tr("Could not apply the layout.") : error);
		m_status->setStyleSheet(QStringLiteral("color: #ff8ba0;"));
	}
}
