# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-05-17

### Added
- Initial release of `LlmTypewriter` for Compose Multiplatform.
- `StreamingTypewriter` — renders a `Flow<String>` of tokens with a configurable cursor, speed
  curve, and renderer. Tap-to-skip, stop/resume controls, a11y live region for screen readers.
- `TypewriterText` — convenience for non-streaming usage (hero banners, demos).
- `CyclingTypewriterText` — types one phrase, holds, deletes back to empty, types the next.
- `StreamingTypewriterState` + `rememberStreamingTypewriterState` — fully usable without
  composition. Phases: `Idle`, `Revealing`, `Waiting`, `Done`, `Stopped`.
- `SpeedCurve` — three presets:
  - `Linear` — constant cadence.
  - `EaseOut` — slight stretch on whitespace.
  - `Natural` — pauses on `.!?,;:` and newlines; simulates a human typist.
- `TypewriterRenderer`:
  - `PlainTypewriterRenderer` — raw text, zero parsing.
  - `rememberMarkdownTypewriterRenderer()` — live progressive markdown (headings, bold,
    italic, bold-italic, strikethrough, inline code, links, fenced code blocks).
- Built-in `MdToken`-emitting prefix-stable Markdown parser — tokens before an opening delimiter
  never re-classify when the close arrives.
- Per-language syntax highlighting for fenced code blocks: Kotlin, JavaScript/TypeScript,
  Python — keyword/string/number/comment classes. Progressive: highlights as the fence grows.
- `TypewriterCursor`: `Block`, `Line`, `Underscore`, `None`, and `Custom { ... }` (any composable).
- `ThinkingDots` — three pulsing dots, the classic "assistant is thinking" indicator.
- `staticFlowOf(text)` and `wordTokenFlowOf(text)` — convenience flow builders for testing and
  demos.
- 50+ pure-logic tests (parser, highlighter, speed curves, state machine) + 7 Compose UI tests.
- Targets: Android (minSdk 24), iOS (x64, arm64, simulatorArm64), Desktop (JVM 11), Web (wasmJs).

[Unreleased]: https://github.com/NadeemIqbal/llm-typewriter/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/NadeemIqbal/llm-typewriter/releases/tag/v0.1.0
