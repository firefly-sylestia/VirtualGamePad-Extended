#pragma once

#include <QHash>
#include <QWidget>

class QFrame;
class QLabel;
class QListWidget;
class QStackedWidget;
class HomePage;
class ControllerTest;
class LayoutsPage;
class KeymapPage;

/**
 * @brief The application shell: a sidebar plus a stack of pages.
 *
 * The always-available pages (home, control test, layouts, key mapping) are
 * built once. The heavier screens (server, preferences, about) are created on
 * demand and torn down when they navigate back.
 */
class Shell final : public QWidget
{
	Q_OBJECT

  public:
	explicit Shell(QWidget *parent = nullptr);
	~Shell() override;

  private:
	enum Page
	{
		PageHome = 0,
		PageControlTest,
		PageLayouts,
		PageKeymap,
		PageServer,
		PagePreferences,
		PageAbout
	};

	void buildSidebar(QFrame *sidebar);
	void buildPages();
	void navigateTo(int index);

	QListWidget *m_nav = nullptr;
	QStackedWidget *m_pages = nullptr;
	QHash<int, QWidget *> m_created;
	bool m_navigating = false;

	HomePage *m_home = nullptr;
};
