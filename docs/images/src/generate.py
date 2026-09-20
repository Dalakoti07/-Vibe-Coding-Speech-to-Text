# -*- coding: utf-8 -*-
"""Hand-built SVG architecture diagrams for the Parakeet dictation app."""
import os, html

# Writes the SVGs beside this file. Rasterise with:
#   rsvg-convert -z 2 -b "#F3F6F8" src/<name>.svg -o <name>.png
OUT = os.path.dirname(os.path.abspath(__file__))

INK      = "#0F171E"
MUTED    = "#5A6B78"
FAINT    = "#8A98A4"
RULE     = "#C4CFDA"
CANVAS   = "#F3F6F8"
SURFACE  = "#FFFFFF"
BLUE     = "#0D5B87"
BLUE_BG  = "#DCE9F2"
AMBER    = "#A6521C"
AMBER_BG = "#F6E7D8"
RED      = "#B23520"
RED_BG   = "#F7E2DE"
GREEN    = "#1A6A4C"
GREEN_BG = "#DCEDE5"

SANS = "Helvetica Neue, Helvetica, Arial, sans-serif"
MONO = "Menlo, DejaVu Sans Mono, monospace"

def esc(t): return html.escape(str(t), quote=False)

def header(w, h, title):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}" font-family="{SANS}">\n'
            f'<title>{esc(title)}</title>\n'
            f'<defs>\n'
            f'  <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
            f'markerHeight="7" orient="auto-start-reverse">'
            f'<path d="M0,0 L10,5 L0,10 z" fill="{MUTED}"/></marker>\n'
            f'  <marker id="arrowblue" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
            f'markerHeight="7" orient="auto-start-reverse">'
            f'<path d="M0,0 L10,5 L0,10 z" fill="{BLUE}"/></marker>\n'
            f'  <marker id="arrowamber" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
            f'markerHeight="7" orient="auto-start-reverse">'
            f'<path d="M0,0 L10,5 L0,10 z" fill="{AMBER}"/></marker>\n'
            f'</defs>\n'
            f'<rect width="{w}" height="{h}" fill="{CANVAS}"/>\n')

def text(x, y, s, size=13, fill=INK, weight="normal", anchor="start", mono=False, ls=None):
    fam = f' font-family="{MONO}"' if mono else ''
    l = f' letter-spacing="{ls}"' if ls else ''
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" font-weight="{weight}" '
            f'text-anchor="{anchor}"{fam}{l}>{esc(s)}</text>\n')

def box(x, y, w, h, fill=SURFACE, stroke=RULE, rx=8, sw=1, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{sw}"{d}/>\n')

def arrow(x1, y1, x2, y2, color=MUTED, marker="arrow", dash=None, sw=1.6):
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return (f'<path d="M{x1},{y1} L{x2},{y2}" stroke="{color}" stroke-width="{sw}" fill="none" '
            f'marker-end="url(#{marker})"{d}/>\n')

def label(x, y, s, size=10, fill=FAINT):
    return text(x, y, s.upper(), size=size, fill=fill, weight="bold", mono=True, ls="1.1")

def caption(w, y, s):
    return text(w/2, y, s, size=12, fill=MUTED, anchor="middle")


# ══════════════════ 1. Layered architecture ══════════════════
def layers():
    W, H = 1000, 700
    s = header(W, H, "Architecture layers")
    s += text(40, 44, "Architecture", size=22, weight="bold")
    s += text(40, 66, "One module, five packages. Every arrow points down; nothing points back up.",
              size=13, fill=MUTED)

    rows = [
        # (y, h, label, title, items, fill, stroke)
        (92,  74, "ui", None,
         ["DictationScreen", "HistoryScreen", "SettingsScreen"], SURFACE, RULE),
        (186, 66, "ui", "DictationViewModel",
         ["phase  ·  result  ·  engineState  ·  scan  ·  history"], SURFACE, RULE),
        (272, 66, "app", "AppContainer   (hand-wired, not Hilt)",
         ["one AsrEngine  ·  AudioRecorder  ·  SettingsStore  ·  HistoryStore"], BLUE_BG, BLUE),
    ]
    for (y, h, lab, title, items, fill, stroke) in rows:
        s += box(40, y, W-80, h, fill=fill, stroke=stroke)
        s += label(56, y+20, lab)
        if title:
            s += text(56, y+40, title, size=14, weight="bold")
            s += text(56, y+58, items[0], size=12, fill=MUTED, mono=True)
        else:
            cw = (W-80-32-24)/3
            for i, it in enumerate(items):
                s += text(56 + i*(cw+12), y+48, it, size=14, weight="bold")

    # core packages row
    y = 372
    s += label(56, y-8, "core")
    cols = [
        ("core/asr", ["AsrEngine", "ModelRecipe", "ModelScanner", "ModelLocator"], BLUE),
        ("core/audio", ["AudioRecorder", "CaptureSource", "16 kHz mono, fixed"], AMBER),
        ("core/data", ["SettingsStore", "HistoryStore", "ClipboardWriter"], GREEN),
        ("core/model", ["Transcript", "ModelDescriptor", "pure Kotlin, no Android"], MUTED),
    ]
    cw = (W-80-3*14)/4
    for i, (name, items, col) in enumerate(cols):
        x = 40 + i*(cw+14)
        s += box(x, y, cw, 104, fill=SURFACE, stroke=RULE)
        s += f'<rect x="{x}" y="{y}" width="{cw}" height="3" rx="1.5" fill="{col}"/>\n'
        s += text(x+14, y+28, name, size=13, weight="bold", mono=True)
        for j, it in enumerate(items):
            s += text(x+14, y+48+j*17, it, size=11.5,
                      fill=MUTED if j == len(items)-1 else INK)

    # vendored
    y = 502
    s += box(40, y, W-80, 58, fill=SURFACE, stroke=RULE, dash="5 4")
    s += label(56, y+20, "vendored · sherpa-onnx v1.13.8")
    s += text(56, y+42, "com.k2fsa.sherpa.onnx — 7 Kotlin files, unmodified", size=13, mono=True)
    s += text(W-56, y+42, "no Maven artifact upstream", size=11.5, fill=FAINT, anchor="end")

    # native
    y = 578
    s += box(40, y, W-80, 50, fill="#E6EDF2", stroke=RULE)
    s += text(56, y+31, "libsherpa-onnx-jni.so   +   libonnxruntime.so", size=13, mono=True)
    s += text(W-56, y+31, "arm64-v8a only", size=11.5, fill=FAINT, anchor="end")

    # storage
    y = 646
    s += box(40, y, W-80, 44, fill=RED_BG, stroke=RED)
    s += text(56, y+28, "/sdcard/Models/<model>/  encoder · decoder · joiner · tokens.txt",
              size=13, mono=True, fill=RED)

    for y1, y2 in [(166,186),(252,272),(338,372),(476,502),(560,578),(628,646)]:
        s += arrow(W/2, y1, W/2, y2)

    return W, H, s + "</svg>\n"


# ══════════════════ 2. Dictation flow ══════════════════
def flow():
    W, H = 1000, 560
    s = header(W, H, "Dictation flow")
    s += text(40, 44, "One dictation, end to end", size=22, weight="bold")
    s += text(40, 66, "One-shot, not streaming: the whole utterance is buffered, then decoded once.",
              size=13, fill=MUTED)

    steps = [
        (40,  110, 210, "1 · Press or hold", ["MicButton", "gesture → startRecording()"], AMBER),
        (290, 110, 210, "2 · Capture", ["AudioRecord @ 16 kHz mono", "PCM16 → FloatArray"], AMBER),
        (540, 110, 210, "3 · Release", ["stop flag, not cancel", "loop returns its buffer"], AMBER),
        (40,  266, 210, "4 · Decode", ["AsrEngine.transcribe()", "mutex-guarded, once"], BLUE),
        (290, 266, 210, "5 · Persist", ["HistoryStore.add()", "before rendering"], GREEN),
        (540, 266, 210, "6 · Render", ["a single Text", "never a LazyColumn"], GREEN),
        (790, 266, 170, "7 · Copy", ["only when pressed", "ClipboardWriter"], RED),
    ]
    for (x, y, w, title, lines, col) in steps:
        s += box(x, y, w, 96, fill=SURFACE, stroke=RULE)
        s += f'<rect x="{x}" y="{y}" width="4" height="96" rx="2" fill="{col}"/>\n'
        s += text(x+18, y+28, title, size=13, weight="bold")
        for j, ln in enumerate(lines):
            s += text(x+18, y+50+j*18, ln, size=11.5, fill=MUTED, mono=(j == 0))

    s += arrow(250, 158, 290, 158, color=AMBER, marker="arrowamber")
    s += arrow(500, 158, 540, 158, color=AMBER, marker="arrowamber")
    s += f'<path d="M645,206 L645,240 L145,240 L145,266" stroke="{BLUE}" stroke-width="1.6" fill="none" marker-end="url(#arrowblue)"/>\n'
    s += arrow(250, 314, 290, 314, color=BLUE, marker="arrowblue")
    s += arrow(500, 314, 540, 314, color=BLUE, marker="arrowblue")
    s += arrow(750, 314, 790, 314, color=MUTED, dash="4 4")

    # notes
    y = 410
    notes = [
        ("Why one shot", "Press-to-talk already gives an explicit end signal, so the model sees "
                         "full sentence context — more accurate, and no segment stitching."),
        ("Why the bullets are gone", "The sherpa demo collected one result per VAD segment into a "
                                     "list. Here there is one result and one Text."),
        ("Why the clipboard waits", "Step 7 is the only code path touching ClipboardManager, and "
                                    "nothing calls it automatically."),
    ]
    for i, (h_, b) in enumerate(notes):
        yy = y + i*44
        s += f'<path d="M40,{yy-12} L40,{yy+22}" stroke="{RULE}" stroke-width="2"/>\n'
        s += text(54, yy, h_, size=12, weight="bold")
        s += text(54, yy+18, b, size=11.5, fill=MUTED)
    return W, H, s + "</svg>\n"


# ══════════════════ 3. Model resolution ══════════════════
def resolution():
    W, H = 1000, 616
    s = header(W, H, "Model resolution")
    s += text(40, 44, "How weights become a loaded model", size=22, weight="bold")
    s += text(40, 66, "New weights of a known family cost no code. A new family costs one object.",
              size=13, fill=MUTED)

    s += box(40, 104, 260, 88, fill=RED_BG, stroke=RED)
    s += label(56, 124, "shared storage")
    s += text(56, 146, "/sdcard/Models/", size=13, mono=True, fill=RED)
    s += text(56, 168, "one directory per model", size=11.5, fill=MUTED)

    s += box(40, 232, 260, 100, fill=SURFACE, stroke=RULE)
    s += text(56, 258, "ModelLocator", size=13, weight="bold", mono=True)
    s += text(56, 280, "isExternalStorageManager()", size=11.5, mono=True, fill=MUTED)
    s += text(56, 300, "re-checked on every ON_RESUME —", size=11.5, fill=MUTED)
    s += text(56, 316, "there is no permission callback", size=11.5, fill=MUTED)

    s += box(360, 104, 280, 228, fill=SURFACE, stroke=BLUE)
    s += label(376, 126, "per directory")
    s += text(376, 150, "ModelScanner.inspect()", size=13, weight="bold", mono=True)
    checks = [
        "model.json present?  → recipe by id",
        "otherwise  → ModelRecipes.detect()",
        "",
        "all required files exist",
        "encoder > 100 MB  (truncated copy)",
        "tokens.txt parses",
    ]
    for j, c in enumerate(checks):
        if not c: continue
        s += text(376, 176+j*20, c, size=11.5, fill=MUTED, mono=True)
    s += f'<path d="M376,296 L620,296" stroke="{RULE}" stroke-width="1"/>\n'
    s += text(376, 318, "never skipped silently", size=11.5, fill=RED)

    s += box(700, 104, 260, 100, fill=GREEN_BG, stroke=GREEN)
    s += text(716, 130, "Recognised", size=13, weight="bold", fill=GREEN)
    s += text(716, 152, "ModelDescriptor", size=11.5, mono=True)
    s += text(716, 172, "id · recipe · size · tuning", size=11.5, fill=MUTED)
    s += text(716, 192, "selectable in Settings", size=11.5, fill=MUTED)

    s += box(700, 232, 260, 100, fill=SURFACE, stroke=RULE)
    s += text(716, 258, "Unrecognised", size=13, weight="bold", fill=MUTED)
    s += text(716, 280, "dirName + the reason", size=11.5, mono=True, fill=MUTED)
    s += text(716, 302, "shown on screen, so a", size=11.5, fill=MUTED)
    s += text(716, 318, "missing folder explains itself", size=11.5, fill=MUTED)

    s += arrow(300, 148, 360, 148, color=BLUE, marker="arrowblue")
    s += arrow(300, 282, 348, 282, color=MUTED)
    s += f'<path d="M348,282 L348,200 L360,200" stroke="{MUTED}" stroke-width="1.6" fill="none" marker-end="url(#arrow)"/>\n'
    s += arrow(640, 154, 700, 154, color=GREEN, marker="arrow")
    s += arrow(640, 282, 700, 282, color=MUTED)

    # recipe layer
    y = 386
    s += box(40, y, W-80, 192, fill=SURFACE, stroke=RULE)
    s += label(58, y+24, "the recipe layer — why swapping weights is cheap")
    s += text(58, y+52, "interface ModelRecipe", size=13, weight="bold", mono=True, fill=BLUE)
    s += text(58, y+76, "requiredFiles  ·  matches(dir)  ·  buildConfig(dir, tuning)",
              size=12, mono=True, fill=MUTED)

    cols = [
        (58,  "Weights", "A directory in storage.",
         "New export of a family already\nsupported → drop it in. Zero code."),
        (378, "Recipe", "How a family maps to a config.",
         "New family (whisper, streaming,\nmoonshine) → one ~15-line object."),
        (698, "model.json", "Optional, in the model dir.",
         "Overrides detection when an\nexport's filenames differ."),
    ]
    for (x, t, sub, body) in cols:
        s += f'<path d="M{x},{y+104} L{x},{y+156}" stroke="{RULE}" stroke-width="2"/>\n'
        s += text(x+14, y+118, t, size=13, weight="bold")
        s += text(x+14, y+136, sub, size=11.5, fill=MUTED)
        for k, line in enumerate(body.split("\n")):
            s += text(x+14, y+154+k*15, line, size=11, fill=FAINT)
    return W, H, s + "</svg>\n"


# ══════════════════ 4. State machine ══════════════════
def states():
    W, H = 1000, 500
    s = header(W, H, "State machine")
    s += text(40, 44, "Engine and dictation states", size=22, weight="bold")
    s += text(40, 66, "Every failure the app can reach is a state with a screen, not a toast.",
              size=13, fill=MUTED)

    s += label(56, 104, "engine  \u00b7  warmed at launch, never on the key press")
    eng = [(40, "Unloaded", MUTED), (250, "Loading", BLUE),
           (460, "Ready", GREEN), (700, "Failed", RED)]
    EY = 140
    for (x, t, col) in eng:
        s += box(x, EY, 170, 52, fill=SURFACE, stroke=col, rx=26)
        s += text(x+85, EY+32, t, size=14, weight="bold", fill=col, anchor="middle")
    s += arrow(210, EY+26, 250, EY+26, color=BLUE, marker="arrowblue")
    s += arrow(420, EY+26, 460, EY+26, color=GREEN, marker="arrow")
    s += text(230, EY-10, "model selected", size=10.5, fill=FAINT, anchor="middle")
    s += (f'<path d="M420,{EY+40} L440,{EY+72} L785,{EY+72} L785,{EY+52}" stroke="{RED}" '
          f'stroke-width="1.6" fill="none" marker-end="url(#arrow)"/>\n')
    s += text(612, EY+92, "invalid directory  \u00b7  missing grant", size=10.5, fill=RED,
              anchor="middle")

    s += label(56, 298, "dictation phase")
    ph = [(40, "Idle", MUTED), (250, "Recording", AMBER),
          (460, "Transcribing", BLUE), (700, "Error", RED)]
    PY = 334
    for (x, t, col) in ph:
        s += box(x, PY, 170, 52, fill=SURFACE, stroke=col, rx=26)
        s += text(x+85, PY+32, t, size=14, weight="bold", fill=col, anchor="middle")
    s += arrow(210, PY+26, 250, PY+26, color=AMBER, marker="arrowamber")
    s += arrow(420, PY+26, 460, PY+26, color=BLUE, marker="arrowblue")
    s += arrow(630, PY+26, 700, PY+26, color=RED, marker="arrow")
    s += text(230, PY-10, "press / tap", size=10.5, fill=FAINT, anchor="middle")
    s += text(440, PY-10, "release / tap again", size=10.5, fill=FAINT, anchor="middle")
    s += text(665, PY-10, "too short", size=10.5, fill=RED, anchor="middle")
    s += (f'<path d="M545,{PY+52} L545,{PY+80} L125,{PY+80} L125,{PY+52}" stroke="{GREEN}" '
          f'stroke-width="1.6" fill="none" marker-end="url(#arrow)"/>\n')
    s += text(335, PY+100, "text emitted, persisted, rendered", size=10.5, fill=GREEN,
              anchor="middle")

    s += f'<path d="M40,462 L{W-40},462" stroke="{RULE}" stroke-width="1"/>\n'
    s += text(40, 486, "Recording ends with a stop flag, never coroutine cancellation \u2014 "
                       "cancelling throws away the audio the user just spoke.",
              size=11.5, fill=MUTED)
    return W, H, s + "</svg>\n"


for name, fn in [("architecture", layers), ("dictation-flow", flow),
                 ("model-resolution", resolution), ("state-machine", states)]:
    w, h, svg = fn()
    with open(f"{OUT}/{name}.svg", "w") as f:
        f.write(svg)
    print("wrote", name, w, "x", h)
