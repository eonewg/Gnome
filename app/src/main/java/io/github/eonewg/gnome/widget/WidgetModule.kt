package io.github.eonewg.gnome.widget

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.eonewg.gnome.data.service.AccountRefreshListener

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    @IntoSet
    abstract fun bindAccountRefreshListener(
        listener: WidgetAccountRefreshListener,
    ): AccountRefreshListener
}