# Contributing to LlmTypewriter

Thanks for your interest in improving this library! Contributions of all kinds are welcome —
bug reports, feature requests, docs, and code.

## Project layout

```
llm-typewriter/                  The published Kotlin Multiplatform library.
  src/commonMain/                 Public API + implementation.
    StreamingTypewriter.kt        Composables (StreamingTypewriter / TypewriterText / Cycling).
    StreamingTypewriterState.kt   Phase machine + reveal buffer + state container.
    SpeedCurve.kt                 Linear, EaseOut, Natural reveal cadences.
    TypewriterRenderer.kt         Renderer interface + Plain + Markdown.
    MarkdownStreamParser.kt       Prefix-stable streaming markdown parser.
    CodeHighlighter.kt            Kotlin/JS/Python syntax highlighter.
    MarkdownStyles.kt             Style record for the markdown renderer.
    Cursor.kt                     Block / Line / Underscore / None / Custom cursors.
    ThinkingIndicator.kt          Three pulsing dots.
    LlmTypewriterDefaults.kt      Defaults — base delay, blink period, theme-derived styles.
    Helpers.kt                    staticFlowOf, wordTokenFlowOf, internal helpers.
  src/commonTest/                 Pure-logic tests (parser, highlighter, state, speed curves).
  src/skikoTest/                  Compose UI tests — run on Desktop and iOS test targets.
sample/composeApp/                Shared fake-LLM sample with markdown + code-block demo.
sample/androidApp/                Android launcher.
sample/desktopApp/                Desktop (JVM) launcher.
sample/webApp/                    Web (wasmJs) launcher.
sample/iosApp/                    iOS launcher (Xcode project).
```

## Building & testing

```bash
./gradlew build                                  # build + test everything
./gradlew allTests                               # run tests on all targets
./gradlew :llm-typewriter:desktopTest            # fastest feedback (commonTest + skikoTest)
./gradlew :llm-typewriter:testDebugUnitTest      # Android unit tests
./gradlew :sample:desktopApp:run                 # run the desktop sample
```

The streaming-markdown parser and code highlighter are pure-logic — keep them that way so any
parser change can be unit-tested without spinning up the Compose runtime. UI tests verify wiring
(test tags, tap-to-skip, stop indicator) on top of the headless state machine.

## Design invariants — please preserve

- **Prefix stability.** `parseStreamingMarkdown` must produce the same prefix of tokens for any
  prefix of input. Reflowing earlier tokens as later text arrives breaks the live reveal — never
  emit a different earlier token when the input grows.
- **Headless state.** `StreamingTypewriterState` is constructable + drivable without composition.
  Any new behaviour belongs there first; the composables should be thin wrappers.
- **No platform reach-arounds in commonMain.** Speed curves, parsers, highlighter, state — all
  pure Kotlin. Anything platform-specific belongs in `iosMain` / `androidMain` / etc.

## Conventions

- Public API gets KDoc.
- Add or update tests for every behaviour change — both pure logic and UI wiring.
- Update the sample app when you change a public API.
- Add a `CHANGELOG.md` entry under `## Unreleased`.

## Releasing

Releases are tag-driven: pushing a `v*` tag runs `.github/workflows/publish.yml`, which publishes
to Maven Central via the `com.vanniktech.maven.publish` plugin and creates a GitHub Release.
