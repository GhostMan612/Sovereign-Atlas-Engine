# Candidate distribution evidence preparation — Issue #16

**Status: incomplete; distribution NO-GO.** This is a collection and review ledger, not an approval, notice bundle, SBOM, or signed attestation. No release candidate or final signed artifact is identified in this document. Do not copy historical hashes, counts, dispositions or archive contents into a candidate record without verifying the exact bytes.

The [A1/A2 audit](MESHTASTIC_LICENSING_AUDIT.md) records observations at `ea9f03dcf96b3fd62b09b234927de269c82fc1a4`, not the current local checkpoint or a future distribution artifact. Its historical observations include an SDK JAR without embedded `META-INF/LICENSE*` or `META-INF/NOTICE*` entries, a POM without `<licenses>`, and dependency archives containing additional notices. These observations identify review work; they do not establish the contents of any later artifact or determine applicable legal obligations.

## Separate evidence records

Create a distinct record for each exact deliverable and delivery channel. Each row is **pending**, including artifact identity and authorization.

| Deliverable | Artifact identity needed | Component inventory to reconcile | Notice and source review |
|---|---|---|---|
| SDK Maven publication | JAR, POM, Gradle module metadata and any additional files, each with SHA-256 and exact coordinates | SDK runtime graph, SDK source, pinned schemas and generated classes | Review package contents, POM metadata, notices, source obligations and delivery for the publication as a whole |
| Play APK | Candidate APK digest, flavor, version and signing certificate fingerprint | Play release runtime graph, app source, generated TAK code and included SDK | Review notices and source obligations for this exact APK and distribution channel |
| Enterprise APK | Candidate APK digest, flavor, version and signing certificate fingerprint | Enterprise release runtime graph, app source, generated TAK code and included SDK | Review separately even if its dependency graph matches Play |

For each record, also retain the source commit and tree IDs, clean-workspace status, build inputs and tool versions, Meshtastic pin and schema hashes, the complete normalized resolved graph with artifact hashes, generation and packaging evidence, and any build-time components whose output or terms require review. Inventory coordinates, POM declarations and archive entries are evidence for review; they are not themselves a license or compatibility conclusion.

## Notice reconciliation ledger

For every included source or binary component, record its precise version and artifact digest; observed license text and upstream provenance; every `LICENSE`, `NOTICE`, bundled third-party notice or relevant source notice found; the applicable distribution obligation as determined by an authorized reviewer; the exact delivered location and digest of each required notice; and a reference to the review. Record missing, conflicting and inaccessible evidence as **unknown**. Verify actual delivered archive entries rather than assuming that a dependency archive's notice was retained by APK or JAR packaging. Review transitive, bundled and generated material as well as direct dependencies.

The historical audit specifically identifies these starting points, which must be rechecked against a candidate and are not an exhaustive notice list:

- Pinned Meshtastic schemas and generated SDK classes: identify the applicable rights, license text, attribution and source-delivery disposition for the actual distribution.
- `takmessage.proto` and generated application code: establish origin, version and applicable rights before selecting notice text.
- `okhttp:4.12.0`: inspect the historical `okhttp3/internal/publicsuffix/NOTICE` and its inclusion and treatment in each actual artifact.
- `protobuf-javalite:3.25.5`: inspect embedded `.proto` resources and associated notices as actually resolved and packaged.
- Google/Play and Firebase artifacts: inspect the actual terms, bundled third-party notices and packaging for each flavor; obtain an artifact-specific compatibility and delivery review.
- Atlas source and any adapted material: obtain contributor authority and a reviewed outbound license before asserting a notice or POM license.

### Read-only local verification artifacts observed 2026-11-15

The following files were already present under the private verification repository and `build/reports/distribution/`. Their generation time, source commit, source tree and relationship to each other were not established from their contents. They are mutable local development outputs, not release candidates or independent evidence of approval. No APK was inspected for this observation.

| Local file | SHA-256 | Observation |
|---|---|---|
| `atlas-sdk-1.1.0.jar` | `2341521993f1e919fb09ab69f264653fafe579e461cf7984f0fff9d1de5b4705` | 573 `org/meshtastic/proto/*.class` entries; no `.proto` entries and no archive entries named `LICENSE` or `NOTICE` observed. Class names containing `License` are not license texts. |
| `atlas-sdk-1.1.0.pom` | `dee5f9ebf61be730fb16bdf2b638a68eddc5ac937ada4c1d497e95ae055c0bac` | No `<licenses>` element observed. |
| `atlas-sdk-1.1.0.module` | `0179a2f178ec5bd685a83cec23b0b6a6365e31322632e3db93a704cdb83f24d6` | Module metadata present; no rights disposition inferred. |
| `sdk.json` | `07b8b73320c302be18828e190f2cc0469ee295c85d4957e98c6dbc08edc55893` | Seven resolved artifact entries; license and provenance metadata marked `UNREVIEWED`. |
| `play.json` | `4611bb5894f90593859f71492fc1f4b2fb8554dcdb4b95060c804d2c01f17004` | 149 artifact entries, all with nonempty SHA-256 values; metadata marked `UNREVIEWED`. |
| `enterprise.json` | `8c1945ccbfde8ad34aa3b52e3011fb3ab1592d1b86c0bc965726428719118828` | 149 artifact entries; artifact and dependency arrays equal those in the observed Play report. The report digest differs. |

The local SDK files above were at `apps/atlas-android/build/atlas-private-verification/repository/com/sovereignatlas/atlas-sdk/1.1.0/`; report files were at `apps/atlas-android/build/reports/distribution/`. Counts of resolved artifact files are not comparable directly to the historical audit's count of Maven coordinates. The observed `gate.json` reports HOLD with missing external evidence and unreviewed rights; it is not a current acceptance decision.

For two notice leads, cached dependency files were matched to the observed Play inventory by SHA-256 and inspected without modifying them:

| Dependency and matched archive SHA-256 | Observed entry | Entry SHA-256 | Remaining reconciliation |
|---|---|---|---|
| `com.squareup.okhttp3:okhttp:4.12.0` — `b1050081b14bb7a3a7e55a4d3ef01b5dcfabc453b4573a4fc019767191d5f4e0` | `okhttp3/internal/publicsuffix/NOTICE` | `8a9c58fbded5ea3474315e4a9824ca8b1486098a1a03e897e0b19419d5e1876a` | Confirm actual candidate inclusion and reviewer-determined delivery obligations. |
| `com.google.protobuf:protobuf-javalite:3.25.5` — `79a3ff51ac2a8b1be7fffa228483d64974f17f3effffd966b054d87179e45634` | Ten `google/protobuf/*.proto` resources observed in the cached JAR. | Per-entry hashes not recorded here. | Inspect their terms and actual candidate packaging; no assertion of APK inclusion is made. |

Matching a cache file to an inventory hash does not bind either to a release artifact or establish the legal sufficiency of a notice. Every other dependency and all final artifact notice locations remain to be reconciled.

## Approval and promotion evidence

The independent reviewer must bind any approval to the exact source, dependency inventories, flavor, candidate digest, rights dispositions, notice locations and digests, and source-delivery record. Where signing changes bytes, retain a verifiable binding from the approved unsigned candidate to the signed result, independently verify the signed artifact, and attest its final digest before publication. The source-delivery record must identify the source archive digest, the reviewed contents and a verified delivery location, not merely a URL assertion.

The checkout's [distribution gate](DISTRIBUTION_GATE.md) provides development checks and currently always returns HOLD; it cannot serve as the independent trust root. Independent verifier, reviewer authorization, historical workflow restrictions, protected signing credentials and publisher are not established by this ledger. No new build, signed artifact, external permission or legal sign-off was produced while preparing it.
