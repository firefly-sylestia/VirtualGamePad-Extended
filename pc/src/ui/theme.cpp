#include "theme.hpp"

#include <QApplication>
#include <QFrame>
#include <QLabel>
#include <QPalette>
#include <QPushButton>
#include <QStyleFactory>
#include <QVBoxLayout>

namespace Theme
{

QString styleSheet()
{
	return QStringLiteral(R"QSS(
/* ------------------------------------------------------------------ base --- */
QWidget {
	background-color: #0f1117;
	color: #e7e9f0;
	font-size: 13px;
}
QToolTip {
	background-color: #1d2130;
	color: #e7e9f0;
	border: 1px solid #2a2f40;
	padding: 6px 8px;
	border-radius: 6px;
}
QMainWindow, QStackedWidget, QScrollArea, QScrollArea > QWidget > QWidget {
	background-color: #0f1117;
}

/* ------------------------------------------------------------- headings --- */
QLabel[role="heading"] {
	font-size: 26px;
	font-weight: 800;
	color: #ffffff;
}
QLabel[role="cardTitle"] {
	font-size: 15px;
	font-weight: 700;
	color: #ffffff;
}
QLabel[role="caption"] {
	color: #98a0b8;
	font-size: 12px;
}
QLabel[role="section"] {
	color: #98a0b8;
	font-size: 11px;
	font-weight: 700;
	letter-spacing: 1px;
}

/* ---------------------------------------------------------------- cards --- */
QFrame[role="card"] {
	background-color: #161923;
	border: 1px solid #2a2f40;
	border-radius: 14px;
}
QFrame[role="card"] QWidget {
	background-color: transparent;
}
QFrame[role="hr"] {
	background-color: #2a2f40;
	max-height: 1px;
	min-height: 1px;
	border: none;
}

/* -------------------------------------------------------------- buttons --- */
QPushButton {
	background-color: #1d2130;
	color: #e7e9f0;
	border: 1px solid #2a2f40;
	border-radius: 10px;
	padding: 9px 16px;
	font-weight: 600;
	min-height: 20px;
}
QPushButton:hover {
	background-color: #252a3b;
	border-color: #3a4056;
}
QPushButton:pressed {
	background-color: #171b28;
}
QPushButton:disabled {
	color: #5c6379;
	background-color: #161923;
	border-color: #232838;
}
QPushButton[role="primary"] {
	background-color: #6c7cff;
	border: 1px solid #6c7cff;
	color: #ffffff;
}
QPushButton[role="primary"]:hover {
	background-color: #7d8bff;
	border-color: #7d8bff;
}
QPushButton[role="primary"]:pressed {
	background-color: #5a69e6;
}
QPushButton[role="ghost"] {
	background-color: transparent;
	border-color: #2a2f40;
}
QPushButton[role="ghost"]:hover {
	background-color: #1d2130;
}
QPushButton[role="danger"] {
	background-color: transparent;
	border-color: #57324a;
	color: #ff8ba0;
}
QPushButton[role="danger"]:hover {
	background-color: #ff5c7a;
	border-color: #ff5c7a;
	color: #ffffff;
}

/* ------------------------------------------------------------- side nav --- */
QListWidget[role="nav"] {
	background-color: #12141c;
	border: none;
	border-right: 1px solid #232838;
	outline: none;
	padding: 10px 8px;
	font-size: 13px;
}
QListWidget[role="nav"]::item {
	color: #98a0b8;
	padding: 11px 14px;
	border-radius: 10px;
	margin: 2px 4px;
}
QListWidget[role="nav"]::item:hover {
	background-color: #1d2130;
	color: #e7e9f0;
}
QListWidget[role="nav"]::item:selected {
	background-color: #232a48;
	color: #ffffff;
	font-weight: 700;
}

/* ------------------------------------------------------------ form bits --- */
QLineEdit, QSpinBox, QDoubleSpinBox, QComboBox {
	background-color: #12141c;
	border: 1px solid #2a2f40;
	border-radius: 8px;
	padding: 7px 10px;
	selection-background-color: #6c7cff;
	selection-color: #ffffff;
	min-height: 18px;
}
QLineEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus, QComboBox:focus {
	border: 1px solid #6c7cff;
}
QLineEdit:read-only {
	color: #98a0b8;
}
QComboBox::drop-down {
	border: none;
	width: 22px;
}
QComboBox QAbstractItemView {
	background-color: #161923;
	border: 1px solid #2a2f40;
	border-radius: 8px;
	selection-background-color: #2b3358;
	outline: none;
	padding: 4px;
}
QSpinBox::up-button, QSpinBox::down-button,
QDoubleSpinBox::up-button, QDoubleSpinBox::down-button {
	background-color: #232838;
	border: none;
	width: 16px;
}

QSlider::groove:horizontal {
	height: 6px;
	background: #232838;
	border-radius: 3px;
}
QSlider::sub-page:horizontal {
	background: #6c7cff;
	border-radius: 3px;
}
QSlider::handle:horizontal {
	background: #ffffff;
	width: 16px;
	margin: -6px 0;
	border-radius: 8px;
}

QCheckBox, QRadioButton {
	spacing: 8px;
}
QCheckBox::indicator, QRadioButton::indicator {
	width: 16px;
	height: 16px;
	border: 1px solid #3a4056;
	background: #12141c;
}
QCheckBox::indicator {
	border-radius: 5px;
}
QRadioButton::indicator {
	border-radius: 9px;
}
QCheckBox::indicator:checked, QRadioButton::indicator:checked {
	background-color: #6c7cff;
	border-color: #6c7cff;
}

QGroupBox {
	background-color: #161923;
	border: 1px solid #2a2f40;
	border-radius: 12px;
	margin-top: 16px;
	padding: 14px 12px 12px 12px;
	font-weight: 700;
}
QGroupBox::title {
	subcontrol-origin: margin;
	left: 14px;
	padding: 0 6px;
	color: #98a0b8;
}

QProgressBar {
	background-color: #12141c;
	border: 1px solid #2a2f40;
	border-radius: 8px;
	text-align: center;
	color: #e7e9f0;
	font-weight: 600;
	min-height: 18px;
}
QProgressBar::chunk {
	background-color: #6c7cff;
	border-radius: 7px;
}

QTabWidget::pane {
	background-color: #161923;
	border: 1px solid #2a2f40;
	border-radius: 12px;
	top: -1px;
}
QTabBar::tab {
	background-color: transparent;
	color: #98a0b8;
	padding: 9px 16px;
	margin-right: 4px;
	border-radius: 9px;
}
QTabBar::tab:selected {
	background-color: #232a48;
	color: #ffffff;
	font-weight: 700;
}
QTabBar::tab:hover {
	color: #e7e9f0;
}

QListWidget, QListView, QTreeView, QTableWidget, QPlainTextEdit, QTextEdit, QTextBrowser {
	background-color: #12141c;
	border: 1px solid #2a2f40;
	border-radius: 12px;
	outline: none;
	padding: 6px;
}
QListWidget::item, QListView::item {
	padding: 9px 10px;
	border-radius: 8px;
	color: #c9cee0;
}
QListWidget::item:hover, QListView::item:hover {
	background-color: #1d2130;
}
QListWidget::item:selected, QListView::item:selected {
	background-color: #232a48;
	color: #ffffff;
}
QHeaderView::section {
	background-color: #1d2130;
	color: #98a0b8;
	border: none;
	border-bottom: 1px solid #2a2f40;
	padding: 7px;
	font-weight: 700;
}

QScrollBar:vertical {
	background: transparent;
	width: 10px;
	margin: 2px;
}
QScrollBar::handle:vertical {
	background: #333a52;
	border-radius: 5px;
	min-height: 30px;
}
QScrollBar::handle:vertical:hover {
	background: #444d6d;
}
QScrollBar:horizontal {
	background: transparent;
	height: 10px;
	margin: 2px;
}
QScrollBar::handle:horizontal {
	background: #333a52;
	border-radius: 5px;
	min-width: 30px;
}
QScrollBar::add-line, QScrollBar::sub-line,
QScrollBar::add-page, QScrollBar::sub-page {
	background: none;
	border: none;
	height: 0;
	width: 0;
}

QMenuBar, QMenu {
	background-color: #161923;
	color: #e7e9f0;
	border: 1px solid #2a2f40;
}
QMenuBar::item {
	background: transparent;
	padding: 6px 10px;
	border-radius: 6px;
}
QMenuBar::item:selected {
	background-color: #232838;
}
QMenu::item {
	padding: 7px 22px;
	border-radius: 6px;
}
QMenu::item:selected {
	background-color: #232a48;
}

QMessageBox {
	background-color: #161923;
}
QStatusBar {
	background-color: #12141c;
	color: #98a0b8;
	border-top: 1px solid #232838;
}
QDialogButtonBox QPushButton {
	min-width: 90px;
}
)QSS");
}

void apply(QApplication &app)
{
	app.setStyle(QStyleFactory::create("Fusion"));

	QPalette pal;
	pal.setColor(QPalette::Window, background());
	pal.setColor(QPalette::WindowText, text());
	pal.setColor(QPalette::Base, surfaceAlt().darker(105));
	pal.setColor(QPalette::AlternateBase, surface());
	pal.setColor(QPalette::ToolTipBase, surfaceAlt());
	pal.setColor(QPalette::ToolTipText, text());
	pal.setColor(QPalette::Text, text());
	pal.setColor(QPalette::Button, surfaceAlt());
	pal.setColor(QPalette::ButtonText, text());
	pal.setColor(QPalette::BrightText, QColor("#ffffff"));
	pal.setColor(QPalette::Highlight, accent());
	pal.setColor(QPalette::HighlightedText, QColor("#ffffff"));
	pal.setColor(QPalette::Link, accentSoft());
	pal.setColor(QPalette::PlaceholderText, muted());
	pal.setColor(QPalette::Disabled, QPalette::Text, QColor("#5c6379"));
	pal.setColor(QPalette::Disabled, QPalette::ButtonText, QColor("#5c6379"));
	pal.setColor(QPalette::Disabled, QPalette::WindowText, QColor("#5c6379"));
	app.setPalette(pal);
	app.setStyleSheet(styleSheet());
}

QLabel *heading(const QString &text, QWidget *parent)
{
	auto *label = new QLabel(text, parent);
	label->setProperty("role", "heading");
	return label;
}

QLabel *cardTitle(const QString &text, QWidget *parent)
{
	auto *label = new QLabel(text, parent);
	label->setProperty("role", "cardTitle");
	return label;
}

QLabel *caption(const QString &text, QWidget *parent)
{
	auto *label = new QLabel(text, parent);
	label->setProperty("role", "caption");
	label->setWordWrap(true);
	return label;
}

QFrame *card(QWidget *parent)
{
	auto *frame = new QFrame(parent);
	frame->setProperty("role", "card");
	frame->setFrameShape(QFrame::NoFrame);
	frame->setAttribute(Qt::WA_StyledBackground, true);
	new QVBoxLayout(frame);
	return frame;
}

QPushButton *primaryButton(const QString &text, QWidget *parent)
{
	auto *button = new QPushButton(text, parent);
	button->setProperty("role", "primary");
	button->setCursor(Qt::PointingHandCursor);
	return button;
}

QPushButton *ghostButton(const QString &text, QWidget *parent)
{
	auto *button = new QPushButton(text, parent);
	button->setProperty("role", "ghost");
	button->setCursor(Qt::PointingHandCursor);
	return button;
}

QLabel *pill(const QString &text, const QColor &colour, QWidget *parent)
{
	auto *label = new QLabel(text, parent);
	label->setAlignment(Qt::AlignCenter);
	label->setStyleSheet(QStringLiteral("QLabel { background-color: %1; color: #0f1117;"
										" border-radius: 9px; padding: 3px 10px;"
										" font-weight: 700; font-size: 11px; }")
							 .arg(colour.name()));
	return label;
}

} // namespace Theme
