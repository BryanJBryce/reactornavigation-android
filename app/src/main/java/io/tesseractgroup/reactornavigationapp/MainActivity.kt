package io.tesseractgroup.reactornavigationapp

import android.os.Bundle
import android.util.Log
import io.tesseractgroup.reactor.Core
import io.tesseractgroup.reactor.CoreUpdate
import io.tesseractgroup.reactornavigation.*

object App {
    private val handler = fun(state: AppState, event: AppEvent): CoreUpdate<AppState, AppCommand> {
        Log.d("NAVIGATION", "Stack: $event}")
        when (event) {
            is AppEvent.UpdateName -> {
                val updatedState = state.copy(name = event.name)
                return CoreUpdate.State(updatedState)
            }
        }
    }

    val core = Core(AppState("Hello World"), listOf(), handler)
    val navigationCore = ReactorNavigation.createNavigationCore(NavigationState())
}

sealed class AppEvent {
    data class UpdateName(val name: String?) : AppEvent()
}

sealed class AppCommand {
}

data class AppState(val name: String?)

const val NAV_CONT = "Main Container"

class NavigationState : NavigationStateProtocol() {

    override var rootViewContainer: ViewContainerState = NavContainerState(NAV_CONT, listOf(NameViewState("Initial View")))
}

class MainActivity : ReactorActivity(R.layout.activity_main, R.id.my_toolbar, R.id.container_reactor) {

    override val navigationCore: Core<NavigationStateProtocol, NavigationEvent, NavigationCommand> = App.navigationCore
}
