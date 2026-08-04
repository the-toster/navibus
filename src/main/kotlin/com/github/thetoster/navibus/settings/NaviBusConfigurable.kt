package com.github.thetoster.navibus.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

/**
 * Страница настроек (Settings | Tools | Navibus).
 * Позволяет задать FQN атрибута-маркера обработчика per-project.
 */
class NaviBusConfigurable(private val project: Project) : BoundConfigurable("Navibus") {

    private val settings = NaviBusSettings.getInstance(project)

    /**
     * Список FQN атрибутов-маркеров класса как многострочный текст (по одному FQN на
     * строку) — адаптер между [NaviBusSettings.messageAttributeFqns] (список) и
     * text area в UI DSL. Round-trip идемпотентен: пустые строки отбрасываются, при
     * чтении список склеивается обратно через `\n`.
     */
    private var messageAttributeFqnsText: String
        get() = settings.messageAttributeFqns.joinToString("\n")
        set(value) {
            settings.messageAttributeFqns = value.split('\n')
        }

    override fun apply() {
        super.apply()
        // FQN мог измениться — пересчитать gutter-иконки во всех файлах.
        DaemonCodeAnalyzer.getInstance(project).restart("navibus: handler attribute changed")
    }

    override fun createPanel(): DialogPanel = panel {
        row("Handler attribute FQN:") {
            textField()
                .columns(COLUMNS_LARGE)
                .bindText(settings::attributeFqn)
        }.rowComment(
            "Fully qualified name of the attribute that marks handler methods, " +
                "e.g. \\App\\Infrastructure\\MessageBus\\Autowire\\Handler. " +
                "The attribute does not have to exist in the project."
        )
        row("Message base type FQN:") {
            textField()
                .columns(COLUMNS_LARGE)
                .bindText(settings::messageBaseFqn)
        }.rowComment(
            "Optional. If set, gutter markers are shown only for classes that " +
                "implement or extend this interface/class (transitively), " +
                "e.g. \\App\\Message\\MessageInterface."
        )
        row("Message attribute FQNs:") {
            textArea()
                .columns(COLUMNS_LARGE)
                .rows(3)
                .bindText(::messageAttributeFqnsText)
        }.rowComment(
            "Optional. One FQN per line. A class is treated as a message if it " +
                "matches the base type above OR is annotated with any of these " +
                "attributes (checked on the class itself), " +
                "e.g. \\App\\Message\\AsMessage. Leave both filters empty to mark any " +
                "class that has handlers."
        )
    }
}
