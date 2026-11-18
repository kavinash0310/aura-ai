import { compare, valid } from "semver";
import { clean as cleanPep440, compare as comparePep440 } from "@renovatebot/pep440";
import type { OsvAffectedPackage, OsvVersionEvent, OsvVersionRange, SbomComponent } from "./types";

export const OSV_PURL_CORRELATION_LIMITATION = "AURA matches exact OSV affected-version strings for any ecosystem, compares PyPI versions and ECOSYSTEM ranges using PEP 440, compares RubyGems versions and ECOSYSTEM ranges using RubyGems ordering, compares Hex versions and ECOSYSTEM ranges using Hex/Elixir SemVer ordering, compares Pub versions and ECOSYSTEM ranges using pub_semver ordering, and evaluates complete SemVer 2 ranges for npm, crates.io, a restricted NuGet subset, and Go modules. RubyGems versions use a bounded strict ASCII version subset; Hex core and numeric prerelease segments are limited to 14 digits; Pub uses a bounded strict version subset with a 120-character cap and 15-digit numeric-segment limit. Pub build suffixes affect precedence. PyPI package names use PEP 503 normalization. Cargo versions must be strict three-part SemVer; build metadata does not affect range ordering. NuGet package IDs match without case. Ordered NuGet comparisons accept only strict three-part versions with lowercase prerelease labels; other forms stay not assessed unless their exact version strings match. Go versions must use the v prefix; only +incompatible build metadata is accepted. Go module paths and pseudo-version ancestry are not validated. Other ecosystems or range types stay not assessed; a no-match is limited to the returned advisory data and is not proof of safety.";
export const OSV_PURL_MATCH_BASIS = "EXACT_PURL_IDENTITY_AFTER_REMOVING_VERSION";
export const OSV_VERSION_ASSESSMENT_POLICY = "EXACT_OSV_VERSIONS_ANY_ECOSYSTEM_PYPI_PEP440_RUBYGEMS_HEX_PUB_NPM_CRATES_IO_NUGET_GO_SEMVER_2_RANGES";

export type OsvVersionAssessmentStatus = "AFFECTED" | "NO_MATCH_IN_RETURNED_OSV_VERSION_DATA" | "NOT_ASSESSED";

export interface OsvComponentVersionAssessment {
  bomRef: string;
  version: string | null;
  status: OsvVersionAssessmentStatus;
  note: string;
  evidence: string | null;
}

export interface OsvPackageIdentityMatch {
  advisoryPackage: OsvAffectedPackage;
  components: SbomComponent[];
  versionAssessments: OsvComponentVersionAssessment[];
}

const MAX_PURL_LENGTH = 4_096;

/**
 * Correlates normalized PURL identities locally. Literal exact-version evidence is
 * ecosystem-independent; PyPI, RubyGems, Hex, and Pub use their ecosystem
 * ordering, and range evaluation is limited to their ECOSYSTEM ranges, strict
 * npm/Cargo SemVer 2, a restricted NuGet SemVer 2 subset, and canonical Go
 * module SemVer 2.
 */
export function correlateOsvPackages(
  advisoryPackages: OsvAffectedPackage[],
  components: SbomComponent[],
  advisoryWithdrawn = false,
): OsvPackageIdentityMatch[] {
  const componentsByIdentity = new Map<string, SbomComponent[]>();
  for (const component of components) {
    const identity = purlIdentity(component.purl);
    if (!identity) continue;
    const matches = componentsByIdentity.get(identity) ?? [];
    if (!matches.some((item) => item.bomRef === component.bomRef)) matches.push(component);
    componentsByIdentity.set(identity, matches);
  }

  return advisoryPackages.flatMap((advisoryPackage) => {
    const identity = purlIdentity(advisoryPackage.purl);
    const matches = identity ? componentsByIdentity.get(identity) : undefined;
    if (!matches?.length) return [];
    return [{
      advisoryPackage,
      components: [...matches],
      versionAssessments: matches.map((component) => assessOsvPackageVersion(advisoryPackage, component, advisoryWithdrawn)),
    }];
  });
}

export function assessOsvPackageVersion(
  advisoryPackage: OsvAffectedPackage,
  component: SbomComponent,
  advisoryWithdrawn = false,
): OsvComponentVersionAssessment {
  const base = { bomRef: component.bomRef, version: component.version || null };
  const notAssessed = (note: string): OsvComponentVersionAssessment => ({
    ...base,
    status: "NOT_ASSESSED",
    note,
    evidence: null,
  });

  if (advisoryWithdrawn) return notAssessed("The OSV advisory is withdrawn; AURA does not assess affected versions from it.");
  if (!component.version) return notAssessed("The SBOM component has no version to compare.");

  if (advisoryPackage.affectedVersions.includes(component.version)) {
    return {
      ...base,
      status: "AFFECTED",
      note: "The inventory version exactly matches an OSV affected-version entry.",
      evidence: `OSV affected version ${component.version}`,
    };
  }

  if (advisoryPackage.ecosystem === "PyPI") {
    const inventoryVersion = strictPep440(component.version);
    const matchingVersion = inventoryVersion && advisoryPackage.affectedVersions.find((version) => {
      const affectedVersion = strictPep440(version);
      return affectedVersion !== null && comparePep440(inventoryVersion, affectedVersion) === 0;
    });
    if (matchingVersion) {
      return {
        ...base,
        status: "AFFECTED",
        note: "The inventory version matches a listed OSV PyPI version under PEP 440 ordering.",
        evidence: `OSV affected version ${matchingVersion}`,
      };
    }
  }

  if (advisoryPackage.ecosystem === "NuGet") {
    const normalizedInventoryVersion = strictNugetSemver(component.version);
    const matchingNugetVersion = normalizedInventoryVersion && advisoryPackage.affectedVersions.some((version) => {
      const normalizedAffectedVersion = strictNugetSemver(version);
      return normalizedAffectedVersion !== null && compare(normalizedInventoryVersion, normalizedAffectedVersion) === 0;
    });
    if (matchingNugetVersion) {
      return {
        ...base,
        status: "AFFECTED",
        note: "The inventory version matches a supported NuGet OSV version under NuGet SemVer precedence.",
        evidence: `OSV affected version ${component.version}`,
      };
    }
  }

  if (advisoryPackage.ecosystem === "RubyGems") {
    const inventoryVersion = strictRubyGemsVersion(component.version);
    const matchingRubyGemsVersion = inventoryVersion && advisoryPackage.affectedVersions.find((version) => {
      const affectedVersion = strictRubyGemsVersion(version);
      return affectedVersion !== null && compareRubyGemsVersions(inventoryVersion, affectedVersion) === 0;
    });
    if (matchingRubyGemsVersion) {
      return {
        ...base,
        status: "AFFECTED",
        note: "The inventory version matches a listed OSV RubyGems version under RubyGems ordering.",
        evidence: `OSV affected version ${matchingRubyGemsVersion}`,
      };
    }
  }

  if (advisoryPackage.ecosystem === "Hex") {
    const inventoryVersion = strictHexVersion(component.version);
    const matchingHexVersion = inventoryVersion && advisoryPackage.affectedVersions.find((version) => {
      const affectedVersion = strictHexVersion(version);
      return affectedVersion !== null && compare(inventoryVersion, affectedVersion) === 0;
    });
    if (matchingHexVersion) {
      return {
        ...base,
        status: "AFFECTED",
        note: "The inventory version matches a listed OSV Hex version under Hex/Elixir SemVer ordering.",
        evidence: `OSV affected version ${matchingHexVersion}`,
      };
    }
  }

  if (advisoryPackage.ecosystem === "Pub") {
    const inventoryVersion = strictPubVersion(component.version);
    const matchingPubVersion = inventoryVersion && advisoryPackage.affectedVersions.find((version) => {
      const affectedVersion = strictPubVersion(version);
      return affectedVersion !== null && comparePubVersions(inventoryVersion, affectedVersion) === 0;
    });
    if (matchingPubVersion) {
      return {
        ...base,
        status: "AFFECTED",
        note: "The inventory version matches a listed OSV Pub version under pub_semver ordering.",
        evidence: `OSV affected version ${matchingPubVersion}`,
      };
    }
  }

  if (advisoryPackage.versionRangesTruncated) {
    return notAssessed("OSV range evidence was truncated or malformed; AURA did not assess a partial range.");
  }

  const supportsSemverRanges = ["npm", "crates.io", "NuGet", "Go"].includes(advisoryPackage.ecosystem);
  const supportsPep440Ranges = advisoryPackage.ecosystem === "PyPI";
  const supportsRubyGemsRanges = advisoryPackage.ecosystem === "RubyGems";
  const supportsHexRanges = advisoryPackage.ecosystem === "Hex";
  const supportsPubRanges = advisoryPackage.ecosystem === "Pub";
  if (!supportsSemverRanges && !supportsPep440Ranges && !supportsRubyGemsRanges && !supportsHexRanges && !supportsPubRanges) {
    if (advisoryPackage.affectedVersionsTruncated) {
      return notAssessed("OSV affected-version evidence was truncated; a negative result cannot be determined safely.");
    }
    if (advisoryPackage.versionRanges.length > 0 || advisoryPackage.versionRangeDetails.length > 0) {
      return notAssessed("Range evaluation supports PyPI PEP 440, RubyGems, Hex, and Pub ECOSYSTEM ranges, npm and crates.io SemVer 2, a restricted NuGet SemVer 2 subset, and canonical Go module SemVer 2 only; this ecosystem's range was not compared.");
    }
    if (advisoryPackage.affectedVersions.length === 0) {
      return notAssessed("OSV did not return an exact affected-version list that AURA can assess.");
    }
    return {
      ...base,
      status: "NO_MATCH_IN_RETURNED_OSV_VERSION_DATA",
      note: "No exact match was found in the complete returned OSV affected-version list; this does not prove safety beyond that record.",
      evidence: null,
    };
  }

  const ecosystem = advisoryPackage.ecosystem;
  const inventoryVersion = supportsPep440Ranges
    ? strictPep440(component.version)
    : supportsRubyGemsRanges || supportsPubRanges ? null : strictSemverForEcosystem(component.version, ecosystem);
  const rubyGemsInventoryVersion = supportsRubyGemsRanges ? strictRubyGemsVersion(component.version) : null;
  const pubInventoryVersion = supportsPubRanges ? strictPubVersion(component.version) : null;
  if (!inventoryVersion && !rubyGemsInventoryVersion && !pubInventoryVersion) {
    return notAssessed(supportsPep440Ranges
      ? "The inventory version is not a supported PEP 440 version, so AURA did not compare it."
      : supportsRubyGemsRanges
        ? "The inventory version is not a supported RubyGems version, so AURA did not compare it."
      : supportsHexRanges
        ? "The inventory version is not a supported Hex/Elixir SemVer version, so AURA did not compare it."
      : supportsPubRanges
        ? "The inventory version is not a supported Pub version, so AURA did not compare it."
      : "The inventory version is not strict SemVer 2, so AURA did not compare it.");
  }
  if (advisoryPackage.versionRanges.length > 0 && advisoryPackage.versionRangeDetails.length === 0) {
    return notAssessed("OSV returned range descriptions without structured events; AURA did not infer range semantics.");
  }

  const affectedVersionsSupported = advisoryPackage.affectedVersions.every(
    (version) => (supportsPep440Ranges
      ? strictPep440(version)
      : supportsRubyGemsRanges ? strictRubyGemsVersion(version) : supportsPubRanges ? strictPubVersion(version) : strictSemverForEcosystem(version, ecosystem)) !== null,
  );
  const rangeResults = advisoryPackage.versionRangeDetails.map(
    (range) => supportsPep440Ranges
      ? evaluatePep440Range(inventoryVersion!, range)
      : supportsRubyGemsRanges
        ? evaluateRubyGemsRange(rubyGemsInventoryVersion!, range)
        : supportsPubRanges
          ? evaluatePubRange(pubInventoryVersion!, range)
        : evaluateSemverRange(inventoryVersion!, range, ecosystem, supportsHexRanges ? "ECOSYSTEM" : "SEMVER"),
  );
  const matchingRange = rangeResults.find((result) => result?.affected === true);
  if (matchingRange) {
    return {
      ...base,
      status: "AFFECTED",
      note: `The inventory version falls within a supported ${supportsPep440Ranges ? "PyPI PEP 440" : supportsRubyGemsRanges ? "RubyGems" : supportsHexRanges ? "Hex/Elixir SemVer" : supportsPubRanges ? "Pub" : `${ecosystem === "Go" ? "Go module " : ""}SemVer 2`} OSV range.`,
      evidence: matchingRange.description,
    };
  }

  const hasUnsupportedRanges = rangeResults.some((result) => result === null);
  if (advisoryPackage.affectedVersionsTruncated) {
    return notAssessed("OSV version evidence was truncated; a negative result cannot be determined safely.");
  }
  if (!affectedVersionsSupported || hasUnsupportedRanges) {
    return notAssessed("OSV includes version evidence outside the supported PyPI PEP 440, RubyGems, Hex/Elixir, Pub, npm, crates.io, restricted NuGet, and Go SemVer 2 subsets.");
  }
  const hasSupportedVersionEvidence = advisoryPackage.affectedVersions.length > 0 || rangeResults.length > 0;
  if (!hasSupportedVersionEvidence) return notAssessed("OSV did not return version evidence that AURA can assess.");

  return {
    ...base,
    status: "NO_MATCH_IN_RETURNED_OSV_VERSION_DATA",
    note: "No match was found in the complete returned OSV version data; this does not prove safety beyond that record.",
    evidence: null,
  };
}

function strictPep440(value: string): string | null {
  if (!value || value.length > 120) return null;
  const normalized = cleanPep440(value);
  if (!normalized) return null;

  // The library stores numeric PEP 440 fields as JavaScript numbers. Reject
  // values outside the exact integer range instead of comparing rounded values.
  const numericFields = value.match(/\d+/g) ?? [];
  if (numericFields.some((field) => !Number.isSafeInteger(Number(field)))) return null;
  return normalized;
}

type RubyGemsSegment = { kind: "numeric"; value: string } | { kind: "text"; value: string };

interface RubyGemsVersion {
  segments: RubyGemsSegment[];
}

function strictRubyGemsVersion(value: string): RubyGemsVersion | null {
  if (!value || value.length > 120 || value.trim() !== value || !/^\d+(?:\.[0-9A-Za-z]+)*(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/.test(value)) return null;

  let canonical = value.replace(/-/g, ".pre.");
  if (/[A-Za-z]/.test(canonical)) {
    canonical = canonical.replace(/(^|\.)([0.]+)(?=[A-Za-z])/, (_match, separator: string) => separator);
  }
  const parts = canonical.match(/\d+|[a-z]+/gi);
  if (!parts?.length) return null;
  return {
    segments: parts.map((part) => /^\d+$/.test(part)
      ? { kind: "numeric", value: part.replace(/^0+(?=\d)/, "") }
      : { kind: "text", value: part }),
  };
}

function compareRubyGemsVersions(left: RubyGemsVersion, right: RubyGemsVersion): number {
  const sharedLength = Math.min(left.segments.length, right.segments.length);
  for (let index = 0; index < sharedLength; index += 1) {
    const leftSegment = left.segments[index]!;
    const rightSegment = right.segments[index]!;
    if (leftSegment.kind !== rightSegment.kind) return leftSegment.kind === "text" ? -1 : 1;
    if (leftSegment.value === rightSegment.value) continue;
    if (leftSegment.kind === "numeric" && leftSegment.value.length !== rightSegment.value.length) {
      return leftSegment.value.length < rightSegment.value.length ? -1 : 1;
    }
    return leftSegment.value < rightSegment.value ? -1 : 1;
  }

  const longerSide = left.segments.length > sharedLength ? left.segments : right.segments;
  const firstSignificant = longerSide.slice(sharedLength).find((segment) => segment.kind === "text" || segment.value !== "0");
  if (!firstSignificant) return 0;
  const leftHasRemainder = left.segments.length > sharedLength;
  if (firstSignificant.kind === "text") return leftHasRemainder ? -1 : 1;
  return leftHasRemainder ? 1 : -1;
}

function strictHexVersion(value: string): string | null {
  if (!value || value.length > 120) return null;
  const match = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/.exec(value);
  if (!match) return null;
  const versionWithoutBuildMetadata = value.split("+", 1)[0]!;
  const normalized = strictSemver(versionWithoutBuildMetadata);
  if (!normalized) return null;
  const numericParts = [match[1]!, match[2]!, match[3]!, ...(match[4]?.split(".").filter((part) => /^\d+$/.test(part)) ?? [])];
  if (numericParts.some((part) => part.length > 14)) return null;
  return normalized;
}

type PubIdentifier = { kind: "numeric"; value: string } | { kind: "text"; value: string };

interface PubVersion {
  semver: string;
  build: PubIdentifier[];
}

function strictPubVersion(value: string): PubVersion | null {
  if (!value || value.length > 120) return null;
  const match = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/.exec(value);
  if (!match) return null;

  const semver = strictSemver(value.split("+", 1)[0]!);
  if (!semver) return null;
  const numericParts = [
    match[1]!,
    match[2]!,
    match[3]!,
    ...(match[4]?.split(".").filter((part) => /^\d+$/.test(part)) ?? []),
    ...(match[5]?.split(".").filter((part) => /^\d+$/.test(part)) ?? []),
  ];
  if (numericParts.some((part) => part.length > 15)) return null;

  return {
    semver,
    build: match[5]?.split(".").map(pubIdentifier) ?? [],
  };
}

function pubIdentifier(value: string): PubIdentifier {
  if (/^\d+$/.test(value)) return { kind: "numeric", value: value.replace(/^0+(?=\d)/, "") };
  return { kind: "text", value };
}

function comparePubVersions(left: PubVersion, right: PubVersion): number {
  const semverComparison = compare(left.semver, right.semver);
  if (semverComparison !== 0) return semverComparison;

  const sharedLength = Math.min(left.build.length, right.build.length);
  for (let index = 0; index < sharedLength; index += 1) {
    const leftPart = left.build[index]!;
    const rightPart = right.build[index]!;
    if (leftPart.kind !== rightPart.kind) return leftPart.kind === "numeric" ? -1 : 1;
    if (leftPart.value === rightPart.value) continue;
    if (leftPart.kind === "numeric" && leftPart.value.length !== rightPart.value.length) {
      return leftPart.value.length < rightPart.value.length ? -1 : 1;
    }
    return leftPart.value < rightPart.value ? -1 : 1;
  }
  if (left.build.length === right.build.length) return 0;
  return left.build.length < right.build.length ? -1 : 1;
}

function evaluatePep440Range(version: string, range: OsvVersionRange): SemverRangeEvaluation | null {
  if (range.type !== "ECOSYSTEM" || range.events.length === 0 || range.events.some((event) => !event.validShape)) return null;

  const events: Array<{ kind: "introduced" | "fixed" | "lastAffected" | "limit"; value: string; parsedValue: string | null }> = [];
  for (const event of range.events) {
    const kind = eventKind(event);
    const value = eventValue(event, kind);
    if (!kind || value === null) return null;
    if (kind === "introduced" && value === "0") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    if (kind === "limit" && value === "*") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    const parsedValue = strictPep440(value);
    if (!parsedValue) return null;
    events.push({ kind, value, parsedValue });
  }

  const introducedCount = events.filter((event) => event.kind === "introduced").length;
  const hasFixed = events.some((event) => event.kind === "fixed");
  const hasLastAffected = events.some((event) => event.kind === "lastAffected");
  if (introducedCount === 0 || (hasFixed && hasLastAffected)) return null;

  const boundaries = events
    .filter((event) => event.kind !== "limit")
    .sort(comparePep440BoundaryValues);
  if (boundaries.some((event, index) => index > 0 && comparePep440BoundaryValues(boundaries[index - 1]!, event) === 0)) return null;

  let expectsIntroduction = true;
  for (const event of boundaries) {
    if (event.kind === "introduced") {
      if (!expectsIntroduction) return null;
      expectsIntroduction = false;
    } else {
      if (expectsIntroduction) return null;
      expectsIntroduction = true;
    }
  }

  const limits = events.filter((event) => event.kind === "limit");
  if (limits.length > 0 && !limits.some((limit) => limit.value === "*" || comparePep440(version, limit.parsedValue!) < 0)) {
    return { affected: false, description: displayRange(range) };
  }

  let affected = false;
  for (const event of boundaries) {
    if (event.kind === "introduced" && (event.value === "0" || comparePep440(version, event.parsedValue!) >= 0)) {
      affected = true;
    } else if (event.kind === "fixed" && comparePep440(version, event.parsedValue!) >= 0) {
      affected = false;
    } else if (event.kind === "lastAffected" && comparePep440(version, event.parsedValue!) > 0) {
      affected = false;
    }
  }
  return { affected, description: displayRange(range) };
}

function comparePep440BoundaryValues(
  left: { kind: string; value: string; parsedValue: string | null },
  right: { kind: string; value: string; parsedValue: string | null },
): number {
  const leftIsNegativeInfinity = left.kind === "introduced" && left.value === "0";
  const rightIsNegativeInfinity = right.kind === "introduced" && right.value === "0";
  if (leftIsNegativeInfinity) return rightIsNegativeInfinity ? 0 : -1;
  if (rightIsNegativeInfinity) return 1;
  return comparePep440(left.parsedValue!, right.parsedValue!);
}

interface SemverRangeEvaluation {
  affected: boolean;
  description: string;
}

function evaluateSemverRange(
  version: string,
  range: OsvVersionRange,
  ecosystem: string,
  rangeType: "SEMVER" | "ECOSYSTEM" = "SEMVER",
): SemverRangeEvaluation | null {
  if (range.type !== rangeType || range.events.length === 0 || range.events.some((event) => !event.validShape)) return null;

  const events: Array<{ kind: "introduced" | "fixed" | "lastAffected" | "limit"; value: string; parsedValue: string | null }> = [];
  for (const event of range.events) {
    const kind = eventKind(event);
    const value = eventValue(event, kind);
    if (!kind || value === null) return null;
    if (kind === "introduced" && value === "0") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    if (kind === "limit" && value === "*") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    const parsedValue = strictSemverForEcosystem(value, ecosystem);
    if (!parsedValue) return null;
    events.push({ kind, value, parsedValue });
  }

  const introducedCount = events.filter((event) => event.kind === "introduced").length;
  const hasFixed = events.some((event) => event.kind === "fixed");
  const hasLastAffected = events.some((event) => event.kind === "lastAffected");
  if (introducedCount === 0 || (hasFixed && hasLastAffected)) return null;

  const boundaries = events
    .filter((event) => event.kind !== "limit")
    .sort(compareBoundaryValues);
  if (boundaries.some((event, index) => index > 0 && compareBoundaryValues(boundaries[index - 1]!, event) === 0)) return null;

  let expectsIntroduction = true;
  for (const event of boundaries) {
    if (event.kind === "introduced") {
      if (!expectsIntroduction) return null;
      expectsIntroduction = false;
    } else {
      if (expectsIntroduction) return null;
      expectsIntroduction = true;
    }
  }

  const limits = events.filter((event) => event.kind === "limit");
  if (limits.length > 0 && !limits.some((limit) => limit.value === "*" || compare(version, limit.parsedValue!) < 0)) {
    return { affected: false, description: displayRange(range) };
  }

  let affected = false;
  for (const event of boundaries) {
    if (event.kind === "introduced" && (event.value === "0" || compare(version, event.parsedValue!) >= 0)) {
      affected = true;
    } else if (event.kind === "fixed" && compare(version, event.parsedValue!) >= 0) {
      affected = false;
    } else if (event.kind === "lastAffected" && compare(version, event.parsedValue!) > 0) {
      affected = false;
    }
  }
  return { affected, description: displayRange(range) };
}

function evaluateRubyGemsRange(version: RubyGemsVersion, range: OsvVersionRange): SemverRangeEvaluation | null {
  if (range.type !== "ECOSYSTEM" || range.events.length === 0 || range.events.some((event) => !event.validShape)) return null;

  const events: Array<{ kind: "introduced" | "fixed" | "lastAffected" | "limit"; value: string; parsedValue: RubyGemsVersion | null }> = [];
  for (const event of range.events) {
    const kind = eventKind(event);
    const value = eventValue(event, kind);
    if (!kind || value === null) return null;
    if (kind === "introduced" && value === "0") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    if (kind === "limit" && value === "*") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    const parsedValue = strictRubyGemsVersion(value);
    if (!parsedValue) return null;
    events.push({ kind, value, parsedValue });
  }

  const introducedCount = events.filter((event) => event.kind === "introduced").length;
  const hasFixed = events.some((event) => event.kind === "fixed");
  const hasLastAffected = events.some((event) => event.kind === "lastAffected");
  if (introducedCount === 0 || (hasFixed && hasLastAffected)) return null;

  const boundaries = events
    .filter((event) => event.kind !== "limit")
    .sort(compareRubyGemsBoundaryValues);
  if (boundaries.some((event, index) => index > 0 && compareRubyGemsBoundaryValues(boundaries[index - 1]!, event) === 0)) return null;

  let expectsIntroduction = true;
  for (const event of boundaries) {
    if (event.kind === "introduced") {
      if (!expectsIntroduction) return null;
      expectsIntroduction = false;
    } else {
      if (expectsIntroduction) return null;
      expectsIntroduction = true;
    }
  }

  const limits = events.filter((event) => event.kind === "limit");
  if (limits.length > 0 && !limits.some((limit) => limit.value === "*" || compareRubyGemsVersions(version, limit.parsedValue!) < 0)) {
    return { affected: false, description: displayRange(range) };
  }

  let affected = false;
  for (const event of boundaries) {
    if (event.kind === "introduced" && (event.value === "0" || compareRubyGemsVersions(version, event.parsedValue!) >= 0)) {
      affected = true;
    } else if (event.kind === "fixed" && compareRubyGemsVersions(version, event.parsedValue!) >= 0) {
      affected = false;
    } else if (event.kind === "lastAffected" && compareRubyGemsVersions(version, event.parsedValue!) > 0) {
      affected = false;
    }
  }
  return { affected, description: displayRange(range) };
}

function compareRubyGemsBoundaryValues(
  left: { kind: string; value: string; parsedValue: RubyGemsVersion | null },
  right: { kind: string; value: string; parsedValue: RubyGemsVersion | null },
): number {
  const leftIsNegativeInfinity = left.kind === "introduced" && left.value === "0";
  const rightIsNegativeInfinity = right.kind === "introduced" && right.value === "0";
  if (leftIsNegativeInfinity) return rightIsNegativeInfinity ? 0 : -1;
  if (rightIsNegativeInfinity) return 1;
  return compareRubyGemsVersions(left.parsedValue!, right.parsedValue!);
}

function evaluatePubRange(version: PubVersion, range: OsvVersionRange): SemverRangeEvaluation | null {
  if (range.type !== "ECOSYSTEM" || range.events.length === 0 || range.events.some((event) => !event.validShape)) return null;

  const events: Array<{ kind: "introduced" | "fixed" | "lastAffected" | "limit"; value: string; parsedValue: PubVersion | null }> = [];
  for (const event of range.events) {
    const kind = eventKind(event);
    const value = eventValue(event, kind);
    if (!kind || value === null) return null;
    if (kind === "introduced" && value === "0") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    if (kind === "limit" && value === "*") {
      events.push({ kind, value, parsedValue: null });
      continue;
    }
    const parsedValue = strictPubVersion(value);
    if (!parsedValue) return null;
    events.push({ kind, value, parsedValue });
  }

  const introducedCount = events.filter((event) => event.kind === "introduced").length;
  const hasFixed = events.some((event) => event.kind === "fixed");
  const hasLastAffected = events.some((event) => event.kind === "lastAffected");
  if (introducedCount === 0 || (hasFixed && hasLastAffected)) return null;

  const boundaries = events
    .filter((event) => event.kind !== "limit")
    .sort(comparePubBoundaryValues);
  if (boundaries.some((event, index) => index > 0 && comparePubBoundaryValues(boundaries[index - 1]!, event) === 0)) return null;

  let expectsIntroduction = true;
  for (const event of boundaries) {
    if (event.kind === "introduced") {
      if (!expectsIntroduction) return null;
      expectsIntroduction = false;
    } else {
      if (expectsIntroduction) return null;
      expectsIntroduction = true;
    }
  }

  const limits = events.filter((event) => event.kind === "limit");
  if (limits.length > 0 && !limits.some((limit) => limit.value === "*" || comparePubVersions(version, limit.parsedValue!) < 0)) {
    return { affected: false, description: displayRange(range) };
  }

  let affected = false;
  for (const event of boundaries) {
    if (event.kind === "introduced" && (event.value === "0" || comparePubVersions(version, event.parsedValue!) >= 0)) {
      affected = true;
    } else if (event.kind === "fixed" && comparePubVersions(version, event.parsedValue!) >= 0) {
      affected = false;
    } else if (event.kind === "lastAffected" && comparePubVersions(version, event.parsedValue!) > 0) {
      affected = false;
    }
  }
  return { affected, description: displayRange(range) };
}

function comparePubBoundaryValues(
  left: { kind: string; value: string; parsedValue: PubVersion | null },
  right: { kind: string; value: string; parsedValue: PubVersion | null },
): number {
  const leftIsNegativeInfinity = left.kind === "introduced" && left.value === "0";
  const rightIsNegativeInfinity = right.kind === "introduced" && right.value === "0";
  if (leftIsNegativeInfinity) return rightIsNegativeInfinity ? 0 : -1;
  if (rightIsNegativeInfinity) return 1;
  return comparePubVersions(left.parsedValue!, right.parsedValue!);
}

function eventKind(event: OsvVersionEvent): "introduced" | "fixed" | "lastAffected" | "limit" | null {
  if (event.introduced !== null) return "introduced";
  if (event.fixed !== null) return "fixed";
  if (event.lastAffected !== null) return "lastAffected";
  if (event.limit !== null) return "limit";
  return null;
}

function eventValue(event: OsvVersionEvent, kind: "introduced" | "fixed" | "lastAffected" | "limit" | null): string | null {
  if (!kind) return null;
  if (kind === "introduced") return event.introduced;
  if (kind === "fixed") return event.fixed;
  if (kind === "lastAffected") return event.lastAffected;
  return event.limit;
}

function compareBoundaryValues(
  left: { kind: string; value: string; parsedValue: string | null },
  right: { kind: string; value: string; parsedValue: string | null },
): number {
  const leftIsNegativeInfinity = left.kind === "introduced" && left.value === "0";
  const rightIsNegativeInfinity = right.kind === "introduced" && right.value === "0";
  if (leftIsNegativeInfinity) return rightIsNegativeInfinity ? 0 : -1;
  if (rightIsNegativeInfinity) return 1;
  return compare(left.parsedValue!, right.parsedValue!);
}

function strictSemver(value: string): string | null {
  const parsed = valid(value);
  return parsed === value ? parsed : null;
}

function strictSemverForEcosystem(value: string, ecosystem: string): string | null {
  if (ecosystem === "npm") return strictSemver(value);
  if (ecosystem === "crates.io") return strictCargoSemver(value);
  if (ecosystem === "NuGet") return strictNugetSemver(value);
  if (ecosystem === "Hex") return strictHexVersion(value);
  if (ecosystem !== "Go" || !value.startsWith("v")) return null;

  let versionWithoutPrefix = value.slice(1);
  if (versionWithoutPrefix.includes("+")) {
    const [versionCore, metadata, ...extra] = versionWithoutPrefix.split("+");
    if (metadata !== "incompatible" || extra.length > 0) return null;
    versionWithoutPrefix = versionCore!;
  }
  const parsed = valid(versionWithoutPrefix);
  return parsed === versionWithoutPrefix ? parsed : null;
}

function strictCargoSemver(value: string): string | null {
  const match = /^(\d+\.\d+\.\d+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?)(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/.exec(value);
  return match ? strictSemver(match[1]!) : null;
}

function strictNugetSemver(value: string): string | null {
  const match = /^(\d+\.\d+\.\d+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?)(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/.exec(value);
  if (!match) return null;
  const semverWithoutBuildMetadata = match[1]!;
  const parsed = strictSemver(semverWithoutBuildMetadata);
  if (!parsed) return null;
  const prerelease = /^\d+\.\d+\.\d+-([^+]+)$/.exec(semverWithoutBuildMetadata)?.[1];
  if (prerelease && /[A-Z]/.test(prerelease)) return null;
  return parsed;
}

function displayRange(range: OsvVersionRange): string {
  const descriptions = range.events.flatMap((event) => {
    const kind = eventKind(event);
    const value = eventValue(event, kind);
    return kind && value !== null ? [`${kind.replace(/[A-Z]/g, (letter) => " " + letter.toLowerCase())} ${value}`] : [];
  });
  return [range.type, ...descriptions].join(" · ");
}

function purlIdentity(purl: string): string | null {
  const value = purl.trim();
  if (!value || value.length > MAX_PURL_LENGTH) return null;
  const match = /^pkg:([a-z][a-z0-9.+-]*)\/([^?#]+)(\?[^#]*)?(?:#.*)?$/i.exec(value);
  if (!match) return null;

  let packagePath = match[2];
  const lastSlash = packagePath.lastIndexOf("/");
  const versionSeparator = packagePath.lastIndexOf("@");
  if (versionSeparator > lastSlash) {
    packagePath = packagePath.slice(0, versionSeparator);
  }
  if (!packagePath || packagePath.endsWith("/")) return null;
  if (match[1].toLowerCase() === "nuget") {
    if (!/^[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*$/.test(packagePath)) return null;
    packagePath = packagePath.toLowerCase();
  }
  if (match[1].toLowerCase() === "pypi") {
    if (!/^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$/.test(packagePath)) return null;
    packagePath = packagePath.toLowerCase().replace(/[-_.]+/g, "-");
  }
  return "pkg:" + match[1].toLowerCase() + "/" + packagePath + (match[3] ?? "");
}
