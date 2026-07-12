# LlmTypewriter

**The streaming-text typewriter built for LLM apps on Compose.** Renders a
`Flow<String>` of tokens with live progressive Markdown, syntax-highlighted code blocks that
build up as tokens arrive, three speed curves (linear / ease-out / natural), a configurable
blinking cursor, tap-to-skip, graceful stop-mid-stream, selectable text, and a screen-reader-
friendly live region.

[![Maven Central](https://img.shields.io/maven-central/v/cc.ptoe/llm-typewriter)](https://central.sonatype.com/artifact/cc.ptoe/llm-typewriter)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Build](https://github.com/ECSDevs/llm-typewriter/actions/workflows/build.yml/badge.svg)](https://github.com/ECSDevs/llm-typewriter/actions/workflows/build.yml)

<p align="center">
  <img src="docs/hero.gif" width="320" alt="LlmTypewriter streaming a markdown response with a syntax-highlighted Kotlin code block on Android" />
</p>

> **Pairs naturally with [`prompt-bar`](https://github.com/ECSDevs/prompt-bar)** — drop both in and you have a ChatGPT-quality chat UI on Android in ~20 lines. PromptBar's Send/Stop button auto-syncs with `state.isStreaming`.

## Why another typewriter?

Existing CMP typewriters take a static `String` — they're animations, not stream renderers.
LlmTypewriter is built around the **AI chatbot streaming-token use case** that nobody else has
shipped:

| Capability | LlmTypewriter | Typist-CMP | Texty | GetStream `StreamingText` |
|---|---|---|---|---|
| `Flow<String>` source | ✅ | ❌ static string | ❌ static string | ✅ Android-only |
| Live progressive Markdown | ✅ | ❌ | ❌ | ❌ |
| Syntax-highlighted code blocks (live) | ✅ Kotlin/JS/Python | ❌ | ❌ | ❌ |
| Speed curves (linear / easeOut / natural) | ✅ | ❌ linear only | ❌ linear only | ❌ |
| Tap-to-skip | ✅ | ❌ | ❌ | ❌ |
| Graceful stop-mid-stream | ✅ | ❌ | ❌ | partial |
| Custom `@Composable` cursor | ✅ | ❌ | ❌ no cursor | ❌ |
| Selectable text mid-stream | ✅ | ❌ | ❌ | partial |
| A11y live-region announcements | ✅ | ❌ | ❌ | partial |
| CMP target coverage | Android | Mostly | Mostly | **Android-only** |
| Active maintenance | ✅ | ❌ stale ~2y | ⚠️ alpha | locked in chat SDK |

## Install

```kotlin
// settings.gradle.kts — already on Maven Central, no extra repos.
dependencies {
    implementation("cc.ptoe:llm-typewriter:0.1.0")
}
```

## Usage

### Stream an LLM response

```kotlin
@Composable
fun ChatBubble(chatViewModel: ChatViewModel) {
    val state = rememberStreamingTypewriterState()
    StreamingTypewriter(
        tokens = chatViewModel.responseFlow,   // Flow<String>
        state = state,
        renderer = rememberMarkdownTypewriterRenderer(),
        cursor = TypewriterCursor.Line,
        speedCurve = SpeedCurve.Natural,
    )
    Button(onClick = { state.stop() }, enabled = state.isStreaming) { Text("Stop") }
}
```

### Static text — banner / hero

```kotlin
TypewriterText(
    text = "Build LLM-powered apps for Android.",
    cursor = TypewriterCursor.Block,
    speedCurve = SpeedCurve.Natural,
)
```

### Cycling banner

```kotlin
CyclingTypewriterText(
    phrases = listOf("Type", "Stream", "Render", "Anywhere"),
    holdMs = 1200L,
)
```

### Custom cursor

```kotlin
StreamingTypewriter(
    tokens = tokens,
    cursor = TypewriterCursor.Custom {
        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    },
)
```

## Speed curves

| Curve | Behaviour | Use for |
|---|---|---|
| `SpeedCurve.Linear` | Constant cadence | Logs, terminals |
| `SpeedCurve.EaseOut` | Slight stretch on whitespace | Marketing copy |
| `SpeedCurve.Natural` | Pauses on `.!?,;:\n` — simulates human typing | LLM chat, intros |

## Renderers

| Renderer | What it does |
|---|---|
| `PlainTypewriterRenderer` | Paints raw text in the ambient style. Zero parsing cost. |
| `rememberMarkdownTypewriterRenderer()` | Live progressive Markdown — headings, bold, italic, strikethrough, inline code, links, fenced code blocks with **per-language syntax highlighting** (Kotlin / JS / TS / Python). |

The Markdown parser is **prefix-stable**: a `**bold` mid-stream renders as plain text until the
closing `**` arrives, but every token *before* that opening `**` stays exactly where it was.
Code fences highlight progressively as tokens stream in — no waiting for the closing ``` ``` ```.

## Platforms

| Target | Status |
|---|---|
| Android (minSdk 24) | ✅ |

## State API (headless)

`StreamingTypewriterState` is fully usable without composition — handy for tests and for hosts
that want to drive the typewriter from a background coroutine:

```kotlin
val state = StreamingTypewriterState()
state.appendToken("Hello, ")
state.appendToken("world!")
state.completeSource()
state.skipToEnd()      // flush buffer
state.stop()           // freeze mid-stream
state.resume()         // pick up where we left off
state.reset()          // start fresh
```

## Sample app

```bash
./gradlew :sample:androidApp:assembleDebug       # Android
```

The sample includes a fake-LLM that streams pre-canned responses with Markdown + code blocks, a
speed-curve switcher, and Stop/Resume/Skip controls.

## License

Apache 2.0 — see [LICENSE](LICENSE).
