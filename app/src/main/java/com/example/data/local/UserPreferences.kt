package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "poddeck_prefs")

class UserPreferences(private val context: Context) {
    private object Keys {
        val LAST_EPISODE_ID = longPreferencesKey("last_episode_id")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
    }

    val lastEpisodeId: Flow<Long?> = context.userPrefsDataStore.data.map { it[Keys.LAST_EPISODE_ID] }
    val defaultSpeed: Flow<Float> = context.userPrefsDataStore.data.map { it[Keys.DEFAULT_SPEED] ?: 1.0f }
    val autoPlayNext: Flow<Boolean> = context.userPrefsDataStore.data.map { it[Keys.AUTO_PLAY_NEXT] ?: true }

    suspend fun setLastEpisodeId(episodeId: Long) {
        context.userPrefsDataStore.edit { it[Keys.LAST_EPISODE_ID] = episodeId }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.userPrefsDataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }
}
