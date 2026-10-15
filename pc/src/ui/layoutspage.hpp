#pragma once

#include <QWidget>

class QLabel;
class QListWidget;
class QPushButton;
class QStackedWidget;
class GamepadDiagram;

/**
 * @brief Browse and apply ready-made controller layouts.
 *
 * The left column lists every layout. The right column previews it: a painted
 * controller for the generic layouts, and a bespoke Minecraft-styled controller
 * (with a live hotbar) for the Minecraft layouts.
 */
class LayoutsPage final : public QWidget
{
	Q_OBJECT

  public:
	explicit LayoutsPage(QWidget *parent = nullptr);
	~LayoutsPage() override;

  signals:
	void navigateBack();
	void layoutApplied(const QString &layoutName);

  private slots:
	void selectLayout(int index);
	void applySelected();

  private:
	void showPreview(bool minecraft);

	QListWidget *m_list = nullptr;
	QLabel *m_name = nullptr;
	QLabel *m_tagline = nullptr;
	QLabel *m_description = nullptr;
	QLabel *m_bindings = nullptr;
	QLabel *m_status = nullptr;
	QPushButton *m_apply = nullptr;
	GamepadDiagram *m_diagram = nullptr;
	QWidget *m_specialPanel = nullptr;
	QStackedWidget *m_preview = nullptr;
};
