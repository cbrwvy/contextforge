# ContextForge

ContextForge is a standalone open-source Java library for preparing large collections of text, documents, and conversations before they are sent to a language model or another text-processing system.

The library is intended to select relevant passages, drop exact duplicates in a traceable way, and return a context package that records where each retained passage came from. It is designed to run locally and to stay independent of any particular model provider.

## Status

The Java 21 build and the first public types are in the repository. Those types are caller-supplied identifiers and half-open 64-bit Unicode code-point spans. Streaming ingestion, normalization, segmentation, pattern matching, deduplication, and context packing are not implemented yet.

There is no published artifact yet. The documents in this repository describe the planned behavior of the later milestones. They are not a claim that the capabilities below already exist.

## Building

Install JDK 21, then use the Gradle Wrapper checked into the repository:

```bash
./gradlew test
./gradlew build
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

`test` compiles the library and runs the unit tests. `build` also packages the jar and checks that the core runtime classpath has no third-party libraries. The first run downloads the Gradle distribution pinned by the wrapper and the JUnit Jupiter test dependency. Later runs reuse the local cache.

Maven coordinates are `io.github.cbrwvy:contextforge`. The current version is `0.1.0-SNAPSHOT`. JUnit Jupiter is a test dependency only.

GitHub Actions runs `./gradlew build` on Temurin 21.

## Planned capabilities

- Streaming ingestion of plain text and structured messages, with memory bounded where the processing stage allows it
- Configurable text normalization that records every transform it applies
- Literal multi-pattern search with source offsets
- Exact duplicate grouping, followed later by optional near-duplicate grouping
- Task-aware selection and packing into a caller-defined token budget
- A provenance report that separates original excerpts, omissions, and any generated summaries

Lossy modes are planned to make omissions and generated text explicit. They are not planned to guarantee that every fact or relevant passage survives.

## Documentation

- [Project idea](docs/idea.md) — direction and motivation
- [Product requirements](docs/PRD.md) — proposed scope, API, milestones, and acceptance criteria
- [Contributing](CONTRIBUTING.md) — branch, review, and commit workflow

## Contributing

The maintainers use feature branches, small commits, and pull requests into `main`. See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

ContextForge is licensed under the [Apache License 2.0](LICENSE).
