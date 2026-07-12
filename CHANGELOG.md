# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **Attribution:** declared the project as a fork of
  [`NadeemIqbal/llm-typewriter`](https://github.com/NadeemIqbal/llm-typewriter).
  Added a "Credits" section to the README, an upstream copyright notice to the
  LICENSE, and listed NadeemIqbal as an upstream developer in the published POM.
  The fork detached from GitHub's fork network because of substantial divergence
  (Android-only target, streaming LaTeX math, incremental parsing, performance
  work); all code — original and modified — remains Apache 2.0.

## [0.1.0] - 2026-07-12

First release on Maven Central. Targets Android only (minSdk 24); earlier
multiplatform iterations (iOS / Desktop / Web) never shipped to a public Maven
repository and are superseded by this release.

### Added
- `StreamingTypewriter` — renders a `Flow<String>` of tokens with a configurable
  cursor, speed curve, and renderer. Tap-to-skip, stop/resume controls, a11y live
  region for screen readers.
- `TypewriterText` — convenience for non-streaming usage (hero banners, demos).
- `CyclingTypewriterText` — types one phrase, holds, deletes back to empty,
  types the next.
- `StreamingTypewriterState` + `rememberStreamingTypewriterState` — fully
  usable without composition. Phases: `Idle`, `Revealing`, `Waiting`, `Done`,
  `Stopped`. The primary constructor is public so the state can be created
  directly from a `ViewModel` / chat-row factory / coroutine / test.
- `SpeedCurve` — three presets:
  - `Linear` — constant cadence.
  - `EaseOut` — slight stretch on whitespace.
  - `Natural` — pauses on `.!?,;:` and newlines; simulates a human typist.
- `TypewriterRenderer`:
  - `PlainTypewriterRenderer` — raw text, zero parsing.
  - `rememberMarkdownTypewriterRenderer()` — live progressive markdown (headings,
    bold, italic, bold-italic, strikethrough, inline code, links, fenced code
    blocks, inline `$…$` and display `$$…$$` math).
- Built-in `MdToken`-emitting prefix-stable Markdown parser — tokens before an
  opening delimiter never re-classify when the close arrives. `$$…$$` takes
  priority over `$…$`; inline math rejects whitespace-adjacent delimiters to
  avoid currency false positives (`$5` stays plain text).
- Per-language syntax highlighting for fenced code blocks: Kotlin, JavaScript /
  TypeScript, Python — keyword/string/number/comment classes. Progressive:
  highlights as the fence grows.
- `TypewriterCursor`: `Block`, `Line`, `Underscore`, `None`, and
  `Custom { ... }` (any composable).
- `ThinkingDots` — three pulsing dots, the classic "assistant is thinking"
  indicator.
- `staticFlowOf(text)` and `wordTokenFlowOf(text)` — convenience flow builders
  for testing and demos.
- LaTeX math rendering via [AndroidMath](https://github.com/gregcockroft/AndroidMath)'s
  `MTMathView` (native Freetype with Latin Modern Math / Tex Gyre Termes / XITS
  Math fonts). Inline `$…$` renders in text style; display `$$…$$` renders in
  display style with a background tint and scaled-up font. Math renders only
  after the closing `$` / `$$` has arrived — never with a half-formed string.
- `TexParser.kt` — prefix-stable lexer for TeX math fragments (`\command`,
  `{group}`, `^`/`_` scripts, `%comment`); unclosed groups and dangling scripts
  degrade to safe text. Retained for prefix-stability testing.
- `MathAst.kt` — semantic AST with fractions, roots, binomials, big operators,
  delimiters, 150+ symbol commands, and `\mathbb` blackboard-bold mapping.
  `x_a^b` folds into a single `SubSup` node; scripts on `\sum`/`\int`/… fold
  into `lower`/`upper` limits on the operator. Retained for testing.
- `measurePlatformMath` expect/actual — pre-measures each inline equation so the
  placeholder width matches the equation's actual rendered width (instead of a
  fixed estimate); surrounding text reflows correctly around each equation.
- 136+ pure-logic tests (parser, highlighter, speed curves, state machine, TeX
  parser, math AST, paragraph grouping) + Compose UI instrumented tests.
- Sample app with a fake-LLM that streams pre-canned Markdown + code + math
  responses, a speed-curve switcher, and Stop/Resume/Skip controls.

### Performance
- **`revealNext` no longer allocates a String per character.** The revealed text
  is tracked as an `Int` length into the append-only buffer; `revealed` is
  computed lazily on read. Removes the O(N²) substring-allocation cost for an
  N-character stream.
- **Streaming Markdown re-parse is incremental.** `revealedTokens()` caches the
  parsed token list and, when the buffer grows and the trailing token is
  `Plain`, re-parses only the small tail. Prefix-stability guarantees the
  earlier tokens are unchanged, so the cache is safe. Removes the O(N²) re-parse
  cost.
- **Math measurement is cached process-wide.** `measurePlatformMath` backs its
  results with an `LruCache` keyed on (latex, displayMode, fontSizePx), so a
  given equation is laid out at most once for the app lifetime.
- **`RenderPlatformMath` skips redundant `MTMathView` writes.** The
  `AndroidView.update` block only calls `setLatex` / `setLabelMode` /
  `setTextColor` / `setFontSize` when the value actually changed.
- **`planBlocks` skips non-streaming recompositions.** The block-plan `remember`
  key is the cached tokens list reference (stable across scroll / theme /
  cursor-blink recompositions) instead of the revealed `String`.
- **`deleteLastRevealed` is O(1)** — just decrements the length counter.

### Fixed
- **Inline LaTeX all rendered at the same width.** Each inline `$…$` equation's
  placeholder now uses the equation's actual measured width instead of a fixed
  `fontSize × 4` estimate.
- **Headings only bolded, not enlarged.** `headingScale` (1.8×/1.5×/1.3×/1.1×/
  1.0×/0.9× for levels 1–6) is applied to the ambient font size so headings
  scale up per CommonMark/HTML convention.
- **Single newlines broke lines instead of soft-wrapping.** `groupIntoParagraphs`
  follows CommonMark: a single newline is a soft break (rendered as a space),
  and a run of two or more newlines collapses to one paragraph break.
- **Inline math split from surrounding text.** Inline runs containing math are
  emitted as a single `InlineRunWithMath` block laid out via `Text` with
  `InlineTextContent`, so math sits inline with surrounding words and wraps at
  word boundaries.
- **Superscripts rendered too low.** `RenderScript`'s `Box` now has an explicit
  height so `sup` truly sits above the baseline.
- **Big-operator limits detached from the glyph / misaligned with the formula.**
  `RenderBigOperator` applies a tight `TextStyle` and the outer `Row` uses
  `Alignment.CenterVertically` in display mode.

[Unreleased]: https://github.com/ECSDevs/llm-typewriter/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ECSDevs/llm-typewriter/releases/tag/v0.1.0
