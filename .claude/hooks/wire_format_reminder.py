"""TASK-142 — surface the wire-format procedure at the moment a format is edited.

Why a hook and not a rule in CLAUDE.md: a written rule depends on the agent still holding it in
context. Long sessions compact, and the rule drifts out. A hook fires on the edit itself, so
remembering is not a precondition for being reminded.

Fires at most ONCE per session (see `already_fired`). A package rename touches a hundred files;
a reminder per file trains the reader to scroll past it, which is worse than no reminder at all.
Once the procedure is in context, repeating it buys nothing.

Contract: reads the PostToolUse payload on stdin, prints a JSON object carrying
`additionalContext`. Never fails the tool call — a hook that breaks editing would be removed
within a day, and then nothing reminds anyone. Any unexpected condition exits quietly.
"""

import json
import os
import re
import sys

REMINDER = """\
This edit touched a wire format. Before finishing, invoke the `wire-format-change` skill — it \
asks at most two questions and then performs the whole mechanic (numbers, mirrored constants in \
workers/*/src/contract/*.ts and firestore.rules, @JsonNames on renames, golden fixtures, checks).

Two facts a diff cannot show, and the reason the skill exists:
  - a renamed field may or may not have kept its meaning — only the author knows;
  - a document assembled as a map or buildJsonObject is invisible to the compiler, so nothing \
will fail to build if its version header goes stale.

If the edit was cosmetic (comment, formatting, test-only), no version change is needed — say so \
and move on."""

# A path worth reminding about. Kotlin files are additionally content-filtered below: most .kt
# files under these trees have nothing to do with the wire.
PATH_PATTERNS = (
    re.compile(r"workers/[^/]+/src/contract/.*\.ts$"),
    re.compile(r"firestore\.rules$"),
    re.compile(r"assets/.*\.json$"),
    re.compile(r"\.kt$"),
)

# What makes a Kotlin file a wire format rather than ordinary code.
KOTLIN_MARKERS = ("WireVersionHeader", "schemaVersion", "minReaderVersion", "WireVersion(")

# Test sources never define a format. Found by the hook firing on its own author: editing
# ArchitectureFitnessTest — which mentions every marker because it checks for them — asked for a
# version bump on a test-only change. A reminder that cries wolf gets ignored, so this matters
# more than it looks.
TEST_MARKERS = (
    "/test/", "/androidTest/", "/commonTest/", "/jvmTest/", "/androidUnitTest/",
    "/androidInstrumentedTest/", "/iosTest/", "/androidRealBackendUnitTest/", "/testFixtures/",
    "firestore-tests/",
)


def already_fired(session_id):
    """True when this session has been reminded. Marker lives under .git/, which is never
    committed and is wiped with the checkout rather than lingering in a temp dir."""
    if not session_id:
        return False
    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    marker_dir = os.path.join(root, ".git", "wire-format-hook")
    marker = os.path.join(marker_dir, re.sub(r"[^A-Za-z0-9_-]", "", session_id)[:64])
    try:
        if os.path.exists(marker):
            return True
        os.makedirs(marker_dir, exist_ok=True)
        with open(marker, "w") as handle:
            handle.write("reminded")
        return False
    except OSError:
        # Cannot track state — better to remind again than to fall silent.
        return False


def is_wire_format(path):
    unix = path.replace("\\", "/")
    if any(marker in unix for marker in TEST_MARKERS):
        return False
    if not any(pattern.search(unix) for pattern in PATH_PATTERNS):
        return False
    if not unix.endswith(".kt"):
        return True
    try:
        with open(path, encoding="utf-8", errors="ignore") as handle:
            body = handle.read()
    except OSError:
        return False
    # The interface's own declaration is not a format.
    if "interface WireVersionHeader" in body:
        return False
    return any(marker in body for marker in KOTLIN_MARKERS)


def main():
    try:
        payload = json.load(sys.stdin)
    except (ValueError, OSError):
        return
    if payload.get("tool_name") not in ("Edit", "Write", "NotebookEdit", "MultiEdit"):
        return
    path = (payload.get("tool_input") or {}).get("file_path")
    if not path or not is_wire_format(path):
        return
    if already_fired(payload.get("session_id")):
        return
    json.dump(
        {
            "hookSpecificOutput": {
                "hookEventName": "PostToolUse",
                "additionalContext": REMINDER,
            }
        },
        sys.stdout,
    )


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Never break the edit. See module docstring.
        pass
