#include "mainwindow.hpp"

#include "../settings/settings_singleton.hpp"
#include "shell.hpp"
#include "ui_mainwindow.h"

#include <QApplication>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent), ui(new Ui::MainWindow)
{
	qDebug() << "Initializing MainWindow";

	SettingsSingleton::instance().loadAll();
	ui->setupUi(this);

	setWindowTitle(tr("Virtual GamePad Extended %1").arg(QApplication::applicationVersion()));
	resize(1180, 820);
	setMinimumSize(960, 660);

	ui->stackedWidget->addWidget(new Shell(ui->stackedWidget));
	ui->stackedWidget->setCurrentIndex(0);

	qDebug() << "MainWindow initialized successfully";
}

MainWindow::~MainWindow()
{
	qDebug() << "Destroying MainWindow";
	delete ui;
}
