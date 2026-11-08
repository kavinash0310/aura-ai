import { describe, expect, it } from "vitest";
import { correlateOsvPackages } from "./sbomAdvisoryCorrelation";
import type { OsvAffectedPackage, SbomComponent } from "./types";

const advisory = (purl: string): OsvAffectedPackage => ({
  name: "demo",
  ecosystem: "npm",
  purl,
  versionRanges: ["SEMVER · introduced 1.0.0 · fixed 2.0.0"],
});

const component = (bomRef: string, purl: string, version: string): SbomComponent => ({
  bomRef,
  name: "demo",
  version,
  purl,
});

describe("correlateOsvPackages", () => {
  it("matches the same package identity while ignoring only the version segment", () => {
    const result = correlateOsvPackages(
      [advisory("pkg:npm/demo")],
      [component("pkg:npm/demo@1.2.0", "pkg:npm/demo@1.2.0", "1.2.0")],
    );

    expect(result).toHaveLength(1);
    expect(result[0]?.components.map((item) => item.bomRef)).toEqual(["pkg:npm/demo@1.2.0"]);
  });

  it("keeps namespace and ecosystem identity exact", () => {
    const result = correlateOsvPackages(
      [advisory("pkg:npm/%40example/demo")],
      [component("other", "pkg:npm/demo@1.2.0", "1.2.0"), component("maven", "pkg:maven/org.example/demo@1.2.0", "1.2.0")],
    );

    expect(result).toEqual([]);
  });

  it("does not infer an identity when either PURL is missing or malformed", () => {
    const result = correlateOsvPackages(
      [advisory(""), advisory("pkg:npm/demo")],
      [component("missing", "", "1.2.0"), component("invalid", "npm:demo@1.2.0", "1.2.0")],
    );

    expect(result).toEqual([]);
  });

  it("ignores PURLs above the local comparison bound", () => {
    const result = correlateOsvPackages(
      [advisory("pkg:npm/" + "x".repeat(5_000))],
      [component("large", "pkg:npm/" + "x".repeat(5_000), "1.0.0")],
    );

    expect(result).toEqual([]);
  });

  it("does not decide whether the package version falls inside advisory ranges", () => {
    const result = correlateOsvPackages(
      [advisory("pkg:npm/demo")],
      [component("patched", "pkg:npm/demo@2.5.0", "2.5.0")],
    );

    expect(result[0]?.components[0]?.version).toBe("2.5.0");
    expect(result[0]?.advisoryPackage.versionRanges).toContain("SEMVER · introduced 1.0.0 · fixed 2.0.0");
  });
});
