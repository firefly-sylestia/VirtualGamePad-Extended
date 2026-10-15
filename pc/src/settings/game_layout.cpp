#include "game_layout.hpp"

#include "key_names.hpp"
#include "settings_singleton.hpp"

#include <QDebug>

namespace
{
/** Every button the protocol defines, so a layout can explicitly unbind one. */
const GamepadButtons kStandardButtons[] = {
	GamepadButtons_A,
	GamepadButtons_B,
	GamepadButtons_X,
	GamepadButtons_Y,
	GamepadButtons_DPadUp,
	GamepadButtons_DPadDown,
	GamepadButtons_DPadLeft,
	GamepadButtons_DPadRight,
	GamepadButtons_LeftShoulder,
	GamepadButtons_RightShoulder,
	GamepadButtons_LeftThumbstick,
	GamepadButtons_RightThumbstick,
	GamepadButtons_Menu,
	GamepadButtons_View,
	GamepadButtons_Plus,
	GamepadButtons_Minus,
	GamepadButtons_Home,
	GamepadButtons_Capture,
	GamepadButtons_LeftSide,
	GamepadButtons_RightSide,
	GamepadButtons_Paddle1,
	GamepadButtons_Paddle2,
	GamepadButtons_Paddle3,
	GamepadButtons_Paddle4,
};

/** Convenience builder so the preset tables stay readable. */
LayoutBinding bind(GamepadButtons button, const char *key, const char *label)
{
	return LayoutBinding{button, QString::fromUtf8(key), QString::fromUtf8(label)};
}

LayoutThumbstick stick(const char *up, const char *down, const char *left, const char *right)
{
	LayoutThumbstick s;
	s.up = QString::fromUtf8(up);
	s.down = QString::fromUtf8(down);
	s.left = QString::fromUtf8(left);
	s.right = QString::fromUtf8(right);
	return s;
}
} // namespace

const QVector<GameLayout> &allGameLayouts()
{
	static const QVector<GameLayout> layouts = []
	{
		QVector<GameLayout> list;

		// ---------------------------------------------------------- desktop --
		{
			GameLayout l;
			l.id = "desktop";
			l.name = QStringLiteral("Desktop / FPS (default)");
			l.tagline = QStringLiteral("Mouse-look shooter with WASD movement");
			l.description = QStringLiteral(
				"A balanced desktop layout. The left stick walks, the right stick aims, and the "
				"shoulders cover the usual action keys.");
			l.buttons = {
				bind(GamepadButtons_A, "Space", "Jump"),
				bind(GamepadButtons_B, "LeftCtrl", "Crouch"),
				bind(GamepadButtons_X, "R", "Reload"),
				bind(GamepadButtons_Y, "E", "Interact"),
				bind(GamepadButtons_DPadUp, "Up", "Menu up"),
				bind(GamepadButtons_DPadDown, "Down", "Menu down"),
				bind(GamepadButtons_DPadLeft, "Left", "Menu left"),
				bind(GamepadButtons_DPadRight, "Right", "Menu right"),
				bind(GamepadButtons_LeftShoulder, "F", "Melee"),
				bind(GamepadButtons_RightShoulder, "MouseRight", "Aim"),
				bind(GamepadButtons_LeftThumbstick, "LeftShift", "Sprint"),
				bind(GamepadButtons_RightThumbstick, "MouseMiddle", "Ping"),
				bind(GamepadButtons_Menu, "Escape", "Pause"),
				bind(GamepadButtons_View, "Tab", "Scoreboard"),
				bind(GamepadButtons_Plus, "PageUp", "Wheel up"),
				bind(GamepadButtons_Minus, "PageDown", "Wheel down"),
				bind(GamepadButtons_Home, "Home", "Home"),
				bind(GamepadButtons_Capture, "F2", "Screenshot"),
				bind(GamepadButtons_LeftSide, "Q", "Ability 1"),
				bind(GamepadButtons_RightSide, "E", "Ability 2"),
				bind(GamepadButtons_Paddle1, "Digit1", "Slot 1"),
				bind(GamepadButtons_Paddle2, "Digit2", "Slot 2"),
				bind(GamepadButtons_Paddle3, "Digit3", "Slot 3"),
				bind(GamepadButtons_Paddle4, "Digit4", "Slot 4"),
			};
			l.leftStick = stick("W", "S", "A", "D");
			l.rightStick = stick("", "", "", "");
			l.rightStick.mouseMove = true;
			l.leftTriggerKey = "MouseRight";
			l.rightTriggerKey = "F";
			l.triggerThreshold = 0.35f;
			list.append(l);
		}

		// -------------------------------------------------------- minecraft --
		{
			GameLayout l;
			l.id = "minecraft";
			l.name = QStringLiteral("Minecraft");
			l.tagline = QStringLiteral("Mine, build and swap hotbar slots");
			l.description = QStringLiteral(
				"Built for survival Minecraft. The right stick looks around, the triggers mine and "
				"place, and the D-pad or the paddles swap hotbar slots. Extra keys are added for "
				"chat, the debug overlay and hiding the HUD.");
			l.accent = "#4caf50";
			l.minecraftThemed = true;
			l.buttons = {
				bind(GamepadButtons_A, "Space", "Jump"),
				bind(GamepadButtons_B, "E", "Inventory"),
				bind(GamepadButtons_X, "LeftShift", "Sneak"),
				bind(GamepadButtons_Y, "Q", "Drop item"),
				bind(GamepadButtons_DPadUp, "ScrollUp", "Hotbar up"),
				bind(GamepadButtons_DPadDown, "ScrollDown", "Hotbar down"),
				bind(GamepadButtons_DPadLeft, "Digit1", "Slot 1"),
				bind(GamepadButtons_DPadRight, "Digit9", "Slot 9"),
				bind(GamepadButtons_LeftShoulder, "MouseLeft", "Mine"),
				bind(GamepadButtons_RightShoulder, "MouseRight", "Place / use"),
				bind(GamepadButtons_LeftThumbstick, "LeftCtrl", "Sprint"),
				bind(GamepadButtons_RightThumbstick, "F5", "Camera view"),
				bind(GamepadButtons_Menu, "Escape", "Pause"),
				bind(GamepadButtons_View, "Tab", "Player list"),
				bind(GamepadButtons_Plus, "", "Free"),
				bind(GamepadButtons_Minus, "", "Free"),
				bind(GamepadButtons_Home, "", "Free"),
				bind(GamepadButtons_Capture, "F2", "Screenshot"),
				bind(GamepadButtons_LeftSide, "Q", "Drop"),
				bind(GamepadButtons_RightSide, "E", "Inventory"),
				bind(GamepadButtons_Paddle1, "Digit1", "Hotbar 1"),
				bind(GamepadButtons_Paddle2, "Digit2", "Hotbar 2"),
				bind(GamepadButtons_Paddle3, "Digit3", "Hotbar 3"),
				bind(GamepadButtons_Paddle4, "Digit4", "Hotbar 4"),
			};
			l.customKeys = {
				bind(GamepadButtons_Minus, "T", "Open chat"),
				bind(GamepadButtons_Plus, "F1", "Hide HUD"),
				bind(GamepadButtons_Home, "F3", "Debug overlay"),
			};
			l.leftStick = stick("W", "S", "A", "D");
			l.rightStick = stick("", "", "", "");
			l.rightStick.mouseMove = true;
			l.leftTriggerKey = "MouseLeft";
			l.rightTriggerKey = "MouseRight";
			l.triggerThreshold = 0.3f;
			list.append(l);
		}

		// ------------------------------------------------- creative builder --
		{
			GameLayout l;
			l.id = "builder";
			l.name = QStringLiteral("Creative builder");
			l.tagline = QStringLiteral("Fly around and place blocks fast");
			l.description = QStringLiteral(
				"Sandbox building layout. Fly up and down with the face buttons, pick blocks with "
				"the scroll wheel, and keep the mouse on the right stick.");
			l.accent = "#8b5cf6";
			l.minecraftThemed = true;
			l.buttons = {
				bind(GamepadButtons_A, "Space", "Fly up"),
				bind(GamepadButtons_B, "LeftShift", "Fly down"),
				bind(GamepadButtons_X, "E", "Block palette"),
				bind(GamepadButtons_Y, "Q", "Pick block"),
				bind(GamepadButtons_DPadUp, "ScrollUp", "Prev block"),
				bind(GamepadButtons_DPadDown, "ScrollDown", "Next block"),
				bind(GamepadButtons_DPadLeft, "Left", "Nudge left"),
				bind(GamepadButtons_DPadRight, "Right", "Nudge right"),
				bind(GamepadButtons_LeftShoulder, "MouseLeft", "Break"),
				bind(GamepadButtons_RightShoulder, "MouseRight", "Place"),
				bind(GamepadButtons_LeftThumbstick, "LeftCtrl", "Boost"),
				bind(GamepadButtons_RightThumbstick, "F5", "Camera view"),
				bind(GamepadButtons_Menu, "Escape", "Pause"),
				bind(GamepadButtons_View, "Tab", "Player list"),
				bind(GamepadButtons_Plus, "F1", "Hide HUD"),
				bind(GamepadButtons_Minus, "F3", "Debug overlay"),
				bind(GamepadButtons_Home, "T", "Chat"),
				bind(GamepadButtons_Capture, "F2", "Screenshot"),
				bind(GamepadButtons_LeftSide, "Digit1", "Slot 1"),
				bind(GamepadButtons_RightSide, "Digit2", "Slot 2"),
				bind(GamepadButtons_Paddle1, "Digit3", "Slot 3"),
				bind(GamepadButtons_Paddle2, "Digit4", "Slot 4"),
				bind(GamepadButtons_Paddle3, "Digit5", "Slot 5"),
				bind(GamepadButtons_Paddle4, "Digit6", "Slot 6"),
			};
			l.leftStick = stick("W", "S", "A", "D");
			l.rightStick = stick("", "", "", "");
			l.rightStick.mouseMove = true;
			l.leftTriggerKey = "LeftShift";
			l.rightTriggerKey = "Space";
			l.triggerThreshold = 0.3f;
			list.append(l);
		}

		// ------------------------------------------------------------ racing --
		{
			GameLayout l;
			l.id = "racing";
			l.name = QStringLiteral("Racing / driving");
			l.tagline = QStringLiteral("Triggers are throttle and brake");
			l.description = QStringLiteral(
				"Analog driving layout. The left trigger accelerates and the right trigger brakes, "
				"the left stick steers, and the right stick looks around the cockpit.");
			l.accent = "#ff8a3d";
			l.buttons = {
				bind(GamepadButtons_A, "Space", "Handbrake"),
				bind(GamepadButtons_B, "R", "Reset car"),
				bind(GamepadButtons_X, "C", "Change camera"),
				bind(GamepadButtons_Y, "H", "Horn"),
				bind(GamepadButtons_DPadUp, "Up", "Menu up"),
				bind(GamepadButtons_DPadDown, "Down", "Menu down"),
				bind(GamepadButtons_DPadLeft, "Left", "Menu left"),
				bind(GamepadButtons_DPadRight, "Right", "Menu right"),
				bind(GamepadButtons_LeftShoulder, "A", "Gear down"),
				bind(GamepadButtons_RightShoulder, "D", "Gear up"),
				bind(GamepadButtons_LeftThumbstick, "LeftShift", "Look back"),
				bind(GamepadButtons_RightThumbstick, "MouseMiddle", "Look behind"),
				bind(GamepadButtons_Menu, "Escape", "Pause"),
				bind(GamepadButtons_View, "Tab", "Leaderboard"),
				bind(GamepadButtons_Plus, "NumpadPlus", "Zoom in"),
				bind(GamepadButtons_Minus, "NumpadMinus", "Zoom out"),
				bind(GamepadButtons_Home, "Home", "Home"),
				bind(GamepadButtons_Capture, "F2", "Screenshot"),
				bind(GamepadButtons_LeftSide, "Q", "Radio"),
				bind(GamepadButtons_RightSide, "E", "Horn"),
				bind(GamepadButtons_Paddle1, "Digit1", "Telemetry"),
				bind(GamepadButtons_Paddle2, "Digit2", "Telemetry 2"),
				bind(GamepadButtons_Paddle3, "Digit3", "Telemetry 3"),
				bind(GamepadButtons_Paddle4, "Digit4", "Telemetry 4"),
			};
			l.leftStick = stick("", "", "A", "D");
			l.rightStick = stick("", "", "", "");
			l.rightStick.mouseMove = true;
			l.leftTriggerKey = "W";
			l.rightTriggerKey = "S";
			l.triggerThreshold = 0.12f;
			list.append(l);
		}

		// -------------------------------------------------------- platformer --
		{
			GameLayout l;
			l.id = "platformer";
			l.name = QStringLiteral("Retro platformer");
			l.tagline = QStringLiteral("D-pad feel for 2D games");
			l.description = QStringLiteral(
				"The left stick and the D-pad both emulate the arrow keys, which is what most 2D "
				"platformers and emulators expect.");
			l.accent = "#22d3ee";
			l.buttons = {
				bind(GamepadButtons_A, "Space", "Jump"),
				bind(GamepadButtons_B, "LeftShift", "Run"),
				bind(GamepadButtons_X, "Z", "Action 1"),
				bind(GamepadButtons_Y, "X", "Action 2"),
				bind(GamepadButtons_DPadUp, "Up", "Up"),
				bind(GamepadButtons_DPadDown, "Down", "Down"),
				bind(GamepadButtons_DPadLeft, "Left", "Left"),
				bind(GamepadButtons_DPadRight, "Right", "Right"),
				bind(GamepadButtons_LeftShoulder, "LeftAlt", "Action 3"),
				bind(GamepadButtons_RightShoulder, "LeftCtrl", "Action 4"),
				bind(GamepadButtons_LeftThumbstick, "LeftShift", "Run"),
				bind(GamepadButtons_RightThumbstick, "", "Free"),
				bind(GamepadButtons_Menu, "Escape", "Pause"),
				bind(GamepadButtons_View, "Enter", "Confirm"),
				bind(GamepadButtons_Plus, "", "Free"),
				bind(GamepadButtons_Minus, "", "Free"),
				bind(GamepadButtons_Home, "Escape", "Quit to menu"),
				bind(GamepadButtons_Capture, "F12", "Save state"),
				bind(GamepadButtons_LeftSide, "Digit1", "Slot 1"),
				bind(GamepadButtons_RightSide, "Digit2", "Slot 2"),
				bind(GamepadButtons_Paddle1, "F5", "Save state"),
				bind(GamepadButtons_Paddle2, "F8", "Load state"),
				bind(GamepadButtons_Paddle3, "F11", "Fullscreen"),
				bind(GamepadButtons_Paddle4, "F1", "Help"),
			};
			l.leftStick = stick("Up", "Down", "Left", "Right");
			l.rightStick = stick("", "", "", "");
			l.leftTriggerKey = "";
			l.rightTriggerKey = "";
			list.append(l);
		}

		// ------------------------------------------------------------- media --
		{
			GameLayout l;
			l.id = "media";
			l.name = QStringLiteral("Media centre / HTPC");
			l.tagline = QStringLiteral("Couch browsing with the right stick as a mouse");
			l.description = QStringLiteral(
				"For watching things from the sofa. The right stick is the mouse, the face buttons "
				"are select, back and play/pause, and the shoulders control volume.");
			l.accent = "#f472b6";
			l.buttons = {
				bind(GamepadButtons_A, "Enter", "Select"),
				bind(GamepadButtons_B, "Escape", "Back"),
				bind(GamepadButtons_X, "Space", "Play / pause"),
				bind(GamepadButtons_Y, "F", "Fullscreen"),
				bind(GamepadButtons_DPadUp, "Up", "Up"),
				bind(GamepadButtons_DPadDown, "Down", "Down"),
				bind(GamepadButtons_DPadLeft, "Left", "Left"),
				bind(GamepadButtons_DPadRight, "Right", "Right"),
				bind(GamepadButtons_LeftShoulder, "NumpadMinus", "Volume down"),
				bind(GamepadButtons_RightShoulder, "NumpadPlus", "Volume up"),
				bind(GamepadButtons_LeftThumbstick, "M", "Mute"),
				bind(GamepadButtons_RightThumbstick, "MouseLeft", "Click"),
				bind(GamepadButtons_Menu, "Escape", "Menu"),
				bind(GamepadButtons_View, "Tab", "Switch app"),
				bind(GamepadButtons_Plus, "PageUp", "Next chapter"),
				bind(GamepadButtons_Minus, "PageDown", "Previous chapter"),
				bind(GamepadButtons_Home, "Home", "Home screen"),
				bind(GamepadButtons_Capture, "PrintScreen", "Screenshot"),
				bind(GamepadButtons_LeftSide, "Left", "Seek back"),
				bind(GamepadButtons_RightSide, "Right", "Seek forward"),
				bind(GamepadButtons_Paddle1, "Digit1", "Subtitle"),
				bind(GamepadButtons_Paddle2, "Digit2", "Audio track"),
				bind(GamepadButtons_Paddle3, "Digit3", "Aspect ratio"),
				bind(GamepadButtons_Paddle4, "Digit4", "Stats"),
			};
			l.leftStick = stick("Up", "Down", "Left", "Right");
			l.rightStick = stick("", "", "", "");
			l.rightStick.mouseMove = true;
			l.leftTriggerKey = "NumpadMinus";
			l.rightTriggerKey = "NumpadPlus";
			l.triggerThreshold = 0.4f;
			list.append(l);
		}

		// ------------------------------------------------------------ hotbar --
		{
			GameLayout l;
			l.id = "hotbar";
			l.name = QStringLiteral("Hotbar / inventory scroller");
			l.tagline = QStringLiteral("Cycle the mouse wheel with the left stick");
			l.description = QStringLiteral(
				"Shows off joystick scrolling: pushing the left stick up or down turns the mouse "
				"wheel, which cycles hotbar slots in Minecraft and inventory pages everywhere "
				"else. The right stick still moves the pointer.");
			l.accent = "#facc15";
			l.minecraftThemed = true;
			l.buttons = {
				bind(GamepadButtons_A, "MouseLeft", "Select"),
				bind(GamepadButtons_B, "MouseRight", "Secondary"),
				bind(GamepadButtons_X, "E", "Inventory"),
				bind(GamepadButtons_Y, "Escape", "Close"),
				bind(GamepadButtons_DPadUp, "ScrollUp", "Scroll up"),
				bind(GamepadButtons_DPadDown, "ScrollDown", "Scroll down"),
				bind(GamepadButtons_DPadLeft, "Left", "Left"),
				bind(GamepadButtons_DPadRight, "Right", "Right"),
				bind(GamepadButtons_LeftShoulder, "Q", "Previous slot"),
				bind(GamepadButtons_RightShoulder, "E", "Next slot"),
				bind(GamepadButtons_LeftThumbstick, "LeftShift", "Sprint"),
				bind(GamepadButtons_RightThumbstick, "MouseMiddle", "Pick block"),
				bind(GamepadButtons_Menu, "Escape", "Pause"),
				bind(GamepadButtons_View, "Tab", "List"),
				bind(GamepadButtons_Plus, "ScrollUp", "Scroll up"),
				bind(GamepadButtons_Minus, "ScrollDown", "Scroll down"),
				bind(GamepadButtons_Home, "T", "Chat"),
				bind(GamepadButtons_Capture, "F2", "Screenshot"),
				bind(GamepadButtons_LeftSide, "Digit1", "Slot 1"),
				bind(GamepadButtons_RightSide, "Digit2", "Slot 2"),
				bind(GamepadButtons_Paddle1, "Digit3", "Slot 3"),
				bind(GamepadButtons_Paddle2, "Digit4", "Slot 4"),
				bind(GamepadButtons_Paddle3, "Digit5", "Slot 5"),
				bind(GamepadButtons_Paddle4, "Digit6", "Slot 6"),
			};
			l.leftStick = stick("", "", "", "");
			l.leftStick.mouseScroll = true;
			l.rightStick = stick("", "", "", "");
			l.rightStick.mouseMove = true;
			l.leftTriggerKey = "";
			l.rightTriggerKey = "";
			list.append(l);
		}

		return list;
	}();

	return layouts;
}

const GameLayout *gameLayoutById(const QString &id)
{
	for (const auto &layout : allGameLayouts())
	{
		if (layout.id == id)
			return &layout;
	}
	return nullptr;
}

const GameLayout &gameLayoutOrFirst(const QString &id)
{
	if (const GameLayout *layout = gameLayoutById(id))
		return *layout;
	return allGameLayouts().first();
}

namespace
{
/** Resolve @p name and write it into @p button, warning when the name is bad. */
void applyBinding(KeymapProfile &profile, const LayoutBinding &binding)
{
	if (binding.key.isEmpty())
	{
		profile.setButtonInput(binding.button, 0, QString());
		return;
	}

	const auto resolved = KeyNames::resolve(binding.key);
	if (!resolved)
	{
		qWarning() << "Layout uses an unknown key name:" << binding.key;
		profile.setButtonInput(binding.button, 0, QString());
		return;
	}
	profile.setButtonInput(binding.button, resolved->code, resolved->display);
}

void applyStick(KeymapProfile &profile, Thumbstick which, const LayoutThumbstick &spec)
{
	ThumbstickInput input;
	input.is_mouse_move = spec.mouseMove;
	input.is_mouse_scroll = spec.mouseScroll;

	auto fill = [](ButtonInput &target, const QString &name)
	{
		if (name.isEmpty())
		{
			target = ButtonInput{};
			return;
		}
		const auto resolved = KeyNames::resolve(name);
		if (!resolved)
		{
			qWarning() << "Layout uses an unknown thumbstick key:" << name;
			target = ButtonInput{};
			return;
		}
		target.vk = resolved->code;
		target.is_mouse_button = resolved->isMouseButton;
		target.displayName = resolved->display;
	};

	fill(input.up, spec.up);
	fill(input.down, spec.down);
	fill(input.left, spec.left);
	fill(input.right, spec.right);
	profile.setThumbstickInput(which, input);
}
} // namespace

bool applyGameLayout(const GameLayout &layout, QString *error)
{
	auto &settings = SettingsSingleton::instance();
	auto &profile = settings.activeKeymapProfile();

	// Start from a clean slate so a key that the new layout does not use is not
	// left behind from the previous layout.
	for (const auto button : kStandardButtons)
		profile.setButtonInput(button, 0, QString());

	for (const auto &binding : layout.buttons)
		applyBinding(profile, binding);

	profile.clearCustomBindings();
	for (const auto &binding : layout.customKeys)
	{
		const auto resolved = KeyNames::resolve(binding.key);
		if (!resolved)
		{
			qWarning() << "Layout uses an unknown custom key name:" << binding.key;
			continue;
		}
		profile.addCustomBinding({static_cast<uint32_t>(binding.button),
								  binding.label,
								  resolved->code,
								  resolved->display});
	}

	applyStick(profile, Thumbstick_Left, layout.leftStick);
	applyStick(profile, Thumbstick_Right, layout.rightStick);

	auto fillTrigger = [](TriggerInput &trigger, const QString &name, float threshold)
	{
		trigger = TriggerInput{};
		trigger.threshold = threshold;
		if (name.isEmpty())
			return;
		const auto resolved = KeyNames::resolve(name);
		if (!resolved)
		{
			qWarning() << "Layout uses an unknown trigger key:" << name;
			return;
		}
		trigger.button_input.vk = resolved->code;
		trigger.button_input.is_mouse_button = resolved->isMouseButton;
		trigger.button_input.displayName = resolved->display;
	};

	auto leftTrigger = profile.triggerInput(Trigger::Left);
	fillTrigger(leftTrigger, layout.leftTriggerKey, layout.triggerThreshold);
	profile.setTriggerInput(Trigger::Left, leftTrigger);

	auto rightTrigger = profile.triggerInput(Trigger::Right);
	fillTrigger(rightTrigger, layout.rightTriggerKey, layout.triggerThreshold);
	profile.setTriggerInput(Trigger::Right, rightTrigger);

	if (!settings.saveActiveProfile())
	{
		if (error)
			*error = QStringLiteral("Could not write the profile to disk.");
		return false;
	}
	qInfo() << "Applied game layout:" << layout.id;
	return true;
}
