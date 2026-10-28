# Contributing to ContextForge

ContextForge is maintained by a small team. The workflow below keeps changes easy to review and the history easy to understand.

## License

By submitting a contribution, you agree that it is licensed under the [Apache License 2.0](LICENSE).

## Workflow

1. Start from the latest `main` branch.
2. Create a feature branch for the change. Use a short, descriptive name, such as `detect/literal-patterns` or `docs/packing-notes`.
3. Keep commits small and focused. Each commit should represent one logical change.
4. Open a pull request into `main`.
5. Obtain a review from the other maintainer before merging.
6. Merge only after the review is addressed and the agreed checks pass.

Direct pushes to `main` are reserved for exceptional repository maintenance, not for feature work.

## Changes that include behavior

- Add or update tests for every functional change.
- Keep deterministic stages reproducible. Tests should not depend on the wall clock, an unspecified locale, or collection iteration order.
- Do not weaken a guarantee in [the product requirements](docs/PRD.md) without updating that document in the same pull request.

Documentation-only changes do not require runtime tests. They still go through a pull request and a review.

## Documentation

Update the relevant documentation when a change affects behavior, public API, setup, or requirements. User-facing project documentation is written in English.

## Secrets and private data

Do not commit secrets, credentials, access tokens, private datasets, or personal data. Examples and fixtures must use synthetic or clearly redistributable content.

If a secret is committed by mistake, rotate it. Removing it from a later commit is not enough, because it remains in history.

## Reviews

Review for correctness, narrow scope, tests, and documentation. Prefer a follow-up pull request over expanding an already focused change.

A pull request that changes library behavior should state what was verified. Until continuous integration exists, the author runs the relevant tests locally and records that in the pull request.
