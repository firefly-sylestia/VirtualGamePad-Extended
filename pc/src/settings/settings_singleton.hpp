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

enum class ExecutorType
{
	GamepadExecutor,
	KeyboardMouseExecutor
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
	DualShock4
};

class SettingsSingleton : public QObject
{
	Q_OBJECT
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
	 * The virtual gamepad, because that is the only backend that presents a
	 * controller the PC's other programs can see. Defaulting to keyboard and
	 * mouse meant a phone that clearly connected registered nothing anywhere
	 * until the user found Preferences and switched backends by hand.
	 *
	 * Keyboard and mouse remain available, but as a deliberate choice: it needs
	 * a key map, and it is invisible to anything that reads a controller.
	 */
	static constexpr ExecutorType DEFAULT_EXECUTOR_TYPE = ExecutorType::GamepadExecutor;

	/**
	 * XInput is what modern games read, so the 360 pad is the default. The
	 * DualShock 4 is opt-in for the older HID/DirectInput-only readers.
	 */
	static constexpr VirtualGamepadType DEFAULT_VIRTUAL_GAMEPAD_TYPE = VirtualGamepadType::Xbox360;

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
