#include "controller_test_state.hpp"

ControllerTestState &ControllerTestState::instance()
{
	static ControllerTestState state;
	return state;
}

void ControllerTestState::updateFromReading(const vgp_data_exchange_gamepad_reading &reading)
{
	m_state.buttonsDown = reading.buttons_down;
	m_state.leftTrigger = reading.left_trigger;
	m_state.rightTrigger = reading.right_trigger;
	m_state.leftX = reading.left_thumbstick_x;
	m_state.leftY = reading.left_thumbstick_y;
	m_state.rightX = reading.right_thumbstick_x;
	m_state.rightY = reading.right_thumbstick_y;
	m_state.hasData = true;
	emit updated();
}

void ControllerTestState::setConnected(bool connected)
{
	if (m_state.connected == connected)
	{
		return;
	}
	m_state.connected = connected;
	if (!connected)
	{
		// Release everything so the display does not freeze on the last packet.
		m_state.buttonsDown = 0;
		m_state.leftTrigger = 0.0f;
		m_state.rightTrigger = 0.0f;
		m_state.leftX = 0.0f;
		m_state.leftY = 0.0f;
		m_state.rightX = 0.0f;
		m_state.rightY = 0.0f;
		m_state.hasData = false;
	}
	emit updated();
}
