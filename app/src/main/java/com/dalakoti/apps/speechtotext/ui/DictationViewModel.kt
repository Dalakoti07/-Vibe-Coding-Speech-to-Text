package com.dalakoti.apps.speechtotext.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dalakoti.apps.speechtotext.AppContainer
import com.dalakoti.apps.speechtotext.core.asr.EngineDiagnostics
import com.dalakoti.apps.speechtotext.core.asr.ModelLocator
import com.dalakoti.apps.speechtotext.core.audio.CaptureSource
import com.dalakoti.apps.speechtotext.core.model.EngineState
import com.dalakoti.apps.speechtotext.core.model.ModelDescriptor
import com.dalakoti.apps.speechtotext.core.model.ScanResult
import com.dalakoti.apps.speechtotext.core.model.StorageAccess
import com.dalakoti.apps.speechtotext.core.model.Transcript
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** What the dictation screen is doing right now. */
sealed interface DictationPhase {
    data object Idle : DictationPhase
    data object Recording : DictationPhase
    data object Transcribing : DictationPhase
    data class Error(val message: String) : DictationPhase
}

const val MAX_RECORDING_MS = 120_000L

/** Under a third of a second is a stray tap, not speech. */
private const val MIN_SAMPLES = 16_000 / 3

class DictationViewModel(
    app: Application,
    private val container: AppContainer,
) : AndroidViewModel(app) {

    private val tag = "Dictation"

    val engineState: StateFlow<EngineState> = container.engine.state
    val diagnostics: StateFlow<EngineDiagnostics> = container.engine.diagnostics
    val level: StateFlow<Float> = container.recorder.level

    val history: StateFlow<List<Transcript>> = container.history.transcripts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _phase = MutableStateFlow<DictationPhase>(DictationPhase.Idle)
    val phase: StateFlow<DictationPhase> = _phase.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    private val _storage = MutableStateFlow<StorageAccess>(StorageAccess.NeedsAllFilesAccess)
    val storage: StateFlow<StorageAccess> = _storage.asStateFlow()

    private val _scan = MutableStateFlow<List<ScanResult>>(emptyList())
    val scan: StateFlow<List<ScanResult>> = _scan.asStateFlow()

    private val _modelRoot = MutableStateFlow(ModelLocator.DEFAULT_ROOT)
    val modelRoot: StateFlow<String> = _modelRoot.asStateFlow()

    private val _captureSource = MutableStateFlow(CaptureSource.VoiceRecognition)
    val captureSource: StateFlow<CaptureSource> = _captureSource.asStateFlow()

    private var recordJob: Job? = null

    init {
        viewModelScope.launch {
            _modelRoot.value = container.settings.modelRoot.first()
            _captureSource.value = container.settings.captureSource.first()
            refresh()
        }
    }

    /** Called on every ON_RESUME: the storage grant can change while we are alive. */
    fun refresh() {
        viewModelScope.launch {
            _storage.value = ModelLocator.storageAccess()
            if (_storage.value != StorageAccess.Granted) {
                _scan.value = emptyList()
                return@launch
            }
            val results = container.scanner.scan(File(_modelRoot.value))
            _scan.value = results

            val recognised = results.filterIsInstance<ScanResult.Recognised>()
            if (recognised.isEmpty()) return@launch

            val savedId = container.settings.activeModelId.first()
            val chosen = recognised.firstOrNull { it.descriptor.id == savedId }
                ?: recognised.first()

            // Warm up now, in the background, so the cold load never lands on a key press.
            if (engineState.value !is EngineState.Ready) {
                selectModel(chosen.descriptor)
            }
        }
    }

    fun selectModel(descriptor: ModelDescriptor) {
        viewModelScope.launch {
            container.settings.setActiveModelId(descriptor.id)
            container.engine.load(descriptor)
        }
    }

    fun setModelRoot(path: String) {
        viewModelScope.launch {
            container.settings.setModelRoot(path)
            _modelRoot.value = path
            refresh()
        }
    }

    fun setCaptureSource(source: CaptureSource) {
        viewModelScope.launch {
            container.settings.setCaptureSource(source)
            _captureSource.value = source
        }
    }

    fun startRecording() {
        if (_phase.value != DictationPhase.Idle && _phase.value !is DictationPhase.Error) return
        if (engineState.value !is EngineState.Ready) return

        _result.value = null
        _elapsedMs.value = 0
        _phase.value = DictationPhase.Recording

        Log.i(tag, "startRecording source=${_captureSource.value.label}")
        recordJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                val pcm = container.recorder.record(
                    source = _captureSource.value,
                    maxMillis = MAX_RECORDING_MS,
                    onElapsed = { _elapsedMs.value = it },
                )
                finish(pcm, System.currentTimeMillis() - startedAt)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(tag, "recording failed", t)
                _phase.value = DictationPhase.Error(t.message ?: "Recording failed")
            }
        }
    }

    /**
     * Ends the utterance.
     *
     * Deliberately not `recordJob.cancel()`: cancelling throws away the audio we just
     * collected, so the recorder gets a stop flag and returns its buffer normally.
     */
    fun stopRecording() {
        if (_phase.value != DictationPhase.Recording) return
        Log.i(tag, "stopRecording requested")
        container.recorder.requestStop()
    }

    private suspend fun finish(pcm: FloatArray, durationMs: Long) {
        Log.i(tag, "finish: ${pcm.size} samples, ${durationMs}ms")
        if (pcm.size < MIN_SAMPLES) {
            // Say so. A silent no-op here is what made the button feel broken.
            _phase.value = DictationPhase.Error("Too short — hold the button while you speak")
            return
        }
        _phase.value = DictationPhase.Transcribing
        try {
            val text = container.engine.transcribe(pcm, 16_000)
            if (text.isBlank()) {
                _phase.value = DictationPhase.Error("Nothing recognised — try again")
                return
            }
            // Persist before rendering: a transcript that flashes up and dies with the
            // process is worse than one that takes 20 ms longer to appear.
            container.history.add(
                Transcript(
                    text = text,
                    createdAt = System.currentTimeMillis(),
                    durationMs = durationMs,
                    modelId = (engineState.value as? EngineState.Ready)?.modelId.orEmpty(),
                )
            )
            _result.value = text
            _phase.value = DictationPhase.Idle
        } catch (t: Throwable) {
            _phase.value = DictationPhase.Error(t.message ?: "Transcription failed")
        }
    }

    fun clearError() {
        if (_phase.value is DictationPhase.Error) _phase.value = DictationPhase.Idle
    }

    fun clearHistory() {
        viewModelScope.launch { container.history.clear() }
    }

    companion object {
        fun factory(app: Application, container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DictationViewModel(app, container) as T
        }
    }
}
