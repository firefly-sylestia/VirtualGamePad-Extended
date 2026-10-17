#include "../mouseSim.hpp"

#include "../input_target.hpp"

/**
 * Whether a mouse event has anywhere useful to go.
 *
 * The same reasoning as the keyboard injector: SendInput delivers to whatever
 * window has focus, and when that window is this application's own shell the
 * event lands in the layout editor instead of the game - a stray click on the
 * settings page, or the camera spinning a slider. Dropping it is the only safe
 * outcome, because there is no other window to aim at.
 */
static bool targetAcceptsInput()
{
	// Shared with the keyboard injector: it both keeps events out of this
	// application's own shell and honours a window the player has locked input
	// to in Preferences, so a connected phone cannot click in another program.
	return input_target::acceptsInjection();
}

MouseInjector::MouseInjector()
{
	// No initialization needed for Windows
}

MouseInjector::~MouseInjector()
{
	// No cleanup needed for Windows
}

void MouseInjector::moveMouseToPosition(int x, int y)
{
	if (!targetAcceptsInput())
		return;

	// Get the screen resolution
	int screenWidth = GetSystemMetrics(SM_CXSCREEN);
	int screenHeight = GetSystemMetrics(SM_CYSCREEN);

	// Calculate the absolute coordinates
	long absoluteX = (x * 65535L) / screenWidth;
	long absoluteY = (y * 65535L) / screenHeight;

	// Move the mouse
	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;
	input.mi.dx = absoluteX;
	input.mi.dy = absoluteY;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::moveMouseByOffset(int x, int y)
{
	if (!targetAcceptsInput())
		return;

	// Move the mouse
	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_MOVE_NOCOALESCE;
	input.mi.dx = x;
	input.mi.dy = y;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::leftClick()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_LEFTDOWN;
	SendInput(1, &input, sizeof(INPUT));

	Sleep(ClickHoldTime);

	ZeroMemory(&input, sizeof(INPUT));
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_LEFTUP;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::singleClick()
{
	if (!targetAcceptsInput())
		return;

	leftClick();
	Sleep(GetDoubleClickTime()); // Sleep for the double click time
}

void MouseInjector::leftDown()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_LEFTDOWN;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::leftUp()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_LEFTUP;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::doubleClick()
{
	if (!targetAcceptsInput())
		return;

	leftClick();
	leftClick();
}

void MouseInjector::rightClick()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_RIGHTDOWN;
	SendInput(1, &input, sizeof(INPUT));

	Sleep(ClickHoldTime);

	ZeroMemory(&input, sizeof(INPUT));
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_RIGHTUP;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::rightDown()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_RIGHTDOWN;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::rightUp()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_RIGHTUP;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::middleClick()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_MIDDLEDOWN;
	SendInput(1, &input, sizeof(INPUT));

	ZeroMemory(&input, sizeof(INPUT));
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_MIDDLEUP;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::middleDown()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_MIDDLEDOWN;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::middleUp()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_MIDDLEUP;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::scrollUp()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_WHEEL;
	input.mi.mouseData = WHEEL_DELTA;
	SendInput(1, &input, sizeof(INPUT));
}

void MouseInjector::scrollDown()
{
	if (!targetAcceptsInput())
		return;

	INPUT input = {};
	input.type = INPUT_MOUSE;
	input.mi.dwFlags = MOUSEEVENTF_WHEEL;
	input.mi.mouseData = -WHEEL_DELTA;
	SendInput(1, &input, sizeof(INPUT));
}
