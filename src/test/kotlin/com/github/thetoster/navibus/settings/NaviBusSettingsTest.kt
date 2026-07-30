package com.github.thetoster.navibus.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NaviBusSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            // light-фикстура переиспользует проект между тестами — сбрасываем состояние
            NaviBusSettings.getInstance(project).attributeFqn = DEFAULT_HANDLER_ATTRIBUTE_FQN
        } finally {
            super.tearDown()
        }
    }

    fun testDefaultValue() {
        assertEquals(DEFAULT_HANDLER_ATTRIBUTE_FQN, NaviBusSettings.getInstance(project).attributeFqn)
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
