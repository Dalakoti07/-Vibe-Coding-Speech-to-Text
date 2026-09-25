# Parakeet

On-device speech-to-text for Android. Hold a button, speak, get one continuous
paragraph. The model runs entirely on the phone, the transcript stays in the app, and
the APK declares **no network permission at all**.

Built against [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) with NVIDIA's
[Parakeet](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2) weights.

| | |
|---|---|
| **Model** | `parakeet-unified-en-0.6b` · int8 · non-streaming |
| **Runtime** | sherpa-onnx v1.13.8 + ONNX Runtime, `arm64-v8a` |
| **Weights** | `/sdcard/Models/` — never downloaded, never bundled |
| **Language** | English |
| **Verified on** | OnePlus 9R (LE2101, Android 14 / ColorOS) · Galaxy S24 Ultra (SM-S928B, Android 16) |

### Measured, not estimated

| | |
|---|---|
| Cold model load | **2,598 – 3,028 ms** (9R) · **2,874 ms** (S24 Ultra) |
| Peak memory (total PSS) | **830 – 838 MB** (9R) · **1,173 MB** (S24 Ultra) |
| Encoder on disk | 624 MB |
| Release APK | 28 MB — `libonnxruntime.so` is 22 MB of it |

The build plan budgeted for 1.2–1.6 GB and set a 1.8 GB abort threshold. The real
figure is roughly half that, so none of the planned OOM mitigations were needed.

---

## Quick start

**1. Build and install**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**2. Put a model on the device**

```bash
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/\
sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2
tar xf sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2

adb shell mkdir -p /sdcard/Models
adb push sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming /sdcard/Models/
```

MTP or the phone's own file manager work just as well — the directory is yours, not
the app's, and it survives uninstalls.

**3. Grant two permissions, by hand**

- **All files access** — Settings → Apps → Parakeet → Permissions → Files and media
- **Microphone** — the app asks the first time you hold the button

> **ColorOS will not let `adb` do this for you** — Samsung will. On OnePlus builds both
> `pm grant … RECORD_AUDIO` and `appops set … MANAGE_EXTERNAL_STORAGE allow` fail with
> a `SecurityException` — the shell user lacks `GRANT_RUNTIME_PERMISSIONS` and
> `MANAGE_APP_OPS_MODES`. Stock Android allows both. Any automated first-run test has
> to account for this.

---

## Architecture

![Architecture](docs/images/architecture.png)

One Gradle module. The package boundaries are the module boundaries a future refactor
would use, so extracting real modules is a move-files job rather than a redesign.

| Package | Holds | Depends on |
|---|---|---|
| `ui` | Compose screens, `DictationViewModel` | everything below |
| `core/asr` | `AsrEngine`, `ModelRecipe`, `ModelScanner`, `ModelLocator` | `core/model`, vendored sherpa |
| `core/audio` | `AudioRecorder`, `CaptureSource` | — |
| `core/data` | `SettingsStore`, `HistoryStore`, `ClipboardWriter` | `core/model` |
| `core/model` | `Transcript`, `ModelDescriptor`, state types | **nothing** — pure Kotlin |
| `com.k2fsa.sherpa.onnx` | 7 vendored files, unmodified | JNI |

`core/asr` deliberately does not know `core/data` exists. The active model id is passed
*in*, so the engine never reaches for storage on its own.

---

## How one dictation works

![Dictation flow](docs/images/dictation-flow.png)

**One-shot, not streaming.** Press-to-talk already supplies an explicit end signal, so
the whole utterance is buffered and decoded once. The model sees full sentence context,
which is both more accurate and free of segment-stitching artefacts.

This is also the fix for the defect that motivated the project. The sherpa demo app
collects one result per VAD segment into a list and renders it in a `LazyColumn`, so
every pause looks like a new bullet. Here there is one result and one `Text`.

Audio is **16 kHz mono PCM16**, which is not a tuning knob — the feature extractor is
built for it, and any other rate degrades accuracy silently rather than failing. The
recorder refuses to start if `AudioRecord` hands back a different rate. What *is*
selectable, in Settings, is the capture source:

| Source | Notes |
|---|---|
| `VOICE_RECOGNITION` *(default)* | Tuned for ASR — no aggressive AGC or noise suppression mangling the signal |
| `UNPROCESSED` | Raw microphone. Cleanest where supported, but quieter |
| `MIC` | Default source; may apply gain control that hurts accuracy |

---

## Swapping in a better model

![Model resolution](docs/images/model-resolution.png)

The distinction that makes this cheap:

- **Weights are a directory.** A new export of a family already supported → drop the
  folder into `/sdcard/Models`, rescan, pick it. **Zero code.**
- **A recipe is how a family maps onto a sherpa config.** A genuinely new family —
  whisper, moonshine, streaming — is one object of roughly fifteen lines.

`parakeet-unified-en-0.6b` and `parakeet-tdt-0.6b-v2` produce a byte-identical
`OfflineModelConfig`, so switching between them really is a directory swap.

### Adding a family

```kotlin
object WhisperRecipe : ModelRecipe {
    override val id = "whisper"
    override val requiredFiles = listOf("encoder.onnx", "decoder.onnx", "tokens.txt")
    override fun buildConfig(dir: File, tuning: Tuning) = OfflineRecognizerConfig(/* … */)
}
// then add it to ModelRecipes.all
```

### `model.json` — when filenames differ

Drop this beside the weights to override detection without touching code:

```json
{
  "schema": 1,
  "id": "parakeet-unified-en-0.6b-int8",
  "displayName": "Parakeet Unified EN 0.6B (int8)",
  "recipe": "nemo_offline_transducer",
  "language": "en",
  "tuning": { "sampleRate": 16000, "featureDim": 80, "numThreads": 4,
              "decodingMethod": "greedy_search" }
}
```

A directory that matches nothing is **never dropped silently** — it appears in Settings
with the reason it was rejected. A folder that vanishes with no explanation is the worst
failure mode this design has.

---

## States

![State machine](docs/images/state-machine.png)

Every reachable failure is a state with a screen behind it, not a toast. `NoModel` in
particular carries the expected path and the `adb push` command, because it is the state
every clean install begins in.

---

## Architecture decisions

### Where the weights live

| Decision | `/sdcard/Models/` + `MANAGE_EXTERNAL_STORAGE` |
|---|---|
| **Why** | sherpa's `newFromFile` hands paths to C++ `fopen`. It cannot read a SAF `content://` URI or a file descriptor — it needs a real absolute path readable by the app's uid. That rules out the portable option outright. |
| **Why not app-private** | `getExternalFilesDir()` needs no permission and no copy, but uninstall wipes 600 MB, and `Android/data/` is unreachable from the ColorOS file manager and MTP. Shared storage survives every reinstall and is drag-and-drop. |
| **Why not a SAF import** | It works, but costs a ~600 MB copy and doubles disk use. It remains the migration path if this ever ships on Play, which will not accept `MANAGE_EXTERNAL_STORAGE` for this use case. |
| **Trade-off** | A one-time *All files access* grant, and no Play Store route. |

| Decision | The grant is a state, not a call |
|---|---|
| **Why** | `MANAGE_EXTERNAL_STORAGE` is not a runtime permission. `requestPermissions()` does nothing for it, it is granted from a Settings page, and **no callback fires**. Worse: without it, paths still resolve and `File.exists()` quietly returns `false` — indistinguishable from a bad sherpa config. |
| **How** | `ModelLocator.storageAccess()` is re-checked on every `ON_RESUME`, and the engine logs `isExternalStorageManager()` beside every resolved path before constructing a recognizer. |

| Decision | No `INTERNET` permission |
|---|---|
| **Why** | Fell out of dropping the download server, and then kept deliberately. An app that *cannot* exfiltrate audio is a stronger claim than one that promises not to. |

### Recognition

| Decision | Why | Trade-off |
|---|---|---|
| **One-shot, not streaming** | Press-to-talk gives an explicit end signal; full sentence context is more accurate and needs no stitching | No live partials. See [Known gaps](#known-gaps) |
| **No VAD** | Silero only existed to find utterance boundaries during continuous listening, which press-to-talk removes | No hands-free mode |
| **One `Text`, never a `LazyColumn`** | A list of per-segment results *is* the bullet-list bug | — |
| **Singleton engine, mutex-guarded** | Two instances means two ~600 MB encoders and an OOM kill | Model switches serialise |
| **Unload *before* load** | Never hold two models across a switch | A switch is not instant |
| **Warm at app launch** | The 3 s cold load must never land on the record press | Slight battery cost at startup |
| **`arm64-v8a` only** | A 624 MB model will not sit comfortably in a 32-bit address space | No 32-bit devices |

### Audio

| Decision | Why |
|---|---|
| **16 kHz mono, asserted in one place** | Fixed by the model. A wrong rate degrades accuracy *silently*, so it fails loudly instead |
| **Stop via a flag, never `job.cancel()`** | Cancellation propagates into the capture loop and throws away the audio the user just spoke. This was a real bug — the app recorded, then hung forever |
| **Hold *and* tap-to-toggle** | A quick tap is a real gesture, not a mistake. Holding records while held; tapping starts, and the next tap stops |
| **Read `recording` via `rememberUpdatedState`** | Keying `pointerInput` on it restarts the gesture detector the instant recording begins, cancelling the in-flight `tryAwaitRelease()` and losing the finger-lift. Also a real bug |

### Data and clipboard

| Decision | Why |
|---|---|
| **DataStore, not Room** | A hundred bounded transcripts and three settings. No queries, no joins, no migrations — nothing Room earns its keep on |
| **Persist before rendering** | A transcript that flashes up and dies with the process is worse than one that takes 20 ms longer to appear |
| **Clipboard only on an explicit press** | `ClipboardWriter` is the single code path touching `ClipboardManager`, and nothing calls it automatically. Silently owning the user's clipboard is a misfeature |
| **`EXTRA_IS_SENSITIVE` on copy** | Dictated text can contain anything; this suppresses the system clipboard preview |
| **No "Copied!" toast** | Android 13+ shows its own. Yours makes two |
| **History excluded from Auto Backup** | Otherwise your last hundred transcripts sync to Google Drive |

### Build and dependencies

| Decision | Why |
|---|---|
| **One module, five packages** | Same boundaries as the planned module split, none of the multi-module build config. Extracting modules later is a move-files refactor |
| **`AppContainer`, not Hilt** | The whole graph is one engine and three stores. Hilt was justified partly by a keyboard's `InputMethodService` needing `@AndroidEntryPoint`; with the IME out of scope that argument disappeared |
| **sherpa-onnx vendored, `.so` committed** | Upstream publishes no Maven artifact. The project does not build without the 27 MB of native libraries, so they are in the repo. Provenance in [`VENDORED.md`](app/src/main/java/com/k2fsa/sherpa/onnx/VENDORED.md) |
| **Catalogue versions pinned *back*** | The generated `core-ktx 1.19.0` / `lifecycle 2.11.0` / `activity-compose 1.13.0` all require AGP 9.1+. Against AGP 8.13.2 the build fails before compiling a line |
| **R8 keep rules for the JNI surface** | Native code resolves those classes and their fields by name; R8 strips them silently and only the release build breaks |

---

## Known gaps

| | Status |
|---|---|
| **Recipe collision with streaming models** | A streaming parakeet export has the *same four filenames* as the non-streaming one, so the scanner would claim it as offline. Fix is a 4 KB tail read of `decoder.int8.onnx` — the `nemo_parakeet_unified_streaming` marker sits in its last 53 bytes. Scheduled as v2.0.0 Phase 10 |
| **No foreground service** | Recording is foreground-only. ColorOS's freezer will cut it off if the screen sleeps mid-sentence |
| **No idle unload** | Measured memory made it unnecessary; revisit if a larger model lands |
| **Streaming recognition** | **Planned as v2.0.0.** Measured one-shot decode is over 4 s, which is what makes it worth a second engine. Phases 10–14 in [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md#v200--streaming-recognition) |
| **IME / keyboard** | Out of scope. Every dictation therefore ends in a manual copy-paste |

---

## Repository layout

```
app/src/main/
├── java/com/dalakoti/apps/speechtotext/
│   ├── SpeechToTextApp.kt        Application + AppContainer
│   ├── MainActivity.kt
│   ├── core/asr/                 engine, recipes, scanner, locator
│   ├── core/audio/               AudioRecorder, CaptureSource
│   ├── core/data/                stores, ClipboardWriter
│   ├── core/model/               pure Kotlin types
│   └── ui/                       Compose screens, ViewModel, theme
├── java/com/k2fsa/sherpa/onnx/   vendored, unmodified — see VENDORED.md
└── jniLibs/arm64-v8a/            libsherpa-onnx-jni.so, libonnxruntime.so

docs/
├── IMPLEMENTATION_PLAN.md        plan of record, deviations, measurements
├── RELEASING.md                  tag -> APK -> GitHub Release
├── README.md                     documentation index
└── images/                       architecture diagrams (PNG)

.github/workflows/release.yml     builds and publishes on a v* tag
scripts/release.sh                tags and pushes, with the checks you want
```

## Releasing

Push a tag; GitHub Actions builds the APK and publishes it to
[Releases](https://github.com/Dalakoti07/-Vibe-Coding-Speech-to-Text/releases). Nothing
else triggers a build.

```bash
./scripts/release.sh 1.0.0
```

Signing is optional — without secrets the APK is debug-signed but still installable. Full
detail, including keystore setup, in [`docs/RELEASING.md`](docs/RELEASING.md).

## Further reading

- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — the plan this was built
  from, including what was verified against sherpa-onnx source and where the build
  deviates from it
- [`docs/RELEASING.md`](docs/RELEASING.md) — how a tag becomes a GitHub Release, and how
  to add signing
- [`docs/README.md`](docs/README.md) — diagram index
