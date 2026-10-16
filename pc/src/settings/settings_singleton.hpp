#pragma once

#include "../../VGP_Data_Exchange/C/GameButtons.h"
#include "input_types.hpp"
#include "keymap_profile.hpp"

#include <QDir>
#include <QList>
#include <QMap>
#include <QObject>
#include <QSettings>
#include <QString>
#include <QVariant>

/**
 * Which backend turns the phone's input into something on this PC.
 *
 * The values are stored as their number, so new ones are appended rather than
 * inserted: an existing setting has to keep meaning what it meant.
 */
enum class ExecutorType
{
	/** The virtual pad alone: what controller-aware games read. */
	GamepadExecutor,
	/** Keys and pointer alone: what the desktop and older programs read. */
	KeyboardMouseExecutor,
	/**
	 * Both at once, the same reading fed to each.
	 *
	 * Which list the program in front of the user reads is not knowable in
	 * advance — feeding only the pad left the desktop, launchers and non-controller
	 * games dead, and feeding only keys left controller games dead — so the default
	 * covers both and the single modes are there for anyone they double up on.
	 */
	GamepadAndKeyboardMouse
};

/**
 * Which virtual device the gamepad executor plugs into the driver.
 *
 * The two are not interchangeable for a game: an Xbox 360 pad is an XInput
 * device and nothing else, while a DualShock 4 is also a HID gamepad, so games
 * and launchers that only read the HID or DirectInput device list only see the
 * second one. See `vigemClient.hpp` for the full trade-off.
 */
enum class VirtualGamepadType
{
	Xbox360,
	DualShock4,
	/**
	 * Both devices at once, so every kind of program finds one.
	 *
	 * A program's input-device list comes from XInput or from HID/DirectInput,
	 * and which one it uses is invisible to the user: emulators and launchers
	 * list HID pads and show nothing for an XInput-only pad, while XInput-only
	 * games show nothing for a HID one. Presenting both means whichever list is
	 * open has a controller in it, and the user picks between two devices in that
	 * program exactly as they would with two real pads plugged in.
	 */
	Both
};

class SettingsSingleton : public QObject
{
	Q_OBJECT
  signals:
	/**
	 * Emitted when a setting that decides the input backend takes a new value.
	 *
	 * The virtual pad is baked into the backend when it is created, so the page
	 * that owns that backend has to be told: otherwise a device chosen in
	 * Preferences only appears the next time that page is visited, which reads as
	 * "the controller was never detected".
	 */
	void inputBackendChanged();

  public:
	static SettingsSingleton &instance()
	{
		static SettingsSingleton _instance;
		return _instance;
	}

	QSettings *qsettings()
	{
		return &settings;
	}

	int mouseSensitivity() const
	{
		return mouse_sensitivity;
	}
	void setMouseSensitivity(int value);

	quint16 port() const
	{
		return port_number;
	}
	void setPort(quint16 value);

	ExecutorType executorType() const
	{
		return executor_type;
	}
	void setExecutorType(ExecutorType type);

	VirtualGamepadType virtualGamepadType() const
	{
		return virtual_gamepad_type;
	}
	void setVirtualGamepadType(VirtualGamepadType type);

	void loadAll();
	void saveSetting(const QString &key, const QVariant &value);
	QVariant loadSetting(const QString &key);

	void resetToDefaults();

	QString activeProfileName() const;
	void setActiveProfileName(const QString &name);
	KeymapProfile &activeKeymapProfile();

	// Profile management methods
	QString getProfilesDir() const;
	QStringList listAvailableProfiles() const;
	bool createProfile(const QString &profileName);
	bool deleteProfile(const QString &profileName);
	bool profileExists(const QString &profileName) const;
	bool loadProfile(const QString &profileName);
	bool saveActiveProfile();

	static constexpr int DEFAULT_MOUSE_SENSITIVITY = 10;
	static constexpr int MOUSE_SENSITIVITY_MULTIPLIER = 10;
	static constexpr quint16 DEFAULT_PORT_NUMBER = 0;

	/**
	 * Both backends, because neither one alone works everywhere: a virtual pad is
	 * invisible to the desktop and to programs that only read keys, while keys and
	 * the pointer are ignored by anything that reads a controller. Defaulting to
	 * either single one meant a phone that clearly connected did nothing in one
	 * half of the user's programs until they found Preferences and switched by
	 * hand; the single modes remain for programs that react to both at once.
	 */
	static constexpr ExecutorType DEFAULT_EXECUTOR_TYPE = ExecutorType::GamepadAndKeyboardMouse;

	/**
	 * Both devices, because one alone is invisible to half the programs on a PC.
	 * An Xbox 360 pad answers XInput and does not appear in a HID or DirectInput
	 * device list — which is where emulators, launchers and older games look —
	 * while a DualShock 4 answers those and is not an XInput device. The single
	 * choices remain for anyone who wants exactly one of them.
	 */
	static constexpr VirtualGamepadType DEFAULT_VIRTUAL_GAMEPAD_TYPE = VirtualGamepadType::Both;

  private:
	SettingsSingleton();
	~SettingsSingleton() override = default;
	SettingsSingleton(const SettingsSingleton &) = delete;
	SettingsSingleton &operator=(const SettingsSingleton &) = delete;

	QSettings settings;
	int mouse_sensitivity;
	quint16 port_number;
	ExecutorType executor_type;
	VirtualGamepadType virtual_gamepad_type;

	QString m_activeProfileName;
	KeymapProfile m_activeKeymapProfile;

	void loadMouseSensitivity();
	void loadPort();
	void loadExecutorType();
	void loadVirtualGamepadType();
};
