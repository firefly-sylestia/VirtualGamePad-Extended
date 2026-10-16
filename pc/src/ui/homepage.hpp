#pragma once

#include <QWidget>

#include <memory>

class QLabel;
class QTimer;
class UpdateChecker;

/**
 * @brief The landing page shown when the application starts.
 *
 * Summarises the current server/profile state and offers one-click access to
 * every other screen.
 */
class HomePage final : public QWidget
{
	Q_OBJECT

  public:
	explicit HomePage(QWidget *parent = nullptr);
	/** Defined in the .cpp, where the update checker is a complete type. */
	~HomePage() override;

	/** Re-read the connection, profile and port shown in the status cards. */
	void refreshStatus();

  signals:
	void startServerRequested();
	void openControlTestRequested();
	void openLayoutsRequested();
	void openKeymapRequested();
	void openUsbRequested();
	void openPreferencesRequested();
	void openAboutRequested();

  private:
	void refreshSummary();

	QLabel *m_connectionValue = nullptr;
	QLabel *m_connectionHint = nullptr;
	QLabel *m_profileValue = nullptr;
	QLabel *m_executorValue = nullptr;
	QLabel *m_portValue = nullptr;

	/** Rides the app: the newest release is looked for once, at startup. */
	std::unique_ptr<UpdateChecker> m_updater;
	QTimer *m_updateCheckTimer = nullptr;
};
