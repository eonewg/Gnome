package io.github.eonewg.gnome.data.module

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.eonewg.gnome.data.local.GnomeDatabase
import io.github.eonewg.gnome.data.local.dao.MemoDao
import io.github.eonewg.gnome.data.local.dao.SyncOperationDao
import io.github.eonewg.gnome.data.local.dao.TagDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): GnomeDatabase {
        return GnomeDatabase.getDatabase(context)
    }

    @Singleton
    @Provides
    fun provideMemoDao(database: GnomeDatabase) = database.memoDao()

    @Singleton
    @Provides
    fun provideSyncOperationDao(database: GnomeDatabase) = database.syncOperationDao()

    @Singleton
    @Provides
    fun provideTagDao(database: GnomeDatabase) = database.tagDao()
}
