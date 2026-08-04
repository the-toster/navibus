package com.github.thetoster.navibus

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ExtendsList
import com.jetbrains.php.lang.psi.elements.ImplementsList
import com.jetbrains.php.lang.psi.elements.Method
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

        val search = HandlerMethodSearch.getInstance(element.project)

        val handlers: List<Method> = if (search.isIgnoreHandlerAttribute()) {
            // Режим «атрибут обработчика не размечен»: фильтр сообщения — обязательный
            // гейт (иначе «сообщением» был бы любой класс, и дорогой поиск потребителей
            // стал бы шумом). Сначала дешёвая проверка фильтра, затем — все public-методы,
            // принимающие сообщение.
            if (!search.isMessageFilterActive() || !search.isMessageClass(fqn)) return
            search.findMethodsAccepting(fqn)
        } else {
            // Атрибутный режим: обработчики берутся по атрибуту (дешёвый кэш-lookup),
            // фильтр сообщения проверяется только после — и только если активен, чтобы
            // не дёргать индекс на каждый leaf.
            val found = search.findHandlers(fqn)
            if (found.isEmpty()) return
            if (search.isMessageFilterActive() && !search.isMessageClass(fqn)) return
            found
        }
        if (handlers.isEmpty()) return

        // Не ведём переход «сам на себя»: если якорь иконки лежит внутри самого
        // метода-обработчика (тип-хинт его же параметра), эта цель бесполезна.
        // Остаются только «соседние» обработчики; если их нет — иконку не рисуем.
        val targets = handlers.filterNot { PsiTreeUtil.isAncestor(it, element, false) }
        if (targets.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(NavibusIcons.Handler, "Handlers")
            .setTargets(targets)
            .setTooltipText(
                if (targets.size == 1) "Go to handler"
                else "Go to handlers (${targets.size})"
            )
        result.add(builder.createLineMarkerInfo(element))
    }

    /**
     * FQN класса, если этот leaf — «якорь» для иконки: последний идентификатор
     * упоминания класса ([ClassReference]) либо имя в определении класса
     * ([PhpClass]). Иначе null. Условие на конкретный leaf исключает дублирование
     * иконки в пределах одного элемента.
     *
     * Ссылки внутри `extends`/`implements` ([ExtendsList]/[ImplementsList])
     * игнорируются: это объявление иерархии, а не «упоминание сообщения». Иначе на
     * строке `class Foo implements Message` появлялся бы второй маркер (на ссылке
     * `Message`), сливающийся с иконкой самого класса — особенно заметно в режиме
     * «игнорировать атрибут», где у базового интерфейса почти всегда есть принимающий
     * его метод.
     */
    private fun classFqnForLeaf(element: PsiElement): String? {
        return when (val parent = element.parent) {
            is ClassReference -> when {
                parent.lastChild !== element -> null
                parent.parent is ExtendsList || parent.parent is ImplementsList -> null
                else -> parent.fqn
            }
            is PhpClass -> if (parent.nameIdentifier === element) parent.fqn else null
            else -> null
        }
    }
}
