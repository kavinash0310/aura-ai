export const MAX_PAGE_SEARCH_LENGTH = 200;
export const MAX_PAGE_SEARCH_MATCHES = 200;
const CONTEXT_CHARACTERS = 120;

export interface ResearchPageTextMatch {
  start: number;
  end: number;
  before: string;
  text: string;
  after: string;
}

export interface ResearchPageTextSearchResult {
  matches: ResearchPageTextMatch[];
  hasMore: boolean;
}

export function findResearchPageTextMatches(text: string, phrase: string): ResearchPageTextSearchResult {
  if (!phrase || phrase.length > MAX_PAGE_SEARCH_LENGTH) return { matches: [], hasMore: false };

  const matches: ResearchPageTextMatch[] = [];
  let cursor = 0;
  while (matches.length < MAX_PAGE_SEARCH_MATCHES) {
    const start = text.indexOf(phrase, cursor);
    if (start < 0) break;
    const end = start + phrase.length;
    const excerptStart = Math.max(0, start - CONTEXT_CHARACTERS);
    const excerptEnd = Math.min(text.length, end + CONTEXT_CHARACTERS);
    matches.push({
      start,
      end,
      before: text.slice(excerptStart, start),
      text: text.slice(start, end),
      after: text.slice(end, excerptEnd),
    });
    cursor = end;
  }

  return {
    matches,
    hasMore: matches.length === MAX_PAGE_SEARCH_MATCHES && text.indexOf(phrase, cursor) >= 0,
  };
}
