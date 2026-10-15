#include "input_backend_status.hpp"

InputBackendStatus &InputBackendStatus::instance()
{
	static InputBackendStatus status;
	return status;
}

void InputBackendStatus::set(const QString &backendName, bool available, const QString &detail)
{
	if (m_snapshot.backendName == backendName && m_snapshot.available == available &&
		m_snapshot.detail == detail)
	{
		return;
	}

	m_snapshot.backendName = backendName;
	m_snapshot.available = available;
	m_snapshot.detail = detail;
	emit changed();
}
