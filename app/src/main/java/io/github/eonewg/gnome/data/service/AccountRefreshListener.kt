package io.github.eonewg.gnome.data.service

/**
 * Implemented by peripheral surfaces that hold account-scoped data outside
 * the activity tree (widgets today). [AccountService] notifies every
 * listener whenever the account list or the current account changes, so
 * those surfaces re-read from the now-current account instead of showing
 * stale (or, after logout, sensitive) rows.
 */
interface AccountRefreshListener {
    fun onAccountDataChanged()
}