# Parakeet Dictation — Implementation Plan

**Repo:** `SpeechToText2` · `com.dalakoti.apps.speechtotext`
**Device:** OnePlus 9R (SD870, Android 14, ColorOS)
**Status:** plan only — no implementation yet

---

## 0. What changed from the original build plan

Source: artifact *"Parakeet Dictation — Android Build Plan"*
(`8641d95d-deda-404d-8833-1d52b5f9c295`). The link you pasted
(`HaZbPgZDWdjJkR7eMrKeMz`) is not readable from this session; the artifact above is the
only Parakeet build plan in your artifact list and matches this repo, so it is the one
this plan supersedes. Say the word if it was a different document.

Two scope changes from you:

**Cut — no server.** The model is never downloaded. It is already in the phone's
storage. The app is pointed at a directory, loads from there, done.

**Add — model-agnostic.** You want to drop a better set of weights into storage later
and have the app pick it up. Recording, buffering and result handling stay identical.
Target model for now: **`parakeet-unified-en-0.6b` int8 non-streaming**.

### What the cut deletes outright

| Deleted | Was in phase |
|---|---|
| HTTPS server, Let's Encrypt, nginx `Accept-Ranges` | 3 |
| `manifest.json` served from your Linux box | 3 |
| WorkManager `CoroutineWorker` download | 3 |
| OkHttp + `Range` header resume | 3 |
| `.tmp` dir + atomic rename after verify | 3 |
| `NetworkType.UNMETERED` policy + override toggle | 3 |
| Download progress UI | 3 |
| **`android.permission.INTERNET`** | manifest |

That last line is worth stating plainly: **this app ships with no network permission at
all.** A dictation app that physically cannot exfiltrate audio is a better product than
one that promises not to.

### Explicitly out of scope

Named here so they stop re-entering the plan by accident:

| Out | Note |
|---|---|
| **IME / custom keyboard** | Dropped entirely. Not deferred, not gated — out. |
| `RecognitionService` | Same. |
| **Automatic copy to clipboard** | The app never writes to the clipboard on its own. |
| Server, download, model versioning | The original cut. |
| VAD / continuous listening | Press-to-talk makes it unnecessary — see Phase 3. |

The clipboard is not gone, only demoted: there is an explicit **Copy to clipboard**
action the user presses. The distinction that matters is that transcripts live in the
app, and the clipboard is a thing you opt into per transcript.

### What the cut adds

- A model directory the app does not own: it can be missing, half-copied, or the wrong model.
- Validation on every cold start, not once at install.
- A picker + persistence for *which* model is active.
- A recipe layer so a new weights folder is a folder, not a rewrite.

---

## 1. Facts verified against source

The original plan listed four things it could not confirm. All four are now resolved,
read from `k2-fsa/sherpa-onnx@master`.

| Question | Answer | Evidence |
|---|---|---|
| Is there a Maven artifact? | **No.** Vendor the Kotlin files, drop in the `.so` | `build-sherpa-onnx.html` documents tarball + manual copy only |
| Is `assetManager` nullable? | **Yes**, `AssetManager? = null`, and `null` selects `newFromFile(config)` | `kotlin-api/OfflineRecognizer.kt:180-190` |
| Is `modelType` `"nemo_transducer"`? | **Yes**, for both parakeet models | `OfflineRecognizer.kt` case `30` and case `62` |
| Bullet-point cause | Per-VAD-segment results appended to a list and rendered in a `LazyColumn` | confirmed by design, not line number — irrelevant now, see Phase 3 |

Two more, pinned while I was in there:

- `OfflineStream.acceptWaveform(samples: FloatArray, sampleRate: Int)` — exact signature.
- `WaveReader.readWave(filename: String)` — there **is** a filesystem overload alongside
  the asset one. Use it for the Phase 1 spike.

### Pinned versions and URLs

```
sherpa-onnx runtime   v1.13.8
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-android.tar.bz2   (46.1 MB)

model (target)
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2   (501.4 MB)

model (fallback / A-B comparison)
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2   (482.5 MB)
```

Streaming variants of the unified model exist (`-streaming-240ms`, `-560ms`, `-1120ms`,
all 501.4 MB). They need `OnlineRecognizer`, a different class. Out of scope for v1 but
the recipe layer in Phase 2 is shaped so adding one is a new recipe, not a refactor.

### The two target models are config-identical

```kotlin
// case 30 — sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8
// case 62 — sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming
OfflineModelConfig(
    transducer = OfflineTransducerModelConfig(
        encoder = "$modelDir/encoder.int8.onnx",
        decoder = "$modelDir/decoder.int8.onnx",
        joiner  = "$modelDir/joiner.int8.onnx",
    ),
    tokens    = "$modelDir/tokens.txt",
    modelType = "nemo_transducer",
)
```

Byte-for-byte the same shape. Swapping between them is a directory swap with zero code
change — which is exactly the property you asked for, and the reason the recipe
abstraction is cheap to build.

---

## 2. The one hard constraint

`OfflineRecognizer(assetManager = null, config)` routes to `newFromFile`, which hands
your strings to C++ `std::ifstream`/`fopen`. It therefore **cannot** read:

- a SAF `content://` URI,
- a `ParcelFileDescriptor`,
- anything that is not an absolute path readable by the app's uid.

Every design decision below falls out of that sentence.

### Where the weights live — three options

| | Location | Permission | Copy? | Survives uninstall | Verdict |
|---|---|---|---|---|---|
| A | `getExternalFilesDir(null)/models/` | none | no | no | Rejected — models die on uninstall |
| **B** | Anywhere on `/sdcard`, e.g. `/sdcard/Models/` | `MANAGE_EXTERNAL_STORAGE` | no | **yes** | **Chosen** |
| C | SAF pick → copy into `filesDir` | picker grant | **yes, ~600 MB** | no | Last resort |

**Decision: option B.** The models live in ordinary shared storage and outlive every
install, uninstall and *clear data* you will do across Phases 1–4 — which, given you are
about to reinstall this app a hundred times, is the property that actually matters.

Two upsides beyond survival, both worth naming:

- **You are not tied to `adb`.** `/sdcard/Models/` is visible to the ColorOS file manager
  and over MTP, so dropping in a new model is drag-and-drop. `Android/data/` is
  restricted in both on modern OnePlus builds.
- **One model tree, any number of apps.** Nothing else reads it today, but a second
  process never needs a second 600 MB copy.

The cost is a one-time **All files access** grant, and the fact that Google Play will not
accept `MANAGE_EXTERNAL_STORAGE` for this use case. Both are fine for a personal
sideloaded app. If you ever want to ship it, option C is the migration path, and the
`ModelLocator` interface in Phase 2 is what makes that a swap rather than a rewrite.

### On-device layout

```
/sdcard/Models/                              ← default root, user-overridable
├── sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming/
│   ├── encoder.int8.onnx
│   ├── decoder.int8.onnx
│   ├── joiner.int8.onnx
│   ├── tokens.txt
│   └── model.json          ← optional descriptor, see Phase 2
└── sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/
    └── …
```

One directory per model. The app scans the root, lists every child directory a recipe
recognises, and you pick the active one in Settings. **That is the whole "swap in better
weights later" story.**

The root is a persisted absolute path, not a hard-coded one — `/sdcard/Models` is only
the default offered on first run.

### Getting them there

```bash
# one-time, per model
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/\
sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2
tar xf sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2

adb shell mkdir -p /sdcard/Models
adb push sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming /sdcard/Models/
adb shell ls -la /sdcard/Models/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming
```

No dependency on the app having launched first — the directory is yours, not the app's.
Expect 2–5 minutes over USB, or use MTP / the phone's own file manager instead.

---

## 3. Phases

Ordered so the assumption that can kill the project dies first.

| # | Phase | Effort | Ships? |
|---|---|---|---|
| 0 | Project prep — deps, ABI, vendored runtime | ~2 h | no |
| 1 | Spike — load from an absolute path, transcribe a WAV | ~½ day | throwaway |
| 2 | Model layer — scan, recipes, validate, engine lifecycle | ~1 day | no UI yet |
| 3 | Dictation — the app you actually use | ~2–3 days | **yes, this is the deliverable** |
| 4 | Hardening — foreground service, ColorOS, R8, memory | ~1–2 days | yes |


---

## Phase 0 — Project prep

`~2 h` · no app behaviour changes

### Fetch and vendor the runtime

```bash
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-android.tar.bz2
tar xf sherpa-onnx-v1.13.8-android.tar.bz2
# → jniLibs/arm64-v8a/libsherpa-onnx-jni.so
# → jniLibs/arm64-v8a/libonnxruntime.so
```

Copy both into `app/src/main/jniLibs/arm64-v8a/` for Phase 1; they move to
`core/asr/src/main/jniLibs/arm64-v8a/` in Phase 2.

### Vendor the Kotlin API — exactly seven files

Pinned at `v1.13.8` from `sherpa-onnx/kotlin-api/`, into package
`com.k2fsa.sherpa.onnx`:

```
OfflineRecognizer.kt        (56 KB — carries every config data class)
OfflineStream.kt
FeatureConfig.kt
HomophoneReplacerConfig.kt
QnnConfig.kt
WaveReader.kt               (Phase 1 only; keep, it is 1.4 KB)
VersionInfo.kt              (optional, useful in a debug screen)
```

I checked the type closure: these seven reference no symbol they do not define. Nothing
else from `kotlin-api/` is needed. **Do not edit them** — treat as vendored, and record
the upstream tag in a `VENDORED.md` beside them so the next bump is a diff, not
archaeology.

`OfflineRecognizer.kt` carries a ~1,400-line `getOfflineModelConfig(type: Int)` you will
never call. Leave it — deleting it is churn, and it is a useful lookup table when you add
a recipe.

### `app/build.gradle.kts`

```kotlin
android {
    defaultConfig {
        ndk { abiFilters += "arm64-v8a" }   // never ship 32-bit: a 600 MB model
                                            // will not sit comfortably in a 32-bit
                                            // address space
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false      // load .so straight from the APK
        }
    }
}
```

### Manifest

```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<!-- deliberately absent: android.permission.INTERNET -->
```

`MANAGE_EXTERNAL_STORAGE` is **not** a runtime permission — `requestPermissions()` does
nothing for it. It is granted only from a Settings page you send the user to, and the
result arrives as a state change you re-check on resume, never as a callback. Budget for
that asymmetry now; it is the most common way this permission gets implemented wrong.

Current `minSdk 33` / `compileSdk 36` are both fine — keep them. `minSdk 33` removes
every legacy storage-permission branch except this one.

### Dependency additions — `gradle/libs.versions.toml`

| What | Why |
|---|---|
| Hilt + KSP | The `@Singleton` engine and `hiltViewModel()` — see the note below |
| DataStore Preferences | active model id, last-5 history |
| `kotlinx-serialization-json` | history payload + `model.json` |
| `lifecycle-viewmodel-compose` | `hiltViewModel()` |
| Compose BOM bump | current `2024.09.00` is a year stale |

**On Hilt.** It was originally in here partly because a custom keyboard's
`InputMethodService` would have needed `@AndroidEntryPoint`. With the IME out of scope
that argument is gone, and a hand-written `AppContainer` on `Application` holding the one
engine instance is ~15 lines with no KSP and no build-time cost. Hilt is still the
default here because it keeps ViewModel wiring tidy across five modules — but if you want
the smaller footprint, dropping it costs you nothing you will miss. Your call; nothing
else in the plan depends on it.

Let Android Studio resolve the versions. Do not copy numbers from this document.

**Not added:** OkHttp, Retrofit, WorkManager, Room. All four were only there for the
server. Room in particular is pure ceremony for five strings.

---

## Phase 1 — Spike

`~½ day` · throwaway code · no Compose, no Hilt, no modules

**Goal:** prove `OfflineRecognizer` reads a ~600 MB encoder from an absolute path outside
the APK and returns a correct transcript on the 9R.

**Why first:** every later phase assumes it. If it fails, the architecture changes, not
the code. Find out in half a day, not in week three.

### Steps

1. One `SpikeActivity`, one button, one `TextView`. No DI, no ViewModel.
2. Vendored files + `.so` in place from Phase 0.
3. Push the model per §2, **and grant All files access by hand**:
   `Settings → Apps → SpeechToText → Permissions → Files and media → Allow management of all files`.
   Also push one `test_wavs/*.wav` from the archive.
4. Configure against absolute paths:

```kotlin
// Spike only: hard-code the root and grant All files access by hand.
// The real permission + picker flow lands in Phase 2.
val root = File("/sdcard/Models")
val dir  = File(root, "sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming")

val config = OfflineRecognizerConfig(
    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
    modelConfig = OfflineModelConfig(
        transducer = OfflineTransducerModelConfig(
            encoder = File(dir, "encoder.int8.onnx").absolutePath,
            decoder = File(dir, "decoder.int8.onnx").absolutePath,
            joiner  = File(dir, "joiner.int8.onnx").absolutePath,
        ),
        tokens     = File(dir, "tokens.txt").absolutePath,
        modelType  = "nemo_transducer",
        numThreads = 4,
        provider   = "cpu",
    ),
    decodingMethod = "greedy_search",
)

// assetManager = null is the entire point of this spike:
// it routes the init block to newFromFile(config) instead of newFromAsset(...)
val recognizer = OfflineRecognizer(assetManager = null, config = config)

val wav = WaveReader.readWave(File(dir, "test.wav").absolutePath)
recognizer.createStream().use { stream ->
    stream.acceptWaveform(wav.samples, wav.sampleRate)
    recognizer.decode(stream)
    val text: String = recognizer.getResult(stream).text   // plain String. No list.
}
```

5. **Measure two numbers and write them down:**
   - wall-clock seconds from `OfflineRecognizer(...)` call to constructor return (cold load)
   - peak RSS: `adb shell dumpsys meminfo com.dalakoti.apps.speechtotext`

> ### ✅ Done test
> On the 9R, the app prints the correct transcript of a 5-second WAV with every model
> file read from the external files directory, and you have the cold-load time and peak
> RSS written down.

> ### 🛑 Stop conditions — reassess before Phase 2
> - `newFromFile` returns `0L` (surfaces as `IllegalArgumentException: Invalid
>   OfflineRecognizerConfig`) → check `Environment.isExternalStorageManager()` **first**.
>   Without the grant, paths still resolve and `File.exists()` quietly returns false, so a
>   missing permission looks exactly like a bad config. Log the permission state beside
>   every resolved path.
> - Peak RSS above ~1.8 GB → fall back to the tdt-0.6b-v2 int8 build, or a smaller model.
>   `android:largeHeap="true"` will **not** help: this is native allocation, the Java heap
>   cap does not apply to it.
> - Cold load above ~15 s → the idle-unload policy in Phase 4 becomes harmful; keep the
>   model resident instead and drop the unload timer.

---

## Phase 2 — The model layer

`~1 day` · no user-visible UI yet · this is where "generic" gets built

### Modules

Five, not fifteen. You are one person; over-modularising buys build config and nothing else.

```
app/                  MainActivity, nav, DictationScreen + ViewModel, SettingsScreen
core/asr/             AsrEngine, recipes, scanner, SherpaOfflineAsrEngine
                      + vendored com.k2fsa.sherpa.onnx  + jniLibs/arm64-v8a
core/audio/           AudioRecorder: AudioRecord → FloatArray
core/model/           pure Kotlin: Transcript, ModelDescriptor, DictationState
core/data/            SettingsStore, HistoryStore (DataStore Preferences)
```

Dependency direction is one-way: `app → core:*`, and `core:model` depends on nothing.
`core:asr` must not know `core:data` exists — the active-model id is passed *in*.

Design-system module: skip it. Fold theme into `app` until it hurts.

### The storage gate

Option B means the model root is only reachable once **All files access** is granted, and
that grant is nothing like a runtime permission. Model it as a first-class state, not a
try/catch:

```kotlin
// core/asr — ModelLocator owns "can I see the models, and where are they"
sealed interface StorageAccess {
    data object Granted : StorageAccess
    data object NeedsAllFilesAccess : StorageAccess   // not yet granted
    data object Unavailable : StorageAccess           // no Settings activity resolves
}

fun storageAccess(): StorageAccess =
    if (Environment.isExternalStorageManager()) StorageAccess.Granted
    else StorageAccess.NeedsAllFilesAccess

// Sending the user to grant it. There is no permission callback — re-check on resume.
val intent = Intent(
    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
    "package:$packageName".toUri(),
)
```

Three rules that matter:

1. **Re-check on every `ON_RESUME`.** The grant can be revoked from Settings while your
   process is alive, and you get no callback either way. A `LifecycleEventObserver` that
   re-runs `storageAccess()` and re-scans is the whole mechanism.
2. **Never construct a recognizer without `Granted`.** Without the grant, paths resolve
   fine and `File.exists()` quietly returns `false` — indistinguishable from a bad
   config, and you will lose an hour to it at least once.
3. **Have a fallback.** Some OEM builds do not resolve
   `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`. Guard with `resolveActivity()` and
   fall back to `Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` (the global list),
   then to on-screen instructions. ColorOS is exactly the kind of build that does this.

### Choosing the root

Default to `/sdcard/Models`, persist an override, and make picking one pleasant rather
than typing a path:

```kotlin
// ACTION_OPEN_DOCUMENT_TREE for the UX, then derive a real filesystem path
// content://com.android.externalstorage.documents/tree/primary%3AModels
//   → docId "primary:Models" → /storage/emulated/0/Models
fun treeUriToPath(uri: Uri): File? {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val (volume, rel) = docId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    if (volume != "primary") return null          // removable volume — see below
    return File(Environment.getExternalStorageDirectory(), rel)
}
```

The picker is for convenience only; the **path** is what gets persisted and what the
engine uses. If `treeUriToPath` returns `null` — a removable SD card, an unusual volume —
say so plainly and offer manual path entry rather than silently falling back. On the 9R
there is no SD slot, so `primary` is the only case you will actually hit.

### The recipe layer

This is the answer to "weights are one thing, code also needs to be added". Split the
two explicitly:

- **Weights** = a directory in storage. New weights of a known family: drop the folder in, restart, pick it. **Zero code.**
- **Recipe** = how a *family* of weights maps onto a sherpa config. New family (whisper, moonshine, sense-voice, streaming): **one new object**, ~15 lines.

```kotlin
// core/model — pure data, no Android
data class ModelDescriptor(
    val id: String,              // directory name
    val displayName: String,
    val dir: File,
    val recipeId: String,
    val language: String,
    val sizeBytes: Long,
)

// core/asr
interface ModelRecipe {
    val id: String
    val requiredFiles: List<String>
    fun matches(dir: File): Boolean =
        requiredFiles.all { File(dir, it).length() > 0L }
    fun buildConfig(dir: File, tuning: Tuning): OfflineRecognizerConfig
}

object NemoOfflineTransducerRecipe : ModelRecipe {
    override val id = "nemo_offline_transducer"
    override val requiredFiles = listOf(
        "encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt",
    )
    override fun buildConfig(dir: File, tuning: Tuning) = OfflineRecognizerConfig(
        featConfig = FeatureConfig(tuning.sampleRate, tuning.featureDim),
        modelConfig = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = File(dir, "encoder.int8.onnx").absolutePath,
                decoder = File(dir, "decoder.int8.onnx").absolutePath,
                joiner  = File(dir, "joiner.int8.onnx").absolutePath,
            ),
            tokens     = File(dir, "tokens.txt").absolutePath,
            modelType  = "nemo_transducer",
            numThreads = tuning.numThreads,
        ),
        decodingMethod = tuning.decodingMethod,
    )
}

object ModelRecipes {
    val all = listOf(NemoOfflineTransducerRecipe /*, WhisperRecipe, … */)
    fun forId(id: String) = all.firstOrNull { it.id == id }
    fun detect(dir: File) = all.firstOrNull { it.matches(dir) }
}
```

Both target models satisfy `NemoOfflineTransducerRecipe` unchanged. So does
`parakeet-tdt-0.6b-v3-int8`, and any future NeMo transducer export that keeps the
sherpa-onnx file naming. **That covers the realistic upgrade path with no code at all.**

### `model.json` — the escape hatch

A model whose filenames differ (`encoder.onnx` instead of `encoder.int8.onnx`, say)
should not need a rebuild. Support an optional descriptor in the model directory that
overrides detection:

```json
{
  "schema": 1,
  "id": "parakeet-unified-en-0.6b-int8",
  "displayName": "Parakeet Unified EN 0.6B (int8)",
  "recipe": "nemo_offline_transducer",
  "language": "en",
  "tuning": { "sampleRate": 16000, "featureDim": 80, "numThreads": 4,
              "decodingMethod": "greedy_search" },
  "files": { "encoder": "encoder.int8.onnx", "decoder": "decoder.int8.onnx",
             "joiner": "joiner.int8.onnx",  "tokens":  "tokens.txt" }
}
```

Resolution order: `model.json` if present → otherwise `ModelRecipes.detect(dir)` →
otherwise the directory is listed as **Unrecognised** with the reason, never silently
skipped. A folder that does not show up with no explanation is the worst possible
failure mode here.

### Scanner and validation

```kotlin
class ModelScanner(private val root: File) {
    fun scan(): List<ScanResult>   // Recognised(descriptor) | Unrecognised(dir, reason)
}
```

Validate before constructing a recognizer — the engine must never be handed a
half-pushed file:

1. Directory exists and is readable.
2. Every file the recipe requires exists.
3. Encoder is `> 100 MB` — catches an interrupted `adb push`, which leaves a
   plausible-looking short file.
4. `tokens.txt` is non-empty and its first line parses as `<token> <id>`.
5. **Optional** SHA-256, off by default. Hashing 600 MB costs ~8 s on this device.
   Offer it as a "Verify" button in Settings and cache the result keyed by
   `(path, size, lastModified)`.

### Engine lifecycle

```kotlin
interface AsrEngine {
    val state: StateFlow<EngineState>          // Unloaded | Loading | Ready(id) | Failed(msg)
    suspend fun load(descriptor: ModelDescriptor)
    suspend fun transcribe(pcm: FloatArray, sampleRate: Int): String
    fun unload()
}
```

Rules, each of which exists because of a specific failure:

- **`@Singleton`.** Two instances = two 600 MB encoders = OOM kill.
- **`Mutex`-guarded `load()`.** Two rapid switches must not both allocate.
- **`unload()` before loading a different model.** Not after. Never hold both.
- **Warm up at app launch** in a background coroutine, showing a *Loading model* state.
  The 3–8 s cold load must never happen after the user presses record. This single
  decision is the difference between "fast" and "broken".
- **Keep the interface.** `OfflineModelConfig` exposes `whisper`, `moonshine`,
  `senseVoice` as sibling fields — model-swapping stays a recipe, not a rewrite.

> ### ✅ Done test
> With two model directories pushed, a debug screen lists both with correct names and
> sizes; selecting either loads it and transcribes the test WAV; a directory with its
> `tokens.txt` deleted is listed as Unrecognised with the reason, and does not crash
> anything.

---

## Phase 3 — The app you actually use

`~2–3 days` · **this is the deliverable**

Press, speak, release, get one continuous paragraph on screen. It is saved. The last five
are there when you come back. Copying is a button you press, never something that happens
to you. Resist every feature idea until this is the thing you reach for.

### Audio capture — `core/audio`

Format is dictated by the model and is not negotiable:

```kotlin
AudioRecord(
    MediaRecorder.AudioSource.VOICE_RECOGNITION,
    16000,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize,
)
// then: short / 32768f  →  FloatArray
```

Any other sample rate silently wrecks accuracy rather than erroring — so assert 16 000 in
exactly one place and never let it be a parameter.

`VOICE_RECOGNITION` over `MIC`: it disables the aggressive ColorOS voice processing that
hurts ASR.

Memory is a non-issue: 16 kHz mono float is 64 KB/s. Two minutes is 7.7 MB. Buffer the
whole utterance in RAM.

### The bullet-point fix

The demo was not emitting a list — the model returns a plain `String`. The demo collected
one result per VAD segment into a list and rendered it in a `LazyColumn`, so every pause
looked like a new bullet.

**Single-shot is the fix, and it is free here.** You already have an explicit end signal:
the user lifting their finger. Buffer press-to-release, run the recognizer once, emit one
string. No VAD, no segmentation, no stitching — and slightly *better* accuracy, because
the model sees full sentence context.

So: **no Silero VAD in v1.** It was only needed for continuous listening.

If you later want live partials, never append to a list:

```kotlin
data class Dictation(
    val settled: String = "",   // finalised, already joined
    val partial: String = "",   // in-flight guess, replaced each tick
) {
    val display: String get() = (settled + " " + partial).trim()
}
```

Render `display` in a single `Text`. The moment a `LazyColumn` of results appears, you
are back to bullets.

### State machine

```
NoModel ──select──▶ Loading ──▶ Ready ──press──▶ Recording ──release──▶ Transcribing ──▶ Ready
                       │                                                      │
                       └──────────────── Failed(reason) ◀─────────────────────┘
```

`NoModel` is a first-class state with a real screen: the expected path, the exact `adb
push` command, and a Rescan button. It is the state you will hit on every clean install,
so it deserves better than a toast.

### Screens

- **Dictation** — big press-and-hold button, elapsed timer, the result in one `Text`, and a **Copy to clipboard** button beneath it.
- **History** — the last five transcripts, newest first, each with its own **Copy to clipboard** action. A `LazyColumn` is correct here: it is a list of transcripts, not a list of segments.
- **Settings** — active model picker, rescan, verify hashes, model path, cold-load time.

### Storage — the app owns the transcript

The transcript's home is the app. Nothing leaves it unless you press something.

- **On transcription finishing: write to the store, then render.** That is the whole
  success path. No clipboard write, no share sheet, no toast.
- **DataStore Preferences, not Room.** Five bounded items, no queries, no joins, no
  migrations.
- The ring buffer is one line: `(listOf(new) + old).take(5)`, stored as a
  `kotlinx.serialization` JSON string.
- Persist *before* rendering, not after. A transcript that appeared on screen and then
  vanished with the process is worse than one that took 20 ms longer to show.

### Copy to clipboard — explicit, per transcript

One action, labelled exactly **Copy to clipboard**, on the current result and on every
history row. It is the only code path in the app that touches `ClipboardManager`.

```kotlin
fun copyToClipboard(context: Context, text: String) {
    val clip = ClipData.newPlainText("Transcript", text).apply {
        description.extras = PersistableBundle().apply {
            // dictated text can contain anything — suppress the clipboard preview
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
```

- **No "Copied!" toast.** Android 13+ shows its own confirmation. Yours makes two.
- **Do not pre-emptively copy** "so it's ready" — silently taking over the user's
  clipboard is exactly the behaviour being removed here.
- Keep it one function in one place. When the clipboard question reopens later, there is a
  single call site to change.

### Guards

- Cap a single recording at **120 s** with a visible countdown past 90 s. Long audio is where transducer memory grows unpredictably.
- `RECORD_AUDIO` denied → a state, not a crash.
- Press while `Loading` → queue the press, do not drop it.

> ### ✅ Done test
> On the 9R: press, speak 15 seconds, release — one continuous paragraph appears within
> ~3 s, **no bullets**. Nothing reached the clipboard on its own — check it still holds
> whatever you copied before opening the app. Press **Copy to clipboard**, then paste
> into WhatsApp and get the transcript. Force-stop, reopen: the last five
> transcripts still there, model warms in the background, first press after warm-up is
> instant.

---

## Phase 4 — Hardening

`~1–2 days`

### Survive ColorOS

- **Foreground service while capturing.** On API 34+ declare
  `android.permission.FOREGROUND_SERVICE_MICROPHONE` and
  `android:foregroundServiceType="microphone"`. Without it the hans freezer will freeze
  you mid-sentence.
- `POST_NOTIFICATIONS` at first record (required on 33+ for the service notification).
- **Battery exemption.** Prompt with `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, plus an
  onboarding note telling you to turn off *Optimise battery usage* in ColorOS settings.
  The system prompt alone is not enough on OnePlus.

### Memory

- **Idle unload after ~60 s**, re-warm on resume. Holding 1.5 GB indefinitely makes you
  the first thing the OS kills. Skip this if Phase 1 measured a cold load above ~15 s —
  then residency is cheaper than the reload.
- `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` → unload immediately.
- Never hold two engines across a model switch.

### Release build

- R8 on. **Keep rules for the JNI surface** — native code resolves these by name, and R8
  strips them silently:

```proguard
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { native <methods>; }
```

  `OfflineRecognizerResult` and every `*Config` data class are constructed *from* native
  code. Field names matter. Keep them whole.

- **Test the release build, not just debug.** This class of failure never appears in debug.
- **Exclude history from Auto Backup.** `dataExtractionRules` + `backup_rules` excluding
  the DataStore file, or your last five transcripts sync to Google Drive.
- Confirm the release APK is **under ~20 MB** — `libonnxruntime.so` is most of it.
  If it is 600 MB, a model ended up in `assets/`.

> ### ✅ Done test
> Release APK, model pushed, Wi-Fi and mobile data both off (proving no network path
> exists). Dictate a 60-second passage with the screen off after 10 seconds — no dropped
> words. `dumpsys meminfo` shows the model released 60 s after the last use and reloaded
> on resume.

---

## Risk ledger

Re-scored for the cut scope. Three of the original eight risks were download risks and
are now gone.

| Sev | Risk | Mitigation | Phase |
|---|---|---|---|
| **HIGH** | `newFromFile` rejects the config and the failure mode is an opaque `ptr == 0L` | Phase 1 exists solely to hit this in half a day; log `isExternalStorageManager()` beside every resolved path | 1 |
| **HIGH** | OOM from 1.2–1.6 GB peak, or two engines alive across a model switch | `@Singleton`, mutex-guarded load, unload-before-load, idle unload, measure in Phase 1 | 1–4 |
| **MED** | 3–8 s cold load reads as "Parakeet is slow" | Warm at app open, never after the record press | 2–3 |
| **MED** | All files access revoked from Settings while the process is alive — no callback fires | Re-check `isExternalStorageManager()` on every `ON_RESUME`; `NeedsAllFilesAccess` is a real screen | 2 |
| **MED** | Model directory moved or deleted by a cleaner app — shared storage is not yours alone | `NoModel` is a real screen with the path and the push command; re-validate every cold start | 2 |
| **LOW** | ColorOS does not resolve `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` | `resolveActivity()` guard, fall back to the global list, then to written instructions | 2 |
| **MED** | Interrupted `adb push` leaves a truncated encoder that looks valid | Size floor on the encoder; optional SHA-256 verify in Settings | 2 |
| **MED** | ColorOS hans freezer drops audio mid-sentence | Foreground service + battery exemption prompt | 4 |
| **MED** | Every dictation still ends in a manual copy-and-paste — more friction than the Gboard mic button | Accepted, not solved. The IME that would fix it is out of scope by your call; revisit if the friction bites | — |
| **LOW** | Future model does not match any recipe and is silently skipped | Unrecognised directories are listed with a reason; `model.json` override | 2 |
| **LOW** | Wrong `AudioRecord` sample rate degrades accuracy silently | Hard-code 16 kHz mono; assert in exactly one place | 3 |
| **LOW** | R8 strips sherpa JNI bindings in release | Keep rules; test the release build, not just debug | 4 |

---

## Decisions taken

| Decision | Choice | Why |
|---|---|---|
| Model placement | `/sdcard/Models/` + `MANAGE_EXTERNAL_STORAGE` | Survives uninstall and `clear data`; drag-and-drop over MTP, not adb-only |
| Target model | `parakeet-unified-en-0.6b` int8 non-streaming | Your call; config-identical to `tdt-0.6b-v2`, so A/B costs nothing |
| VAD | None in v1 | Press-to-talk gives an explicit end signal; single-shot is more accurate and fixes the bullets |
| Persistence | DataStore, not Room | Five bounded strings |
| Network | No `INTERNET` permission | Falls out of the cut; keep it deliberately |
| Clipboard | Explicit **Copy to clipboard** only | The app never writes to the clipboard on its own; transcripts live in the app |
| Keyboard / IME | Out of scope | Your call — removed, not deferred |
| Modules | Five | One developer; more buys build config and nothing else |

Everything here is a recommendation I am comfortable defending; push back on any of it.

---

*References: [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) ·
[nvidia/parakeet-tdt-0.6b-v2](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2)*

---

## Implementation notes — deviations from this plan

Recorded as they happened, so the plan and the code do not drift apart silently.

| Planned | Built | Why |
|---|---|---|
| Five Gradle modules | **One module, five packages** (`core/asr`, `core/audio`, `core/data`, `core/model`, `ui`) | Same boundaries, none of the multi-module build config. Extracting modules later is a move-files refactor, not a redesign. |
| Hilt + KSP | **Hand-written `AppContainer`** on `Application` | With the IME out of scope, the whole graph is one engine and three stores — about fifteen lines. No KSP, no build-time cost. |
| Silero VAD | **Not built** | Press-to-talk gives an explicit end signal. |
| Foreground service (Phase 4) | **Not built yet** | Dictation is foreground-only today. Needed once recording must survive the screen going off. |
| `Environment.isExternalStorageManager()` state machine | **Built, simplified** | Re-checked on `ON_RESUME`, but there is no elaborate revocation UI — you said you would not revoke it. |

### Catalogue versions had to move backwards

The generated `libs.versions.toml` pinned `core-ktx 1.19.0`, `lifecycle 2.11.0` and
`activity-compose 1.13.0`, all of which demand **AGP 9.1+ / compileSdk 37**. Against AGP
8.13.2 the build fails before compiling a line. Pinned down to `core-ktx 1.15.0`,
`lifecycle 2.8.7`, `activity-compose 1.9.3`, `compose-bom 2024.12.01`.

### ColorOS will not let adb grant permissions

Worth knowing before you try to script a clean-install test:

```
adb shell pm grant … android.permission.RECORD_AUDIO
  → SecurityException: Neither user 2000 nor current process has GRANT_RUNTIME_PERMISSIONS

adb shell appops set … MANAGE_EXTERNAL_STORAGE allow
  → SecurityException: uid 2000 does not have MANAGE_APP_OPS_MODES
```

Stock Android allows both. OnePlus's build does not, so **both permissions must be
granted by hand on the device**. Any automated first-run test has to account for that.

### Audio: "highest quality" inside a fixed format

16 kHz mono is not a tuning knob — the model's feature extractor is built for it, and any
other rate degrades accuracy silently rather than failing. What *is* selectable is the
capture source, exposed in Settings:

| Source | Notes |
|---|---|
| `VOICE_RECOGNITION` (default) | Tuned for ASR: no aggressive AGC or noise suppression mangling the signal |
| `UNPROCESSED` | Raw microphone. Cleanest signal where supported, but quieter |
| `MIC` | Default source; may apply gain control that hurts accuracy |

The recorder also refuses to start if `AudioRecord` hands back a rate other than 16 kHz,
rather than quietly resampling.
