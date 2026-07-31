package com.github.thetoster.navibus

import com.github.thetoster.navibus.settings.NaviBusSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Processor
import com.jetbrains.php.PhpClassHierarchyUtils
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.stubs.indexes.PhpAttributeIndex

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

    /**
     * Активен ли фильтр «класс-сообщение по implements/extends» — задан ли базовый FQN
     * в настройках. Если нет — фильтровать классы по типу не нужно (текущее поведение).
     */
    fun isMessageFilterActive(): Boolean =
        normalizeFqn(NaviBusSettings.getInstance(project).messageBaseFqn) != null

    /**
     * Проходит ли класс с данным FQN фильтр по типу: является ли он подтипом
     * настроенного базового FQN. Строгая семантика `implements`/`extends` —
     * транзитивно обходятся только **родительские классы и интерфейсы**; трейты и
     * `@mixin` не учитываются (на уровне языка PHP это не extends/implements). Фильтр
     * выключен (пустой FQN) → всегда `true`.
     *
     * Класс резолвится по FQN через [PhpIndex.getAnyByFQN], а НЕ через
     * `ClassReference.resolve()`: у `new Foo()` резолв ссылки может вернуть
     * `__construct` (Method), а не класс. Если класса с таким FQN нет — не проходит
     * (нельзя проверить иерархию; аналогично тому, как атрибута может не быть).
     */
    fun isMessageClass(classFqn: String): Boolean {
        val baseFqn = normalizeFqn(NaviBusSettings.getInstance(project).messageBaseFqn)
            ?: return true
        val targetFqn = normalizeFqn(classFqn) ?: return false
        // Сам базовый тип тоже считаем сообщением (аналог processSelf = true).
        if (targetFqn == baseFqn) return true

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
            PhpClassHierarchyUtils.processSuperClasses(phpClass, false, true, processor)
            if (!matched) {
                PhpClassHierarchyUtils.processSuperInterfaces(phpClass, false, true, processor)
            }
            if (matched) break
        }
        return matched
    }

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
