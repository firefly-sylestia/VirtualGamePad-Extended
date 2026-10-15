#pragma once

#include "../../VGP_Data_Exchange/C/GameButtons.h"

#include <QString>
#include <Qt>

#ifdef WIN32
#include <windows.h>
#elif defined(__linux__)
#include <linux/input.h>
#endif

/**
 * @brief Return type of `QKeyEvent::nativeVirtualKey()` and `QKeyEvent::nativeScanCode()`
 */
using InputKeyCode = quint32;

/**
 * Pseudo key codes used to describe mouse-wheel actions. They never collide
 * with a real native key code because the top byte is 0xFF, which the platform
 * APIs do not use for keys or mouse buttons.
 */
constexpr InputKeyCode INPUT_SCROLL_UP = 0xFFFF0001u;
constexpr InputKeyCode INPUT_SCROLL_DOWN = 0xFFFF0002u;

/** True when @p code is one of the pseudo mouse-wheel action codes. */
inline bool is_scroll_action(InputKeyCode code)
{
	return code == INPUT_SCROLL_UP || code == INPUT_SCROLL_DOWN;
}

struct ButtonInput
{
	InputKeyCode vk{};			  // Platform native virtual key code
	bool is_mouse_button = false; // true if the button is a mouse button
	QString displayName;		  // Human-readable name for the input
};

/**
 * A thumbstick has two axes (X and Y)
 * Types of input:
 * - The entire thumbstick can be mapped to mouse movement
 * - Or the thumbstick directions can be mapped to keyboard keys/mouse clicks
 * */
struct ThumbstickInput
{
	bool is_mouse_move = false; // true if the thumbstick is mapped to mouse movement
	ButtonInput up{}, down{}, left{}, right{}; // up, down, left, right directions of the thumbstick
	// Declared last so the existing brace initialisers keep their meaning.
	bool is_mouse_scroll = false; // true if the thumbstick cycles the mouse wheel (inventory/hotbar)
};

/**
 * A trigger can be mapped to execute a ButtonInput when beyond a threshold
 */
struct TriggerInput
{
	ButtonInput button_input{}; // The button input to execute when trigger is beyond threshold
	float threshold = 0.5f;		// Button press threshold (0.0 to 1.0)
};

/**
 * Thumbstick enum
 */
enum Thumbstick
{
	Thumbstick_Left,
	Thumbstick_Right
};

/**
 * Trigger enum
 */
enum class Trigger
{
	Left,
	Right
};

// Helper functions for mouse button detection using native virtual keys
#ifdef WIN32
inline bool is_mouse_button(InputKeyCode code)
{
	return code == VK_LBUTTON || code == VK_RBUTTON || code == VK_MBUTTON;
}
#elif defined(__linux__)
inline bool is_mouse_button(InputKeyCode code)
{
	return code == BTN_LEFT || code == BTN_RIGHT || code == BTN_MIDDLE;
}
#else
inline bool is_mouse_button(InputKeyCode code)
{
	return false; // Fallback for unsupported platforms
}
#endif
