package com.github.thetoster.navibus

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Вешает gutter-иконку с переходом к методам-обработчикам класса (помечены целевым
 * атрибутом и принимают его параметром). Иконка ставится:
 *  - на **упоминании** класса ([ClassReference] — тип-хинт, `new`, и т.п.);
 *  - на **определении** класса ([PhpClass] — его имя).
 *
 * Переход — к одному или нескольким обработчикам.
 */
class HandlerLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Маркер вешаем только на leaf-элемент (требование платформы).
        if (element.firstChild != null) return

        val fqn = classFqnForLeaf(element) ?: return
        if (fqn.isBlank()) return

        val handlers = HandlerMethodSearch.getInstance(element.project).findHandlers(fqn)
        if (handlers.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(handlers)
            .setTooltipText(
                if (handlers.size == 1) "Перейти к обработчику"
                else "Перейти к обработчикам (${handlers.size})"
            )
        result.add(builder.createLineMarkerInfo(element))
    }

    /**
     * FQN класса, если этот leaf — «якорь» для иконки: последний идентификатор
     * упоминания класса ([ClassReference]) либо имя в определении класса
     * ([PhpClass]). Иначе null. Условие на конкретный leaf исключает дублирование
     * иконки в пределах одного элемента.
     */
    private fun classFqnForLeaf(element: PsiElement): String? {
        return when (val parent = element.parent) {
            is ClassReference -> if (parent.lastChild === element) parent.fqn else null
            is PhpClass -> if (parent.nameIdentifier === element) parent.fqn else null
            else -> null
        }
    }
}
