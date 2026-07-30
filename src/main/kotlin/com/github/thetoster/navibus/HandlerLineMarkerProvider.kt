package com.github.thetoster.navibus

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ClassReference

/**
 * Вешает gutter-иконку на упоминание класса ([ClassReference]), если существуют
 * методы-обработчики этого класса (помечены целевым атрибутом и принимают его
 * параметром). Переход — к одному или нескольким обработчикам.
 */
class HandlerLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Маркер вешаем только на leaf-элемент (требование платформы).
        if (element.firstChild != null) return

        val classRef = element.parent as? ClassReference ?: return
        // В одной ClassReference несколько leaf-токенов (части FQN); берём только
        // последний — идентификатор имени класса, чтобы не дублировать иконку.
        if (classRef.lastChild !== element) return

        val fqn = classRef.fqn ?: return
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
}
