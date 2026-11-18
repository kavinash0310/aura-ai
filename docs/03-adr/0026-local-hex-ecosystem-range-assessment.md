# ADR 0026: Local Hex ecosystem range assessment

- Status: Accepted for the local MVP
- Date: 2026-11-18

### Context

AURA compares package versions locally after an owner explicitly retrieves an OSV advisory for a declared CVE. Hex is an OSV ecosystem whose package versions use Elixir's SemVer 2 version format. OSV may encode Hex ranges as `ECOSYSTEM`, so the range type must be handled according to the package ecosystem rather than guessed from the boundary strings.

### Problem

Without a Hex evaluator, the browser leaves Hex range evidence unassessed. Treating every OSV range as `SEMVER` would be too broad because `ECOSYSTEM` ranges are defined by ecosystem-specific semantics.

### Options considered

1. Leave Hex affected-version ranges unassessed.
2. Interpret Hex `ECOSYSTEM` ranges as generic, unbounded SemVer.
3. Use the existing strict SemVer comparator for Hex `ECOSYSTEM` ranges while enforcing Elixir's additional version limits.

### Selected decision

Support Hex literal version equivalence and OSV `ECOSYSTEM` range comparison locally. Use the existing strict SemVer 2 parser and comparator, restricted to valid three-part versions and Elixir's 14-digit limit for major, minor, patch, and numeric prerelease identifiers. Permit build metadata and ignore it for precedence, as Elixir's `Version.compare/2` does. Limit version input to 120 characters. Do not evaluate Hex `SEMVER` or `GIT` ranges in this slice; only `ECOSYSTEM` is supported for this ecosystem path.

The evaluator continues to require complete, structured OSV events, alternating introduction/end boundaries, and recognized `introduced`, `fixed`, `last_affected`, and `limit` semantics. Truncated, malformed, withdrawn, or unsupported evidence remains `NOT_ASSESSED`.

### Reasons

- Elixir documents a SemVer 2 format, three numeric core parts, a 14-digit maximum for core and numeric prerelease segments, and build metadata that does not affect comparison.
- Reusing the strict SemVer comparator avoids a second implementation while preserving the ecosystem's input limits.
- Restricting evaluation to OSV `ECOSYSTEM` avoids inferring support for unrelated range encodings.
- Version and range comparison remains local; no inventory values are sent to OSV.

### Advantages and disadvantages

- Adds useful Hex advisory coverage using a small, bounded code change.
- Keeps build metadata and prerelease ordering aligned with Elixir SemVer comparison.
- The accepted 14-digit version cap must be kept aligned with Elixir's parser behavior.
- Regression fixtures cover prerelease/fixed boundaries, ignored build metadata, and the numeric-segment cap. They cover the supported subset, not every possible Hex version input.

### Consequences

- Export policy metadata and user-visible coverage text include Hex/Elixir SemVer ordering and `ECOSYSTEM` ranges.
- Other ecosystem-specific version semantics remain unsupported unless explicitly added.
- This remains advisory applicability comparison; it does not assess asset context or scan a host.

### Conditions to reconsider

Revisit if Elixir changes `Version` parsing or ordering, OSV changes how Hex records encode ranges, fixtures reveal a mismatch, or the existing SemVer library no longer matches Elixir's comparison behavior for the accepted subset.

### Learning and interview prompts

- Why can one comparator safely support both a strict SemVer range and an ecosystem-specific range for a SemVer-based ecosystem only when the ecosystem's constraints are also applied?
- Why does Elixir cap numeric segments even when JavaScript can parse larger integers imprecisely?
- Why is build metadata excluded from precedence?
