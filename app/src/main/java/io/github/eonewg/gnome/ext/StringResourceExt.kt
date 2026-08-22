package io.github.eonewg.gnome.ext

import io.github.eonewg.gnome.GnomeApp

/**
 * Get the string resources by the R.string.xx.string
 *
 * To support i18n
 * @author Xeu<thankrain@qq.com>
 */
val Int.string get() = GnomeApp.CONTEXT.getString(this)