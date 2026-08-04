package com.github.thetoster.navibus.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InlineBanner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil

class NaviBusSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            // light-фикстура переиспользует проект между тестами — сбрасываем состояние
            NaviBusSettings.getInstance(project).attributeFqn = DEFAULT_HANDLER_ATTRIBUTE_FQN
            NaviBusSettings.getInstance(project).messageBaseFqn = ""
            NaviBusSettings.getInstance(project).messageAttributeFqns = emptyList()
            NaviBusSettings.getInstance(project).ignoreHandlerAttribute = false
        } finally {
            super.tearDown()
        }
    }

    fun testDefaultValue() {
        assertEquals(DEFAULT_HANDLER_ATTRIBUTE_FQN, NaviBusSettings.getInstance(project).attributeFqn)
        // Фильтр класса-сообщения по умолчанию выключен: пустой базовый FQN и пустой
        // список атрибутов-маркеров.
        assertEquals("", NaviBusSettings.getInstance(project).messageBaseFqn)
        assertTrue(NaviBusSettings.getInstance(project).messageAttributeFqns.isEmpty())
        // Режим «игнорировать атрибут» по умолчанию выключен.
        assertFalse(NaviBusSettings.getInstance(project).ignoreHandlerAttribute)
    }

    fun testIgnoreHandlerAttributePersists() {
        val settings = NaviBusSettings.getInstance(project)
        settings.ignoreHandlerAttribute = true

        assertTrue(settings.ignoreHandlerAttribute)
        assertTrue(settings.state.ignoreHandlerAttribute)
    }

    fun testMessageAttributeFqnsPersistTrimAndDedup() {
        val settings = NaviBusSettings.getInstance(project)
        // trim, отбрасывание пустых строк и дублей.
        settings.messageAttributeFqns = listOf(
            "  \\App\\Message\\AsMessage  ", "", "\\App\\Message\\Command",
            "\\App\\Message\\AsMessage",
        )

        assertEquals(
            listOf("\\App\\Message\\AsMessage", "\\App\\Message\\Command"),
            settings.messageAttributeFqns,
        )
        assertEquals(
            listOf("\\App\\Message\\AsMessage", "\\App\\Message\\Command"),
            settings.state.messageAttributeFqns,
        )
    }

    // Round-trip адаптера text↔list в Configurable: после ap() значений
    // isModified должен снова стать false (иначе apply() не идемпотентен).
    fun testConfigurableMessageAttributesRoundTrip() {
        val settings = NaviBusSettings.getInstance(project)
        settings.messageAttributeFqns = listOf("\\App\\Message\\AsMessage")
        val configurable = NaviBusConfigurable(project)
        try {
            configurable.createComponent()
            assertFalse("just built from settings", configurable.isModified)
            configurable.apply()
            assertFalse("apply is idempotent", configurable.isModified)
            assertEquals(
                listOf("\\App\\Message\\AsMessage"),
                settings.messageAttributeFqns,
            )
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testMessageBaseFqnPersistsAndTrims() {
        val settings = NaviBusSettings.getInstance(project)
        settings.messageBaseFqn = "  \\App\\Message\\Envelope  "

        assertEquals("\\App\\Message\\Envelope", settings.messageBaseFqn)
        assertEquals("\\App\\Message\\Envelope", settings.state.messageBaseFqn)
    }

    fun testPersistsAndTrims() {
        val settings = NaviBusSettings.getInstance(project)
        settings.attributeFqn = "  \\App\\Custom\\Handler  "

        assertEquals("\\App\\Custom\\Handler", settings.attributeFqn)
        assertEquals("\\App\\Custom\\Handler", settings.state.attributeFqn)
    }

    // Включение «Ignore handler attribute» дизейблит поле «Handler attribute FQN»; если
    // при этом фильтр сообщений пуст — показывается предупреждение, которое исчезает,
    // как только фильтр задан.
    fun testIgnoreCheckboxDisablesHandlerFieldAndWarnsWithoutFilter() {
        // Герметичность: light-проект переиспользуется между классами тестов, а поля
        // фильтра могут протечь. Начинаем с пустого фильтра и выключенного режима.
        val settings = NaviBusSettings.getInstance(project)
        settings.messageBaseFqn = ""
        settings.messageAttributeFqns = emptyList()
        settings.ignoreHandlerAttribute = false

        val configurable = NaviBusConfigurable(project)
        try {
            val panel = configurable.createComponent()
            val checkbox = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first()
            val textFields = UIUtil.findComponentsOfType(panel, JBTextField::class.java)
            val handlerField = textFields.first()
            val baseField = textFields[1]
            val warning = UIUtil.findComponentsOfType(panel, InlineBanner::class.java).first()

            assertTrue("handler field enabled by default", handlerField.isEnabled)
            assertFalse("no warning by default", warning.isVisible)

            checkbox.isSelected = true
            assertFalse("handler field disabled in ignore mode", handlerField.isEnabled)
            assertTrue("warning shown: ignore on but filter empty", warning.isVisible)

            baseField.text = "\\App\\Message\\X"
            assertFalse("warning hidden once a filter is set", warning.isVisible)
            assertFalse("handler field still disabled in ignore mode", handlerField.isEnabled)

            checkbox.isSelected = false
            assertTrue("handler field re-enabled when ignore off", handlerField.isEnabled)
        } finally {
            configurable.disposeUIResources()
        }
    }

    // Пустой «Handler attribute FQN» без включённого режима «игнорировать атрибут» —
    // нерабочая конфигурация: должна быть ошибка валидации (блокирует Apply). Включение
    // режима убирает требование к полю.
    fun testEmptyHandlerFqnIsInvalidUnlessIgnoreEnabled() {
        val settings = NaviBusSettings.getInstance(project)
        settings.attributeFqn = "\\App\\Attribute\\Handler"
        settings.ignoreHandlerAttribute = false

        val configurable = NaviBusConfigurable(project)
        try {
            val panel = configurable.createComponent() as DialogPanel
            val textFields = UIUtil.findComponentsOfType(panel, JBTextField::class.java)
            val handlerField = textFields.first()
            val checkbox = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first()

            handlerField.text = "   "
            assertTrue(
                "empty handler FQN + ignore off must be invalid",
                panel.validateAll().any { it.component === handlerField },
            )

            checkbox.isSelected = true
            assertTrue(
                "ignore on: handler FQN no longer required",
                panel.validateAll().none { it.component === handlerField },
            )

            checkbox.isSelected = false
            handlerField.text = "\\App\\Attribute\\Handler"
            assertTrue(
                "non-empty handler FQN is valid",
                panel.validateAll().none { it.component === handlerField },
            )
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testConfigurablePanelBuilds() {
        val configurable = NaviBusConfigurable(project)
        try {
            assertNotNull(configurable.createComponent())
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }
}
