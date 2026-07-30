package com.github.thetoster.navibus

import com.github.thetoster.navibus.settings.NaviBusSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method

/**
 * Ищет методы-обработчики: помечены настроенным атрибутом и принимают параметр
 * заданного типа.
 *
 * Этап 2: полный обход [PhpIndex] с кэшированием результата
 * (карта «тип параметра → методы») через [CachedValuesManager]. Кэш сбрасывается
 * при изменении PSI или FQN атрибута в настройках. На этапе 3 обход заменится на
 * собственный индекс.
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
        val attrFqn = normalizeFqn(attributeFqn) ?: return emptyMap()
        val phpIndex = PhpIndex.getInstance(project)
        val result = HashMap<String, MutableList<Method>>()

        for (classFqn in phpIndex.getAllClassFqns(null)) {
            for (phpClass in phpIndex.getClassesByFQN(classFqn)) {
                for (method in phpClass.ownMethods) {
                    if (method.getAttributes(attrFqn).isEmpty()) continue
                    for (parameter in method.parameters) {
                        // .global() резолвит короткие имена из use в полный FQN —
                        // без него импортированный тип-хинт не совпал бы с
                        // разрешённым FQN из ClassReference.
                        for (type in parameter.declaredType.global(project).types) {
                            val paramFqn = normalizeFqn(type) ?: continue
                            result.getOrPut(paramFqn) { mutableListOf() }.add(method)
                        }
                    }
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
