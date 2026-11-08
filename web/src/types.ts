export type ResearchMode = "GENERAL" | "CYBERSECURITY";
export type SourceKind = "WEB" | "NEWS" | "DISCUSSION";
export type SearchProviderChoice = "" | "brave" | "gdelt" | "bluesky" | "all";

export interface Credentials {
  username: string;
  password: string;
}

export interface ResearchJob {
  id: string;
  subject: string;
  mode: ResearchMode;
  status: "REQUESTED" | "SEARCHING" | "COMPLETED" | "PARTIAL" | "FAILED";
  createdAt: string;
  searchProvider: string | null;
  searchResultCount: number;
  failureCode?: string;
  providerRuns?: ProviderRun[];
}

export interface ProviderRun {
  provider: string;
  queryOrder: number;
  query: string;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
  resultCount: number;
  failureCode: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface ResearchSource {
  id: string;
  provider: string;
  searchQuery: string;
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

export interface ResearchPageResponse {
  readId: string;
  sourceId: string;
  sourceUrl: string;
  host: string;
  title: string;
  contentHash: string;
  characterCount: number;
  truncated: boolean;
  fetchedAt: string;
  text: string;
  coverageNote: string;
}

export interface ResearchPageReadAudit {
  id: string;
  sourceId: string;
  status: "RUNNING" | "SUCCEEDED" | "FAILED" | "ABANDONED";
  requestedAt: string;
  completedAt: string | null;
  contentHash: string | null;
  characterCount: number | null;
  truncated: boolean | null;
  failureCode: string | null;
}

export interface ResearchDocument {
  id: string;
  title: string;
  originalFilename: string;
  mediaType: string;
  contentHash: string;
  characterCount: number;
  uploadedAt: string;
  content: string | null;
}

export interface ResearchDocumentSearchHit {
  documentId: string;
  title: string;
  originalFilename: string;
  contentHash: string;
  uploadedAt: string;
  snippet: string;
  page: number | null;
  startOffset: number;
  endOffset: number;
  citation: string;
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
  providerRuns: ProviderRun[];
  coverageNote: string;
}

export interface ResearchReportFinding {
  text: string;
  sourceIds: string[];
}

export interface ResearchReportCitation {
  id: string;
  title: string;
  url: string;
  domain: string;
  kind: SourceKind;
}

export interface ModelRunTelemetry {
  promptVersion: string;
  durationMs: number | null;
  promptTokenCount: number | null;
  outputTokenCount: number | null;
}

export interface ResearchReportResponse {
  generatedAt: string;
  provider: "ollama";
  model: string;
  modelRun?: ModelRunTelemetry | null;
  title: string;
  summary: string;
  summarySourceIds: string[];
  findings: ResearchReportFinding[];
  limitations: string[];
  citations: ResearchReportCitation[];
  coverageNote: string;
}

export interface ClaimComparisonEvidence {
  stance: "SUPPORTS" | "CHALLENGES" | "CONTEXT" | "UNCLEAR";
  text: string;
  sourceIds: string[];
}

export interface ClaimComparisonCitation {
  id: string;
  title: string;
  url: string;
  domain: string;
  kind: SourceKind;
}

export interface ClaimComparisonResponse {
  generatedAt: string;
  provider: "ollama";
  model: string;
  modelRun?: ModelRunTelemetry | null;
  claim: string;
  assessment: "SUPPORTED" | "CHALLENGED" | "MIXED" | "INSUFFICIENT";
  explanation: string;
  evidence: ClaimComparisonEvidence[];
  limitations: string[];
  citations: ClaimComparisonCitation[];
  coverageNote: string;
}

export interface ResultFilters {
  kind: "" | SourceKind;
  provider: string;
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

export interface SbomComponent {
  bomRef: string;
  name: string;
  version: string;
  purl: string;
}

export interface SbomDeclaredVulnerability {
  cve: string;
  affectedBomRefs: string[];
  affectedComponents: SbomComponent[];
}

export interface SbomLookupResponse {
  requestedAt: string;
  format: string;
  specVersion: string;
  componentCount: number;
  cveCount: number;
  vulnerabilities: SbomDeclaredVulnerability[];
  intelligence: VulnerabilityLookupResponse | null;
  coverageNote: string;
}

export interface NvdCpeCriterion {
  criteria: string;
  vulnerable: boolean;
  versionStartIncluding: string | null;
  versionStartExcluding: string | null;
  versionEndIncluding: string | null;
  versionEndExcluding: string | null;
}

export interface NvdReference {
  url: string;
  tags: string[];
}

export interface NvdLookupResponse {
  requestedAt: string;
  cve: string;
  status: "AVAILABLE" | "NOT_FOUND" | "UNAVAILABLE" | "RATE_LIMITED";
  retrievedAt: string;
  sourceUrl: string;
  note: string;
  publishedAt: string | null;
  lastModifiedAt: string | null;
  vulnerabilityStatus: string | null;
  description: string | null;
  cvssVersion: string | null;
  cvssScore: number | null;
  cvssSeverity: string | null;
  cvssVector: string | null;
  cwes: string[];
  applicability: NvdCpeCriterion[];
  applicabilityTruncated: boolean;
  references: NvdReference[];
  referencesTruncated: boolean;
}

export interface OsvAffectedPackage {
  name: string;
  ecosystem: string;
  purl: string;
  affectedVersions: string[];
  affectedVersionsTruncated: boolean;
  versionRanges: string[];
  versionRangeDetails: OsvVersionRange[];
  versionRangesTruncated: boolean;
}

export interface OsvVersionRange {
  type: string;
  events: OsvVersionEvent[];
}

export interface OsvVersionEvent {
  introduced: string | null;
  fixed: string | null;
  lastAffected: string | null;
  limit: string | null;
  validShape: boolean;
}

export interface OsvReference {
  type: string;
  url: string;
}

export interface OsvLookupResponse {
  requestedAt: string;
  cve: string;
  status: "AVAILABLE" | "NOT_FOUND" | "UNAVAILABLE" | "RATE_LIMITED";
  retrievedAt: string;
  sourceUrl: string;
  osvId: string | null;
  summary: string | null;
  details: string | null;
  publishedAt: string | null;
  modifiedAt: string | null;
  withdrawnAt: string | null;
  aliases: string[];
  affectedPackages: OsvAffectedPackage[];
  affectedPackagesTruncated: boolean;
  references: OsvReference[];
  referencesTruncated: boolean;
  note: string;
}

export interface GitHubAdvisoryCvss {
  vector: string | null;
  score: number | null;
}

export interface GitHubAdvisoryPackage {
  ecosystem: string;
  name: string;
  vulnerableVersionRange: string;
  firstPatchedVersion: string;
}

export interface GitHubAdvisory {
  ghsaId: string;
  cveId: string;
  sourceUrl: string;
  type: string;
  severity: string;
  summary: string;
  description: string;
  publishedAt: string;
  updatedAt: string;
  reviewedAt: string;
  withdrawnAt: string;
  cvssV3: GitHubAdvisoryCvss | null;
  cvssV4: GitHubAdvisoryCvss | null;
  cwes: string[];
  cwesTruncated: boolean;
  affectedPackages: GitHubAdvisoryPackage[];
  affectedPackagesTruncated: boolean;
  references: Array<{ url: string }>;
  referencesTruncated: boolean;
}

export interface GitHubAdvisoryLookupResponse {
  requestedAt: string;
  cve: string;
  status: "AVAILABLE" | "NOT_FOUND" | "UNAVAILABLE" | "RATE_LIMITED";
  retrievedAt: string;
  advisories: GitHubAdvisory[];
  advisoriesTruncated: boolean;
  note: string;
}

export interface NvdBatchLookupResponse {
  requestedAt: string;
  results: NvdLookupResponse[];
}
