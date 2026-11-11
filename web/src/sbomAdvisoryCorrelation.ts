import { compare, valid } from "semver";
import type { OsvAffectedPackage, OsvVersionEvent, OsvVersionRange, SbomComponent } from "./types";

export const OSV_PURL_CORRELATION_LIMITATION = "AURA matches exact OSV affected-version strings for any ecosystem and evaluates complete SemVer 2 ranges for npm and Go modules only. Go versions must use the v prefix; only +incompatible build metadata is accepted. Go module paths and pseudo-version ancestry are not validated. Other ecosystems or range types stay not assessed; a no-match is limited to the returned advisory data and is not proof of safety.";
export const OSV_PURL_MATCH_BASIS = "EXACT_PURL_IDENTITY_AFTER_REMOVING_VERSION";
export const OSV_VERSION_ASSESSMENT_POLICY = "EXACT_OSV_VERSIONS_ANY_ECOSYSTEM_NPM_GO_SEMVER_2_RANGES";

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
 * Correlates exact PURL identities locally. Literal exact-version evidence is
 * ecosystem-independent; range evaluation is limited to strict npm SemVer 2
 * and canonical Go module SemVer 2.
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

  if (advisoryPackage.versionRangesTruncated) {
    return notAssessed("OSV range evidence was truncated or malformed; AURA did not assess a partial range.");
  }

  const supportsSemverRanges = advisoryPackage.ecosystem === "npm" || advisoryPackage.ecosystem === "Go";
  if (!supportsSemverRanges) {
    if (advisoryPackage.affectedVersionsTruncated) {
      return notAssessed("OSV affected-version evidence was truncated; a negative result cannot be determined safely.");
    }
    if (advisoryPackage.versionRanges.length > 0 || advisoryPackage.versionRangeDetails.length > 0) {
      return notAssessed("Range evaluation supports npm and canonical Go module SemVer 2 only; this ecosystem's range was not compared.");
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
  const inventoryVersion = strictSemverForEcosystem(component.version, ecosystem);
  if (!inventoryVersion) return notAssessed("The inventory version is not strict SemVer 2, so AURA did not compare it.");
  if (advisoryPackage.versionRanges.length > 0 && advisoryPackage.versionRangeDetails.length === 0) {
    return notAssessed("OSV returned range descriptions without structured events; AURA did not infer range semantics.");
  }

  const affectedVersionsSupported = advisoryPackage.affectedVersions.every(
    (version) => strictSemverForEcosystem(version, ecosystem) !== null,
  );
  const rangeResults = advisoryPackage.versionRangeDetails.map(
    (range) => evaluateSemverRange(inventoryVersion, range, ecosystem),
  );
  const matchingRange = rangeResults.find((result) => result?.affected === true);
  if (matchingRange) {
    return {
      ...base,
      status: "AFFECTED",
      note: `The inventory version falls within a supported ${ecosystem === "Go" ? "Go module " : ""}OSV SemVer 2 range.`,
      evidence: matchingRange.description,
    };
  }

  const hasUnsupportedRanges = rangeResults.some((result) => result === null);
  if (advisoryPackage.affectedVersionsTruncated) {
    return notAssessed("OSV version evidence was truncated; a negative result cannot be determined safely.");
  }
  if (!affectedVersionsSupported || hasUnsupportedRanges) {
    return notAssessed("OSV includes version evidence outside the supported npm or Go SemVer 2 subset.");
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

interface SemverRangeEvaluation {
  affected: boolean;
  description: string;
}

function evaluateSemverRange(version: string, range: OsvVersionRange, ecosystem: string): SemverRangeEvaluation | null {
  if (range.type !== "SEMVER" || range.events.length === 0 || range.events.some((event) => !event.validShape)) return null;

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
  if (ecosystem !== "Go" || !value.startsWith("v")) return null;
  if (value.includes("+") && !value.endsWith("+incompatible")) return null;

  const versionWithoutPrefix = value.slice(1);
  const parsed = valid(versionWithoutPrefix);
  return parsed === versionWithoutPrefix ? parsed : null;
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
  return "pkg:" + match[1].toLowerCase() + "/" + packagePath + (match[3] ?? "");
}
