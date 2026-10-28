# ContextForge Development Tasks

> **Status:** M1.1 is in review. Later tasks are not implemented. This file is a task checklist, not a claim that those later features exist.
>
> **Source of truth for requirements:** [docs/PRD.md](docs/PRD.md). Read [docs/idea.md](docs/idea.md) for the project vision. If this checklist and the PRD disagree, discuss the discrepancy in a PR before implementation.

## Ownership and workflow

- **Repository maintainer:** prepares the initial Java project skeleton, reviews PRs, manages releases and GitHub settings.
- **Java developer:** owns the Java core implementation from M1.2 onward, including algorithms, tests, and performance work.
- Reviewers provide feedback; ownership describes the proposed division of work, not an access restriction.
- Work on a feature branch per small task, open a PR against `main`, and merge only after review. Follow [CONTRIBUTING.md](CONTRIBUTING.md).
- Update this checklist in the same PR that completes a task. Mark a checkbox only after its acceptance criteria and automated checks pass. Link the PR or issue in the completion note.
- Never commit private text corpora, credentials, generated binaries, or benchmark claims without measurements.
- No mandatory LLM, network service, or external provider dependency in the core.

## M0 — Repository foundation

- [x] Document the project vision in `docs/idea.md`.
- [x] Write and review the requirements in `docs/PRD.md`.
- [x] Add README, Apache-2.0 license, contributing guide, `.gitignore`, and `.editorconfig`.

## M1 — Foundation library

### M1.1 — Java project bootstrap (maintainer)

- [x] Configure Java 21, Gradle Kotlin DSL, and Maven coordinates `io.github.cbrwvy:contextforge`.
- [x] Commit a functioning Gradle Wrapper and configure JUnit Jupiter.
- [x] Create one dependency-free core JAR and minimal package structure.
- [x] Add immutable foundational identifiers and 64-bit Unicode code-point span types, with validation tests.
- [x] Add a basic GitHub Actions compile/test workflow and README build instructions.
- [x] Confirm `./gradlew test build` passes and open a reviewable PR.

Local `./gradlew test build` passed on Temurin 21. The completing pull request is linked after it is opened.

**Handoff gate:** M1.1 must be merged into `main` before the Java developer starts implementation branches based on it.

### M1.2 — Input and streaming decode (Java developer)

- [ ] Support `String`/`CharSequence`, `Reader`, `InputStream` with explicit charset, and structured messages.
- [ ] Define source ownership and validation behavior; reject malformed charset input.
- [ ] Process incremental reads, including code points split across chunks, without buffering an entire Preserve stream.
- [ ] Add ASCII, Cyrillic, CJK, emoji/ZWJ, combining-mark, BOM, and invalid-UTF-8 fixtures.
- [ ] Test a generated input larger than the configured test heap; verify bounded-memory streaming behavior.

**Done when:** input contracts and error behavior follow PRD M1 and tests pass.

### M1.3 — Normalization and segmentation (Java developer)

- [ ] Implement `none`, newline, and whitespace normalization profiles; do not normalize by default.
- [ ] Maintain mappings to original decoded-text **64-bit code-point** offsets.
- [ ] Handle CRLF split across reads and all documented line separators.
- [ ] Implement message, document, and paragraph segmenters.
- [ ] Account for segment delimiters and blank lines in provenance/partition reporting.
- [ ] Add determinism, boundary, and quote-fidelity tests.

**Done when:** output spans map to the correct original text and transforms are recorded.

### M1.4 — Preserve mode and milestone closure (Java developer)

- [ ] Implement Preserve-mode `write` into caller-provided sinks with bounded buffering.
- [ ] Implement the safe default listener, processing statistics, and output/report semantics.
- [ ] Keep full source text out of default logs, listeners, and exceptions.
- [ ] Verify code-point round-trip with no transforms; verify transformed output is labeled correctly.
- [ ] Enforce the no-network/no-third-party-core-runtime constraints and complete M1 acceptance tests from the PRD.

**Done when:** all PRD section 15.3 acceptance criteria pass.

## M2 — Detection and exact duplicates (Java developer)

### M2.1 — Literal multi-pattern matching

- [ ] Implement exact case-sensitive pattern matching, including overlaps and chunk-boundary matches.
- [ ] Consider Aho–Corasick internally; do not make its implementation part of the public contract.
- [ ] Return source IDs and decoded code-point spans; enforce pattern and stored-match caps.
- [ ] Support streaming count/reporting without storing every match; test inputs larger than heap.
- [ ] Keep match detection on full analyzed input, before context selection.

### M2.2 — Exact duplicate detection

- [ ] Group by normalized segment text and confirm equality (never rely on a hash alone).
- [ ] Choose representatives deterministically and keep provenance of all copies.
- [ ] Implement documented group/collapse behavior; Preserve mode must retain all text.
- [ ] Test hash collisions, duplicate chains, and multiple sources.

### M2.3 — Benchmark harness

- [ ] Add a JMH benchmark source set that is excluded from the core JAR.
- [ ] Provide reproducible synthetic datasets and record JDK, hardware, revision, heap, and dataset parameters.
- [ ] Publish measured results only after running them; do not invent performance numbers.

**Done when:** PRD section 15.4 acceptance criteria pass.

## M3 — Context selection and MVP completion (Java developer)

### M3.1 — Task scoring and selection

- [ ] Implement explicit prefix selection and explainable task/pattern-based heuristic ranking.
- [ ] Preserve deterministic tie-breaking and source ordering by default.
- [ ] Record selection reasons; do not claim semantic relevance guarantees.

### M3.2 — Token budgets and packing

- [ ] Provide an approximate built-in token estimator and a pluggable estimator API.
- [ ] Account for segment text, separators, and caller-configured overhead.
- [ ] Implement greedy-skip and stop-on-overflow strategies.
- [ ] Never exceed the configured estimated budget; do not silently split segments.

### M3.3 — Provenance, truncation, and MVP validation

- [ ] Track retained, duplicate-collapsed, and omitted source spans without double counting.
- [ ] Distinguish `lossy`, `transformed`, `budgetExhausted`, and `analysisTruncated`.
- [ ] Handle retention caps with an explicitly unanalysed `TRUNCATED` tail.
- [ ] Test quote fidelity, partition coverage, real evidence retention (not merely omission disclosure), and deterministic output.
- [ ] Reject unimplemented summarization with a documented error.
- [ ] Verify all PRD section 15.5 acceptance criteria; prepare a reviewed MVP preview.

## M4 — Conversation-aware packing (after MVP)

- [ ] Include neighboring messages only when budget permits; preserve source order.
- [ ] Add optional, stable timestamp ordering.
- [ ] Document and test conversation JSON mapping without adding a required JSON dependency to core.

## M5 — Optional algorithms and extension points (after MVP)

- [ ] Evaluate SimHash versus MinHash on documented fixtures; record the chosen algorithm and its trade-offs.
- [ ] Add opt-in, clearly marked approximate near-duplicate grouping.
- [ ] Add a caller-provided summarizer interface; label generated text and its source spans.
- [ ] Keep core independent of any model provider or network SDK.

## M6 — CLI and release hygiene (after MVP)

- [ ] Add a separate minimal CLI artifact with Preserve and Selective operations.
- [ ] Add compiling usage examples, reproducible benchmarks, and release documentation.
- [ ] Create `SECURITY.md` with a vulnerability reporting channel before tagging releases.
- [ ] Choose publishing channel and validate package metadata; do not publish without maintainer review.

## First actions for the Java developer

1. Accept the GitHub collaborator invitation and clone the repository.
2. Read `README.md`, `CONTRIBUTING.md`, `docs/idea.md`, and `docs/PRD.md`.
3. Wait for M1.1 to merge, then pull the latest `main`.
4. Create a branch such as `feat/m1-streaming` and implement **M1.2 only**.
5. Run the relevant JUnit tests and Gradle build, then open a PR for review; do not push implementation directly to `main`.
6. Continue with M1.3, M1.4, and later milestones in small reviewable PRs.

**Note:** A checkbox is an implementation milestone, not a schedule or an assignment with a deadline.
