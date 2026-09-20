package com.dalakoti.apps.speechtotext.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dalakoti.apps.speechtotext.core.asr.ModelLocator
import com.dalakoti.apps.speechtotext.core.audio.CaptureSource
import com.dalakoti.apps.speechtotext.core.model.Transcript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("speechtotext")

/**
 * DataStore, not Room. Five bounded transcripts and three settings: no queries, no joins,
 * no migrations, nothing Room would earn its keep on.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val modelRoot = stringPreferencesKey("model_root")
        val activeModelId = stringPreferencesKey("active_model_id")
        val captureSource = stringPreferencesKey("capture_source")
    }

    val modelRoot: Flow<String> = context.dataStore.data
        .map { it[Keys.modelRoot] ?: ModelLocator.DEFAULT_ROOT }

    val activeModelId: Flow<String?> = context.dataStore.data
        .map { it[Keys.activeModelId] }

    val captureSource: Flow<CaptureSource> = context.dataStore.data
        .map { CaptureSource.fromName(it[Keys.captureSource]) }

    suspend fun setModelRoot(path: String) =
        context.dataStore.edit { it[Keys.modelRoot] = path }.let { }

    suspend fun setActiveModelId(id: String) =
        context.dataStore.edit { it[Keys.activeModelId] = id }.let { }

    suspend fun setCaptureSource(source: CaptureSource) =
        context.dataStore.edit { it[Keys.captureSource] = source.name }.let { }
}

/**
 * The transcript's home. Nothing here reaches the clipboard on its own — copying is an
 * explicit action the user takes, see ClipboardWriter.
 */
class HistoryStore(private val context: Context) {

    private val key = stringPreferencesKey("history_json")
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val MAX_ITEMS = 5
    }

    private val listSerializer = ListSerializer(Transcript.serializer())

    val transcripts: Flow<List<Transcript>> = context.dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    suspend fun add(transcript: Transcript) {
        val current = transcripts.first()
        val next = (listOf(transcript) + current).take(MAX_ITEMS)
        context.dataStore.edit { it[key] = json.encodeToString(listSerializer, next) }
    }

    suspend fun clear() {
        context.dataStore.edit { it[key] = json.encodeToString(listSerializer, emptyList()) }
    }
}
