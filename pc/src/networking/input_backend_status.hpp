#pragma once

#include <QObject>
#include <QString>

/**
 * @brief What the PC is using to deliver input to games, and whether it works.
 *
 * The keyboard/mouse backend works everywhere. The virtual gamepad backend
 * needs the operating system to allow it: `/dev/uinput` access on Linux, and the
 * Windows input-injection API (which many desktop games ignore, and which needs
 * elevation to create). When it cannot be created the user used to see nothing at
 * all, which reads as "the controller is not detected in games". The active
 * executor reports here instead, and the UI shows it.
 */
class InputBackendStatus : public QObject
{
	Q_OBJECT

  public:
	struct Snapshot
	{
		QString backendName;
		bool available = true;
		QString detail;
	};

	static InputBackendStatus &instance();

	/** Called by each executor when it becomes the active backend. */
	void set(const QString &backendName, bool available, const QString &detail);

	/**
	 * Forget the current backend, for when its executor is gone.
	 *
	 * A backend exists only while the object that created it does, so the UI must
	 * stop calling a torn-down one "active": that read as "the pad is ready"
	 * while nothing was plugged in at all.
	 */
	void clear();

	Snapshot snapshot() const { return m_snapshot; }

  signals:
	void changed();

  private:
	InputBackendStatus() = default;

	Snapshot m_snapshot;
};
