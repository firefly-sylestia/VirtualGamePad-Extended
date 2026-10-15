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
 * per-game configuration and without running the PC app as administrator. It is a
 * separate download, so the client library is loaded at run time: when it is not
 * installed, [`isReady`] is false and the caller keeps its fallback instead of
 * failing to build or start.
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
	/**
	 * One report, in the units this class converts to the driver's:
	 * triggers are 0..1, thumbsticks -1..1, and buttons is a mask of XUSB flags.
	 */
	struct Report
	{
		uint16_t buttons = 0;
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

	/** Connects to the driver and plugs in the virtual pad. Never throws. */
	ViGEmGamepad();
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

	/** Human-readable name of the driver behind the pad, for the UI. */
	static QString backendName();

  private:
	struct Impl;
	std::unique_ptr<Impl> m_impl;
};
