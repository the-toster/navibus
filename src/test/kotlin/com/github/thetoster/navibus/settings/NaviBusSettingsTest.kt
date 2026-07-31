package com.github.thetoster.navibus.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NaviBusSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            // light-фикстура переиспользует проект между тестами — сбрасываем состояние
            NaviBusSettings.getInstance(project).attributeFqn = DEFAULT_HANDLER_ATTRIBUTE_FQN
            NaviBusSettings.getInstance(project).messageBaseFqn = ""
        } finally {
            super.tearDown()
        }
    }

    fun testDefaultValue() {
        assertEquals(DEFAULT_HANDLER_ATTRIBUTE_FQN, NaviBusSettings.getInstance(project).attributeFqn)
        // Фильтр по типу класса-сообщения по умолчанию выключен (пусто).
        assertEquals("", NaviBusSettings.getInstance(project).messageBaseFqn)
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
