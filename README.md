# ContextForge

ContextForge is a standalone open-source Java library for preparing large collections of text, documents, and conversations before they are sent to a language model or another text-processing system.

The library is intended to select relevant passages, drop exact duplicates in a traceable way, and return a context package that records where each retained passage came from. It is designed to run locally and to stay independent of any particular model provider.

## Status

The project is in the design stage. The library has not been implemented, and there is no published API artifact yet.

The documents in this repository describe planned behavior. They are not a claim that the capabilities below already exist.

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
