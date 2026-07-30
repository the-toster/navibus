package com.github.thetoster.navibus

import com.github.thetoster.navibus.settings.NaviBusSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HandlerLineMarkerTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/navigation"

    override fun setUp() {
        super.setUp()
        // FQN атрибута из фикстур (короче реального дефолта).
        NaviBusSettings.getInstance(project).attributeFqn = "\\App\\Attribute\\Handler"
        myFixture.configureByFiles("messages.php", "attribute.php", "handlers.php")
    }

    private fun search() = HandlerMethodSearch.getInstance(project)

    // Дискриминатор: тип-хинт обработчика задан импортированным коротким именем.
    // Если резолв FQN не работает — списки будут пустыми.
    fun testResolvesImportedShortNameAndCountsHandlers() {
        assertEquals(2, search().findHandlers("\\App\\Message\\Foo").size)
        assertEquals(1, search().findHandlers("\\App\\Message\\Bar").size)
    }

    fun testNoHandlersForPlainClass() {
        assertTrue(search().findHandlers("\\App\\Message\\Plain").isEmpty())
    }

    fun testFqnMatchIsCaseInsensitiveAndBackslashTolerant() {
        assertEquals(1, search().findHandlers("app\\message\\bar").size)
    }

    fun testGutterOnSingleReference() {
        myFixture.configureByFile("usage_single.php")
        assertEquals(1, myFixture.findGuttersAtCaret().size)
    }

    fun testMarkerPerClassOnSameLine() {
        myFixture.configureByFile("usage_multi.php")
        myFixture.doHighlighting()
        // Две ClassReference на одной строке -> два независимых LineMarkerInfo
        // (в gutter платформа их визуально сливает в одну иконку).
        val ours = DaemonCodeAnalyzerImpl
            .getLineMarkers(myFixture.editor.document, project)
            .count { it.lineMarkerTooltip?.startsWith("Перейти") == true }
        assertEquals(2, ours)
    }

    fun testNoGutterWhenNoHandler() {
        myFixture.configureByFile("usage_none.php")
        assertTrue(myFixture.findGuttersAtCaret().isEmpty())
    }

    // Требование: атрибута может не быть в проекте — плагин не должен падать.
    fun testAbsentAttributeYieldsNothing() {
        NaviBusSettings.getInstance(project).attributeFqn = "\\App\\Nonexistent\\Attr"
        assertTrue(search().findHandlers("\\App\\Message\\Foo").isEmpty())

        myFixture.configureByFile("usage_single.php")
        assertTrue(myFixture.findGuttersAtCaret().isEmpty())
    }
}
