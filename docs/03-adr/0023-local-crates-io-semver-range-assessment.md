# ADR 0023: Local crates.io SemVer range assessment

## Status

Accepted for the local M7 SBOM review.

## Context

AURA already compares exact OSV affected-version strings for any ecosystem and evaluates complete `SEMVER` ranges for npm, Go modules, and a restricted NuGet subset. OSV identifies Rust registry packages as `crates.io`, and its schema defines `SEMVER` boundaries by SemVer 2.0 precedence. Cargo package versions also follow SemVer, but AURA should not treat Cargo dependency requirements or other OSV range types as generic SemVer.

## Decision

- Add range assessment only when the OSV ecosystem string is exactly `crates.io` and the range type is `SEMVER`.
- Require canonical strict SemVer 2.0 versions with exactly three numeric components and no leading `v`. Accept valid prerelease and build-metadata components; use SemVer precedence, which ignores build metadata.
- Reuse the existing bounded OSV event evaluator and npm `semver` parser. Keep package inventory and all assessment local; the only outbound value remains the explicitly requested CVE ID.
- Keep exact affected-version list matching as literal equality for all ecosystems.
- Leave `ECOSYSTEM`, `GIT`, malformed or incomplete ranges, truncated data, and invalid Cargo version forms as `NOT_ASSESSED`.
- Do not normalize Cargo package names or infer identity from names; require the existing PURL identity match.

## Consequences

The browser can assess complete crates.io SemVer ranges without adding a Rust runtime or another library. The result means only that the component version's SemVer precedence falls within OSV's returned range. It does not reproduce Cargo dependency requirement matching, prove the package is installed or reachable, or establish exploitability or asset risk.

The [OSV schema](https://ossf.github.io/osv-schema/) defines `SEMVER` ordering and includes a `crates.io` example. [The Cargo Book](https://doc.rust-lang.org/cargo/reference/manifest.html) requires three numeric version components and describes prereleases and informational build metadata. The Rust [`semver` crate documentation](https://docs.rs/semver/latest/semver/struct.Version.html) documents strict SemVer parsing and precedence.

## Verification

Browser fixtures cover a prerelease inside a range, the exclusive fixed boundary, build metadata ignored for precedence, and `NOT_ASSESSED` results for abbreviated, `v`-prefixed, four-part, and malformed versions.
