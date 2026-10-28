# ContextForge Product Requirements

**Status:** Proposed

**Date:** 2026-10-28

**Audience:** The two maintainers and future contributors

**Related:** [Project idea](idea.md)

This document turns the project idea into a proposed requirements baseline for a standalone Java library. It is a design contract for review, not a description of existing code. No ContextForge library, command-line tool, or published artifact exists yet.

The idea document remains the direction statement. Decisions already made, and the questions that can wait, are in [section 16.5](#165-decisions-and-remaining-questions).

The scope is sized for two developers. The core is an in-process library. Optional behavior is added by caller-supplied implementations or, later, by a separate artifact that depends on the core. The design does not add a required service, a model provider, or a distributed runtime.

## 1. Vision and target users

### 1.1 Vision

ContextForge prepares large bodies of text into an auditable context package that a language model, or another text processor, can consume. The library reduces how much irrelevant, repeated, or noisy text the caller has to send, and it records what happened to the source.

The product optimizes for correctness, predictable resource use, reproducibility, and measurable evidence quality. A smaller package is useful only when the caller can see what was kept, what was dropped, and what was generated.

The library runs locally. It does not require an account, a network connection, or a particular model. A caller can use the result with any downstream system, or with none.

### 1.2 Target users

| User | Need |
| --- | --- |
| Application developers building retrieval or prompting pipelines | A Java API that selects and packs source text under a token budget and returns offsets back to the original input |
| Teams processing document archives or exported conversation histories | Streaming ingestion, duplicate handling, and a report they can store next to the model output |
| Developers analyzing support transcripts, logs, and text datasets | Pattern search and exact duplicate grouping over the full input, with chronology and speaker metadata preserved when the caller supplies them |
| Local-first tools | Preparation that finishes before any external call, with no implicit transmission of user text |

The caller is a programmer using the library from Java. A command-line interface is planned later for language-neutral use. It is not part of the first usable library slice.

### 1.3 Product principles

- The public Java API is the product. Adapters sit behind interfaces the caller passes in.
- Deterministic stages are pure functions of their input, configuration, and library version.
- Original excerpts, derived metadata, and generated summaries are different kinds of data and stay labeled.
- The default configuration does not drop, rewrite, or invent content.
- Exact search and duplicate detection run on the complete configured input. They do not run on an already reduced subset.
- Privacy is structural: the core has no network client, no telemetry, and no log of source text unless the caller installs a listener and opts into text.
- Performance claims wait for published, reproducible runs. This document sets design goals only.

## 2. Problems addressed and scope

### 2.1 Problems

Large inputs often exceed a context window or a practical budget of cost and latency. Sending everything spends that budget on duplicated text, formatting noise, and passages that do not bear on the task.

Truncating from the start or the end can drop the relevant passage. An unlabeled summary can invent or blur wording and cannot be traced to a source offset. After the fact, a reviewer cannot tell whether a statement in the model input was a quotation, an omission, or a generated sentence.

Callers need one preparation layer that:

- accepts inputs larger than the memory they are willing to spend on the final package
- finds configured patterns and exact duplicates across the whole input
- builds a smaller package when the caller asks for one
- accounts for every omission and every generated span, and says when a cap has replaced per-segment detail with a coarser tail

### 2.2 In scope

The library covers plain text and caller-supplied structured messages. It covers normalization, literal pattern search, exact duplicate grouping, heuristic task selection, token-budget packing, and a provenance report. Later milestones add near-duplicate grouping, conversation-neighbor selection, a caller-supplied summarizer, and a thin command-line wrapper.

Processing stays in one JVM. Parallelism, when it is added, is an explicit caller choice. The MVP pipeline does not start threads.

### 2.3 Out of scope

Section 14 lists non-goals in full. In short, ContextForge is not a chat product, a hosted model service, a search engine, or a distributed processing platform. Lossy output has no semantic-preservation guarantee.

### 2.4 Definitions

| Term | Meaning |
| --- | --- |
| Source | One identified input: a text stream, an in-memory string, or a message list |
| Decoded text | Unicode scalar values after character decoding, before optional normalization |
| Transform | A named, opt-in change to decoded text, such as newline canonicalization |
| Segment | The packing unit: a message, a whole source, or a paragraph, depending on the segmenter |
| Original excerpt | Segment text copied from the source after only the enabled transforms |
| Summary | Text produced by a caller-supplied summarizer. It is never an original excerpt |
| Span | A half-open code-point range `[start, end)` in a named source, referring to decoded text |
| Context package | The immutable result: ordered segments plus a report |
| Token estimate | An integer from a pluggable `TokenEstimator`. It is not a provider's exact token count unless the caller supplies that estimator |
| Lossless result | A result from which the decoded source code points can be recovered exactly, or a declared encoding of those code points that round-trips |
| Lossy result | A result that cannot reconstruct the source wording, because text was omitted or generated |

## 3. Core capabilities

Each capability must be usable on its own. A caller can scan for patterns without packing, or pack without a summarizer. Stages that the caller does not configure are skipped.

Proposed pipeline order, when all stages are enabled:

1. Decode bytes or accept an already decoded `Reader` or `String`.
2. Apply the configured normalization and build an offset map back to decoded text.
3. Split the normalized text into segments.
4. Group exact duplicates, and later near-duplicates, on segment text.
5. Run pattern detection across the full normalized input. Matches do not cross source or message boundaries. They do cross chunk boundaries inside one source.
6. Score segments for the task.
7. If the mode packs, choose segments under the token budget.
8. Emit the package and the report.

Pattern matches and duplicate groups are computed before packing. The report can cite a match inside an omitted span without copying that omitted text into the package.

### 3.1 Streaming text processing

The MVP accepts:

- `String` and `CharSequence` for inputs the caller already holds
- `Reader` for decoded streams
- `InputStream` plus an explicit `Charset` for byte streams
- an ordered message list
- a composite of sources, in caller order

`Source.utf8(InputStream, sourceId)` is a convenience for UTF-8. The library does not guess a charset. A `Reader` is already decoded, so byte-order-mark handling for that path belongs to the caller.

Streaming rules:

- Preserve-mode writing uses a bounded carry buffer. For newline canonicalization the buffer holds at most a trailing carriage return so a CRLF pair split across reads is recognized.
- Literal pattern search uses a carry buffer of `longestPattern - 1` code points so a match split across reads is recognized.
- A streaming session is pull-based and single-threaded. The library does not create an executor. Finishing a session into a package follows the `prepare` memory rule. `write` is the bounded Preserve path.
- `Pipeline` configuration is immutable and safe to share. A `StreamingSession` is not.
- Closing a source closes only resources the library opened. A caller-owned `Reader` or `InputStream` is left open for the caller to close, unless the caller uses a factory method that documents ownership.
- `write` returns counts, transforms, and the built-in token estimate when that estimator is selected. It does not embed the copied text, and it does not run a custom estimator over the stream. The bytes go only to the caller-supplied sink.

Preserve `write` and a match scan that does not store matches are the bounded-memory paths. Selective `prepare` keeps a span index and the segment text it still needs. [Section 3.6](#36-memory-provenance-and-truncation) is the memory and provenance rule. [Section 12](#12-performance-and-memory-goals) repeats it as a performance goal.

### 3.2 Pattern matching

MVP pattern matching is literal, multi-pattern, and code-point based. A match record stores the pattern identifier, the source span in decoded text, and the segment identifier when the match falls inside a segment.

Required behavior:

- Overlapping and nested literal matches are all observed. Stored records follow the cap in [section 3.6](#36-memory-provenance-and-truncation).
- An empty pattern is rejected.
- Patterns are matched against normalized text when a transform is enabled, and the reported span is mapped back to decoded text.
- The library does not rewrite the pattern string. Callers who enable newline canonicalization should pass patterns in that same form.
- Case-sensitive comparison is the default. It compares Unicode scalar values.
- Case-insensitive matching is not an MVP feature. Java lowercasing, Unicode case folding, and locale-specific case pairs differ, especially for Turkish. A later mode must name the exact case rule and ship fixtures for it.
- Regular expressions are not an MVP feature. They bring backtracking risk and a second match semantics. A later design can add them behind a separate type with an explicit limit on match effort.

The suggested implementation is an Aho–Corasick automaton, or another algorithm with the same observable result: every literal occurrence, linear in the input length plus the number of matches, with automaton memory determined by the pattern set. The public contract is that observable result, including truncation flags, not the automaton.

Match storage is capped by `Limits.maxMatches`. When that cap is hit, the scan can continue and increment a total count, the stored list stops growing, and the report sets `matchesTruncated`. That storage cap is separate from `analysisTruncated`, which stops classification of a tail. The default cap is a guard against untrusted pattern sets and noisy inputs. It is a proposed default in [section 8](#8-error-handling), and it is not a measured capacity.

### 3.3 Duplicate and near-duplicate detection

Exact duplicates compare normalized segment text with Java string equality. A hash may bucket candidates. Group membership is decided by equality, so a hash collision cannot merge different strings.

Each group records:

- a deterministic representative (the earliest segment in source order, then by span start)
- the group size
- the source span of every member
- whether the group is exact

`DuplicatePolicy.GROUP` keeps every copy available to the packer and annotates the group. A copy that is not packed is an omission, and the group still cites it. `DuplicatePolicy.COLLAPSE` exposes only the representative to the packer. The other members are the collapsed-duplicate covering, not a second omission list. Collapse never discards the last copy. Member spans that were actually grouped are part of the `prepare` index. A tail marked `TRUNCATED` is not grouped. Preserve mode does not remove text. In Preserve mode the same grouping is advisory data on the report.

Near-duplicate detection is post-MVP. It is approximate, off unless the caller selects it, and every group it produces is marked `approximate = true`. An approximate group is not an assertion that the texts are identical or that one may stand in for the other without loss. The report must carry the algorithm identifier and the configured threshold.

The near-duplicate algorithm is an open choice, with constraints:

- It must be deterministic for a given version and configuration.
- It must define a unit that works for writing systems that do not separate words with spaces. Code-point n-grams are the fallback the design must account for. Whitespace word shingles alone are not sufficient.
- It must not run as part of the default pipeline.
- False merges are an accepted risk of the feature and must be visible on the group.

A 64-bit SimHash over code-point n-grams is the leading candidate because the sketch is small and the result is deterministic. MinHash is the alternative when the maintainer wants a threshold that maps more directly to Jaccard similarity. The milestone that builds this feature chooses one after running the validation fixtures, and records the choice. This PRD does not treat either algorithm as already selected.

### 3.4 Context compression

In this project, compression means producing a context package that is cheaper to pass downstream than the raw input. There are three mechanisms, and they do not share a guarantee:

| Mechanism | What changes | Effect on model tokens | Recoverability |
| --- | --- | --- | --- |
| Lossless encoding | Gzip or another byte encoding of the complete text | None by itself. The model still sees the full text after decoding | Decoded code points round-trip |
| Selection | Original excerpts are copied. Other spans are omitted and listed | The estimate can fall because text is absent | Retained quotes match the source. The package alone does not contain the omitted wording |
| Summarization | A caller-supplied adapter writes new text from source spans | The estimate can fall because wording was replaced | The summary is generated text. It is not a quotation and it is not a complete record of the source |

The project does not define success as a compression ratio. A package that drops the only relevant paragraph can be small and still fail the product goal. Quality evaluation uses evidence retention, quote fidelity, and disclosure, as defined in [section 13](#13-benchmark-methodology-and-validation-datasets).

Transport compression may wrap a Preserve-mode byte stream. It is an encoding of the complete text. It must not be reported as a reduction in token estimate.

### 3.5 Source provenance

Every original segment carries a source identifier and a decoded-text span. Every stored pattern match and every grouped duplicate member carries a span. Every omission carries a span and a reason code. Every summary carries the spans it was derived from and does not carry a span that presents the summary as a quotation.

The report is part of the result object. It is not a log line. A caller can serialize it, store it, and later resolve a span against the original source they still hold.

Required report contents:

- library version string, or `unknown` when the manifest has no implementation version
- processing mode
- `lossy` and `transformed`. A `prepare` report also includes `partitionComplete`. `analysisTruncated` is set when a cap stopped classification. Preserve `write` does not include `partitionComplete`
- estimator identifier, and the package token estimate when an estimate applies. Preserve `write` leaves a custom estimate unset
- configured budget, when one was set
- transforms applied, in order
- segments, with kind, text, span or derived-from spans, and reasons
- duplicate groups
- pattern matches, subject to the match cap and the truncation flag
- omitted spans with reason codes
- counts: input code points, segment count, retained count, omitted count, duplicate group count

Segment reasons and omission reasons are different lists. A kept segment uses `SELECTED`, a feature reason, `NEIGHBOR` once neighbor expansion exists, or `SUMMARY_SOURCE` on a later summary. An omission span uses `BUDGET`, `EXCEEDS_BUDGET`, `NOT_SELECTED`, `SEGMENT_BOUNDARY`, or `TRUNCATED`.

Partition rule for `prepare`: when `partitionComplete` is true, every code point of every source sits in exactly one covering: a retained original span, a collapsed-duplicate member span, or an omission span. A duplicate group may also cite a span. That citation is an index, not a second covering. Empty input is valid and produces an empty package with `lossy = false` and `partitionComplete = true`.

Preserve `write` does not build this span partition. Completeness for that path is the sink round-trip in section 4.

### 3.6 Memory, provenance, and truncation

A bounded buffer and a complete per-segment index are different promises. The MVP does not treat them as the same path.

| Path | Memory | Provenance |
| --- | --- | --- |
| Preserve `write` | Independent of input length, aside from the carry buffer, the decoder buffer, and the caller-owned sink | Transforms, output code-point count, and, when the built-in estimator is selected, a token estimate derived from that count. No omission list. A custom `TokenEstimator` is not applied to the stream, and the custom estimate is left unset |
| Literal scan that counts matches or streams them to a caller sink | Independent of input length, aside from the automaton, the carry buffer, and the match being emitted | Every match is observed. Stored records stop at the match cap. Further matches increment a count and set `matchesTruncated` |
| Selective `prepare`, and exact duplicate grouping | Proportional to retained segment text, distinct grouped texts, and the span index | Omitted wording is not copied into the package. Omissions are coalesced spans. Grouping keeps one representative string per distinct text it grouped, plus the member spans |

Adjacent omission spans with the same reason are merged. The index grows with the number of runs, not with the size of the omitted wording. That growth belongs to `prepare`. It is not a constant bound.

When the retention cap stops storage of another segment body, classification of new segments stops. The remainder of that source is one tail span with reason `TRUNCATED`. The tail keeps the partition complete. The report sets `analysisTruncated`. Matches and duplicate groups are not claimed inside the tail. `partitionComplete` is false only when the remainder cannot be bounded. In that case the package says so and is not presented as a full accounting.

`lossy` is true when any omission span exists or any summary segment is present. Exact collapse does not by itself add an omission: the non-representative spans are the collapsed covering, and their wording is the retained representative. A collapsed copy whose representative was not retained still leaves that representative on the omission list, so `lossy` is true. A Selective package that retains every source code point, with no boundary spans and no tail, has `lossy = false`. Preserve `write` does not become lossy because an estimate exceeds a budget. The full output is still produced, and `budgetExhausted` records the comparison.

## 4. Lossless and lossy processing

### 4.1 Modes

The modes are Preserve, Selective, and, after the MVP, Summarized. M1 does not need to publish a third constant if a request for generated text fails clearly.

**Preserve.** The output contains the complete decoded text, after only those transforms the caller enabled. With `Normalization.none()` and no byte-order-mark strip, the sink output of `write`, or the package text of a materialized `prepare`, is lossless at the Unicode code-point level: concatenating that output yields the same scalar values as the decoded input. The `write` report stores counts and transforms, not a second copy of the text. A token budget, when supplied, is compared with the estimate and recorded. An estimate over the budget sets `budgetExhausted` and leaves `lossy` false. Preserve does not truncate to meet the budget. If the caller needs a smaller package, they select Selective or Summarized.

A gzip (or similar) wrapper around Preserve output is lossless encoding. It does not reduce the token estimate of the text inside it. `write` streams that output to a caller-supplied sink and returns the report described in [section 3.1](#31-streaming-text-processing).

**Selective.** The output contains original excerpts chosen under the budget and the duplicate policy. Each excerpt matches the source slice it cites, after only enabled transforms. The package is lossy because other source text is absent. Omitted spans are listed. Selective mode does not paraphrase.

**Summarized.** The output may contain original excerpts and summary segments produced by a `Summarizer` the caller supplies. The core library does not include a summarizer implementation and does not include a client for any model provider. Summary segments use `SegmentKind.SUMMARY`, include `derivedFrom` spans, and are excluded from quote-fidelity checks. Summarized mode is lossy. The summary may omit details, generalize, or misstate what the source said. The library does not claim that a summary preserves facts, quotations, or every relevant passage.

The result model has a place for summaries after the MVP. Until that milestone, a request for generated text fails with a clear error.

### 4.2 Flags

| Flag | Set when |
| --- | --- |
| `lossy` | Any omission span exists, or any summary segment is present. [Section 3.6](#36-memory-provenance-and-truncation) covers exact collapse and Preserve budgets |
| `transformed` | Any enabled normalization or decoding rule changed decoded text |
| `budgetExhausted` | A budget was set and the estimate exceeds it, or at least one candidate was left out because of the budget |
| `matchesTruncated` | The match store hit its cap |
| `analysisTruncated` | A cap stopped classification and the remainder is a `TRUNCATED` tail, or the remainder could not be bounded |
| `partitionComplete` | On a `prepare` report, the coverings in section 3.5 partition every source. Unset on Preserve `write`, which has no span partition |

Preserve with `Normalization.none()` and no BOM has `lossy = false` and `transformed = false`.

Newline canonicalization and the whitespace profile set `transformed = true`. In Preserve mode they leave `lossy = false`. Each is a declared structural change, not a summary. The offset map records every deleted or rewritten code point. A transformed segment's code-point length can differ from `end - start` of its decoded span. Quote checks apply the recorded transforms to the decoded span and compare that with the segment text. The token estimator sees the transformed segment string, not the span length. Callers who need byte-faithful or code-point-faithful text leave normalization off. The project does not call a transforming configuration lossless.

### 4.3 What is guaranteed

| Guarantee | Preserve, no transform | Selective | Summarized |
| --- | --- | --- | --- |
| Decoded code points can be reconstructed | Yes, from the `write` sink or from materialized `prepare` text. The `write` report alone does not hold that text | No | No |
| Retained `ORIGINAL` text equals the decoded span after the enabled transforms | Yes | Yes | Yes, for original segments only |
| `prepare` coverings partition every source code point | On materialized `prepare`, yes. `write` has no span partition | When `partitionComplete` is true | When `partitionComplete` is true. Summaries are additional derived segments |
| Facts, answers, and task relevance are preserved | The text is complete, so nothing was selected away | No | No |
| Token estimate matches a given provider tokenizer | No | No | No |

The sentence that matters for lossy modes: ContextForge does not guarantee semantic preservation. A Selective package can miss a relevant passage. A summary can be wrong. Callers who need the full wording use Preserve and keep the source.

## 5. Task-aware selection and token budgets

### 5.1 Tasks

A task is caller-supplied. The MVP task has:

- an objective string
- zero or more required terms
- zero or more preferred terms

The objective is stored on the report so the package is self-describing. The MVP ranker does not parse the objective as a natural-language query beyond the term lists. Term matching is case-sensitive on Unicode scalar values. Segment text is split on Unicode whitespace into tokens, and characters in Unicode category P (punctuation) are removed from the start and end of each token before comparison. A term with no whitespace matches one token. A term that contains whitespace matches that token sequence. The excerpt stored in the package is unchanged. This rule is explainable and weak on morphology. Stemming, lemmatization, and embeddings are out of the MVP.

`Selection.heuristic()` requires a task, a pattern set, or both. If neither is present, pipeline construction fails. That prevents a silent "keep the beginning" default.

`Selection.prefix()` is the explicit strategy for callers who want source order until the budget fills. It does not pretend to be relevance.

Later, a `Ranker` interface can accept embeddings or any other scorer the caller implements. The core will not ship an embedding model or download one. Semantic ranking stays optional, off by default, and labeled with the ranker identifier on each reason.

### 5.2 Heuristic score

The MVP score is an integer. Floating-point scores are rejected because they make cross-run identity harder to test and explain.

Features, each recorded as a reason when it contributes:

- distinct required term present in the segment
- distinct preferred term present
- distinct configured pattern matched in the segment
- extra exact-duplicate copies beyond the representative, capped

The feature list above is the MVP heuristic. Scores are integers. The tie-break above is the initial total order. Weights and the tie-break key order are locked by tests when M3 implements them. Either may change before the first preview tag if a fixture shows a problem. That change updates the tests in the same pull request. Neither is a compatibility promise before 1.0.0.

Tie-break, in order: higher score, earlier source in the composite order, earlier span start, then segment identifier lexicographically. The last key exists so two segments with the same span metadata still sort stably.

### 5.3 What selection does not mean

A high score means the configured features fired. It does not mean the segment answers the task, and a zero score does not mean the segment is irrelevant. The report's reasons exist so a caller can see which features fired. Evaluation measures planted-evidence retention separately from omission disclosure. A disclosed omission is not retained evidence. The heuristic is not a relevance oracle.

### 5.4 Token budget

```text
segmentCost = estimator.estimate(segmentText) + overheadPerSegment
packageCost  = sum(segmentCost) + separatorCost(n) + packageOverhead
```

A segment is accepted only when the resulting `packageCost` is less than or equal to `maxTokens`. The estimator sees the exact string that will be placed in the original segment, including only enabled transforms.

`overheadPerSegment` and `packageOverhead` exist because the caller often wraps each passage in labels, speaker names, or a prompt template. The library cannot see that wrapper. Defaults are zero overhead and a separator of `"\n\n"` passed through the same estimator. Callers with a heavier template should set the overhead. The report includes the overhead configuration so the estimate can be audited.

`TokenEstimator` is:

```java
public interface TokenEstimator {
    String id();
    int estimate(String text);
}
```

The initial built-in estimator id is `codepoints-divided-by-4`. Its value is `(codePointCount + 3) / 4`, using the transformed text the caller will receive. This is a rough stand-in sometimes used for English text. It is a poor estimate for many scripts, including CJK text, where a model may use far more tokens per code point. The report includes the active estimator id. The library does not ship tokenizer vocabularies and does not claim this estimate matches any provider. The id and the divisor are the initial definition. They may change before the first preview tag. The 1.0.0 release is the compatibility point. Preserve `write` derives this built-in value from the running output code-point count and does not buffer the stream to do it.

A caller who has a real tokenizer implements `TokenEstimator` and passes it in. That implementation lives outside the core jar.

Budget exhaustion is a normal result, not an exception. Selective mode returns the package and records the omissions. `lossy` follows [section 3.6](#36-memory-provenance-and-truncation). The reported `estimatedTokens` value is this packing cost, not `estimator.estimate` of the segments concatenated with their separators. Those integers differ when the estimator rounds each piece, which the initial built-in estimator does. Sums use 64-bit arithmetic. A negative estimate is rejected.

Packing strategies:

- `Packing.greedySkip()` considers candidates in tie-break order and skips a candidate that does not fit. This is the default.
- `Packing.stopOnOverflow()` stops at the first candidate that does not fit. That candidate is omitted with `EXCEEDS_BUDGET`. Later candidates are omitted with `NOT_SELECTED`.

The library does not split a segment below the configured segmenter to force it into the budget. A segment larger than the remaining budget, or larger than the whole budget, is omitted with `EXCEEDS_BUDGET`. Callers who want smaller units select a finer segmenter. Sentence segmentation is deferred because sentence boundaries are language-specific.

Output order is independent of consideration order. The default `OutputOrder.SOURCE` emits accepted segments in source order so a conversation stays chronological in the text the downstream system sees. `OutputOrder.SCORE` is available and explicit. Scores remain on the report in either case.

### 5.5 Segmenters

| Segmenter | MVP | Unit |
| --- | --- | --- |
| `Segmenter.messages()` | Yes | One segment per message. Patterns do not cross messages |
| `Segmenter.documents()` | Yes | One segment per source |
| `Segmenter.paragraphs()` | Yes | Split on blank lines in normalized text. A final segment without a trailing blank line is kept |

The packed text of a message is `Message.text()` only. Speaker, timestamp, and metadata stay on the report. They affect the token total only through the caller-supplied overhead fields. The library does not prepend a speaker label to the text.

`Segmenter.paragraphs()` splits the normalized text on blank lines. A blank line is a line-break sequence, optional U+0009 or U+0020 characters, and another line-break sequence. The paragraph bodies become segments. The blank-line code points are omission spans with reason `SEGMENT_BOUNDARY`, so a document that contains a blank line is `lossy` even when every paragraph body is kept. Callers who want every code point retained use `Segmenter.documents()` or Preserve. Line breaks are the set in the normalization note under [section 6](#6-functional-requirements).

Markdown structure, code fences, and HTML blocks are not parsed in the MVP. A paragraph inside a code sample is still a paragraph to `Segmenter.paragraphs()`.

## 6. Functional requirements

Priority `MVP` is required for the first usable library slice. Priority `Later` is in the product direction and is scheduled in [section 15](#15-milestones-dependencies-and-acceptance-criteria).

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-01 | The caller can prepare a `String`, a `Reader`, or an `InputStream` with an explicit charset | MVP |
| FR-02 | The caller can prepare an ordered message list with id, optional speaker, optional timestamp, text, and string metadata | MVP |
| FR-03 | The caller can prepare a composite of sources. Order is the list order. Patterns do not cross source boundaries | MVP |
| FR-04 | Every source and every message has a caller-supplied non-blank id, unique within the prepare call | MVP |
| FR-05 | A charset decoding error fails the prepare call. The default coding action is report, not replace | MVP |
| FR-06 | `Normalization.none()` is the default. `Normalization.newlines()` and `Normalization.whitespace()` are opt-in. Newlines rewrites CR, CRLF, and Unicode line separators to LF, including pairs split across reads. Whitespace includes that rewrite, collapses each run of spaces and tabs to one space, and drops trailing spaces and tabs at line ends | MVP |
| FR-07 | Every transform is named on the report, in application order, and sets `transformed` | MVP |
| FR-08 | The three MVP segmenters exist and are deterministic | MVP |
| FR-09 | Literal multi-pattern search observes every match on the configured input, including overlapping matches. Stored matches stop at the cap and set `matchesTruncated` | MVP |
| FR-10 | Each match carries a pattern id and a decoded-text span | MVP |
| FR-11 | Exact duplicate groups use string equality of normalized segment text and list every member span | MVP |
| FR-12 | The caller can group duplicates or collapse them to a deterministic representative. Preserve mode does not delete text | MVP |
| FR-13 | Near-duplicate grouping is opt-in, approximate, labeled, and absent from the default pipeline | Later |
| FR-14 | MVP implements Preserve and Selective. A request for generated summaries fails with a clear error until the summarizer milestone. Exception class names are not frozen | MVP |
| FR-15 | Preserve with no transform round-trips decoded code points and does not truncate to a token budget | MVP |
| FR-16 | Every `ORIGINAL` segment's text equals the decoded source slice after the enabled transforms | MVP |
| FR-17 | Summary segments are labeled `SUMMARY`, carry derived-from spans, and are never quote-checked as originals | Later |
| FR-18 | The default pipeline does not omit, transform, or generate text | MVP |
| FR-19 | Heuristic selection uses the documented integer features, reasons, and tie-break | MVP |
| FR-20 | Prefix selection exists as an explicit strategy for source-order packing | MVP |
| FR-21 | Token accounting includes segment text, configured overhead, and separators, and never accepts a package over the budget | MVP |
| FR-22 | The caller can replace the estimator. The MVP includes the documented code-point heuristic in section 5.4. Its id and divisor are the initial definition, not a 1.0 contract | MVP |
| FR-23 | Greedy-skip and stop-on-overflow packing exist | MVP |
| FR-24 | Default output order is source order. Score order is opt-in | MVP |
| FR-25 | A `prepare` report with `partitionComplete` satisfies the partition rule and includes the fields in [section 3.5](#35-source-provenance). Preserve `write` reports counts and transforms instead of a span partition | MVP |
| FR-26 | Omissions use the documented reason codes. Budget exhaustion returns a package and does not throw | MVP |
| FR-27 | Pattern detection and duplicate detection run before packing. They cover the complete configured input when `analysisTruncated` is false. A `TRUNCATED` tail is excluded from both results | MVP |
| FR-28 | Limits reject excessive pattern sets and cap stored matches and retained segments, with truncation or a limit error as specified | MVP |
| FR-29 | A no-op listener is the default. Events omit source text unless the caller opts in | MVP |
| FR-30 | MVP keeps message input order regardless of timestamps. Timestamp reordering is Later | MVP |
| FR-31 | A selected message can pull neighboring messages into the package when they fit, with reason `NEIGHBOR` | Later |
| FR-32 | A command-line wrapper can run Preserve and Selective on a file or UTF-8 stdin and write the package as JSON | Later |

Line breaks for `Normalization.newlines()`, `Normalization.whitespace()`, and paragraph splitting are the set matched by Java's `\R`: U+000A, U+000B, U+000C, U+000D, U+0085, U+2028, and U+2029. U+000D U+000A is one break. `newlines()` rewrites each break to U+000A. `whitespace()` does that, collapses each run of U+0009 and U+0020 to one U+0020, and removes trailing U+0009 and U+0020 before a line break. It stays off unless the caller selects it. It can change indentation and aligned text, so it sets `transformed` and records an offset map. Stripping zero-width characters and other technical noise is a later profile, because those characters are sometimes meaningful. Unicode normalization forms and case-insensitive patterns are also later, and opt-in. NFKC, if it is ever offered, must set `transformed`, because it changes compatibility characters.

## 7. Non-functional requirements

| ID | Requirement |
| --- | --- |
| NFR-01 | The language baseline is Java 21. Public types may use records and sealed interfaces. The library does not require virtual threads or preview features |
| NFR-02 | The core jar has no runtime dependencies. JUnit is a test dependency. JMH, when added, is a benchmark dependency and is not packaged in the core jar |
| NFR-03 | For a fixed input, configuration, and library version, deterministic stages return equal packages, including report order |
| NFR-04 | The core does not open sockets, read credentials, start background threads, or emit telemetry. Tests run without a network connection |
| NFR-05 | Offsets, matching, and estimation are defined on Unicode scalar values. Fixtures cover the scripts and sequences in [section 10](#10-unicode-support) |
| NFR-06 | Preserve `write`, and a literal scan that does not store matches, do not retain the full input. Selective `prepare` follows [section 3.6](#36-memory-provenance-and-truncation): it retains packed text and a span index, and a cap ends classification with a `TRUNCATED` tail. Tests cover the bounded paths and the capped tail |
| NFR-07 | Exceptions and default listener events may include source ids and offsets. They omit source text unless a documented debug flag or listener detail is enabled. That flag defaults to off |
| NFR-08 | A pipeline is immutable. A session is confined to one thread. The documentation states this |
| NFR-09 | Before 1.0.0, a minor version may change public behavior. From 1.0.0, the project uses semantic versioning. Behavior changes in weights, estimator formulas, or default limits are release-note items |
| NFR-10 | User-facing documentation is in English |
| NFR-11 | The MVP is implementable by two people as the milestone sequence in section 15, without a service deployment |
| NFR-12 | The machine-readable report is sufficient to audit a package without reading library logs |

Agreed Maven coordinates:

- Group: `io.github.cbrwvy`
- Artifact: `contextforge`
- Module name, if a module descriptor is added later: `io.github.cbrwvy.contextforge`
- Base package: `io.github.cbrwvy.contextforge`

The build tool for implementation is Gradle with the Kotlin DSL, using the checked-in wrapper. The wrapper JAR and properties are project files and must remain tracked. The Gradle version is chosen when the build is introduced, from a current stable release. This PRD does not add the build yet.

## 8. Error handling

Failures split into caller mistakes, defective input, resource guards, and ordinary outcomes.

| Situation | Type | Notes |
| --- | --- | --- |
| Null required argument | `NullPointerException` | Includes a null source, mode, or estimator when a budget is set |
| Blank id, empty pattern, negative budget, heuristic selection with neither a task nor patterns | `IllegalArgumentException` | Construction fails before reading input |
| Duplicate id, null message text, malformed structured field | `InvalidSourceException` | Carries source id and, when known, a code-point offset. Omits source text by default |
| Byte sequence illegal for the charset | `InvalidSourceException` | Caused by the decoder failure |
| I/O error while reading | checked `IOException` | Declared on `prepare` and `write` |
| Pattern count, pattern length, or a caller-set input cap exceeded | `LimitExceededException` | Thrown before unbounded allocation where the limit can be checked up front |
| Stored matches or retained segments hit their cap | normal report flags | `matchesTruncated` or `analysisTruncated`. A selective tail uses one `TRUNCATED` span as defined in section 3.6 |
| Token budget cannot fit a candidate in Selective mode | normal package | Omission reason. `lossy` follows section 3.6. Preserve does not omit text to meet a budget |
| `write` called when the mode is not Preserve | `IllegalStateException` | Selective results are packages, not a full copy |
| Session used after `finish` or `close` | `IllegalStateException` | |
| Generated summaries requested before that milestone, or requested without a summarizer | unsupported-mode error | Unchecked. The class name is not frozen |

`ContextForgeException` is the unchecked base for invalid source data, limit failures, and unsupported modes. Programming errors stay on the JDK types `NullPointerException`, `IllegalArgumentException`, and `IllegalStateException`. The exception names in this section are the initial sketch. M1 may rename them. The failure categories are the requirement.

Proposed default guards, subject to confirmation before the API is stable:

| Limit | Proposed default | On excess |
| --- | --- | --- |
| Patterns | 10,000 | `LimitExceededException` |
| Code points in one pattern | 1,024 | `LimitExceededException` |
| Stored matches | 100,000 | Stop storing, keep counting, set `matchesTruncated` |
| Retained segment texts during selective `prepare` | 100,000 | Stop classifying further segments. Cover the remainder with one `TRUNCATED` tail and set `analysisTruncated` |
| Input code points | No default cap | If the caller sets one, exceeding it throws `LimitExceededException` |

Callers who process untrusted input should set an input cap and review the match and retention caps. The library still treats all text as data: it does not evaluate expressions, load classes from input, or interpret markup.

Empty input is valid. It produces an empty segment list and a report with zero counts.

## 9. Deterministic behavior

Deterministic stages are ingestion boundaries, normalization, segmentation, literal matching, exact duplicate grouping, heuristic and prefix selection, packing, and report assembly.

Rules:

- No `Random`, `UUID.randomUUID()`, or wall-clock read influences a result. Timestamps appear only when the caller supplied them.
- No `HashMap` or `HashSet` iteration order is visible in a result. Implementation may use them internally and must sort or use ordered structures before emitting.
- Any case conversion added in a later milestone uses `Locale.ROOT` or an explicitly named Unicode rule, never the platform default locale.
- Tie-breaks in section 5.2 are total for a given input.
- Generated identifiers are not used in the MVP. The caller supplies ids, which keeps provenance stable across processes.
- Two `prepare` calls on equal inputs and equal configuration return equal packages. Tests compare that equality, not a single golden file that embeds a machine path or a timestamp.

Near-duplicate algorithms and caller-supplied estimators, rankers, and summarizers are deterministic only to the extent those implementations are. The report records their identifiers. The core does not promise determinism for a caller-supplied component that reads the clock or the network. The core's own contract assumes those components are pure. The documentation tells callers that.

## 10. Unicode support

The public offset unit is the Unicode code point (scalar value) in decoded text. Spans are half-open and use a 64-bit count (`long`). A stream can pass `Integer.MAX_VALUE` code points, so a 32-bit offset is not the public span type. Token budgets stay separate: `maxTokens` is a package limit and may use a 32-bit count because it bounds the packed output, not the source.

Decoded text is the scalar sequence produced by the charset decoder, before BOM stripping and before normalization. A leading U+FEFF that came from a UTF-8 BOM is a code point at the start of that sequence. Stripping it is transform `UTF8_BOM`. The offset map records the deletion, later spans skip that code point, and `transformed` is set.

Consequences the API must document:

- Callers with a Java `String` convert with `Character.offsetByCodePoints` or an equivalent helper the library provides.
- A grapheme cluster, such as a family emoji joined by ZWJ, can be several code points. The MVP does not snap spans to grapheme boundaries. A literal match may begin or end inside a cluster. Fixtures must include a ZWJ sequence so this stays visible.
- Combining character sequences are matched as separate code points. The library does not apply NFC or NFD by default, because those forms change the sequence.
- When a transform changes length, `end - start` on the decoded span is not the code-point length of the segment string. Quote fidelity and token estimates use the rules in section 4.2.
- Byte offsets are not an MVP report field. A later addition can record them on the byte-stream path. They are not invented for `Reader` input.

Decoding:

- The charset is explicit for byte streams.
- UTF-8 is the charset used by convenience constructors and by project fixtures.
- Illegal sequences fail the call by default. A replace action, if added, is opt-in, recorded, and sets `transformed`.

Required fixture coverage:

- ASCII
- Latin text with diacritics
- Cyrillic
- Arabic
- CJK
- supplementary-plane emoji
- a ZWJ emoji sequence
- combining marks
- mixed CR, LF, and CRLF, including a CRLF split across two reads
- a UTF-8 BOM
- an illegal UTF-8 sequence, asserting failure
- a pattern match that crosses a read boundary

Right-to-left text is stored in logical Unicode order. The library does not reorder for display.

## 11. Privacy

- The core jar does not contain a network client. An automated test fails if compiled core classes reference `java.net.Socket`, `java.net.URL`, or `java.net.http.HttpClient`.
- The library does not read environment variables for endpoints, keys, or telemetry.
- There is no default logger and no logging-framework dependency. Diagnostics go through an optional `PipelineListener` supplied by the caller.
- The default listener detail includes stage names, ids, offsets, counts, and reason codes. It excludes segment text, message text, and pattern-match text. `ListenerDetail.INCLUDE_TEXT` is opt-in, and the caller is then responsible for where those events go.
- Exceptions follow the same default: id and offset, not source text. `includeTextInErrors` defaults to false.
- The library does not write temporary files. It does not cache source text in static fields.
- Optional later artifacts that talk to a model are separate jars. The core must not depend on them. Calling a summarizer is a call the application makes through an object it constructed, so any network use is visible at the caller's construction site.

## 12. Performance and memory goals

The goals in this section are design targets. They are not measurements. The repository has no benchmark results. No throughput, latency, or compression figure is committed here. After a run is published with its hardware, JDK build, and dataset identity, that run can become a baseline. Only then should the maintainers pick a regression threshold.

### 12.1 Complexity targets

| Stage | Target |
| --- | --- |
| Newline normalization and literal multi-pattern scan | Linear in decoded input length plus the number of matches. Automaton size depends on the pattern set |
| Exact duplicate grouping | Expected linear in the number of segments, with equality confirmation inside each hash bucket |
| Heuristic scoring | Linear in the total segment text for the configured terms and patterns |
| Packing | Linear in the number of candidates after they are ordered, or `O(n log n)` if a sort is required |

These are algorithm goals for the core implementations. A caller-supplied estimator or summarizer has whatever cost that implementation has, and the library calls it only on candidate text it has retained.

### 12.2 Memory targets

| Path | Goal |
| --- | --- |
| Preserve `write` to a caller-supplied sink | Retained memory is independent of input length, aside from the carry buffer, the decoder buffer, and the sink the caller owns |
| Literal scan that counts matches or streams them to a caller sink | Retained memory is independent of input length, aside from the automaton, the carry buffer, and the match currently being emitted |
| Selective `prepare` and exact duplicate grouping | Memory grows with retained segment text, distinct grouped texts, and the span index. The retention cap stops further classification and covers the remainder with one `TRUNCATED` tail. `analysisTruncated` and `partitionComplete` can both be true. This path does not promise memory independent of input length |
| Near-duplicate sketches, when implemented | Memory grows with the number of segments sketched. The feature documents that growth. It is off by default |

A test for the streaming paths must process a generated input larger than the JVM heap configured for that test, while counting matches or writing Preserve output, and must complete without storing the input. The heap size and input size are chosen in the test when it is written. They are not claimed here as an observed result.

In-memory `prepare(String)` may retain the string the caller already allocated. The library does not promise to use less heap than the caller's own input in that case.

### 12.3 What this document refuses to promise

- A tokens-per-second or megabytes-per-second number
- A compression ratio
- That the built-in estimator matches a provider's tokenizer
- That Selective mode keeps every relevant passage
- That near-duplicate groups are safe to collapse without reading them

## 13. Benchmark methodology and validation datasets

### 13.1 Correctness suite

JUnit tests, run offline, with fixtures checked into the repository and generators that use a fixed seed.

Checked-in fixtures stay small and are written for the project so the repository stays redistributable under Apache-2.0. They cover the Unicode list in section 10, duplicate groups, overlapping patterns, budget edges, and the partition rule.

Generated inputs cover scale. A generator accepts a seed, a document count, a duplicate rate, a script profile, and a count of planted relevant paragraphs. It writes a manifest of:

- duplicate groups and their member ids
- spans that contain the planted evidence
- expected partition coverage

The seed and generator version identify the dataset. A published run must record both.

### 13.2 Quality metrics

These are the primary metrics. Compression ratio is not one of them.

| Metric | Definition |
| --- | --- |
| Quote fidelity | For every `ORIGINAL` segment, text equals the cited source slice after enabled transforms. Target: every segment, on every fixture |
| Provenance resolvability | Every original segment and every stored match has a source id and a span that locates the text. Unstored matches are represented by `matchesTruncated` and a count. Target: every stored item |
| Partition coverage | When `partitionComplete` is true, every source code point sits in exactly one covering. This measures disclosure. It does not measure retention |
| Planted-evidence retention | Fraction of planted spans covered by an `ORIGINAL` segment whose text matches the transformed source. An omission does not count, even when the omission span and reason are correct. On a fixture whose budget can hold the planted spans and whose caps are not hit, the target is full retention |
| Duplicate correctness | Precision and recall of exact groups against the manifest. Target: exact agreement on the equality fixtures |
| Summary labeling | When Summarized mode exists, every generated segment is `SUMMARY`, has derived-from spans, and fails a check if it is marked `ORIGINAL` |
| Determinism | Two runs produce equal packages |
| Estimator error | Optional later comparison against a caller-supplied reference tokenizer. Reported as a distribution of estimate minus reference. Never reported as proof of exact token counts |

Retention and partition coverage are both reported. A planted span that appears only as an omission passes partition coverage and fails retention. A fixture with room in the budget asserts retention. A tighter budget asserts the omission span and reason, and that omission is not counted as retention.

### 13.3 Performance harness

When implementation reaches the detection milestone, add a JMH harness in a source set that is not shipped in the core jar. Benchmarks vary:

- input length
- duplicate rate
- pattern count and pattern length
- segment count
- script profile, at least ASCII and one non-Latin profile

Each published result records the JDK build, OS, CPU class, heap flags, JMH options, git revision, and dataset identity. Results live outside this requirements document, in the run notes attached to that revision. Until the first archived run, documentation says that a baseline does not exist yet.

Microbenchmarks are not a substitute for the heap-bounded streaming test in section 12.2.

### 13.4 External corpora

An optional large corpus may be used locally and must not be committed. The benchmark notes record its license, a content hash, and where a third party can obtain the same bytes. Public-domain or Creative Commons text is appropriate. The core test suite must pass with no external corpus present.

The project does not ship a dataset of personal conversations, support tickets, or other private text.

## 14. MVP and non-goals

### 14.1 MVP

The MVP is the first usable library slice: milestones M1 through M3. A Java caller can, from their own application:

- stream plain text and prepare an in-memory message list
- apply no normalization, newline canonicalization, or the whitespace profile, and see that choice on the report
- search a literal pattern set over the full input
- group or collapse exact duplicates
- select segments with the heuristic or with an explicit prefix
- pack them under a token budget using the built-in or a custom estimator
- read a `prepare` package whose original excerpts resolve to source spans and whose omissions are coalesced spans

Preserve and Selective work. A request for generated summaries fails until its milestone. There is no command-line tool, no near-duplicate detector, and no embedding ranker in the MVP. Constant-memory streaming is Preserve `write` and match emission. Selective provenance is the span index described in section 3.6, not a second copy of the corpus and not an O(1) heap.

The illustrative types in section 16.4 are not extra MVP surface. M1 implements the smallest types that satisfy the behavioral requirements.

### 14.2 Non-goals

The project will not:

- build a general-purpose chat application or a hosted AI service
- train or serve a language model
- depend on a specific model provider, hosted API, or API key
- replace a full-text search engine or provide a query language comparable to one
- guarantee that lossy compression preserves every fact, quotation, or relevant passage
- promise that Preserve reduces LLM token counts
- add distributed workers, a cluster, a queue, or a microservice architecture before a measured single-process limit says the library cannot meet a real workload
- require Docker, a database, or a background daemon
- perform OCR, speech recognition, HTML rendering, or browser automation
- translate text
- parse untrusted input as code or as a plugin
- enable telemetry or upload source text

A local command-line wrapper and optional in-process extension interfaces are in scope for later milestones. A network service is not.

## 15. Milestones, dependencies, and acceptance criteria

The maintainers implement these in order. M1 is shared work on the model. After the segment and span types are in place, pattern search and exact deduplication can proceed in parallel. Packing starts after both report their results into the package model. Each milestone should land as a small series of pull requests rather than one large change.

No calendar dates are attached. The sequence is the commitment.

### 15.1 Dependencies

| Dependency | Role |
| --- | --- |
| JDK 21 | Compile and test baseline |
| Gradle Wrapper | Build. Wrapper files are committed when the build is added |
| JUnit Jupiter | Unit and fixture tests. Version pinned at M1 from the current stable line |
| JMH | Benchmark source set at M2. Not a core dependency |

The core artifact adds no other runtime dependency without a recorded decision. In particular, the core does not add a JSON library, an HTTP client, a logging facade, or a tokenizer. The later command-line milestone may add a JSON library to that separate artifact only.

Tests and the build must succeed with the network disabled, aside from the first time a developer resolves dependencies. The wrapper and dependency versions are committed so a later build does not float.

### 15.2 M0 — Repository foundation

This milestone is documentation and repository policy.

Acceptance:

- [idea.md](idea.md), this PRD, the README, `CONTRIBUTING.md`, `LICENSE`, `.gitignore`, and `.editorconfig` agree on scope and license
- The license text is Apache License 2.0
- The README describes planned work and does not claim that the library exists
- No application modules, Docker files, or CI workflows are added in this milestone

### 15.3 M1 — Foundation library

Deliver the Gradle build, the public input and report types, streaming decode, `Normalization.none()`, `Normalization.newlines()`, `Normalization.whitespace()`, span mapping, the three segmenters, Preserve-mode `write`, and the listener.

Acceptance:

- FR-01 through FR-08, FR-15, FR-18, FR-29, and FR-30 have tests
- A Preserve round-trip test covers a chunked reader, including a split CRLF
- A whitespace-profile test shows `transformed`, and every output code point maps back to a decoded-text span
- The Unicode fixtures in section 10 pass or, for the illegal UTF-8 fixture, fail as specified
- The forbidden-API test in section 11 passes
- A streaming Preserve test uses an input larger than the test heap and does not retain it
- Two runs on the same input return equal reports
- The core jar's runtime classpath does not add third-party libraries

### 15.4 M2 — Detection and exact duplicates

Deliver literal multi-pattern search, exact grouping and collapse, limits, and the JMH harness skeleton.

Acceptance:

- FR-09 through FR-12, FR-27, and FR-28 have tests
- A match that crosses a read boundary is found and carries the decoded span
- Duplicate fixtures match the manifest exactly, including representative choice
- Hash-bucket tests include a case that would collide if equality were not checked, or the implementation is structured so grouping calls `String.equals`
- The streaming scan test counts matches on an input larger than the test heap
- JMH classes are not inside the core jar
- No throughput number is added to the README

### 15.5 M3 — Selection, packing, and the MVP report

Deliver the task, heuristic and prefix selection, both packing strategies, both output orders, the built-in estimator, custom estimators, and the partition report. This milestone completes the MVP.

Acceptance:

- FR-14 for Preserve and Selective, and FR-16, FR-19 through FR-26, have tests
- A request for generated summaries fails with a documented error
- A retention-cap fixture sets `analysisTruncated`, covers the remainder with one `TRUNCATED` span, and does not count that tail as retained evidence
- Quote fidelity and partition coverage hold on the fixture set
- A segment larger than the budget is omitted with `EXCEEDS_BUDGET` and is not split
- Package cost never exceeds `maxTokens` in a property test over generated segments
- Heuristic selection with neither a task nor patterns fails at construction
- Prefix selection is reachable only through `Selection.prefix()`
- A planted-evidence fixture with a budget defined to fit the planted spans retains them, and a tighter budget omits them with spans and reasons
- Determinism tests include tie-breaks

### 15.6 M4 — Conversation-aware packing

Deliver neighbor expansion, optional timestamp ordering, and a documented JSON mapping for a conversation file. The JSON parser may live in a small separate source set if it would force a parser dependency into the core. The core message model from M1 stays dependency-free.

Acceptance:

- FR-31 has tests: neighbors are included only when they fit, they carry reason `NEIGHBOR`, and source order is preserved in the default output
- Timestamp order is stable when timestamps are missing or equal
- An adapter test reads a fixture of messages and produces the same package as the in-memory message API

### 15.7 M5 — Optional extensions

Deliver the `Summarizer` and near-duplicate extension points, plus one near-duplicate implementation chosen from the candidates in section 3.3. Provide a test double summarizer that marks its output as a summary. Do not add a provider SDK.

Acceptance:

- FR-13 and FR-17 have tests
- Summary text that does not appear in the source is still `SUMMARY` and still carries derived-from spans
- Near-duplicate groups set `approximate = true` and record the algorithm id and threshold
- A fixture of unrelated strings that happen to share a short prefix does not have to stay ungrouped, but if they group, the approximate flag is set
- The default pipeline still performs no near-duplicate work and no summary call
- The core jar still has no network API references

### 15.8 M6 — Integration and release hygiene

Deliver usage examples, a thin command-line wrapper (FR-32), semantic-versioning notes, a security-reporting document, and a repeatable benchmark job. The Maven coordinates are decided. Publishing to a public artifact repository waits on the release-channel choice in section 16.5.

Acceptance:

- The CLI is a separate artifact that depends on the core
- CLI tests cover Preserve and Selective on a temporary file and do not log fixture text
- Examples compile against the public API
- A contributor can run the unit tests from a documented command
- The benchmark job records the metadata from section 13.3 and does not fail the build for lack of a historical baseline until one has been accepted
- `SECURITY.md` describes how to report a vulnerability

## 16. Risks, trade-offs, and open questions

### 16.1 Risks

| Risk | Why it matters | Mitigation in this design |
| --- | --- | --- |
| Callers treat the built-in estimator as an exact tokenizer | A downstream request can overflow a real context window | Estimator id is on every report. Documentation states the formula and its bias. A custom estimator is a first-class API |
| Callers treat heuristic selection as complete retention | Relevant text can be omitted with a confident-looking package | Reasons are feature-level. Omissions set `lossy`. Evaluation reports planted retention and partition coverage as separate numbers. The objective string is not described as understood |
| Exact hashes used as identity | A collision would merge different texts | Equality confirms group membership |
| Near-duplicate false merges | Different passages could be collapsed | Feature is off by default, groups are marked approximate, and collapse of approximate groups requires an explicit policy at implementation time |
| UTF-16 versus code-point confusion | Java APIs use `char` indexes. Emoji and supplementary characters would get the wrong span | Public spans are code points. A helper converts. Fixtures include supplementary characters |
| Selective mode retains too much text | A large archive can exhaust heap | Retention cap, truncation flag, and Preserve streaming for the full-text path |
| Match floods | A short pattern on a large input can allocate without bound | `maxMatches` stops storage and sets `matchesTruncated` |
| Scope grows into a service or a search product | Two people cannot maintain that surface | Non-goals in section 14. Extension points are in-process and caller-constructed |
| A summarizer dependency pulls a provider SDK into the core | The library would stop being provider-agnostic and offline | Summarizer is an interface. Implementations live outside the core artifact |
| Public API freezes too early | Weights, limits, and type names will move during M1–M3 | Pre-1.0 policy in NFR-09. Ship the MVP as a preview version |

### 16.2 Trade-offs

- **Code-point spans instead of Java `char` indexes.** Callers do a conversion. Spans stay meaningful for supplementary characters.
- **Global detection before packing.** The scan sees the whole input, which is the correct search result, and the implementation must stream or cap storage rather than concatenate a second copy of the corpus for each stage.
- **Candidate cap instead of an unbounded exact ranking.** Past the cap, selective `prepare` stops classifying and covers the rest with one tail span. The span index itself still grows with the number of runs before the cap.
- **No regular expressions in the MVP.** Callers with regex needs keep them outside, or wait for a later bounded design. The core avoids backtracking on untrusted patterns.
- **No bundled tokenizer.** Estimates can disagree with a provider. The core stays small and provider-agnostic.
- **Integer heuristic features instead of embeddings.** Results are explainable and offline. Semantic recall is weaker. Embeddings can arrive later as a caller-supplied ranker.
- **Source order as the default output.** The model sees chronology. The highest-scoring passage may appear after a weaker one. Score order is opt-in.
- **One jar for the MVP.** Releases and reviews stay simple. Optional parsers and the CLI become separate artifacts when they would add dependencies.
- **Constructor injection instead of classpath plugin discovery.** Behavior is visible at the call site. There is no `ServiceLoader` scan and no remote plugin loading in the MVP.
- **Newline canonicalization is opt-in.** Default Preserve round-trips code points. Callers who want stable line endings accept `transformed = true`.

### 16.3 Architecture alternatives

**Recommended: one core artifact, package boundaries, caller-injected extensions.**

Packages under `io.github.cbrwvy.contextforge`:

| Package | Responsibility |
| --- | --- |
| `io.github.cbrwvy.contextforge` | `Pipeline`, modes, `Source`, `ContextPackage`, `Segment`, errors |
| `ingest` | Readers, charsets, composite sources, sessions |
| `normalize` | Profiles and offset maps |
| `segment` | Message, document, and paragraph segmenters |
| `detect` | Literal pattern sets and matches |
| `dedup` | Exact groups now, near-duplicate API when scheduled |
| `select` | Task, heuristic, prefix |
| `pack` | Budget, packing strategies, output order |
| `estimate` | `TokenEstimator` and the built-in estimator |
| `report` | Report assembly and partition checks used by tests |

**Alternative: a multi-module Gradle build from the first commit.** This separates API and implementation earlier and costs more build structure than two people need before a second artifact exists. Revisit at M5, when a CLI or summarizer example would otherwise add dependencies to the core. The package list above is the seam along which a later split would happen.

**Alternative: `ServiceLoader` plugins.** This hides which implementation runs and complicates deterministic tests. Rejected for the MVP. An application can still wrap ContextForge in its own plugin system.

**Alternative: a long-running HTTP or RPC service.** This adds deployment, authentication, and a place where user text could be logged or sent onward. The idea allows a service only if real usage shows the in-process library and a later CLI are not enough. It is a non-goal for the milestones in this document.

**Alternative: shelling out to an external search or compression program.** This breaks the offline, dependency-free core and makes Windows and macOS behavior part of the contract. Rejected.

### 16.4 Proposed Java API

The types below are an illustration, not a signature that M1 must copy. Names, builders, and exception types may change before a preview release. The behavioral requirements are sections 6 and 8, together with the memory rule in section 3.6. Offsets in the illustration use `long`.

Pipeline construction:

```java
Pipeline pipeline = Pipeline.builder()
    .mode(ProcessingMode.SELECTIVE)
    .normalization(Normalization.none())
    .segmenter(Segmenter.paragraphs())
    .patterns(PatternSet.literals("timeout", "payment failed"))
    .deduplication(Deduplication.exact(DuplicatePolicy.GROUP))
    .task(Task.builder()
        .objective("Explain repeated checkout timeouts")
        .preferredTerms("checkout", "timeout")
        .build())
    .selection(Selection.heuristic())
    .packing(Packing.greedySkip())
    .outputOrder(OutputOrder.SOURCE)
    .tokenBudget(TokenBudget.builder()
        .maxTokens(4_000)
        .estimator(TokenEstimators.codepointsDividedBy(4))
        .overheadPerSegment(8)
        .build())
    .limits(Limits.defaults())
    .listener(Listener.none())
    .build();
```

Preparing a reader:

```java
try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    ContextPackage result = pipeline.prepare(
        Source.reader(reader, "ticket-export"));
    for (Segment segment : result.segments()) {
        if (segment.kind() == SegmentKind.ORIGINAL) {
            SourceSpan span = segment.span();
            String sourceId = span.sourceId();
            long start = span.startCodePoint();
            long end = span.endCodePoint();
            // resolve [start, end) against the decoded source the caller kept
        }
    }
    Report report = result.report();
    boolean lossy = report.lossy();
    int estimatedTokens = report.estimatedTokens();
}
```

`Source.reader` accepts text the caller has already decoded. The `InputStream` factory is the one that decodes:

```java
ContextPackage result = pipeline.prepare(
    Source.utf8(inputStream, "ticket-export"));
```

Preparing messages:

```java
Source thread = Source.messages("thread-42", List.of(
    Message.of(
        "m1",
        "agent",
        Instant.parse("2026-04-02T10:15:00Z"),
        "The customer saw a timeout."),
    Message.of(
        "m2",
        "customer",
        Instant.parse("2026-04-02T10:16:10Z"),
        "It happened again at checkout.")
));
Pipeline messages = Pipeline.builder()
    .mode(ProcessingMode.SELECTIVE)
    .segmenter(Segmenter.messages())
    .task(Task.builder()
        .objective("Explain repeated checkout timeouts")
        .preferredTerms("checkout", "timeout")
        .build())
    .selection(Selection.heuristic())
    .tokenBudget(TokenBudget.builder()
        .maxTokens(4_000)
        .estimator(TokenEstimators.codepointsDividedBy(4))
        .build())
    .build();
ContextPackage result = messages.prepare(thread);
```

Preserve streaming:

```java
Pipeline preserve = Pipeline.builder()
    .mode(ProcessingMode.PRESERVE)
    .normalization(Normalization.newlines())
    .build();
Report report = preserve.write(
    Source.utf8(inputStream, "archive-001"),
    TextSink.utf8(outputStream));
```

A caller-supplied estimator:

```java
TokenEstimator estimator = new TokenEstimator() {
    @Override
    public String id() {
        return "application-tokenizer";
    }

    @Override
    public int estimate(String text) {
        return applicationTokenizer.count(text);
    }
};
```

A streaming session for chunked text the caller already decoded:

```java
try (StreamingSession session = pipeline.openSession("notes")) {
    session.accept(chunkOne);
    session.accept(chunkTwo);
    ContextPackage result = session.finish();
}
```

Error handling:

```java
try {
    pipeline.prepare(source);
} catch (InvalidSourceException ex) {
    String sourceId = ex.sourceId();
    OptionalLong offset = ex.codePointOffset();
} catch (LimitExceededException ex) {
    String limitName = ex.limitName();
}
```

Illustrative selective JSON, for the later CLI and for reviewers of the report shape. Field names are a proposal, not an MVP schema. This document does not add a JSON dependency to the core. Offset numbers are not limited to 32 bits. The numbers below use the initial built-in estimator, zero overhead, and a preferred term of `checkout`. `It happened again at checkout.` is 30 code points, so its estimate is `(30 + 3) / 4 = 8`. That higher score is packed first and fills a budget of 8. `The customer saw a timeout.` is 27 code points and is omitted because it does not fit. Message boundaries are not blank-line delimiters, so there is no `SEGMENT_BOUNDARY` span.

```json
{
  "mode": "SELECTIVE",
  "lossy": true,
  "transformed": false,
  "estimator": "codepoints-divided-by-4",
  "maxTokens": 8,
  "estimatedTokens": 8,
  "budgetExhausted": true,
  "segments": [
    {
      "kind": "ORIGINAL",
      "text": "It happened again at checkout.",
      "source": { "id": "m2", "start": 0, "end": 30, "unit": "CODE_POINT" },
      "reasons": ["PREFERRED_TERM:checkout"]
    }
  ],
  "omissions": [
    { "id": "m1", "start": 0, "end": 27, "reason": "BUDGET" }
  ]
}
```

### 16.5 Decisions and remaining questions

These choices are decided and are the baseline for implementation:

- Java 21.
- Gradle with the Kotlin DSL, and a committed wrapper when the build is added.
- Coordinates `io.github.cbrwvy:contextforge` and base package `io.github.cbrwvy.contextforge`.
- One core JAR for the MVP.
- Pluggable token estimation, with the initial code-point heuristic in section 5.4. No provider tokenizer in the core.
- No required model provider, external service, or network access.
- The CLI, near-duplicate detection, and summarization come after the MVP.

Nothing below blocks merging this document or starting M1.

Resolve during implementation, before the first preview tag:

- The built-in estimator id and divisor, if a fixture shows the initial definition should change. Lock the chosen values in tests at M3.
- Heuristic weights and the final tie-break key order, at M3.
- The numeric guards in section 8. The listed values are starting points.
- The copyright-owner string for source headers. Omit headers until it is chosen. M1 does not wait on it.

Resolve at the milestone that needs them:

- Near-duplicate algorithm, n-gram width, threshold, and whether approximate groups may be collapsed. M5, after the fixture study. SimHash and MinHash remain the candidates. Case folding is not part of that choice.
- Release channel. GitHub Releases for preview jars and Maven Central after 1.0 are the working assumption. Neither is configured yet.
- Security contact and `SECURITY.md`, before the first tagged release.

Not in the MVP, and not required before a caller asks for them: case-insensitive patterns, NFC or NFD, and byte offsets. A later case-insensitive mode must name its case rule. `String.toLowerCase()` is not that specification by itself. NFKC stays discouraged.

## 17. Traceability to the idea

| Idea topic | Where this PRD specifies it |
| --- | --- |
| Streaming ingestion and adapters | Sections 3.1 and 5.5, FR-01–FR-03 |
| Normalization without silent edits | Sections 4 and 6, FR-06–FR-07, default `Normalization.none()` |
| Pattern detection with source references | Section 3.2, FR-09–FR-10 |
| Exact and near-duplicates, grouping versus removal | Section 3.3, FR-11–FR-13 |
| Conversation order, speakers, timestamps, neighbors | Sections 5.4 and 15.6, FR-02, FR-30, FR-31 |
| Task-aware selection, heuristics first | Section 5, FR-19–FR-20 |
| Token budgets and pluggable estimators | Section 5.4, FR-21–FR-22 |
| Provenance and statistics | Section 3.5, FR-25–FR-26 |
| Preserve, Selective, Summarized | Section 4 |
| Full-input search, not search over a compressed subset | Section 3, FR-27 |
| Provider-agnostic, offline core | Sections 1, 11, and 14 |
| Unicode, determinism, explicit loss | Sections 4, 9, and 10 |
| Measured performance | Sections 12 and 13 |
| Staged delivery and non-goals | Sections 14 and 15 |
| License and contribution workflow | `LICENSE`, `CONTRIBUTING.md`, and section 15.2 |

This PRD does not override the idea's caution: lossy modes disclose loss, and they do not promise that facts or relevant passages survive.
