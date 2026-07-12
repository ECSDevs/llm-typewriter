# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Performance
- **`revealNext` no longer allocates a String per character.** The revealed text is now tracked
  as an `Int` length into the append-only buffer; `revealed` is computed lazily on read. This
  removes the O(N²) substring-allocation cost for an N-character stream (each of N reveal steps
  previously copied an O(N) String).
- **Streaming Markdown re-parse is now incremental.** `StreamingTypewriterState.revealedTokens()`
  caches the parsed token list and, when the buffer grows and the trailing token is `Plain`,
  re-parses only the small tail instead of the whole revealed string. Prefix-stability guarantees
  the earlier tokens are unchanged, so the cache is safe. Removes the O(N²) re-parse cost.
- **Math measurement is cached process-wide.** `measurePlatformMath` now backs its results with
  an `LruCache` keyed on (latex, displayMode, fontSizePx), so a given equation is laid out at
  most once for the app lifetime — across recompositions, Composable instances, and chat bubbles
  — instead of once per recomposition per inline equation.
- **`RenderPlatformMath` skips redundant `MTMathView` writes.** The `AndroidView.update` block
  now only calls `setLatex` / `setLabelMode` / `setTextColor` / `setFontSize` when the value
  actually changed, avoiding a full LaTeX re-parse on every recomposition during streaming.
- **`planBlocks` skips non-streaming recompositions.** The block-plan `remember` key is now the
  cached tokens list reference (stable across scroll / theme / cursor-blink recompositions) instead
  of the revealed `String` (which changed every frame and forced a substring + planBlocks rebuild).
- **`deleteLastRevealed` is O(1).** Previously walked the buffer copying chars; now just decrements
  the length counter (same machinery as `revealNext`).

### Added
- Mini LaTeX math renderer — inline `$…$` and display `$$…$$` math in the streaming Markdown.
  - `TexParser.kt` — prefix-stable lexer for TeX math fragments (`\command`, `{group}`,
    `^`/`_` scripts, `%comment`); unclosed groups and dangling scripts degrade to safe text.
  - `MathAst.kt` — semantic AST with fractions, roots, binomials, big operators, delimiters,
    150+ symbol commands, and `\mathbb` blackboard-bold mapping. `x_a^b` folds into a single
    `SubSup` node so sub/sup render in the same column; scripts on `\sum`/`\int`/… fold into
    `lower`/`upper` limits on the operator itself.
  - `MathRenderer.kt` — Compose renderer: fractions with bar, `\sqrt` with vinculum, same-column
    sub/sup, display-mode big-operator limits stacked above/below the glyph, display-math scaling.
  - `MarkdownStreamParser` now emits `InlineMath` / `DisplayMath` tokens; `$$…$$` takes priority
    over `$…$`, and inline math rejects whitespace-adjacent delimiters to avoid currency
    false positives (`$5` stays plain text).
  - 50+ new unit tests (`TexParserTest`, `MathAstTest`, math cases in `MarkdownStreamParserTest`).
  - Sample app gains a 4th demo response showcasing streaming math rendering.

### Changed
- Removed all non-Android platform targets (iOS, Desktop/JVM, Web/wasmJs). The library now
  targets Android only (`androidTarget`, minSdk 24).
- Deleted sample modules: `:sample:desktopApp`, `:sample:webApp`, and `sample/iosApp/`.
- Moved Compose UI tests from `skikoTest/` (Skiko-backed, Desktop+iOS only) to
  `androidInstrumentedTest/` — run via `./gradlew :llm-typewriter:connectedAndroidTest`.
- CI workflows now run on `ubuntu-latest` instead of `macos-latest`.

### Fixed
- **Inline LaTeX all rendered at the same width.** Each inline `$…$` equation's
  `InlineTextContent` placeholder used a fixed `fontSize × 4` estimate, so every equation occupied
  the same slot regardless of content. The renderer now pre-measures each fragment via a new
  `measurePlatformMath` expect/actual (a detached `MTMathView.measure` pass on Android) and sizes
  the placeholder to the equation's actual width — surrounding text reflows correctly around each.
- **Headings only bolded, not enlarged.** `headingSize` returned `TextUnit.Unspecified`, so `# …`
  rendered at body size. Replaced with `headingScale` (1.8×/1.5×/1.3×/1.1×/1.0×/0.9× for levels
  1–6) applied to the ambient font size so headings scale up per CommonMark/HTML convention.
- **Single newlines broke lines instead of soft-wrapping.** The renderer turned every
  `MdToken.Newline` into a literal `\n`, forcing a line break for each. New `groupIntoParagraphs`
  follows CommonMark: a single newline is a soft break (rendered as a space), and a run of two or
  more newlines collapses to one paragraph break. Block-level tokens (heading/code block/display
  math) always start their own paragraph. Covered by new `ParagraphGroupingTest`.
- TeX rendering layout bugs reported in the inline-vs-display review:
  - **Inline math split from surrounding text.** Inline `$…$` math and the markdown text around it
    were forced onto separate rows because each `InlineMath` token became its own `MdBlock`.
    Inline runs containing math are now emitted as a single `InlineRunWithMath` block laid out via
    `FlowRow`, with text pre-split into word-level segments so the row wraps naturally at word
    boundaries and math sits inline with the surrounding words.
  - **Superscripts rendered too low.** `RenderScript`'s `Box` wrapped its content, so `sup` at
    `TopStart` ended up at the baseline. The Box now has an explicit height
    (`fontSize × ScriptBoxHeightScale`, 1.3×) so `sup` truly sits above the baseline and `sub` sits
    at/below it.
  - **Big-operator limits detached from the glyph.** Default line-height padding pushed the
    stacked `upper`/glyph/`lower` apart. `RenderBigOperator` now applies a tight `TextStyle`
    (`lineHeight = fontSize`, `LineHeightStyle.Trim.Both`, `includeFontPadding = false`) to the
    glyph and both limits in display mode so they sit close together.
  - **Big-operator column misaligned with the rest of the formula.** With `Alignment.Bottom`
    on the outer `Row`, a tall stacked `BigOperator` had its lower limit on the baseline, making
    the operator look like a subscript. In display mode the outer `Row` and `Group` `Row` now use
    `Alignment.CenterVertically` so the operator's glyph aligns with the rest of the formula.

### Changed (math rendering)
- **LaTeX rendering is now delegated to [AndroidMath](https://github.com/gregcockroft/AndroidMath)'s
  `MTMathView`** instead of the in-tree Compose renderer. AndroidMath uses native Freetype to
  render LaTeX with proper font metrics (Latin Modern Math / Tex Gyre Termes / XITS Math fonts),
  so fractions, big-operator stacked limits, roots, and the rest of the LaTeX grammar render
  correctly without us having to re-implement them. The previous `MathRenderer.kt` is deleted.
  - New `expect`/`actual` split: `MathRendering.kt` (commonMain) declares `RenderPlatformMath`;
    `MathRendering.android.kt` (androidMain) wraps `MTMathView` via `AndroidView`.
  - `MTMathView.labelMode` switches between `KMTMathViewModeDisplay` (for `$$…$$`) and
    `KMTMathViewModeText` (for inline `$…$`).
  - `MarkdownStyles` drops `texCommand` and `fractionBarColor` (no longer used — AndroidMath owns
    the fraction bar, vinculum, and command rendering). The `math` field's `color` is honored;
    other `SpanStyle` properties are ignored because AndroidMath owns the typography.
  - `MathStyles` data class and `mathStyles()` extension removed.
- `TexParser` and `MathAst` are retained for prefix-stability testing and future programmatic
  AST use, but no longer participate in rendering.
- New dependency: `com.github.gregcockroft:AndroidMath:v1.1.0` via JitPack (added to
  `settings.gradle.kts`).

## [0.1.1] - 2026-05-17

### Changed
- `StreamingTypewriterState`'s primary constructor is now **public** — was `internal`,
  blocking headless construction outside `@Composable` scope. The headless API was
  always advertised as drivable without composition, but `rememberStreamingTypewriterState`
  was the only way to obtain an instance. Now you can do
  `val state = StreamingTypewriterState()` directly from a `ViewModel` / chat-row factory
  / coroutine / test.

[0.1.1]: https://github.com/ECSDevs/llm-typewriter/releases/tag/v0.1.1

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

[Unreleased]: https://github.com/ECSDevs/llm-typewriter/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ECSDevs/llm-typewriter/releases/tag/v0.1.0
