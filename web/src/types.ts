export type ResearchMode = "GENERAL" | "CYBERSECURITY";
export type SourceKind = "WEB" | "NEWS" | "DISCUSSION";

export interface Credentials {
  username: string;
  password: string;
}

export interface ResearchJob {
  id: string;
  subject: string;
  mode: ResearchMode;
  status: "REQUESTED" | "SEARCHING" | "COMPLETED" | "FAILED";
  createdAt: string;
  searchProvider: string | null;
  searchResultCount: number;
  failureCode?: string;
}

export interface ResearchSource {
  id: string;
  provider: string;
  kind: SourceKind;
  rank: number;
  title: string;
  url: string;
  domain: string;
  description: string | null;
  publisher: string | null;
  publishedAt: string | null;
  retrievedAt: string;
}

export interface SearchResponse {
  jobId: string;
  query: string;
  status: ResearchJob["status"];
  provider: string | null;
  country: string | null;
  language: string | null;
  freshness: string | null;
  requestedLimit: number | null;
  providerResultCount: number;
  searchedAt: string | null;
  results: ResearchSource[];
  coverageNote: string;
}

export interface ResultFilters {
  kind: "" | SourceKind;
  q: string;
  host: string;
  publishedFrom: string;
  publishedTo: string;
}

export interface VulnerabilitySourceStatus {
  status: "AVAILABLE" | "STALE" | "UNAVAILABLE";
  retrievedAt: string | null;
  dataAsOf: string | null;
  sourceUrl: string;
  note: string;
}

export interface VulnerabilityFinding {
  cve: string;
  priorityRank: number;
  knownExploited: boolean | null;
  vendorProject: string | null;
  product: string | null;
  vulnerabilityName: string | null;
  dateAdded: string | null;
  dueDate: string | null;
  requiredAction: string | null;
  knownRansomwareCampaignUse: string | null;
  epssScore: number | null;
  epssPercentile: number | null;
  epssScoreDate: string | null;
  priorityRationale: string;
  cisaUrl: string;
  nvdUrl: string;
  epssUrl: string;
}

export interface VulnerabilityLookupResponse {
  requestedAt: string;
  coverageNote: string;
  kev: VulnerabilitySourceStatus;
  epss: VulnerabilitySourceStatus;
  results: VulnerabilityFinding[];
}
