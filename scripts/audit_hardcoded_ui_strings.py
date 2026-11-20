#!/usr/bin/env python3
"""
8th Compliance Guard: Hardcoded UI String Audit (Compose Text).

Scans app/src/main/kotlin/com/sbf/lightspeed/ for hardcoded user-facing English
literals inside Compose Text(...) calls that do not resolve via lsString(...),
stringResource(...), getString(...), or LightspeedVocabulary/LightspeedLanguageEngine.

Ignores non-translatable literals (empty strings, numbers/units/percentages,
single symbols/emojis, bullets/separators, [NIGHTLY] dev-only labels, and
technical keys/identifiers) so output stays clean and actionable.

Zero external dependencies (Python 3 stdlib only).
"""

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
SRC_DIR = ROOT_DIR / "app" / "src" / "main" / "kotlin" / "com" / "sbf" / "lightspeed"

TEXT_CALL_RE = re.compile(r"(?<![A-Za-z0-9_])Text\s*\(")
CORE_PREVIEW_FN_RE = re.compile(r"\bfun\s+CoreSetupPreviewBanner\s*\(")

LOCALIZED_RESOLVERS = (
    "lsString(",
    "stringResource(",
    "getString(",
    "LightspeedLanguageEngine.resolve",
    "LightspeedVocabulary",
)

IGNORED_BRANDS_AND_CODES = {
    "lightspeed",
    "lightspeed ∞",
    "github",
    "buy me a coffee",
    "shizuku",
    "xda",
    "xda thread",
    "google play",
    "sha-256",
    "android",
    "settings",
    "nightly",
}

UNIT_TOKENS_RE = re.compile(
    r"\b(?:ms|px|dp|sp|hz|fps|mah|mb|gb|kb|min|mins|sec|secs|hr|hrs|px/s|m/s|w|k|v|x|db|pt|es|en)\b",
    re.IGNORECASE,
)


def strip_kotlin_comments_preserving_lines(text: str) -> str:
    """Replace // and nested /* */ comments with spaces while keeping newlines intact."""
    out: list[str] = []
    i = 0
    n = len(text)
    block_depth = 0
    in_line_comment = False
    in_char = False
    in_str = False
    in_triple = False
    interp_braces: list[int] = []

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""

        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
                out.append("\n")
            else:
                out.append(" ")
            i += 1
            continue

        if block_depth > 0:
            if ch == "/" and nxt == "*":
                block_depth += 1
                out.extend((" ", " "))
                i += 2
                continue
            if ch == "*" and nxt == "/":
                block_depth -= 1
                out.extend((" ", " "))
                i += 2
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue

        if in_triple:
            out.append(ch)
            if text.startswith('"""', i):
                out.extend(('"', '"'))
                i += 3
                in_triple = False
            else:
                i += 1
            continue

        if in_str:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                out.append(nxt)
                i += 2
                continue
            if ch == "$" and nxt == "{":
                out.append("{")
                interp_braces.append(1)
                in_str = False
                i += 2
                continue
            if ch == '"':
                in_str = False
            i += 1
            continue

        if in_char:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                out.append(nxt)
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue

        # Code context (including inside ${...})
        if ch == "/" and nxt == "/":
            in_line_comment = True
            out.extend((" ", " "))
            i += 2
            continue
        if ch == "/" and nxt == "*":
            block_depth = 1
            out.extend((" ", " "))
            i += 2
            continue
        if text.startswith('"""', i):
            in_triple = True
            out.extend(('"', '"', '"'))
            i += 3
            continue
        if ch == '"':
            in_str = True
            out.append(ch)
            i += 1
            continue
        if ch == "'":
            in_char = True
            out.append(ch)
            i += 1
            continue
        if interp_braces:
            if ch == "{":
                interp_braces[-1] += 1
            elif ch == "}":
                interp_braces[-1] -= 1
                if interp_braces[-1] == 0:
                    interp_braces.pop()
                    in_str = True
        out.append(ch)
        i += 1

    return "".join(out)


def find_core_preview_span(code: str) -> tuple[int, int] | None:
    """Return character span of fun CoreSetupPreviewBanner(...) { ... } if present."""
    m = CORE_PREVIEW_FN_RE.search(code)
    if not m:
        return None
    brace_start = code.find("{", m.end())
    if brace_start == -1:
        return None
    depth = 0
    i = brace_start
    n = len(code)
    in_str = False
    in_char = False
    while i < n:
        ch = code[i]
        if in_str:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_str = False
        elif in_char:
            if ch == "\\":
                i += 2
                continue
            if ch == "'":
                in_char = False
        else:
            if ch == '"':
                in_str = True
            elif ch == "'":
                in_char = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return (m.start(), i + 1)
        i += 1
    return None


def extract_text_arg_expression(code: str, open_paren_end: int) -> str | None:
    """Return the `text = ...` or 1st positional argument of a Compose Text(...) call."""
    i = open_paren_end
    n = min(len(code), open_paren_end + 1500)
    depth = 1
    arg_start = i
    args: list[str] = []
    in_str = False
    in_char = False

    while i < n and depth > 0:
        ch = code[i]
        if in_str:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_str = False
        elif in_char:
            if ch == "\\":
                i += 2
                continue
            if ch == "'":
                in_char = False
        else:
            if ch == '"':
                in_str = True
            elif ch == "'":
                in_char = True
            elif ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
                if depth == 0:
                    args.append(code[arg_start:i])
                    break
            elif ch == "," and depth == 1:
                args.append(code[arg_start:i])
                arg_start = i + 1
        i += 1

    if not args:
        return None
    for arg in args:
        stripped = arg.strip()
        if re.match(r"^text\s*=", stripped):
            return re.sub(r"^text\s*=\s*", "", stripped, count=1).strip()
    first = args[0].strip()
    if re.match(r"^[A-Za-z_]\w*\s*=", first):
        return None
    return first


def extract_double_quoted_literals(expr: str) -> list[tuple[str, int, int]]:
    """
    Extract Kotlin double-quoted string literals `(raw_content, start_idx, end_idx)`
    while keeping `${...}` interpolations intact inside the outer literal.
    """
    literals: list[tuple[str, int, int]] = []
    i = 0
    n = len(expr)
    while i < n:
        if expr.startswith('"""', i):
            end = expr.find('"""', i + 3)
            i = (end + 3) if end != -1 else n
            continue
        if expr[i] == "'":
            i += 2
            while i < n and expr[i] != "'":
                if expr[i] == "\\":
                    i += 2
                else:
                    i += 1
            i += 1
            continue
        if expr[i] == '"':
            start = i
            i += 1
            chars: list[str] = []
            brace_depth = 0
            in_nested_str = False
            while i < n:
                ch = expr[i]
                nxt = expr[i + 1] if i + 1 < n else ""
                if brace_depth > 0:
                    chars.append(ch)
                    if in_nested_str:
                        if ch == "\\" and i + 1 < n:
                            chars.append(nxt)
                            i += 2
                            continue
                        if ch == '"':
                            in_nested_str = False
                    else:
                        if ch == '"':
                            in_nested_str = True
                        elif ch == "{":
                            brace_depth += 1
                        elif ch == "}":
                            brace_depth -= 1
                    i += 1
                    continue

                if ch == "\\" and i + 1 < n:
                    chars.append(ch)
                    chars.append(nxt)
                    i += 2
                    continue
                if ch == "$" and nxt == "{":
                    chars.append("${")
                    brace_depth = 1
                    i += 2
                    continue
                if ch == '"':
                    i += 1
                    literals.append(("".join(chars), start, i))
                    break
                chars.append(ch)
                i += 1
            continue
        i += 1
    return literals


def strip_interpolations(raw: str) -> str:
    """Remove `${...}` (nested-brace safe) and `$ident` templates from a Kotlin string."""
    out: list[str] = []
    i = 0
    n = len(raw)
    while i < n:
        ch = raw[i]
        nxt = raw[i + 1] if i + 1 < n else ""
        if ch == "\\" and i + 1 < n:
            if nxt in ("n", "t", "r"):
                out.append(" ")
            elif nxt != "$":
                out.append(nxt)
            i += 2
            continue
        if ch == "$" and nxt == "{":
            depth = 1
            i += 2
            in_nested = False
            while i < n and depth > 0:
                c = raw[i]
                if in_nested:
                    if c == "\\":
                        i += 2
                        continue
                    if c == '"':
                        in_nested = False
                else:
                    if c == '"':
                        in_nested = True
                    elif c == "{":
                        depth += 1
                    elif c == "}":
                        depth -= 1
                i += 1
            out.append(" ")
            continue
        if ch == "$" and (nxt.isalpha() or nxt == "_"):
            i += 1
            while i < n and (raw[i].isalnum() or raw[i] == "_"):
                i += 1
            out.append(" ")
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def is_ignored_literal(raw: str, expr: str, start: int, end: int) -> bool:
    """Filter out non-translatable technical tokens, units, symbols, and dev-only badges."""
    if not raw.strip():
        return True
    if "[NIGHTLY]" in raw or "NightlyFeatures.BADGE_SUFFIX" in raw:
        return True

    before = expr[:start].rstrip()
    after = expr[end:].lstrip()
    if after.startswith("->"):
        return True
    if after.startswith("==") or after.startswith("!=") or before.endswith("==") or before.endswith("!="):
        return True

    static = strip_interpolations(raw).strip()
    if not static:
        return True

    lower = static.lower()
    if lower in IGNORED_BRANDS_AND_CODES:
        return True
    if re.fullmatch(r"[a-z0-9_]+", static) and "_" in static:
        return True
    if re.fullmatch(r"(?:com\.sbf\.|android\.|lsinf-)[A-Za-z0-9_.*\-]+", static, re.IGNORECASE):
        return True
    if re.fullmatch(r"SHA-256\s*:?", static, re.IGNORECASE):
        return True
    if "Copyright (c)" in static or "Licensed under" in static:
        return True
    if re.fullmatch(r"[\s\{\}\[\]\(\)\"':.,;+\-*/=%°•·▸▾◂▴◀▶▲▼⇄✦◈⋮✕✓∞—_~|!?@#&/\\]+", static):
        return True

    without_units = UNIT_TOKENS_RE.sub(" ", static)
    without_units = re.sub(r"m/s²|°[cf]", " ", without_units, flags=re.IGNORECASE)
    if not re.search(r"[A-Za-z]{2,}", without_units):
        return True

    return False


def get_changed_files() -> set[Path]:
    out = subprocess.check_output(
        ["git", "-C", str(ROOT_DIR), "status", "--porcelain"],
        text=True,
    )
    changed: set[Path] = set()
    for line in out.splitlines():
        if not line.strip():
            continue
        rel = line[3:].strip().split(" -> ")[-1]
        p = ROOT_DIR / rel
        if p.suffix == ".kt" and p.exists():
            changed.add(p)
    return changed


def scan_files(files: list[Path]) -> tuple[dict[str, list[tuple[int, str]]], int]:
    findings: dict[str, list[tuple[int, str]]] = defaultdict(list)
    scanned = 0
    for path in files:
        if not path.exists() or not str(path).startswith(str(SRC_DIR)):
            continue
        scanned += 1
        raw_text = path.read_text(encoding="utf-8", errors="ignore")
        code = strip_kotlin_comments_preserving_lines(raw_text)
        preview_span = find_core_preview_span(code) if path.name == "LightspeedCoreSetupPanel.kt" else None
        rel = path.relative_to(SRC_DIR).as_posix()

        for m in TEXT_CALL_RE.finditer(code):
            if preview_span and preview_span[0] <= m.start() < preview_span[1]:
                continue
            expr = extract_text_arg_expression(code, m.end())
            if not expr:
                continue
            if any(tok in expr for tok in LOCALIZED_RESOLVERS):
                continue
            if "[NIGHTLY]" in expr or "NightlyFeatures.BADGE_SUFFIX" in expr:
                continue

            line_no = code.count("\n", 0, m.start()) + 1
            for raw_lit, lit_start, lit_end in extract_double_quoted_literals(expr):
                if is_ignored_literal(raw_lit, expr, lit_start, lit_end):
                    continue
                findings[rel].append((line_no, raw_lit))

    return findings, scanned


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Audit Compose Text(...) calls for hardcoded English UI literals."
    )
    parser.add_argument("--changed", action="store_true", help="Scan only modified Kotlin files")
    parser.add_argument("--summary", action="store_true", help="Print compact per-file summary")
    parser.add_argument("--strict", action="store_true", help="Exit 1 if hardcoded UI strings exist")
    parser.add_argument("--limit", type=int, default=40, help="Max detail lines to print (default: 40)")
    args = parser.parse_args()

    files = sorted(get_changed_files()) if args.changed else sorted(SRC_DIR.rglob("*.kt"))
    findings, scanned = scan_files(files)
    total_hits = sum(len(v) for v in findings.values())

    print("=== Lightspeed Hardcoded UI String Audit (Compose Text) ===")
    print(f"Scanned {scanned} Kotlin files | Files with findings: {len(findings)} | Total literals: {total_hits}")

    if args.summary:
        ranked = sorted(findings.items(), key=lambda kv: (-len(kv[1]), kv[0]))
        for rel, items in ranked[:15]:
            print(f"  {len(items):3d}  {rel}")
        if len(ranked) > 15:
            print(f"  ... and {len(ranked) - 15} more files")
    else:
        printed = 0
        for rel, items in sorted(findings.items()):
            for ln, lit in items:
                if printed >= args.limit:
                    break
                compact = lit.replace("\n", "\\n")
                if len(compact) > 90:
                    compact = compact[:87] + "..."
                print(f'  {rel}:{ln} -> "{compact}"')
                printed += 1
            if printed >= args.limit:
                break
        if total_hits > printed:
            print(f"  ... and {total_hits - printed} more (run with --summary or --limit {total_hits})")

    if total_hits == 0:
        print("✓ PASS: Zero hardcoded UI literals in Compose Text() calls!")
    else:
        print(
            "ℹ ADVISORY: Secondary dialogs/widgets still contain hardcoded English Text(...) literals."
            if not args.strict
            else "❌ FAILED: Hardcoded UI literals found in Compose Text() calls."
        )

    sys.exit(1 if (args.strict and total_hits > 0) else 0)


if __name__ == "__main__":
    main()
