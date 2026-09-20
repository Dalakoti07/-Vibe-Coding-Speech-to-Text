package com.dalakoti.apps.speechtotext.core.model

import kotlinx.serialization.Serializable
import java.io.File

/** A finished piece of dictation. Lives in the app; nothing leaves unless the user says so. */
@Serializable
data class Transcript(
    val text: String,
    val createdAt: Long,
    val durationMs: Long,
    val modelId: String,
)

/** Tuning knobs a recipe needs. Defaults match every NeMo parakeet export. */
@Serializable
data class Tuning(
    val sampleRate: Int = 16_000,
    val featureDim: Int = 80,
    val numThreads: Int = 4,
    val decodingMethod: String = "greedy_search",
)

/** A model directory we understood. */
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val dir: File,
    val recipeId: String,
    val language: String,
    val sizeBytes: Long,
    val tuning: Tuning = Tuning(),
)

/** The result of looking at one child directory of the model root. */
sealed interface ScanResult {
    val dirName: String

    data class Recognised(val descriptor: ModelDescriptor) : ScanResult {
        override val dirName get() = descriptor.id
    }

    /** Never skip a directory silently — always say why it was rejected. */
    data class Unrecognised(override val dirName: String, val reason: String) : ScanResult
}

sealed interface EngineState {
    data object Unloaded : EngineState
    data class Loading(val modelId: String) : EngineState
    data class Ready(val modelId: String, val coldLoadMs: Long) : EngineState
    data class Failed(val modelId: String, val message: String) : EngineState
}

sealed interface StorageAccess {
    data object Granted : StorageAccess
    data object NeedsAllFilesAccess : StorageAccess
}
