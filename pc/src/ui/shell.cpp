#include "shell.hpp"

#include "../networking/server.hpp"
#include "about.hpp"
#include "controllertest.hpp"
#include "homepage.hpp"
#include "keymappage.hpp"
#include "layoutspage.hpp"
#include "preferences.hpp"
#include "theme.hpp"

#include <QApplication>
#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QListWidget>
#include <QStackedWidget>
#include <QVBoxLayout>

namespace
{
/** Sidebar entry labels, in the same order as Shell::Page. */
const char *const kNavLabels[] = {
	"\u2302   Home",	 "\u25CE   Control test", "\u25A6   Layouts",		"\u2328   Key mapping",
	"\u25B6   Server",	 "\u2699   Preferences",  "\u24D8   About",
};
} // namespace

Shell::Shell(QWidget *parent) : QWidget(parent)
{
	auto *root = new QHBoxLayout(this);
	root->setContentsMargins(0, 0, 0, 0);
	root->setSpacing(0);

	auto *sidebar = new QFrame(this);
	sidebar->setFixedWidth(232);
	sidebar->setStyleSheet(QStringLiteral("QFrame { background-color: #12141c;"
										  " border-right: 1px solid #232838; }"));
	root->addWidget(sidebar);

	m_pages = new QStackedWidget(this);
	root->addWidget(m_pages, 1);

	buildSidebar(sidebar);
	buildPages();
	navigateTo(PageHome);
}

Shell::~Shell() = default;

void Shell::buildSidebar(QFrame *sidebar)
{
	auto *layout = new QVBoxLayout(sidebar);
	layout->setContentsMargins(0, 0, 0, 0);
	layout->setSpacing(0);

	auto *brandLayout = new QVBoxLayout();
	brandLayout->setContentsMargins(20, 22, 20, 14);
	brandLayout->setSpacing(2);

	auto *title = new QLabel(tr("Virtual GamePad"), sidebar);
	title->setStyleSheet(QStringLiteral("font-size: 17px; font-weight: 800; color: #ffffff;"
										" background: transparent;"));
	brandLayout->addWidget(title);

	auto *subtitle = new QLabel(tr("Extended"), sidebar);
	subtitle->setStyleSheet(QStringLiteral("font-size: 11px; font-weight: 700; color: #6c7cff;"
										   " letter-spacing: 2px; background: transparent;"));
	brandLayout->addWidget(subtitle);
	layout->addLayout(brandLayout);

	m_nav = new QListWidget(sidebar);
	m_nav->setProperty("role", "nav");
	m_nav->setFrameShape(QFrame::NoFrame);
	m_nav->setVerticalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
	m_nav->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
	m_nav->setSelectionMode(QAbstractItemView::SingleSelection);
	m_nav->setFocusPolicy(Qt::NoFocus);
	for (const char *label : kNavLabels)
		m_nav->addItem(QString::fromUtf8(label));
	layout->addWidget(m_nav, 1);

	auto *footer = new QLabel(tr("Version %1").arg(QApplication::applicationVersion()), sidebar);
	footer->setStyleSheet(QStringLiteral("color: #5c6379; font-size: 11px; padding: 12px 20px;"
										 " background: transparent;"));
	layout->addWidget(footer);

	connect(m_nav,
			&QListWidget::currentRowChanged,
			this,
			[this](int row)
			{
				if (row >= 0)
					navigateTo(row);
			});
}

void Shell::buildPages()
{
	m_home = new HomePage(m_pages);
	m_created.insert(PageHome, m_home);

	auto *test = new ControllerTest(m_pages);
	m_created.insert(PageControlTest, test);

	auto *layouts = new LayoutsPage(m_pages);
	m_created.insert(PageLayouts, layouts);

	auto *keymap = new KeymapPage(m_pages);
	m_created.insert(PageKeymap, keymap);

	for (auto *page : m_created)
		m_pages->addWidget(page);

	// ---------------------------------------------------------- wiring up --
	connect(m_home, &HomePage::startServerRequested, this, [this] { navigateTo(PageServer); });
	connect(m_home, &HomePage::openControlTestRequested, this,
			[this] { navigateTo(PageControlTest); });
	connect(m_home, &HomePage::openLayoutsRequested, this, [this] { navigateTo(PageLayouts); });
	connect(m_home, &HomePage::openKeymapRequested, this, [this] { navigateTo(PageKeymap); });
	connect(m_home, &HomePage::openPreferencesRequested, this,
			[this] { navigateTo(PagePreferences); });
	connect(m_home, &HomePage::openAboutRequested, this, [this] { navigateTo(PageAbout); });

	connect(test, &ControllerTest::navigateBack, this, [this] { navigateTo(PageHome); });
	connect(layouts, &LayoutsPage::navigateBack, this, [this] { navigateTo(PageHome); });
	connect(layouts, &LayoutsPage::layoutApplied, this, [this] { m_home->refreshStatus(); });
	connect(keymap, &KeymapPage::navigateBack, this, [this] { navigateTo(PageHome); });
	connect(keymap, &KeymapPage::profileChanged, this, [this] { m_home->refreshStatus(); });
}

void Shell::navigateTo(int index)
{
	if (m_navigating)
		return;

	m_navigating = true;
	if (m_nav->currentRow() != index)
		m_nav->setCurrentRow(index);

	switch (index)
	{
	case PageHome:
	case PageControlTest:
	case PageLayouts:
	case PageKeymap:
		if (QWidget *page = m_created.value(index, nullptr))
			m_pages->setCurrentWidget(page);
		break;
	case PageServer:
	{
		auto *server = new Server();
		m_pages->addWidget(server);
		m_pages->setCurrentWidget(server);
		connect(server,
				&Server::navigateBack,
				this,
				[this, server]
				{
					m_pages->removeWidget(server);
					server->deleteLater();
					navigateTo(PageHome);
				});
		break;
	}
	case PagePreferences:
	{
		auto *preferences = new Preferences();
		m_pages->addWidget(preferences);
		m_pages->setCurrentWidget(preferences);
		connect(preferences,
				&Preferences::navigateBack,
				this,
				[this, preferences]
				{
					m_pages->removeWidget(preferences);
					preferences->deleteLater();
					navigateTo(PageHome);
				});
		break;
	}
	case PageAbout:
	{
		auto *about = new About();
		m_pages->addWidget(about);
		m_pages->setCurrentWidget(about);
		connect(about,
				&About::navigateBack,
				this,
				[this, about]
				{
					m_pages->removeWidget(about);
					about->deleteLater();
					navigateTo(PageHome);
				});
		break;
	}
	default:
		break;
	}

	m_navigating = false;
}
