#include "../keyboardSim.hpp"

#include "../input_target.hpp"

#include <QKeySequence>
#include <unordered_set>

/**
 * Extended key lookup table.
 * Reference:
 * https://learn.microsoft.com/en-us/windows/win32/inputdev/about-keyboard-input#extended-key-flag
 */
static const std::unordered_set<WORD> extendedKeys = {VK_RMENU,
													  VK_RCONTROL,
													  VK_INSERT,
													  VK_DELETE,
													  VK_HOME,
													  VK_END,
													  VK_PRIOR,
													  VK_NEXT,
													  VK_RIGHT,
													  VK_UP,
													  VK_LEFT,
													  VK_DOWN,
													  VK_NUMLOCK,
													  VK_CANCEL,
													  VK_SNAPSHOT,
													  VK_DIVIDE};

/**
 * Whether a keystroke has anywhere useful to go.
 *
 * SendInput delivers to whichever window currently has focus, which is what the
 * player wants - the game they are playing. The exception is this application's
 * own shell: if it holds focus, the keystrokes land in the layout editor, typing
 * into whichever field or button happens to be selected, and the game never sees
 * them. There is no other window to aim at in that case, so the press is dropped
 * rather than being allowed to disturb the settings screen.
 */
static bool targetAcceptsInput()
{
	// Shared with the mouse injector: it both keeps events out of this
	// application's own shell and honours a window the player has locked input
	// to in Preferences, so a connected phone cannot type into another program.
	return input_target::acceptsInjection();
}

KeyboardInjector::KeyboardInjector()
{
	// No initialization needed for Windows
}

KeyboardInjector::~KeyboardInjector()
{
	// No cleanup needed for Windows
}

/**
 * @brief Adds the scan code to the input structure.
 *
 * @details
 * This is necessary because some apps and games use the scan code
 * to determine the actual location of the key on the keyboard.
 * The difference becomes vital when using non-QWERTY keyboard layouts.
 */
void KeyboardInjector::addScanCode(INPUT &input, WORD key)
{
	input.ki.wScan = static_cast<WORD>(MapVirtualKeyW(key, MAPVK_VK_TO_VSC));
	input.ki.dwFlags |= KEYEVENTF_SCANCODE;
	if (extendedKeys.contains(key))
	{
		input.ki.dwFlags |= KEYEVENTF_EXTENDEDKEY;
	}
}

void KeyboardInjector::pressKey(quint32 nativeKeyCode)
{
	if (!targetAcceptsInput())
		return;

	WORD key = static_cast<WORD>(nativeKeyCode);
	INPUT input = {};
	input.type = INPUT_KEYBOARD;
	input.ki.wVk = key;
	addScanCode(input, key);
	SendInput(1, &input, sizeof(INPUT));
	// Wait
	Sleep(PRESS_INTERVAL);
	// Release the key
	input.ki.dwFlags |= KEYEVENTF_KEYUP;
	SendInput(1, &input, sizeof(INPUT));
}

void KeyboardInjector::pressKeyCombo(std::vector<quint32> nativeKeys)
{
	if (!targetAcceptsInput())
		return;

	std::vector<INPUT> inputs(nativeKeys.size() * 2);
	for (size_t i = 0; i < nativeKeys.size(); i++)
	{
		inputs[i].type = INPUT_KEYBOARD;
		inputs[i].ki.wVk = static_cast<WORD>(nativeKeys[i]);
		addScanCode(inputs[i], static_cast<WORD>(nativeKeys[i]));
	}
	// Wait
	Sleep(PRESS_INTERVAL);
	for (size_t i = 0; i < nativeKeys.size(); i++)
	{
		inputs[i + nativeKeys.size()].type = INPUT_KEYBOARD;
		inputs[i + nativeKeys.size()].ki.wVk = static_cast<WORD>(nativeKeys[i]);
		inputs[i + nativeKeys.size()].ki.dwFlags = KEYEVENTF_KEYUP;
		addScanCode(inputs[i + nativeKeys.size()], static_cast<WORD>(nativeKeys[i]));
	}
	SendInput(static_cast<UINT>(nativeKeys.size() * 2), inputs.data(), sizeof(INPUT));
}

void KeyboardInjector::keyDown(quint32 nativeKeyCode)
{
	if (!targetAcceptsInput())
		return;

	WORD key = static_cast<WORD>(nativeKeyCode);
	INPUT input = {};
	input.type = INPUT_KEYBOARD;
	input.ki.wVk = key;
	addScanCode(input, key);
	SendInput(1, &input, sizeof(INPUT));
}

void KeyboardInjector::keyUp(quint32 nativeKeyCode)
{
	if (!targetAcceptsInput())
		return;

	WORD key = static_cast<WORD>(nativeKeyCode);
	INPUT input = {};
	input.type = INPUT_KEYBOARD;
	input.ki.wVk = key;
	input.ki.dwFlags = KEYEVENTF_KEYUP;
	addScanCode(input, key);
	SendInput(1, &input, sizeof(INPUT));
}

void KeyboardInjector::keyComboUp(std::vector<quint32> nativeKeys)
{
	if (!targetAcceptsInput())
		return;

	std::vector<INPUT> inputs(nativeKeys.size());
	for (size_t i = 0; i < nativeKeys.size(); i++)
	{
		inputs[i].type = INPUT_KEYBOARD;
		inputs[i].ki.wVk = static_cast<WORD>(nativeKeys[i]);
		inputs[i].ki.dwFlags = KEYEVENTF_KEYUP;
		addScanCode(inputs[i], static_cast<WORD>(nativeKeys[i]));
	}
	SendInput(static_cast<UINT>(nativeKeys.size()), inputs.data(), sizeof(INPUT));
}

void KeyboardInjector::keyComboDown(std::vector<quint32> nativeKeys)
{
	if (!targetAcceptsInput())
		return;

	std::vector<INPUT> inputs(nativeKeys.size());
	for (size_t i = 0; i < nativeKeys.size(); i++)
	{
		inputs[i].type = INPUT_KEYBOARD;
		inputs[i].ki.wVk = static_cast<WORD>(nativeKeys[i]);
		addScanCode(inputs[i], static_cast<WORD>(nativeKeys[i]));
	}
	SendInput(static_cast<UINT>(nativeKeys.size()), inputs.data(), sizeof(INPUT));
}

void KeyboardInjector::typeUnicodeString(const QString &str)
{
	if (!targetAcceptsInput())
		return;

	std::wstring wstr = str.toStdWString();
	std::vector<INPUT> inputs(wstr.size() * 2);
	for (size_t i = 0; i < wstr.size(); i++)
	{
		// Press the key using Unicode support
		inputs[2 * i].type = INPUT_KEYBOARD;
		inputs[2 * i].ki.wScan = static_cast<WORD>(wstr[i]);
		inputs[2 * i].ki.dwFlags = KEYEVENTF_UNICODE;
		// Release the key
		inputs[2 * i + 1].type = INPUT_KEYBOARD;
		inputs[2 * i + 1].ki.wScan = static_cast<WORD>(wstr[i]);
		inputs[2 * i + 1].ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;
	}
	SendInput(static_cast<UINT>(wstr.size() * 2), inputs.data(), sizeof(INPUT));
}
