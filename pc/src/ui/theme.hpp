/**
 * @file theme.hpp
 * @brief Modern dark theme for the PC application.
 *
 * A single place that owns the colour palette, the global stylesheet and a few
 * small widget factories so every screen looks consistent.
 */
#pragma once

#include <QColor>
#include <QString>

class QApplication;
class QFrame;
class QLabel;
class QPushButton;
class QVBoxLayout;
class QWidget;

namespace Theme
{
// Palette -------------------------------------------------------------------
inline QColor background()
{
	return QColor("#0f1117");
}
inline QColor surface()
{
	return QColor("#161923");
}
inline QColor surfaceAlt()
{
	return QColor("#1d2130");
}
inline QColor border()
{
	return QColor("#2a2f40");
}
inline QColor text()
{
	return QColor("#e7e9f0");
}
inline QColor muted()
{
	return QColor("#98a0b8");
}
inline QColor accent()
{
	return QColor("#6c7cff");
}
inline QColor accentSoft()
{
	return QColor("#8b5cf6");
}
inline QColor success()
{
	return QColor("#37d67a");
}
inline QColor danger()
{
	return QColor("#ff5c7a");
}

/** The full application stylesheet. */
QString styleSheet();

/** Installs the Fusion style, the dark palette and the stylesheet. */
void apply(QApplication &app);

// Small widget factories ----------------------------------------------------
/** A large page heading. */
QLabel *heading(const QString &text, QWidget *parent = nullptr);

/** A smaller card heading. */
QLabel *cardTitle(const QString &text, QWidget *parent = nullptr);

/** Muted helper text. */
QLabel *caption(const QString &text, QWidget *parent = nullptr);

/** A rounded "card" container with a vertical layout inside it. */
QFrame *card(QWidget *parent = nullptr);

/** A prominent accent button. */
QPushButton *primaryButton(const QString &text, QWidget *parent = nullptr);

/** A quiet, flat button. */
QPushButton *ghostButton(const QString &text, QWidget *parent = nullptr);

/** A small coloured status pill. */
QLabel *pill(const QString &text, const QColor &colour, QWidget *parent = nullptr);
} // namespace Theme
