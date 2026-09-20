package com.dalakoti.apps.speechtotext

import android.app.Application
import com.dalakoti.apps.speechtotext.core.asr.AsrEngine
import com.dalakoti.apps.speechtotext.core.asr.ModelScanner
import com.dalakoti.apps.speechtotext.core.asr.SherpaOfflineAsrEngine
import com.dalakoti.apps.speechtotext.core.audio.AudioRecorder
import com.dalakoti.apps.speechtotext.core.data.HistoryStore
import com.dalakoti.apps.speechtotext.core.data.SettingsStore

/**
 * Hand-wired container instead of Hilt.
 *
 * Hilt was in the plan partly because a custom keyboard's InputMethodService needs
 * @AndroidEntryPoint. With the IME out of scope that argument is gone, and this is the
 * whole dependency graph — a singleton engine and three stores.
 *
 * The engine being a true singleton is load-bearing, not stylistic: two instances means
 * two ~600 MB encoders and an OOM kill.
 */
class AppContainer(application: Application) {
    val engine: AsrEngine = SherpaOfflineAsrEngine()
    val scanner = ModelScanner()
    val recorder = AudioRecorder()
    val settings = SettingsStore(application)
    val history = HistoryStore(application)
}

class SpeechToTextApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            // Better to pay the reload than to be the thing the OS kills.
            container.engine.unload()
        }
    }
}
