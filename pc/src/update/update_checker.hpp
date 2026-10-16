#pragma once

#include <QObject>
#include <QString>
#include <QUrl>

#include <memory>

class QNetworkAccessManager;
class QNetworkReply;
class QWidget;

/**
 * @brief Finds a newer release of this application and offers to install it.
 *
 * Releases are published on GitHub with the Windows installer attached, so the
 * newest one is read from the releases API and its installer is offered for
 * download. Nothing is downloaded or started without the user agreeing to it,
 * and the installer is only launched once it is on disk and complete, because
 * installing is what replaces the running application.
 *
 * Failures are reported in words rather than silently: a check that quietly does
 * nothing is indistinguishable from an app that never looks for updates.
 */
class UpdateChecker : public QObject
{
	Q_OBJECT

  public:
	explicit UpdateChecker(QObject *parent = nullptr);
	~UpdateChecker() override;

	UpdateChecker(const UpdateChecker &) = delete;
	UpdateChecker &operator=(const UpdateChecker &) = delete;
	UpdateChecker(UpdateChecker &&) = delete;
	UpdateChecker &operator=(UpdateChecker &&) = delete;

	/**
	 * @brief Ask GitHub for the newest release and act on what comes back.
	 *
	 * @param parent Window the dialogs belong to, or null for the active window.
	 * @param reportWhenNothing Also answer when there is nothing new. A check the
	 *        user asked for has to tell them it found nothing, while the one at
	 *        startup only speaks up when there is an update to install.
	 */
	void check(QWidget *parent, bool reportWhenNothing);

	/** Where this app looks for releases, and where its releases page lives. */
	static const char *repositoryUrl();

  private:
	void handleReleaseReply(QNetworkReply *reply, QWidget *parent, bool reportWhenNothing);
	void startDownload(const QUrl &installer, const QString &fileName, QWidget *parent);
	void launchInstaller(const QString &path, QWidget *parent);

	std::unique_ptr<QNetworkAccessManager> m_network;
};
