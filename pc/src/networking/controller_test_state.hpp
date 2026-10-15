#pragma once

#include "../../VGP_Data_Exchange/C/Colfer.h"

#include <QObject>
#include <cstdint>

/**
 * A flattened, UI-friendly view of the most recent controller reading.
 */
struct ControllerTestSnapshot
{
	uint32_t buttonsDown = 0;
	float leftTrigger = 0.0f;
	float rightTrigger = 0.0f;
	float leftX = 0.0f;
	float leftY = 0.0f;
	float rightX = 0.0f;
	float rightY = 0.0f;
	bool connected = false;
	bool hasData = false;
};

/**
 * @brief Shares the latest controller input with the UI.
 *
 * The TCP server updates this singleton for every parsed packet, and the
 * control test screen listens for {@link updated} to repaint. Everything runs
 * on the main Qt thread, so no locking is required.
 */
class ControllerTestState : public QObject
{
	Q_OBJECT

  public:
	static ControllerTestState &instance();

	/** Copy the latest packet into the snapshot and notify listeners. */
	void updateFromReading(const vgp_data_exchange_gamepad_reading &reading);

	/** Track whether a device is currently connected. */
	void setConnected(bool connected);

	ControllerTestSnapshot snapshot() const { return m_state; }

  signals:
	void updated();

  private:
	ControllerTestState() = default;

	ControllerTestSnapshot m_state;
};
