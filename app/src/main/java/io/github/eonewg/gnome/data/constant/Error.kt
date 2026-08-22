package io.github.eonewg.gnome.data.constant

import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string

class GnomeException(string: String) : Exception(string) {
    companion object {
        val notLogin = GnomeException("NOT_LOGIN")
        val invalidAccessToken = GnomeException("INVALID_ACCESS_TOKEN")
        val accessTokenInvalid = GnomeException("ACCESS_TOKEN_INVALID")
        val invalidParameter = GnomeException("INVALID_PARAMETER")
        val invalidServer = GnomeException("INVALID_SERVER")
    }

    override fun getLocalizedMessage(): String? {
        return when (this) {
            invalidAccessToken -> R.string.invalid_access_token.string
            accessTokenInvalid -> R.string.access_token_invalid_relogin.string
            invalidServer -> R.string.invalid_server.string
            else -> {
                super.getLocalizedMessage()
            }
        }
    }
}
