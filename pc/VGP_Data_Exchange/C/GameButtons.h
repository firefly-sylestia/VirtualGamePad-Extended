#pragma once
// Base flags mirror the common Windows gamepad button set.
// Extended flags identify mobile controller controls that may need host-side mapping.
// Keep this in sync with mobile/VGP_Data_Exchange/C/GameButtons.h.
enum GamepadButtons
{
	GamepadButtons_None = 0x0,
	GamepadButtons_Menu = 0x1,
	GamepadButtons_View = 0x2,
	GamepadButtons_A = 0x4,
	GamepadButtons_B = 0x8,
	GamepadButtons_X = 0x10,
	GamepadButtons_Y = 0x20,
	GamepadButtons_DPadUp = 0x40,
	GamepadButtons_DPadDown = 0x80,
	GamepadButtons_DPadLeft = 0x100,
	GamepadButtons_DPadRight = 0x200,
	GamepadButtons_LeftShoulder = 0x400,
	GamepadButtons_RightShoulder = 0x800,
	GamepadButtons_LeftThumbstick = 0x1000,
	GamepadButtons_RightThumbstick = 0x2000,
	GamepadButtons_Plus = 0x4000,
	GamepadButtons_Minus = 0x8000,
	GamepadButtons_Home = 0x10000,
	GamepadButtons_Capture = 0x20000,
	GamepadButtons_LeftSide = 0x40000,
	GamepadButtons_RightSide = 0x80000,
	GamepadButtons_Paddle1 = 0x100000,
	GamepadButtons_Paddle2 = 0x200000,
	GamepadButtons_Paddle3 = 0x400000,
	GamepadButtons_Paddle4 = 0x800000
};
