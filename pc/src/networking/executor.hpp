#pragma once

#include "../../VGP_Data_Exchange/C/Colfer.h"
#include "../settings/input_types.hpp"
#include "../simulation/gamepadSim.hpp"
#include "../simulation/keyboardSim.hpp"
#include "../simulation/mouseSim.hpp"

#include <QString>
#include <array>
#include <cstdint>
#include <memory>
#include <unordered_map>

struct ParseResult
{
	enum class FailureReason
	{
		None,
		IncompleteData,
		SchemaMismatch,
		DataTooLarge
	};
	vgp_data_exchange_gamepad_reading reading;
	size_t bytes_consumed;
	bool success;
	FailureReason failure_reason = FailureReason::None;
};

ParseResult parse_gamepad_state(const char *data, size_t len);

/**
 * An executor Interface for handling gamepad state injection
 */
class ExecutorInterface
{
  public:
	virtual ~ExecutorInterface() = default;

	virtual bool inject_gamepad_state(vgp_data_exchange_gamepad_reading const &reading) = 0;

	/**
	 * @brief Whether this backend can actually deliver input to the system.
	 *
	 * Reported to the UI through InputBackendStatus when the executor is built,
	 * so a backend that cannot work (no /dev/uinput access, a refused Windows
	 * injection API) is explained instead of failing silently.
	 */
	virtual bool isAvailable() const { return true; }
};

class GamepadExecutor : public ExecutorInterface
{
  public:
	/** Builds the virtual gamepad and publishes whether it worked. Never throws. */
	GamepadExecutor();
	~GamepadExecutor() override = default;

	bool isAvailable() const override { return m_injector.isAvailable(); }

	// Delete copy and move operations because GamepadInjector is non-movable
	GamepadExecutor(const GamepadExecutor &) = delete;
	GamepadExecutor &operator=(const GamepadExecutor &) = delete;
	GamepadExecutor(GamepadExecutor &&) = delete;
	GamepadExecutor &operator=(GamepadExecutor &&) = delete;

	bool inject_gamepad_state(vgp_data_exchange_gamepad_reading const &reading) override;

  private:
	GamepadInjector m_injector;
	std::unordered_map<int, int> m_nativeButtonPressCounts;
	std::unordered_map<uint32_t, bool> m_semanticButtonStates;

	void pressNativeButton(int buttonCode);
	void releaseNativeButton(int buttonCode);
	void setNativeButtonState(uint32_t semanticButton, int nativeButton, bool pressed);
};

/**
 * @brief Executes gamepad input by converting it to keyboard and mouse actions.
 *
 * @details
 * This class takes gamepad input and converts it to equivalent keyboard and mouse
 * actions based on the active keymap profile. It handles button mappings,
 * thumbstick-to-mouse movement, and thumbstick-to-key mappings.
 */
class KeyboardMouseExecutor : public ExecutorInterface
{
  public:
	/**
	 * @brief Threshold for thumbstick input to register as button press.
	 */
	static constexpr double THRESHOLD = 0.5;

	KeyboardMouseExecutor();
	~KeyboardMouseExecutor() override = default;

	// Delete copy and move operations
	KeyboardMouseExecutor(const KeyboardMouseExecutor &) = delete;
	KeyboardMouseExecutor &operator=(const KeyboardMouseExecutor &) = delete;
	KeyboardMouseExecutor(KeyboardMouseExecutor &&) = delete;
	KeyboardMouseExecutor &operator=(KeyboardMouseExecutor &&) = delete;

	/**
	 * @brief Processes gamepad input and executes corresponding keyboard/mouse actions.
	 *
	 * @param reading The gamepad input data to process.
	 * @return true if the input was processed successfully, false otherwise.
	 */
	bool inject_gamepad_state(vgp_data_exchange_gamepad_reading const &reading) override;

  private:
	/** One mouse-wheel tick at most this often while a stick is held over. */
	static constexpr qint64 SCROLL_INTERVAL_MS = 70;

	std::unique_ptr<KeyboardInjector> m_keyboardInjector;
	std::unique_ptr<MouseInjector> m_mouseInjector;

	/** Timestamps (ms since epoch of steady clock) of the last wheel tick per stick. */
	std::array<qint64, 2> m_lastScrollAt{0, 0};

	void handleButtonDown(const ButtonInput &buttonInput);
	void handleButtonUp(const ButtonInput &buttonInput);
	void handleThumbstickInput(const ThumbstickInput &thumbstick,
							   float x_value,
							   float y_value,
							   double threshold,
							   int stickIndex);
	void handleTriggerInput(const TriggerInput &trigger, float trigger_value);

	/** Fire any extra keys the active profile layers on top of the standard map. */
	void handleCustomBindings(const vgp_data_exchange_gamepad_reading &reading);
};
