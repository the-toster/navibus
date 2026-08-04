package com.github.thetoster.navibus.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.xmlb.annotations.XCollection

/** FQN целевого атрибута по умолчанию. Настраивается в Settings. */
const val DEFAULT_HANDLER_ATTRIBUTE_FQN = "\\App\\Infrastructure\\MessageBus\\Autowire\\Handler"

/**
 * Project-level настройки плагина. Хранит FQN атрибута-маркера обработчика.
 * Значение настраивается пользователем; атрибута может не быть в проекте.
 */
@Service(Service.Level.PROJECT)
@State(name = "NaviBusSettings", storages = [Storage("navibus.xml")])
class NaviBusSettings : SimpleModificationTracker(), PersistentStateComponent<NaviBusSettings.State> {

    data class State(
        var attributeFqn: String = DEFAULT_HANDLER_ATTRIBUTE_FQN,
        var messageBaseFqn: String = "",
        @get:XCollection(style = XCollection.Style.v2)
        var messageAttributeFqns: MutableList<String> = mutableListOf(),
        var ignoreHandlerAttribute: Boolean = false,
    )

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

    /**
     * FQN интерфейса/родительского класса, которым обязан быть подтип класс-сообщение,
     * чтобы получить маркер. Пусто — фильтр выключен (текущее поведение). Нормализуется
     * (trim); при пустом значении маркер ставится для любого класса с обработчиками.
     */
    var messageBaseFqn: String
        get() = myState.messageBaseFqn.trim()
        set(value) {
            val normalized = value.trim()
            if (normalized != myState.messageBaseFqn) {
                myState.messageBaseFqn = normalized
                incModificationCount()
            }
        }

    /**
     * FQN атрибутов, которыми помечен **сам класс-сообщение** (не обработчик), чтобы
     * получить маркер. Дополнительное правило фильтра поверх [messageBaseFqn]: класс —
     * сообщение, если он подтип базового FQN **или** помечен любым из этих атрибутов
     * (семантика OR). Список нормализуется: trim, отбрасываются пустые строки и дубли.
     * Пустой список — правило по атрибутам выключено.
     */
    var messageAttributeFqns: List<String>
        get() = myState.messageAttributeFqns
        set(value) {
            val normalized = value.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (normalized != myState.messageAttributeFqns) {
                myState.messageAttributeFqns = normalized.toMutableList()
                incModificationCount()
            }
        }

    /**
     * Игнорировать атрибут обработчика: если включено, целями навигации становятся
     * **все public-методы**, принимающие класс-сообщение параметром (для проектов, где
     * обработчики не размечены атрибутом). Требует активного фильтра сообщений — иначе
     * «сообщением» был бы любой класс, и режим выключен. Дефолт `false` (атрибутный режим).
     */
    var ignoreHandlerAttribute: Boolean
        get() = myState.ignoreHandlerAttribute
        set(value) {
            if (value != myState.ignoreHandlerAttribute) {
                myState.ignoreHandlerAttribute = value
                incModificationCount()
            }
        }

    companion object {
        fun getInstance(project: Project): NaviBusSettings = project.service()
    }
}
