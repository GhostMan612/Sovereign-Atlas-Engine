# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
from __future__ import annotations

import fcntl
import hashlib
import os
import re
import stat
import threading


class HandoffDenied(Exception):
    pass


def snapshot(root: str, relative: str, expected_digest: str, limit: int) -> "SealedSnapshot":
    if not hasattr(os, "memfd_create") or not hasattr(os, "O_NOFOLLOW"):
        raise HandoffDenied("Linux sealed descriptors are required")
    if not isinstance(expected_digest, str) or not re.fullmatch(r"[0-9a-f]{64}", expected_digest):
        raise HandoffDenied("invalid expected digest")
    if type(limit) is not int or limit <= 0 or not os.path.isabs(root):
        raise HandoffDenied("invalid root or size limit")
    parts = relative.split("/") if isinstance(relative, str) else []
    root_parts = root.split("/")[1:]
    if not parts or any(p in ("", ".", "..") for p in parts + root_parts):
        raise HandoffDenied("invalid path")
    directory = None
    source = None
    sealed = None
    try:
        directory = os.open("/", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC)
        for part in root_parts + parts[:-1]:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC, dir_fd=directory)
            os.close(directory)
            directory = child
        source = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK | os.O_CLOEXEC, dir_fd=directory)
        info = os.fstat(source)
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_mode & 0o022:
            raise HandoffDenied("source must be a single-link regular file without group or world write")
        sealed = os.memfd_create("atlas-inspection", os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING)
        digest = hashlib.sha256()
        total = 0
        while True:
            chunk = os.read(source, min(1024 * 1024, limit + 1 - total))
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise HandoffDenied("input exceeds size limit")
            digest.update(chunk)
            view = memoryview(chunk)
            while view:
                written = os.write(sealed, view)
                if written <= 0:
                    raise HandoffDenied("snapshot write failed")
                view = view[written:]
        if not total or digest.hexdigest() != expected_digest:
            raise HandoffDenied("snapshot digest mismatch")
        fcntl.fcntl(sealed, fcntl.F_ADD_SEALS, fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL)
        os.lseek(sealed, 0, os.SEEK_SET)
        result = SealedSnapshot(sealed, expected_digest, total)
        sealed = None
        return result
    except OSError as error:
        raise HandoffDenied("cannot create sealed snapshot") from error
    finally:
        for fd in (source, directory, sealed):
            if fd is not None:
                os.close(fd)


class SealedSnapshot:
    def __init__(self, fd: int, digest: str, size: int):
        self._fd = fd
        self.digest = digest
        self.size = size
        self._lock = threading.Lock()

    def consume(self) -> int:
        with self._lock:
            if self._fd is None:
                raise HandoffDenied("snapshot already consumed")
            fd, self._fd = self._fd, None
            return fd

    def close(self) -> None:
        with self._lock:
            if self._fd is not None:
                os.close(self._fd)
                self._fd = None
