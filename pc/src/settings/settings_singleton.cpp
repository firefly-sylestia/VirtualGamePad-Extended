#include "settings_singleton.hpp"

#include "../appdir.hpp"
#include "settings.hpp"

#include <QApplication>
#include <QDebug>

SettingsSingleton::SettingsSingleton()
	: settings(QDir::toNativeSeparators(getConfigDir() + "/VirtualGamePad.ini"), QSettings::IniFormat),
	  executor_type(DEFAULT_EXECUTOR_TYPE),
	  virtual_gamepad_type(DEFAULT_VIRTUAL_GAMEPAD_TYPE)
{
	qInfo() << "Settings file path:" << settings.fileName();

	loadAll();

	QString activeProfile = settings.value("profiles/active", "Default").toString();

	// Initialize active keymap profile based on saved profile name
	m_activeProfileName = activeProfile;
	{
		QString profilePath =
			QDir::toNativeSeparators(getDataDir() + "/profiles/" + m_activeProfileName + ".ini");
		m_activeKeymapProfile.load(profilePath);
	}
}

void SettingsSingleton::setMouseSensitivity(int value)
{
	mouse_sensitivity = value;
	saveSetting(setting_keys::mouse_sensitivity, mouse_sensitivity / MOUSE_SENSITIVITY_MULTIPLIER);
}

void SettingsSingleton::setPort(quint16 value)
{
	port_number = value;
	saveSetting(setting_keys::server_port, port_number);
}

void SettingsSingleton::setExecutorType(ExecutorType type)
{
	// Only a real change is announced, so saving the dialog with the same value
	// does not tear the running backend down and plug it back in.
	const bool changed = executor_type != type;
	executor_type = type;
	saveSetting(setting_keys::executor_type, static_cast<int>(executor_type));
	if (changed)
		emit inputBackendChanged();
}

void SettingsSingleton::setVirtualGamepadType(VirtualGamepadType type)
{
	const bool changed = virtual_gamepad_type != type;
	virtual_gamepad_type = type;
	saveSetting(setting_keys::virtual_gamepad_type, static_cast<int>(virtual_gamepad_type));
	if (changed)
		emit inputBackendChanged();
}

void SettingsSingleton::saveSetting(const QString &key, const QVariant &value)
{
	settings.setValue(key, value);
	settings.sync();
}

QVariant SettingsSingleton::loadSetting(const QString &key)
{
	return settings.value(key);
}

void SettingsSingleton::loadMouseSensitivity()
{
	mouse_sensitivity = MOUSE_SENSITIVITY_MULTIPLIER *
						settings.value(setting_keys::mouse_sensitivity, DEFAULT_MOUSE_SENSITIVITY).toInt();
}

void SettingsSingleton::loadPort()
{
	port_number =
		static_cast<quint16>(settings.value(setting_keys::server_port, DEFAULT_PORT_NUMBER).toUInt());
}

void SettingsSingleton::loadExecutorType()
{
	executor_type = static_cast<ExecutorType>(
		settings.value(setting_keys::executor_type, static_cast<int>(DEFAULT_EXECUTOR_TYPE)).toInt());
}

void SettingsSingleton::loadVirtualGamepadType()
{
	virtual_gamepad_type = static_cast<VirtualGamepadType>(
		settings
			.value(setting_keys::virtual_gamepad_type,
				   static_cast<int>(DEFAULT_VIRTUAL_GAMEPAD_TYPE))
			.toInt());

	// One-time carry-over: a stored choice of a single device comes from the time
	// only one could be plugged in, and a single device is exactly what leaves a
	// controller missing from a program's input-device list — an Xbox 360 pad from
	// a HID list, a DualShock 4 from an XInput one. The marker is written on the
	// first run, so a device chosen in Preferences afterwards is kept as it is.
	if (settings.value(setting_keys::virtual_gamepad_type_both).toBool())
		return;

	settings.setValue(setting_keys::virtual_gamepad_type_both, true);
	settings.sync();

	if (virtual_gamepad_type == VirtualGamepadType::Both)
		return;

	qInfo() << "Stored virtual device" << static_cast<int>(virtual_gamepad_type)
			<< "is upgraded once to both pads, so every program's device list finds a "
			   "controller; choose a device in Preferences to change it back";
	virtual_gamepad_type = VirtualGamepadType::Both;
	saveSetting(setting_keys::virtual_gamepad_type, static_cast<int>(virtual_gamepad_type));
}

void SettingsSingleton::loadAll()
{
	try
	{
		loadMouseSensitivity();
		loadPort();
		loadExecutorType();
		loadVirtualGamepadType();
	}
	catch (const std::exception &e)
	{
		qDebug() << "Error loading settings:" << e.what();
		qInfo() << "Loading default settings";
		settings.clear();
		settings.sync();
	}
	catch (...)
	{
		qDebug() << "Unknown error loading settings";
	}
}

/**
 * Active keymap profile accessors
 */
QString SettingsSingleton::activeProfileName() const
{
	return m_activeProfileName;
}

void SettingsSingleton::setActiveProfileName(const QString &name)
{
	m_activeProfileName = name;
	settings.setValue("profiles/active", m_activeProfileName);
	settings.sync();

	// Don't reload the profile here - it causes double loading issues
	// The caller should already have loaded the profile
}

KeymapProfile &SettingsSingleton::activeKeymapProfile()
{
	return m_activeKeymapProfile;
}

/**
 * Profile management methods
 */
QString SettingsSingleton::getProfilesDir() const
{
	return getDataDir() + "/profiles";
}

QStringList SettingsSingleton::listAvailableProfiles() const
{
	QStringList profiles;
	QDir dir(getProfilesDir());
	if (!dir.exists())
	{
		dir.mkpath(".");
		return QStringList{"Default"};
	}

	QStringList nameFilters;
	nameFilters << "*.ini";
	QFileInfoList fileList = dir.entryInfoList(nameFilters, QDir::Files, QDir::Name);

	foreach (const QFileInfo &fileInfo, fileList)
	{
		profiles.append(fileInfo.baseName());
	}

	// If no profiles exist, add a default one
	if (profiles.isEmpty())
	{
		profiles.append("Default");
	}

	return profiles;
}

bool SettingsSingleton::createProfile(const QString &profileName)
{
	if (profileName.isEmpty())
		return false;

	// Check if profile already exists
	if (profileExists(profileName))
		return false;

	// Create directory if needed
	QDir dir(getProfilesDir());
	if (!dir.exists())
		dir.mkpath(".");

	// Save current mappings to this profile
	QString profilePath = getProfilesDir() + "/" + profileName + ".ini";
	bool success = m_activeKeymapProfile.save(profilePath);

	if (success)
		setActiveProfileName(profileName);

	return success;
}

bool SettingsSingleton::deleteProfile(const QString &profileName)
{
	if (profileName.isEmpty())
		return false;

	// Don't delete active profile
	if (profileName == m_activeProfileName)
		return false;

	QString profilePath = getProfilesDir() + "/" + profileName + ".ini";
	QFile file(profilePath);
	return file.remove();
}

bool SettingsSingleton::profileExists(const QString &profileName) const
{
	if (profileName.isEmpty())
		return false;

	QDir dir(getProfilesDir());
	return QFile::exists(dir.filePath(profileName + ".ini"));
}

bool SettingsSingleton::loadProfile(const QString &profileName)
{
	if (profileName.isEmpty())
		return false;

	// Create standard profile path
	QDir dir(getProfilesDir());
	if (!dir.exists())
	{
		dir.mkpath(".");
	}

	QString profilePath = getProfilesDir() + "/" + profileName + ".ini";

	if (!QFile::exists(profilePath))
	{
		qInfo() << "Creating new profile at:" << profilePath;

		// Use default profile as template, or create new if none exists
		if (m_activeKeymapProfile.buttonMappings.empty())
		{
			m_activeKeymapProfile.initializeDefaultMappings();
		}

		if (!m_activeKeymapProfile.save(profilePath))
		{
			qWarning() << "Failed to save new profile at:" << profilePath;
			return false;
		}

		qInfo() << "New profile created successfully at:" << profilePath;
	}

	// Load the profile
	bool success = m_activeKeymapProfile.load(profilePath);
	if (success)
	{
		// Don't reload the profile when setting name - this would cause a double load
		m_activeProfileName = profileName;
		settings.setValue("profiles/active", m_activeProfileName);
		settings.sync();

		qInfo() << "Successfully loaded profile:" << profileName << "from" << profilePath;
	}
	else
	{
		qWarning() << "Failed to load profile:" << profileName << "from" << profilePath;
	}

	return success;
}

bool SettingsSingleton::saveActiveProfile()
{
	if (m_activeProfileName.isEmpty())
		return false;

	// Ensure the profiles directory exists
	QDir dir(getProfilesDir());
	if (!dir.exists())
	{
		dir.mkpath(".");
	}

	QString profilePath = getProfilesDir() + "/" + m_activeProfileName + ".ini";
	bool success = m_activeKeymapProfile.save(profilePath);

	if (success)
	{
		qDebug() << "Successfully saved profile:" << m_activeProfileName << "to" << profilePath;
	}
	else
	{
		qWarning() << "Failed to save profile:" << m_activeProfileName << "to" << profilePath;
	}

	return success;
}

void SettingsSingleton::resetToDefaults()
{
	// Reset mouse sensitivity
	setMouseSensitivity(DEFAULT_MOUSE_SENSITIVITY * MOUSE_SENSITIVITY_MULTIPLIER);

	// Reset port number
	setPort(DEFAULT_PORT_NUMBER);

	// Reset executor type
	setExecutorType(DEFAULT_EXECUTOR_TYPE);

	// Reset which virtual device the gamepad backend plugs in
	setVirtualGamepadType(DEFAULT_VIRTUAL_GAMEPAD_TYPE);

	// Reset keymapping in active profile to defaults
	m_activeKeymapProfile.initializeDefaultMappings();

	// Do not save anything till the user confirms in the UI
	qInfo() << "Settings reset to defaults in UI.";
}
