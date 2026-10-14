package io.github.kitswas.VGP_Data_Exchange;

// Base flags mirror the common Windows gamepad buttons. The extended flags carry
// mobile-only semantic controls; hosts may map them to keyboard/mouse actions.
public enum GameButtons {
  None(0x0),
  Menu(0x1),
  View(0x2),
  A(0x4),
  B(0x8),
  X(0x10),
  Y(0x20),
  DPadUp(0x40),
  DPadDown(0x80),
  DPadLeft(0x100),
  DPadRight(0x200),
  LeftShoulder(0x400),
  RightShoulder(0x800),
  LeftThumbstick(0x1000),
  RightThumbstick(0x2000),
  Plus(0x4000),
  Minus(0x8000),
  Home(0x10000),
  Capture(0x20000),
  LeftSide(0x40000),
  RightSide(0x80000),
  Paddle1(0x100000),
  Paddle2(0x200000),
  Paddle3(0x400000),
  Paddle4(0x800000);

  private final int value;

  GameButtons(int value) { this.value = value; }

  public int getValue() { return value; }
}
