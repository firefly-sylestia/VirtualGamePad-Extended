#pragma once

#include "../networking/controller_test_state.hpp"

#include <QVector>
#include <QWidget>
#include <cstdint>

class QGridLayout;
class QLabel;
class QShowEvent;
class GamepadDiagram;
class MeterBar;

/**
 * @brief Live control test screen.
 *
 * A painted controller lights up as the connected device presses controls, and
 * a chip grid lists every button with the key it currently fires. The server
 * feeds {@link ControllerTestState} for each packet, so this screen needs no
 * connection of its own.
 */
class ControllerTest final : public QWidget
{
	Q_OBJECT

  public:
	explicit ControllerTest(QWidget *parent = nullptr);
	~ControllerTest() override;

  signals:
	void navigateBack();

  protected:
	void showEvent(QShowEvent *event) override;

  private slots:
	void refresh();

  private:
	struct Chip
	{
		QLabel *label = nullptr;
		uint32_t mask = 0;
		QString name;
		QString text;
		bool pressed = false;
	};

	void addChip(QGridLayout *grid, const QString &name, uint32_t mask, int row, int col);
	void refreshBindings();
	void refreshChip(Chip &chip, bool pressed);

	QLabel *m_status = nullptr;
	GamepadDiagram *m_diagram = nullptr;
	QVector<Chip> m_chips;
	MeterBar *m_meters[6] = {};
	QLabel *m_meterValues[6] = {};

	// Cached so the per-packet refresh can bail out when nothing changed.
	ControllerTestSnapshot m_last;
	bool m_haveSnapshot = false;
};
