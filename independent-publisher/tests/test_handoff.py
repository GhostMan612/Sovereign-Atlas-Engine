# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
import hashlib
import os
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from atlas_publisher.handoff import HandoffDenied, snapshot


@unittest.skipUnless(hasattr(os, "memfd_create"), "Linux sealed memfd required")
class HandoffTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / "artifact"
        self.data = b"verified candidate bytes"
        self.path.write_bytes(self.data)
        self.digest = hashlib.sha256(self.data).hexdigest()

    def create(self):
        return snapshot(str(self.root), "artifact", self.digest, 1024)

    def test_sealed_snapshot_survives_path_substitution(self):
        handoff = self.create()
        self.path.unlink()
        self.path.write_bytes(b"replacement")
        fd = handoff.consume()
        self.addCleanup(os.close, fd)
        self.assertEqual(os.read(fd, 1024), self.data)
        with self.assertRaises(PermissionError):
            os.write(fd, b"tamper")

    def test_one_shot_snapshot_rejects_in_process_replay(self):
        handoff = self.create()
        fd = handoff.consume()
        self.addCleanup(os.close, fd)
        with self.assertRaises(HandoffDenied):
            handoff.consume()

    def test_concurrent_consumers_only_one_succeeds(self):
        handoff = self.create()
        barrier = threading.Barrier(3)
        results = []

        def consume():
            barrier.wait()
            try:
                results.append(handoff.consume())
            except HandoffDenied:
                results.append(None)

        threads = [threading.Thread(target=consume) for _ in range(2)]
        for thread in threads:
            thread.start()
        barrier.wait()
        for thread in threads:
            thread.join()
        descriptors = [fd for fd in results if fd is not None]
        for fd in descriptors:
            os.close(fd)
        self.assertEqual(len(descriptors), 1)

    def test_symlink_and_hardlink_rejected(self):
        (self.root / "link").symlink_to(self.path)
        with self.assertRaises(HandoffDenied):
            snapshot(str(self.root), "link", self.digest, 1024)
        os.link(self.path, self.root / "hardlink")
        with self.assertRaises(HandoffDenied):
            self.create()

    def test_group_or_world_writable_rejected(self):
        self.path.chmod(0o666)
        with self.assertRaises(HandoffDenied):
            self.create()

    def test_replacement_during_read_cannot_change_snapshot(self):
        original_read = os.read
        replaced = False

        def replacing_read(fd, count):
            nonlocal replaced
            data = original_read(fd, count)
            if not replaced:
                replaced = True
                self.path.unlink()
                self.path.write_bytes(b"replacement")
            return data

        with patch("atlas_publisher.handoff.os.read", side_effect=replacing_read):
            handoff = self.create()
        fd = handoff.consume()
        self.addCleanup(os.close, fd)
        self.assertEqual(os.read(fd, 1024), self.data)

    def test_in_place_modification_during_read_rejected(self):
        original_read = os.read
        modified = False

        def modifying_read(fd, count):
            nonlocal modified
            if not modified:
                modified = True
                self.path.write_bytes(b"concurrent tampering")
            return original_read(fd, count)

        with patch("atlas_publisher.handoff.os.read", side_effect=modifying_read):
            with self.assertRaises(HandoffDenied):
                self.create()

    def test_intermediate_symlink_rejected(self):
        (self.root / "nested").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(HandoffDenied):
            snapshot(str(self.root), "nested/artifact", self.digest, 1024)

    def test_changed_bytes_and_size_limit_rejected(self):
        self.path.write_bytes(b"substituted bytes")
        with self.assertRaises(HandoffDenied):
            self.create()
        self.path.write_bytes(self.data)
        with self.assertRaises(HandoffDenied):
            snapshot(str(self.root), "artifact", self.digest, 4)
