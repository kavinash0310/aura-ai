# ADR 0027: Local Pub ecosystem range assessment

- Status: Accepted for the local MVP
- Date: 2026-11-18

### Context

AURA correlates SBOM package identities locally after an owner explicitly retrieves an OSV advisory for a declared CVE. OSV identifies the Dart package manager ecosystem as `Pub`. Pub versions resemble SemVer, but Dart's `pub_semver` ordering treats build suffixes as part of precedence, unlike SemVer 2.0.0. Reusing the generic SemVer comparator would therefore misorder some Pub releases.

### Problem

Without a Pub evaluator, the browser leaves Pub range evidence unassessed. Applying the existing SemVer comparator to Pub `ECOSYSTEM` ranges would ignore build suffixes that Pub uses to distinguish versions.

### Options considered

1. Leave Pub affected-version ranges unassessed.
2. Treat Pub as generic SemVer 2 and ignore build suffixes.
3. Add a bounded Pub comparator that follows `pub_semver` ordering for a strict SemVer-compatible subset.

### Selected decision

Support Pub literal version equivalence and OSV `ECOSYSTEM` range comparison locally. Compare the core and prerelease using the existing strict SemVer 2 comparator. If these are equal, compare build identifiers according to Pub's ordering: no build suffix sorts before a present suffix, numeric identifiers sort numerically before text identifiers, and missing identifiers sort before additional identifiers. Accept only strict SemVer-compatible inputs up to 120 characters, with numeric core, prerelease, and build identifiers limited to 15 digits so browser comparisons remain exact. Inputs outside this subset remain `NOT_ASSESSED`. Do not evaluate Pub `SEMVER` or `GIT` ranges in this slice.

The evaluator continues to require complete, structured OSV events, alternating introduction/end boundaries, and recognized `introduced`, `fixed`, `last_affected`, and `limit` semantics. Truncated, malformed, withdrawn, or unsupported evidence remains `NOT_ASSESSED`.

### Reasons

- Dart documents package versions as three-part semantic versions with optional prerelease and build suffixes.
- The official `pub_semver` implementation includes build suffixes in ordering, so generic SemVer ordering is insufficient.
- A bounded, local comparator adds a common ecosystem without sending local package inventory to OSV.
- Restricting evaluation to OSV `ECOSYSTEM` avoids claiming support for other range encodings.

### Advantages and disadvantages

- Adds Pub advisory coverage while preserving the local inventory boundary.
- Matches Pub's key ordering distinction for build suffixes.
- The accepted strict subset excludes leading-zero prerelease identifiers and numeric fields longer than 15 digits; these remain not assessed.
- Regression fixtures cover Pub build-suffix ordering, numeric/text identifiers, fixed boundaries, unsupported range kinds, and the numeric-segment cap. They cover the supported subset, not every possible `pub_semver` input.

### Consequences

- Export policy metadata and user-visible coverage text include Pub ordering and `ECOSYSTEM` ranges.
- Other ecosystem-specific version semantics remain unsupported unless explicitly added.
- This remains advisory applicability comparison; it does not assess asset context or scan a host.

### Conditions to reconsider

Revisit if Dart changes `pub_semver` comparison, OSV changes Pub range conventions, fixtures reveal a mismatch, or the current strict subset proves too restrictive for useful OSV evidence.

### Learning and interview prompts

- Why can a SemVer-looking package ecosystem still need a custom comparator?
- How does Pub ordering of build identifiers differ from SemVer 2 precedence?
- Why should numeric values outside the exact browser comparison bound remain not assessed?
