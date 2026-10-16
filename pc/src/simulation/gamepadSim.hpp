#pragma once

#include <QDebug>
#include <QString>
#include <cmath>

#ifdef _WIN32
// Windows Runtime includes for gamepad injection
#include <winrt/Windows.Gaming.Input.h>
#include <winrt/Windows.UI.Input.Preview.Injection.h>
#include <winrt/base.h>

#include "windows/vigemClient.hpp"
#include <memory>

using winrt::Windows::UI::Input::Preview::Injection::InjectedInputGamepadInfo;
using winrt::Windows::UI::Input::Preview::Injection::InputInjector;
using WinRTGamepadButtons = winrt::Windows::Gaming::Input::GamepadButtons;

/**
 * Report bits for the controls the WinRT button enum has no member of its own for.
 *
 * The enum is what the injection fallback understands, and it stops at the
 * controls an Xbox pad has: there is no member for +/−, HOME/Capture or the
 * Joy-Con rail buttons. Turning each of those into the nearest member instead —
 * + and HOME both becoming Menu, SL/SR becoming the shoulders — is what made
 * them indistinguishable to a game, so they get bits of their own above the
 * enum's, which the platform-specific reporting below translates.
 */
namespace vgp_native
{
constexpr uint32_t Plus = 1u << 20;
constexpr uint32_t Minus = 1u << 21;
constexpr uint32_t Home = 1u << 22;
constexpr uint32_t Capture = 1u << 23;
constexpr uint32_t LeftSide = 1u << 24;
constexpr uint32_t RightSide = 1u << 25;
} // namespace vgp_native
#elif defined(__linux__)
// Linux includes for gamepad injection
#include <libevdev/libevdev-uinput.h>
#include <libevdev/libevdev.h>
#include <linux/input.h>
#include <memory>
#endif

/**
 * @brief Behaves like a virtual device (gamepad).
 *
 * @details
 * Platform-specific implementations:
 * - Windows: ViGEmBus when it is installed, otherwise the Windows UI Input
 *   Injection API (Preview) from WinRT
 *   - ViGEmBus presents a real virtual USB device, so XInput/DirectInput games
 *     detect it like a physical controller and no elevation is needed
 *   - The injection API writes into the system input queue instead, which most
 *     games ignore; it needs Windows 10 or later and usually Admin rights
 * - Linux: Uses libevdev and uinput for gamepad simulation
 *   - Games read it as a physical pad through evdev/SDL
 *   - Requires access to /dev/uinput
 *   - User must be in 'input' group or run as root
 *
 * Not movable or copyable
 */
class GamepadInjector
{
  private:
#ifdef _WIN32
	InjectedInputGamepadInfo gamepadState;
	InputInjector injector;
	/** The ViGEmBus pad, when that driver is available. Null otherwise. */
	std::unique_ptr<ViGEmGamepad> m_vigem;
#elif defined(__linux__)
	std::unique_ptr<libevdev, void (*)(libevdev *)> dev;
	std::unique_ptr<libevdev_uinput, void (*)(libevdev_uinput *)> uidev;
	int fd;
	// Button states for tracking press/release
	bool buttonStates[BTN_GAMEPAD - BTN_JOYSTICK + 16]; // Enough for all gamepad buttons
#endif
	bool m_available = false;
	QString m_lastError;

  public:
	GamepadInjector();
	~GamepadInjector();

	// Delete copy and move operations due to platform-specific restrictions
	GamepadInjector(const GamepadInjector &) = delete;
	GamepadInjector &operator=(const GamepadInjector &) = delete;
	GamepadInjector(GamepadInjector &&) = delete;
	GamepadInjector &operator=(GamepadInjector &&) = delete;

	/**
	 * @brief Whether the virtual gamepad device exists and can be injected into.
	 *
	 * Construction never throws: a machine without permission to create the
	 * device still runs the app, with this reporting the reason so the UI can
	 * explain why games see nothing.
	 */
	bool isAvailable() const { return m_available; }

	/** Human-readable reason the device is unavailable, or an empty string. */
	QString lastError() const { return m_lastError; }

	/**
	 * @brief Name of the mechanism actually in use, for the UI.
	 *
	 * The two backends are not equivalent as far as games are concerned, so the
	 * user needs to be told which one is live rather than just "a controller".
	 */
	QString backendName() const;

	/**
	 * @brief Whether games see this device as a physical controller.
	 *
	 * True for both a uinput pad on Linux and a ViGEmBus pad on Windows: those
	 * present a real device that XInput/DirectInput/evdev readers pick up. The
	 * Windows input-injection API only writes into the system input queue, which
	 * most games never read, so it reports false and the UI says so.
	 */
	bool detectsAsPhysicalController() const;

	/**
	 * @brief What the operating system itself reports for the created pad.
	 *
	 * Shown by the UI so that "the game does not detect it" can be told apart
	 * from "the pad was never created": this is what the system's own controller
	 * APIs answer when they are asked about the device. Empty when a backend
	 * creates no device at all, and empty before a pad exists.
	 */
	QString systemReport() const;

#ifdef _WIN32
	/**
	 * @brief Updates the gamepad state (Windows only).
	 *
	 * @param state The new gamepad state.
	 */
	void update(const InjectedInputGamepadInfo &state);

	void pressButton(WinRTGamepadButtons button);

	void releaseButton(WinRTGamepadButtons button);
#elif defined(__linux__)
	/**
	 * @brief Sets thumbstick values (Linux).
	 *
	 * @param leftX Left thumbstick X (-1.0 to 1.0)
	 * @param leftY Left thumbstick Y (-1.0 to 1.0)
	 * @param rightX Right thumbstick X (-1.0 to 1.0)
	 * @param rightY Right thumbstick Y (-1.0 to 1.0)
	 */
	void setThumbsticks(float leftX, float leftY, float rightX, float rightY);

	/**
	 * @brief Sets trigger values (Linux).
	 *
	 * @param leftTrigger Left trigger (0.0 to 1.0)
	 * @param rightTrigger Right trigger (0.0 to 1.0)
	 */
	void setTriggers(float leftTrigger, float rightTrigger);

	/**
	 * @brief Press a gamepad button (Linux).
	 *
	 * @param buttonCode Linux input event code (BTN_A, BTN_B, etc.)
	 */
	void pressButton(int buttonCode);

	/**
	 * @brief Release a gamepad button (Linux).
	 *
	 * @param buttonCode Linux input event code (BTN_A, BTN_B, etc.)
	 */
	void releaseButton(int buttonCode);
#endif

	/**
	 * @brief Injects the current gamepad state into the system.
	 *
	 * @note
	 * This method sends the current gamepad state to the system for processing.
	 * None of the other methods will take effect until this method is called.
	 */
	void inject();
};
