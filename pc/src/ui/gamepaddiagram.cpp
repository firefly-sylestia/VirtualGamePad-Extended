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
/**
 * Fixed design canvas; paintEvent scales it to whatever space it is given.
 *
 * Wide and short on purpose: the widget lives in a card that is much wider than
 * it is tall, and the pad is drawn right across it. Every control sits on the
 * body below — nothing floats in the background — and the body spans 93% of the
 * width and 89% of the height, so the drawing fills its card instead of
 * shrinking into the middle of it.
 */
constexpr qreal kCanvasW = 900.0;
constexpr qreal kCanvasH = 330.0;

/** The body: a rounded rectangle with a grip at each end of the bottom edge. */
constexpr qreal kBodyX = 30.0;
constexpr qreal kBodyY = 30.0;
constexpr qreal kBodyW = 840.0;
constexpr qreal kBodyH = 250.0;
constexpr qreal kBodyRadius = 95.0;
const QRectF kLeftGrip(95, 175, 165, 150);
const QRectF kRightGrip(640, 175, 165, 150);

/**
 * Most the canvas may be stretched in one direction to fill a card.
 *
 * A gamepad drawing inside a wide, short card would otherwise be scaled down to
 * fit the height and leave a third of the width empty. A little stretch fills
 * the card; more than this and the round controls stop looking round.
 */
constexpr qreal kMaxStretch = 1.35;

const QColor kOffFill("#252a3d");
const QColor kOffBorder("#3a4056");
const QColor kOffText("#c3c9dd");
const QColor kOnText("#ffffff");
const QColor kCaptionText("#8f97ae");

/**
 * Fills the widget with the canvas, allowing a little stretch so a wide, short
 * card is used right to its edges. Keeping the stretch mild means the pad never
 * looks squashed, and the old behaviour — a small drawing with empty margins
 * either side — is gone.
 */
void applyCanvasTransform(QPainter &p, qreal width, qreal height)
{
	const qreal uniform = std::min(width / kCanvasW, height / kCanvasH);
	const qreal scaleX = std::clamp(width / kCanvasW, uniform / kMaxStretch, uniform * kMaxStretch);
	const qreal scaleY = std::clamp(height / kCanvasH, uniform / kMaxStretch, uniform * kMaxStretch);

	p.translate((width - kCanvasW * scaleX) / 2.0, (height - kCanvasH * scaleY) / 2.0);
	p.scale(scaleX, scaleY);
}
} // namespace

GamepadDiagram::GamepadDiagram(QWidget *parent) : QWidget(parent), m_accent(Theme::accent())
{
	setMinimumSize(420, 200);
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
	return QSize(420, 200);
}

void GamepadDiagram::paintEvent(QPaintEvent *)
{
	QPainter p(this);
	p.setRenderHint(QPainter::Antialiasing, true);
	p.setRenderHint(QPainter::TextAntialiasing, true);

	applyCanvasTransform(p, width(), height());

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

	auto circle = [&](const QPointF &centre, qreal radius, const QString &text, bool on, int fontPx = 12)
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
	body.addRoundedRect(QRectF(kBodyX, kBodyY, kBodyW, kBodyH), kBodyRadius, kBodyRadius);
	body.addEllipse(kLeftGrip);
	body.addEllipse(kRightGrip);
	// The grips overlap the body. simplified() merges the subpaths, but it keeps
	// whatever fill rule it is given, so the union has to be asked for before the
	// merge: with the default odd-even rule the overlaps are punched out and the
	// background shows through the middle of the pad.
	body.setFillRule(Qt::WindingFill);
	body = body.simplified();
	body.setFillRule(Qt::WindingFill);

	QLinearGradient bodyGradient(0, kBodyY, 0, kBodyY + kBodyH);
	bodyGradient.setColorAt(0.0, QColor("#242a3c"));
	bodyGradient.setColorAt(1.0, QColor("#151925"));
	p.setPen(QPen(QColor("#333a52"), 1.6));
	p.setBrush(bodyGradient);
	p.drawPath(body);

	/* ------------------------------------------------- triggers + bumpers --- */
	// The triggers read as sitting behind the pad: two bars above the top edge,
	// with their label and live percentage written inside the bar.
	auto triggerBar = [&](const QRectF &rect, float value, bool on, const QString &text)
	{
		p.setPen(QPen(kOffBorder, 1.2));
		p.setBrush(QColor("#191d2a"));
		p.drawRoundedRect(rect, 13, 13);

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

		const QRectF labelBox = rect.adjusted(10, 0, -10, 0);
		setFont(9, true);
		p.setPen(kOffText);
		p.drawText(labelBox, Qt::AlignLeft | Qt::AlignVCenter, text);
		p.drawText(labelBox,
			   Qt::AlignRight | Qt::AlignVCenter,
			   QStringLiteral("%1%").arg(static_cast<int>(clamped * 100.0f)));
	};

	const QRectF leftTriggerBar(150, 4, 170, 26);
	const QRectF rightTriggerBar(580, 4, 170, 26);
	triggerBar(leftTriggerBar, m_snapshot.leftTrigger, isDown(GamepadButtons_LeftShoulder),
			   labelFor(GamepadButtons_LeftShoulder, QStringLiteral("ZL")));
	triggerBar(rightTriggerBar, m_snapshot.rightTrigger, isDown(GamepadButtons_RightShoulder),
			   labelFor(GamepadButtons_RightShoulder, QStringLiteral("ZR")));

	// Bumpers sit on the pad itself, on the top-left and top-right of the body.
	pill(QRectF(120, 40, 150, 26), labelFor(GamepadButtons_LeftShoulder, QStringLiteral("L")),
		 isDown(GamepadButtons_LeftShoulder), 12, 10);
	pill(QRectF(630, 40, 150, 26), labelFor(GamepadButtons_RightShoulder, QStringLiteral("R")),
		 isDown(GamepadButtons_RightShoulder), 12, 10);

	/* ----------------------------------------------------------- sticks --- */
	// One stick per side, each captioned with the name of its press so nothing
	// has to be drawn floating outside the pad.
	auto stick = [&](const QPointF &centre, qreal radius, const QString &caption, float x, float y,
					 bool on)
	{
		p.setPen(QPen(QColor("#333a52"), 1.4));
		p.setBrush(QColor("#12151f"));
		p.drawEllipse(centre, radius, radius);

		const QPointF knob = centre + QPointF(x * radius * 0.34, y * radius * 0.34);
		p.setPen(QPen(on ? m_accent.lighter(120) : QColor("#3a4056"), 1.6));
		p.setBrush(on ? m_accent : kOffFill);
		p.drawEllipse(knob, radius * 0.6, radius * 0.6);

		setFont(9, true);
		p.setPen(kCaptionText);
		p.drawText(QRectF(centre.x() - radius + 6, centre.y() - radius + 3, 40, 14),
				   Qt::AlignLeft | Qt::AlignVCenter, caption);
	};

	stick(QPointF(215, 135), 54, labelFor(GamepadButtons_LeftThumbstick, QStringLiteral("L3")),
		  m_snapshot.leftX, m_snapshot.leftY, isDown(GamepadButtons_LeftThumbstick));
	stick(QPointF(660, 232), 44, labelFor(GamepadButtons_RightThumbstick, QStringLiteral("R3")),
		  m_snapshot.rightX, m_snapshot.rightY, isDown(GamepadButtons_RightThumbstick));

	/* -------------------------------------------------------------- dpad --- */
	{
		const QPointF c(280, 236);
		const qreal arm = 30, thick = 24;
		QPainterPath cross;
		cross.addRoundedRect(QRectF(c.x() - thick / 2, c.y() - arm - thick / 2, thick, arm * 2 + thick), 7, 7);
		cross.addRoundedRect(QRectF(c.x() - arm - thick / 2, c.y() - thick / 2, arm * 2 + thick, thick), 7, 7);
		p.setPen(QPen(QColor("#333a52"), 1.2));
		p.setBrush(QColor("#1b2030"));
		p.drawPath(cross.simplified());

		auto armRect = [&](uint32_t mask, const QRectF &rect)
		{
			p.setPen(Qt::NoPen);
			p.setBrush(isDown(mask) ? m_accent : kOffFill);
			p.drawRoundedRect(rect, 6, 6);
		};
		armRect(GamepadButtons_DPadUp, QRectF(c.x() - thick / 2, c.y() - arm - thick / 2, thick, arm));
		armRect(GamepadButtons_DPadDown, QRectF(c.x() - thick / 2, c.y() + thick / 2, thick, arm));
		armRect(GamepadButtons_DPadLeft, QRectF(c.x() - arm - thick / 2, c.y() - thick / 2, arm, thick));
		armRect(GamepadButtons_DPadRight, QRectF(c.x() + thick / 2, c.y() - thick / 2, arm, thick));
	}

	/* ------------------------------------------------------ face buttons --- */
	circle(QPointF(668, 156), 20, QStringLiteral("A"), isDown(GamepadButtons_A));
	circle(QPointF(704, 124), 20, QStringLiteral("B"), isDown(GamepadButtons_B));
	circle(QPointF(632, 124), 20, QStringLiteral("X"), isDown(GamepadButtons_X));
	circle(QPointF(668, 90), 20, QStringLiteral("Y"), isDown(GamepadButtons_Y));

	/* --------------------------------------------------------- centre row --- */
	pill(QRectF(400, 70, 44, 26), labelFor(GamepadButtons_View, QStringLiteral("View")),
		 isDown(GamepadButtons_View), 10, 9);
	pill(QRectF(450, 70, 44, 26), labelFor(GamepadButtons_Menu, QStringLiteral("Menu")),
		 isDown(GamepadButtons_Menu), 10, 9);
	pill(QRectF(423, 104, 54, 24), labelFor(GamepadButtons_Home, QStringLiteral("Home")),
		 isDown(GamepadButtons_Home), 10, 9);
	pill(QRectF(412, 138, 34, 24), QStringLiteral("+"), isDown(GamepadButtons_Plus), 10, 12);
	pill(QRectF(454, 138, 34, 24), QStringLiteral("-"), isDown(GamepadButtons_Minus), 10, 12);
	pill(QRectF(411, 168, 78, 22), labelFor(GamepadButtons_Capture, QStringLiteral("Capture")),
		 isDown(GamepadButtons_Capture), 10, 8);

	/* -------------------------------------------------------- side buttons --- */
	// Joy-Con SL/SR live on the rails: vertical pills on the outer edges.
	pill(QRectF(78, 150, 26, 52), labelFor(GamepadButtons_LeftSide, QStringLiteral("SL")),
		 isDown(GamepadButtons_LeftSide), 10, 9);
	pill(QRectF(796, 150, 26, 52), labelFor(GamepadButtons_RightSide, QStringLiteral("SR")),
		 isDown(GamepadButtons_RightSide), 10, 9);

	/* ----------------------------------------------------------- paddles --- */
	// Rear paddles, drawn on the lower body between the grips.
	const uint32_t paddleMasks[4] = {GamepadButtons_Paddle1, GamepadButtons_Paddle2,
									 GamepadButtons_Paddle3, GamepadButtons_Paddle4};
	for (int i = 0; i < 4; ++i)
	{
		const QRectF rect(361 + i * 46, 250, 40, 22);
		pill(rect, labelFor(paddleMasks[i], QStringLiteral("P%1").arg(i + 1)), isDown(paddleMasks[i]),
			 10, 9);
	}
}
