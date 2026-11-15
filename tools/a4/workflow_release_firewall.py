# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
from __future__ import annotations

import argparse
import hashlib
import os
import stat
import sys
from pathlib import Path


BASELINE = {
    "atlas.yaml": "ae306c43d74409deed49dd903ea2f3e63622fb7d69843cd1c2f11b1e2c0f6265",
}
MAX_WORKFLOW_BYTES = 1024 * 1024
REPARSE_POINT = 0x400


def is_link_or_reparse(path: Path) -> bool:
    info = path.lstat()
    return stat.S_ISLNK(info.st_mode) or bool(getattr(info, "st_file_attributes", 0) & REPARSE_POINT)


def digest(data: bytes) -> str:
    return hashlib.sha256(data.replace(b"\r\n", b"\n")).hexdigest()


def scan(root: Path, baseline: dict[str, str] | None = None) -> list[str]:
    expected = BASELINE if baseline is None else baseline
    if not expected:
        return ["empty workflow baseline"]
    root = Path(os.path.abspath(root))
    github = root / ".github"
    workflows = github / "workflows"
    try:
        for directory in (root, github, workflows):
            if is_link_or_reparse(directory) or not directory.is_dir():
                return [f"missing, linked or invalid directory: {directory}"]
        entries = list(workflows.iterdir())
        names = [path.name for path in entries]
        if len({name.casefold() for name in names}) != len(names):
            return ["duplicate workflow names under case-insensitive comparison"]
        unexpected = sorted(set(names) - set(expected))
        missing = sorted(set(expected) - set(names))
        problems = [f"unexpected workflow entry: {name}" for name in unexpected]
        problems.extend(f"missing baseline workflow: {name}" for name in missing)
        for path in entries:
            if path.name not in expected:
                continue
            if is_link_or_reparse(path) or not path.is_file():
                problems.append(f"linked or invalid workflow: {path.name}")
                continue
            if path.stat().st_size > MAX_WORKFLOW_BYTES:
                problems.append(f"oversized workflow: {path.name}")
                continue
            if digest(path.read_bytes()) != expected[path.name]:
                problems.append(f"workflow differs from baseline: {path.name}")
        return problems
    except OSError as exc:
        return [f"cannot inspect workflow baseline: {exc}"]


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare candidate workflows to a fixed local baseline; this is not a release authorization")
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    problems = scan(args.root)
    if problems:
        for problem in problems:
            print(f"DENY: {problem}", file=sys.stderr)
        return 2
    print("BASELINE MATCH: candidate-controlled diagnostic only; release remains unauthorized")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
