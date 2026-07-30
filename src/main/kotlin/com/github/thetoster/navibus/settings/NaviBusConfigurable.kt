package com.github.thetoster.navibus.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

/**
 * Страница настроек (Settings | Tools | Handler Navigation).
 * Позволяет задать FQN атрибута-маркера обработчика per-project.
 */
class NaviBusConfigurable(project: Project) : BoundConfigurable("Handler Navigation") {

    private val settings = NaviBusSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        row("Handler attribute FQN:") {
            textField()
                .columns(COLUMNS_LARGE)
                .bindText(settings::attributeFqn)
        }.rowComment(
            "Полный FQN атрибута, которым помечены методы-обработчики, " +
                "например \\App\\Infrastructure\\MessageBus\\Autowire\\Handler. " +
                "Атрибута может не быть в проекте."
        )
    }
}
