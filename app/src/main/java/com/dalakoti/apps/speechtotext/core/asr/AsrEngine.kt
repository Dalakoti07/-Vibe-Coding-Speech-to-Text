package com.dalakoti.apps.speechtotext.core.asr

import android.os.Debug
import android.util.Log
import com.dalakoti.apps.speechtotext.core.model.EngineState
import com.dalakoti.apps.speechtotext.core.model.ModelDescriptor
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface AsrEngine {
    val state: StateFlow<EngineState>
    val diagnostics: StateFlow<EngineDiagnostics>

    suspend fun load(descriptor: ModelDescriptor)
    suspend fun transcribe(pcm: FloatArray, sampleRate: Int): String
    fun unload()
}

/** Numbers worth knowing, surfaced on the Settings screen instead of buried in logcat. */
data class EngineDiagnostics(
    val coldLoadMs: Long? = null,
    val pssAfterLoadKb: Int? = null,
    val peakPssKb: Int? = null,
    val lastTranscribeMs: Long? = null,
    val lastAudioSeconds: Float? = null,
)

/**
 * One recognizer, app-wide.
 *
 * Two instances means two ~600 MB encoders and an OOM kill, so [load] is mutex-guarded
 * and always unloads the previous model *before* allocating the next one.
 */
class SherpaOfflineAsrEngine : AsrEngine {

    private val tag = "AsrEngine"
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var loadedId: String? = null

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(EngineDiagnostics())
    override val diagnostics: StateFlow<EngineDiagnostics> = _diagnostics.asStateFlow()

    override suspend fun load(descriptor: ModelDescriptor) = mutex.withLock {
        if (loadedId == descriptor.id && recognizer != null) return@withLock

        _state.value = EngineState.Loading(descriptor.id)
        withContext(Dispatchers.IO) {
            // Release first. Never hold two models across a switch.
            releaseLocked()

            val recipe = ModelRecipes.forId(descriptor.recipeId)
            if (recipe == null) {
                _state.value = EngineState.Failed(
                    descriptor.id,
                    "Unknown recipe \"${descriptor.recipeId}\"",
                )
                return@withContext
            }

            val config = recipe.buildConfig(descriptor.dir, descriptor.tuning)
            // Log every resolved path: without All files access these all "exist = false"
            // and the native failure is indistinguishable from a bad config.
            Log.i(tag, "loading ${descriptor.id}; allFilesAccess=${ModelLocator.hasAccess()}")
            recipe.requiredFiles.forEach { name ->
                val f = java.io.File(descriptor.dir, name)
                Log.i(tag, "  $name exists=${f.exists()} bytes=${f.length()}")
            }

            val started = System.currentTimeMillis()
            try {
                // assetManager = null routes the init block to newFromFile(config).
                val r = OfflineRecognizer(assetManager = null, config = config)
                val elapsed = System.currentTimeMillis() - started
                recognizer = r
                loadedId = descriptor.id
                val pss = totalPssKb()
                _diagnostics.value = _diagnostics.value.copy(
                    coldLoadMs = elapsed,
                    pssAfterLoadKb = pss,
                    peakPssKb = maxOf(pss, _diagnostics.value.peakPssKb ?: 0),
                )
                _state.value = EngineState.Ready(descriptor.id, elapsed)
                Log.i(tag, "loaded in ${elapsed}ms, totalPss=${pss / 1024}MB")
            } catch (t: Throwable) {
                Log.e(tag, "load failed", t)
                _state.value = EngineState.Failed(
                    descriptor.id,
                    t.message ?: t::class.java.simpleName,
                )
            }
        }
    }

    override suspend fun transcribe(pcm: FloatArray, sampleRate: Int): String =
        mutex.withLock {
            val r = recognizer ?: error("Model is not loaded")
            withContext(Dispatchers.Default) {
                val started = System.currentTimeMillis()
                val stream = r.createStream()
                val text = try {
                    stream.acceptWaveform(pcm, sampleRate)
                    r.decode(stream)
                    r.getResult(stream).text
                } finally {
                    stream.release()
                }
                val pss = totalPssKb()
                _diagnostics.value = _diagnostics.value.copy(
                    lastTranscribeMs = System.currentTimeMillis() - started,
                    lastAudioSeconds = pcm.size.toFloat() / sampleRate,
                    peakPssKb = maxOf(pss, _diagnostics.value.peakPssKb ?: 0),
                )
                text.trim()
            }
        }

    override fun unload() {
        releaseLocked()
        _state.value = EngineState.Unloaded
    }

    private fun releaseLocked() {
        recognizer?.release()
        recognizer = null
        loadedId = null
    }

    private fun totalPssKb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss
    }
}
