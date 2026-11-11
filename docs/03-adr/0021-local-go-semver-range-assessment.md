# ADR 0021: Local Go module SemVer range assessment

- Status: Accepted
- Date: 2026-11-11

## Context

After exact PURL identity matching, AURA can compare OSV affected-version lists for any ecosystem and complete SemVer 2 ranges for npm. Go module versions also follow SemVer 2 precedence, but canonical module versions require a leading `v`; Go additionally has module-path rules and pseudo-version provenance checks that a generic SemVer library does not implement.

Official references: [Go Modules Reference: Versions](https://go.dev/ref/mod#versions), [Go `semver` package](https://pkg.go.dev/golang.org/x/mod/semver), and the [OSV schema](https://ossf.github.io/osv-schema/).

## Decision

- Extend local OSV range assessment to the exact OSV ecosystem string `Go` only when the range type is `SEMVER`.
- Require all inventory versions and range boundaries to be valid, canonical, `v`-prefixed SemVer 2 values. Permit the Go `+incompatible` suffix; reject other build metadata.
- Normalize the leading `v` before using the existing strict SemVer parser/comparator. Continue using the existing bounded OSV event state machine and require complete, structurally valid event evidence.
- Keep exact OSV version-list equality unchanged for every ecosystem. Keep `ECOSYSTEM`, `GIT`, other Go range types, noncanonical versions, malformed events, and truncated data as `NOT_ASSESSED`.
- State explicitly that AURA compares version precedence only. It does not validate a Go module path's `/vN` suffix against the version, or verify pseudo-version timestamps and commit ancestry.
- Include the expanded assessment policy and these limits in local UI text, exports, and documentation.

## Reasons

- Go module versions use SemVer ordering, including pre-releases before corresponding releases and ignored build metadata.
- Go's official version constraints are stricter than generic SemVer parsing; the required prefix and metadata policy prevent accidental acceptance of npm-style strings.
- Module path compatibility and pseudo-version ancestry require information AURA does not have, so the assessment stays scoped to ordering evidence and discloses the missing checks.
- Unsupported OSV ecosystem-specific ranges cannot be safely compared with the npm/Go SemVer parser and remain not assessed.

## Consequences and limitations

- A matching complete Go `SEMVER` range can report `AFFECTED`; a negative result is still limited to the complete OSV version evidence returned for that advisory.
- Valid Go pseudo-versions are compared by SemVer precedence. The comparison is not a `go` command validation and cannot establish whether the pseudo-version points to an ancestor commit.
- Dedicated fixtures for Go pseudo-version edge cases and path-major mismatches remain future work.

## Conditions to reconsider

Add more ecosystems only with their official version specification, strict fixtures for boundary/prerelease/build metadata behavior, and explicit `NOT_ASSESSED` cases. Add Go path or pseudo-version validation only if AURA receives and can safely validate the required module metadata.

## Learning prompts

- Why does AURA accept `v1.2.3` for Go but reject `1.2.3`?
- Why is `+incompatible` treated differently from arbitrary build metadata?
- Why does a semver match not establish that a Go pseudo-version has valid commit ancestry?
