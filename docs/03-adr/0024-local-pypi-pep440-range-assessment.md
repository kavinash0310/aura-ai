# ADR 0024: Local PyPI PEP 440 range assessment

- Status: Accepted
- Date: 2026-11-16

### Context

AURA already correlates SBOM PURLs locally and assesses complete OSV `SEMVER` ranges for npm, crates.io, a restricted NuGet subset, and Go modules. PyPI uses PEP 440 version semantics, which differ from SemVer; treating a PyPI range as SemVer can produce incorrect affected-version results.

### Problem

Extend local PyPI advisory assessment while preserving the existing rule that unsupported, malformed, or incomplete evidence remains `NOT_ASSESSED`. Local inventory names such as `Friendly_Bard` must match the equivalent OSV PyPI PURL name `friendly-bard` without falling back to package names when a PURL is absent.

### Options considered

1. Continue exact string matching only for PyPI.
2. Reuse a SemVer comparator for PyPI ranges.
3. Add PEP 440 version ordering and PEP 503 PURL-name normalization, and assess only OSV `ECOSYSTEM` ranges for the exact `PyPI` ecosystem.

### Selected decision

Use the JavaScript `@renovatebot/pep440` implementation for PEP 440 ordering. Normalize `pkg:pypi` package names by lowercasing and collapsing runs of `.`, `_`, and `-` into `-`, after validating the unqualified single-segment name shape. Compare PyPI exact version-list entries under PEP 440 equality and compare complete `ECOSYSTEM` range events under PEP 440 ordering. Keep `SEMVER`, `GIT`, malformed events, unsupported versions, and truncated range evidence `NOT_ASSESSED`.

Reject numeric version fields outside JavaScript's safe integer range before using the comparator. Continue to compare component inventory locally; only the explicitly selected CVE is sent to OSV.

### Reasons

PEP 440 defines PyPI version ordering and accepted version normalization; PEP 503 defines the package-name normalization rule. OSV distinguishes SemVer `SEMVER` ranges from ecosystem-specific `ECOSYSTEM` ranges. A JavaScript implementation lets AURA keep this comparison in the browser without adding a service or sending local package inventory elsewhere.

### Advantages and disadvantages

- PyPI ranges can be assessed locally across epochs, pre-releases, post-releases, development releases, and local labels supported by the PEP 440 comparator.
- PURL package names follow PyPI's published normalization rule.
- A third-party comparator becomes a production dependency; updates require keeping boundary behavior aligned with the specification.
- AURA still does not establish whether the component is installed, reachable, exploitable, or material to an asset.

### Consequences

- `PyPI` + `ECOSYSTEM` is the only new ordered range combination.
- PyPI exact version strings that differ textually but compare equal under PEP 440 are treated as the same listed version.
- Every version field is bounded to 120 characters and its decimal integer fields must fit JavaScript's safe integer range.
- PEP 440 package-version comparison does not imply dependency-specifier matching or Python environment-marker evaluation.
- No additional provider request is made for correlation.

### Conditions to reconsider

Revisit if the comparator fails reviewed PEP 440 fixtures, if OSV changes its PyPI range conventions, or if AURA adds a shared package-ecosystem library with an equally strict browser-only boundary.

### Learning and interview prompts

Explain why an OSV `ECOSYSTEM` range cannot be compared with SemVer, how PEP 440 orders a development release and local version, why PEP 503 normalization is applied only to PyPI PURLs, and why unsafe numeric fields stay `NOT_ASSESSED`.

### References

- [Python Packaging: version specifiers](https://packaging.python.org/en/latest/specifications/version-specifiers/)
- [Python Packaging: package-name normalization](https://packaging.python.org/en/latest/specifications/name-normalization/)
- [OSV schema: affected ranges](https://ossf.github.io/osv-schema/)
- [`@renovatebot/pep440` source](https://github.com/renovatebot/pep440) (Apache-2.0)
