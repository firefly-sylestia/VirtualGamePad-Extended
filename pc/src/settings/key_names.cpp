#include "key_names.hpp"

#include <QList>
#include <cstring>

namespace
{
/**
 * A named key on both platforms. Codes are plain numbers (Windows virtual keys
 * and Linux evdev key codes) so this table compiles the same everywhere and no
 * platform header is required here.
 */
struct KeyDef
{
	const char *name;
	InputKeyCode winVk;  // Windows virtual key code
	InputKeyCode linuxKey; // Linux evdev key code
	bool isMouseButton;
	bool isScroll;
};

/** Letters: Windows uses the ASCII virtual key, Linux uses evdev codes. */
const KeyDef kLetters[] = {
	{"A", 'A', 30, false, false}, {"B", 'B', 48, false, false}, {"C", 'C', 46, false, false},
	{"D", 'D', 32, false, false}, {"E", 'E', 18, false, false}, {"F", 'F', 33, false, false},
	{"G", 'G', 34, false, false}, {"H", 'H', 35, false, false}, {"I", 'I', 23, false, false},
	{"J", 'J', 36, false, false}, {"K", 'K', 37, false, false}, {"L", 'L', 38, false, false},
	{"M", 'M', 50, false, false}, {"N", 'N', 49, false, false}, {"O", 'O', 24, false, false},
	{"P", 'P', 25, false, false}, {"Q", 'Q', 16, false, false}, {"R", 'R', 19, false, false},
	{"S", 'S', 31, false, false}, {"T", 'T', 20, false, false}, {"U", 'U', 22, false, false},
	{"V", 'V', 47, false, false}, {"W", 'W', 17, false, false}, {"X", 'X', 45, false, false},
	{"Y", 'Y', 21, false, false}, {"Z", 'Z', 44, false, false},
};

const KeyDef kDigits[] = {
	{"Digit1", '1', 2, false, false}, {"Digit2", '2', 3, false, false}, {"Digit3", '3', 4, false, false},
	{"Digit4", '4', 5, false, false}, {"Digit5", '5', 6, false, false}, {"Digit6", '6', 7, false, false},
	{"Digit7", '7', 8, false, false}, {"Digit8", '8', 9, false, false}, {"Digit9", '9', 10, false, false},
	{"Digit0", '0', 11, false, false},
};

/**
 * Punctuation, by its keyboard legend. These are the OEM keys on Windows and
 * the punctuation keys in evdev on Linux; the ASCII code is not the same thing
 * as either, so each platform gets its own number here. A phone custom-key
 * button that sends "/" is how a Minecraft chat command is typed.
 */
const KeyDef kSymbols[] = {
	{"Slash", 0xBF, 53, false, false},       {"Backslash", 0xDC, 43, false, false},
	{"Semicolon", 0xBA, 39, false, false},   {"Apostrophe", 0xDE, 40, false, false},
	{"Comma", 0xBC, 51, false, false},       {"Period", 0xBE, 52, false, false},
	{"Minus", 0xBD, 12, false, false},       {"Equal", 0xBB, 13, false, false},
	{"LeftBracket", 0xDB, 26, false, false}, {"RightBracket", 0xDD, 27, false, false},
	{"Grave", 0xC0, 41, false, false},
};

/**
 * The single-character shorthand for a key, or an empty string when the
 * character is not one. Used so a phone can send "/" or "1" and still reach
 * the table above, which names the keys after their legends. The codes are
 * Unicode code points, which keeps the punctuation out of C++ character
 * literals that would need escaping.
 */
const char *shorthandName(QChar character)
{
	struct Shorthand
	{
		ushort code;
		const char *name;
	};
	static const Shorthand table[] = {
		{0x2F, "Slash"},       {0x5C, "Backslash"}, {0x3B, "Semicolon"}, {0x27, "Apostrophe"},
		{0x2C, "Comma"},       {0x2E, "Period"},    {0x2D, "Minus"},     {0x3D, "Equal"},
		{0x5B, "LeftBracket"}, {0x5D, "RightBracket"}, {0x60, "Grave"},
	};
	for (const auto &entry : table)
	{
		if (entry.code == character.unicode())
			return entry.name;
	}
	return "";
}

const KeyDef kSpecial[] = {
	{"Space", 0x20, 57, false, false},
	{"Enter", 0x0D, 28, false, false},
	{"Escape", 0x1B, 1, false, false},
	{"Tab", 0x09, 15, false, false},
	{"Backspace", 0x08, 14, false, false},
	{"Delete", 0x2E, 111, false, false},
	{"Insert", 0x2D, 110, false, false},
	{"CapsLock", 0x14, 58, false, false},
	{"LeftShift", 0xA0, 42, false, false},
	{"RightShift", 0xA1, 54, false, false},
	{"LeftCtrl", 0xA2, 29, false, false},
	{"RightCtrl", 0xA3, 97, false, false},
	{"LeftAlt", 0xA4, 56, false, false},
	{"RightAlt", 0xA5, 100, false, false},
	{"Up", 0x26, 103, false, false},
	{"Down", 0x28, 108, false, false},
	{"Left", 0x25, 105, false, false},
	{"Right", 0x27, 106, false, false},
	{"Home", 0x24, 102, false, false},
	{"End", 0x23, 107, false, false},
	{"PageUp", 0x21, 104, false, false},
	{"PageDown", 0x22, 109, false, false},
	{"F1", 0x70, 59, false, false},
	{"F2", 0x71, 60, false, false},
	{"F3", 0x72, 61, false, false},
	{"F4", 0x73, 62, false, false},
	{"F5", 0x74, 63, false, false},
	{"F6", 0x75, 64, false, false},
	{"F7", 0x76, 65, false, false},
	{"F8", 0x77, 66, false, false},
	{"F9", 0x78, 67, false, false},
	{"F10", 0x79, 68, false, false},
	{"F11", 0x7A, 87, false, false},
	{"F12", 0x7B, 88, false, false},
	{"PrintScreen", 0x2C, 99, false, false},
	{"NumpadPlus", 0x6B, 78, false, false},
	{"NumpadMinus", 0x6D, 74, false, false},
	{"Numpad0", 0x60, 82, false, false},
	{"Numpad1", 0x61, 79, false, false},
	{"Numpad2", 0x62, 80, false, false},
	{"Numpad3", 0x63, 81, false, false},
	{"Numpad4", 0x64, 75, false, false},
	{"Numpad5", 0x65, 76, false, false},
	{"Numpad6", 0x66, 77, false, false},
	{"Numpad7", 0x67, 71, false, false},
	{"Numpad8", 0x68, 72, false, false},
	{"Numpad9", 0x69, 73, false, false},
	{"MouseLeft", 0x01, 272, true, false},
	{"MouseRight", 0x02, 273, true, false},
	{"MouseMiddle", 0x04, 274, true, false},
	{"ScrollUp", 0, 0, false, true},
	{"ScrollDown", 0, 0, false, true},
};

const QList<KeyDef> &allDefs()
{
	static const QList<KeyDef> defs = []
	{
		QList<KeyDef> list;
		list.reserve(128);
		for (const auto &def : kLetters)
			list.append(def);
		for (const auto &def : kDigits)
			list.append(def);
		for (const auto &def : kSymbols)
			list.append(def);
		for (const auto &def : kSpecial)
			list.append(def);
		return list;
	}();
	return defs;
}

InputKeyCode platformCode(const KeyDef &def)
{
#if defined(_WIN32)
	return def.winVk;
#else
	return def.linuxKey;
#endif
}
} // namespace

namespace KeyNames
{

std::optional<ResolvedKey> resolve(const QString &name)
{
	if (name.isEmpty())
		return std::nullopt;

	for (const auto &def : allDefs())
	{
		if (name.compare(QString::fromUtf8(def.name), Qt::CaseInsensitive) != 0)
			continue;

		ResolvedKey key;
		key.isMouseButton = def.isMouseButton;
		key.isScroll = def.isScroll;
		key.display = QString::fromUtf8(def.name);
		if (def.isScroll)
			key.code = (std::strcmp(def.name, "ScrollUp") == 0) ? INPUT_SCROLL_UP : INPUT_SCROLL_DOWN;
		else
			key.code = platformCode(def);
		return key;
	}

	// Allow a single character as shorthand: "w" for the W key, "1" for
	// Digit1, "/" for Slash. The punctuation names exist because a phone's
	// custom-key button sends what the player typed, and a Minecraft chat
	// command starts with a slash.
	if (name.size() == 1)
	{
		const QChar character = name.at(0);
		if (character.isLetter())
			return resolve(name.toUpper());
		if (character.isDigit())
			return resolve(QStringLiteral("Digit%1").arg(character));
		const QString symbol = QString::fromLatin1(shorthandName(character));
		if (!symbol.isEmpty())
			return resolve(symbol);
	}

	return std::nullopt;
}

const QStringList &allNames()
{
	static const QStringList names = []
	{
		QStringList list;
		for (const auto &def : allDefs())
			list.append(QString::fromUtf8(def.name));
		return list;
	}();
	return names;
}

QString displayForCode(InputKeyCode code)
{
	if (code == 0)
		return QString();
	if (code == INPUT_SCROLL_UP)
		return QStringLiteral("Scroll Up");
	if (code == INPUT_SCROLL_DOWN)
		return QStringLiteral("Scroll Down");

	for (const auto &def : allDefs())
	{
		if (!def.isScroll && platformCode(def) == code)
			return QString::fromUtf8(def.name);
	}
	return QStringLiteral("0x%1").arg(code, 0, 16);
}

} // namespace KeyNames
