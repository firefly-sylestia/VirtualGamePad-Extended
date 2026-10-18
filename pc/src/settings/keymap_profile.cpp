#include "keymap_profile.hpp"

#include "../ui/buttoninputbox.hpp"
#include "settings.hpp"

#include <QDebug>
#include <QSettings>

#ifdef WIN32
#include <windows.h>
#elif defined(__linux__)
#include <linux/input.h>
#endif

void KeymapProfile::initializeDefaultMappings()
{
	m_customBindings.clear();

	// Initialize default display names for buttons
	buttonDisplayNames = {{GamepadButtons::GamepadButtons_Menu, "Menu"},
						  {GamepadButtons::GamepadButtons_View, "Tab"},
						  {GamepadButtons::GamepadButtons_A, "Enter"},
						  {GamepadButtons::GamepadButtons_B, "B"},
						  {GamepadButtons::GamepadButtons_X, "Shift"},
						  {GamepadButtons::GamepadButtons_Y, "Ctrl"},
						  {GamepadButtons::GamepadButtons_DPadUp, "Up"},
						  {GamepadButtons::GamepadButtons_DPadDown, "Down"},
						  {GamepadButtons::GamepadButtons_DPadLeft, "Left"},
						  {GamepadButtons::GamepadButtons_DPadRight, "Right"},
						  {GamepadButtons::GamepadButtons_LeftShoulder, "Left Click"},
						  {GamepadButtons::GamepadButtons_RightShoulder, "Right Click"},
						  {GamepadButtons::GamepadButtons_LeftThumbstick, "Shift"},
						  {GamepadButtons::GamepadButtons_RightThumbstick, "Ctrl"},
						  {GamepadButtons::GamepadButtons_Plus, "+"},
						  {GamepadButtons::GamepadButtons_Minus, "-"},
						  {GamepadButtons::GamepadButtons_Home, "Home"},
						  {GamepadButtons::GamepadButtons_Capture, "Print Screen"},
						  {GamepadButtons::GamepadButtons_LeftSide, "Q"},
						  {GamepadButtons::GamepadButtons_RightSide, "E"},
						  {GamepadButtons::GamepadButtons_Paddle1, "1"},
						  {GamepadButtons::GamepadButtons_Paddle2, "2"},
						  {GamepadButtons::GamepadButtons_Paddle3, "3"},
						  {GamepadButtons::GamepadButtons_Paddle4, "4"}};

#ifdef WIN32
	buttonMappings = {{GamepadButtons::GamepadButtons_Menu, VK_MENU},
					  {GamepadButtons::GamepadButtons_View, VK_TAB},
					  {GamepadButtons::GamepadButtons_A, VK_RETURN},
					  // B sends the letter B. Escape was the old default and had to go: in
					  // nearly every full-screen game it drops out of the game or opens the
					  // menu, so a stray press threw the session away. Backspace replaced it
					  // for the same reason, and the letter is what a game that names a key
					  // "B" actually reads. A player who wants cancel can bind it by hand,
					  // and the phone can send Escape from a custom key button.
					  {GamepadButtons::GamepadButtons_B, 'B'},
					  {GamepadButtons::GamepadButtons_X, VK_SHIFT},
					  {GamepadButtons::GamepadButtons_Y, VK_CONTROL},
					  {GamepadButtons::GamepadButtons_DPadUp, VK_UP},
					  {GamepadButtons::GamepadButtons_DPadDown, VK_DOWN},
					  {GamepadButtons::GamepadButtons_DPadLeft, VK_LEFT},
					  {GamepadButtons::GamepadButtons_DPadRight, VK_RIGHT},
					  {GamepadButtons::GamepadButtons_LeftShoulder, VK_LBUTTON},
					  {GamepadButtons::GamepadButtons_RightShoulder, VK_RBUTTON},
					  {GamepadButtons::GamepadButtons_LeftThumbstick, VK_SHIFT},
					  {GamepadButtons::GamepadButtons_RightThumbstick, VK_CONTROL},
					  {GamepadButtons::GamepadButtons_Plus, VK_ADD},
					  {GamepadButtons::GamepadButtons_Minus, VK_SUBTRACT},
					  {GamepadButtons::GamepadButtons_Home, VK_HOME},
					  {GamepadButtons::GamepadButtons_Capture, VK_SNAPSHOT},
					  {GamepadButtons::GamepadButtons_LeftSide, 'Q'},
					  {GamepadButtons::GamepadButtons_RightSide, 'E'},
					  {GamepadButtons::GamepadButtons_Paddle1, '1'},
					  {GamepadButtons::GamepadButtons_Paddle2, '2'},
					  {GamepadButtons::GamepadButtons_Paddle3, '3'},
					  {GamepadButtons::GamepadButtons_Paddle4, '4'}};

	thumbstickMappings = {
		{Thumbstick_Left,
		 {false, {'W', false, "W"}, {'S', false, "S"}, {'A', false, "A"}, {'D', false, "D"}}},
		{Thumbstick_Right,
		 {true,
		  {VK_UP, false, "Up"},
		  {VK_DOWN, false, "Down"},
		  {VK_LEFT, false, "Left"},
		  {VK_RIGHT, false, "Right"}}}};

	triggerMappings = {{Trigger::Left, {{VK_SHIFT, false, "Shift"}, 0.5f}},
					   {Trigger::Right, {{VK_CONTROL, false, "Ctrl"}, 0.5f}}};
#elif defined(__linux__)
	buttonMappings = {{GamepadButtons::GamepadButtons_Menu, KEY_MENU},
					  {GamepadButtons::GamepadButtons_View, KEY_TAB},
					  {GamepadButtons::GamepadButtons_A, KEY_ENTER},
					  // See the Windows mapping above: B is the letter, not Escape, which
					  // drops full-screen games out of full screen.
					  {GamepadButtons::GamepadButtons_B, KEY_B},
					  {GamepadButtons::GamepadButtons_X, KEY_LEFTSHIFT},
					  {GamepadButtons::GamepadButtons_Y, KEY_LEFTCTRL},
					  {GamepadButtons::GamepadButtons_DPadUp, KEY_UP},
					  {GamepadButtons::GamepadButtons_DPadDown, KEY_DOWN},
					  {GamepadButtons::GamepadButtons_DPadLeft, KEY_LEFT},
					  {GamepadButtons::GamepadButtons_DPadRight, KEY_RIGHT},
					  {GamepadButtons::GamepadButtons_LeftShoulder, BTN_LEFT},
					  {GamepadButtons::GamepadButtons_RightShoulder, BTN_RIGHT},
					  {GamepadButtons::GamepadButtons_LeftThumbstick, KEY_LEFTSHIFT},
					  {GamepadButtons::GamepadButtons_RightThumbstick, KEY_LEFTCTRL},
					  {GamepadButtons::GamepadButtons_Plus, KEY_KPPLUS},
					  {GamepadButtons::GamepadButtons_Minus, KEY_KPMINUS},
					  {GamepadButtons::GamepadButtons_Home, KEY_HOME},
					  {GamepadButtons::GamepadButtons_Capture, KEY_SYSRQ},
					  {GamepadButtons::GamepadButtons_LeftSide, KEY_Q},
					  {GamepadButtons::GamepadButtons_RightSide, KEY_E},
					  {GamepadButtons::GamepadButtons_Paddle1, KEY_1},
					  {GamepadButtons::GamepadButtons_Paddle2, KEY_2},
					  {GamepadButtons::GamepadButtons_Paddle3, KEY_3},
					  {GamepadButtons::GamepadButtons_Paddle4, KEY_4}};

	thumbstickMappings = {
		{Thumbstick_Left,
		 {false, {KEY_W, false, "W"}, {KEY_S, false, "S"}, {KEY_A, false, "A"}, {KEY_D, false, "D"}}},
		{Thumbstick_Right,
		 {true,
		  {KEY_UP, false, "Up"},
		  {KEY_DOWN, false, "Down"},
		  {KEY_LEFT, false, "Left"},
		  {KEY_RIGHT, false, "Right"}}}};

	triggerMappings = {{Trigger::Left, {{KEY_LEFTSHIFT, false, "Left Shift"}, 0.5f}},
					   {Trigger::Right, {{KEY_LEFTCTRL, false, "Left Ctrl"}, 0.5f}}};
#endif
}

bool KeymapProfile::load(const QString &profilePath) noexcept
{
	m_migratedBackKey = false;
	if (!QFile::exists(profilePath))
	{
		qInfo() << tr("Profile file %1 does not exist. Loading defaults.").arg(profilePath);
		initializeDefaultMappings();
		return true;
	}
	try
	{
		QSettings settings(profilePath, QSettings::IniFormat);
		loadFromSettings(settings);
		if (m_migratedBackKey || m_migratedRightStickMouse)
		{
			// Write the migrated values back, so the next run starts from them too.
			QSettings writeBack(profilePath, QSettings::IniFormat);
			saveToSettings(writeBack);
			writeBack.sync();
			qInfo() << "Migrated profile" << profilePath
					<< "(B to Backspace:" << m_migratedBackKey
					<< ", right stick to mouse look:" << m_migratedRightStickMouse << ")";
			m_migratedBackKey = false;
			m_migratedRightStickMouse = false;
		}
	}
	catch (...)
	{
		qInfo() << tr("Error loading keymap profile %1. Loading default mappings.").arg(profilePath);
		initializeDefaultMappings();
	}
	return true;
}

bool KeymapProfile::save(const QString &profilePath) const
{
	QSettings settings(profilePath, QSettings::IniFormat);
	saveToSettings(settings);
	settings.sync();
	return true;
}

void KeymapProfile::addCustomBinding(const CustomBinding &binding)
{
	if (binding.gamepadMask == 0 || binding.vk == 0)
		return;
	m_customBindings.push_back(binding);
}

void KeymapProfile::setCustomBinding(size_t index, const CustomBinding &binding)
{
	if (index >= m_customBindings.size())
		return;
	m_customBindings[index] = binding;
}

void KeymapProfile::removeCustomBinding(size_t index)
{
	if (index >= m_customBindings.size())
		return;
	m_customBindings.erase(m_customBindings.begin() + static_cast<std::ptrdiff_t>(index));
}

void KeymapProfile::clearCustomBindings()
{
	m_customBindings.clear();
}

InputKeyCode KeymapProfile::buttonMap(GamepadButtons btn) const
{
	auto it = buttonMappings.find(btn);
	return it != buttonMappings.end() ? it->second : 0;
}

void KeymapProfile::setButtonInput(GamepadButtons btn, InputKeyCode vk, const QString &displayName)
{
	buttonMappings[btn] = vk;
	buttonDisplayNames[btn] = displayName;
}

QString KeymapProfile::buttonDisplayName(GamepadButtons btn) const
{
	auto it = buttonDisplayNames.find(btn);
	return it != buttonDisplayNames.end() ? it->second : QString();
}

void KeymapProfile::setThumbstickInput(Thumbstick thumb, const ThumbstickInput &input)
{
	thumbstickMappings[thumb] = input;
}

ThumbstickInput KeymapProfile::thumbstickInput(Thumbstick thumb) const
{
	auto it = thumbstickMappings.find(thumb);
	return (it != thumbstickMappings.end()) ? it->second : ThumbstickInput{};
}

void KeymapProfile::setLeftThumbMouseMove(bool enabled)
{
	thumbstickMappings[Thumbstick_Left].is_mouse_move = enabled;
}

bool KeymapProfile::leftThumbMouseMove() const
{
	auto it = thumbstickMappings.find(Thumbstick_Left);
	return (it != thumbstickMappings.end()) ? it->second.is_mouse_move : false;
}

void KeymapProfile::setRightThumbMouseMove(bool enabled)
{
	thumbstickMappings[Thumbstick_Right].is_mouse_move = enabled;
}

bool KeymapProfile::rightThumbMouseMove() const
{
	auto it = thumbstickMappings.find(Thumbstick_Right);
	return (it != thumbstickMappings.end()) ? it->second.is_mouse_move : false;
}

ButtonInput KeymapProfile::buttonInput(GamepadButtons button) const
{
	ButtonInput input;
	if (auto it = buttonMappings.find(button); it != buttonMappings.end())
	{
		input.vk = it->second;
		input.is_mouse_button = is_mouse_button(input.vk);
		input.displayName = buttonDisplayName(button);
	}
	return input;
}

void KeymapProfile::setTriggerInput(Trigger trigger, const TriggerInput &input)
{
	triggerMappings[trigger] = input;
}

TriggerInput KeymapProfile::triggerInput(Trigger trigger) const
{
	if (auto it = triggerMappings.find(trigger); it != triggerMappings.end())
	{
		return it->second;
	}
	return TriggerInput{}; // Return default TriggerInput if not found
}

void KeymapProfile::loadFromSettings(QSettings const &settings)
{
	qDebug() << "Loading button mappings from file:" << settings.fileName();

	// Clear existing mappings
	buttonMappings.clear();
	buttonDisplayNames.clear();
	m_migratedBackKey = false;
	m_migratedRightStickMouse = false;

	// Load with platform-specific defaults
#ifdef WIN32
	buttonMappings[GamepadButtons_A] = settings.value("buttons/A", VK_RETURN).toUInt();
	buttonMappings[GamepadButtons_B] = settings.value("buttons/B", 'B').toUInt();
	buttonMappings[GamepadButtons_X] = settings.value("buttons/X", VK_SHIFT).toUInt();
	buttonMappings[GamepadButtons_Y] = settings.value("buttons/Y", VK_CONTROL).toUInt();
	buttonMappings[GamepadButtons_RightShoulder] = settings.value("buttons/RS", VK_RBUTTON).toUInt();
	buttonMappings[GamepadButtons_LeftShoulder] = settings.value("buttons/LS", VK_LBUTTON).toUInt();
	buttonMappings[GamepadButtons_DPadDown] = settings.value("buttons/DPADDOWN", VK_DOWN).toUInt();
	buttonMappings[GamepadButtons_DPadUp] = settings.value("buttons/DPADUP", VK_UP).toUInt();
	buttonMappings[GamepadButtons_DPadRight] = settings.value("buttons/DPADRIGHT", VK_RIGHT).toUInt();
	buttonMappings[GamepadButtons_DPadLeft] = settings.value("buttons/DPADLEFT", VK_LEFT).toUInt();
	buttonMappings[GamepadButtons_View] = settings.value("buttons/VIEW", VK_TAB).toUInt();
	buttonMappings[GamepadButtons_Menu] = settings.value("buttons/MENU", VK_APPS).toUInt();
	buttonMappings[GamepadButtons_LeftThumbstick] = settings.value("buttons/LTHUMB", 0).toUInt();
	buttonMappings[GamepadButtons_RightThumbstick] = settings.value("buttons/RTHUMB", 0).toUInt();
	buttonMappings[GamepadButtons_Plus] = settings.value("buttons/PLUS", VK_ADD).toUInt();
	buttonMappings[GamepadButtons_Minus] = settings.value("buttons/MINUS", VK_SUBTRACT).toUInt();
	buttonMappings[GamepadButtons_Home] = settings.value("buttons/HOME", VK_HOME).toUInt();
	buttonMappings[GamepadButtons_Capture] = settings.value("buttons/CAPTURE", VK_SNAPSHOT).toUInt();
	buttonMappings[GamepadButtons_LeftSide] = settings.value("buttons/SL", 'Q').toUInt();
	buttonMappings[GamepadButtons_RightSide] = settings.value("buttons/SR", 'E').toUInt();
	buttonMappings[GamepadButtons_Paddle1] = settings.value("buttons/P1", '1').toUInt();
	buttonMappings[GamepadButtons_Paddle2] = settings.value("buttons/P2", '2').toUInt();
	buttonMappings[GamepadButtons_Paddle3] = settings.value("buttons/P3", '3').toUInt();
	buttonMappings[GamepadButtons_Paddle4] = settings.value("buttons/P4", '4').toUInt();
#elif defined(__linux__)
	buttonMappings[GamepadButtons_A] = settings.value("buttons/A", KEY_ENTER).toUInt();
	buttonMappings[GamepadButtons_B] = settings.value("buttons/B", KEY_B).toUInt();
	buttonMappings[GamepadButtons_X] = settings.value("buttons/X", KEY_LEFTSHIFT).toUInt();
	buttonMappings[GamepadButtons_Y] = settings.value("buttons/Y", KEY_LEFTCTRL).toUInt();
	buttonMappings[GamepadButtons_RightShoulder] = settings.value("buttons/RS", BTN_RIGHT).toUInt();
	buttonMappings[GamepadButtons_LeftShoulder] = settings.value("buttons/LS", BTN_LEFT).toUInt();
	buttonMappings[GamepadButtons_DPadDown] = settings.value("buttons/DPADDOWN", KEY_DOWN).toUInt();
	buttonMappings[GamepadButtons_DPadUp] = settings.value("buttons/DPADUP", KEY_UP).toUInt();
	buttonMappings[GamepadButtons_DPadRight] = settings.value("buttons/DPADRIGHT", KEY_RIGHT).toUInt();
	buttonMappings[GamepadButtons_DPadLeft] = settings.value("buttons/DPADLEFT", KEY_LEFT).toUInt();
	buttonMappings[GamepadButtons_View] = settings.value("buttons/VIEW", KEY_TAB).toUInt();
	buttonMappings[GamepadButtons_Menu] = settings.value("buttons/MENU", KEY_MENU).toUInt();
	buttonMappings[GamepadButtons_LeftThumbstick] = settings.value("buttons/LTHUMB", 0).toUInt();
	buttonMappings[GamepadButtons_RightThumbstick] = settings.value("buttons/RTHUMB", 0).toUInt();
	buttonMappings[GamepadButtons_Plus] = settings.value("buttons/PLUS", KEY_KPPLUS).toUInt();
	buttonMappings[GamepadButtons_Minus] = settings.value("buttons/MINUS", KEY_KPMINUS).toUInt();
	buttonMappings[GamepadButtons_Home] = settings.value("buttons/HOME", KEY_HOME).toUInt();
	buttonMappings[GamepadButtons_Capture] = settings.value("buttons/CAPTURE", KEY_SYSRQ).toUInt();
	buttonMappings[GamepadButtons_LeftSide] = settings.value("buttons/SL", KEY_Q).toUInt();
	buttonMappings[GamepadButtons_RightSide] = settings.value("buttons/SR", KEY_E).toUInt();
	buttonMappings[GamepadButtons_Paddle1] = settings.value("buttons/P1", KEY_1).toUInt();
	buttonMappings[GamepadButtons_Paddle2] = settings.value("buttons/P2", KEY_2).toUInt();
	buttonMappings[GamepadButtons_Paddle3] = settings.value("buttons/P3", KEY_3).toUInt();
	buttonMappings[GamepadButtons_Paddle4] = settings.value("buttons/P4", KEY_4).toUInt();
#endif

	// Load display names
	buttonDisplayNames[GamepadButtons_A] = settings.value("button_display_names/A", "").toString();
	buttonDisplayNames[GamepadButtons_B] = settings.value("button_display_names/B", "").toString();
	buttonDisplayNames[GamepadButtons_X] = settings.value("button_display_names/X", "").toString();
	buttonDisplayNames[GamepadButtons_Y] = settings.value("button_display_names/Y", "").toString();
	buttonDisplayNames[GamepadButtons_RightShoulder] =
		settings.value("button_display_names/RS", "").toString();
	buttonDisplayNames[GamepadButtons_LeftShoulder] =
		settings.value("button_display_names/LS", "").toString();
	buttonDisplayNames[GamepadButtons_DPadDown] =
		settings.value("button_display_names/DPADDOWN", "").toString();
	buttonDisplayNames[GamepadButtons_DPadUp] =
		settings.value("button_display_names/DPADUP", "").toString();
	buttonDisplayNames[GamepadButtons_DPadRight] =
		settings.value("button_display_names/DPADRIGHT", "").toString();
	buttonDisplayNames[GamepadButtons_DPadLeft] =
		settings.value("button_display_names/DPADLEFT", "").toString();
	buttonDisplayNames[GamepadButtons_View] = settings.value("button_display_names/VIEW", "").toString();
	buttonDisplayNames[GamepadButtons_Menu] = settings.value("button_display_names/MENU", "").toString();
	buttonDisplayNames[GamepadButtons_LeftThumbstick] =
		settings.value("button_display_names/LTHUMB", "").toString();
	buttonDisplayNames[GamepadButtons_RightThumbstick] =
		settings.value("button_display_names/RTHUMB", "").toString();
	buttonDisplayNames[GamepadButtons_Plus] = settings.value("button_display_names/PLUS", "+").toString();
	buttonDisplayNames[GamepadButtons_Minus] = settings.value("button_display_names/MINUS", "-").toString();
	buttonDisplayNames[GamepadButtons_Home] = settings.value("button_display_names/HOME", "Home").toString();
	buttonDisplayNames[GamepadButtons_Capture] = settings.value("button_display_names/CAPTURE", "Print Screen").toString();
	buttonDisplayNames[GamepadButtons_LeftSide] = settings.value("button_display_names/SL", "Q").toString();
	buttonDisplayNames[GamepadButtons_RightSide] = settings.value("button_display_names/SR", "E").toString();
	buttonDisplayNames[GamepadButtons_Paddle1] = settings.value("button_display_names/P1", "1").toString();
	buttonDisplayNames[GamepadButtons_Paddle2] = settings.value("button_display_names/P2", "2").toString();
	buttonDisplayNames[GamepadButtons_Paddle3] = settings.value("button_display_names/P3", "3").toString();
	buttonDisplayNames[GamepadButtons_Paddle4] = settings.value("button_display_names/P4", "4").toString();

	// A profile saved before the default changed carries the old one — Escape
	// first, then Backspace — as the value of B. Neither came from a choice, so
	// both are migrated to the letter B. A hand-picked key keeps its own display
	// name and is left exactly as it was.
#ifdef _WIN32
	const InputKeyCode legacyBackKey = VK_ESCAPE;
	const InputKeyCode previousBackKey = VK_BACK;
	const InputKeyCode backKey = 'B';
#elif defined(__linux__)
	const InputKeyCode legacyBackKey = KEY_ESC;
	const InputKeyCode previousBackKey = KEY_BACKSPACE;
	const InputKeyCode backKey = KEY_B;
#endif
	const InputKeyCode currentBackKey = buttonMappings[GamepadButtons_B];
	const QString backName = buttonDisplayNames[GamepadButtons_B];
	const bool wasDefaultEscape = currentBackKey == legacyBackKey &&
			(backName.isEmpty() || backName == QStringLiteral("Escape") ||
			 backName == QStringLiteral("Backspace"));
	const bool wasDefaultBackspace = currentBackKey == previousBackKey &&
			(backName.isEmpty() || backName == QStringLiteral("Backspace"));
	if (wasDefaultEscape || wasDefaultBackspace)
	{
		buttonMappings[GamepadButtons_B] = backKey;
		buttonDisplayNames[GamepadButtons_B] = QStringLiteral("B");
		m_migratedBackKey = true;
	}

	// Log mappings for debugging
	qDebug() << "Loaded mappings:"
			 << "A:" << buttonMappings[GamepadButtons_A] << "B:" << buttonMappings[GamepadButtons_B]
			 << "X:" << buttonMappings[GamepadButtons_X] << "Y:" << buttonMappings[GamepadButtons_Y]
			 << "RS:" << buttonMappings[GamepadButtons_RightShoulder]
			 << "LS:" << buttonMappings[GamepadButtons_LeftShoulder]
			 << "DPADDOWN:" << buttonMappings[GamepadButtons_DPadDown]
			 << "DPADUP:" << buttonMappings[GamepadButtons_DPadUp]
			 << "DPADRIGHT:" << buttonMappings[GamepadButtons_DPadRight]
			 << "DPADLEFT:" << buttonMappings[GamepadButtons_DPadLeft]
			 << "VIEW:" << buttonMappings[GamepadButtons_View]
			 << "MENU:" << buttonMappings[GamepadButtons_Menu];

	// Thumbstick mappings
	ThumbstickInput left, right;
	left.is_mouse_move =
		settings.value(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstick], false).toBool();
	left.up.vk =
		settings.value(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickUpKey], 'W')
			.toUInt();
	left.down.vk =
		settings.value(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickDownKey], 'S')
			.toUInt();
	left.left.vk =
		settings.value(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickLeftKey], 'A')
			.toUInt();
	left.right.vk =
		settings.value(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickRightKey], 'D')
			.toUInt();

	right.is_mouse_move =
		settings.value(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstick], false).toBool();
	right.up.vk =
		settings.value(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickUpKey], Qt::Key_Up)
			.toUInt();
	right.down.vk =
		settings
			.value(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickDownKey], Qt::Key_Down)
			.toUInt();
	right.left.vk =
		settings
			.value(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickLeftKey], Qt::Key_Left)
			.toUInt();
	right.right.vk = settings
						 .value(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickRightKey],
								Qt::Key_Right)
						 .toUInt();

	// Load thumbstick display names
	left.up.displayName = settings.value("thumbstick_display_names/LeftThumbstickUp", "").toString();
	left.down.displayName = settings.value("thumbstick_display_names/LeftThumbstickDown", "").toString();
	left.left.displayName = settings.value("thumbstick_display_names/LeftThumbstickLeft", "").toString();
	left.right.displayName = settings.value("thumbstick_display_names/LeftThumbstickRight", "").toString();
	right.up.displayName = settings.value("thumbstick_display_names/RightThumbstickUp", "").toString();
	right.down.displayName = settings.value("thumbstick_display_names/RightThumbstickDown", "").toString();
	right.left.displayName = settings.value("thumbstick_display_names/RightThumbstickLeft", "").toString();
	right.right.displayName =
		settings.value("thumbstick_display_names/RightThumbstickRight", "").toString();

	// The right stick used to default to the arrow keys, which the D-pad already
	// sends; the camera is what a right stick is for on a PC. A profile that has
	// never been saved since is migrated once, and the marker written on the next
	// save keeps a deliberate choice from being changed back.
#ifdef WIN32
	const bool defaultArrowStick = right.up.vk == VK_UP && right.down.vk == VK_DOWN &&
								   right.left.vk == VK_LEFT && right.right.vk == VK_RIGHT;
#elif defined(__linux__)
	const bool defaultArrowStick = right.up.vk == KEY_UP && right.down.vk == KEY_DOWN &&
								   right.left.vk == KEY_LEFT && right.right.vk == KEY_RIGHT;
#else
	const bool defaultArrowStick = false;
#endif
	if (!right.is_mouse_move && defaultArrowStick &&
		!settings.value("thumbsticks/RightThumbstickMouseDefault", false).toBool())
	{
		right.is_mouse_move = true;
		m_migratedRightStickMouse = true;
	}

	thumbstickMappings[Thumbstick_Left] = left;
	thumbstickMappings[Thumbstick_Right] = right;

	// Load trigger mappings
	TriggerInput leftTrigger;
	TriggerInput rightTrigger;

#ifdef WIN32
	leftTrigger.button_input.vk =
		settings.value(trigger_settings[setting_keys::trigger_keys::LeftTriggerKey], VK_SHIFT).toUInt();
	rightTrigger.button_input.vk =
		settings.value(trigger_settings[setting_keys::trigger_keys::RightTriggerKey], VK_CONTROL).toUInt();
#elif defined(__linux__)
	leftTrigger.button_input.vk =
		settings.value(trigger_settings[setting_keys::trigger_keys::LeftTriggerKey], KEY_LEFTSHIFT)
			.toUInt();
	rightTrigger.button_input.vk =
		settings.value(trigger_settings[setting_keys::trigger_keys::RightTriggerKey], KEY_LEFTCTRL)
			.toUInt();
#endif

	leftTrigger.threshold =
		settings.value(trigger_settings[setting_keys::trigger_keys::LeftTriggerThreshold], 0.5f).toFloat();
	rightTrigger.threshold =
		settings.value(trigger_settings[setting_keys::trigger_keys::RightTriggerThreshold], 0.5f).toFloat();

	leftTrigger.button_input.is_mouse_button = is_mouse_button(leftTrigger.button_input.vk);
	rightTrigger.button_input.is_mouse_button = is_mouse_button(rightTrigger.button_input.vk);

	// Load trigger display names
	leftTrigger.button_input.displayName =
		settings.value("trigger_display_names/LeftTrigger", "").toString();
	rightTrigger.button_input.displayName =
		settings.value("trigger_display_names/RightTrigger", "").toString();

	triggerMappings[Trigger::Left] = leftTrigger;
	triggerMappings[Trigger::Right] = rightTrigger;

	// Thumbstick mouse-wheel scrolling (used for hotbar/inventory cycling)
	thumbstickMappings[Thumbstick_Left].is_mouse_scroll =
		settings.value("thumbsticks/LeftThumbstickScroll", false).toBool();
	thumbstickMappings[Thumbstick_Right].is_mouse_scroll =
		settings.value("thumbsticks/RightThumbstickScroll", false).toBool();

	// Extra user-defined keys
	m_customBindings.clear();
	const int customCount = settings.value("custom/count", 0).toInt();
	for (int i = 0; i < customCount; ++i)
	{
		CustomBinding binding;
		binding.gamepadMask = settings.value(QStringLiteral("custom/%1/mask").arg(i), 0).toUInt();
		binding.name = settings.value(QStringLiteral("custom/%1/name").arg(i), "").toString();
		binding.vk = settings.value(QStringLiteral("custom/%1/vk").arg(i), 0).toUInt();
		binding.displayName =
			settings.value(QStringLiteral("custom/%1/display").arg(i), "").toString();
		if (binding.gamepadMask != 0 && binding.vk != 0)
			m_customBindings.push_back(binding);
	}
	qDebug() << "Loaded" << m_customBindings.size() << "custom key bindings";
}

void KeymapProfile::saveToSettings(QSettings &settings) const
{
	qDebug() << "Saving button mappings to file:";

	// Clear existing sections to ensure clean save
	settings.remove("buttons");
	settings.remove("button_display_names");
	settings.remove("thumbsticks");
	settings.remove("thumbstick_display_names");
	settings.remove("triggers");
	settings.remove("trigger_display_names");
	settings.remove("custom");

	// Button mappings - Use explicit mapping to ensure correct values
	// Map GamepadButtons directly to settings keys
	settings.setValue("buttons/A", buttonMap(GamepadButtons_A));
	settings.setValue("buttons/B", buttonMap(GamepadButtons_B));
	settings.setValue("buttons/X", buttonMap(GamepadButtons_X));
	settings.setValue("buttons/Y", buttonMap(GamepadButtons_Y));
	settings.setValue("buttons/RS", buttonMap(GamepadButtons_RightShoulder));
	settings.setValue("buttons/LS", buttonMap(GamepadButtons_LeftShoulder));
	settings.setValue("buttons/DPADDOWN", buttonMap(GamepadButtons_DPadDown));
	settings.setValue("buttons/DPADUP", buttonMap(GamepadButtons_DPadUp));
	settings.setValue("buttons/DPADRIGHT", buttonMap(GamepadButtons_DPadRight));
	settings.setValue("buttons/DPADLEFT", buttonMap(GamepadButtons_DPadLeft));
	settings.setValue("buttons/VIEW", buttonMap(GamepadButtons_View));
	settings.setValue("buttons/MENU", buttonMap(GamepadButtons_Menu));
	settings.setValue("buttons/LTHUMB", buttonMap(GamepadButtons_LeftThumbstick));
	settings.setValue("buttons/RTHUMB", buttonMap(GamepadButtons_RightThumbstick));
	settings.setValue("buttons/PLUS", buttonMap(GamepadButtons_Plus));
	settings.setValue("buttons/MINUS", buttonMap(GamepadButtons_Minus));
	settings.setValue("buttons/HOME", buttonMap(GamepadButtons_Home));
	settings.setValue("buttons/CAPTURE", buttonMap(GamepadButtons_Capture));
	settings.setValue("buttons/SL", buttonMap(GamepadButtons_LeftSide));
	settings.setValue("buttons/SR", buttonMap(GamepadButtons_RightSide));
	settings.setValue("buttons/P1", buttonMap(GamepadButtons_Paddle1));
	settings.setValue("buttons/P2", buttonMap(GamepadButtons_Paddle2));
	settings.setValue("buttons/P3", buttonMap(GamepadButtons_Paddle3));
	settings.setValue("buttons/P4", buttonMap(GamepadButtons_Paddle4));

	// Button display names
	settings.setValue("button_display_names/A", buttonDisplayName(GamepadButtons_A));
	settings.setValue("button_display_names/B", buttonDisplayName(GamepadButtons_B));
	settings.setValue("button_display_names/X", buttonDisplayName(GamepadButtons_X));
	settings.setValue("button_display_names/Y", buttonDisplayName(GamepadButtons_Y));
	settings.setValue("button_display_names/RS", buttonDisplayName(GamepadButtons_RightShoulder));
	settings.setValue("button_display_names/LS", buttonDisplayName(GamepadButtons_LeftShoulder));
	settings.setValue("button_display_names/DPADDOWN", buttonDisplayName(GamepadButtons_DPadDown));
	settings.setValue("button_display_names/DPADUP", buttonDisplayName(GamepadButtons_DPadUp));
	settings.setValue("button_display_names/DPADRIGHT", buttonDisplayName(GamepadButtons_DPadRight));
	settings.setValue("button_display_names/DPADLEFT", buttonDisplayName(GamepadButtons_DPadLeft));
	settings.setValue("button_display_names/VIEW", buttonDisplayName(GamepadButtons_View));
	settings.setValue("button_display_names/MENU", buttonDisplayName(GamepadButtons_Menu));
	settings.setValue("button_display_names/LTHUMB", buttonDisplayName(GamepadButtons_LeftThumbstick));
	settings.setValue("button_display_names/RTHUMB", buttonDisplayName(GamepadButtons_RightThumbstick));
	settings.setValue("button_display_names/PLUS", buttonDisplayName(GamepadButtons_Plus));
	settings.setValue("button_display_names/MINUS", buttonDisplayName(GamepadButtons_Minus));
	settings.setValue("button_display_names/HOME", buttonDisplayName(GamepadButtons_Home));
	settings.setValue("button_display_names/CAPTURE", buttonDisplayName(GamepadButtons_Capture));
	settings.setValue("button_display_names/SL", buttonDisplayName(GamepadButtons_LeftSide));
	settings.setValue("button_display_names/SR", buttonDisplayName(GamepadButtons_RightSide));
	settings.setValue("button_display_names/P1", buttonDisplayName(GamepadButtons_Paddle1));
	settings.setValue("button_display_names/P2", buttonDisplayName(GamepadButtons_Paddle2));
	settings.setValue("button_display_names/P3", buttonDisplayName(GamepadButtons_Paddle3));
	settings.setValue("button_display_names/P4", buttonDisplayName(GamepadButtons_Paddle4));

	// Log mapping values for debugging
	qDebug() << "A:" << buttonMap(GamepadButtons_A) << "B:" << buttonMap(GamepadButtons_B)
			 << "X:" << buttonMap(GamepadButtons_X) << "Y:" << buttonMap(GamepadButtons_Y)
			 << "RS:" << buttonMap(GamepadButtons_RightShoulder)
			 << "LS:" << buttonMap(GamepadButtons_LeftShoulder)
			 << "DPADDOWN:" << buttonMap(GamepadButtons_DPadDown)
			 << "DPADUP:" << buttonMap(GamepadButtons_DPadUp)
			 << "DPADRIGHT:" << buttonMap(GamepadButtons_DPadRight)
			 << "DPADLEFT:" << buttonMap(GamepadButtons_DPadLeft)
			 << "VIEW:" << buttonMap(GamepadButtons_View) << "MENU:" << buttonMap(GamepadButtons_Menu);

	// Thumbstick mappings - keep unchanged
	const auto &left = thumbstickMappings.at(Thumbstick_Left);
	const auto &right = thumbstickMappings.at(Thumbstick_Right);

	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstick],
					  left.is_mouse_move);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickUpKey], left.up.vk);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickDownKey],
					  left.down.vk);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickLeftKey],
					  left.left.vk);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::LeftThumbstickRightKey],
					  left.right.vk);

	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstick],
					  right.is_mouse_move);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickUpKey],
					  right.up.vk);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickDownKey],
					  right.down.vk);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickLeftKey],
					  right.left.vk);
	settings.setValue(thumbstick_settings[setting_keys::thumbstick_keys::RightThumbstickRightKey],
					  right.right.vk);
	// Written rather than read for its value: its presence is what stops the
	// one-time carry-over of the right stick to mouse look from repeating.
	settings.setValue("thumbsticks/RightThumbstickMouseDefault", true);

	// Thumbstick display names
	settings.setValue("thumbstick_display_names/LeftThumbstickUp", left.up.displayName);
	settings.setValue("thumbstick_display_names/LeftThumbstickDown", left.down.displayName);
	settings.setValue("thumbstick_display_names/LeftThumbstickLeft", left.left.displayName);
	settings.setValue("thumbstick_display_names/LeftThumbstickRight", left.right.displayName);
	settings.setValue("thumbstick_display_names/RightThumbstickUp", right.up.displayName);
	settings.setValue("thumbstick_display_names/RightThumbstickDown", right.down.displayName);
	settings.setValue("thumbstick_display_names/RightThumbstickLeft", right.left.displayName);
	settings.setValue("thumbstick_display_names/RightThumbstickRight", right.right.displayName);

	// Trigger mappings
	const auto &leftTrigger = triggerMappings.at(Trigger::Left);
	const auto &rightTrigger = triggerMappings.at(Trigger::Right);

	settings.setValue(trigger_settings[setting_keys::trigger_keys::LeftTriggerKey],
					  leftTrigger.button_input.vk);
	settings.setValue(trigger_settings[setting_keys::trigger_keys::LeftTriggerThreshold],
					  leftTrigger.threshold);
	settings.setValue(trigger_settings[setting_keys::trigger_keys::RightTriggerKey],
					  rightTrigger.button_input.vk);
	settings.setValue(trigger_settings[setting_keys::trigger_keys::RightTriggerThreshold],
					  rightTrigger.threshold);

	// Trigger display names
	settings.setValue("trigger_display_names/LeftTrigger", leftTrigger.button_input.displayName);
	settings.setValue("trigger_display_names/RightTrigger", rightTrigger.button_input.displayName);

	// Thumbstick mouse-wheel scrolling
	settings.setValue("thumbsticks/LeftThumbstickScroll", left.is_mouse_scroll);
	settings.setValue("thumbsticks/RightThumbstickScroll", right.is_mouse_scroll);

	// Extra user-defined keys
	settings.setValue("custom/count", static_cast<int>(m_customBindings.size()));
	for (size_t i = 0; i < m_customBindings.size(); ++i)
	{
		const auto &binding = m_customBindings[i];
		const QString prefix = QStringLiteral("custom/%1/").arg(i);
		settings.setValue(prefix + "mask", binding.gamepadMask);
		settings.setValue(prefix + "name", binding.name);
		settings.setValue(prefix + "vk", binding.vk);
		settings.setValue(prefix + "display", binding.displayName);
	}
}
