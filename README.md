# JetBrains task 15/03/2026

## Integration of an external terminal emulator into the IntelliJ Terminal

## Introduction

This project is part of JetBrains internship exploring the integration of an external terminal emulator.
The goal of this project is to demonstrate underlying principles of terminal emulation and how they could be integrated
into the IntelliJ Terminal.

### Task - Implement a terminal text buffer — the core data structure that terminal emulators use to store and manipulate displayed text.

The core data structure to store and manipulate displayed text. This buffer is a grid of character cells
(each with a character, foreground/background color, and style flags like bold/italic/underline),
a cursor position, and two logical regions: a visible screen and a scrollback history.
The implementation must support cursor movement, text writing/insertion,
line operations, screen clearing, and content access — all in Java or Kotlin
with no external libraries (except for testing).

## Solution explanation
### Architecture overview
`Cell.java` (mutable) - unit holding one character and cell attributes like colors and style. Uses `\0' for empty cells.

`CellAttributes.java` (immutable) - foreground `Color`, background `Color`, and `Set<Style>`. Shared `DEFAULT` to avoid unnecessary allocations.

`TerminalLine.java` - fixed-width array of Cells representing one line in the terminal. Contains a wrapped flag to distinguish soft wraps from the hard line breaks.

`TerminalBuffer.java` - the core. Holds a `TerminalLine[] screen` which is a fixed-sized array for the visible area and an `ArrayList<TerminalLine> scrollback` for history. Tracks cursor position, current attributes, and a `pendingWrap` flag.

`TerminalEmulator.java` — wires it all together, spawns a process, pipes stdout through the parser, renders the buffer.

### How the buffer works
Screen is the bottom N lines. Scrollback stores lines that scroll off the top (capped at `maxScrollbackSize`, oldest evicted first).
Cursor is 0-based within the screen. Writing advances the cursor. At end-of-line, pendingWrap is set the actual wrap/scroll only happens when the next character arrives.
`currentAttributes` are stamped onto every cell written.

### Key mechanism `pendingWrap`
Real terminals don't wrap the instant the cursor hits the last column. The wrap is deferred until the next printable character arrives.

### Scrollback management
Lines pushed off the top of the screen go into the scrollback. When the scrollback exceeds `maxScrollbackSize`, the oldest lines are dropped.
Scrollback is read-only from the buffer API.

### Soft-wrap flag on TerminalLine
Each line tracks whether it was soft-wrapped (auto-wrapped at terminal width) vs. hard-broken (`\n`).
This is important for the resize/reflow bonus: soft-wrapped lines are merged back into logical lines and re-wrapped at the new width, while hard breaks are preserved.

### Trade-offs and design decisions

#### Mutable cells vs. immutable attributes

`Cell` mutability is chosen for simplicity and performance. Terminal buffers are often updated in-place as text is written,
and immutability would require more complex copying logic.
`CellAttributes` is immutable and shared since many cells have the same attributes (e.g. white-on-black), so sharing it reduces memory usage.
The trade-off is that changing attributes requires creating new `CellAttributes` object, but attributes change less frequently than cell content.

#### `TerminalLine[]` as fixed array for screen, ArrayList for scrollback
Screen is aways exactly height lines (fixed array), which avoids resizing overhead and makes access simpler.

Scrollback grows and shrinks dynamically. Oldest lines are evicted using `sublist(0, excess).clear()` which is O(n) but only triggers once the cap is exceeded.
A `LinkedList` or a custom circular buffer would make eviction O(1) but random access for `getScrollbackLine(row)` would become O(n). Chosen approach prioritizes fast read access.

### Deferred wrap (`pendingWrap`) over immediate wrap
An immediate wrap would be simpler to implement, however it could cause blank lines when a program writes exactly `width` characters without a newline.
The deferred wrap avoids this at the cost of one extra boolean check on every `writeChar`.


### Resize reflow strategy

Soft wrapped lines are merged back to logical lines and re-wrapped at the new width. Hard breaks are always preserved.
Simpler strategy would be to trunicate lines on resize. The reflow approach is more complex but provides a better user
experience when resizing the terminal, as it preserves all content and formatting.

### Scrollback is read-only from the public API
No methods to edit scrollback lines. The buffer has only one "active" editing surface (screen).

### Erase operations use currentAttributes for cleared cells
When erased with a colored background active, the erased cells get that background color.
A simpler approach would always reset to `DEFAULT`.




### How to build and run the project

#### Prerequisites
- Java 17+
- Gradle wrapper is included

#### Build
```bash
./gradlew build
```

#### Run tests
```bash
./gradlew test
```
Test reports are generated in `build/reports/tests/test/index.html`.

#### Run demos
All demos are in the `org.example.demo` package. Run them via the unified `Main` launcher:

```bash
./gradlew run --args="buffer"
./gradlew run --args="emulator"
./gradlew run --args="all"
./gradlew run --args='emulator "ls -la"'
```

Or run from your IDE by launching `org.example.Main` with the appropriate argument.

- **BufferDemo** — exercises the `TerminalBuffer` API directly (write, insert, fill, scroll, resize) without a shell or parser.

- **EmulatorDemo** — runs real shell commands (`echo`, `ls`, ANSI color sequences) through the full pipeline: shell → `AnsiParser` → `TerminalBuffer` → rendered output.

### Testing

173 tests using JUnit 5 with `@Nested` groups and `@DisplayName` for readable output. No external mocking libraries — tests exercise the public API directly.

`TerminalBufferTest` (78 tests) — core buffer operations: construction, cursor movement, writing, insertion, scrolling, `pendingWrap`, erase in display/line, delete/insert characters, clear screen, scrollback access, and edge cases.

`AnsiParserTest` (45 tests) — escape sequence parsing: plain text and control chars, CSI cursor movement, erase sequences, SGR colors and styles, scrolling, insert/delete, and edge cases like unknown sequences or incomplete escapes.

`ResizeTest` (26 tests) — resize and reflow: soft-wrapped lines merge and re-wrap at new width, hard breaks preserved, height-only resize, cursor tracking through reflow, attribute preservation, and invalid dimension validation.

`ModelTest` (24 tests) — data model classes: `Cell`, `CellAttributes`, `Color`/`Style` enums, `TerminalLine` char/attribute access, wrapped flag, clear, and out-of-bounds checks.

### Bonus features

**Resize with reflow** — `TerminalBuffer.resize(newWidth, newHeight)` re-wraps content when the width changes. Soft-wrapped lines are merged back into logical lines and re-wrapped at the new width; hard line breaks (`\n`) are always preserved. Height-only changes move lines between scrollback and screen without reflow. The cursor position is tracked through the transformation and stays on screen.

**ANSI parser** — `AnsiParser` is a state-machine parser that translates raw terminal output (CSI cursor movement, SGR colors/styles, erase sequences, scroll, insert/delete, OSC, charset selection) into `TerminalBuffer` operations. Not required by the task, but necessary to demonstrate the buffer working with real shell output.

**Terminal emulator** — `TerminalEmulator` wires everything together: spawns a process, pipes its stdout through the parser into the buffer, and renders the result. The demos use this to run real shell commands (`echo`, `ls`, ANSI color sequences) end-to-end.

### Possible improvements

**Wide character support** — CJK ideographs and emoji occupy 2 cells in real terminals. Currently every character takes exactly one cell. Proper support would require a width lookup (e.g. Unicode East Asian Width), writing a placeholder in the second cell, and adjusting cursor movement, deletion, and reflow accordingly.

**Scrolling regions** — `CSI r` (DECSTBM) sets a top/bottom scroll margin. Programs like `vim`, `less`, and `tmux` rely on this heavily. Currently ignored.

**Alternate screen buffer** — fullscreen programs (`vim`, `htop`) switch to a separate screen buffer and restore the original on exit. Not implemented — the buffer is always the same.

**256-color and true-color** — the parser recognizes `38;5;n` and `38;2;r;g;b` sequences but skips the actual color values. The `Color` enum only holds the 16 standard colors. Supporting extended colors would require replacing the enum with a richer type.

**Cursor save/restore** — `ESC 7` / `ESC 8` (DECSC/DECRC) and `CSI s` / `CSI u` are not yet wired up. Shells like zsh use these for right-prompt rendering.

**Circular buffer for scrollback** — current scrollback uses an `ArrayList` with `subList().clear()` eviction (O(n)). A circular buffer would make eviction O(1) while keeping random access O(1).


### LLM usage

Used GitHub Copilot (Chat + inline completions) as a productivity tool for routine tasks throughout the project:
-
- Generating boilerplate — repetitive getters, `equals`/`hashCode`, enum switch arms, test scaffolding.
- Writing some Javadocs and structure of README.
- Autocompleting repetitive test cases — e.g. filling out similar assertions across `@Nested` groups.
- Gradle configuration tweaks — adding the `application` plugin, wiring `mainClass`.
- CI/CD pipeline setup — GitHub Actions workflow for automated build and test.

All architecture decisions, algorithm design (reflow, pending wrap, parser state machine), and debugging were done manually.
The LLM sped up the mechanical parts but did not drive the design.
