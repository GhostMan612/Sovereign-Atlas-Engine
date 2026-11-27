# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
import sys
import os
import shutil
import stat
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from workflow_release_firewall import BASELINE, digest, scan


def remove_temporary_directory(workspace: Path, target: Path) -> None:
    workspace = Path(os.path.abspath(workspace))
    target = Path(os.path.abspath(target))
    try:
        target.lstat()
    except FileNotFoundError:
        return
    if target.parent != workspace or target.resolve(strict=True).parent != workspace.resolve(strict=True):
        raise RuntimeError("temporary test path is outside the intended workspace")
    for path in (target, *target.parents):
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            raise RuntimeError("temporary test path has a linked or reparse-point ancestor")
        if not stat.S_ISDIR(info.st_mode):
            raise RuntimeError("temporary test path is not a directory")
    shutil.rmtree(target)


class WorkflowReleaseFirewallTests(unittest.TestCase):
    def setUp(self):
        workspace = Path(__file__).resolve().parents[3]
        self.root = Path(tempfile.mkdtemp(prefix="atlas-a4-firewall-", dir=workspace))
        self.addCleanup(remove_temporary_directory, workspace, self.root)
        self.workflows = self.root / ".github" / "workflows"
        self.workflows.mkdir(parents=True)
        self.contents = b"name: approved baseline\non: [pull_request]\n"
        self.baseline = {"atlas.yaml": digest(self.contents)}
        (self.workflows / "atlas.yaml").write_bytes(self.contents)

    def test_exact_baseline_matches_and_line_endings_are_normalized(self):
        self.assertEqual([], scan(self.root, self.baseline))
        (self.workflows / "atlas.yaml").write_bytes(self.contents.replace(b"\n", b"\r\n"))
        self.assertEqual([], scan(self.root, self.baseline))

    def test_ambiguous_and_indirect_release_paths_differ_from_baseline(self):
        additions = (
            b"on: [push]\n",
            b"permissions: write-all\n",
            b"secrets: inherit\n",
            b"run: ./candidate-controlled-script.sh\n",
            b"uses: example/reusable/.github/workflows/release.yml@main\n",
        )
        for addition in additions:
            with self.subTest(addition=addition):
                (self.workflows / "atlas.yaml").write_bytes(self.contents + addition)
                self.assertTrue(scan(self.root, self.baseline))

    def test_additional_or_missing_workflow_is_denied(self):
        extra = self.workflows / "other.yml"
        extra.write_bytes(b"name: extra\n")
        self.assertTrue(scan(self.root, self.baseline))
        extra.unlink()
        (self.workflows / "atlas.yaml").unlink()
        self.assertTrue(scan(self.root, self.baseline))

    def test_symlinked_workflow_is_denied(self):
        path = self.workflows / "atlas.yaml"
        path.unlink()
        target = self.root / "external.yml"
        target.write_bytes(self.contents)
        try:
            path.symlink_to(target)
        except (OSError, NotImplementedError):
            self.skipTest("symlinks unavailable")
        self.assertTrue(scan(self.root, self.baseline))

    def test_empty_baseline_and_missing_directory_are_denied(self):
        self.assertTrue(scan(self.root, {}))
        self.assertTrue(scan(self.root / "missing", self.baseline))

    def test_current_repository_matches_recorded_diagnostic_baseline(self):
        root = Path(__file__).resolve().parents[3]
        self.assertEqual([], scan(root, BASELINE))


if __name__ == "__main__":
    unittest.main()
