package io.github.eonewg.gnome.ext

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import io.github.eonewg.gnome.data.model.Settings
import io.github.eonewg.gnome.util.SettingsSerializer

val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings_v3.json",
    serializer = SettingsSerializer
)
