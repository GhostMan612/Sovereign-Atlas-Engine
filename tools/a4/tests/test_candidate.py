# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import create_candidate as candidate


def require_unlinked_path(path):
    path = Path(os.path.abspath(path))
    for component in reversed((path, *path.parents)):
        info = os.lstat(component)
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            raise RuntimeError(f"linked or reparse-point test path: {component}")


def cleanup_fixture(target, workspace, test_area):
    require_unlinked_path(workspace)
    require_unlinked_path(test_area)
    require_unlinked_path(target)
    workspace = Path(workspace).resolve(strict=True)
    test_area = Path(test_area).resolve(strict=True)
    target = Path(target).resolve(strict=True)
    if test_area == workspace or not test_area.is_relative_to(workspace):
        raise RuntimeError("fixture test area is outside the workspace")
    if target.parent != test_area or not target.is_relative_to(workspace):
        raise RuntimeError("fixture target is outside the intended test area")
    shutil.rmtree(target)


class PlatformTests(unittest.TestCase):
    def test_windows_fails_closed(self):
        with mock.patch.object(candidate.sys, "platform", "win32"):
            with self.assertRaises(candidate.Denied):
                candidate.require_secure_platform()


@unittest.skipUnless(sys.platform.startswith("linux") and hasattr(os, "O_NOFOLLOW"), "requires descriptor-relative Linux access")
class CandidateTests(unittest.TestCase):
    def setUp(self):
        workspace = Path(os.path.abspath(Path(__file__).parents[3]))
        require_unlinked_path(workspace)
        work_dir = workspace / "build" / "a4-candidate-tests"
        work_dir.mkdir(parents=True, exist_ok=True)
        require_unlinked_path(work_dir)
        self.root = Path(tempfile.mkdtemp(dir=work_dir))
        self.addCleanup(cleanup_fixture, self.root, workspace, work_dir)
        self.repo = self.root / "repo"
        self.staging = self.root / "staging"
        self.output = self.root / "candidate"
        self.repo.mkdir()
        self.staging.mkdir()
        subprocess.run(["git", "init", "-q", "-b", "main", str(self.repo)], check=True)
        (self.repo / "source.txt").write_text("example source", encoding="utf-8")
        subprocess.run(["git", "-C", str(self.repo), "add", "--", "source.txt"], check=True)
        subprocess.run(
            ["git", "-C", str(self.repo), "-c", "user.email=test@example.invalid", "-c",
             "user.name=Test Fixture", "commit", "-qm", "fixture"], check=True
        )
        for role, relative in candidate.REQUIRED.items():
            path = self.staging / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(("fixture-" + role).encode())

    def test_manifest_matches_written_bytes(self):
        candidate.write_candidate(self.repo, self.staging, self.output)
        manifest = json.loads((self.output / "candidate.json").read_text(encoding="utf-8"))
        self.assertEqual(set(candidate.REQUIRED), {entry["role"] for entry in manifest["files"]})
        for entry in manifest["files"]:
            self.assertEqual(candidate.REQUIRED[entry["role"]], entry["path"])
            content = (self.output / entry["path"]).read_bytes()
            self.assertEqual(len(content), entry["bytes"])
            self.assertEqual(hashlib.sha256(content).hexdigest(), entry["sha256"])

    def test_missing_input_and_dirty_checkout_deny_before_output(self):
        (self.staging / candidate.REQUIRED["sdk_jar"]).unlink()
        with self.assertRaises(OSError):
            candidate.write_candidate(self.repo, self.staging, self.output)
        self.assertFalse(self.output.exists())
        (self.staging / candidate.REQUIRED["sdk_jar"]).write_bytes(b"example")
        (self.repo / "untracked.txt").write_text("dirty", encoding="utf-8")
        with self.assertRaises(candidate.Denied):
            candidate.write_candidate(self.repo, self.staging, self.output)
        self.assertFalse(self.output.exists())

    def test_symlinked_input_and_parent_are_rejected(self):
        input_path = self.staging / candidate.REQUIRED["sdk_jar"]
        input_path.unlink()
        input_path.symlink_to(self.repo / "source.txt")
        with self.assertRaises(OSError):
            candidate.write_candidate(self.repo, self.staging, self.output)
        self.assertFalse(self.output.exists())
        linked_parent = self.root / "linked"
        linked_parent.symlink_to(self.staging, target_is_directory=True)
        with self.assertRaises(OSError):
            candidate.write_candidate(self.repo, linked_parent, self.output)
        self.assertFalse(self.output.exists())

    def test_disjoint_and_existing_output_required(self):
        with self.assertRaises(candidate.Denied):
            candidate.write_candidate(self.repo, self.staging, self.staging / "out")
        self.output.mkdir()
        (self.output / "preserve.txt").write_text("keep", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            candidate.write_candidate(self.repo, self.staging, self.output)
        self.assertEqual("keep", (self.output / "preserve.txt").read_text(encoding="utf-8"))

    def test_open_input_keeps_original_descriptor_after_path_replacement(self):
        root_fd = candidate.open_directory(self.staging)
        self.addCleanup(os.close, root_fd)
        input_path = self.staging / candidate.REQUIRED["sdk_jar"]
        original = input_path.read_bytes()
        fd = candidate.open_input(root_fd, candidate.REQUIRED["sdk_jar"])
        self.addCleanup(os.close, fd)
        replacement = self.root / "replacement"
        replacement.write_bytes(b"replacement")
        os.replace(replacement, input_path)
        self.assertEqual(original, os.read(fd, len(original) + 1))


if __name__ == "__main__":
    unittest.main()
