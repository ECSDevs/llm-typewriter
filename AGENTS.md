# AGENTS.md

Guidance for AI coding agents working in this repository. Read this before making changes.

> **Maintenance rule (read first).** This file is the source of truth for the project's shape,
> build commands, and invariants. **Update AGENTS.md every time the project structure changes** —
> modules added/removed, source files renamed or moved, new build target, changed test layout,
> new public API entry point, or a changed invariant. A stale AGENTS.md is a bug. Keep it in
> sync in the same change that touches the structure; do not defer it to a follow-up. When in
> doubt about whether a change is "structural", err on the side of updating.

## What this is

`LlmTypewriter` — a Compose library that renders a `Flow<String>` of LLM tokens as
a live, progressively-revealed typewriter with streaming Markdown, syntax-highlighted code
blocks, speed curves, tap-to-skip, graceful stop, and a11y live-region support.

- Group / coordinates: `cc.ptoe:llm-typewriter`
- License: Apache 2.0
- Repo: https://github.com/ECSDevs/llm-typewriter

## Modules

```
:llm-typewriter            The published library (see source layout below).
:sample:composeApp         Shared sample: fake-LLM + Markdown/code-block demo.
:sample:androidApp         Android launcher.
```

`settings.gradle.kts` includes all Gradle modules above.

## Build & test

JDK 17 is used on CI; JVM target for Kotlin/Android is 11.

```bash
./gradlew build                                        # build + test everything
./gradlew :llm-typewriter:testDebugUnitTest            # fastest feedback — pure-logic unit tests
./gradlew :llm-typewriter:connectedAndroidTest         # Compose UI instrumented tests
./gradlew :sample:androidApp:assembleDebug             # build Android sample
```

## Release

Tag-driven. Pushing a `v*` tag triggers `.github/workflows/publish.yml`, which publishes to
Maven Central via `com.vanniktech.maven.publish` and creates a GitHub Release. The version comes
from the tag (`v0.2.0` → `0.2.0`); override locally with `-Pversion=...`.

## Library source layout

`llm-typewriter/src/commonMain/kotlin/cc/ptoe/llmtypewriter/`:

| File | Responsibility |
|---|---|
| `StreamingTypewriter.kt` | Composables: `StreamingTypewriter`, `TypewriterText`, `CyclingTypewriterText`. |
| `StreamingTypewriterState.kt` | Phase machine + reveal buffer + headless state container. |
| `SpeedCurve.kt` | `Linear` / `EaseOut` / `Natural` reveal cadences. |
| `TypewriterRenderer.kt` | `TypewriterRenderer` interface + `Plain` + `Markdown` renderers, including image hook wiring. |
| `MarkdownStreamParser.kt` | Prefix-stable streaming Markdown parser (split lines, images, links, footnotes, and `$…$` / `$$…$$` math delimiters). |
| `TexParser.kt` | Prefix-stable lexer for TeX math fragments (`\command`, `{group}`, `^`/`_` scripts, `%comment`). Retained for prefix-stability testing and future programmatic AST use — rendering is now delegated to AndroidMath (see `MathRendering.kt`). |
| `MathAst.kt` | Semantic AST built from `TexToken`s — fractions, sqrt, sub/sup, big operators, symbols, delimiters. Retained for testing; no longer used by the renderer. |
| `MathRendering.kt` | `expect` declaration of `RenderPlatformMath` — renders a complete LaTeX fragment via the platform's math backend. |
| `CodeHighlighter.kt` | Kotlin / JS / TS / Python syntax highlighter. |
| `CodeBlockFontFamily.kt` | `expect` declaration of the platform font family used by fenced markdown code blocks. |
| `MarkdownStyles.kt` | Style record for the Markdown renderer (math color / display background / display scale). |
| `ImageLoading.kt` | `expect` declaration for the platform-default Coil `ImageLoader` used by markdown images. |
| `ThinkingIndicator.kt` | Three pulsing dots. |
| `LlmTypewriterDefaults.kt` | Defaults — base delay, blink period, theme-derived styles. |
| `Helpers.kt` | `staticFlowOf`, `wordTokenFlowOf`, internal helpers. |

`llm-typewriter/src/androidMain/kotlin/cc/ptoe/llmtypewriter/`:

| File | Responsibility |
|---|---|
| `MathRendering.android.kt` | `actual` implementation — delegates to [AndroidMath](https://github.com/gregcockroft/AndroidMath)'s `MTMathView` via `AndroidView`. Renders LaTeX with native Freetype + Latin Modern Math / Tex Gyre Termes / XITS Math fonts. |
| `ImageLoading.android.kt` | `actual` implementation — builds the default Coil `ImageLoader` with an explicit OkHttp network fetcher for markdown images. |
| `CodeBlockFontFamily.android.kt` | `actual` implementation — loads the embedded Cascadia Code Android font resources for fenced markdown code blocks. |

Tests:
- `src/commonTest/` — pure-logic tests (parser, highlighter, state, speed curves, TeX parser, math AST). No Compose runtime.
- `src/androidInstrumentedTest/` — Compose UI tests (test tags, tap-to-skip, stop indicator);
  backed by `compose.uiTest`, run via `./gradlew :llm-typewriter:connectedAndroidTest`.

## Design invariants — do not break

- **Prefix stability.** `parseStreamingMarkdown` and `parseTex` must produce the same prefix of
  tokens for any prefix of input. Never emit a different earlier token when the input grows — the
  live reveal depends on it. Unclosed markup and unclosed TeX groups must degrade to safe text
  without losing characters.
- **Headless state first.** `StreamingTypewriterState` is constructable and drivable without
  composition. New behaviour belongs in the state first; composables stay thin wrappers.
- **No platform code in commonMain.** Speed curves, parsers, highlighter, TeX parser, math AST,
  and state are pure Kotlin. Platform-specific code (including the AndroidMath `MTMathView`
  binding) belongs in `androidMain`.
- **Same-column scripts.** `x_a^b` (both scripts on one base) must fold into a single `SubSup`
  node in `MathAst`. (Kept for AST correctness testing; visual rendering now handled by AndroidMath.)
- **Big-operator limits.** Scripts on a `BigOperator` (`\sum`, `\int`, …) fold into `lower`/
  `upper` fields on the operator itself in `MathAst`. (Kept for AST correctness testing; visual
  rendering now handled by AndroidMath.)
- **Math renders only after the fragment is complete.** `RenderPlatformMath` is called once the
  closing `$` / `$$` has arrived — never with a half-formed LaTeX string. Before that, the parser
  emits the partial input as plain text.
- **Image loading stays replaceable.** Markdown image URLs are parsed in common code and the
  default `MarkdownStyles.imageRenderer` uses Coil; applications may replace it with their own
  loader or cache.
- **Code block font is embedded on Android.** Fenced markdown code blocks use the library-bundled
  Cascadia Code font family from `src/androidMain/res/font/`; keep those resources in sync with
  `CodeBlockFontFamily.android.kt`.

## Conventions

- Public API gets KDoc.
- Add or update tests for every behaviour change — both pure logic and UI wiring.
- Update the sample app when you change a public API.
- Add a `CHANGELOG.md` entry under `## Unreleased`.
- The streaming Markdown parser, TeX parser, math AST, and code highlighter are pure-logic by
  design — keep them that way so any parser change is unit-testable without the Compose runtime.

## Tech stack (from `gradle/libs.versions.toml`)

- Kotlin `2.3.21`, Compose Multiplatform `1.10.3`, AGP `9.2.0`
- `kotlinx-coroutines` `1.10.2`
- Android: minSdk `24`, compileSdk/targetSdk `36`
- Target: `androidTarget`
