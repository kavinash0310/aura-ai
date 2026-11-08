# ADR 0013: Local PURL identity correlation without version inference

- Status: Accepted
- Date: 2026-11-08

## Context

SBOMs identify local packages, and OSV advisories identify packages with known CVEs. A source CVE can be explicitly declared against a package reference, while advisory package lists can offer additional corroborating identity. However, ecosystem version schemes and advisory range formats differ; the current AURA model does not contain a standards-complete comparator for each ecosystem.

## Problem

The owner needs a useful local connection between an OSV advisory's package list and the components already associated with an explicitly declared CVE, without implying that a package is vulnerable based only on similar names or unsupported range parsing.

## Options considered

- Exact package name plus ecosystem: rejected because names can collide across namespaces and aliases.
- Exact PURL identity after removing the version: selected as a narrow, deterministic identity correlation.
- Compare all ecosystem-specific version ranges: deferred until AURA has a reviewed comparator per ecosystem and fixtures for edge cases.
- Send component identifiers to a remote provider using an `affects` filter: rejected because it discloses local inventory and is not needed for this local correlation.

## Decision

After the owner explicitly loads an OSV advisory for a CVE declared in an uploaded SBOM, the browser may compare OSV affected-package PURLs with PURLs from the selected finding's locally resolved components. Normalize only the PURL package type to lowercase and remove the version segment after the final slash. Preserve namespace/name and qualifiers as supplied; ignore subpaths for package identity. Require both PURLs to match the helper's bounded minimum PURL shape; this is not a complete PURL standards validator. Do not fall back to names, ecosystem labels, or fuzzy matching. Label results as exact PURL identity matches and mark version assessment `NOT_PERFORMED`. Perform no new network request and persist no new data. Include the correlation and limitation in explicit JSON/CSV downloads.

## Consequences

- The user can see which local components share an advisory package identity.
- OSV receives only the CVE ID; the uploaded SBOM stays in the local app flow.
- A match is not evidence that the SBOM component is installed, deployed, or vulnerable at its listed version.
- Missing, differently encoded, reordered-qualifier, or absent PURLs can produce false negatives. The UI says that no exact PURL match does not rule out other identifiers.
- No package-name-only match or unsupported version comparator can create a vulnerable finding.

## Conditions to reconsider

Add range assessment only for an explicitly supported package ecosystem with a maintained parser/comparator, curated fixtures for inclusive/exclusive/fixed/introduced/pre-release cases, and user-visible coverage limits. Revisit PURL canonicalization if a reviewed library or specification test suite becomes available. Any provider-side inventory query needs a separate egress and privacy decision.

## Learning and interview prompts

- Why does the implementation remove PURL version before package identity comparison?
- Why is exact identity correlation not the same as vulnerability applicability?
- What false negatives arise from preserving PURL qualifiers exactly?
- How would an ecosystem-aware semver or PEP 440 comparator change the threat model and test matrix?
