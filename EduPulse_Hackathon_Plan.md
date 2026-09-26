# EduPulse — iQOO × Reskilll Hackathon Plan

**Project:** EduPulse, "Doubt-to-Diagram" — a 100% offline AI STEM tutor.
**Pipeline:** Camera → PaddleOCR v5 (ONNX Runtime) → quantized Gemma 2B (LiteRT-LM) → structured JSON → deterministic solver/verifier → Jetpack Compose Canvas diagram → What-If sliders → offline TTS (English / Telugu / Hindi).
**Team roles:** M = mobile dev, A = AI/ML, U = UX & pitch. (If 2 people, M also covers U.)

> Everything marked **(confirm)** is unverified against the official rules and must be checked at the Saturday 10:00 teach-in or with the organisers in writing before you rely on it. Nothing in this file should be read as a confirmed rule.

---

## 0. Before the event

- Do **not** write EduPulse code before the event. The Terms & Conditions require submissions to be original work created *during* the Event; only pre-Event drafting of *ideas* is permitted.
- Practice the phone-only workflow (GitHub, github.dev, cloud builds, AI tools) on an **unrelated throwaway project**, and delete/don't reuse that code.
- Read the licences for Gemma, PaddleOCR, ONNX Runtime, and LiteRT-LM. Third-party APIs/libraries are allowed under the Terms if you comply with licence and attribution requirements.
- Send the organisers a written question list (see Section 8) and save the answers.

---

## 1. Architecture principles (design for Red Light)

- **Modular pipeline** — Camera → OCR → Problem Parser → Solver → Verifier → Diagram → What-If → Voice, each with a simple interface. Any stage can be fed typed text directly for isolated testing.
- **Gemma classifies and extracts; Kotlin computes.** Gemma returns `{topic, variables, formula_id}`. Your own Kotlin solver does the math; a separate verifier recomputes and checks it. Never trust the LLM's arithmetic directly.
- **Config-driven, not code-driven.** Prompts (`prompts/*.txt`), `formulas.json`, `diagrams.json` load from the app's private files folder, falling back to bundled assets. Build a hidden **Prompt Lab** screen (long-press logo) to edit + validate + test these on the phone with no rebuild needed.
- **Models load one at a time** (OCR, then release it, then Gemma) to manage memory.
- **Always show an editable "recognized problem" text box** — one OCR misread shouldn't kill the demo.
- **What-If sliders never call the LLM.** Gemma identifies the formula once; the slider redraw is pure deterministic math.

---

## 2. Red Light toolchain — pick a path at the teach-in

- **Path A — internet + GitHub allowed:** edit via phone browser (github.dev), push, GitHub Actions builds a debug APK, sideload it.
- **Path B — no internet in Red:** Red becomes config-only (Prompt Lab) + hardware testing. All Kotlin work moves into Green blocks.
- **Path C — organiser-provided toolchain:** adapt to whatever is issued.

Minimal GitHub Actions workflow (Path A):

```yaml
name: Build APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: { name: app-debug, path: app/build/outputs/apk/debug/app-debug.apk }
```

**Tools usable in Red (confirm each):**
- EduPulse's own Prompt Lab screen (always available, no confirmation needed)
- Phone browser + github.dev **(confirm internet + GitHub allowed)**
- GitHub Actions cloud build **(confirm internet allowed)**
- Google AI Studio **(confirm AI tools allowed; confirm native-library support for ONNX Runtime/LiteRT-LM first — untested)**
- Claude app (chat) on phone **(confirm AI tools allowed)**
- Office Kit: file transfer, clipboard, screen mirroring
- Phone's own Camera, Files, Settings, Stopwatch apps

**Tools usable in Green:**
- Android Studio, Gradle, adb, Logcat, Git
- Python for model preparation
- Antigravity IDE / Claude Code — agentic coding assistants **(confirm AI tools allowed)**

---

## 3. Full Schedule

### Sat 10:00–11:00 — Teach-in
- Ask the question list (Section 8); write down every answer.
- Test Office Kit file transfer + clipboard on the loaner phone.
- Decide Red build Path A / B / C.

### Sat 11:00–13:00 🟢 Green
**11:00–11:45**
- M: Create Kotlin + Jetpack Compose project. Add CameraX, ONNX Runtime Android, LiteRT-LM dependency. Push to GitHub with the workflow file.
- A: Get PaddleOCR v5 ONNX model files and Gemma 2B in LiteRT-LM format ready (check exact format against LiteRT-LM docs).
- U: Handwrite 30 test problems (kinematics, F=ma, projectile) and photograph them.

**11:45–12:30**
- M: Build config loader (bundled assets + files-folder override) and Prompt Lab screen with JSON validation. Add "Import model" button (system file picker).
- A: Write `prompts/classify.txt`, `prompts/extract.txt`, `formulas.json`. Write Kotlin solver for kinematics and F=ma.
- U: Move models + test photos to phone via Office Kit file transfer. Start an Office Kit evidence log (screenshot + timestamp per use).

**12:30–13:00**
- M: Build APK, install via adb/cable, confirm camera opens.
- All: Confirm typed text reaches the solver. **Do not leave this block until install works.**

### Sat 13:00–15:30 🔴 Red
**13:00–14:00**
- M: Wire CameraX capture to OCR. Use github.dev / cloud build if Path A works.
- A: Run Gemma via Prompt Lab with typed problems; check valid JSON output.
- U: Photograph test problems in-app; log every OCR error.

**14:00–15:00**
- M: Add editable "recognized problem" box.
- A: Tune prompts until ≥8/10 test problems classify correctly.
- U: Time each pipeline stage with the phone's stopwatch.

**15:00–15:30 — Decision gate**
- If Gemma isn't reliably running on-phone: ship typed-input + template solver for now.
- Write the prioritized "needs laptop" list.

### Sat 15:30–16:30 🟢 Green
- M (15:30–16:15): Fix top 3 blockers only (Logcat/adb).
- A (15:30–16:15): Add JSON parser + verifier (recompute answer in Kotlin, compare to Gemma's).
- M (16:15–16:30): Build, install, tag commit as known-good backup. **No new features.**

### Sat 16:30–19:00 🔴 Red
**16:30–17:45**
- M: Compose Canvas kinematics diagram (via AI Studio/github.dev if available, else add to laptop list).
- A: Add F=ma solver + formula entries.
- U: Define diagram JSON — labels, values, axis ranges.

**17:45–19:00**
- M: Add What-If slider (math engine only, never Gemma).
- U: Test sliders on phone; write pitch script.
- Commit before every AI-assisted session.

### Sat 19:00–22:00 ⚪ Grey
- Rest, or pitch work if organisers allow.

### Sat 22:00–01:00 🔴 Red
**22:00–23:00**
- A: Time OCR/Gemma/diagram stages; record numbers for the pitch.
- U: Check English & Telugu offline voices in Settings → text-to-speech; note if Telugu voice data is installed.

**23:00–00:00**
- M: Wire Android TextToSpeech to explanation text (English first).
- All: First full airplane-mode test of the whole chain.

**00:00–01:00**
- Fix small issues via Prompt Lab or cloud build.
- Write the prioritized Sunday-Green list.

### Sun 01:00–06:30 🟢 Green
**01:00–02:30 — Office Kit bridges (M)**
1. Send lesson to laptop — export diagram+steps as PNG/PDF via file transfer.
2. Paste problem from laptop — clipboard into problem box (doubles as typed fallback).
3. Classroom mode — mirror the What-If screen to the laptop; phone controls the slider.
- A (parallel): measure latency and RAM on-phone.

**02:30–03:30**
- A + M: Try GPU/NPU acceleration for LiteRT-LM. Drop it if unstable by 03:30. Record before/after numbers.

**03:30–05:00**
- M: Projectile diagram.
- A: Verifier for projectile problems.
- U: Telugu voice, checked on-phone.

**05:00–06:00**
- All: UI polish (loading states, error messages, text). Full offline test.

**06:00–06:30 — FREEZE**
- Build final APK, install, smoke test, tag as final version.
- **No code changes after this point.**

### Sun 06:30–09:00 🔴 Red
**06:30–07:30**
- Airplane-mode test of full chain, then re-enable link and test Office Kit segment.
- Fix problems only via JSON/Prompt Lab edits.

**07:30–08:30**
- U: 3 full demo rehearsals, timed.
- All: Record a backup demo video.

**08:30–09:00**
- Charge phone, clear notifications, set up demo screen, load final test problems. **Change nothing.**

### Sun 09:00–12:00 ⚪ Grey
- Likely judging — confirm.

---

## 4. Office Kit usage plan (scored ~10–25%, confirm exact weight)

**In your workflow** (log each with screenshot + timestamp):
- File transfer — models, APKs, test images, logs
- Clipboard — prompts, snippets, error messages
- Screen mirroring — team watches phone tests together
- Remote control — drive phone during Green testing

**Built into EduPulse itself** (build 3 — full implementation in [EduPulse_AI_Cheatsheet.md](EduPulse_AI_Cheatsheet.md#6-office-kit-integration-spec-mandatory-10-score)):
1. **Send lesson to laptop (`WorksheetExporter.kt`):** Renders high-res 1080x1520 PNG worksheet with derivation steps and saves to MediaStore for instant Office Kit file drag-and-drop.
2. **Paste problem from laptop (`ClipboardBridge.kt`):** Ingests digital textbook doubts from laptop clipboard with 1 tap.
3. **Classroom mode (`ClassroomScreen.kt`):** Fullscreen 16:9 dark-theme simulation canvas with large thumb sliders, designed for live blackboard projector mirroring.

Optional (if time): import textbook page photo from laptop as OCR input.

---

## 5. Demo script (~3 min)

1. Turn on airplane mode.
2. Photograph a handwritten problem.
3. Show OCR result → Gemma's structured solution → verifier check.
4. Show diagram; move the What-If slider.
5. Play the Telugu explanation.
6. Show stage-timing numbers and "0.00 KB cloud transfer" screen.
7. Re-enable local link; show classroom mode + send-to-laptop.
8. Close with the impact story.

---

## 6. Feature priority

- **Must (by Sat 19:00):** camera, OCR/typed input, Gemma or template solve, verifier, answer.
- **Should (by Sun 01:00):** 2 diagram types, What-If slider, English voice, first airplane-mode proof.
- **Sunday Green:** 3 Office Kit bridges, Telugu voice, projectile diagram, GPU tuning, metrics.
- **If time remains:** Hindi voice, voice input, textbook import.
- **Cut order under time pressure:** Hindi → extra diagrams → general polish → GPU/acceleration tuning. Keep Telugu and the airplane-mode demo till the end.

---

## 7. Risks and pitfalls (read before you start)

- **Nothing about AI tools, Red/Green rules, Office Kit, or internet access is confirmed.** The Terms & Conditions you have don't mention any of these. Get written answers before relying on Paths A/C or any AI assistant.
- **"Original work created during the Event"** — don't bring pre-written EduPulse code; practice only on unrelated projects.
- **Loaner phone must not be modified/unlocked (Terms, Clause 05).** Confirm whether Developer Options / USB debugging / sideloading are permitted before doing them.
- **Rubric weights can change per round/city (Terms, Clause 06).** Don't over-optimize for one Office Kit percentage.
- **Disclose AI tool use** if you use Claude/AI Studio/Antigravity — keep a short log of generated vs. hand-written code, in case authorship is questioned.
- **Gemma reliability is unproven on-device** — fit, speed, and JSON-format reliability need testing early; the Sat 15:00 decision gate and template fallback exist for this reason.
- **Airplane mode vs. Office Kit** — if Office Kit needs Wi-Fi/Bluetooth, it won't work mid airplane-mode demo. Keep the two demo halves separate.
- **AI Studio's native-library support (ONNX Runtime, LiteRT-LM) is untested** — verify on a practice project before relying on it for Red Light diagram/UI work.
- **Telugu offline voice data may not be preinstalled** on the loaner — check early; have a fallback of English-only if unavailable.
- **06:00 freeze is the single highest-risk moment** — any code change after it is the most likely way to break a working demo.
- **Fatigue** during the 01:00–06:30 Green block, your hardest work at lowest energy — schedule sleep shifts if the team allows it.
- **Grey blocks (19:00–22:00, 09:00–12:00) are unconfirmed** — don't assume they're free work time or that 09:00 isn't a hard deadline.

---

## 8. Questions for the organisers / teach-in

1. How do we build and install an APK during Red Light?
2. Is there internet on the loaner phone during Red Light? Are GitHub and AI coding tools (ChatGPT, Gemini/AI Studio, Claude, Antigravity) allowed — in Red? In Green?
3. During Red Light, may we use a laptop keyboard/mouse via Office Kit remote control to type on the phone?
4. What exactly does Office Kit expose, does it work offline, and what % of the score does it represent?
5. Can we preload/bring model files (Gemma, PaddleOCR) via a drive or cable?
6. What are the grey blocks (Sat 19:00–22:00, Sun 09:00–12:00)?
7. Can we sideload our own APKs on the loaner device?
8. What are the full rubric weights beyond Office Kit?
9. Is disclosure required if we use AI coding assistants?
10. Does enabling Developer Options / USB debugging count as "modifying" the loaner hardware under the Terms?
