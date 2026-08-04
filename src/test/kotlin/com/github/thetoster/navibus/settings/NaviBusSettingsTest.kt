package com.github.thetoster.navibus.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NaviBusSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            // light-фикстура переиспользует проект между тестами — сбрасываем состояние
            NaviBusSettings.getInstance(project).attributeFqn = DEFAULT_HANDLER_ATTRIBUTE_FQN
            NaviBusSettings.getInstance(project).messageBaseFqn = ""
            NaviBusSettings.getInstance(project).messageAttributeFqns = emptyList()
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
