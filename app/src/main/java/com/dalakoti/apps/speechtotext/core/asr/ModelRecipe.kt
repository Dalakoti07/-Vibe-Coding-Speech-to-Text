package com.dalakoti.apps.speechtotext.core.asr

import com.dalakoti.apps.speechtotext.core.model.Tuning
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * How a *family* of weights maps onto a sherpa-onnx config.
 *
 * Weights are a directory: dropping in a new export of a family we already handle costs
 * no code. A genuinely new family — whisper, moonshine, a streaming transducer — is one
 * more object in [ModelRecipes.all].
 */
interface ModelRecipe {
    val id: String
    val displayName: String

    /** Files that must exist and be non-empty for this recipe to claim a directory. */
    val requiredFiles: List<String>

    fun matches(dir: File): Boolean = requiredFiles.all { File(dir, it).length() > 0L }

    fun buildConfig(dir: File, tuning: Tuning): OfflineRecognizerConfig
}

/**
 * Covers parakeet-unified-en-0.6b and parakeet-tdt-0.6b-v2/v3 — sherpa-onnx builds an
 * identical config for all of them (see getOfflineModelConfig cases 30 and 62).
 */
object NemoOfflineTransducerRecipe : ModelRecipe {
    override val id = "nemo_offline_transducer"
    override val displayName = "NeMo offline transducer"
    override val requiredFiles = listOf(
        "encoder.int8.onnx",
        "decoder.int8.onnx",
        "joiner.int8.onnx",
        "tokens.txt",
    )

    override fun buildConfig(dir: File, tuning: Tuning) = OfflineRecognizerConfig(
        featConfig = FeatureConfig(
            sampleRate = tuning.sampleRate,
            featureDim = tuning.featureDim,
        ),
        modelConfig = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = File(dir, "encoder.int8.onnx").absolutePath,
                decoder = File(dir, "decoder.int8.onnx").absolutePath,
                joiner = File(dir, "joiner.int8.onnx").absolutePath,
            ),
            tokens = File(dir, "tokens.txt").absolutePath,
            modelType = "nemo_transducer",
            numThreads = tuning.numThreads,
            provider = "cpu",
        ),
        decodingMethod = tuning.decodingMethod,
    )
}

/** Same family, non-quantised export. Listed second so int8 wins when both could match. */
object NemoOfflineTransducerFp32Recipe : ModelRecipe {
    override val id = "nemo_offline_transducer_fp32"
    override val displayName = "NeMo offline transducer (fp32/fp16)"
    override val requiredFiles = listOf(
        "encoder.onnx",
        "decoder.onnx",
        "joiner.onnx",
        "tokens.txt",
    )

    override fun buildConfig(dir: File, tuning: Tuning) = OfflineRecognizerConfig(
        featConfig = FeatureConfig(
            sampleRate = tuning.sampleRate,
            featureDim = tuning.featureDim,
        ),
        modelConfig = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = File(dir, "encoder.onnx").absolutePath,
                decoder = File(dir, "decoder.onnx").absolutePath,
                joiner = File(dir, "joiner.onnx").absolutePath,
            ),
            tokens = File(dir, "tokens.txt").absolutePath,
            modelType = "nemo_transducer",
            numThreads = tuning.numThreads,
            provider = "cpu",
        ),
        decodingMethod = tuning.decodingMethod,
    )
}

object ModelRecipes {
    val all: List<ModelRecipe> = listOf(
        NemoOfflineTransducerRecipe,
        NemoOfflineTransducerFp32Recipe,
    )

    fun forId(id: String): ModelRecipe? = all.firstOrNull { it.id == id }

    fun detect(dir: File): ModelRecipe? = all.firstOrNull { it.matches(dir) }
}
