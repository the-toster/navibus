package com.github.thetoster.navibus.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.util.ui.JBUI
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Страница настроек (Settings | Tools | Navibus).
 * Позволяет задать FQN атрибута-маркера обработчика per-project.
 */
class NaviBusConfigurable(private val project: Project) : BoundConfigurable("Navibus") {

    private val settings = NaviBusSettings.getInstance(project)

    // Компоненты, между которыми есть реактивная связь (dependency между полями UI DSL
    // не выражается штатно и версионно-хрупок) — держим ссылки и обновляем вручную.
    private var handlerField: JBTextField? = null
    private var ignoreBox: JBCheckBox? = null
    private var baseField: JBTextField? = null
    private var attrsArea: JBTextArea? = null
    private var warningBanner: InlineBanner? = null
    private var dialogPanel: DialogPanel? = null

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

    /**
     * Синхронизирует зависимое состояние UI: при включённом «Ignore handler attribute»
     * поле «Handler attribute FQN» не нужно (дизейблим), а если при этом фильтр
     * сообщений пуст — режим ничего не пометит, поэтому показываем предупреждение.
     * Читает **живые** значения компонентов (а не settings), чтобы реагировать на ввод
     * до `apply()`.
     */
    private fun refreshUi() {
        val ignore = ignoreBox?.isSelected ?: return
        handlerField?.isEnabled = !ignore
        val filterEmpty = baseField?.text.isNullOrBlank() && attrsArea?.text.isNullOrBlank()
        val warn = ignore && filterEmpty
        warningBanner?.isVisible = warn
        // Переключение чекбокса меняет требование к полю атрибута — перепроверяем.
        dialogPanel?.validateAll()
    }

    /**
     * Поле «Handler attribute FQN» обязательно, если режим «игнорировать атрибут»
     * выключен: иначе обработчиков искать нечем и плагин не покажет переходов. При
     * включённом режиме поле не нужно (и задизейблено) — ошибки нет.
     */
    private fun ValidationInfoBuilder.validateHandlerFqn(field: JBTextField): ValidationInfo? =
        if (ignoreBox?.isSelected != true && field.text.isBlank()) error(HANDLER_FQN_REQUIRED)
        else null

    override fun apply() {
        super.apply()
        // Настройки могли измениться — пересчитать gutter-иконки во всех файлах.
        DaemonCodeAnalyzer.getInstance(project).restart("navibus: settings changed")
    }

    override fun reset() {
        super.reset()
        // super.reset() перезаписал значения компонентов из settings — пересчитать
        // зависимое состояние (enabled/предупреждение).
        refreshUi()
    }

    override fun createPanel(): DialogPanel {
        val ui = panel {
            row("Handler attribute FQN:") {
                handlerField = textField()
                    .columns(COLUMNS_LARGE)
                    .bindText(settings::attributeFqn)
                    .validationOnInput { validateHandlerFqn(it) }
                    .validationOnApply { validateHandlerFqn(it) }
                    .component
            }.rowComment(
                "Fully qualified name of the attribute that marks handler methods, " +
                    "e.g. \\App\\Infrastructure\\MessageBus\\Autowire\\Handler. " +
                    "The attribute does not have to exist in the project."
            )
            row {
                ignoreBox = checkBox("Match handlers by parameter type")
                    .bindSelected(settings::ignoreHandlerAttribute)
                    .component
            }.rowComment(
                "For projects where handlers are not annotated. When enabled, a handler is " +
                    "any public method that accepts the message as a parameter — the " +
                    "handler attribute above is not required. Requires a message filter " +
                    "(base type or attribute) below."
            )
            row("Message base type FQN:") {
                baseField = textField()
                    .columns(COLUMNS_LARGE)
                    .bindText(settings::messageBaseFqn)
                    .component
            }.rowComment(
                "Optional. If set, gutter markers are shown only for classes that " +
                    "implement or extend this interface/class (transitively), " +
                    "e.g. \\App\\Message\\MessageInterface."
            )
            row("Message attribute FQNs:") {
                attrsArea = textArea()
                    .columns(COLUMNS_LARGE)
                    .rows(3)
                    .bindText(::messageAttributeFqnsText)
                    .component
            }.rowComment(
                "Optional. One FQN per line. A class is treated as a message if it " +
                    "matches the base type above OR is annotated with any of these " +
                    "attributes (checked on the class itself), " +
                    "e.g. \\App\\Message\\AsMessage. Leave both filters empty to mark any " +
                    "class that has handlers."
            )
            row {
                // InlineBanner: рамка с фоном и иконкой. Текст переносим фиксированной
                // HTML-шириной (иначе одна длинная строка раздувает ширину панели →
                // горизонтальный скролл; сам по себе баннер строки не переносит).
                val banner = InlineBanner(warningHtml(), EditorNotificationPanel.Status.Warning)
                    .showCloseButton(false)
                warningBanner = banner
                cell(banner)
            }
        }

        // Реактивная связь: чекбокс дизейблит поле атрибута и (вместе с полями фильтра)
        // управляет видимостью предупреждения.
        val docListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refreshUi()
            override fun removeUpdate(e: DocumentEvent) = refreshUi()
            override fun changedUpdate(e: DocumentEvent) = refreshUi()
        }
        ignoreBox?.addItemListener { refreshUi() }
        baseField?.document?.addDocumentListener(docListener)
        attrsArea?.document?.addDocumentListener(docListener)
        dialogPanel = ui
        refreshUi()

        return ui
    }

    /**
     * Текст предупреждения в HTML с фиксированной шириной — так Swing переносит строки
     * и не раздувает ширину панели (иначе горизонтальный скролл). Ширина ≤ ширины полей
     * над баннером, чтобы он сам не задавал минимальную ширину панели.
     */
    private fun warningHtml(): String =
        "<html><body style='width:${JBUI.scale(360)}px'>$FILTER_REQUIRED_WARNING</body></html>"

    companion object {
        const val FILTER_REQUIRED_WARNING: String =
            "No gutter markers will be shown: \"Match handlers by parameter type\" " +
                "requires a message filter — set a base type or attribute FQNs above."

        const val HANDLER_FQN_REQUIRED: String =
            "Handler attribute FQN is required unless 'Match handlers by parameter type' " +
                "is enabled."
    }
}
