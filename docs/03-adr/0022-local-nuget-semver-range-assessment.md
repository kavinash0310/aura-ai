# ADR 0022: Restricted local NuGet SemVer range assessment

## Status

Accepted for the local M7 SBOM review.

## Context

AURA already compares exact OSV affected-version strings for any ecosystem and structured SemVer 2 ranges for npm and Go modules. NuGet package IDs are case-insensitive, and NuGet uses SemVer ordering with additional behaviors: it accepts abbreviated and four-component versions, treats prerelease labels without case sensitivity, and normalizes build metadata. A generic strict SemVer comparator would not safely cover all NuGet versions.

## Decision

- Accept ASCII NuGet package IDs only when they match `^[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*$`, then compare them without case after removing the version segment. Do not fall back to package names or fuzzy identity.
- Keep raw exact-version string matches for every ecosystem.
- For NuGet exact-version lists and `SEMVER` ranges, compare only strict three-component SemVer 2 versions with lowercase prerelease labels. SemVer precedence ignores build metadata, matching NuGet's ordering for this subset.
- Return `NOT_ASSESSED` for abbreviated versions, four-component versions, uppercase prerelease labels, malformed event sequences, unsupported range types, and truncated evidence unless the raw version strings match exactly.
- Keep all comparison local in the browser. Send only the explicitly selected CVE ID to OSV; never send an SBOM or local package inventory.

## Consequences

NuGet coverage expands without a NuGet runtime or NuGet-specific library in the frontend. The accepted subset is intentionally narrower than NuGet's complete version model. A `NO_MATCH_IN_RETURNED_OSV_VERSION_DATA` result describes only supported evidence in the returned advisory and does not prove that a package is safe.

The OSV schema defines `SEMVER` ranges using SemVer 2.0 precedence. Microsoft's [NuGet package version reference](https://learn.microsoft.com/en-us/nuget/concepts/package-versioning) documents NuGet's normalization, four-part versions, abbreviated versions, and case-insensitive prerelease comparison. The documented [NU1017 package-ID rule](https://learn.microsoft.com/en-us/nuget/reference/errors-and-warnings/nu1017) defines the restricted package-name pattern; AURA applies an ASCII-only subset before case normalization.

## Verification

The browser evaluation covers case-insensitive NuGet PURL identity, supported ranges, build-metadata precedence, and `NOT_ASSESSED` outcomes for uppercase prerelease and four-component versions.
