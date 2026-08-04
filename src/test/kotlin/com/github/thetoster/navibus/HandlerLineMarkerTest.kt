package com.github.thetoster.navibus

import com.github.thetoster.navibus.settings.NaviBusSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class HandlerLineMarkerTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/navigation"

    override fun setUp() {
        super.setUp()
        // FQN атрибута из фикстур (короче реального дефолта).
        NaviBusSettings.getInstance(project).attributeFqn = "\\App\\Attribute\\Handler"
        // Фильтр выключен по умолчанию; сбрасываем оба правила, т.к. light-проект
        // переиспользуется между тестами (иначе значение фильтра протекает).
        NaviBusSettings.getInstance(project).messageBaseFqn = ""
        NaviBusSettings.getInstance(project).messageAttributeFqns = emptyList()
        NaviBusSettings.getInstance(project).ignoreHandlerAttribute = false
        myFixture.configureByFiles("messages.php", "attribute.php", "handlers.php")
    }

    private fun search() = HandlerMethodSearch.getInstance(project)

    /** Число наших gutter-маркеров в текущем редакторе (по тултипу "Go to..."). */
    private fun goToMarkerCount(): Int =
        DaemonCodeAnalyzerImpl
            .getLineMarkers(myFixture.editor.document, project)
            .count { it.lineMarkerTooltip?.startsWith("Go to") == true }

    /** Есть ли наш маркер на строке, где впервые встречается [marker]. */
    private fun hasMarkerAtLineOf(marker: String): Boolean {
        val doc = myFixture.editor.document
        val line = doc.getLineNumber(doc.text.indexOf(marker))
        return DaemonCodeAnalyzerImpl
            .getLineMarkers(doc, project)
            .filter { it.lineMarkerTooltip?.startsWith("Go to") == true }
            .any { doc.getLineNumber(it.element!!.textRange.startOffset) == line }
    }

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

    // Маркер должен использовать кастомную иконку плагина, а не платформенную.
    fun testMarkerUsesCustomIcon() {
        myFixture.configureByFile("usage_single.php")
        myFixture.doHighlighting()
        val marker = DaemonCodeAnalyzerImpl
            .getLineMarkers(myFixture.editor.document, project)
            .first { it.lineMarkerTooltip?.startsWith("Go to") == true }
        assertSame(NavibusIcons.Handler, marker.icon)
    }

    fun testNoGutterWhenNoHandler() {
        myFixture.configureByFile("usage_none.php")
        assertTrue(myFixture.findGuttersAtCaret().isEmpty())
    }

    fun testMarkerOnClassDefinition() {
        // messages.php содержит определения Foo, Bar, Loose, Trec (есть обработчики)
        // и Plain (нет). Фильтр по типу выключен (дефолт) → маркер у всех четырёх.
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))
        myFixture.doHighlighting()
        assertEquals(4, goToMarkerCount())
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

    // Фильтр по типу ограничивает маркеры подтипами базового FQN. Пара «выключен →
    // включён» на определениях messages.php: Foo/Bar/Loose/Trec (4) сужаются до
    // Foo/Bar (2). Loose имеет обработчик, но не подтип Envelope — выпадает.
    fun testMessageFilterRestrictsToSubtypes() {
        val settings = NaviBusSettings.getInstance(project)
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))

        myFixture.doHighlighting()
        assertEquals("filter off: markers on Foo, Bar, Loose, Trec", 4, goToMarkerCount())

        settings.messageBaseFqn = "\\App\\Message\\Envelope"
        // Смена настройки не меняет PSI — форсируем пересчёт маркеров (в проде это
        // делает NaviBusConfigurable.apply()).
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()
        assertEquals("filter on: Loose dropped", 2, goToMarkerCount())
        assertTrue("Bar implements Envelope directly", hasMarkerAtLineOf("class Bar"))
        assertFalse("Loose is not a subtype", hasMarkerAtLineOf("class Loose"))
    }

    // Транзитивность: Foo реализует Command, а Command расширяет Envelope. Фильтр по
    // Envelope обязан пропускать Foo (проверяет обход всей иерархии, а не только
    // прямых интерфейсов).
    fun testMessageFilterIsTransitive() {
        NaviBusSettings.getInstance(project).messageBaseFqn = "\\App\\Message\\Envelope"
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))
        myFixture.doHighlighting()
        assertTrue("Foo -> Command -> Envelope", hasMarkerAtLineOf("class Foo"))
    }

    // Строгая семантика: `use TraitName` — это не implements/extends. Trec использует
    // трейт Marker; фильтр по \App\Message\Marker не должен давать ему маркер (ни один
    // класс не наследует/реализует Marker) → 0 маркеров.
    fun testMessageFilterExcludesTraits() {
        NaviBusSettings.getInstance(project).messageBaseFqn = "\\App\\Message\\Marker"
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()
        assertEquals(0, goToMarkerCount())
        assertFalse("trait use is not implements/extends", hasMarkerAtLineOf("class Trec"))
    }

    // Регресс: при активном фильтре маркер на тип-хинте параметра обработчика (тоже
    // ClassReference) не должен пропадать, если класс — подтип. Foo реализует
    // Command extends Envelope, у onFoo есть сосед onFooAgain → иконка обязана
    // остаться и с фильтром по Envelope.
    fun testMessageFilterKeepsHandlerParamMarker() {
        NaviBusSettings.getInstance(project).messageBaseFqn = "\\App\\Message\\Envelope"
        myFixture.openFileInEditor(myFixture.findFileInTempDir("handlers.php"))
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()
        assertTrue(
            "handler param marker must survive the type filter",
            hasMarkerAtLineOf("public function onFoo(Foo \$foo)"),
        )
    }

    // Фильтр применяется и к упоминаниям (ClassReference), не только к определениям.
    fun testMessageFilterAppliesToUsages() {
        val settings = NaviBusSettings.getInstance(project)

        // Bar — подтип Envelope: маркер на упоминании остаётся. Bar имеет явный
        // конструктор — регресс: для `new Bar()` нельзя проверять тип через
        // ClassReference.resolve() (вернёт __construct), только по FQN.
        settings.messageBaseFqn = "\\App\\Message\\Envelope"
        myFixture.configureByFile("usage_single.php")
        assertEquals(1, myFixture.findGuttersAtCaret().size)

        // Несуществующий базовый тип: ни один класс не подтип — маркера нет.
        settings.messageBaseFqn = "\\App\\Message\\Nonexistent"
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()
        assertTrue(myFixture.findGuttersAtCaret().isEmpty())
    }

    // Правило фильтра по атрибуту-маркеру класса (без базового типа). Loose помечен
    // #[AsMessage] (короткое имя из use → резолв в FQN), остальные классы с
    // обработчиками — нет. Базовый тип пуст → правило по типу ничего не пропускает,
    // решает только атрибут: остаётся один маркер, у Loose.
    fun testMessageFilterMatchesByAttribute() {
        val settings = NaviBusSettings.getInstance(project)
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))

        settings.messageAttributeFqns = listOf("\\App\\Attribute\\AsMessage")
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()
        assertEquals("only attribute-marked Loose passes", 1, goToMarkerCount())
        assertTrue("Loose is annotated #[AsMessage]", hasMarkerAtLineOf("class Loose"))
        assertFalse("Foo has no marker attribute", hasMarkerAtLineOf("class Foo"))
    }

    // Семантика OR: активны оба правила. База = Envelope пропускает Foo/Bar (подтипы),
    // атрибут AsMessage пропускает Loose. Trec — ни подтип, ни помечен → выпадает.
    fun testMessageFilterCombinesBaseAndAttributeWithOr() {
        val settings = NaviBusSettings.getInstance(project)
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))

        settings.messageBaseFqn = "\\App\\Message\\Envelope"
        settings.messageAttributeFqns = listOf("\\App\\Attribute\\AsMessage")
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()
        assertEquals("Foo/Bar by subtype + Loose by attribute", 3, goToMarkerCount())
        assertTrue("Bar is a subtype", hasMarkerAtLineOf("class Bar"))
        assertTrue("Loose is annotated", hasMarkerAtLineOf("class Loose"))
        assertFalse("Trec matches neither rule", hasMarkerAtLineOf("class Trec"))
    }

    // Режим «игнорировать атрибут»: цели — все public-методы, принимающие Foo, без
    // учёта атрибута. Дискриминатор: атрибутный поиск даёт 2 (onFoo/onFooAgain), а
    // findMethodsAccepting — 3 (плюс notAHandler, короткое имя из use резолвится через
    // ReferencesSearch). private onFooPrivate исключён -> проверка public-only.
    fun testIgnoreAttributeFindsAllPublicAcceptingMethods() {
        assertEquals("attribute mode: 2 marked handlers", 2, search().findHandlers("\\App\\Message\\Foo").size)
        val names = search().findMethodsAccepting("\\App\\Message\\Foo").map { it.name }.toSet()
        assertEquals(
            "ignore mode: all public methods accepting Foo, private excluded",
            setOf("onFoo", "onFooAgain", "notAHandler"),
            names,
        )
    }

    // Режим «игнорировать атрибут» гейтится фильтром сообщения. Фильтр = Envelope:
    // Foo/Bar (подтипы) получают маркер на все принимающие public-методы; Loose имеет
    // потребителя onLoose(Loose), но не подтип Envelope (правило по атрибуту не задано)
    // -> маркера нет. Заодно прогоняет ReferencesSearch в marker-проходе (не должно
    // упасть на SlowOperations).
    fun testIgnoreAttributeGatedByMessageFilter() {
        val settings = NaviBusSettings.getInstance(project)
        settings.ignoreHandlerAttribute = true
        settings.messageBaseFqn = "\\App\\Message\\Envelope"
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()

        assertTrue("Foo is a subtype -> marked to its consumers", hasMarkerAtLineOf("class Foo"))
        assertFalse("Loose has a consumer but is not a message", hasMarkerAtLineOf("class Loose"))
    }

    // Режим «игнорировать атрибут» без активного фильтра сообщений не ставит маркеров:
    // без фильтра «сообщением» был бы любой класс — режим выключен.
    fun testIgnoreAttributeInactiveWithoutFilter() {
        NaviBusSettings.getInstance(project).ignoreHandlerAttribute = true
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()
        assertEquals(0, goToMarkerCount())
    }

    // Число наших маркеров на строке, где впервые встречается [marker].
    private fun goToMarkerCountAtLineOf(marker: String): Int {
        val doc = myFixture.editor.document
        val line = doc.getLineNumber(doc.text.indexOf(marker))
        return DaemonCodeAnalyzerImpl.getLineMarkers(doc, project)
            .filter { it.lineMarkerTooltip?.startsWith("Go to") == true }
            .count { doc.getLineNumber(it.element!!.textRange.startOffset) == line }
    }

    // Регресс: ссылка на класс в extends/implements не должна получать маркер. На
    // строке `class Bar implements Envelope` (Envelope — базовый тип, у него есть
    // принимающий метод onEnvelope) в ignore-режиме должен быть ровно ОДИН маркер — на
    // самом Bar (ведёт к принимающим Bar методам), а не второй на ссылке `Envelope`.
    fun testIgnoreModeSkipsInheritanceClauseReference() {
        val settings = NaviBusSettings.getInstance(project)
        settings.ignoreHandlerAttribute = true
        settings.messageBaseFqn = "\\App\\Message\\Envelope"
        myFixture.openFileInEditor(myFixture.findFileInTempDir("messages.php"))
        DaemonCodeAnalyzer.getInstance(project).restart("navibus test: settings changed")
        myFixture.doHighlighting()

        assertEquals(
            "exactly one marker on the class name, none on the `implements` reference",
            1,
            goToMarkerCountAtLineOf("class Bar implements Envelope"),
        )
    }

    // Требование: атрибута может не быть в проекте — плагин не должен падать.
    fun testAbsentAttributeYieldsNothing() {
        NaviBusSettings.getInstance(project).attributeFqn = "\\App\\Nonexistent\\Attr"
        assertTrue(search().findHandlers("\\App\\Message\\Foo").isEmpty())

        myFixture.configureByFile("usage_single.php")
        assertTrue(myFixture.findGuttersAtCaret().isEmpty())
    }
}
