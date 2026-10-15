#pragma once

#include "input_types.hpp"

#include <QFile>
#include <QObject>
#include <QSettings>
#include <QString>
#include <cstdint>
#include <map>
#include <vector>

/**
 * @brief An extra key bound on top of the standard button map.
 *
 * Custom bindings let a single gamepad button fire a key that the standard map
 * does not cover, and a button may own several of them (a small macro).
 */
struct CustomBinding
{
	uint32_t gamepadMask = 0; // Which gamepad button triggers this
	QString name;		  // User-facing label, for example "Open chat"
	InputKeyCode vk = 0;	  // Platform native key code
	QString displayName;	  // Friendly name of the key
};

class KeymapProfile : public QObject
{
	Q_OBJECT

  public:
	KeymapProfile() = default;
	~KeymapProfile() final = default;
	KeymapProfile(const KeymapProfile &) = default;
	KeymapProfile &operator=(const KeymapProfile &) = default;

	bool load(const QString &profilePath) noexcept;
	bool save(const QString &profilePath) const;

	InputKeyCode buttonMap(GamepadButtons btn) const;

	// Methods for display names
	void setButtonInput(GamepadButtons btn, InputKeyCode vk, const QString &displayName);
	QString buttonDisplayName(GamepadButtons btn) const;

	ButtonInput buttonInput(GamepadButtons button) const;

	void setThumbstickInput(Thumbstick thumb, const ThumbstickInput &input);
	ThumbstickInput thumbstickInput(Thumbstick thumb) const;

	void setTriggerInput(Trigger trigger, const TriggerInput &input);
	TriggerInput triggerInput(Trigger trigger) const;

	void setLeftThumbMouseMove(bool enabled);
	bool leftThumbMouseMove() const;
	void setRightThumbMouseMove(bool enabled);
	bool rightThumbMouseMove() const;

	void initializeDefaultMappings();

	// Extra keys layered on top of the standard button map
	void addCustomBinding(const CustomBinding &binding);
	void setCustomBinding(size_t index, const CustomBinding &binding);
	void removeCustomBinding(size_t index);
	void clearCustomBindings();
	const std::vector<CustomBinding> &customBindings() const
	{
		return m_customBindings;
	}

	// For direct access if needed
	std::map<GamepadButtons, InputKeyCode> buttonMappings;
	std::map<GamepadButtons, QString> buttonDisplayNames;
	std::map<Thumbstick, ThumbstickInput> thumbstickMappings;
	std::map<Trigger, TriggerInput> triggerMappings;

  private:
	void loadFromSettings(QSettings const &settings);
	void saveToSettings(QSettings &settings) const;

	std::vector<CustomBinding> m_customBindings;
};
