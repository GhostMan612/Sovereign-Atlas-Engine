# Candidate data packer

This tool assembles untrusted candidate data. It neither signs nor publishes, and it does not approve a release. The A3 distribution gates remain on HOLD; the independent publisher must authenticate source, build provenance, licensing evidence, reviewers and exact candidate bytes under its own policy.

The packer requires an existing clean local Git checkout on a branch named `main`, a staging directory containing the ten fixed inputs listed in `create_candidate.py`, and a new output directory whose parent already exists. The local branch name, Git revision, repository label, schema pin and file names are candidate-supplied claims. In particular, the packer does not prove a connection to remote `main`, prove that artifacts came from the reported tree, determine that APKs are unsigned, assess license evidence, or validate the contents of an input beyond regular nonempty files and size bounds.

Packing is currently supported on Linux with descriptor-relative `open`, `O_NOFOLLOW`, and `/proc/self/fd`. It deliberately fails closed on Windows and other platforms: Python's standard Windows file APIs do not provide the descriptor-relative traversal used here, and Windows junction/reparse-point safety requires a separately reviewed implementation. The packer uses open directory and input descriptors, rejects symlinks in traversed paths, and copies and hashes bytes from the same open input descriptors. It detects size or timestamp changes during copy, but a hostile writer or privileged system actor can still affect inputs; the output and its claims are never a trust anchor. Run in an isolated workspace with trusted control of the Git metadata, local Git configuration, and output parent; Git status can consult repository configuration. Git's ordinary clean-status check does not enumerate ignored files.

The output directory must not already exist and must not overlap staging. If a failure occurs after output creation, the tool leaves an incomplete directory in place without attempting recursive deletion. `candidate.json` is written last; its absence indicates an incomplete bundle. Do not reuse it; inspect and remove it through the operator's normal cleanup process before retrying.

Example on a supported host, with paths chosen by the operator:

```sh
python3 tools/a4/create_candidate.py --repo-root /path/to/atlas --staging /path/to/staging --output /path/to/new-candidate
```

The manifest binds hashes to the bytes copied into the bundle. It is not a signed approval or independent provenance attestation, and a future verifier must validate the complete manifest, expected file set and policy against independently trusted evidence.

`workflow_release_firewall.py` compares the local workflow set to a fixed candidate-controlled baseline. Any workflow change is denied until the baseline is deliberately reviewed and updated. A match is only a regression signal: it does not examine indirect scripts, authenticate the baseline, protect historical refs, or authorize release. The existing A3 hold and external controls remain necessary.

The host tests use `python3 -m unittest discover -s tools/a4/tests`. Linux exercises packing and path handling; other platforms only exercise the explicit denial and skip Linux-specific cases. A passing non-Linux test run is not proof that candidate packing works on that platform.
