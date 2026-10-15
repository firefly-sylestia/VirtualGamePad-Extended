/**
 * @file key_names.hpp
 * @brief Human-readable key names <-> platform native key codes.
 *
 * Game layout presets and the "add a key" editor describe keys by name (for
 * example "Space" or "Digit1") so the same preset works on Windows and Linux.
 * This module resolves a name into the platform native code that the executor
 * understands.
 */
#pragma once

#include "input_types.hpp"

#include <QString>
#include <QStringList>
#include <optional>

namespace KeyNames
{
/** A resolved key: native code plus a friendly label. */
struct ResolvedKey
{
	InputKeyCode code = 0;
	QString display;
	bool isMouseButton = false;
	bool isScroll = false;
};

/** Resolve a key name such as "Space", "W" or "ScrollUp". */
std::optional<ResolvedKey> resolve(const QString &name);

/** Every key name the resolver understands, in a stable order. */
const QStringList &allNames();

/** The friendly label for a native code, or a hex fallback when unknown. */
QString displayForCode(InputKeyCode code);
} // namespace KeyNames
