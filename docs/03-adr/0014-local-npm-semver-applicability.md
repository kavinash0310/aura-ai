# ADR 0014: Local npm SemVer applicability from OSV evidence

- Status: Accepted
- Date: 2026-11-08

## Context

ADR 0013 added local exact-PURL identity correlation but deliberately did not compare inventory versions with advisory ranges. AURA now receives OSV affected versions and structured range events, but an incorrect comparator could turn source data into a false vulnerability claim.

## Problem

The owner needs a small, auditable applicability check that provides useful SBOM evidence without pretending every package ecosystem uses the same version rules or sending local package details to OSV.

## Options considered

- Keep identity-only forever: safest, but leaves machine-readable OSV `SEMVER` evidence unused.
- Evaluate all OSV ranges using one generic parser: rejected because `ECOSYSTEM` and `GIT` ranges do not share SemVer ordering.
- Ask OSV to evaluate each local PURL/version: rejected for this slice because it would disclose inventory data and add a provider request.
- Compare exact OSV affected-version strings for exact PURL matches in any ecosystem, and evaluate ranges only for npm with strict SemVer 2: selected.
- Write a custom SemVer parser: rejected because a maintained parser/comparator already exists.

## Decision

Only after an explicit per-CVE OSV lookup from an SBOM finding, the browser may assess version data for an exact local/advisory PURL identity. Literal exact equality against OSV's enumerated affected versions is supported for any ecosystem. Range evaluation uses `semver` 7.8.5 with strict parsing and is limited to OSV `SEMVER` ranges when the package ecosystem is `npm`. It supports valid `introduced`, `fixed`, `last_affected`, and `limit` events. It rejects malformed, ambiguous, truncated, or unsupported ranges; other ecosystem ranges and OSV `ECOSYSTEM`/`GIT` ranges remain `NOT_ASSESSED`.

Results use `AFFECTED`, `NO_MATCH_IN_RETURNED_OSV_VERSION_DATA`, or `NOT_ASSESSED`. A no-match is limited to the complete returned OSV version data and is not a general safety claim. A positive result is source-backed applicability evidence, not proof that the package is installed, deployed, reachable, exploitable, or a business risk. Withdrawn OSV advisories are not assessed.

The API sends only the exact CVE ID. Local component versions stay in the browser comparison, and AURA persists no derived result. Provider parser caps are surfaced as truncation flags so they can block negative conclusions.

## Consequences

- Exact enumerated OSV versions can be matched without interpreting ecosystem version semantics; a common npm case can also show why a version does or does not match an explicit OSV range.
- Source event parsing, SemVer comparison, and status labeling are independently tested.
- The browser bundle gains the `semver` package and its TypeScript declarations.
- Unsupported range semantics, malformed range records, incomplete negative evidence, and withdrawn advisories produce no applicability conclusion; an exact affected-version entry remains direct evidence even if unrelated ranges were truncated.
- The data flow remains local after the explicit OSV response; no PURL or inventory version is added to provider requests.

## Conditions to reconsider

Add another ecosystem only after selecting its maintained version comparator, defining normalization rules, reviewing its OSV `ECOSYSTEM` range semantics, and adding curated fixtures for boundaries, prereleases, epochs/revisions where relevant, malformed data, and source truncation. Do not broaden support merely because sample versions look similar.

## Learning and interview prompts

- Explain why `SEMVER` is distinct from OSV's `ECOSYSTEM` range type.
- Explain why a `NO_MATCH_IN_RETURNED_OSV_VERSION_DATA` result is weaker than “not vulnerable.”
- Explain why package PURL identity and version applicability are separate gates.
- Propose how to test a future Python package-version comparator without reusing npm rules.
