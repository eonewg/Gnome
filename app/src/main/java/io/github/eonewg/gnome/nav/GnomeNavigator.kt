package io.github.eonewg.gnome.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The single back-stack mutation point for the app. Routes receive this
 * navigator and translate user callbacks into stack operations; produce no
 * other navigation path.
 *
 * All operations are plain [NavBackStack] list mutations, so the stack is
 * saveable (process death restores [rememberNavBackStack]) and every entry
 * keeps its own ViewModelStore via the entry decorators.
 */
class GnomeNavigator(
    private val backStack: NavBackStack<NavKey>,
) {
    /** Pushes [key]; with [singleTop] a repeated top entry is left untouched. */
    fun navigate(key: NavKey, singleTop: Boolean = false) {
        if (singleTop && backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    /**
     * Switches the drawer's root content without retaining previously visited
     * drawer destinations in the back stack. Returns false only for a clean
     * re-selection of the destination already being shown.
     */
    fun switchDrawerDestination(key: GnomeNavKey): Boolean {
        require(isDrawerSwitchDestination(key)) { "$key is not a drawer switch destination" }
        if (backStack.size == 1 && backStack.lastOrNull() == key) return false
        resetTo(key)
        return true
    }

    /** Pops the top entry. Safe to call repeatedly (rapid back). */
    fun goBack() {
        backStack.removeLastOrNull()
    }

    /** Replaces the whole stack with [keys] (login/logout/initial route). */
    fun resetTo(vararg keys: NavKey) {
        backStack.clear()
        backStack.addAll(keys)
    }

    /**
     * Pops every entry above [key]. With [inclusive] the matching entry itself
     * is popped too. No-op when [key] is not on the stack.
     */
    fun popUpTo(key: NavKey, inclusive: Boolean = false) {
        val index = backStack.indexOfLast { it == key }
        if (index == -1) return
        val keep = if (inclusive) index else index + 1
        while (backStack.size > keep) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}