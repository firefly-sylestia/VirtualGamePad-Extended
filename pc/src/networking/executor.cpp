#include "executor.hpp"

#include "../settings/input_types.hpp"
#include "../settings/settings_singleton.hpp"
#include "../simulation/gamepadSim.hpp"
#include "../simulation/keyboardSim.hpp"
#include "../simulation/mouseSim.hpp"
#include "input_backend_status.hpp"

#include <QApplication>
#include <QDebug>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <errno.h>
#include <sstream>
#include <string>
#include <vector>

/**
 * Converts a circular position (x,y with radius=1) to square coordinates.
 * This allows diagonal movement to reach (1,1) instead of (0.71,0.71).
 */
std::pair<float, float> circleToSquare(float x, float y)
{
	// Fast path for common cases
	if (x == 0.0f && y == 0.0f)
		return {0.0f, 0.0f};

	float magnitude = std::sqrt(x * x + y * y);
	if (magnitude == 0.0f)
		return {0.0f, 0.0f};

	// Normalize coordinates
	float nx = x / magnitude;
	float ny = y / magnitude;

	// Calculate scaling factor
	float scale = (std::abs(nx) > std::abs(ny)) ? (1.0f / std::abs(nx)) : (1.0f / std::abs(ny));

	// Apply magnitude limits and return
	float clampedMagnitude = std::min(magnitude, 1.0f);
	return {nx * scale * clampedMagnitude, ny * scale * clampedMagnitude};
}

/**
 * Helper function to convert button flags to readable names
 */
std::string getButtonNames(uint32_t buttons)
{
	std::vector<std::string> buttonNames;

	if (buttons & GamepadButtons_Menu)
		buttonNames.emplace_back("Menu");
	if (buttons & GamepadButtons_View)
		buttonNames.emplace_back("View");
	if (buttons & GamepadButtons_A)
		buttonNames.emplace_back("A");
	if (buttons & GamepadButtons_B)
		buttonNames.emplace_back("B");
	if (buttons & GamepadButtons_X)
		buttonNames.emplace_back("X");
	if (buttons & GamepadButtons_Y)
		buttonNames.emplace_back("Y");
	if (buttons & GamepadButtons_DPadUp)
		buttonNames.emplace_back("DPadUp");
	if (buttons & GamepadButtons_DPadDown)
		buttonNames.emplace_back("DPadDown");
	if (buttons & GamepadButtons_DPadLeft)
		buttonNames.emplace_back("DPadLeft");
	if (buttons & GamepadButtons_DPadRight)
		buttonNames.emplace_back("DPadRight");
	if (buttons & GamepadButtons_LeftShoulder)
		buttonNames.emplace_back("LeftShoulder");
	if (buttons & GamepadButtons_RightShoulder)
		buttonNames.emplace_back("RightShoulder");
	if (buttons & GamepadButtons_LeftThumbstick)
		buttonNames.emplace_back("LeftThumbstick");
	if (buttons & GamepadButtons_RightThumbstick)
		buttonNames.emplace_back("RightThumbstick");
	if (buttons & GamepadButtons_Plus)
		buttonNames.emplace_back("Plus");
	if (buttons & GamepadButtons_Minus)
		buttonNames.emplace_back("Minus");
	if (buttons & GamepadButtons_Home)
		buttonNames.emplace_back("Home");
	if (buttons & GamepadButtons_Capture)
		buttonNames.emplace_back("Capture");
	if (buttons & GamepadButtons_LeftSide)
		buttonNames.emplace_back("LeftSide");
	if (buttons & GamepadButtons_RightSide)
		buttonNames.emplace_back("RightSide");
	if (buttons & GamepadButtons_Paddle1)
		buttonNames.emplace_back("Paddle1");
	if (buttons & GamepadButtons_Paddle2)
		buttonNames.emplace_back("Paddle2");
	if (buttons & GamepadButtons_Paddle3)
		buttonNames.emplace_back("Paddle3");
	if (buttons & GamepadButtons_Paddle4)
		buttonNames.emplace_back("Paddle4");

	if (buttonNames.empty())
	{
		return "None";
	}

	std::ostringstream oss;
	for (size_t i = 0; i < buttonNames.size(); ++i)
	{
		if (i > 0)
			oss << " | ";
		oss << buttonNames[i];
	}

	return oss.str();
}

ParseResult parse_gamepad_state(const char *data, size_t len)
{
	ParseResult result;
	result.reading.buttons_up = 0;
	result.reading.buttons_down = 0;
	result.reading.left_trigger = 0;
	result.reading.right_trigger = 0;
	result.reading.left_thumbstick_x = 0;
	result.reading.left_thumbstick_y = 0;
	result.reading.right_thumbstick_x = 0;
	result.reading.right_thumbstick_y = 0;
	result.bytes_consumed = 0;
	result.success = false;

	// Deserialize the data
	size_t decoded_octects = vgp_data_exchange_gamepad_reading_unmarshal(&result.reading, data, len);

	// When the return is zero then errno is set to one of the following 3 values:
	// EWOULDBLOCK on incomplete data, EFBIG on a breach of either colfer_size_max
	// or colfer_list_max and EILSEQ on schema mismatch.
	if (decoded_octects == 0)
	{
		if (errno == EWOULDBLOCK)
		{
			result.failure_reason = ParseResult::FailureReason::IncompleteData;
		}
		else if (errno == EFBIG)
		{
			result.failure_reason = ParseResult::FailureReason::DataTooLarge;
			qWarning() << "Data too large to process";
		}
		else if (errno == EILSEQ)
		{
			result.failure_reason = ParseResult::FailureReason::SchemaMismatch;
			qWarning() << "Schema mismatch detected";
		}
		else
		{
			qWarning() << "Unknown error occurred during deserialization";
		}
		return result;
	}

	result.bytes_consumed = decoded_octects;
	result.success = true;

#ifdef QT_DEBUG
	// Log the gamepad state
	qDebug() << "Gamepad state:"
			 << "\nButtons up: " << getButtonNames(result.reading.buttons_up).c_str()
			 << "\nButtons down: " << getButtonNames(result.reading.buttons_down).c_str()
			 << "\nLeft trigger: " << result.reading.left_trigger
			 << "\nRight trigger: " << result.reading.right_trigger
			 << "\nLeft thumbstick x: " << result.reading.left_thumbstick_x
			 << "\nLeft thumbstick y: " << result.reading.left_thumbstick_y
			 << "\nRight thumbstick x: " << result.reading.right_thumbstick_x
			 << "\nRight thumbstick y: " << result.reading.right_thumbstick_y;
#endif

	return result;
}

KeyboardMouseExecutor::KeyboardMouseExecutor()
{
	try
	{
		m_keyboardInjector = std::make_unique<KeyboardInjector>();
		m_mouseInjector = std::make_unique<MouseInjector>();
	}
	catch (const std::exception &e)
	{
		qCritical() << "Failed to initialize input injectors:" << e.what();
		throw;
	}

	m_backendName = QStringLiteral("Keyboard + mouse");
	m_backendDetail =
		QStringLiteral("works in every game, following the active key map");
	InputBackendStatus::instance().set(m_backendName, true, m_backendDetail);
}

GamepadExecutor::GamepadExecutor()
{
	const bool available = m_injector.isAvailable();

	// The backend is named because the paths are not equivalent to a game: a
	// ViGEmBus or uinput pad is detected as real hardware, the Windows
	// input-injection fallback usually is not.
	QString detail;
	if (!available)
		detail = m_injector.lastError();
	else if (m_injector.detectsAsPhysicalController())
	{
		// "Is it detected as an input device?" is worth answering with what the
		// system itself reports, because the answer differs by device and by list.
		const QString report = m_injector.systemReport();
		detail = report.isEmpty()
					 ? QStringLiteral("games detect this as a physical controller")
					 : QStringLiteral("games detect this as a physical controller; %1")
						   .arg(report);
	}
	else
		detail = QStringLiteral("games that read XInput or DirectInput directly may not see this "
								"pad. Install ViGEmBus, or switch to Keyboard + mouse mode");

	m_backendName = m_injector.backendName();
	m_backendDetail = detail;
	InputBackendStatus::instance().set(m_backendName, available, m_backendDetail);
}

void KeyboardMouseExecutor::handleButtonDown(const ButtonInput &buttonInput)
{
	// The phone reports which controls are held on every packet, so a press is
	// only news the first time it arrives. Sending it again for each packet flooded
	// the PC with key-down and mouse-button-down events: a held shoulder button
	// meant the left mouse button going down dozens of times a second, which turned
	// every real click into a drag and made the desktop look frozen. Counting the
	// holders of one output also keeps two controls that share it — a shoulder and
	// a stick direction, say — from releasing each other.
	int &holdCount = m_heldInputs[buttonInput.vk];
	if (holdCount++ > 0)
		return;

	injectButtonDown(buttonInput);
}

void KeyboardMouseExecutor::handleButtonUp(const ButtonInput &buttonInput)
{
	const auto held = m_heldInputs.find(buttonInput.vk);
	if (held == m_heldInputs.end())
		// Nothing is held, so a release here would be an event the PC never asked
		// for — and a stray mouse release ends a drag the user is in the middle of.
		return;
	if (--held->second > 0)
		return;
	m_heldInputs.erase(held);

	injectButtonUp(buttonInput);
}

void KeyboardMouseExecutor::releaseAll()
{
	if (m_heldInputs.empty())
		return;

	// The releases are built from the output codes alone: a release only has to
	// lift something, and the reading the press came from is long gone.
	std::vector<InputKeyCode> held;
	held.reserve(m_heldInputs.size());
	for (const auto &entry : m_heldInputs)
		held.push_back(entry.first);
	m_heldInputs.clear();

	for (const InputKeyCode code : held)
		injectButtonUp(ButtonInput{code, is_mouse_button(code), QString()});
}

void KeyboardMouseExecutor::injectButtonDown(const ButtonInput &buttonInput)
{
	// Mouse-wheel pseudo keys: one tick per press.
	if (is_scroll_action(buttonInput.vk))
	{
		if (buttonInput.vk == INPUT_SCROLL_UP)
			m_mouseInjector->scrollUp();
		else
			m_mouseInjector->scrollDown();
		return;
	}

	if (buttonInput.is_mouse_button)
	{
#ifdef WIN32
		if (buttonInput.vk == VK_LBUTTON)
			m_mouseInjector->leftDown();
		else if (buttonInput.vk == VK_RBUTTON)
			m_mouseInjector->rightDown();
		else if (buttonInput.vk == VK_MBUTTON)
			m_mouseInjector->middleDown();
#elif defined(__linux__)
		if (buttonInput.vk == BTN_LEFT)
			m_mouseInjector->leftDown();
		else if (buttonInput.vk == BTN_RIGHT)
			m_mouseInjector->rightDown();
		else if (buttonInput.vk == BTN_MIDDLE)
			m_mouseInjector->middleDown();
#endif
		else [[unlikely]] // Unknown mouse button, do nothing
			qWarning() << "Unknown mouse button pressed: " << buttonInput.vk;
	}
	else
	{
		m_keyboardInjector->keyDown(buttonInput.vk);
	}
}

void KeyboardMouseExecutor::injectButtonUp(const ButtonInput &buttonInput)
{
	// A wheel tick has no "up" state.
	if (is_scroll_action(buttonInput.vk))
		return;

	if (buttonInput.is_mouse_button)
	{
#ifdef WIN32
		if (buttonInput.vk == VK_LBUTTON)
			m_mouseInjector->leftUp();
		else if (buttonInput.vk == VK_RBUTTON)
			m_mouseInjector->rightUp();
		else if (buttonInput.vk == VK_MBUTTON)
			m_mouseInjector->middleUp();
#elif defined(__linux__)
		if (buttonInput.vk == BTN_LEFT)
			m_mouseInjector->leftUp();
		else if (buttonInput.vk == BTN_RIGHT)
			m_mouseInjector->rightUp();
		else if (buttonInput.vk == BTN_MIDDLE)
			m_mouseInjector->middleUp();
#endif
		else [[unlikely]] // Unknown mouse button, do nothing
			qWarning() << "Unknown mouse button released: " << buttonInput.vk;
	}
	else
	{
		m_keyboardInjector->keyUp(buttonInput.vk);
	}
}

namespace
{
/** Milliseconds from a monotonic clock, used to rate-limit mouse-wheel ticks. */
qint64 steadyNowMs()
{
	using namespace std::chrono;
	return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}
} // namespace

void KeyboardMouseExecutor::handleThumbstickInput(const ThumbstickInput &thumbstick,
												  float x_value,
												  float y_value,
												  double threshold,
												  int stickIndex)
{
	// Wheel mode: pushing the stick up or down cycles the mouse wheel. This is
	// what swaps hotbar slots in Minecraft and pages through inventories.
	if (thumbstick.is_mouse_scroll)
	{
		const bool scrolling = std::abs(y_value) > threshold;
		if (!scrolling)
		{
			m_lastScrollAt[static_cast<size_t>(stickIndex)] = 0;
			return;
		}

		const qint64 now = steadyNowMs();
		qint64 &last = m_lastScrollAt[static_cast<size_t>(stickIndex)];
		if (last != 0 && now - last < SCROLL_INTERVAL_MS)
			return;
		last = now;

		if (y_value > threshold)
			m_mouseInjector->scrollDown();
		else
			m_mouseInjector->scrollUp();
		return;
	}

	// Convert circular area to square area
	auto [squareX, squareY] = circleToSquare(x_value, y_value);

	if (thumbstick.is_mouse_move)
	{
		// Mouse movement code
		auto offsetX = static_cast<int>(
			squareX * static_cast<float>(SettingsSingleton::instance().mouseSensitivity()));
		auto offsetY = static_cast<int>(
			squareY * static_cast<float>(SettingsSingleton::instance().mouseSensitivity()));

		// Only move if offset is above threshold
		if (double thresholdPixels =
				threshold * static_cast<double>(SettingsSingleton::instance().mouseSensitivity());
			std::abs(offsetX) < thresholdPixels && std::abs(offsetY) < thresholdPixels)
			return;

		// qDebug() << "Moving mouse by offset:" << offsetX << "," << offsetY;

		// Break movement into steps for smooth movement
		int maxSteps = std::max(std::abs(offsetX), std::abs(offsetY));
		if (maxSteps > 0)
		{
			for (int step = 1; step <= maxSteps; ++step)
			{
				int stepX = (offsetX * step) / maxSteps - (offsetX * (step - 1)) / maxSteps;
				int stepY = (offsetY * step) / maxSteps - (offsetY * (step - 1)) / maxSteps;
				// qDebug() << "Moving mouse by step:" << stepX << "," << stepY;
				m_mouseInjector->moveMouseByOffset(stepX, stepY);
			}
		}
	}
	else
	{
		// Direction handling
		if (x_value > threshold)
			handleButtonDown(thumbstick.right);
		else if (x_value < -threshold)
			handleButtonDown(thumbstick.left);
		else
		{
			handleButtonUp(thumbstick.right);
			handleButtonUp(thumbstick.left);
		}

		if (y_value > threshold)
			handleButtonDown(thumbstick.down);
		else if (y_value < -threshold)
			handleButtonDown(thumbstick.up);
		else
		{
			handleButtonUp(thumbstick.down);
			handleButtonUp(thumbstick.up);
		}
	}
}

void KeyboardMouseExecutor::handleTriggerInput(const TriggerInput &trigger, float trigger_value)
{
	if (trigger_value >= trigger.threshold)
	{
		handleButtonDown(trigger.button_input);
	}
	else
	{
		handleButtonUp(trigger.button_input);
	}
}

bool KeyboardMouseExecutor::inject_gamepad_state(vgp_data_exchange_gamepad_reading const &reading)
{
	// Handle button input using active keymap profile
	const auto &profile = SettingsSingleton::instance().activeKeymapProfile();
	static const std::vector<GamepadButtons> buttons = {GamepadButtons_Menu,
														GamepadButtons_View,
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
														GamepadButtons_Plus,
														GamepadButtons_Minus,
														GamepadButtons_Home,
														GamepadButtons_Capture,
														GamepadButtons_LeftSide,
														GamepadButtons_RightSide,
														GamepadButtons_Paddle1,
														GamepadButtons_Paddle2,
														GamepadButtons_Paddle3,
														GamepadButtons_Paddle4};
	for (auto button : buttons)
	{
		InputKeyCode vk = profile.buttonMap(button);
		if (vk == 0)
			continue;
		ButtonInput input{vk, is_mouse_button(vk), ""};
		if (reading.buttons_down & button)
			handleButtonDown(input);
		if (reading.buttons_up & button)
			handleButtonUp(input);
	}

	handleThumbstickInput(
		SettingsSingleton::instance().activeKeymapProfile().thumbstickInput(Thumbstick_Left),
		reading.left_thumbstick_x,
		reading.left_thumbstick_y,
		THRESHOLD,
		0);

	handleThumbstickInput(
		SettingsSingleton::instance().activeKeymapProfile().thumbstickInput(Thumbstick_Right),
		reading.right_thumbstick_x,
		reading.right_thumbstick_y,
		THRESHOLD,
		1);

	handleCustomBindings(reading);

	handleTriggerInput(SettingsSingleton::instance().activeKeymapProfile().triggerInput(Trigger::Left),
					   reading.left_trigger);

	handleTriggerInput(SettingsSingleton::instance().activeKeymapProfile().triggerInput(Trigger::Right),
					   reading.right_trigger);

	return true;
}

void KeyboardMouseExecutor::handleCustomBindings(vgp_data_exchange_gamepad_reading const &reading)
{
	const auto &custom = SettingsSingleton::instance().activeKeymapProfile().customBindings();
	if (custom.empty())
		return;

	for (const auto &binding : custom)
	{
		if (binding.gamepadMask == 0 || binding.vk == 0)
			continue;
		ButtonInput input{binding.vk, is_mouse_button(binding.vk), binding.displayName};
		if (reading.buttons_down & binding.gamepadMask)
			handleButtonDown(input);
		if (reading.buttons_up & binding.gamepadMask)
			handleButtonUp(input);
	}
}

void GamepadExecutor::pressNativeButton(int buttonCode)
{
#ifdef _WIN32
	m_injector.pressButton(static_cast<WinRTGamepadButtons>(buttonCode));
#elif defined(__linux__)
	m_injector.pressButton(buttonCode);
#endif
}

void GamepadExecutor::releaseNativeButton(int buttonCode)
{
#ifdef _WIN32
	m_injector.releaseButton(static_cast<WinRTGamepadButtons>(buttonCode));
#elif defined(__linux__)
	m_injector.releaseButton(buttonCode);
#endif
}

void GamepadExecutor::setNativeButtonState(uint32_t semanticButton, int nativeButton, bool pressed)
{
	const bool wasPressed = m_semanticButtonStates[semanticButton];
	if (wasPressed == pressed)
		return;

	m_semanticButtonStates[semanticButton] = pressed;
	if (pressed)
	{
		int &count = m_nativeButtonPressCounts[nativeButton];
		if (count++ == 0)
			pressNativeButton(nativeButton);
		return;
	}

	auto it = m_nativeButtonPressCounts.find(nativeButton);
	if (it == m_nativeButtonPressCounts.end())
		return;
	if (--it->second <= 0)
	{
		releaseNativeButton(nativeButton);
		m_nativeButtonPressCounts.erase(it);
	}
}

bool GamepadExecutor::inject_gamepad_state(vgp_data_exchange_gamepad_reading const &reading)
{
#ifdef _WIN32
	using enum winrt::Windows::Gaming::Input::GamepadButtons;
	InjectedInputGamepadInfo newState;
	WinRTGamepadButtons heldButtons = None;
	for (const auto &[buttonCode, pressCount] : m_nativeButtonPressCounts)
	{
		if (pressCount > 0)
		{
			heldButtons = static_cast<WinRTGamepadButtons>(
				static_cast<uint32_t>(heldButtons) | static_cast<uint32_t>(buttonCode));
		}
	}
	newState.Buttons(heldButtons);
	newState.LeftThumbstickX(reading.left_thumbstick_x);
	newState.LeftThumbstickY(-reading.left_thumbstick_y);
	newState.RightThumbstickX(reading.right_thumbstick_x);
	newState.RightThumbstickY(-reading.right_thumbstick_y);
	newState.LeftTrigger(reading.left_trigger);
	newState.RightTrigger(reading.right_trigger);
	m_injector.update(newState);

	// Switch/system controls fall back to standard Xbox-compatible buttons.
	// Reference counting prevents one alias releasing a button still held by another.
	setNativeButtonState(GamepadButtons_Menu, static_cast<int>(Menu), reading.buttons_down & GamepadButtons_Menu);
	setNativeButtonState(GamepadButtons_View, static_cast<int>(View), reading.buttons_down & GamepadButtons_View);
	setNativeButtonState(GamepadButtons_A, static_cast<int>(A), reading.buttons_down & GamepadButtons_A);
	setNativeButtonState(GamepadButtons_B, static_cast<int>(B), reading.buttons_down & GamepadButtons_B);
	setNativeButtonState(GamepadButtons_X, static_cast<int>(X), reading.buttons_down & GamepadButtons_X);
	setNativeButtonState(GamepadButtons_Y, static_cast<int>(Y), reading.buttons_down & GamepadButtons_Y);
	setNativeButtonState(GamepadButtons_DPadUp, static_cast<int>(DPadUp), reading.buttons_down & GamepadButtons_DPadUp);
	setNativeButtonState(GamepadButtons_DPadDown, static_cast<int>(DPadDown), reading.buttons_down & GamepadButtons_DPadDown);
	setNativeButtonState(GamepadButtons_DPadLeft, static_cast<int>(DPadLeft), reading.buttons_down & GamepadButtons_DPadLeft);
	setNativeButtonState(GamepadButtons_DPadRight, static_cast<int>(DPadRight), reading.buttons_down & GamepadButtons_DPadRight);
	setNativeButtonState(GamepadButtons_LeftShoulder, static_cast<int>(LeftShoulder), reading.buttons_down & GamepadButtons_LeftShoulder);
	setNativeButtonState(GamepadButtons_RightShoulder, static_cast<int>(RightShoulder), reading.buttons_down & GamepadButtons_RightShoulder);
	setNativeButtonState(GamepadButtons_LeftThumbstick, static_cast<int>(LeftThumbstick), reading.buttons_down & GamepadButtons_LeftThumbstick);
	setNativeButtonState(GamepadButtons_RightThumbstick, static_cast<int>(RightThumbstick), reading.buttons_down & GamepadButtons_RightThumbstick);
	setNativeButtonState(GamepadButtons_Plus, static_cast<int>(Menu), reading.buttons_down & GamepadButtons_Plus);
	setNativeButtonState(GamepadButtons_Minus, static_cast<int>(View), reading.buttons_down & GamepadButtons_Minus);
	setNativeButtonState(GamepadButtons_Home, static_cast<int>(Menu), reading.buttons_down & GamepadButtons_Home);
	setNativeButtonState(GamepadButtons_Capture, static_cast<int>(View), reading.buttons_down & GamepadButtons_Capture);
	setNativeButtonState(GamepadButtons_LeftSide, static_cast<int>(LeftShoulder), reading.buttons_down & GamepadButtons_LeftSide);
	setNativeButtonState(GamepadButtons_RightSide, static_cast<int>(RightShoulder), reading.buttons_down & GamepadButtons_RightSide);
	setNativeButtonState(GamepadButtons_Paddle1, static_cast<int>(A), reading.buttons_down & GamepadButtons_Paddle1);
	setNativeButtonState(GamepadButtons_Paddle2, static_cast<int>(B), reading.buttons_down & GamepadButtons_Paddle2);
	setNativeButtonState(GamepadButtons_Paddle3, static_cast<int>(X), reading.buttons_down & GamepadButtons_Paddle3);
	setNativeButtonState(GamepadButtons_Paddle4, static_cast<int>(Y), reading.buttons_down & GamepadButtons_Paddle4);
#elif defined(__linux__)
	m_injector.setThumbsticks(reading.left_thumbstick_x, -reading.left_thumbstick_y,
							  reading.right_thumbstick_x, -reading.right_thumbstick_y);
	m_injector.setTriggers(reading.left_trigger, reading.right_trigger);

	setNativeButtonState(GamepadButtons_Menu, BTN_START, reading.buttons_down & GamepadButtons_Menu);
	setNativeButtonState(GamepadButtons_View, BTN_SELECT, reading.buttons_down & GamepadButtons_View);
	setNativeButtonState(GamepadButtons_A, BTN_A, reading.buttons_down & GamepadButtons_A);
	setNativeButtonState(GamepadButtons_B, BTN_B, reading.buttons_down & GamepadButtons_B);
	setNativeButtonState(GamepadButtons_X, BTN_X, reading.buttons_down & GamepadButtons_X);
	setNativeButtonState(GamepadButtons_Y, BTN_Y, reading.buttons_down & GamepadButtons_Y);
	setNativeButtonState(GamepadButtons_DPadUp, BTN_DPAD_UP, reading.buttons_down & GamepadButtons_DPadUp);
	setNativeButtonState(GamepadButtons_DPadDown, BTN_DPAD_DOWN, reading.buttons_down & GamepadButtons_DPadDown);
	setNativeButtonState(GamepadButtons_DPadLeft, BTN_DPAD_LEFT, reading.buttons_down & GamepadButtons_DPadLeft);
	setNativeButtonState(GamepadButtons_DPadRight, BTN_DPAD_RIGHT, reading.buttons_down & GamepadButtons_DPadRight);
	setNativeButtonState(GamepadButtons_LeftShoulder, BTN_TL, reading.buttons_down & GamepadButtons_LeftShoulder);
	setNativeButtonState(GamepadButtons_RightShoulder, BTN_TR, reading.buttons_down & GamepadButtons_RightShoulder);
	setNativeButtonState(GamepadButtons_LeftThumbstick, BTN_THUMBL, reading.buttons_down & GamepadButtons_LeftThumbstick);
	setNativeButtonState(GamepadButtons_RightThumbstick, BTN_THUMBR, reading.buttons_down & GamepadButtons_RightThumbstick);
	setNativeButtonState(GamepadButtons_Plus, BTN_START, reading.buttons_down & GamepadButtons_Plus);
	setNativeButtonState(GamepadButtons_Minus, BTN_SELECT, reading.buttons_down & GamepadButtons_Minus);
	setNativeButtonState(GamepadButtons_Home, BTN_START, reading.buttons_down & GamepadButtons_Home);
	setNativeButtonState(GamepadButtons_Capture, BTN_SELECT, reading.buttons_down & GamepadButtons_Capture);
	setNativeButtonState(GamepadButtons_LeftSide, BTN_TL, reading.buttons_down & GamepadButtons_LeftSide);
	setNativeButtonState(GamepadButtons_RightSide, BTN_TR, reading.buttons_down & GamepadButtons_RightSide);
	setNativeButtonState(GamepadButtons_Paddle1, BTN_A, reading.buttons_down & GamepadButtons_Paddle1);
	setNativeButtonState(GamepadButtons_Paddle2, BTN_B, reading.buttons_down & GamepadButtons_Paddle2);
	setNativeButtonState(GamepadButtons_Paddle3, BTN_X, reading.buttons_down & GamepadButtons_Paddle3);
	setNativeButtonState(GamepadButtons_Paddle4, BTN_Y, reading.buttons_down & GamepadButtons_Paddle4);
#endif

	m_injector.inject();
	// false tells the caller nothing reached the system; the UI reports why once.
	return m_injector.isAvailable();
}

void GamepadExecutor::releaseAll()
{
	// The pad is left reporting nothing held and both sticks at rest. A game keeps
	// reading the pad as long as the app runs, so a stream that stops in the middle
	// of a press would otherwise leave it holding a button or a stick pushed over.
	m_nativeButtonPressCounts.clear();
	m_semanticButtonStates.clear();

#ifdef _WIN32
	InjectedInputGamepadInfo atRest;
	atRest.Buttons(static_cast<WinRTGamepadButtons>(0));
	atRest.LeftThumbstickX(0.0);
	atRest.LeftThumbstickY(0.0);
	atRest.RightThumbstickX(0.0);
	atRest.RightThumbstickY(0.0);
	atRest.LeftTrigger(0.0);
	atRest.RightTrigger(0.0);
	m_injector.update(atRest);
#elif defined(__linux__)
	m_injector.setThumbsticks(0.0f, 0.0f, 0.0f, 0.0f);
	m_injector.setTriggers(0.0f, 0.0f);
#endif
	m_injector.inject();
}

CombinedExecutor::CombinedExecutor()
{
	// Each sub-backend announced itself while it was being built; the page is told
	// about the pair instead, because both stay live. The pad's note comes first
	// because it is the one that can fail to exist, and it says why if it did.
	InputBackendStatus::instance().set(
		QStringLiteral("%1 + %2").arg(m_gamepad.backendName(), m_keyboardMouse.backendName()),
		true,
		QStringLiteral("%1. Keyboard and mouse input is sent at the same time, so programs "
					   "with no controller support react too")
			.arg(m_gamepad.backendDetail()));
}

bool CombinedExecutor::inject_gamepad_state(vgp_data_exchange_gamepad_reading const &reading)
{
	m_gamepad.inject_gamepad_state(reading);
	// The keyboard and mouse path is the one that always reaches the system, so its
	// result is what the caller is told: a false here means nothing was delivered.
	return m_keyboardMouse.inject_gamepad_state(reading);
}

void CombinedExecutor::releaseAll()
{
	// Both halves stop holding, because the stream that would have sent their
	// releases has ended whichever one it was feeding.
	m_gamepad.releaseAll();
	m_keyboardMouse.releaseAll();
}

bool CombinedExecutor::isAvailable() const
{
	// Input still reaches the desktop through the keyboard and mouse mapping even
	// when the virtual pad could not be created, so this mode works either way.
	return m_keyboardMouse.isAvailable();
}

QString CombinedExecutor::backendName() const
{
	return QStringLiteral("%1 + %2").arg(m_gamepad.backendName(), m_keyboardMouse.backendName());
}

QString CombinedExecutor::backendDetail() const
{
	return QStringLiteral("%1. Keyboard and mouse input is sent at the same time, so programs "
						  "with no controller support react too")
		.arg(m_gamepad.backendDetail());
}
