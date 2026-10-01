# ADR-0002: Standardize documentation language to English

## Status

Accepted

## Context

ADR-0000 and ADR-0001, along with `docs/troubleshooting.md`, were written in Portuguese, since
the project started as a direct response to a feedback received in Portuguese (iFood — Elas São
Tech program). As the project started being shared publicly (GitHub repository, LinkedIn posts),
the goal shifted to maximizing reach for an international audience, including recruiters and
technical leads who may not read Portuguese.

A README in English was introduced, but it links directly to ADRs and documentation still written
in Portuguese — creating an inconsistent experience for an English-speaking reader who follows
those links expecting content in the same language as the page that referenced them.

## Decision

From this point forward, all new code, comments, SQL, commit messages and documentation
(including ADRs, README files and any Markdown documentation) will be written in **English**.

The two existing ADRs (0000, 0001) and `docs/troubleshooting.md`, already written in Portuguese,
will **not** be translated immediately. They remain as-is, tracked as documentation debt, to avoid
blocking project momentum on a translation task that does not add new technical value. They may be
translated opportunistically in the future, without urgency.

## Consequences

### Positive

- Wider reach for the portfolio's primary audience (recruiters, technical leads, international
  job postings), consistent with the project's goal of being evaluated from GitHub and LinkedIn.
- Consistency from this point onward: every new artifact (code, ADRs, commit messages) follows the
  same language, removing ambiguity for future contributions.
- Documenting this decision as an ADR, rather than silently switching languages, keeps the
  project's own documentation practice coherent with the principle established since ADR-0000:
  decisions are recorded, not just made.

### Negative

- The repository will temporarily have **mixed-language documentation**: ADR-0000, ADR-0001 and
  `docs/troubleshooting.md` in Portuguese, everything else in English. A reader going through the
  ADR index may need to use translation tools for the earliest two decisions.
- There is a non-zero chance the Portuguese ADRs are never translated, since "do it later" tasks
  without a deadline tend to be deprioritized indefinitely. This is accepted here as the lesser
  cost compared to pausing active development for a translation pass.

## Alternatives considered

- **Translate ADR-0000 and ADR-0001 immediately, before continuing:** rejected because it delays
  the next phase of the roadmap (domain classes, concurrency test refinement) for a task that does
  not change the project's engineering substance, only its language.
- **Keep everything in Portuguese and translate the README back to Portuguese:** rejected because
  it directly contradicts the goal of maximizing reach for the project's intended audience.
- **Maintain a bilingual version of every document (PT + EN side by side):** rejected as
  overengineering for a solo portfolio project — doubling the maintenance cost of every future
  document for a benefit (serving two audiences at once) that does not currently exist, since the
  primary audience from this point on is English-speaking.
