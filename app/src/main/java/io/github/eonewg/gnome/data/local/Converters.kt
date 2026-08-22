package io.github.eonewg.gnome.data.local

import androidx.room.TypeConverter
import io.github.eonewg.gnome.data.local.entity.SyncEntityType
import io.github.eonewg.gnome.data.local.entity.SyncOperationType
import io.github.eonewg.gnome.data.model.MemoVisibility
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilli()
    }

    @TypeConverter
    fun toMemoVisibility(value: String) = enumValueOf<MemoVisibility>(value)

    @TypeConverter
    fun fromMemoVisibility(value: MemoVisibility) = value.name

    @TypeConverter
    fun toSyncEntityType(value: String) = enumValueOf<SyncEntityType>(value)

    @TypeConverter
    fun fromSyncEntityType(value: SyncEntityType) = value.name

    @TypeConverter
    fun toSyncOperationType(value: String) = enumValueOf<SyncOperationType>(value)

    @TypeConverter
    fun fromSyncOperationType(value: SyncOperationType) = value.name
}
