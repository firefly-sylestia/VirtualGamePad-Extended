#include "vigemClient.hpp"

#include <QDebug>

#include <algorithm>
#include <cmath>
#include <windows.h>

namespace
{
/**
 * `VIGEM_ERROR_NONE` from ViGEm's `Client.h`. Every failure code sets the high
 * bit, so this is the only value that means "the driver accepted the call".
 */
constexpr uint32_t kErrorNone = 0x20000000u;

/**
 * ViGEm's `XUSB_REPORT`, laid out exactly as the driver expects. It is declared
 * here instead of including the SDK so the project builds — and the app ships —
 * without the ViGEm headers or an import library.
 */
struct XusbReport
{
	uint16_t wButtons;
	uint8_t bLeftTrigger;
	uint8_t bRightTrigger;
	int16_t sThumbLX;
	int16_t sThumbLY;
	int16_t sThumbRX;
	int16_t sThumbRY;
};

static_assert(sizeof(XusbReport) == 12, "XUSB_REPORT is four 16-bit pairs");

/**
 * ViGEm's `DS4_REPORT`: the DualShock 4's own HID input report, which is what
 * the driver forwards to the HID stack once a target is plugged in.
 *
 * It is declared here for the same reason as [XusbReport], and it is passed by
 * value, so the layout has to match the SDK byte for byte. `Common.h` does not
 * pack this one (the `#pragma pack` around it only covers the structs after
 * it), so it carries a byte of tail padding and is ten bytes, not nine: with
 * nine the driver would read one byte past what was copied. The trailing
 * `static_assert` makes a wrong assumption fail the build instead of sending
 * the driver garbage.
 */
struct Ds4Report
{
	uint8_t bThumbLX;
	uint8_t bThumbLY;
	uint8_t bThumbRX;
	uint8_t bThumbRY;
	uint16_t wButtons;
	uint8_t bSpecial;
	uint8_t bTriggerL;
	uint8_t bTriggerR;
};

static_assert(sizeof(Ds4Report) == 10, "DS4_REPORT is padded to its 16-bit alignment");

/**
 * `DS4_BUTTONS` and `DS4_SPECIAL_BUTTONS` from ViGEm's `Common.h`.
 *
 * The button word is the little-endian pair at offset 4 of the HID report, so
 * these bits line up with the DualShock 4's own report: bits 0-3 are the d-pad
 * hat, bits 4-7 are the face buttons, the next byte carries the shoulders and
 * the analog-trigger bits, and `bSpecial` is the byte after it.
 */
namespace ds4
{
constexpr uint16_t kSquare = 1u << 4;
constexpr uint16_t kCross = 1u << 5;
constexpr uint16_t kCircle = 1u << 6;
constexpr uint16_t kTriangle = 1u << 7;
constexpr uint16_t kL1 = 1u << 8;      // DS4_BUTTON_SHOULDER_LEFT
constexpr uint16_t kR1 = 1u << 9;      // DS4_BUTTON_SHOULDER_RIGHT
constexpr uint16_t kL2 = 1u << 10;     // DS4_BUTTON_TRIGGER_LEFT, the digital bit
constexpr uint16_t kR2 = 1u << 11;     // DS4_BUTTON_TRIGGER_RIGHT, the digital bit
constexpr uint16_t kShare = 1u << 12;
constexpr uint16_t kOptions = 1u << 13;
constexpr uint16_t kL3 = 1u << 14;     // DS4_BUTTON_THUMB_LEFT
constexpr uint16_t kR3 = 1u << 15;     // DS4_BUTTON_THUMB_RIGHT

/** The PS button, which lives outside the button word. */
constexpr uint8_t kPs = 1u << 0;

/**
 * `DS4_DPAD_DIRECTIONS`: the d-pad is a hat, so one nibble names all eight
 * directions and `8` is the centred ("none") value — not `0`, which is north.
 */
constexpr uint8_t kHatNorth = 0x0;
constexpr uint8_t kHatNorthEast = 0x1;
constexpr uint8_t kHatEast = 0x2;
constexpr uint8_t kHatSouthEast = 0x3;
constexpr uint8_t kHatSouth = 0x4;
constexpr uint8_t kHatSouthWest = 0x5;
constexpr uint8_t kHatWest = 0x6;
constexpr uint8_t kHatNorthWest = 0x7;
constexpr uint8_t kHatCentred = 0x8;

/**
 * Real hardware also sets the digital L2/R2 bits once the analog trigger is
 * pulled, and some games only ever read those bits. Above this value both are
 * reported, so the trigger is seen either way.
 */
constexpr float kDigitalTriggerThreshold = 0.5f;
} // namespace ds4

/** The exports this class uses, in the order the SDK documents them. */
struct Api
{
	using Alloc = void *(*)();
	using Free = void (*)(void *);
	using Connect = uint32_t (*)(void *);
	using Disconnect = void (*)(void *);
	using TargetAlloc = void *(*)();
	using TargetFree = void (*)(void *);
	using TargetAdd = uint32_t (*)(void *, void *);
	using TargetRemove = uint32_t (*)(void *, void *);
	/** `vigem_target_x360_update`, which takes the report by value. */
	using UpdateX360 = uint32_t (*)(void *, void *, XusbReport);
	/** `vigem_target_ds4_update`, the deprecated-but-simple DS4 entry point. */
	using UpdateDs4 = uint32_t (*)(void *, void *, Ds4Report);

	Alloc alloc = nullptr;
	Free freeClient = nullptr;
	Connect connect = nullptr;
	Disconnect disconnect = nullptr;
	/** Whichever of the two allocators the chosen target kind uses. */
	TargetAlloc targetAlloc = nullptr;
	TargetFree targetFree = nullptr;
	TargetAdd targetAdd = nullptr;
	TargetRemove targetRemove = nullptr;
	/** Exactly one of these is resolved, matching the target kind. */
	UpdateX360 updateX360 = nullptr;
	UpdateDs4 updateDs4 = nullptr;
};

template <typename Fn> Fn resolve(HMODULE module, const char *name)
{
	return reinterpret_cast<Fn>(reinterpret_cast<void *>(GetProcAddress(module, name)));
}

int16_t thumbToReport(float value)
{
	const float clamped = std::clamp(value, -1.0f, 1.0f);
	return static_cast<int16_t>(std::lround(clamped * 32767.0f));
}

uint8_t triggerToReport(float value)
{
	const float clamped = std::clamp(value, 0.0f, 1.0f);
	return static_cast<uint8_t>(std::lround(clamped * 255.0f));
}

/**
 * The DS4's stick axes are unsigned bytes centred on 0x80.
 *
 * Both X axes run the same way as XInput — larger means further right — so the
 * signed value maps straight onto the byte.
 */
uint8_t thumbToDs4X(float value)
{
	const float clamped = std::clamp(value, -1.0f, 1.0f);
	return static_cast<uint8_t>(std::lround((clamped + 1.0f) * 127.5f));
}

/**
 * The DS4's Y axes are inverted against XInput.
 *
 * A real pad reports 0 with the stick pushed up and 255 with it pulled down:
 * SDL's PS4 driver decodes the byte with `byte * 257 - 32768` for Y exactly as
 * it does for X, and SDL's own Y axis is negative-up, so the byte must grow
 * downwards. The [Report] handed to this class is in XInput's positive-is-up
 * convention, so it has to be flipped back.
 */
uint8_t thumbToDs4Y(float value)
{
	const float clamped = std::clamp(value, -1.0f, 1.0f);
	return static_cast<uint8_t>(std::lround((1.0f - clamped) * 127.5f));
}

/** Turns the four XUSB d-pad bits into the DS4's single hat value. */
uint8_t toDs4Hat(uint16_t xusb)
{
	const bool up = (xusb & ViGEmGamepad::DPadUp) != 0;
	const bool down = (xusb & ViGEmGamepad::DPadDown) != 0;
	const bool left = (xusb & ViGEmGamepad::DPadLeft) != 0;
	const bool right = (xusb & ViGEmGamepad::DPadRight) != 0;

	// A hat only has eight directions, so a diagonal is one value, not two
	// bits. Opposite pairs cannot be expressed; the first cardinals win.
	if (up && right)
		return ds4::kHatNorthEast;
	if (right && down)
		return ds4::kHatSouthEast;
	if (down && left)
		return ds4::kHatSouthWest;
	if (left && up)
		return ds4::kHatNorthWest;
	if (up)
		return ds4::kHatNorth;
	if (right)
		return ds4::kHatEast;
	if (down)
		return ds4::kHatSouth;
	if (left)
		return ds4::kHatWest;
	return ds4::kHatCentred;
}

/**
 * Translates the XUSB flags the executor fills in into the DS4's button word.
 *
 * The face buttons keep their position rather than their letter, which is what
 * a player expects: the pad's bottom button (Xbox's A) is the DualShock's
 * Cross, and so on around the diamond.
 */
uint16_t toDs4Buttons(uint16_t xusb)
{
	uint16_t buttons = 0;
	auto include = [&buttons](uint16_t flag) { buttons |= flag; };

	if ((xusb & ViGEmGamepad::LeftShoulder) != 0)
		include(ds4::kL1);
	if ((xusb & ViGEmGamepad::RightShoulder) != 0)
		include(ds4::kR1);
	if ((xusb & ViGEmGamepad::LeftThumb) != 0)
		include(ds4::kL3);
	if ((xusb & ViGEmGamepad::RightThumb) != 0)
		include(ds4::kR3);
	if ((xusb & ViGEmGamepad::Start) != 0)
		include(ds4::kOptions);
	if ((xusb & ViGEmGamepad::Back) != 0)
		include(ds4::kShare);

	if ((xusb & ViGEmGamepad::A) != 0)
		include(ds4::kCross);
	if ((xusb & ViGEmGamepad::B) != 0)
		include(ds4::kCircle);
	if ((xusb & ViGEmGamepad::X) != 0)
		include(ds4::kSquare);
	if ((xusb & ViGEmGamepad::Y) != 0)
		include(ds4::kTriangle);

	// The hat is the low nibble and every button bit is above it, so this OR
	// cannot collide.
	buttons |= toDs4Hat(xusb);
	return buttons;
}

/**
 * The buttons that live outside the DS4's button word.
 *
 * Guide is the only Xbox system button the DS4 has a counterpart for. The
 * Nintendo Home/Capture controls arrive here as Menu/View, so they stay on
 * OPTIONS/SHARE rather than being pulled onto the PS button as well.
 *
 * The DS4's touchpad button has no equivalent in the shared button mask, so
 * nothing is reported for it.
 */
uint8_t toDs4Special(uint16_t xusb)
{
	return (xusb & ViGEmGamepad::Guide) != 0 ? ds4::kPs : 0;
}

/**
 * Asking the system what it makes of the pad that was just plugged in.
 *
 * A virtual pad is only useful if the PC's own controller support agrees that it
 * is there, and the two targets register in different places: an Xbox 360 pad
 * answers XInput, while a DualShock 4 is also a HID gamepad that the system's
 * DirectInput controller list shows. Both are queried here through the same API
 * a game would use, each loaded from its own DLL so the build needs no extra
 * import libraries or headers.
 */
namespace systemprobe
{
/** `XINPUT_STATE`: a packet counter followed by the same 12-byte gamepad. */
struct XInputState
{
	uint32_t packetNumber = 0;
	XusbReport gamepad{};
};
static_assert(sizeof(XInputState) == sizeof(XusbReport) + sizeof(uint32_t),
			  "XINPUT_STATE is a packet number followed by a gamepad report");

/** XInput, from the DLL the system ships it in. */
struct XInput
{
	using GetState = uint32_t(WINAPI *)(uint32_t, void *);

	GetState getState = nullptr;

	XInput()
	{
		for (const wchar_t *name : {L"xinput1_4.dll", L"xinput1_3.dll"})
		{
			HMODULE module = LoadLibraryW(name);
			if (module == nullptr)
				continue;
			getState = reinterpret_cast<GetState>(
				reinterpret_cast<void *>(GetProcAddress(module, "XInputGetState")));
			if (getState != nullptr)
				return;
		}
	}
};

/**
 * The XInput slots the system reports a controller in, as "0" or "0, 1".
 *
 * Which slot is which cannot be asked, so this reports what the system says is
 * there rather than guessing which of them is this pad.
 */
QString xinputSlots()
{
	static const XInput xinput;
	if (xinput.getState == nullptr)
		return QString();

	// Not named "slots": Qt defines that as a keyword macro, which would erase
	// the variable declaration.
	QString reported;
	for (uint32_t slot = 0; slot < 4; ++slot)
	{
		XInputState state;
		if (xinput.getState(slot, &state) != 0) // not ERROR_SUCCESS
			continue;
		if (!reported.isEmpty())
			reported += QStringLiteral(", ");
		reported += QString::number(slot);
	}
	return reported;
}

/**
 * The joystick API behind the system's own "Set up USB game controllers" list.
 *
 * `joyGetDevCaps` is what that list is built from, so counting the devices it
 * reports is the answer to whether the pad appears there. `JOYCAPS` is not
 * declared here to keep the headers out of the build; it is a few hundred bytes,
 * and the call never writes more than the size it is given.
 */
struct JoyApi
{
	using GetNumDevs = uint32_t(WINAPI *)();
	using GetDevCaps = uint32_t(WINAPI *)(uintptr_t, void *, uint32_t);

	GetNumDevs getNumDevs = nullptr;
	GetDevCaps getDevCaps = nullptr;

	JoyApi()
	{
		HMODULE module = LoadLibraryW(L"winmm.dll");
		if (module == nullptr)
			return;
		getNumDevs = reinterpret_cast<GetNumDevs>(
			reinterpret_cast<void *>(GetProcAddress(module, "joyGetNumDevs")));
		getDevCaps = reinterpret_cast<GetDevCaps>(
			reinterpret_cast<void *>(GetProcAddress(module, "joyGetDevCapsW")));
	}
};

/** How many game controllers that list shows, or -1 when it cannot be read. */
int controllerListCount()
{
	static const JoyApi joy;
	if (joy.getDevCaps == nullptr)
		return -1;

	// joyGetNumDevs reports how many slots the driver supports, not how many are
	// in use, so every one has to be asked for its capabilities.
	const uint32_t slotCount = joy.getNumDevs != nullptr ? joy.getNumDevs() : 16;
	int count = 0;
	for (uint32_t index = 0; index < slotCount; ++index)
	{
		std::uint8_t caps[2048]{};
		if (joy.getDevCaps(index, caps, sizeof(caps)) == 0) // JOYERR_NOERROR
			++count;
	}
	return count;
}
} // namespace systemprobe
} // namespace

struct ViGEmGamepad::Impl
{
	HMODULE module = nullptr;
	Api api;
	void *client = nullptr;
	void *target = nullptr;
	TargetType type = TargetType::Xbox360;
	bool ready = false;
	QString error;
	bool reportedFailure = false;

	/** Undo whatever half of the set-up succeeded, so nothing is left behind. */
	void teardown()
	{
		if (target != nullptr)
		{
			if (client != nullptr)
				api.targetRemove(client, target);
			api.targetFree(target);
			target = nullptr;
		}
		if (client != nullptr)
		{
			api.disconnect(client);
			api.freeClient(client);
			client = nullptr;
		}
		if (module != nullptr)
		{
			FreeLibrary(module);
			module = nullptr;
		}
		ready = false;
	}
};

ViGEmGamepad::ViGEmGamepad(TargetType type) : m_impl(std::make_unique<Impl>())
{
	Impl &impl = *m_impl;
	impl.type = type;

	// Standard search order, so the DLL is found wherever ViGEmBus' installer or
	// another tool (DS4Windows, x360ce, Steam Input) put it.
	impl.module = LoadLibraryW(L"ViGEmClient.dll");
	if (impl.module == nullptr)
	{
		impl.error = QStringLiteral("the ViGEmBus driver is not installed");
		return;
	}

	impl.api.alloc = resolve<Api::Alloc>(impl.module, "vigem_alloc");
	impl.api.freeClient = resolve<Api::Free>(impl.module, "vigem_free");
	impl.api.connect = resolve<Api::Connect>(impl.module, "vigem_connect");
	impl.api.disconnect = resolve<Api::Disconnect>(impl.module, "vigem_disconnect");
	impl.api.targetFree = resolve<Api::TargetFree>(impl.module, "vigem_target_free");
	impl.api.targetAdd = resolve<Api::TargetAdd>(impl.module, "vigem_target_add");
	impl.api.targetRemove = resolve<Api::TargetRemove>(impl.module, "vigem_target_remove");

	// Only the pair that is actually in use is required. A client too old to
	// carry the DS4 entry points still works for the Xbox 360 pad rather than
	// failing the whole class.
	switch (type)
	{
	case TargetType::DualShock4:
		impl.api.targetAlloc = resolve<Api::TargetAlloc>(impl.module, "vigem_target_ds4_alloc");
		impl.api.updateDs4 = resolve<Api::UpdateDs4>(impl.module, "vigem_target_ds4_update");
		break;
	case TargetType::Xbox360:
	default:
		impl.api.targetAlloc = resolve<Api::TargetAlloc>(impl.module, "vigem_target_x360_alloc");
		impl.api.updateX360 = resolve<Api::UpdateX360>(impl.module, "vigem_target_x360_update");
		break;
	}

	const bool hasUpdate = (type == TargetType::DualShock4) ? (impl.api.updateDs4 != nullptr)
														    : (impl.api.updateX360 != nullptr);

	if (impl.api.alloc == nullptr || impl.api.freeClient == nullptr || impl.api.connect == nullptr ||
		impl.api.disconnect == nullptr || impl.api.targetAlloc == nullptr ||
		impl.api.targetFree == nullptr || impl.api.targetAdd == nullptr ||
		impl.api.targetRemove == nullptr || !hasUpdate)
	{
		impl.error = QStringLiteral("the installed ViGEmClient.dll is not a version this app knows");
		impl.teardown();
		return;
	}

	impl.client = impl.api.alloc();
	if (impl.client == nullptr)
	{
		impl.error = QStringLiteral("the ViGEm client could not be allocated");
		impl.teardown();
		return;
	}

	if (impl.api.connect(impl.client) != kErrorNone)
	{
		impl.error = QStringLiteral("the ViGEmBus driver is installed but not reachable. "
									"Reinstall ViGEmBus, then start the app again.");
		impl.teardown();
		return;
	}

	impl.target = impl.api.targetAlloc();
	if (impl.target == nullptr)
	{
		impl.error = QStringLiteral("the %1 could not be created").arg(nameFor(type));
		impl.teardown();
		return;
	}

	if (impl.api.targetAdd(impl.client, impl.target) != kErrorNone)
	{
		impl.error = QStringLiteral("the ViGEmBus driver refused a new virtual pad. "
									"Close other controller tools and try again.");
		impl.teardown();
		return;
	}

	impl.ready = true;
	qInfo() << nameFor(type) << "plugged in";
}

ViGEmGamepad::~ViGEmGamepad()
{
	if (m_impl != nullptr)
		m_impl->teardown();
}

bool ViGEmGamepad::isReady() const
{
	return m_impl != nullptr && m_impl->ready;
}

QString ViGEmGamepad::lastError() const
{
	return m_impl != nullptr ? m_impl->error : QString();
}

ViGEmGamepad::TargetType ViGEmGamepad::targetType() const
{
	return m_impl != nullptr ? m_impl->type : TargetType::Xbox360;
}

void ViGEmGamepad::submit(const Report &report)
{
	if (!isReady())
		return;

	Impl &impl = *m_impl;
	uint32_t result = kErrorNone;

	if (impl.type == TargetType::DualShock4)
	{
		Ds4Report ds4{};
		ds4.bThumbLX = thumbToDs4X(report.leftX);
		ds4.bThumbLY = thumbToDs4Y(report.leftY);
		ds4.bThumbRX = thumbToDs4X(report.rightX);
		ds4.bThumbRY = thumbToDs4Y(report.rightY);
		ds4.bTriggerL = triggerToReport(report.leftTrigger);
		ds4.bTriggerR = triggerToReport(report.rightTrigger);
		ds4.wButtons = toDs4Buttons(report.buttons);
		// Some games read only the digital trigger bits, so raise them too once
		// the analog value is past halfway.
		if (report.leftTrigger >= ds4::kDigitalTriggerThreshold)
			ds4.wButtons |= ds4::kL2;
		if (report.rightTrigger >= ds4::kDigitalTriggerThreshold)
			ds4.wButtons |= ds4::kR2;
		ds4.bSpecial = toDs4Special(report.buttons);

		result = impl.api.updateDs4(impl.client, impl.target, ds4);
	}
	else
	{
		XusbReport xusb{};
		xusb.wButtons = report.buttons;
		xusb.bLeftTrigger = triggerToReport(report.leftTrigger);
		xusb.bRightTrigger = triggerToReport(report.rightTrigger);
		xusb.sThumbLX = thumbToReport(report.leftX);
		xusb.sThumbLY = thumbToReport(report.leftY);
		xusb.sThumbRX = thumbToReport(report.rightX);
		xusb.sThumbRY = thumbToReport(report.rightY);

		result = impl.api.updateX360(impl.client, impl.target, xusb);
	}

	if (result != kErrorNone && !impl.reportedFailure)
	{
		// Once is enough: this runs for every packet, and the pad is gone.
		impl.reportedFailure = true;
		qWarning() << "ViGEmBus rejected a report; the virtual pad may have been removed";
		impl.ready = false;
	}
}

QString ViGEmGamepad::backendName() const
{
	return nameFor(targetType());
}

QString ViGEmGamepad::systemReport() const
{
	if (!isReady())
		return QString();

	if (m_impl->type == TargetType::DualShock4)
	{
		const int listed = systemprobe::controllerListCount();
		if (listed < 0)
			return QStringLiteral("the system's controller list could not be read");
		return QStringLiteral("the system's controller list shows %1 pad(s)").arg(listed);
	}

	const QString slots = systemprobe::xinputSlots();
	if (slots.isEmpty())
		return QStringLiteral("XInput does not report it, so XInput games will not find this pad");
	return QStringLiteral("XInput reports a controller in slot(s) %1; the system's own controller "
						  "list shows DirectInput pads only, so it will not appear there")
		.arg(slots);
}

QString ViGEmGamepad::nameFor(TargetType type)
{
	switch (type)
	{
	case TargetType::DualShock4:
		return QStringLiteral("Virtual DualShock 4 (ViGEmBus)");
	case TargetType::Xbox360:
	default:
		return QStringLiteral("Virtual Xbox 360 pad (ViGEmBus)");
	}
}
