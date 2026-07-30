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
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpAttribute
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
