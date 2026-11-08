import type { OsvAffectedPackage, SbomComponent } from "./types";

export const OSV_PURL_MATCH_LIMITATION = "Exact PURL identity only after removing the version segment. AURA does not evaluate advisory version ranges or decide whether an installed version is affected.";
export const OSV_PURL_MATCH_BASIS = "EXACT_PURL_IDENTITY_AFTER_REMOVING_VERSION";
export const OSV_VERSION_ASSESSMENT = "NOT_PERFORMED";

export interface OsvPackageIdentityMatch {
  advisoryPackage: OsvAffectedPackage;
  components: SbomComponent[];
}

const MAX_PURL_LENGTH = 4_096;

/**
 * Correlates only exact PURL identities after removing the version segment.
 * It deliberately does not evaluate whether a component version is affected.
 */
export function correlateOsvPackages(
  advisoryPackages: OsvAffectedPackage[],
  components: SbomComponent[],
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
    return matches?.length ? [{ advisoryPackage, components: [...matches] }] : [];
  });
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
