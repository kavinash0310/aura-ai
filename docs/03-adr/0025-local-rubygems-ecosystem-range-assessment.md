# ADR 0025: Local RubyGems ecosystem range assessment

- Status: Accepted for the local MVP
- Date: 2026-11-18

### Context

AURA correlates SBOM package identities locally after an owner explicitly retrieves an OSV record for a declared CVE. OSV RubyGems affected ranges use `ECOSYSTEM` ordering. SemVer ordering cannot safely stand in for RubyGems `Gem::Version`, which has different prerelease and segment comparison rules.

### Problem

Without a RubyGems evaluator, an SBOM component can match only a literal RubyGems version string. Treating RubyGems boundaries as SemVer could incorrectly classify versions such as lettered prereleases or versions with numeric components beyond JavaScript's safe integer range.

### Options considered

1. Continue to leave all RubyGems range evidence not assessed.
2. Compare RubyGems ranges using the existing SemVer parser.
3. Implement the bounded `Gem::Version` ordering behavior in the browser and use it only for RubyGems `ECOSYSTEM` ranges.

### Selected decision

Implement RubyGems version comparison locally in the SBOM correlation module. Use it for equivalent exact affected-version entries and complete OSV `ECOSYSTEM` ranges when the advisory, package identity, and version evidence are usable. Keep PURL identity comparison exact for the `gem` package path; do not infer names or perform network lookups from this evaluator.

Accept only a strict subset of RubyGems' ASCII version grammar, with no surrounding whitespace and at most 120 characters. Split numeric and text segments, compare numeric segments as arbitrary-length decimal integers, preserve case-sensitive text ordering, apply RubyGems canonicalization for zero segments before prerelease text, and translate hyphen prerelease separators to the RubyGems `.pre.` representation. Continue using the established OSV event validation: `introduced` including `0`, exclusive `fixed`, inclusive `last_affected`, and `limit`; malformed, ambiguous, truncated, withdrawn, or unsupported evidence remains `NOT_ASSESSED`.

### Reasons

- RubyGems documents its `Gem::Version` segment and prerelease ordering separately from SemVer.
- Local arbitrary-length decimal comparison avoids floating-point rounding and adds no numeric dependency.
- A bounded grammar and input length limit keep processing predictable.
- OSV versions and ranges are third-party evidence; a negative result continues to mean only that no match was found in the returned record.

### Advantages and disadvantages

- Correctly handles the supported RubyGems prerelease ordering and avoids treating the scheme as SemVer.
- Adds a small in-browser comparator without sending package names, versions, or inventories to a service.
- The hand-maintained comparator needs cross-check fixtures against RubyGems before claiming release verification.
- RubyGems versions outside the ASCII grammar or length bound remain not assessed.

### Consequences

- Export policy metadata and UI coverage notes include RubyGems ordering and ranges.
- Regression fixtures cover numeric segment ordering, prerelease ordering, an exclusive fixed boundary, and rejection of unsupported whitespace. They cover selected cases, not exhaustive parity with every `Gem::Version` input.
- This remains advisory applicability comparison, not asset-context risk scoring, host scanning, or exploit verification.

### Conditions to reconsider

Revisit if OSV changes its RubyGems range semantics, RubyGems changes `Gem::Version` ordering, fixture comparison reveals a mismatch, or the project adopts a maintained cross-language comparator with demonstrated parity.

### Learning and interview prompts

- Why does matching the package ecosystem matter when comparing vulnerability ranges?
- Why do floating-point or JavaScript `Number` values make large numeric version segments unsafe?
- What evidence is needed before a custom implementation can claim parity with RubyGems?
