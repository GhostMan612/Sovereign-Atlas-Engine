# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
from contextlib import ExitStack
from pathlib import Path


REQUIRED = {
    "play_unsigned_apk": "artifacts/play-unsigned.apk",
    "enterprise_unsigned_apk": "artifacts/enterprise-unsigned.apk",
    "sdk_jar": "artifacts/atlas-sdk.jar",
    "dependencies": "evidence/dependencies.json",
    "licenses": "evidence/licenses.json",
    "source_tree": "evidence/source-tree.json",
    "meshtastic_pin": "evidence/meshtastic-pin.json",
    "tak_rights": "evidence/tak-rights.json",
    "ownership": "evidence/ownership.json",
    "release_disposition": "evidence/release-disposition.json",
}
MESHTASTIC_PIN = "ffd5ad428da6db4a7ddfe62e06548718cc2603e0"
REPOSITORY = "GhostMan612/Sovereign-Atlas-Engine"
MAX_FILE_BYTES = 2 * 1024 * 1024 * 1024


class Denied(Exception):
    pass


def require_secure_platform() -> None:
    if not sys.platform.startswith("linux") or not hasattr(os, "O_NOFOLLOW") or not hasattr(os, "O_DIRECTORY"):
        raise Denied("secure descriptor-relative candidate packing is unavailable on this platform")
    if os.open not in os.supports_dir_fd or os.mkdir not in os.supports_dir_fd:
        raise Denied("secure descriptor-relative candidate packing is unavailable on this platform")


def directory_flags() -> int:
    return os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)


def open_directory(path: Path) -> int:
    absolute = Path(os.path.abspath(path))
    current = os.open(os.path.sep, directory_flags())
    try:
        for part in absolute.parts[1:]:
            next_fd = os.open(part, directory_flags(), dir_fd=current)
            os.close(current)
            current = next_fd
        return current
    except BaseException:
        os.close(current)
        raise


def open_input(root_fd: int, relative: str) -> int:
    parts = relative.split("/")
    parent = os.dup(root_fd)
    try:
        for part in parts[:-1]:
            next_fd = os.open(part, directory_flags(), dir_fd=parent)
            os.close(parent)
            parent = next_fd
        flags = os.O_RDONLY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NONBLOCK", 0)
        fd = os.open(parts[-1], flags, dir_fd=parent)
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_size <= 0 or info.st_size > MAX_FILE_BYTES:
            os.close(fd)
            raise Denied(f"invalid candidate input: {relative}")
        return fd
    finally:
        os.close(parent)


def git_output(repo_fd: int, *args: str) -> str:
    try:
        env = {key: value for key, value in os.environ.items() if not key.upper().startswith("GIT_")}
        env["GIT_CONFIG_NOSYSTEM"] = "1"
        env["GIT_CONFIG_GLOBAL"] = os.devnull
        return subprocess.check_output(
            ["git", "-c", "core.fsmonitor=false", *args],
            text=True, stderr=subprocess.PIPE, env=env, cwd=f"/proc/self/fd/{repo_fd}", pass_fds=(repo_fd,),
        ).strip()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise Denied(f"cannot establish local Git state: {exc}") from exc


def source_revision(repo: Path, repo_fd: int) -> str:
    top = Path(git_output(repo_fd, "rev-parse", "--show-toplevel"))
    if os.path.normcase(os.path.abspath(top)) != os.path.normcase(os.path.abspath(repo)):
        raise Denied("repo root must be the Git checkout root")
    sha = git_output(repo_fd, "rev-parse", "--verify", "HEAD^{commit}")
    branch = git_output(repo_fd, "symbolic-ref", "--quiet", "--short", "HEAD")
    status = git_output(repo_fd, "status", "--porcelain=v1", "--untracked-files=all")
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or branch != "main" or status:
        raise Denied("source must be a committed, clean local main checkout")
    return sha


def disjoint(first: Path, second: Path) -> bool:
    common = os.path.commonpath((str(first), str(second)))
    return common != str(first) and common != str(second)


def write_all(fd: int, data: bytes) -> None:
    view = memoryview(data)
    while view:
        count = os.write(fd, view)
        if count <= 0:
            raise OSError("short candidate output write")
        view = view[count:]


def write_candidate(repo: Path, staging: Path, output: Path) -> None:
    require_secure_platform()
    repo = Path(os.path.abspath(repo))
    staging = Path(os.path.abspath(staging))
    output = Path(os.path.abspath(output))
    if not disjoint(staging, output) or not output.name or output.name in (".", ".."):
        raise Denied("staging and output must be disjoint")
    with ExitStack() as stack:
        repo_fd = open_directory(repo)
        stack.callback(os.close, repo_fd)
        root_fd = open_directory(staging)
        stack.callback(os.close, root_fd)
        output_parent = open_directory(output.parent)
        stack.callback(os.close, output_parent)
        revision = source_revision(repo, repo_fd)
        inputs = []
        for role, relative in REQUIRED.items():
            fd = open_input(root_fd, relative)
            stack.callback(os.close, fd)
            inputs.append((role, relative, fd, os.fstat(fd)))
        os.mkdir(output.name, mode=0o700, dir_fd=output_parent)
        output_fd = os.open(output.name, directory_flags(), dir_fd=output_parent)
        stack.callback(os.close, output_fd)
        for directory in ("artifacts", "evidence"):
            os.mkdir(directory, mode=0o700, dir_fd=output_fd)
        directories = {}
        for directory in ("artifacts", "evidence"):
            fd = os.open(directory, directory_flags(), dir_fd=output_fd)
            stack.callback(os.close, fd)
            directories[directory] = fd
        entries = []
        for role, relative, source_fd, before in inputs:
            directory, name = relative.split("/", 1)
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)
            destination = os.open(name, flags, 0o600, dir_fd=directories[directory])
            try:
                digest = hashlib.sha256()
                count = 0
                while True:
                    chunk = os.read(source_fd, 1024 * 1024)
                    if not chunk:
                        break
                    count += len(chunk)
                    if count > MAX_FILE_BYTES:
                        raise Denied(f"candidate input exceeds limit: {relative}")
                    write_all(destination, chunk)
                    digest.update(chunk)
            finally:
                os.close(destination)
            after = os.fstat(source_fd)
            if count != before.st_size or (before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
                after.st_size, after.st_mtime_ns, after.st_ctime_ns
            ):
                raise Denied(f"candidate input changed while copying: {relative}")
            entries.append({"role": role, "path": relative, "sha256": digest.hexdigest(), "bytes": count})
        if source_revision(repo, repo_fd) != revision:
            raise Denied("local source revision changed during packing")
        manifest = {
            "schema": "sovereign-atlas-candidate/v1",
            "repository": REPOSITORY,
            "source_ref": "refs/heads/main",
            "source_sha": revision,
            "meshtastic_pin": MESHTASTIC_PIN,
            "files": entries,
        }
        encoded = json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)
        manifest_fd = os.open("candidate.json", flags, 0o600, dir_fd=output_fd)
        try:
            write_all(manifest_fd, encoded)
        finally:
            os.close(manifest_fd)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare untrusted candidate data; no signing or publication")
    parser.add_argument("--staging", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--repo-root", required=True, type=Path)
    args = parser.parse_args()
    try:
        write_candidate(args.repo_root, args.staging, args.output)
    except (Denied, OSError, ValueError) as exc:
        print(f"DENY: {exc}", file=sys.stderr)
        return 2
    print("Candidate data written; source identity, evidence, signatures, and publication are not verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
