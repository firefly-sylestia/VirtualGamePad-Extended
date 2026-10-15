/**
 * @file game_layout.hpp
 * @brief Ready-made controller layouts for the PC side.
 *
 * A layout is a named preset that fills the whole active keymap profile: every
 * gamepad button, both thumbsticks, both triggers and any extra "custom" keys
 * the game needs. This is what makes one physical mobile controller behave like
 * a comfortable controller for Minecraft, a shooter, a racing game and so on.
 */
#pragma once

#include "../../VGP_Data_Exchange/C/GameButtons.h"
#include "input_types.hpp"

#include <QString>
#include <QVector>

/** A single gamepad control bound to a named key. */
struct LayoutBinding
{
	GamepadButtons button = GamepadButtons_A;
	QString key;   // A name the resolver understands, empty means "leave unbound"
	QString label; // Game meaning of the binding, for example "Jump"
};

/** How a thumbstick behaves in a layout. */
struct LayoutThumbstick
{
	QString up;
	QString down;
	QString left;
	QString right;
	bool mouseMove = false;
	bool mouseScroll = false;
};

/** A complete, applicable controller layout. */
struct GameLayout
{
	QString id;
	QString name;
	QString tagline;
	QString description;
	QString accent = "#6c7cff";
	bool minecraftThemed = false;

	QVector<LayoutBinding> buttons;
	QVector<LayoutBinding> customKeys; // Extra keys layered on top of the defaults
	LayoutThumbstick leftStick;
	LayoutThumbstick rightStick;
	QString leftTriggerKey;
	QString rightTriggerKey;
	float triggerThreshold = 0.35f;
};

/** All built-in layouts, in presentation order. */
const QVector<GameLayout> &allGameLayouts();

/** Look a layout up by its stable id. Returns nullptr when unknown. */
const GameLayout *gameLayoutById(const QString &id);

/** The layout whose id is @p id, or the first layout when unknown. */
const GameLayout &gameLayoutOrFirst(const QString &id);

/**
 * @brief Apply a layout to the active keymap profile and save it.
 *
 * @param layout The layout to apply.
 * @param error  Optional out-parameter describing a failure.
 * @return true when the profile was updated and saved.
 */
bool applyGameLayout(const GameLayout &layout, QString *error = nullptr);
