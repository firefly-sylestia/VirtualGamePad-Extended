#include "update_checker.hpp"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMessageBox>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QPointer>
#include <QProcess>
#include <QStandardPaths>

#include <algorithm>
#include <vector>

namespace
{
/** The repository releases are published in, as `owner/name`. */
constexpr const char *kRepositorySlug = "firefly-sylestia/VirtualGamePad-Extended";

/**
 * Numeric comparison of release versions such as `0.4.10`.
 *
 * A leading `v` and anything after a `-` are ignored, because a tag is written
 * as `v1.2.3` while the version baked into the build is `1.2.3`, and a
 * pre-release suffix must not make a version look newer than it is.
 *
 * @return negative when @p a is older, 0 when they are the same, positive when
 *         @p a is newer.
 */
int compareVersions(const QString &a, const QString &b)
{
	auto parts = [](const QString &version)
	{
		QString cleaned = version.trimmed();
		if (cleaned.startsWith(QLatin1Char('v')) || cleaned.startsWith(QLatin1Char('V')))
			cleaned.remove(0, 1);
		const int dash = cleaned.indexOf(QLatin1Char('-'));
		if (dash >= 0)
			cleaned.truncate(dash);

		std::vector<int> numbers;
		for (const QString &part : cleaned.split(QLatin1Char('.')))
		{
			bool ok = false;
			const int number = part.toInt(&ok);
			numbers.push_back(ok ? number : 0);
		}
		return numbers;
	};

	const std::vector<int> left = parts(a);
	const std::vector<int> right = parts(b);
	const size_t count = std::max(left.size(), right.size());
	for (size_t index = 0; index < count; ++index)
	{
		const int l = index < left.size() ? left[index] : 0;
		const int r = index < right.size() ? right[index] : 0;
		if (l != r)
			return l < r ? -1 : 1;
	}
	return 0;
}

/** The releases page, opened when there is nothing this app can install itself. */
QString releasesPageUrl()
{
	return QStringLiteral("https://github.com/%1/releases").arg(QString::fromUtf8(kRepositorySlug));
}
} // namespace

UpdateChecker::UpdateChecker(QObject *parent) : QObject(parent), m_network(new QNetworkAccessManager)
{
	m_network->setParent(this);
}

UpdateChecker::~UpdateChecker() = default;

const char *UpdateChecker::repositoryUrl()
{
	return kRepositorySlug;
}

void UpdateChecker::check(QWidget *parent, bool reportWhenNothing)
{
	QNetworkRequest request{
		QUrl(QStringLiteral("https://api.github.com/repos/%1/releases/latest")
				 .arg(QString::fromUtf8(kRepositorySlug)))};
	// The API answers JSON only, and refuses a call with no user agent.
	request.setRawHeader("Accept", "application/vnd.github+json");
	request.setRawHeader("X-GitHub-Api-Version", "2022-11-28");
	request.setRawHeader("User-Agent", QByteArrayLiteral("VirtualGamePad-Extended/" APP_VERSION));
	request.setAttribute(QNetworkRequest::RedirectPolicyAttribute,
						 QNetworkRequest::NoLessSafeRedirectPolicy);

	QNetworkReply *reply = m_network->get(request);
	connect(reply, &QNetworkReply::finished, this,
			[this, reply, window = QPointer<QWidget>(parent), reportWhenNothing]()
			{
				handleReleaseReply(reply, window, reportWhenNothing);
			});
}

void UpdateChecker::handleReleaseReply(QNetworkReply *reply, QWidget *parent, bool reportWhenNothing)
{
	const QByteArray body = reply->readAll();
	const QNetworkReply::NetworkError error = reply->error();
	const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
	reply->deleteLater();

	const QString current = QCoreApplication::applicationVersion();

	if (error != QNetworkReply::NoError)
	{
		// A repository with no release yet answers 404, which is not a fault the
		// user can act on, so it is told apart from a real network failure.
		// A check the user asked for has to answer even when it found nothing or
		// could not reach the network; the one at startup stays quiet, because an
		// offline machine is not something to interrupt the user about.
		if (reportWhenNothing)
		{
			const QString reason = status == 404
									   ? tr("No release has been published yet.")
									   : tr("The update check failed: %1").arg(reply->errorString());
			QMessageBox::warning(parent, tr("Check for updates"), reason);
		}
		return;
	}

	const QJsonObject release = QJsonDocument::fromJson(body).object();
	const QString tag = release.value(QStringLiteral("tag_name")).toString();
	if (tag.isEmpty())
	{
		if (reportWhenNothing)
			QMessageBox::information(
				parent,
				tr("Check for updates"),
				tr("The newest release could not be read. See %1.").arg(releasesPageUrl()));
		return;
	}

	if (compareVersions(tag, current) <= 0)
	{
		if (reportWhenNothing)
			QMessageBox::information(parent,
									 tr("Check for updates"),
									 tr("This is the newest version (%1).").arg(current));
		return;
	}

	QString installerUrl;
	QString installerName;
	for (const QJsonValue &value : release.value(QStringLiteral("assets")).toArray())
	{
		const QJsonObject asset = value.toObject();
		const QString name = asset.value(QStringLiteral("name")).toString();
		if (!name.endsWith(QStringLiteral(".exe"), Qt::CaseInsensitive))
			continue;
		installerUrl = asset.value(QStringLiteral("browser_download_url")).toString();
		installerName = name;
		break;
	}

	if (installerUrl.isEmpty())
	{
		if (reportWhenNothing)
			QMessageBox::information(
				parent,
				tr("Check for updates"),
				tr("Version %1 is published, but without an installer this app can run. "
				   "Get it from %2.")
					.arg(tag, releasesPageUrl()));
		return;
	}

	const QMessageBox::StandardButton answer = QMessageBox::question(
		parent,
		tr("Check for updates"),
		tr("Version %1 is available; this is %2.\n\nDownload and install it now? "
		   "The app closes and the installer takes over once the download is complete.")
			.arg(tag, current),
		QMessageBox::Yes | QMessageBox::No,
		QMessageBox::Yes);
	if (answer != QMessageBox::Yes)
		return;

	startDownload(QUrl(installerUrl), installerName, parent);
}

void UpdateChecker::startDownload(const QUrl &installer, const QString &fileName, QWidget *parent)
{
	const QString directory = QStandardPaths::writableLocation(QStandardPaths::TempLocation);
	const QString safeName = fileName.isEmpty() ? QStringLiteral("VirtualGamePad-Extended-Setup.exe")
												: QFileInfo(fileName).fileName();
	const QString path = QDir(directory).filePath(safeName);

	auto *file = new QFile(path, this);
	if (!file->open(QIODevice::WriteOnly | QIODevice::Truncate))
	{
		QMessageBox::warning(parent,
							 tr("Check for updates"),
							 tr("The installer could not be saved to %1.").arg(QDir::toNativeSeparators(path)));
		file->deleteLater();
		return;
	}

	QNetworkRequest request{installer};
	request.setAttribute(QNetworkRequest::RedirectPolicyAttribute,
						 QNetworkRequest::NoLessSafeRedirectPolicy);
	request.setRawHeader("User-Agent", QByteArrayLiteral("VirtualGamePad-Extended/" APP_VERSION));

	QNetworkReply *reply = m_network->get(request);
	connect(reply, &QNetworkReply::readyRead, this, [reply, file]() { file->write(reply->readAll()); });
	connect(reply,
			&QNetworkReply::finished,
			this,
			[this, reply, file, path, window = QPointer<QWidget>(parent)]()
			{
				file->write(reply->readAll());
				file->close();
				const bool ok = reply->error() == QNetworkReply::NoError;
				const QString reason = reply->errorString();
				reply->deleteLater();
				file->deleteLater();

				if (!ok)
				{
					QMessageBox::warning(window,
										 tr("Check for updates"),
										 tr("The installer could not be downloaded: %1").arg(reason));
					return;
				}

				launchInstaller(path, window);
			});
}

void UpdateChecker::launchInstaller(const QString &path, QWidget *parent)
{
	// The installer replaces the files this process is running from, so the app
	// stops as soon as the setup wizard is up; leaving it running would lock the
	// executable the installer has to overwrite.
	if (!QProcess::startDetached(path, QStringList{}))
	{
		QMessageBox::warning(parent,
							 tr("Check for updates"),
							 tr("The installer was saved to %1 but could not be started. "
								"Run it by hand to update.")
								 .arg(QDir::toNativeSeparators(path)));
		return;
	}

	QCoreApplication::quit();
}
