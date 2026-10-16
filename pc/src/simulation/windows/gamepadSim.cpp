#include "../gamepadSim.hpp"

#include "../../settings/settings_singleton.hpp"

#include <QDebug>
#include <cmath>

namespace
{
/**
 * Maps the configured device onto the backend's target type. The two enums are
 * the same shape but kept apart so the settings layer does not have to know
 * about the driver and its headers.
 */
ViGEmGamepad::TargetType toVigemTarget(VirtualGamepadType type)
{
	switch (type)
	{
	case VirtualGamepadType::DualShock4:
		return ViGEmGamepad::TargetType::DualShock4;
	case VirtualGamepadType::Xbox360:
	default:
		return ViGEmGamepad::TargetType::Xbox360;
	}
}

/**
 * Translates the WinRT button enum into the flag bits the virtual Xbox 360 pad
 * reports. Both describe the same controls, so a switch over the named values
 * keeps this correct even if a future SDK renumbers them.
 */
uint16_t toXusbButtons(WinRTGamepadButtons buttons)
{
	using winrt::Windows::Gaming::Input::GamepadButtons;

	const uint32_t held = static_cast<uint32_t>(buttons);
	auto has = [held](GamepadButtons button) { return (held & static_cast<uint32_t>(button)) != 0; };

	uint32_t flags = 0;
	auto include = [&flags](ViGEmGamepad::Button flag)
	{
		flags |= static_cast<uint32_t>(flag);
	};

	if (has(GamepadButtons::DPadUp))
		include(ViGEmGamepad::DPadUp);
	if (has(GamepadButtons::DPadDown))
		include(ViGEmGamepad::DPadDown);
	if (has(GamepadButtons::DPadLeft))
		include(ViGEmGamepad::DPadLeft);
	if (has(GamepadButtons::DPadRight))
		include(ViGEmGamepad::DPadRight);
	if (has(GamepadButtons::Menu))
		include(ViGEmGamepad::Start);
	if (has(GamepadButtons::View))
		include(ViGEmGamepad::Back);
	if (has(GamepadButtons::LeftThumbstick))
		include(ViGEmGamepad::LeftThumb);
	if (has(GamepadButtons::RightThumbstick))
		include(ViGEmGamepad::RightThumb);
	if (has(GamepadButtons::LeftShoulder))
		include(ViGEmGamepad::LeftShoulder);
	if (has(GamepadButtons::RightShoulder))
		include(ViGEmGamepad::RightShoulder);
	if (has(GamepadButtons::A))
		include(ViGEmGamepad::A);
	if (has(GamepadButtons::B))
		include(ViGEmGamepad::B);
	if (has(GamepadButtons::X))
		include(ViGEmGamepad::X);
	if (has(GamepadButtons::Y))
		include(ViGEmGamepad::Y);

	return static_cast<uint16_t>(flags);
}
} // namespace

GamepadInjector::GamepadInjector() : injector(nullptr)
{
	qDebug() << "GamepadInjector constructor called";

	// Initialize gamepadState with default values
	gamepadState = InjectedInputGamepadInfo{};
	gamepadState.Buttons(static_cast<WinRTGamepadButtons>(0));
	gamepadState.LeftThumbstickX(0.0);
	gamepadState.LeftThumbstickY(0.0);
	gamepadState.RightThumbstickX(0.0);
	gamepadState.RightThumbstickY(0.0);
	gamepadState.LeftTrigger(0.0);
	gamepadState.RightTrigger(0.0);

	// Prefer ViGEmBus: it plugs in a real virtual USB device, so games that read
	// XInput or DirectInput detect this controller exactly like a physical one.
	// The injection API below only writes into the system input queue, which those
	// games never look at, so it is a fallback rather than the first choice.
	// The device is picked in Preferences. Reading the setting here, rather than
	// passing it down through the executor, keeps the choice out of every layer
	// that only forwards it.
	m_vigem = std::make_unique<ViGEmGamepad>(
		toVigemTarget(SettingsSingleton::instance().virtualGamepadType()));
	if (m_vigem->isReady())
	{
		m_available = true;
		m_lastError.clear();
		return;
	}

	const QString vigemError = m_vigem->lastError();
	m_vigem.reset();

	// Construction never throws: a machine that cannot create the injector still
	// runs the app, with the reason reported so the UI can explain why games see
	// no controller. Throwing here used to abort the whole application instead.
	m_lastError = QStringLiteral("%1. For a controller games detect as real hardware, "
								 "install ViGEmBus from https://github.com/nefarius/ViGEmBus/releases, "
								 "or use Keyboard + mouse mode, which works in every game.")
					  .arg(vigemError);
	try
	{
		injector = InputInjector::TryCreate();
		if (injector == nullptr)
		{
			qCritical() << "Failed to create input injector:" << m_lastError;
			return;
		}

		injector.InitializeGamepadInjection();
		m_available = true;
		// Still worth keeping: the UI explains that games may not see this pad.
		qInfo() << "Input injector created successfully and initialized for gamepad injection.";
	}
	catch (const winrt::hresult_error &ex)
	{
		const QString message = QString::fromWCharArray(ex.message().c_str());
		qCritical() << "Exception while creating gamepad injector:" << message;
		injector = nullptr;
		m_lastError = QStringLiteral("the Windows input injection API refused to start (%1), "
									 "and ViGEmBus is not available either (%2)")
						  .arg(message, vigemError);
	}
	catch (...)
	{
		qCritical() << "Unknown exception while creating gamepad injector.";
		injector = nullptr;
	}
}

GamepadInjector::~GamepadInjector()
{
	qDebug() << "GamepadInjector destructor called";
	if (m_vigem != nullptr)
		return; // The ViGEm pad unplugs itself.

	try
	{
		if (injector != nullptr)
			injector.UninitializeGamepadInjection();
		injector = nullptr; // Clear the injector to avoid dangling pointer
		qInfo() << "Gamepad injection uninitialized successfully.";
	}
	catch (...)
	{
		qDebug() << "Failed to uninitialize gamepad injection";
	}
}

void GamepadInjector::update(const InjectedInputGamepadInfo &state)
{
	gamepadState = state;
}

void GamepadInjector::pressButton(WinRTGamepadButtons button)
{
	gamepadState.Buttons(static_cast<WinRTGamepadButtons>(static_cast<uint32_t>(gamepadState.Buttons()) |
														  static_cast<uint32_t>(button)));
}

void GamepadInjector::releaseButton(WinRTGamepadButtons button)
{
	gamepadState.Buttons(static_cast<WinRTGamepadButtons>(static_cast<uint32_t>(gamepadState.Buttons()) &
														  ~static_cast<uint32_t>(button)));
}

void GamepadInjector::inject()
{
	if (!m_available)
		return;

	if (m_vigem != nullptr)
	{
		ViGEmGamepad::Report report;
		report.buttons = toXusbButtons(gamepadState.Buttons());
		// The executor already flips Y for Windows, which is what XInput expects.
		report.leftTrigger = static_cast<float>(gamepadState.LeftTrigger());
		report.rightTrigger = static_cast<float>(gamepadState.RightTrigger());
		report.leftX = static_cast<float>(gamepadState.LeftThumbstickX());
		report.leftY = static_cast<float>(gamepadState.LeftThumbstickY());
		report.rightX = static_cast<float>(gamepadState.RightThumbstickX());
		report.rightY = static_cast<float>(gamepadState.RightThumbstickY());
		m_vigem->submit(report);
		return;
	}

	if (injector)
	{
		try
		{
			injector.InjectGamepadInput(gamepadState);
		}
		catch (...)
		{
			qDebug() << "Failed to inject gamepad input";
		}
	}
}

QString GamepadInjector::backendName() const
{
	if (m_vigem != nullptr)
		return m_vigem->backendName();
	return QStringLiteral("Virtual gamepad (Windows input injection)");
}

bool GamepadInjector::detectsAsPhysicalController() const
{
	// Only the ViGEmBus device is a real device as far as a game is concerned.
	return m_vigem != nullptr;
}

QString GamepadInjector::systemReport() const
{
	if (m_vigem != nullptr)
		return m_vigem->systemReport();

	// The input-injection API writes into the system input queue: it creates no
	// device for anything to list, so there is nothing to report.
	return QString();
}
