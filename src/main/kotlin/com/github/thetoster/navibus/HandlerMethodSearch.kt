package com.github.thetoster.navibus

import com.github.thetoster.navibus.settings.NaviBusSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import com.jetbrains.php.PhpClassHierarchyUtils
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpModifier
import com.jetbrains.php.lang.psi.stubs.indexes.PhpAttributeIndex
import java.util.concurrent.ConcurrentHashMap

/**
 * Ищет методы-обработчики: помечены настроенным атрибутом и принимают параметр
 * заданного типа.
 *
 * Результат («тип параметра → методы») кэшируется через [CachedValuesManager];
 * кэш сбрасывается при изменении PSI или FQN атрибута в настройках.
 *
 * Этап 3: кандидаты берутся прямым запросом к готовому [PhpAttributeIndex]
 * PHP-плагина (использования атрибута по FQN), вместо полного перебора всех
 * классов проекта. Ключи индекса — FQN в нижнем регистре с ведущим `\`, что
 * совпадает с [normalizeFqn].
 */
@Service(Service.Level.PROJECT)
class HandlerMethodSearch(private val project: Project) {

    /** Методы-обработчики, принимающие класс с данным FQN. Пусто, если таких нет. */
    fun findHandlers(classFqn: String): List<Method> {
        val key = normalizeFqn(classFqn) ?: return emptyList()
        return handlersByParamType()[key].orEmpty()
    }

    /** Включён ли режим «игнорировать атрибут обработчика». */
    fun isIgnoreHandlerAttribute(): Boolean =
        NaviBusSettings.getInstance(project).ignoreHandlerAttribute

    /**
     * **Все public-методы**, принимающие класс с данным FQN параметром — без учёта
     * атрибута обработчика (режим [isIgnoreHandlerAttribute]). Кандидаты берутся
     * поиском ссылок на сам класс ([ReferencesSearch]); ссылка засчитывается, только
     * если она внутри параметра, чей `declaredType.global` совпадает с FQN — та же
     * проверка типа, что и в атрибутном режиме (единая семантика union/nullable/
     * импортированных имён). Результат кэшируется по FQN на цикл PSI (см.
     * [acceptingMethodsCache]); дорогой поиск не гоняется на каждый leaf/файл повторно.
     */
    fun findMethodsAccepting(classFqn: String): List<Method> {
        val key = normalizeFqn(classFqn) ?: return emptyList()
        return acceptingMethodsCache().getOrPut(key) { computeMethodsAccepting(classFqn, key) }
    }

    private fun computeMethodsAccepting(classFqn: String, key: String): List<Method> {
        val result = LinkedHashSet<Method>()
        val scope = GlobalSearchScope.allScope(project)
        for (phpClass in PhpIndex.getInstance(project).getAnyByFQN(classFqn)) {
            ReferencesSearch.search(phpClass, scope).forEach { ref ->
                val parameter = ref.element.parentOfType<Parameter>() ?: return@forEach
                val method = parameter.parentOfType<Method>() ?: return@forEach
                if (method.access != PhpModifier.Access.PUBLIC) return@forEach
                // Отсекаем ссылки, не являющиеся типом параметра (напр. значение
                // по умолчанию), сверяя FQN типа так же, как атрибутный режим.
                if (parameter.declaredType.global(project).types.any { normalizeFqn(it) == key }) {
                    result.add(method)
                }
            }
        }
        return result.toList()
    }

    /**
     * Кэш «FQN сообщения → принимающие public-методы» на цикл PSI. Ленивое заполнение
     * (см. [findMethodsAccepting]); инвалидация по PSI и по изменению настроек.
     */
    private fun acceptingMethodsCache(): ConcurrentHashMap<String, List<Method>> {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                ConcurrentHashMap<String, List<Method>>(),
                PsiModificationTracker.MODIFICATION_COUNT,
                NaviBusSettings.getInstance(project),
            )
        }
    }

    /**
     * Активен ли фильтр класса-сообщения — задано ли хоть одно правило: базовый FQN
     * (`implements`/`extends`) **или** непустой список атрибутов-маркеров класса. Если
     * ни одно правило не задано — фильтровать классы не нужно (текущее поведение).
     */
    fun isMessageFilterActive(): Boolean {
        val settings = NaviBusSettings.getInstance(project)
        return normalizeFqn(settings.messageBaseFqn) != null ||
            messageAttributeKeys(settings).isNotEmpty()
    }

    /**
     * Проходит ли класс с данным FQN фильтр класса-сообщения. Семантика OR: класс —
     * сообщение, если выполнено **любое** правило:
     *  - подтип настроенного базового FQN (`implements`/`extends`, транзитивно —
     *    обходятся только **родительские классы и интерфейсы**; трейты и `@mixin` не
     *    учитываются, на уровне PHP это не extends/implements);
     *  - помечен любым из настроенных атрибутов **на самом классе** (атрибуты в PHP не
     *    наследуются — иерархию для этого правила не обходим).
     *
     * Если ни одно правило не задано → всегда `true` (фильтр выключен).
     *
     * Класс резолвится по FQN через [PhpIndex.getAnyByFQN], а НЕ через
     * `ClassReference.resolve()`: у `new Foo()` резолв ссылки может вернуть
     * `__construct` (Method), а не класс. Если класса с таким FQN нет — не проходит
     * (нельзя проверить ни иерархию, ни атрибуты; аналогично тому, как атрибута может
     * не быть).
     */
    fun isMessageClass(classFqn: String): Boolean {
        val settings = NaviBusSettings.getInstance(project)
        val baseFqn = normalizeFqn(settings.messageBaseFqn)
        val attrKeys = messageAttributeKeys(settings)
        // Ни одного правила — фильтр выключен.
        if (baseFqn == null && attrKeys.isEmpty()) return true

        val targetFqn = normalizeFqn(classFqn) ?: return false
        // Сам базовый тип тоже считаем сообщением (аналог processSelf = true).
        if (baseFqn != null && targetFqn == baseFqn) return true

        var matched = false
        val processor = Processor<PhpClass> { sup ->
            if (normalizeFqn(sup.fqn) == baseFqn) {
                matched = true
                false // нашли — прекращаем обход
            } else {
                true
            }
        }
        for (phpClass in PhpIndex.getInstance(project).getAnyByFQN(classFqn)) {
            // Правило по атрибуту: атрибут на самом классе, без обхода иерархии.
            if (attrKeys.isNotEmpty() &&
                phpClass.attributes.any { normalizeFqn(it.fqn) in attrKeys }
            ) {
                return true
            }
            // Правило по типу: транзитивный обход родителей/интерфейсов.
            if (baseFqn != null) {
                PhpClassHierarchyUtils.processSuperClasses(phpClass, false, true, processor)
                if (!matched) {
                    PhpClassHierarchyUtils.processSuperInterfaces(phpClass, false, true, processor)
                }
                if (matched) return true
            }
        }
        return false
    }

    /** Нормализованные ключи атрибутов-маркеров класса (без пустых). */
    private fun messageAttributeKeys(settings: NaviBusSettings): Set<String> =
        settings.messageAttributeFqns.mapNotNull { normalizeFqn(it) }.toSet()

    private fun handlersByParamType(): Map<String, List<Method>> {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            val settings = NaviBusSettings.getInstance(project)
            val map = buildIndex(settings.attributeFqn)
            CachedValueProvider.Result.create(
                map,
                PsiModificationTracker.MODIFICATION_COUNT,
                settings,
            )
        }
    }

    private fun buildIndex(attributeFqn: String): Map<String, MutableList<Method>> {
        val attrKey = normalizeFqn(attributeFqn) ?: return emptyMap()
        val result = HashMap<String, MutableList<Method>>()

        val attributes = StubIndex.getElements(
            PhpAttributeIndex.KEY,
            attrKey,
            project,
            GlobalSearchScope.allScope(project),
            PhpAttribute::class.java,
        )
        for (attribute in attributes) {
            // Нас интересуют только атрибуты на методах (не на классах/параметрах).
            val method = attribute.owner as? Method ?: continue
            for (parameter in method.parameters) {
                // .global() резолвит короткие имена из use в полный FQN — без него
                // импортированный тип-хинт не совпал бы с разрешённым FQN из
                // ClassReference.
                for (type in parameter.declaredType.global(project).types) {
                    val paramFqn = normalizeFqn(type) ?: continue
                    result.getOrPut(paramFqn) { mutableListOf() }.add(method)
                }
            }
        }
        return result
    }

    companion object {
        fun getInstance(project: Project): HandlerMethodSearch = project.service()

        /**
         * Приводит FQN к каноничному виду для сравнения: с ведущим `\`, в нижнем
         * регистре (имена классов в PHP регистронезависимы). Возвращает null для
         * пустых/непригодных значений.
         */
        fun normalizeFqn(fqn: String?): String? {
            val trimmed = fqn?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val withLeading = if (trimmed.startsWith("\\")) trimmed else "\\$trimmed"
            return withLeading.lowercase()
        }
    }
}
