package io.tesseractgroup.reactornavigation

import android.content.Context
import io.tesseractgroup.messagerouter.MessageRouter
import io.tesseractgroup.reactor.CommandProcessor
import io.tesseractgroup.reactor.Core
import io.tesseractgroup.reactor.CoreUpdate

/**
 * PrototypeBluetoothLibrary
 * Created by matt on 11/29/17.
 */
internal interface VisibleViewChanged

sealed class NavigationCommand {
    // View Container Changes
    data class TabIndexChanged(val lastVisibleViewContainer: NavContainerState) : NavigationCommand(), VisibleViewChanged

    data class ModalPresented(val lastVisibleViewContainer: NavContainerState) : NavigationCommand(), VisibleViewChanged
    data class ModalDismissed(val lastVisibleViewContainer: NavContainerState) : NavigationCommand(), VisibleViewChanged
    object RootContainerChanged : NavigationCommand(), VisibleViewChanged
    // View State Changes
    object NavViewPushed : NavigationCommand(), VisibleViewChanged

    object NavViewPopped : NavigationCommand(), VisibleViewChanged
    object NavViewReplaced : NavigationCommand(), VisibleViewChanged

    // Change that didn't affect the visible View State
    object HiddenUpdate : NavigationCommand()

    object AppContextChanged : NavigationCommand()
}

abstract class NavigationStateProtocol {

    abstract var rootViewContainer: ViewContainerState
        internal set
    var appInForeground: Boolean = true
        internal set

    internal fun findSubstateWithTag(tag: ViewContainerTag): ViewContainerState? {
        return rootViewContainer.findSubstateWithTag(tag)
    }

    fun findVisibleView(): ReactorViewState? {
        return rootViewContainer.findVisibleView()
    }

    fun findVisibleContainer(): NavContainerState? {
        return rootViewContainer.findVisibleContainer()
    }
}

object ReactorNavigation {

    fun createNavigationCore(
        state: NavigationStateProtocol,
        commandProcessors: List<CommandProcessor<NavigationStateProtocol, NavigationEvent, NavigationCommand>> = listOf())
        : Core<NavigationStateProtocol, NavigationEvent, NavigationCommand> {
        val processors = commandProcessors + ::commandProcessor
        return Core(state, processors, ::eventHandler)
    }

    private fun eventHandler(state: NavigationStateProtocol, event: NavigationEvent): CoreUpdate<NavigationStateProtocol, NavigationCommand> {

        val containerToUpdate = state.rootViewContainer.findSubstateWithTag(event.containerId)
        val oldVisibleViewContainer = state.findVisibleContainer()
        val oldVisibleView = state.findVisibleView()
        var command: NavigationCommand = NavigationCommand.HiddenUpdate
        val updatingCurrentContainer = oldVisibleViewContainer == containerToUpdate

        when (event) {
            is NavigationEvent.ChangeContainerIndex -> {
                if (containerToUpdate is TabContainerState) {
                    containerToUpdate.selectedIndex = event.selectedIndex
                    if (state.findVisibleContainer() != oldVisibleViewContainer && oldVisibleViewContainer != null) {
                        command = NavigationCommand.TabIndexChanged(oldVisibleViewContainer)
                    }
                }
            }
            is NavigationEvent.PresentModally -> {
                containerToUpdate?.modal = event.viewContainer
                containerToUpdate?.modal?.parentTag = event.containerId
                if (state.findVisibleContainer() != oldVisibleViewContainer && oldVisibleViewContainer != null) {
                    command = NavigationCommand.ModalPresented(oldVisibleViewContainer)
                }
            }
            is NavigationEvent.DismissModal -> {
                containerToUpdate?.modal = null
                if (state.findVisibleContainer() != oldVisibleViewContainer && oldVisibleViewContainer != null) {
                    command = NavigationCommand.ModalDismissed(oldVisibleViewContainer)
                }
            }
            is NavigationEvent.PushNavView -> {
                if (containerToUpdate is NavContainerState) {
                    containerToUpdate.viewStates = containerToUpdate.viewStates.plus(event.view)
                    if (updatingCurrentContainer) {
                        command = NavigationCommand.NavViewPushed
                    }
                }
            }
            is NavigationEvent.PopNavView -> {
                if (containerToUpdate is NavContainerState && containerToUpdate.viewStates.count() > 1) {
                    containerToUpdate.viewStates = containerToUpdate.viewStates.dropLast(1)
                    if (updatingCurrentContainer) {
                        command = NavigationCommand.NavViewPopped
                    }
                }
            }
            is NavigationEvent.UnwindToView -> {
                if (containerToUpdate is NavContainerState) {
                    val unwindToView = event.view
                    val lastView = containerToUpdate.viewStates.last()
                    if (unwindToView != null) {
                        val index = containerToUpdate.viewStates.indexOf(unwindToView) + 1
                        containerToUpdate.viewStates = containerToUpdate.viewStates.subList(0, index)
                    } else if (containerToUpdate.viewStates.count() > 1) {
                        containerToUpdate.viewStates = listOf(containerToUpdate.viewStates.first())
                    }
                    if (updatingCurrentContainer && lastView != state.findVisibleView()) {
                        command = NavigationCommand.NavViewPopped
                    }
                }
            }
            is NavigationEvent.ReplaceNavView -> {
                if (containerToUpdate is NavContainerState) {
                    containerToUpdate.viewStates = containerToUpdate.viewStates.dropLast(1)
                    containerToUpdate.viewStates = containerToUpdate.viewStates.plus(event.view)
                    if (updatingCurrentContainer) {
                        command = NavigationCommand.NavViewReplaced
                    }
                }
            }
            is NavigationEvent.ReplaceNavViewStack -> {
                if (containerToUpdate is NavContainerState) {
                    containerToUpdate.viewStates = event.views
                    if (updatingCurrentContainer) {
                        command = NavigationCommand.NavViewPopped
                    }
                }
            }
            is NavigationEvent.AppContextChanged -> {
                state.appInForeground = event.inForeground
                command = NavigationCommand.AppContextChanged
            }
            is NavigationEvent.ReplaceRootContainer -> {
                state.rootViewContainer = event.container
                command = NavigationCommand.RootContainerChanged
            }
        }

        return CoreUpdate.StateAndCommands(state, listOf(command))
    }

    val navigationCommandReceived = MessageRouter<NavigationCommand>()

    private fun commandProcessor(@Suppress("UNUSED_PARAMETER") core: Core<NavigationStateProtocol, NavigationEvent, NavigationCommand>, command: NavigationCommand) {
        navigationCommandReceived.send(command)
    }
}

interface ReactorViewState {
    fun view(context: Context): ReactorView
}

typealias ViewContainerTag = String

abstract class ViewContainerState {

    abstract var tag: ViewContainerTag
        internal set
    abstract var modal: NavContainerState?
        internal set
    var parentTag: ViewContainerTag? = null
        internal set

    internal fun findSubstateWithTag(tag: ViewContainerTag): ViewContainerState? {
        if (tag == this.tag) {
            return this
        } else if (modal?.findSubstateWithTag(tag) != null) {
            return modal?.findSubstateWithTag(tag)
        } else if (this is TabContainerState) {
            for (tabContainer in this.tabContainers) {
                if (tabContainer.findSubstateWithTag(tag) != null) {
                    return tabContainer.findSubstateWithTag(tag)
                }
            }
        }
        return null
    }

    internal fun findVisibleView(): ReactorViewState? {
        return findVisibleContainer()?.viewStates?.lastOrNull()
    }

    internal fun findVisibleContainer(): NavContainerState? {
        if (this.modal != null) {
            return this.modal?.findVisibleContainer()
        } else if (this is TabContainerState) {
            val visibleTab = this.tabContainers[selectedIndex]
            val visibleSubState = visibleTab.findVisibleContainer()
            if (visibleSubState != null) {
                return visibleSubState
            } else {
                return visibleTab
            }
        } else if (this is NavContainerState) {
            return this
        }
        return null
    }
}

class TabContainerState(override var tag: ViewContainerTag, val tabContainers: List<NavContainerState>, override var modal: NavContainerState? = null, selectedIndex: Int = 0) : ViewContainerState() {

    var selectedIndex: Int
        internal set

    init {
        this.selectedIndex = selectedIndex
        for (container in tabContainers) {
            container.parentTag = tag
        }
        modal?.parentTag = tag
    }
}

class NavContainerState(override var tag: ViewContainerTag, viewStates: List<ReactorViewState>, override var modal: NavContainerState? = null) : ViewContainerState() {

    var viewStates: List<ReactorViewState>
        internal set

    init {
        this.viewStates = viewStates
        this.modal = modal
        modal?.parentTag = parentTag
    }
}

sealed class NavigationEvent(val containerId: ViewContainerTag) {

    class AppContextChanged(val inForeground: Boolean) : NavigationEvent("rn_none")

    class ChangeContainerIndex(
        containerId: ViewContainerTag,
        val selectedIndex: Int) : NavigationEvent(containerId)

    class PresentModally(
        overContainerTag: ViewContainerTag,
        val viewContainer: NavContainerState
    ) : NavigationEvent(overContainerTag)

    class DismissModal(forContainer: ViewContainerTag) : NavigationEvent(forContainer)

    class PushNavView(
        containerId: ViewContainerTag,
        val view: ReactorViewState) : NavigationEvent(containerId)

    class ReplaceNavView(
        containerId: ViewContainerTag,
        val view: ReactorViewState) : NavigationEvent(containerId)

    class ReplaceNavViewStack(
        containerId: ViewContainerTag,
        val views: List<ReactorViewState>) : NavigationEvent(containerId)

    class PopNavView(
        containerId: ViewContainerTag) : NavigationEvent(containerId)

    class UnwindToView(
        containerId: ViewContainerTag,
        val view: ReactorViewState?) : NavigationEvent(containerId)

    class ReplaceRootContainer(
        val container: ViewContainerState) : NavigationEvent("rn_none")
}