import { describe, expect, it } from "vitest";
import { assessOsvPackageVersion, correlateOsvPackages } from "./sbomAdvisoryCorrelation";
import type { OsvAffectedPackage, OsvVersionEvent, OsvVersionRange, SbomComponent } from "./types";

const advisory = (purl: string, overrides: Partial<OsvAffectedPackage> = {}): OsvAffectedPackage => ({
  name: "demo",
  ecosystem: "npm",
  purl,
  affectedVersions: [],
  affectedVersionsTruncated: false,
  versionRanges: [],
  versionRangeDetails: [],
  versionRangesTruncated: false,
  ...overrides,
});

const component = (bomRef: string, purl: string, version: string): SbomComponent => ({
  bomRef,
  name: "demo",
  version,
  purl,
});

const event = (kind: "introduced" | "fixed" | "lastAffected" | "limit", value: string): OsvVersionEvent => ({
  introduced: kind === "introduced" ? value : null,
  fixed: kind === "fixed" ? value : null,
  lastAffected: kind === "lastAffected" ? value : null,
  limit: kind === "limit" ? value : null,
  validShape: true,
});

const semverRange = (...events: OsvVersionEvent[]): OsvVersionRange => ({ type: "SEMVER", events });

describe("correlateOsvPackages", () => {
  it("matches exact PURL identity and reports a local npm SemVer range match", () => {
    const packageInfo = advisory("pkg:npm/demo", {
      versionRangeDetails: [semverRange(event("introduced", "1.0.0"), event("fixed", "2.0.0"))],
    });
    const result = correlateOsvPackages(
      [packageInfo],
      [component("pkg:npm/demo@1.2.0", "pkg:npm/demo@1.2.0", "1.2.0")],
    );

    expect(result).toHaveLength(1);
    expect(result[0]?.components.map((item) => item.bomRef)).toEqual(["pkg:npm/demo@1.2.0"]);
    expect(result[0]?.versionAssessments[0]).toMatchObject({
      status: "AFFECTED",
      evidence: "SEMVER · introduced 1.0.0 · fixed 2.0.0",
    });
  });

  it("respects fixed boundaries and SemVer prerelease ordering", () => {
    const packageInfo = advisory("pkg:npm/demo", {
      versionRangeDetails: [semverRange(event("introduced", "1.0.0"), event("fixed", "2.0.0"))],
    });

    expect(assessOsvPackageVersion(packageInfo, component("pre", "pkg:npm/demo@2.0.0-beta.1", "2.0.0-beta.1")).status).toBe("AFFECTED");
    expect(assessOsvPackageVersion(packageInfo, component("fixed", "pkg:npm/demo@2.0.0", "2.0.0")).status)
      .toBe("NO_MATCH_IN_RETURNED_OSV_VERSION_DATA");
  });

  it("supports introduced zero, last affected, and disjoint vulnerable ranges", () => {
    const packageInfo = advisory("pkg:npm/demo", {
      versionRangeDetails: [
        semverRange(event("introduced", "0"), event("fixed", "1.0.0")),
        semverRange(event("introduced", "2.0.0"), event("lastAffected", "2.5.0")),
      ],
    });

    expect(assessOsvPackageVersion(packageInfo, component("before", "pkg:npm/demo@0.9.0", "0.9.0")).status).toBe("AFFECTED");
    expect(assessOsvPackageVersion(packageInfo, component("gap", "pkg:npm/demo@1.5.0", "1.5.0")).status)
      .toBe("NO_MATCH_IN_RETURNED_OSV_VERSION_DATA");
    expect(assessOsvPackageVersion(packageInfo, component("last", "pkg:npm/demo@2.5.0", "2.5.0")).status).toBe("AFFECTED");
    expect(assessOsvPackageVersion(packageInfo, component("after", "pkg:npm/demo@2.5.1", "2.5.1")).status)
      .toBe("NO_MATCH_IN_RETURNED_OSV_VERSION_DATA");
  });

  it("treats OSV limit events as exclusive upper bounds", () => {
    const packageInfo = advisory("pkg:npm/demo", {
      versionRangeDetails: [semverRange(event("introduced", "1.0.0"), event("limit", "2.0.0"))],
    });

    expect(assessOsvPackageVersion(packageInfo, component("inside", "pkg:npm/demo@1.9.9", "1.9.9")).status).toBe("AFFECTED");
    expect(assessOsvPackageVersion(packageInfo, component("limit", "pkg:npm/demo@2.0.0", "2.0.0")).status)
      .toBe("NO_MATCH_IN_RETURNED_OSV_VERSION_DATA");
  });

  it("matches explicitly enumerated npm affected versions", () => {
    const packageInfo = advisory("pkg:npm/demo", { affectedVersions: ["1.2.3"] });
    expect(assessOsvPackageVersion(packageInfo, component("listed", "pkg:npm/demo@1.2.3", "1.2.3")).status).toBe("AFFECTED");
    expect(assessOsvPackageVersion(packageInfo, component("other", "pkg:npm/demo@1.2.4", "1.2.4")).status)
      .toBe("NO_MATCH_IN_RETURNED_OSV_VERSION_DATA");
  });

  it("leaves unsupported ecosystems, range types, malformed evidence, and truncated data not assessed", () => {
    const inventory = component("item", "pkg:npm/demo@1.2.0", "1.2.0");
    const ecosystemRange = { ...semverRange(event("introduced", "1.0.0"), event("fixed", "2.0.0")), type: "ECOSYSTEM" };

    expect(assessOsvPackageVersion(advisory("pkg:npm/demo", { ecosystem: "PyPI" }), inventory).status).toBe("NOT_ASSESSED");
    expect(assessOsvPackageVersion(advisory("pkg:npm/demo", { versionRangeDetails: [ecosystemRange] }), inventory).status).toBe("NOT_ASSESSED");
    expect(assessOsvPackageVersion(advisory("pkg:npm/demo", {
      versionRangeDetails: [semverRange({ ...event("introduced", "1.0.0"), validShape: false })],
    }), inventory).status).toBe("NOT_ASSESSED");
    expect(assessOsvPackageVersion(advisory("pkg:npm/demo", {
      versionRangeDetails: [semverRange(event("introduced", "1.0.0"), event("fixed", "2.0.0"))],
      versionRangesTruncated: true,
    }), component("inside", "pkg:npm/demo@1.5.0", "1.5.0")).status).toBe("NOT_ASSESSED");
    expect(assessOsvPackageVersion(advisory("pkg:npm/demo", {
      affectedVersions: ["1.5.0"],
      affectedVersionsTruncated: true,
      versionRangesTruncated: true,
    }), component("exact", "pkg:npm/demo@1.5.0", "1.5.0")).status).toBe("AFFECTED");
    expect(assessOsvPackageVersion(advisory("pkg:npm/demo", {
      versionRangeDetails: [semverRange(event("introduced", "1.0.0"), event("fixed", "2.0.0"))],
      versionRangesTruncated: true,
    }), component("outside", "pkg:npm/demo@2.5.0", "2.5.0")).status).toBe("NOT_ASSESSED");
    expect(assessOsvPackageVersion(advisory("pkg:npm/demo"), component("bad", "pkg:npm/demo@v1.2.0", "v1.2.0")).status)
      .toBe("NOT_ASSESSED");
  });

  it("does not infer package identity from a name or ignore namespaces", () => {
    const result = correlateOsvPackages(
      [advisory("pkg:npm/%40example/demo")],
      [component("other", "pkg:npm/demo@1.2.0", "1.2.0"), component("maven", "pkg:maven/org.example/demo@1.2.0", "1.2.0")],
    );
    expect(result).toEqual([]);
  });

  it("does not infer identity from missing or malformed PURLs and ignores oversized values", () => {
    expect(correlateOsvPackages(
      [advisory(""), advisory("pkg:npm/demo")],
      [component("missing", "", "1.2.0"), component("invalid", "npm:demo@1.2.0", "1.2.0")],
    )).toEqual([]);
    expect(correlateOsvPackages(
      [advisory("pkg:npm/" + "x".repeat(5_000))],
      [component("large", "pkg:npm/" + "x".repeat(5_000), "1.0.0")],
    )).toEqual([]);
  });

  it("does not assess a withdrawn OSV advisory", () => {
    const packageInfo = advisory("pkg:npm/demo", {
      versionRangeDetails: [semverRange(event("introduced", "1.0.0"))],
    });
    expect(assessOsvPackageVersion(packageInfo, component("item", "pkg:npm/demo@1.2.0", "1.2.0"), true).status)
      .toBe("NOT_ASSESSED");
  });
});
