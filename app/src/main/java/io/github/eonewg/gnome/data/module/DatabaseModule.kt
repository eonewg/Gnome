package io.github.eonewg.gnome.data.module

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.eonewg.gnome.data.local.FileStorage
import io.github.eonewg.gnome.data.local.GnomeDatabase
import io.github.eonewg.gnome.data.local.dao.MemoDao
import io.github.eonewg.gnome.data.repository.LocalDatabaseRepository
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
    fun provideLocalDatabaseRepository(
        memoDao: MemoDao,
        fileStorage: FileStorage
    ) = LocalDatabaseRepository(memoDao, fileStorage)
}
