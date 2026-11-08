import { describe, expect, it } from "vitest";
import { findResearchPageTextMatches, MAX_PAGE_SEARCH_LENGTH, MAX_PAGE_SEARCH_MATCHES } from "./researchPageSearch";

describe("research page exact-text search", () => {
  it("returns case-sensitive literal matches with stable character offsets and context", () => {
    const result = findResearchPageTextMatches("AURA research, AURA tools, aura lowercase", "AURA");

    expect(result.matches).toHaveLength(2);
    expect(result.matches[0]).toMatchObject({ start: 0, end: 4, text: "AURA", after: " research, AURA tools, aura lowercase" });
    expect(result.matches[1]).toMatchObject({ start: 15, end: 19, text: "AURA", before: "AURA research, " });
    expect(result.hasMore).toBe(false);
  });

  it("returns no results for an empty phrase or a phrase beyond the input limit", () => {
    expect(findResearchPageTextMatches("Some page text", "")).toEqual({ matches: [], hasMore: false });
    expect(findResearchPageTextMatches("A".repeat(500), "A".repeat(MAX_PAGE_SEARCH_LENGTH + 1)))
      .toEqual({ matches: [], hasMore: false });
  });

  it("caps excerpts at 200 and reports when more matches exist", () => {
    const result = findResearchPageTextMatches("x ".repeat(MAX_PAGE_SEARCH_MATCHES + 1), "x");

    expect(result.matches).toHaveLength(MAX_PAGE_SEARCH_MATCHES);
    expect(result.hasMore).toBe(true);
  });
});
