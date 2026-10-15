#include "gamepaddiagram.hpp"

#include "../../VGP_Data_Exchange/C/GameButtons.h"
#include "theme.hpp"

#include <QFont>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <algorithm>
#include <cmath>

namespace
{
/** Fixed design canvas; paintEvent scales it to whatever space it is given. */
constexpr qreal kCanvasW = 660.0;
constexpr qreal kCanvasH = 400.0;

const QColor kOffFill("#252a3d");
const QColor kOffBorder("#3a4056");
const QColor kOffText("#c3c9dd");
const QColor kOnText("#ffffff");
} // namespace

GamepadDiagram::GamepadDiagram(QWidget *parent) : QWidget(parent), m_accent(Theme::accent())
{
	setMinimumSize(420, 255);
	setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
}

void GamepadDiagram::setSnapshot(const ControllerTestSnapshot &snapshot)
{
	m_snapshot = snapshot;
	update();
}

void GamepadDiagram::setBindings(const QHash<uint32_t, QString> &bindings)
{
	m_bindings = bindings;
	update();
}

void GamepadDiagram::setAccent(const QColor &accent)
{
	m_accent = accent;
	update();
}

QSize GamepadDiagram::sizeHint() const
{
	return QSize(static_cast<int>(kCanvasW), static_cast<int>(kCanvasH));
}

QSize GamepadDiagram::minimumSizeHint() const
{
	return QSize(420, 255);
}

void GamepadDiagram::paintEvent(QPaintEvent *)
{
	QPainter p(this);
	p.setRenderHint(QPainter::Antialiasing, true);
	p.setRenderHint(QPainter::TextAntialiasing, true);

	const qreal scale = std::min(width() / kCanvasW, height() / kCanvasH);
	p.translate((width() - kCanvasW * scale) / 2.0, (height() - kCanvasH * scale) / 2.0);
	p.scale(scale, scale);

	auto isDown = [this](uint32_t mask) { return (m_snapshot.buttonsDown & mask) != 0; };
	auto labelFor = [this](uint32_t mask, const QString &fallback)
	{
		const auto it = m_bindings.constFind(mask);
		if (it != m_bindings.constEnd() && !it.value().isEmpty())
			return it.value();
		return fallback;
	};

	auto setFont = [&p](int pixelSize, bool bold)
	{
		QFont f = p.font();
		f.setPixelSize(pixelSize);
		f.setBold(bold);
		p.setFont(f);
	};

	auto pill = [&](const QRectF &rect, const QString &text, bool on, qreal radius, int fontPx = 10)
	{
		p.setPen(QPen(on ? m_accent.lighter(115) : kOffBorder, 1.4));
		p.setBrush(on ? m_accent : kOffFill);
		p.drawRoundedRect(rect, radius, radius);
		setFont(fontPx, true);
		p.setPen(on ? kOnText : kOffText);
		p.drawText(rect, Qt::AlignCenter, text);
	};

	auto circle = [&](const QPointF &centre, qreal radius, const QString &text, bool on, int fontPx = 13)
	{
		p.setPen(QPen(on ? m_accent.lighter(115) : kOffBorder, 1.6));
		p.setBrush(on ? m_accent : kOffFill);
		p.drawEllipse(centre, radius, radius);
		setFont(fontPx, true);
		p.setPen(on ? kOnText : kOffText);
		QRectF box(centre.x() - radius, centre.y() - radius, radius * 2, radius * 2);
		p.drawText(box, Qt::AlignCenter, text);
	};

	/* ------------------------------------------------------------- body --- */
	QPainterPath body;
	body.addRoundedRect(QRectF(110, 66, 440, 270), 130, 130);
	body.addEllipse(QRectF(150, 218, 144, 164));
	body.addEllipse(QRectF(366, 218, 144, 164));
	body = body.simplified();

	QLinearGradient bodyGradient(0, 60, 0, 390);
	bodyGradient.setColorAt(0.0, QColor("#242a3c"));
	bodyGradient.setColorAt(1.0, QColor("#151925"));
	p.setPen(QPen(QColor("#333a52"), 1.6));
	p.setBrush(bodyGradient);
	p.drawPath(body);

	/* ------------------------------------------------- triggers + bumpers --- */
	auto triggerBar = [&](const QRectF &rect, float value, bool on, const QString &text)
	{
		p.setPen(QPen(kOffBorder, 1.2));
		p.setBrush(QColor("#191d2a"));
		p.drawRoundedRect(rect, 14, 14);

		const float clamped = std::clamp(value, 0.0f, 1.0f);
		if (clamped > 0.01f)
		{
			QPainterPath clip;
			clip.addRoundedRect(rect.adjusted(2, 2, -2, -2), 12, 12);
			p.save();
			p.setClipPath(clip);
			p.setPen(Qt::NoPen);
			p.setBrush(on ? m_accent : QColor("#4b5bd6"));
			QRectF fill = rect.adjusted(2, 2, -2, -2);
			fill.setWidth(fill.width() * clamped);
			p.drawRect(fill);
			p.restore();
		}

		setFont(10, true);
		p.setPen(kOffText);
		p.drawText(rect, Qt::AlignCenter, text);

		// Percent readout above the bar.
		setFont(9, false);
		p.setPen(QColor("#8f97ae"));
		p.drawText(QRectF(rect.x(), rect.y() - 17, rect.width(), 15), Qt::AlignCenter,
				   QStringLiteral("%1%").arg(static_cast<int>(clamped * 100.0f)));
	};

	triggerBar(QRectF(150, 30, 130, 28), m_snapshot.leftTrigger, isDown(GamepadButtons_LeftShoulder),
			   labelFor(GamepadButtons_LeftShoulder, QStringLiteral("ZL")));
	triggerBar(QRectF(380, 30, 130, 28), m_snapshot.rightTrigger,
			   isDown(GamepadButtons_RightShoulder),
			   labelFor(GamepadButtons_RightShoulder, QStringLiteral("ZR")));

	pill(QRectF(140, 70, 150, 30), labelFor(GamepadButtons_LeftShoulder, QStringLiteral("LB")),
		 isDown(GamepadButtons_LeftShoulder), 12);
	pill(QRectF(370, 70, 150, 30), labelFor(GamepadButtons_RightShoulder, QStringLiteral("RB")),
		 isDown(GamepadButtons_RightShoulder), 12);

	/* ----------------------------------------------------------- sticks --- */
	auto stick = [&](const QPointF &centre, qreal radius, float x, float y, bool on)
	{
		p.setPen(QPen(QColor("#333a52"), 1.4));
		p.setBrush(QColor("#12151f"));
		p.drawEllipse(centre, radius, radius);

		const QPointF knob = centre + QPointF(x * radius * 0.34, y * radius * 0.34);
		p.setPen(QPen(on ? m_accent.lighter(120) : QColor("#3a4056"), 1.6));
		p.setBrush(on ? m_accent : kOffFill);
		p.drawEllipse(knob, radius * 0.6, radius * 0.6);
	};

	stick(QPointF(222, 168), 50, m_snapshot.leftX, m_snapshot.leftY, isDown(GamepadButtons_LeftThumbstick));
	stick(QPointF(424, 282), 48, m_snapshot.rightX, m_snapshot.rightY, isDown(GamepadButtons_RightThumbstick));

	setFont(10, true);
	p.setPen(QColor("#8f97ae"));
	p.drawText(QRectF(152, 224, 140, 18), Qt::AlignCenter,
			   labelFor(GamepadButtons_LeftThumbstick, QStringLiteral("L3")));
	p.drawText(QRectF(354, 336, 140, 18), Qt::AlignCenter,
			   labelFor(GamepadButtons_RightThumbstick, QStringLiteral("R3")));

	/* -------------------------------------------------------------- dpad --- */
	{
		const QPointF c(250, 282);
		const qreal arm = 36, thick = 26;
		QPainterPath cross;
		cross.addRoundedRect(QRectF(c.x() - thick / 2, c.y() - arm - thick / 2, thick, arm * 2 + thick), 8, 8);
		cross.addRoundedRect(QRectF(c.x() - arm - thick / 2, c.y() - thick / 2, arm * 2 + thick, thick), 8, 8);
		p.setPen(QPen(QColor("#333a52"), 1.2));
		p.setBrush(QColor("#1b2030"));
		p.drawPath(cross.simplified());

		auto armRect = [&](uint32_t mask, const QRectF &rect)
		{
			p.setPen(Qt::NoPen);
			p.setBrush(isDown(mask) ? m_accent : kOffFill);
			p.drawRoundedRect(rect, 7, 7);
		};
		armRect(GamepadButtons_DPadUp, QRectF(c.x() - thick / 2, c.y() - arm - thick / 2, thick, arm));
		armRect(GamepadButtons_DPadDown, QRectF(c.x() - thick / 2, c.y() + thick / 2, thick, arm));
		armRect(GamepadButtons_DPadLeft, QRectF(c.x() - arm - thick / 2, c.y() - thick / 2, arm, thick));
		armRect(GamepadButtons_DPadRight, QRectF(c.x() + thick / 2, c.y() - thick / 2, arm, thick));
	}

	/* ------------------------------------------------------ face buttons --- */
	circle(QPointF(448, 194), 23, QStringLiteral("A"), isDown(GamepadButtons_A));
	circle(QPointF(484, 158), 23, QStringLiteral("B"), isDown(GamepadButtons_B));
	circle(QPointF(412, 158), 23, QStringLiteral("X"), isDown(GamepadButtons_X));
	circle(QPointF(448, 122), 23, QStringLiteral("Y"), isDown(GamepadButtons_Y));

	setFont(9, false);
	p.setPen(QColor("#8f97ae"));
	p.drawText(QRectF(384, 214, 128, 16), Qt::AlignCenter,
			   labelFor(GamepadButtons_A, QStringLiteral("A")) + QStringLiteral(" / ") +
				   labelFor(GamepadButtons_B, QStringLiteral("B")));

	/* --------------------------------------------------------- centre row --- */
	pill(QRectF(276, 116, 48, 26), labelFor(GamepadButtons_View, QStringLiteral("View")),
		 isDown(GamepadButtons_View), 10);
	pill(QRectF(336, 116, 48, 26), labelFor(GamepadButtons_Menu, QStringLiteral("Menu")),
		 isDown(GamepadButtons_Menu), 10);
	pill(QRectF(302, 152, 56, 24), labelFor(GamepadButtons_Home, QStringLiteral("Home")),
		 isDown(GamepadButtons_Home), 10);
	pill(QRectF(292, 184, 36, 24), QStringLiteral("+"), isDown(GamepadButtons_Plus), 10, 13);
	pill(QRectF(332, 184, 36, 24), QStringLiteral("-"), isDown(GamepadButtons_Minus), 10, 13);
	pill(QRectF(288, 216, 84, 22), labelFor(GamepadButtons_Capture, QStringLiteral("Capture")),
		 isDown(GamepadButtons_Capture), 10, 9);

	/* -------------------------------------------------------- side buttons --- */
	pill(QRectF(112, 110, 46, 24), labelFor(GamepadButtons_LeftSide, QStringLiteral("SL")),
		 isDown(GamepadButtons_LeftSide), 10, 9);
	pill(QRectF(502, 110, 46, 24), labelFor(GamepadButtons_RightSide, QStringLiteral("SR")),
		 isDown(GamepadButtons_RightSide), 10, 9);

	/* ----------------------------------------------------------- paddles --- */
	const uint32_t paddleMasks[4] = {GamepadButtons_Paddle1, GamepadButtons_Paddle2,
									 GamepadButtons_Paddle3, GamepadButtons_Paddle4};
	for (int i = 0; i < 4; ++i)
	{
		const QRectF rect(262 + i * 44, 356, 40, 24);
		pill(rect, labelFor(paddleMasks[i], QStringLiteral("P%1").arg(i + 1)), isDown(paddleMasks[i]),
			 10, 9);
	}
}
