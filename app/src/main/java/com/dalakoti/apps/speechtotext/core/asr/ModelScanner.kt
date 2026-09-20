package com.dalakoti.apps.speechtotext.core.asr

import com.dalakoti.apps.speechtotext.core.model.ModelDescriptor
import com.dalakoti.apps.speechtotext.core.model.ScanResult
import com.dalakoti.apps.speechtotext.core.model.Tuning
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Optional descriptor a model directory may carry, for exports whose filenames differ. */
@Serializable
private data class ModelJson(
    val schema: Int = 1,
    val id: String? = null,
    @SerialName("displayName") val displayName: String? = null,
    val recipe: String? = null,
    val language: String? = null,
    val tuning: Tuning = Tuning(),
)

/**
 * Reads the model root and reports on every child directory.
 *
 * A directory is never dropped silently: anything we cannot use comes back as
 * [ScanResult.Unrecognised] carrying the reason, so the Settings screen can say what is
 * wrong instead of showing an empty list.
 */
class ModelScanner {

    private val json = Json { ignoreUnknownKeys = true }

    /** Encoder files are hundreds of MB; anything smaller is a truncated copy. */
    private val minEncoderBytes = 100L * 1024 * 1024

    fun scan(root: File): List<ScanResult> {
        if (!root.exists()) return emptyList()
        val children = root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }
            ?: return emptyList()
        return children.map { inspect(it) }
    }

    fun inspect(dir: File): ScanResult {
        if (!dir.canRead()) {
            return ScanResult.Unrecognised(dir.name, "Directory is not readable")
        }

        val declared = readModelJson(dir)
        val recipe = declared?.recipe?.let { ModelRecipes.forId(it) }
            ?: ModelRecipes.detect(dir)

        if (recipe == null) {
            val present = dir.listFiles()?.map { it.name }?.sorted().orEmpty()
            val reason = if (declared?.recipe != null) {
                "model.json names an unknown recipe \"${declared.recipe}\""
            } else {
                "No recipe matched. Found: ${present.take(6).joinToString().ifEmpty { "nothing" }}"
            }
            return ScanResult.Unrecognised(dir.name, reason)
        }

        recipe.requiredFiles.firstOrNull { File(dir, it).length() == 0L }?.let {
            return ScanResult.Unrecognised(dir.name, "Missing or empty: $it")
        }

        // An interrupted copy leaves a short file that still looks plausible.
        val encoder = recipe.requiredFiles.firstOrNull { it.startsWith("encoder") }
            ?.let { File(dir, it) }
        if (encoder != null && encoder.length() < minEncoderBytes) {
            return ScanResult.Unrecognised(
                dir.name,
                "Encoder is only ${encoder.length() / 1024 / 1024} MB — copy looks truncated",
            )
        }

        val tokens = File(dir, "tokens.txt")
        if (tokens.exists()) {
            val first = tokens.useLines { it.firstOrNull() }
            if (first.isNullOrBlank()) {
                return ScanResult.Unrecognised(dir.name, "tokens.txt is empty")
            }
        }

        return ScanResult.Recognised(
            ModelDescriptor(
                id = declared?.id ?: dir.name,
                displayName = declared?.displayName ?: prettify(dir.name),
                dir = dir,
                recipeId = recipe.id,
                language = declared?.language ?: "en",
                sizeBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                tuning = declared?.tuning ?: Tuning(),
            )
        )
    }

    private fun readModelJson(dir: File): ModelJson? {
        val f = File(dir, "model.json")
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<ModelJson>(f.readText()) }.getOrNull()
    }

    private fun prettify(dirName: String): String = dirName
        .removePrefix("sherpa-onnx-")
        .removePrefix("nemo-")
        .split('-', '_')
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
