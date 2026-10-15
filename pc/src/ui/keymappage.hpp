#pragma once

#include "../settings/keymap_profile.hpp"

#include <QHash>
#include <QVector>
#include <QWidget>

class QComboBox;
class QLabel;
class QLineEdit;
class QListWidget;
class QPushButton;
class QShowEvent;
class ButtonInputBox;

/**
 * @brief Full key-mapping editor.
 *
 * Covers every control the protocol defines (not just the handful the old
 * preferences dialog exposed), lets a user add extra keys on top of the
 * standard map, and chooses how each thumbstick behaves - including using a
 * stick as a mouse wheel to cycle hotbar slots or inventory pages.
 */
class KeymapPage final : public QWidget
{
	Q_OBJECT

  public:
	explicit KeymapPage(QWidget *parent = nullptr);
	~KeymapPage() override;

  signals:
	void navigateBack();
	void profileChanged();

  protected:
	void showEvent(QShowEvent *event) override;

  private slots:
	void changeProfile(int index);
	void saveProfile();
	void addCustomKey();
	void removeCustomKey();
	void resetProfile();

  private:
	struct StickEditor
	{
		QComboBox *mode = nullptr;
		ButtonInputBox *up = nullptr;
		ButtonInputBox *down = nullptr;
		ButtonInputBox *left = nullptr;
		ButtonInputBox *right = nullptr;
	};

	void buildUi();
	QWidget *buildButtonsCard();
	QWidget *buildSticksCard();
	QWidget *buildCustomCard();
	void loadFromProfile();
	void writeToProfile();
	void refreshCustomList();

	QComboBox *m_profile = nullptr;
	QLabel *m_status = nullptr;
	QHash<uint32_t, ButtonInputBox *> m_buttonBoxes;
	StickEditor m_leftStick;
	StickEditor m_rightStick;
	ButtonInputBox *m_leftTrigger = nullptr;
	ButtonInputBox *m_rightTrigger = nullptr;
	QComboBox *m_leftThreshold = nullptr;
	QComboBox *m_rightThreshold = nullptr;

	QListWidget *m_customList = nullptr;
	QComboBox *m_customButton = nullptr;
	QLineEdit *m_customName = nullptr;
	ButtonInputBox *m_customKey = nullptr;
	QVector<CustomBinding> m_customBindings;
};
