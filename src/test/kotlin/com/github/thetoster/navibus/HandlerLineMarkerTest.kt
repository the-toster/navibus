package com.github.thetoster.navibus

import com.github.thetoster.navibus.settings.NaviBusSettings
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
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
            .count { it.lineMarkerTooltip?.startsWith("Go to") == true }
        assertEquals(2, ours)
    }

    fun testRelatedItemsGroupName() {
        myFixture.configureByFile("usage_single.php")
        myFixture.doHighlighting()
        val groups = DaemonCodeAnalyzerImpl
            .getLineMarkers(myFixture.editor.document, project)
            .filterIsInstance<RelatedItemLineMarkerInfo<*>>()
            .filter { it.lineMarkerTooltip?.startsWith("Go to") == true }
            .flatMap { it.createGotoRelatedItems() }
            .map { it.group }
        // Заголовок группы в popup "Related Symbol" — не дефолтный 'XML'.
        assertFalse(groups.isEmpty())
        assertTrue("groups=$groups", groups.all { it == "Handlers" })
    }

    fun testNoGutterWhenNoHandler() {
        myFixture.configureByFile("usage_none.php")
        assertTrue(myFixture.findGuttersAtCaret().isEmpty())
    }

    fun testMarkerOnClassDefinition() {
        // messages.php (открыт в setUp) содержит определения Foo, Bar (есть
        // обработчики) и Plain (нет).
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))
        myFixture.doHighlighting()
        val ours = DaemonCodeAnalyzerImpl
            .getLineMarkers(myFixture.editor.document, project)
            .count { it.lineMarkerTooltip?.startsWith("Go to") == true }
        assertEquals(2, ours)
    }

    // Иконка на тип-хинте параметра самого обработчика не должна вести «сам на
    // себя». onBar — единственный обработчик Bar => на его хинте иконки нет;
    // onFoo имеет соседа onFooAgain => иконка есть, но с единственной целью.
    fun testNoSelfNavigationOnHandlerParam() {
        myFixture.openFileInEditor(myFixture.findFileInTempDir("handlers.php"))
        myFixture.doHighlighting()
        val doc = myFixture.editor.document
        val text = doc.text

        fun tooltipAt(marker: String): String? {
            val line = doc.getLineNumber(text.indexOf(marker))
            return DaemonCodeAnalyzerImpl
                .getLineMarkers(doc, project)
                .filter { it.lineMarkerTooltip?.startsWith("Go to") == true }
                .firstOrNull { doc.getLineNumber(it.element!!.textRange.startOffset) == line }
                ?.lineMarkerTooltip
        }

        // Единственный обработчик своего класса -> иконки нет.
        assertNull(tooltipAt("public function onBar(Bar \$bar)"))
        // Есть сосед onFooAgain -> иконка ведёт к нему одному.
        assertEquals("Go to handler", tooltipAt("public function onFoo(Foo \$foo)"))
    }

    // Требование: атрибута может не быть в проекте — плагин не должен падать.
    fun testAbsentAttributeYieldsNothing() {
        NaviBusSettings.getInstance(project).attributeFqn = "\\App\\Nonexistent\\Attr"
        assertTrue(search().findHandlers("\\App\\Message\\Foo").isEmpty())

        myFixture.configureByFile("usage_single.php")
        assertTrue(myFixture.findGuttersAtCaret().isEmpty())
    }
}
