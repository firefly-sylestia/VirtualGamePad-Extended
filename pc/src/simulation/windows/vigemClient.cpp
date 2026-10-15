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
	using Update = uint32_t (*)(void *, void *, XusbReport);

	Alloc alloc = nullptr;
	Free freeClient = nullptr;
	Connect connect = nullptr;
	Disconnect disconnect = nullptr;
	TargetAlloc targetAlloc = nullptr;
	TargetFree targetFree = nullptr;
	TargetAdd targetAdd = nullptr;
	TargetRemove targetRemove = nullptr;
	Update update = nullptr;
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
} // namespace

struct ViGEmGamepad::Impl
{
	HMODULE module = nullptr;
	Api api;
	void *client = nullptr;
	void *target = nullptr;
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

ViGEmGamepad::ViGEmGamepad() : m_impl(std::make_unique<Impl>())
{
	Impl &impl = *m_impl;

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
	impl.api.targetAlloc = resolve<Api::TargetAlloc>(impl.module, "vigem_target_x360_alloc");
	impl.api.targetFree = resolve<Api::TargetFree>(impl.module, "vigem_target_free");
	impl.api.targetAdd = resolve<Api::TargetAdd>(impl.module, "vigem_target_add");
	impl.api.targetRemove = resolve<Api::TargetRemove>(impl.module, "vigem_target_remove");
	impl.api.update = resolve<Api::Update>(impl.module, "vigem_target_x360_update");

	if (impl.api.alloc == nullptr || impl.api.freeClient == nullptr || impl.api.connect == nullptr ||
		impl.api.disconnect == nullptr || impl.api.targetAlloc == nullptr ||
		impl.api.targetFree == nullptr || impl.api.targetAdd == nullptr ||
		impl.api.targetRemove == nullptr || impl.api.update == nullptr)
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
		impl.error = QStringLiteral("the virtual Xbox 360 pad could not be created");
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
	qInfo() << "ViGEmBus virtual Xbox 360 controller plugged in";
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

void ViGEmGamepad::submit(const Report &report)
{
	if (!isReady())
		return;

	XusbReport xusb{};
	xusb.wButtons = report.buttons;
	xusb.bLeftTrigger = triggerToReport(report.leftTrigger);
	xusb.bRightTrigger = triggerToReport(report.rightTrigger);
	xusb.sThumbLX = thumbToReport(report.leftX);
	xusb.sThumbLY = thumbToReport(report.leftY);
	xusb.sThumbRX = thumbToReport(report.rightX);
	xusb.sThumbRY = thumbToReport(report.rightY);

	const uint32_t result = m_impl->api.update(m_impl->client, m_impl->target, xusb);
	if (result != kErrorNone && !m_impl->reportedFailure)
	{
		// Once is enough: this runs for every packet, and the pad is gone.
		m_impl->reportedFailure = true;
		qWarning() << "ViGEmBus rejected a report; the virtual pad may have been removed";
		m_impl->ready = false;
	}
}

QString ViGEmGamepad::backendName()
{
	return QStringLiteral("Virtual gamepad (ViGEmBus)");
}
