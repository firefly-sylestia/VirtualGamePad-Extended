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

	/**
	 * @brief What this backend is called, as the page shows it.
	 *
	 * Needed by a backend made of more than one, which has to name the parts it
	 * is running rather than only the one that reported itself last.
	 */
	virtual QString backendName() const = 0;

	/** The note that goes with [backendName], saying what it can and cannot do. */
	virtual QString backendDetail() const = 0;

	/**
	 * @brief Let go of every key, button and axis this backend is holding.
	 *
	 * Called when the stream from the phone ends — the link dropped, no packet
	 * arrived for a while, the app is closing, or another backend is taking over.
	 * Without it, anything that was down when the stream stopped stays down: a
	 * held mouse button turns every later click into a drag and a held Shift or
	 * Ctrl changes every keystroke, which looks like the PC has stopped responding
	 * and cannot be undone from the phone any more. Must be safe to call when
	 * nothing is held.
	 */
	virtual void releaseAll() = 0;
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

	QString backendName() const override { return m_backendName; }
	QString backendDetail() const override { return m_backendDetail; }
	void releaseAll() override;

  private:
	GamepadInjector m_injector;
	std::unordered_map<int, int> m_nativeButtonPressCounts;
	std::unordered_map<uint32_t, bool> m_semanticButtonStates;

	/** What this backend reported when it was built, for the page to show. */
	QString m_backendName;
	QString m_backendDetail;

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

	QString backendName() const override { return m_backendName; }
	QString backendDetail() const override { return m_backendDetail; }
	void releaseAll() override;

  private:
	/** What this backend reported when it was built, for the page to show. */
	QString m_backendName;
	QString m_backendDetail;

	/**
	 * Output key or mouse button -> how many controls are holding it down.
	 *
	 * The phone reports which controls are held on every packet, so the press is
	 * only news the first time it arrives; this is what turns that repeating state
	 * into one press and one release, and what [releaseAll] empties.
	 */
	std::unordered_map<InputKeyCode, int> m_heldInputs;

	/** One mouse-wheel tick at most this often while a stick is held over. */
	static constexpr qint64 SCROLL_INTERVAL_MS = 70;

	std::unique_ptr<KeyboardInjector> m_keyboardInjector;
	std::unique_ptr<MouseInjector> m_mouseInjector;

	/** Timestamps (ms since epoch of steady clock) of the last wheel tick per stick. */
	std::array<qint64, 2> m_lastScrollAt{0, 0};

	/** Press or lift one output, counting how many controls hold it. */
	void handleButtonDown(const ButtonInput &buttonInput);
	void handleButtonUp(const ButtonInput &buttonInput);

	/** The events for one input, with no bookkeeping around them. */
	void injectButtonDown(const ButtonInput &buttonInput);
	void injectButtonUp(const ButtonInput &buttonInput);
	void handleThumbstickInput(const ThumbstickInput &thumbstick,
							   float x_value,
							   float y_value,
							   double threshold,
							   int stickIndex);
	void handleTriggerInput(const TriggerInput &trigger, float trigger_value);

	/** Fire any extra keys the active profile layers on top of the standard map. */
	void handleCustomBindings(const vgp_data_exchange_gamepad_reading &reading);
};

/**
 * @brief The virtual gamepad and the keyboard/mouse mapping, both at once.
 *
 * Declared after both of the backends it runs, because it holds each of them by
 * value and a class member has to be a complete type.
 *
 * The two backends answer different questions. A controller-aware game reads the
 * pad, while the desktop, a launcher, or a program with no controller support
 * only reacts to keys and the pointer — and which of those the phone is being
 * used for is not knowable in advance, which is why the app used to need the
 * mode switched by hand. Feeding the same reading to both means whichever kind
 * of program is in front of the user reacts.
 *
 * The cost is a program that reads controller *and* key input itself: it sees
 * both versions of one press, which is why the single-backend modes remain in
 * Preferences for anyone who hits that.
 */
class CombinedExecutor : public ExecutorInterface
{
  public:
	/** Builds both backends and publishes what the pair can do. Never throws. */
	CombinedExecutor();
	~CombinedExecutor() override = default;

	CombinedExecutor(const CombinedExecutor &) = delete;
	CombinedExecutor &operator=(const CombinedExecutor &) = delete;
	CombinedExecutor(CombinedExecutor &&) = delete;
	CombinedExecutor &operator=(CombinedExecutor &&) = delete;

	bool inject_gamepad_state(vgp_data_exchange_gamepad_reading const &reading) override;
	/** True while the keyboard and mouse path exists, which is always here. */
	bool isAvailable() const override;
	void releaseAll() override;

	QString backendName() const override;
	QString backendDetail() const override;

  private:
	GamepadExecutor m_gamepad;
	KeyboardMouseExecutor m_keyboardMouse;
};
