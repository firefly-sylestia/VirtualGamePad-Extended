#include "about.hpp"

#include "badge.hpp"
#include "ui_about.h"

About::About(QStackedWidget *parent) : QWidget(parent), ui(new Ui::About)
{
	qDebug() << "Initializing About dialog";

	ui->setupUi(this);
	ui->versionLabel->setText(tr("Version: %1").arg(QApplication::applicationVersion()));

	// Setup text description
	QString aboutText = tr("<p>This is a part of the <a "
						   "href='https://github.com/firefly-sylestia/VirtualGamePad-Extended'>Virtual Gamepad "
						   "project</a>.</p>"
						   "<p>The source code is available on <a "
						   "href='https://github.com/firefly-sylestia/VirtualGamePad-Extended'>GitHub</a>.</p>"
						   "Report bugs or request features at <a "
						   "href='https://github.com/firefly-sylestia/VirtualGamePad-Extended/issues/new/"
						   "choose'>GitHub Issues</a>.</p>");

	ui->descriptionBox->setHtml(aboutText);
	ui->descriptionBox->setOpenExternalLinks(true);
	ui->descriptionBox->setReadOnly(true);

	// GitHub URLs
	const QString repoUrl = "https://github.com/firefly-sylestia/VirtualGamePad-Extended";
	const QString serverLicenseUrl = "https://github.com/firefly-sylestia/VirtualGamePad-Extended/blob/main/pc/LICENCE.TXT";
	const QString releaseUrl = "https://github.com/firefly-sylestia/VirtualGamePad-Extended/releases/latest";

	qDebug() << "Loading GitHub badges for project information";

	// Load badge images directly through the Badge widgets in the UI
	ui->licenseBadge->loadBadge("https://raster.shields.io/github/license/firefly-sylestia/VirtualGamePad-Extended",
								serverLicenseUrl);
	ui->starsBadge->loadBadge("https://raster.shields.io/github/stars/firefly-sylestia/VirtualGamePad-Extended?style=social",
							  repoUrl);
	ui->downloadsBadge->loadBadge(
		"https://raster.shields.io/github/downloads/firefly-sylestia/VirtualGamePad-Extended/total",
		releaseUrl);
	ui->releaseBadge->loadBadge(
		"https://raster.shields.io/github/v/release/firefly-sylestia/VirtualGamePad-Extended?logo=github",
		releaseUrl);

	qDebug() << "About dialog initialized successfully";
}

About::~About()
{
	delete ui;
}

void About::on_backButton_clicked()
{
	qDebug() << "Closing About dialog";
	emit navigateBack();
	this->deleteLater();
}
