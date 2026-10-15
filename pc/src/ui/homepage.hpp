#pragma once

#include <QWidget>

class QLabel;

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
	~HomePage() override = default;

	/** Re-read the connection, profile and port shown in the status cards. */
	void refreshStatus();

  signals:
	void startServerRequested();
	void openControlTestRequested();
	void openLayoutsRequested();
	void openKeymapRequested();
	void openPreferencesRequested();
	void openAboutRequested();

  private:
	void refreshSummary();

	QLabel *m_connectionValue = nullptr;
	QLabel *m_connectionHint = nullptr;
	QLabel *m_profileValue = nullptr;
	QLabel *m_executorValue = nullptr;
	QLabel *m_portValue = nullptr;
};
