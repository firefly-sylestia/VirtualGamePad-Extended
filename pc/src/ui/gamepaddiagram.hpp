/**
 * @file gamepaddiagram.hpp
 * @brief A software-painted controller that lights up as buttons are pressed.
 *
 * The same widget drives the control test screen (live pressed state) and the
 * layouts screen (showing what each control is bound to), which keeps the two
 * views visually consistent.
 */
#pragma once

#include "../networking/controller_test_state.hpp"

#include <QColor>
#include <QHash>
#include <QWidget>

class GamepadDiagram final : public QWidget
{
	Q_OBJECT

  public:
	explicit GamepadDiagram(QWidget *parent = nullptr);
	~GamepadDiagram() override = default;

	/** Update the pressed state that is drawn. */
	void setSnapshot(const ControllerTestSnapshot &snapshot);

	/** Map a gamepad button mask to the label drawn on that control. */
	void setBindings(const QHash<uint32_t, QString> &bindings);

	/** Tint the highlighted controls with this colour. */
	void setAccent(const QColor &accent);

	QSize sizeHint() const override;
	QSize minimumSizeHint() const override;

  protected:
	void paintEvent(QPaintEvent *event) override;

  private:
	ControllerTestSnapshot m_snapshot;
	QHash<uint32_t, QString> m_bindings;
	QColor m_accent;
};
