#!/usr/bin/env python3
"""
Rename namespace:  io.github.nadeemiqbal  →  cc.ptoe
                  github.com/NadeemIqbal/llm-typewriter  →  github.com/ECSDevs/llm-typewriter
                  developer: NadeemIqbal / Nadeem Iqbal  →  originalFactor <2438926613@qq.com>

Three-phase execution ( originals are NEVER touched until you confirm ):
  1. COPY   — create new files at new paths with replaced content
  2. REVIEW — print diffs + path mappings, wait for "yes"
  3. DELETE — remove old source files, replace content-only files
"""

import sys
import os
import shutil
from pathlib import Path
from difflib import unified_diff

# ─── Configuration ──────────────────────────────────────────────────────────

# Content replacements — ORDER MATTERS (specific before general)
REPLACEMENTS = [
    # Repo URLs (org name) — must run before standalone NadeemIqbal rule
    ("github.com/NadeemIqbal/llm-typewriter", "github.com/ECSDevs/llm-typewriter"),
    ("github.com/NadeemIqbal/prompt-bar",      "github.com/ECSDevs/prompt-bar"),
    # Developer profile URL (standalone, after repo URLs consumed)
    ("github.com/NadeemIqbal",                 "github.com/originalFactor"),
    # Developer id (POM <id>)
    ("NadeemIqbal",                             "originalFactor"),
    # Developer name / copyright holder
    ("Nadeem Iqbal",                            "originalFactor"),
    # Developer email
    ("mr_nadeem_iqbal@yahoo.com",               "2438926613@qq.com"),
    # Maven namespace / groupId / Kotlin package
    ("io.github.nadeemiqbal",                   "cc.ptoe"),
]

# Filesystem path segment to replace
OLD_PATH_SEGMENT = os.path.join("io", "github", "nadeemiqbal")
NEW_PATH_SEGMENT = os.path.join("cc", "ptoe")

# Old directory names eligible for cleanup after file deletion
OLD_DIR_NAMES = {"io", "github", "nadeemiqbal"}

# Directories to skip during traversal
SKIP_DIRS = {
    ".git", ".gradle", "build", ".idea", ".kotlin", ".kotlin-js-store",
    "node_modules", "captures", ".cxx",
}

# Extensions processed as text
TEXT_EXTENSIONS = {".kt", ".kts", ".md", ".toml", ".properties", ".yml", ".yaml", ".txt", ".xml", ""}

# Temporary suffix for content-only files (review phase)
RENAMED_SUFFIX = ".renamed"


# ─── ANSI colors ─────────────────────────────────────────────────────────────

class C:
    RED = "\033[31m"
    GREEN = "\033[32m"
    CYAN = "\033[36m"
    DIM = "\033[2m"
    BOLD = "\033[1m"
    RESET = "\033[0m"


# ─── Helpers ─────────────────────────────────────────────────────────────────

def apply_replacements(content: str) -> str:
    for old, new in REPLACEMENTS:
        content = content.replace(old, new)
    return content


def count_replacements(content: str) -> list:
    """Return [(old_string, count), ...] for each rule that matched."""
    results = []
    for old, _ in REPLACEMENTS:
        n = content.count(old)
        if n:
            results.append((old, n))
    return results


def is_text_file(path: Path) -> bool:
    return path.suffix in TEXT_EXTENSIONS


def find_files(root: Path):
    """
    Walk the project tree. Categorize affected files into:
      path_moves   — file is under io/github/nadeemiqbal/, needs path + content change
      content_only — file has namespace text but path stays (in-place content edit)
    """
    path_moves = []
    content_only = []
    old_seg_posix = OLD_PATH_SEGMENT.replace("\\", "/")
    new_seg_posix = NEW_PATH_SEGMENT.replace("\\", "/")

    for dirpath, dirnames, filenames in os.walk(root):
        # Prune skip dirs in-place
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]

        for fname in filenames:
            fpath = Path(dirpath) / fname
            rel = fpath.relative_to(root)
            rel_posix = str(rel).replace("\\", "/")

            # Skip already-created .renamed files (idempotency)
            if fname.endswith(RENAMED_SUFFIX):
                continue

            if old_seg_posix in rel_posix:
                # File is under io/github/nadeemiqbal/ — needs path move + content rename
                new_rel_posix = rel_posix.replace(old_seg_posix, new_seg_posix)
                new_path = root / new_rel_posix.replace("/", os.sep)
                path_moves.append((fpath, new_path))
            else:
                if not is_text_file(fpath):
                    continue
                try:
                    content = fpath.read_text(encoding="utf-8")
                except (UnicodeDecodeError, PermissionError):
                    continue
                if any(old in content for old, _ in REPLACEMENTS):
                    content_only.append(fpath)

    return path_moves, content_only


def make_diff(old_content: str, new_content: str, filename: str) -> str:
    diff = unified_diff(
        old_content.splitlines(keepends=True),
        new_content.splitlines(keepends=True),
        fromfile=f"a/{filename}",
        tofile=f"b/{filename}",
    )
    return "".join(diff)


def print_colored_diff(diff_text: str):
    for line in diff_text.splitlines():
        if line.startswith("+++") or line.startswith("---"):
            print(f"    {C.DIM}{line}{C.RESET}")
        elif line.startswith("+"):
            print(f"    {C.GREEN}{line}{C.RESET}")
        elif line.startswith("-"):
            print(f"    {C.RED}{line}{C.RESET}")
        elif line.startswith("@@"):
            print(f"    {C.CYAN}{line}{C.RESET}")
        else:
            print(f"    {C.DIM}{line}{C.RESET}")


# ─── Phase 1: Copy ───────────────────────────────────────────────────────────

def phase_copy(root: Path, path_moves, content_only):
    """Create new files with renamed content. Originals are NOT touched."""
    created = []

    # Path-moved files: write to new path with replaced content
    for old_path, new_path in path_moves:
        new_path.parent.mkdir(parents=True, exist_ok=True)
        if is_text_file(old_path):
            content = old_path.read_text(encoding="utf-8")
            new_content = apply_replacements(content)
            new_path.write_text(new_content, encoding="utf-8")
        else:
            shutil.copy2(old_path, new_path)
        created.append(("MOVE", old_path, new_path))

    # Content-only files → write to <file>.renamed (original untouched)
    for fpath in content_only:
        content = fpath.read_text(encoding="utf-8")
        new_content = apply_replacements(content)
        renamed = fpath.with_suffix(fpath.suffix + RENAMED_SUFFIX)
        renamed.write_text(new_content, encoding="utf-8")
        created.append(("CONT", fpath, renamed))

    return created


# ─── Phase 2: Review ────────────────────────────────────────────────────────

def phase_review(root: Path, path_moves, content_only):
    """Print detailed review and wait for confirmation."""
    print()
    print(f"{C.BOLD}{'=' * 72}{C.RESET}")
    print(f"{C.BOLD}  PHASE 2: REVIEW — inspect created files before deletion{C.RESET}")
    print(f"{C.BOLD}{'=' * 72}{C.RESET}")

    # Path moves
    print(f"\n{C.CYAN}{'─' * 72}{C.RESET}")
    print(f"  PATH MOVES ({len(path_moves)} files — moved from io/github/nadeemiqbal/ to cc/ptoe/)")
    print(f"{C.CYAN}{'─' * 72}{C.RESET}")
    for old, new in path_moves:
        old_rel = old.relative_to(root)
        new_rel = new.relative_to(root)
        print(f"  {old_rel}")
        print(f"    {C.GREEN}→ {new_rel}{C.RESET}")

    # Content-only changes with diffs
    print(f"\n{C.CYAN}{'─' * 72}{C.RESET}")
    print(f"  CONTENT CHANGES ({len(content_only)} files — .renamed created alongside original)")
    print(f"{C.CYAN}{'─' * 72}{C.RESET}")
    for fpath in content_only:
        rel = fpath.relative_to(root)
        renamed = fpath.with_suffix(fpath.suffix + RENAMED_SUFFIX)
        old_content = fpath.read_text(encoding="utf-8")
        new_content = renamed.read_text(encoding="utf-8")
        counts = count_replacements(old_content)

        print(f"\n  {C.BOLD}▸ {rel}{C.RESET}")
        for old_str, cnt in counts:
            print(f"      {cnt}×  \"{old_str}\"")

        diff = make_diff(old_content, new_content, str(rel))
        print_colored_diff(diff)

    # Summary
    print(f"\n{C.CYAN}{'─' * 72}{C.RESET}")
    print(f"  SUMMARY")
    print(f"{C.CYAN}{'─' * 72}{C.RESET}")
    print(f"  Path-moved files:   {len(path_moves)}")
    print(f"  Content-only files: {len(content_only)}")
    print(f"  Total affected:     {len(path_moves) + len(content_only)}")
    print()
    print(f"  {C.BOLD}New files are on disk — open them in your IDE to inspect:{C.RESET}")
    print(f"    • Path-moved files are at the new {C.CYAN}cc/ptoe/{C.RESET} locations")
    print(f"    • Content-only files have a {C.CYAN}.renamed{C.RESET} sibling")
    print()
    print(f"  {C.BOLD}Type 'yes' to DELETE old files and finalize the rename.{C.RESET}")
    print(f"  Anything else aborts (new files remain on disk for manual cleanup).")
    print()

    answer = input("  Proceed with deletion? [yes/no]: ").strip().lower()
    return answer == "yes"


# ─── Phase 3: Delete ────────────────────────────────────────────────────────

def phase_delete(root: Path, path_moves, content_only):
    """Delete old files, replace content-only files with .renamed versions."""
    print()
    print(f"{C.BOLD}{'=' * 72}{C.RESET}")
    print(f"{C.BOLD}  PHASE 3: DELETE — removing old files{C.RESET}")
    print(f"{C.BOLD}{'=' * 72}{C.RESET}")

    # Delete path-moved old source files
    deleted = 0
    for old_path, _ in path_moves:
        try:
            old_path.unlink()
            deleted += 1
        except FileNotFoundError:
            pass

    # Clean up empty io/github/nadeemiqbal directories (deepest first)
    dirs_to_clean = set()
    for old_path, _ in path_moves:
        parent = old_path.parent
        while parent != root and parent.name in OLD_DIR_NAMES:
            dirs_to_clean.add(parent)
            parent = parent.parent

    removed_dirs = 0
    for d in sorted(dirs_to_clean, key=lambda p: len(p.parts), reverse=True):
        try:
            d.rmdir()  # Only removes if empty
            removed_dirs += 1
        except OSError:
            pass  # Not empty — skip

    # Replace content-only files: delete original, rename .renamed → original
    replaced = 0
    for fpath in content_only:
        renamed = fpath.with_suffix(fpath.suffix + RENAMED_SUFFIX)
        fpath.unlink()
        renamed.rename(fpath)
        replaced += 1

    print(f"\n  Deleted old source files:  {deleted}")
    print(f"  Replaced content files:    {replaced}")
    print(f"  Removed empty directories:  {removed_dirs}")
    print(f"\n  {C.GREEN}✅ Rename complete!{C.RESET}")
    print(f"\n  Next steps:")
    print(f"    • Run {C.CYAN}./gradlew build{C.RESET} to verify compilation")
    print(f"    • Run {C.CYAN}git status{C.RESET} to review all changes")
    print(f"    • Update your Maven Central namespace at central.sonatype.com")
    print(f"    • Update GitHub repo URL if the repo has been transferred")


# ─── Main ────────────────────────────────────────────────────────────────────

def main():
    # Enable ANSI colors on Windows 10+
    if sys.platform == "win32":
        os.system("")

    # Determine project root
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    if not (root / "settings.gradle.kts").exists():
        print(f"{C.RED}ERROR:{C.RESET} 'settings.gradle.kts' not found in {root}")
        print(f"       Pass the project root as argument: python rename_namespace.py <path>")
        sys.exit(1)

    print(f"{C.BOLD}Namespace Rename Script{C.RESET}")
    print(f"Project root: {root}")
    print()
    print("Replacement rules:")
    for old, new in REPLACEMENTS:
        print(f"  {C.RED}{old}{C.RESET}  →  {C.GREEN}{new}{C.RESET}")
    print(f"  {C.RED}{OLD_PATH_SEGMENT}{C.RESET}  →  {C.GREEN}{NEW_PATH_SEGMENT}{C.RESET}  (directory paths)")

    # Find affected files
    print("\nScanning for affected files...")
    path_moves, content_only = find_files(root)

    if not path_moves and not content_only:
        print(f"\n{C.GREEN}No files containing the old namespace found. Nothing to do.{C.RESET}")
        return

    print(f"  Path-moved files:   {len(path_moves)}")
    print(f"  Content-only files: {len(content_only)}")

    # Phase 1: Copy
    print()
    print(f"{C.BOLD}{'=' * 72}{C.RESET}")
    print(f"{C.BOLD}  PHASE 1: COPY — creating new files with renamed content{C.RESET}")
    print(f"{C.BOLD}{'=' * 72}{C.RESET}")
    created = phase_copy(root, path_moves, content_only)
    for kind, old, new in created:
        print(f"  [{kind:4}] {old.relative_to(root)}")
        print(f"         {C.GREEN}→ {new.relative_to(root)}{C.RESET}")

    # Phase 2: Review
    if not phase_review(root, path_moves, content_only):
        print(f"\n  {C.RED}Aborted.{C.RESET} New files are still on disk:")
        print(f"    • Path-moved files under {C.CYAN}cc/ptoe/{C.RESET}")
        print(f"    • Content files with {C.CYAN}.renamed{C.RESET} suffix")
        print(f"  Delete them manually if not needed.")
        return

    # Phase 3: Delete
    phase_delete(root, path_moves, content_only)


if __name__ == "__main__":
    main()
