#include "keymappage.hpp"

#include "../../VGP_Data_Exchange/C/GameButtons.h"
#include "../settings/settings_singleton.hpp"
#include "buttoninputbox.hpp"
#include "theme.hpp"

#include <QComboBox>
#include <QFrame>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QPushButton>
#include <QScrollArea>
#include <QShowEvent>
#include <QVBoxLayout>
#include <algorithm>

namespace
{
/** Every control the protocol can carry, in a sensible reading order. */
struct ButtonInfo
{
	uint32_t mask;
	const char *name;
};

const ButtonInfo kButtons[] = {
	{GamepadButtons_A, "A"},
	{GamepadButtons_B, "B"},
	{GamepadButtons_X, "X"},
	{GamepadButtons_Y, "Y"},
	{GamepadButtons_DPadUp, "D-pad Up"},
	{GamepadButtons_DPadDown, "D-pad Down"},
	{GamepadButtons_DPadLeft, "D-pad Left"},
	{GamepadButtons_DPadRight, "D-pad Right"},
	{GamepadButtons_LeftShoulder, "Left shoulder (LB)"},
	{GamepadButtons_RightShoulder, "Right shoulder (RB)"},
	{GamepadButtons_LeftThumbstick, "Left stick button (L3)"},
	{GamepadButtons_RightThumbstick, "Right stick button (R3)"},
	{GamepadButtons_Menu, "Menu"},
	{GamepadButtons_View, "View"},
	{GamepadButtons_Plus, "Plus"},
	{GamepadButtons_Minus, "Minus"},
	{GamepadButtons_Home, "Home"},
	{GamepadButtons_Capture, "Capture"},
	{GamepadButtons_LeftSide, "Left side (SL)"},
	{GamepadButtons_RightSide, "Right side (SR)"},
	{GamepadButtons_Paddle1, "Paddle 1"},
	{GamepadButtons_Paddle2, "Paddle 2"},
	{GamepadButtons_Paddle3, "Paddle 3"},
	{GamepadButtons_Paddle4, "Paddle 4"},
};

constexpr int kStickDirectional = 0;
constexpr int kStickMouseLook = 1;
constexpr int kStickMouseWheel = 2;

const char *buttonName(uint32_t mask)
{
	for (const auto &info : kButtons)
	{
		if (info.mask == mask)
			return info.name;
	}
	return "Unknown";
}
} // namespace

KeymapPage::KeymapPage(QWidget *parent) : QWidget(parent)
{
	buildUi();
	loadFromProfile();
}

KeymapPage::~KeymapPage() = default;

void KeymapPage::buildUi()
{
	auto *root = new QVBoxLayout(this);
	root->setContentsMargins(28, 24, 28, 24);
	root->setSpacing(14);

	auto *header = new QHBoxLayout();
	header->setSpacing(12);
	auto *back = Theme::ghostButton(tr("Back"), this);
	connect(back, &QPushButton::clicked, this, &KeymapPage::navigateBack);
	header->addWidget(back);
	header->addWidget(Theme::heading(tr("Key mapping"), this));
	header->addStretch();
	header->addWidget(new QLabel(tr("Profile:"), this));

	m_profile = new QComboBox(this);
	m_profile->setMinimumWidth(180);
	m_profile->addItems(SettingsSingleton::instance().listAvailableProfiles());
	m_profile->setCurrentText(SettingsSingleton::instance().activeProfileName());
	connect(m_profile, &QComboBox::currentIndexChanged, this, &KeymapPage::changeProfile);
	header->addWidget(m_profile);
	root->addLayout(header);

	root->addWidget(Theme::caption(
		tr("Click a box, then press the key you want. Every control the phone can send is listed, "
		   "and you can add extra keys below for things the standard set does not cover.")));

	auto *scroll = new QScrollArea(this);
	scroll->setWidgetResizable(true);
	scroll->setFrameShape(QFrame::NoFrame);

	auto *content = new QWidget(scroll);
	auto *contentLayout = new QVBoxLayout(content);
	contentLayout->setContentsMargins(0, 0, 8, 0);
	contentLayout->setSpacing(14);
	contentLayout->addWidget(buildButtonsCard());
	contentLayout->addWidget(buildSticksCard());
	contentLayout->addWidget(buildCustomCard());
	contentLayout->addStretch();
	scroll->setWidget(content);
	root->addWidget(scroll, 1);

	auto *footer = new QHBoxLayout();
	footer->setSpacing(12);
	auto *save = Theme::primaryButton(tr("Save profile"), this);
	connect(save, &QPushButton::clicked, this, &KeymapPage::saveProfile);
	footer->addWidget(save);

	auto *reset = Theme::ghostButton(tr("Restore defaults"), this);
	connect(reset, &QPushButton::clicked, this, &KeymapPage::resetProfile);
	footer->addWidget(reset);

	m_status = Theme::caption(QString(), this);
	footer->addWidget(m_status, 1);
	root->addLayout(footer);
}

QWidget *KeymapPage::buildButtonsCard()
{
	auto *card = Theme::card(this);
	auto *layout = static_cast<QVBoxLayout *>(card->layout());
	layout->setContentsMargins(18, 16, 18, 18);
	layout->setSpacing(10);
	layout->addWidget(Theme::cardTitle(tr("Buttons"), card));

	auto *grid = new QGridLayout();
	grid->setHorizontalSpacing(16);
	grid->setVerticalSpacing(8);

	int row = 0;
	int col = 0;
	for (const auto &info : kButtons)
	{
		auto *box = new ButtonInputBox(card);
		box->setMinimumWidth(140);
		grid->addWidget(new QLabel(QString::fromUtf8(info.name), card), row, col * 2);
		grid->addWidget(box, row, col * 2 + 1);
		m_buttonBoxes.insert(info.mask, box);

		if (++col == 2)
		{
			col = 0;
			++row;
		}
	}

	layout->addLayout(grid);
	return card;
}

QWidget *KeymapPage::buildSticksCard()
{
	auto *card = Theme::card(this);
	auto *layout = static_cast<QVBoxLayout *>(card->layout());
	layout->setContentsMargins(18, 16, 18, 18);
	layout->setSpacing(10);
	layout->addWidget(Theme::cardTitle(tr("Thumbsticks and triggers"), card));
	layout->addWidget(Theme::caption(
		tr("Choose \"Mouse wheel\" to cycle hotbar slots or inventory pages with a stick. "
		   "\"Mouse look\" moves the pointer."),
		card));

	auto *grid = new QGridLayout();
	grid->setHorizontalSpacing(16);
	grid->setVerticalSpacing(10);

	auto buildStick = [&](const QString &title, StickEditor &editor, int row)
	{
		grid->addWidget(new QLabel(title, card), row, 0);

		editor.mode = new QComboBox(card);
		editor.mode->addItem(tr("Directional keys"));
		editor.mode->addItem(tr("Mouse movement (look)"));
		editor.mode->addItem(tr("Mouse wheel (scroll)"));
		grid->addWidget(editor.mode, row, 1);

		auto *directions = new QHBoxLayout();
		const QString labels[4] = {tr("Up"), tr("Down"), tr("Left"), tr("Right")};
		ButtonInputBox **boxes[4] = {&editor.up, &editor.down, &editor.left, &editor.right};
		for (int i = 0; i < 4; ++i)
		{
			auto *column = new QVBoxLayout();
			auto *label = new QLabel(labels[i], card);
			label->setStyleSheet(QStringLiteral("color: #98a0b8; font-size: 11px;"));
			column->addWidget(label);
			*boxes[i] = new ButtonInputBox(card);
			(*boxes[i])->setMinimumWidth(110);
			column->addWidget(*boxes[i]);
			directions->addLayout(column);
		}
		grid->addLayout(directions, row, 2);
	};

	buildStick(tr("Left stick"), m_leftStick, 0);
	buildStick(tr("Right stick"), m_rightStick, 1);

	grid->addWidget(new QLabel(tr("Left trigger"), card), 2, 0);
	m_leftTrigger = new ButtonInputBox(card);
	m_leftTrigger->setMinimumWidth(140);
	grid->addWidget(m_leftTrigger, 2, 1);
	m_leftThreshold = new QComboBox(card);
	for (int i = 1; i <= 9; ++i)
		m_leftThreshold->addItem(tr("%1%").arg(i * 10));
	grid->addWidget(m_leftThreshold, 2, 2);

	grid->addWidget(new QLabel(tr("Right trigger"), card), 3, 0);
	m_rightTrigger = new ButtonInputBox(card);
	m_rightTrigger->setMinimumWidth(140);
	grid->addWidget(m_rightTrigger, 3, 1);
	m_rightThreshold = new QComboBox(card);
	for (int i = 1; i <= 9; ++i)
		m_rightThreshold->addItem(tr("%1%").arg(i * 10));
	grid->addWidget(m_rightThreshold, 3, 2);

	layout->addLayout(grid);
	return card;
}

QWidget *KeymapPage::buildCustomCard()
{
	auto *card = Theme::card(this);
	auto *layout = static_cast<QVBoxLayout *>(card->layout());
	layout->setContentsMargins(18, 16, 18, 18);
	layout->setSpacing(10);
	layout->addWidget(Theme::cardTitle(tr("Extra keys"), card));
	layout->addWidget(Theme::caption(
		tr("Add a key on top of the standard map. A single gamepad button can own several extra "
		   "keys, which makes simple macros possible."),
		card));

	m_customList = new QListWidget(card);
	m_customList->setMinimumHeight(110);
	layout->addWidget(m_customList);

	auto *row = new QHBoxLayout();
	row->setSpacing(8);

	m_customButton = new QComboBox(card);
	for (const auto &info : kButtons)
		m_customButton->addItem(QString::fromUtf8(info.name), info.mask);
	row->addWidget(m_customButton);

	m_customName = new QLineEdit(card);
	m_customName->setPlaceholderText(tr("What it does (e.g. Open chat)"));
	row->addWidget(m_customName, 1);

	m_customKey = new ButtonInputBox(card);
	m_customKey->setPlaceholderText(tr("press a key"));
	m_customKey->setMinimumWidth(130);
	row->addWidget(m_customKey);

	auto *add = Theme::primaryButton(tr("Add key"), card);
	connect(add, &QPushButton::clicked, this, &KeymapPage::addCustomKey);
	row->addWidget(add);

	auto *remove = Theme::ghostButton(tr("Remove"), card);
	remove->setProperty("role", "danger");
	connect(remove, &QPushButton::clicked, this, &KeymapPage::removeCustomKey);
	row->addWidget(remove);

	layout->addLayout(row);
	return card;
}

void KeymapPage::showEvent(QShowEvent *event)
{
	QWidget::showEvent(event);
	loadFromProfile();
}

void KeymapPage::loadFromProfile()
{
	const auto &profile = SettingsSingleton::instance().activeKeymapProfile();

	for (auto it = m_buttonBoxes.constBegin(); it != m_buttonBoxes.constEnd(); ++it)
	{
		const auto button = static_cast<GamepadButtons>(it.key());
		it.value()->setKeyCodeAndDisplayName(profile.buttonMap(button),
											 profile.buttonDisplayName(button));
	}

	auto loadStick = [](const ThumbstickInput &input, StickEditor &editor)
	{
		if (input.is_mouse_scroll)
			editor.mode->setCurrentIndex(kStickMouseWheel);
		else if (input.is_mouse_move)
			editor.mode->setCurrentIndex(kStickMouseLook);
		else
			editor.mode->setCurrentIndex(kStickDirectional);

		editor.up->setKeyCodeAndDisplayName(input.up.vk, input.up.displayName);
		editor.down->setKeyCodeAndDisplayName(input.down.vk, input.down.displayName);
		editor.left->setKeyCodeAndDisplayName(input.left.vk, input.left.displayName);
		editor.right->setKeyCodeAndDisplayName(input.right.vk, input.right.displayName);
	};

	loadStick(profile.thumbstickInput(Thumbstick_Left), m_leftStick);
	loadStick(profile.thumbstickInput(Thumbstick_Right), m_rightStick);

	const TriggerInput leftTrigger = profile.triggerInput(Trigger::Left);
	const TriggerInput rightTrigger = profile.triggerInput(Trigger::Right);
	m_leftTrigger->setKeyCodeAndDisplayName(leftTrigger.button_input.vk,
											leftTrigger.button_input.displayName);
	m_rightTrigger->setKeyCodeAndDisplayName(rightTrigger.button_input.vk,
											 rightTrigger.button_input.displayName);
	m_leftThreshold->setCurrentIndex(
		std::clamp(static_cast<int>(leftTrigger.threshold * 10.0f) - 1, 0, 8));
	m_rightThreshold->setCurrentIndex(
		std::clamp(static_cast<int>(rightTrigger.threshold * 10.0f) - 1, 0, 8));

	m_customBindings.clear();
	for (const auto &binding : profile.customBindings())
		m_customBindings.append(binding);
	refreshCustomList();
}

void KeymapPage::refreshCustomList()
{
	m_customList->clear();
	for (const auto &binding : m_customBindings)
	{
		m_customList->addItem(tr("%1  ->  %2   (%3)")
								  .arg(QString::fromUtf8(buttonName(binding.gamepadMask)),
									   binding.displayName.isEmpty() ? tr("unknown key")
																	 : binding.displayName,
									   binding.name.isEmpty() ? tr("no description")
															  : binding.name));
	}
}

void KeymapPage::writeToProfile()
{
	auto &profile = SettingsSingleton::instance().activeKeymapProfile();

	for (auto it = m_buttonBoxes.constBegin(); it != m_buttonBoxes.constEnd(); ++it)
	{
		const auto button = static_cast<GamepadButtons>(it.key());
		profile.setButtonInput(button, it.value()->keyCode(), it.value()->displayName());
	}

	auto writeStick = [&profile](Thumbstick which, const StickEditor &editor)
	{
		ThumbstickInput input;
		const int mode = editor.mode->currentIndex();
		input.is_mouse_scroll = (mode == kStickMouseWheel);
		input.is_mouse_move = (mode == kStickMouseLook);

		auto fill = [](ButtonInput &target, const ButtonInputBox *box)
		{
			target.vk = box->keyCode();
			target.displayName = box->displayName();
			target.is_mouse_button = is_mouse_button(target.vk);
		};
		fill(input.up, editor.up);
		fill(input.down, editor.down);
		fill(input.left, editor.left);
		fill(input.right, editor.right);
		profile.setThumbstickInput(which, input);
	};

	writeStick(Thumbstick_Left, m_leftStick);
	writeStick(Thumbstick_Right, m_rightStick);

	auto writeTrigger = [&profile](Trigger which, const ButtonInputBox *box, int index)
	{
		TriggerInput trigger;
		trigger.button_input.vk = box->keyCode();
		trigger.button_input.displayName = box->displayName();
		trigger.button_input.is_mouse_button = is_mouse_button(trigger.button_input.vk);
		trigger.threshold = static_cast<float>(index + 1) / 10.0f;
		profile.setTriggerInput(which, trigger);
	};

	writeTrigger(Trigger::Left, m_leftTrigger, m_leftThreshold->currentIndex());
	writeTrigger(Trigger::Right, m_rightTrigger, m_rightThreshold->currentIndex());

	profile.clearCustomBindings();
	for (const auto &binding : m_customBindings)
		profile.addCustomBinding(binding);
}

void KeymapPage::saveProfile()
{
	writeToProfile();
	if (SettingsSingleton::instance().saveActiveProfile())
	{
		m_status->setText(tr("Saved to the \"%1\" profile.")
							  .arg(SettingsSingleton::instance().activeProfileName()));
		m_status->setStyleSheet(QStringLiteral("color: #37d67a;"));
		emit profileChanged();
	}
	else
	{
		m_status->setText(tr("Could not save the profile."));
		m_status->setStyleSheet(QStringLiteral("color: #ff8ba0;"));
	}
}

void KeymapPage::resetProfile()
{
	writeToProfile();
	SettingsSingleton::instance().activeKeymapProfile().initializeDefaultMappings();
	SettingsSingleton::instance().saveActiveProfile();
	loadFromProfile();
	m_status->setText(tr("Restored the default mappings."));
	m_status->setStyleSheet(QStringLiteral("color: #facc15;"));
	emit profileChanged();
}

void KeymapPage::changeProfile(int index)
{
	if (index < 0)
		return;

	const QString name = m_profile->itemText(index);
	if (name == SettingsSingleton::instance().activeProfileName())
		return;

	if (SettingsSingleton::instance().loadProfile(name))
	{
		loadFromProfile();
		m_status->setText(tr("Editing profile \"%1\".").arg(name));
		m_status->setStyleSheet(QStringLiteral("color: #98a0b8;"));
		emit profileChanged();
	}
	else
	{
		m_status->setText(tr("Could not open profile \"%1\".").arg(name));
		m_status->setStyleSheet(QStringLiteral("color: #ff8ba0;"));
	}
}

void KeymapPage::addCustomKey()
{
	if (m_customKey->keyCode() == 0)
	{
		m_status->setText(tr("Press a key in the last box before adding."));
		m_status->setStyleSheet(QStringLiteral("color: #facc15;"));
		return;
	}

	CustomBinding binding;
	binding.gamepadMask = m_customButton->currentData().toUInt();
	binding.name = m_customName->text().trimmed();
	binding.vk = m_customKey->keyCode();
	binding.displayName = m_customKey->displayName();

	m_customBindings.append(binding);
	refreshCustomList();

	m_customName->clear();
	m_customKey->clearKeyCode();
	m_status->setText(tr("Added \"%1\". Press Save profile to keep it.")
						  .arg(binding.displayName));
	m_status->setStyleSheet(QStringLiteral("color: #37d67a;"));
}

void KeymapPage::removeCustomKey()
{
	const int row = m_customList->currentRow();
	if (row < 0 || row >= m_customBindings.size())
	{
		m_status->setText(tr("Select an extra key to remove it."));
		m_status->setStyleSheet(QStringLiteral("color: #facc15;"));
		return;
	}

	m_customBindings.remove(row);
	refreshCustomList();
	m_status->setText(tr("Removed. Press Save profile to keep the change."));
	m_status->setStyleSheet(QStringLiteral("color: #98a0b8;"));
}
