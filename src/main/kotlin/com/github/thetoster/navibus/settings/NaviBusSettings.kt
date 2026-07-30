package com.github.thetoster.navibus.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

/** FQN целевого атрибута по умолчанию. Настраивается в Settings. */
const val DEFAULT_HANDLER_ATTRIBUTE_FQN = "\\App\\Infrastructure\\MessageBus\\Autowire\\Handler"

/**
 * Project-level настройки плагина. Хранит FQN атрибута-маркера обработчика.
 * Значение настраивается пользователем; атрибута может не быть в проекте.
 */
@Service(Service.Level.PROJECT)
@State(name = "NaviBusSettings", storages = [Storage("navibus.xml")])
class NaviBusSettings : SimpleModificationTracker(), PersistentStateComponent<NaviBusSettings.State> {

    data class State(var attributeFqn: String = DEFAULT_HANDLER_ATTRIBUTE_FQN)

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        incModificationCount()
    }

    /** Нормализованный FQN (без ведущих/хвостовых пробелов). */
    var attributeFqn: String
        get() = myState.attributeFqn.trim()
        set(value) {
            val normalized = value.trim()
            if (normalized != myState.attributeFqn) {
                myState.attributeFqn = normalized
                incModificationCount()
            }
        }

    companion object {
        fun getInstance(project: Project): NaviBusSettings = project.service()
    }
}
