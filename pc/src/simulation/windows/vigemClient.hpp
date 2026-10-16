/**
 * @file vigemClient.hpp
 * @brief A virtual Xbox 360 controller that Windows games detect as real hardware.
 *
 * The Windows input-injection API (`Windows.UI.Input.Preview.Injection`, used by
 * `GamepadInjector` as a fallback) writes into the system's own input queue. Games
 * that read XInput, DirectInput or RawInput directly — which is nearly all of them
 * — never look there, so the PC side looked like "no controller connected" even
 * while the phone was clearly sending input.
 *
 * ViGEmBus is a kernel driver that presents a virtual USB device instead, so a
 * game detects this pad exactly like a plugged-in Xbox 360 controller, with no
 * per-game configuration and without running the PC app as administrator. The
 * client library is built with the application and installed beside it, while
 * the driver is a separate install: when the driver is missing, [`isReady`] is
 * false and the caller keeps its fallback instead of failing to build or start.
 *
 * Two kinds of device can be plugged in, and they are not interchangeable:
 *
 * - **Xbox 360** is an XInput device. Modern games use XInput and get this pad
 *   as a first-class controller, but an XInput device does *not* appear in the
 *   HID or DirectInput device list, so older games and launchers that only
 *   enumerate those see nothing at all.
 * - **DualShock 4** is a HID gamepad. It shows up through the HID stack, so
 *   DirectInput-only readers find it too, which is the difference between "the
 *   game has no controller" and "the game found a PlayStation pad".
 *
 * Both are plugged in at once by default, because a program builds its device
 * list from one API or the other — present only one and the pad is missing from
 * half of the lists. A single kind can still be chosen in Preferences for a
 * program that reads both APIs and would act on two pads at once.
 *
 * Reference: https://github.com/nefarius/ViGEmClient (MIT).
 */
#pragma once

#include <QString>
#include <cstdint>
#include <memory>

class ViGEmGamepad
{
  public:
	/** Which virtual device(s) the driver plugs in. */
	enum class TargetType
	{
		/** Xbox 360 pad: XInput only, so DirectInput-only readers miss it. */
		Xbox360,
		/** DualShock 4 pad: also a HID gamepad, so DirectInput readers see it. */
		DualShock4,
		/**
		 * Both, side by side.
		 *
		 * A program's own device list is built from one API or the other, and which
		 * one it reads is not something the user can see: games that read XInput
		 * find only the Xbox pad, while emulators, launchers and DirectInput readers
		 * find only the DualShock. Presenting the pair means whichever list is open
		 * has a pad in it, and the user picks between two devices exactly as they
		 * would with two real controllers plugged in.
		 */
		Both
	};

	/**
	 * One report, in the units this class converts to the driver's:
	 * triggers are 0..1, thumbsticks -1..1, and buttons is a mask of XUSB flags.
	 *
	 * The mask owns the *meaning* of each button; picking the target decides how
	 * that meaning is presented, so callers never deal with two button spaces.
	 */
	struct Report
	{
		uint16_t buttons = 0;
		/**
		 * Controls the XUSB button word has no room for.
		 *
		 * An Xbox 360 pad has no button left over once the standard ones are
		 * placed, so a control that arrives here is only reported by a device that
		 * really has somewhere to put it — the DualShock 4's touchpad click is what
		 * a Capture button becomes there.
		 */
		uint16_t extraButtons = 0;
		float leftTrigger = 0.0f;
		float rightTrigger = 0.0f;
		float leftX = 0.0f;
		float leftY = 0.0f;
		float rightX = 0.0f;
		float rightY = 0.0f;
	};

	/** XUSB button flags, the ones the virtual device reports. */
	enum Button : uint16_t
	{
		DPadUp = 0x0001,
		DPadDown = 0x0002,
		DPadLeft = 0x0004,
		DPadRight = 0x0008,
		Start = 0x0010,
		Back = 0x0020,
		LeftThumb = 0x0040,
		RightThumb = 0x0080,
		LeftShoulder = 0x0100,
		RightShoulder = 0x0200,
		Guide = 0x0400,
		A = 0x1000,
		B = 0x2000,
		X = 0x4000,
		Y = 0x8000,
	};

	/** Flags for [Report::extraButtons], one per control with no XUSB bit. */
	enum ExtraButton : uint16_t
	{
		/** The DualShock 4's touchpad click, which is the Capture button. */
		Touchpad = 0x0001,
	};

	/** Connects to the driver and plugs in the virtual pad. Never throws. */
	explicit ViGEmGamepad(TargetType type = TargetType::Xbox360);
	~ViGEmGamepad();

	ViGEmGamepad(const ViGEmGamepad &) = delete;
	ViGEmGamepad &operator=(const ViGEmGamepad &) = delete;
	ViGEmGamepad(ViGEmGamepad &&) = delete;
	ViGEmGamepad &operator=(ViGEmGamepad &&) = delete;

	/** True when the driver is present and the virtual pad is plugged in. */
	bool isReady() const;

	/** Why the pad is not ready, in words the UI can show. Empty when ready. */
	QString lastError() const;

	/** Sends one report to the virtual pad. */
	void submit(const Report &report);

	/** Which device this instance is presenting. */
	TargetType targetType() const;

	/**
	 * What the system itself currently reports for the created pad, in words the
	 * UI can show. Empty when the pad is not ready.
	 *
	 * "Is it detected as an input device?" is answered by asking the same APIs a
	 * game uses — XInput for the Xbox 360 pad, the joystick API behind the
	 * system's own controller list for the DualShock 4 — so the app states what
	 * the system sees instead of leaving the user to guess which list to look in.
	 */
	QString systemReport() const;

	/** Human-readable name of the device behind the pad, for the UI. */
	QString backendName() const;

	/** Human-readable name of a device kind, for a caller with no instance yet. */
	static QString nameFor(TargetType type);

  private:
	struct Impl;
	std::unique_ptr<Impl> m_impl;
};
