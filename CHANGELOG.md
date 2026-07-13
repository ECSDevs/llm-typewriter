# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- **Maven dependency scopes.** Compose and Kotlin coroutines dependencies used by the public API
  are now published with compile scope, while internal Coil, Highlights, and AndroidMath
  dependencies remain runtime-only.

- **Inline formatting inside headings.** `MdToken.Heading` now carries `inline: List<MdToken>`
  instead of a raw `text: String` — the heading's content is run through `parseStreamingMarkdown`
  at parse time, so `**bold**`, `*italic*`, `` `code` ``, `[link](url)`, `$math$`, etc. all render
  inside `# …` lines. The renderer paints the heading's inline tokens with the bold + scaled-font
  heading style, and `ProvideTextStyle` propagates that style to descendants (inline math, inline
  code) so equations and code spans sit at the heading's size, not the body size. Prefix stability
  is preserved: as the heading line streams in, earlier inline tokens stay stable (same pattern as
  `ListItem` and `BlockQuote`).

- **Markdown task lists.** The streaming parser now recognises GFM task-list items
  (`- [ ]` / `- [x]`, also `*` / `+` markers and uppercase `[X]`). `MdToken.ListItem` gains a
  `checked: Boolean?` field (`null` for plain items, `false` / `true` for task items); the
  renderer shows the Unicode ballot-box glyphs (`☐` / `☑`) in place of the bullet marker when it
  is non-null. Prefix stability is preserved: a partial marker (e.g. `- [x` with no closing `]`)
  parses as a plain item and re-classifies to a task item once the `]` arrives. The sample app's
  Lists demo includes a nested task list.

- **Multi-line block quotes render as one box.** Consecutive `>` lines at the same level now
  share a single stripe + background instead of one box per line. Nested quotes (`>> deeper`)
  render as a nested box inside the parent quote. Adds a `buildQuoteTree` pure-logic helper
  (unit-tested in `QuoteTreeTest`).

- **Code highlighting via [Highlights](https://github.com/SnipMeDev/Highlights).** Fenced code
  blocks now use the Highlights KMP engine for structural analysis, expanding language support
  from 3 (Kotlin / JS / Python) to 17 (C, C++, Dart, Java, Kotlin, Rust, C#, CoffeeScript,
  JavaScript, Perl, Python, Ruby, Shell, Swift, TypeScript, Go, PHP). The internal `CodeSpan` /
  `CodeSpanKind` / `CodeLanguage` API and the `MarkdownStyles` colour fields are unchanged, so
  existing renderer and style customisations continue to work. Every character is still covered
  by exactly one span (gaps between Highlights' regions are filled with `Plain`).

- **Sample app releases.** Tag pushes now attach the signed sample APK to the GitHub Release.

- **Removed the cursor.** The buggy cursor composable and cursor parameters were removed from
  all typewriter entry points.

- Fixed long inline code spans overflowing the grid when rendered inside Markdown table cells.

- Fixed nested bold-italic Markdown spans such as `**_bold italic_**` rendering with both styles.

- **Sample app streams.** Reorganized the demo into one mixed showcase and focused streams for
  plain text, Markdown, code blocks, images, math, lists, tables, and footnotes.

- **Markdown split lines.** The streaming Markdown parser and renderer now support `---` as a
  full-width horizontal divider.

### Added
- **Markdown footnotes.** The streaming parser now supports `[^label]` references and
  `[^label]: definition` lines. References render as superscripts and definitions render in a
  compact, styled section as the stream grows. The sample app includes a dedicated Footnotes
  stream demonstrating both forms.

- **Embedded Cascadia Code for markdown code.** Android inline code spans and fenced code blocks
  now load a bundled Cascadia Code font family from `src/androidMain/res/font/`, so markdown code
  renders with the intended typeface instead of the platform-generic monospace fallback.

- **Markdown code block language labels.** Fenced code blocks with a language now show that
  language in a compact label at the top-right of the code surface.

- **Markdown block quotes.** The streaming Markdown parser now recognises line-start `>`
  markers as `MdToken.BlockQuote`, including nested `>>` quote levels. Consecutive quote lines
  are grouped into styled quote blocks by the renderer, and inline formatting inside a quote
  line still works. `MarkdownStyles` now exposes `blockQuoteStripe` and `blockQuoteBackground`
  for theme overrides.

- **Markdown images.** `![alt text](url)` is parsed as `MdToken.Image` and rendered through the
  configurable `MarkdownStyles.imageRenderer` composable hook. The default uses Coil to load
  remote images, while applications can plug in their own loader or cache.

- **GFM tables.** The streaming Markdown parser now recognises GFM-style tables (`| a | b |`
  header + `| --- | --- |` separator + `| 1 | 2 |` data rows). Each table is emitted as a
  `MdToken.Table` whose cells are stored as raw strings and inline-parsed at render time, so
  bold, italic, inline code, and links work inside cells. Column alignment is derived from
  the separator row's colons (`:---` left, `:---:` center, `---:` right, `---` default).
  The renderer paints a bordered grid with a tinted header row (`MarkdownStyles.tableBorder`
  / `tableHeaderBackground`) and per-column `TextAlign`. Rows stream in one-by-one as the
  table grows; the parser is prefix-stable — a header line alone (no separator yet) renders
  as plain text and re-classifies to a `Table` token once the separator arrives.

- **Ordered & unordered lists.** The streaming Markdown parser now recognises unordered
  (`-` / `*` / `+`) and ordered (`1.` / `1)`) list markers at line start, including indented
  nesting (2-space indent unit). Each list item is emitted as a `MdToken.ListItem` whose inline
  content is parsed recursively — so bold, italic, inline code, links, and inline `$…$` math
  all render inside list items. The renderer builds a nested `MdBlock.ListBlock` tree from a
  run of `ListItem` tokens and paints it as a real indented list with `•` / `N.` markers.
  Marker-vs-plain-text disambiguation follows CommonMark: `1.text` (no space after marker),
  `---` (horizontal rule territory), and dates like `2024.01.01` do **not** trigger list parsing.

### Fixed
- **Markdown code block trailing blank line.** Closed fenced code blocks now trim the single
  newline that exists only to place the closing fence on its own line, so code no longer renders
  with a redundant empty trailing line.

- **Markdown image diagnostics.** The default markdown image renderer now distinguishes loading
  and error states in its fallback UI and logs Android image-load failures through `Log.e`, so
  remote-image issues can be diagnosed from a single repro.

- **Markdown image network wiring.** The default markdown image renderer now uses an explicit
  Android Coil `ImageLoader` with `OkHttpNetworkFetcherFactory` instead of relying on implicit
  component discovery. This fixes remote markdown images falling back to alt text even when the
  device has working network connectivity.

- **Markdown image fallback and sizing.** The default Coil-backed `MarkdownStyles.imageRenderer`
  no longer collapses to zero height while a remote image is unresolved, which made images appear
  missing in streamed markdown. It now reserves visible space during loading, preserves a real
  aspect ratio once the image resolves, and falls back to the Markdown alt text when the request
  fails.

- **Markdown table styling.** Reworked GFM tables as rounded Material 3 surfaces with a surface
  container background, a tinted header, single interior dividers, and a visible outer outline.
  The outer shape now clips row backgrounds cleanly without cutting away the four rounded corners.

- **Publishing:** moved the `listenablefuture` exclusion from a build-local
  `configurations.all { exclude(...) }` rule onto the `AndroidMath` dependency
  declaration itself. The previous rule only affected the library's own build
  and was stripped from the published POM, so downstream consumers still pulled
  in `com.google.guava:listenablefuture:1.0` transitively and hit
  `Duplicate class com.google.common.util.concurrent.ListenableFuture` at
  merge. Declaring the exclude on the dependency emits `<exclusions>` in the
  POM and `excludes` in the Gradle module metadata, so the duplicate no longer
  reaches consumers. The blanket `configurations.all` rule was removed from
  the library module to avoid polluting the POM with redundant exclusions on
  unrelated dependencies.

### Changed
- **Inline code styling.** Markdown inline code now renders with rounded corners and compact
  horizontal and vertical padding while retaining its configured background and Cascadia Code
  font.

- **Sample app image demo.** Replaced the Wikimedia markdown image URL in the sample stream with
  `https://picsum.photos/200` after Wikimedia responded with HTTP 403 to the app's image request.

- **Attribution:** declared the project as a fork of
  [`NadeemIqbal/llm-typewriter`](https://github.com/NadeemIqbal/llm-typewriter).
  Added a "Credits" section to the README, an upstream copyright notice to the
  LICENSE, and listed NadeemIqbal as an upstream developer in the published POM.
  The fork detached from GitHub's fork network because of substantial divergence
  (Android-only target, streaming LaTeX math, incremental parsing, performance
  work); all code — original and modified — remains Apache 2.0.
- **Sample app:** the single "New stream" button (which auto-cycled the demo
  responses) has been replaced with a row of named stream chips. Tapping a chip
  plays that stream directly, and the currently-loaded stream is highlighted.
  The playback controls (Stop / Resume / Speed) moved into a separate row.

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
