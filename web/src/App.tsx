import { useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import {
  ApiError,
  compareResearchClaim,
  createResearchJob,
  deleteDocument,
  deleteResearchJob,
  downloadExport,
  generateResearchReport,
  readResearchPage,
  listResearchPageReads,
  lookupSbom,
  lookupNvdBatch,
  lookupNvd,
  lookupVulnerabilities,
  getDocument,
  listDocuments,
  listResearchJobs,
  listSources,
  searchDocuments,
  runResearch,
  uploadDocument,
  validateConnection,
} from "./api";
import type {
  Credentials,
  ResearchJob,
  ResearchPageResponse,
  ResearchPageReadAudit,
  ResearchDocument,
  ResearchDocumentSearchHit,
  ResearchMode,
  SearchProviderChoice,
  ResearchSource,
  ResearchReportResponse,
  ClaimComparisonResponse,
  ResultFilters,
  NvdBatchLookupResponse,
  NvdLookupResponse,
  SbomLookupResponse,
  SearchResponse,
  VulnerabilityFinding,
  VulnerabilityLookupResponse,
} from "./types";
import { findResearchPageTextMatches, MAX_PAGE_SEARCH_LENGTH } from "./researchPageSearch";

const emptyFilters: ResultFilters = {
  kind: "",
  provider: "",
  q: "",
  host: "",
  publishedFrom: "",
  publishedTo: "",
};

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Those API credentials were not accepted. Check your local .env values.";
    if (error.code === "SEARCH_PROVIDER_NOT_CONFIGURED") {
      return "No selected search provider is enabled. Enable a public provider in .env or add your own Brave API key.";
    }
    if (error.code === "SEARCH_PROVIDER_RATE_LIMITED") return "The search provider rate limited this request. Wait a little and try again.";
    if (error.code === "AI_SERVICE_UNAVAILABLE") return "The local AI model is disabled or unavailable. Configure Ollama and an installed model in .env, then retry.";
    if (error.code === "VALIDATION_FAILED") return "Enter one to 25 CVE IDs, such as CVE-2024-12345.";
    if (error.code === "INVALID_SBOM" || error.code === "SBOM_TOO_LARGE" || error.code === "INVALID_DOCUMENT" || error.code === "UPLOAD_TOO_LARGE") return error.message;
    if (error.status === 0) return error.message;
    return error.message;
  }
  return "Something went wrong. Please try again.";
}

function formatDate(value: string | null): string {
  if (!value) return "Date unavailable";
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? "Date unavailable"
    : new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(date);
}

function kindLabel(kind: ResearchSource["kind"]): string {
  return kind === "DISCUSSION" ? "Discussion" : kind === "NEWS" ? "News" : "Web";
}

function providerLabel(provider: string): string {
  return provider === "brave" ? "Brave" : provider === "gdelt" ? "GDELT" : provider === "bluesky" ? "Bluesky" : provider === "all" ? "All configured sources" : provider;
}

function assessmentLabel(assessment: ClaimComparisonResponse["assessment"]): string {
  return assessment === "SUPPORTED" ? "Selected evidence supports the claim"
    : assessment === "CHALLENGED" ? "Selected evidence challenges the claim"
      : assessment === "MIXED" ? "Selected evidence is mixed"
        : "Not enough evidence in these snippets";
}

function stanceLabel(stance: ClaimComparisonResponse["evidence"][number]["stance"]): string {
  return stance === "SUPPORTS" ? "Supports"
    : stance === "CHALLENGES" ? "Challenges"
      : stance === "CONTEXT" ? "Context"
        : "Unclear";
}

function sourceInitial(domain: string): string {
  return domain.replace(/^www\./, "").slice(0, 1).toUpperCase() || "S";
}

function csvCell(value: string | number | boolean | null): string {
  const text = value == null ? "" : String(value);
  const safe = /^[\s]*[=+@-]/.test(text) ? `'${text}` : text;
  return `"${safe.replaceAll('"', '""')}"`;
}

function formatProbability(value: number | null): string {
  return value == null ? "Not available" : `${(value * 100).toFixed(1)}%`;
}

function NvdDetails({ data }: { data: NvdLookupResponse }) {
  if (data.status !== "AVAILABLE") {
    return <div className="nvd-details" aria-live="polite"><p className="nvd-note">NVD {data.status}: {data.note}</p></div>;
  }
  return <div className="nvd-details" aria-live="polite">
    <p className="nvd-note"><a href={data.sourceUrl} target="_blank" rel="noopener noreferrer">View NVD record ↗</a> · {data.vulnerabilityStatus || "status unavailable"} · Published {formatDate(data.publishedAt)} · Updated {formatDate(data.lastModifiedAt)}</p>
    {data.cvssScore != null && <p className="nvd-note">CVSS {data.cvssVersion} <strong>{data.cvssScore.toFixed(1)} {data.cvssSeverity}</strong>{data.cvssVector ? ` · ${data.cvssVector}` : ""} · NVD severity is not an asset-specific risk score.</p>}
    {data.description && <p className="nvd-description">{data.description}</p>}
    {data.cwes.length > 0 && <p className="nvd-note">Weaknesses: {data.cwes.join(", ")}</p>}
    {data.applicability.length > 0 && <details className="nvd-applicability">
      <summary>NVD CPE criteria ({data.applicability.length}{data.applicabilityTruncated ? "+" : ""})</summary>
      <p className="nvd-note">These are NVD applicability statements; AURA does not automatically match them to SBOM packages.</p>
      {data.applicability.map((item, index) => <p className="sbom-component" key={`${item.criteria}-${index}`}>
        <strong>{item.vulnerable ? "Vulnerable match" : "Context match"}</strong> · {item.criteria}
        {[item.versionStartIncluding && `start including ${item.versionStartIncluding}`, item.versionStartExcluding && `start excluding ${item.versionStartExcluding}`, item.versionEndIncluding && `end including ${item.versionEndIncluding}`, item.versionEndExcluding && `end excluding ${item.versionEndExcluding}`].filter(Boolean).map((value) => <span key={value}> · {value}</span>)}
      </p>)}
    </details>}
    {data.references.length > 0 && <div className="nvd-references"><span>References</span>{data.references.map((reference) => <a key={reference.url} href={reference.url} target="_blank" rel="noopener noreferrer">{reference.tags[0] || "NVD reference"} ↗</a>)}{data.referencesTruncated && <span>More references are available from NVD.</span>}</div>}
    <p className="nvd-note">{data.note}</p>
  </div>;
}

export default function App() {
  const workspaceSession = useRef(0);
  const pageHistoryRequest = useRef(0);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [subject, setSubject] = useState("");
  const [additionalQueries, setAdditionalQueries] = useState<string[]>([]);
  const [mode, setMode] = useState<ResearchMode>("GENERAL");
  const [searchProvider, setSearchProvider] = useState<SearchProviderChoice>("");
  const [country, setCountry] = useState("");
  const [language, setLanguage] = useState("");
  const [freshness, setFreshness] = useState("");
  const [resultLimit, setResultLimit] = useState(10);
  const [jobs, setJobs] = useState<ResearchJob[]>([]);
  const [job, setJob] = useState<ResearchJob | null>(null);
  const [search, setSearch] = useState<SearchResponse | null>(null);
  const [sources, setSources] = useState<ResearchSource[]>([]);
  const [researchReport, setResearchReport] = useState<ResearchReportResponse | null>(null);
  const [claimDraft, setClaimDraft] = useState("");
  const [claimComparison, setClaimComparison] = useState<ClaimComparisonResponse | null>(null);
  const [researchPage, setResearchPage] = useState<ResearchPageResponse | null>(null);
  const [pageReadHistory, setPageReadHistory] = useState<ResearchPageReadAudit[] | null>(null);
  const [pageReadHistorySourceId, setPageReadHistorySourceId] = useState<string | null>(null);
  const [pageSearchQuery, setPageSearchQuery] = useState("");
  const [readingSourceId, setReadingSourceId] = useState<string | null>(null);
  const [loadingPageHistorySourceId, setLoadingPageHistorySourceId] = useState<string | null>(null);
  const [reportBusy, setReportBusy] = useState(false);
  const [claimBusy, setClaimBusy] = useState(false);
  const [draftFilters, setDraftFilters] = useState<ResultFilters>(emptyFilters);
  const [appliedFilters, setAppliedFilters] = useState<ResultFilters>(emptyFilters);
  const [searching, setSearching] = useState(false);
  const [openingJobId, setOpeningJobId] = useState<string | null>(null);
  const [filtering, setFiltering] = useState(false);
  const [exporting, setExporting] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [canRetry, setCanRetry] = useState(false);
  const [cveInput, setCveInput] = useState("");
  const [vulnerabilityLookup, setVulnerabilityLookup] = useState<VulnerabilityLookupResponse | null>(null);
  const [vulnerabilityBusy, setVulnerabilityBusy] = useState(false);
  const [nvdLookups, setNvdLookups] = useState<Record<string, NvdLookupResponse>>({});
  const [nvdBusyCve, setNvdBusyCve] = useState<string | null>(null);
  const [nvdBatchBusy, setNvdBatchBusy] = useState(false);
  const [documents, setDocuments] = useState<ResearchDocument[]>([]);
  const [documentFile, setDocumentFile] = useState<File | null>(null);
  const [documentInputKey, setDocumentInputKey] = useState(0);
  const [documentTitle, setDocumentTitle] = useState("");
  const [documentQuery, setDocumentQuery] = useState("");
  const [documentHits, setDocumentHits] = useState<ResearchDocumentSearchHit[]>([]);
  const [openedDocument, setOpenedDocument] = useState<ResearchDocument | null>(null);
  const [documentUploading, setDocumentUploading] = useState(false);
  const [documentSearching, setDocumentSearching] = useState(false);
  const [openingDocumentId, setOpeningDocumentId] = useState<string | null>(null);
  const [sbomFile, setSbomFile] = useState<File | null>(null);
  const [sbomLookup, setSbomLookup] = useState<SbomLookupResponse | null>(null);
  const [sbomBusy, setSbomBusy] = useState(false);

  const credentials = useMemo<Credentials>(() => ({ username, password }), [username, password]);
  const researchPageMatches = useMemo(
    () => researchPage ? findResearchPageTextMatches(researchPage.text, pageSearchQuery) : null,
    [researchPage, pageSearchQuery],
  );
  const plannedQueries = useMemo(() => {
    const candidates = [subject.trim(), ...additionalQueries.map((query) => query.trim()).filter(Boolean)];
    const seen = new Set<string>();
    return candidates.filter((query) => {
      const key = query.toLowerCase();
      if (!query || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [subject, additionalQueries]);

  async function connect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setConnecting(true);
    setError("");
    try {
      await validateConnection(credentials);
      const [recentJobs, savedDocuments] = await Promise.all([
        listResearchJobs(credentials),
        listDocuments(credentials),
      ]);
      setJobs(recentJobs);
      setDocuments(savedDocuments);
      setConnected(true);
    } catch (caught) {
      setConnected(false);
      setError(errorMessage(caught));
    } finally {
      setConnecting(false);
    }
  }

  async function startSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!connected || !subject.trim()) return;
    setSearching(true);
    setError("");
    setSearch(null);
    setSources([]);
    setResearchReport(null);
    setClaimComparison(null);
    setClaimDraft("");
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    let requestedJobId: string | null = null;
    try {
      const pendingJob = canRetry && job?.subject === subject.trim() && job.mode === mode ? job : null;
      if (!pendingJob) setJob(null);
      setCanRetry(false);
      const currentJob = pendingJob ?? await createResearchJob(credentials, subject.trim(), mode);
      requestedJobId = currentJob.id;
      setJob(currentJob);
      setJobs((current) => [currentJob, ...current.filter((item) => item.id !== currentJob.id)].slice(0, 25));
      const response = await runResearch(credentials, currentJob.id, {
        country,
        language,
        freshness,
        resultLimit,
        provider: searchProvider,
        queries: plannedQueries,
      });
      setSearch(response);
      setSources(response.results);
      const completedJob = {
        ...currentJob,
        status: response.status,
        searchProvider: response.provider,
        searchResultCount: response.providerResultCount,
        providerRuns: response.providerRuns,
      };
      setJob(completedJob);
      setJobs((current) => current.map((item) => item.id === completedJob.id ? completedJob : item));
      setCanRetry(false);
    } catch (caught) {
      const apiError = caught instanceof ApiError ? caught : null;
      setCanRetry(apiError?.code === "SEARCH_PROVIDER_NOT_CONFIGURED");
      setError(errorMessage(caught));
      try {
        const updatedJobs = await listResearchJobs(credentials);
        setJobs(updatedJobs);
        const updatedJob = updatedJobs.find((item) => item.id === requestedJobId);
        if (updatedJob) setJob(updatedJob);
      } catch {
        // Preserve the original search error if refreshing history also fails.
      }
    } finally {
      setSearching(false);
    }
  }

  async function openHistoryItem(item: ResearchJob) {
    setOpeningJobId(item.id);
    setError("");
    setJob(item);
    setSubject(item.subject);
    setAdditionalQueries([]);
    setMode(item.mode);
    setSearch(null);
    setSources([]);
    setResearchReport(null);
    setClaimComparison(null);
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setCanRetry(item.status === "REQUESTED");
    try {
      if (item.status === "COMPLETED" || item.status === "PARTIAL") {
        const response = await listSources(credentials, item.id, emptyFilters);
        setSearch(response);
        setSources(response.results);
      }
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setOpeningJobId(null);
    }
  }

  async function removeHistoryItem(item: ResearchJob) {
    if (!window.confirm(`Delete “${item.subject}” and its saved source metadata?`)) return;
    setError("");
    try {
      await deleteResearchJob(credentials, item.id);
      setJobs((current) => current.filter((entry) => entry.id !== item.id));
      if (job?.id === item.id) {
        setJob(null);
        setSearch(null);
        setSources([]);
        setResearchReport(null);
        setClaimComparison(null);
        setCanRetry(false);
      }
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!job) return;
    setFiltering(true);
    setError("");
    try {
      const response = await listSources(credentials, job.id, draftFilters);
      setAppliedFilters({ ...draftFilters });
      setSources(response.results);
      setResearchReport(null);
      setClaimComparison(null);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setFiltering(false);
    }
  }

  async function exportResults(format: "json" | "csv" | "md") {
    if (!job) return;
    setExporting(format);
    setError("");
    try {
      const blob = await downloadExport(credentials, job.id, appliedFilters, format);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `aura-research-${job.id}.${format}`;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setExporting(null);
    }
  }

  async function createResearchReport() {
    if (!job || sources.length === 0) return;
    const selectedSources = sources.slice(0, 20);
    setReportBusy(true);
    setResearchReport(null);
    setError("");
    try {
      const response = await generateResearchReport(
        credentials,
        job.id,
        selectedSources.map((source) => source.id),
      );
      setResearchReport(response);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setReportBusy(false);
    }
  }

  async function compareClaim() {
    if (!job || sources.length === 0 || !claimDraft.trim()) return;
    const selectedSources = sources.slice(0, 20);
    setClaimBusy(true);
    setClaimComparison(null);
    setError("");
    try {
      const response = await compareResearchClaim(
        credentials,
        job.id,
        claimDraft.trim(),
        selectedSources.map((source) => source.id),
      );
      setClaimComparison(response);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setClaimBusy(false);
    }
  }

  async function readSourcePage(source: ResearchSource) {
    if (!job) return;
    setReadingSourceId(source.id);
    pageHistoryRequest.current += 1;
    setLoadingPageHistorySourceId(null);
    setResearchPage(null);
    setPageReadHistory(null);
    setPageReadHistorySourceId(null);
    setPageSearchQuery("");
    setError("");
    try {
      setResearchPage(await readResearchPage(credentials, job.id, source.id));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setReadingSourceId(null);
    }
  }

  async function loadPageReadHistory(source: ResearchSource) {
    if (!job) return;
    const requestId = ++pageHistoryRequest.current;
    setLoadingPageHistorySourceId(source.id);
    setPageReadHistory(null);
    setPageReadHistorySourceId(source.id);
    setError("");
    try {
      const history = await listResearchPageReads(credentials, job.id, source.id);
      if (pageHistoryRequest.current === requestId) setPageReadHistory(history);
    } catch (caught) {
      if (pageHistoryRequest.current === requestId) {
        setPageReadHistorySourceId(null);
        setError(errorMessage(caught));
      }
    } finally {
      if (pageHistoryRequest.current === requestId) setLoadingPageHistorySourceId(null);
    }
  }

  function downloadResearchPage(page: ResearchPageResponse) {
    const url = URL.createObjectURL(new Blob([page.text], { type: "text/plain;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `aura-page-${page.host.replace(/[^a-zA-Z0-9.-]/g, "_")}.txt`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function downloadResearchPageMatches(
    page: ResearchPageResponse,
    phrase: string,
    matches: NonNullable<typeof researchPageMatches>["matches"],
    hasMore: boolean,
  ) {
    const lines = [
      "AURA exact-phrase page excerpts",
      `Source: ${page.sourceUrl}`,
      `Fetched: ${page.fetchedAt}`,
      `Extracted text SHA-256: ${page.contentHash}`,
      `Case-sensitive phrase: ${phrase}`,
      `Matches shown: ${matches.length}${hasMore ? " (more matches exist; output capped at 200)" : ""}`,
      "",
      ...matches.flatMap((match, index) => [
        `Match ${index + 1} · character offset ${match.start} · length ${match.end - match.start}`,
        `${match.before}${match.text}${match.after}`,
        "",
      ]),
    ];
    const url = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `aura-page-${page.host.replace(/[^a-zA-Z0-9.-]/g, "_")}-matches.txt`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function downloadResearchReport() {
    if (!researchReport || !job) return;
    const citationById = new Map(researchReport.citations.map((citation) => [citation.id, citation]));
    const escapeMarkdown = (value: string) => value
      .replace(/[\r\n]+/g, " ")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("`", "\\`")
      .replaceAll("*", "\\*")
      .replaceAll("_", "\\_");
    const cite = (ids: string[]) => ids.map((id) => {
      const citation = citationById.get(id);
      return citation ? ` [${escapeMarkdown(citation.title)}](<${citation.url}>)` : "";
    }).join("");
    const lines = [
      `# ${escapeMarkdown(researchReport.title)}`,
      "",
      `Query: ${escapeMarkdown(job.subject)}`,
      `Generated: ${formatDate(researchReport.generatedAt)} · Model: ${escapeMarkdown(researchReport.model)}`,
      "",
      "## Summary",
      "",
      `${escapeMarkdown(researchReport.summary)}${cite(researchReport.summarySourceIds)}`,
      "",
      "## Findings",
      "",
      ...researchReport.findings.map((finding) => `- ${escapeMarkdown(finding.text)}${cite(finding.sourceIds)}`),
      "",
      "## Limitations",
      "",
      ...researchReport.limitations.map((limitation) => `- ${escapeMarkdown(limitation)}`),
      `- ${escapeMarkdown(researchReport.coverageNote)}`,
      "",
    ];
    const url = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/markdown;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `aura-report-${job.id}.md`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  function downloadResearchReportJson() {
    if (!researchReport || !job) return;
    const evidence = researchReport.citations.map((citation) => {
      const source = sources.find((item) => item.id === citation.id);
      return {
        sourceId: citation.id,
        title: citation.title,
        url: citation.url,
        domain: citation.domain,
        kind: citation.kind,
        provider: source?.provider ?? null,
        publisher: source?.publisher ?? null,
        publishedAt: source?.publishedAt ?? null,
        retrievedAt: source?.retrievedAt ?? null,
        description: source?.description ?? null,
      };
    });
    const snapshot = {
      schemaVersion: 1,
      exportedAt: new Date().toISOString(),
      research: {
        jobId: job.id,
        query: search?.query ?? job.subject,
        mode: job.mode,
        status: search?.status ?? job.status,
        provider: search?.provider ?? job.searchProvider,
        searchedAt: search?.searchedAt ?? null,
      },
      evidence,
      report: researchReport,
    };
    const url = URL.createObjectURL(new Blob([JSON.stringify(snapshot, null, 2)], {
      type: "application/json;charset=utf-8",
    }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `aura-report-${job.id}.json`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function searchVulnerabilities(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!connected) return;
    const cves = [...new Set(cveInput.split(/[\s,;]+/).map((value) => value.trim().toUpperCase()).filter(Boolean))];
    if (cves.length === 0 || cves.length > 25) {
      setError("Enter between one and 25 CVE IDs.");
      return;
    }
    setVulnerabilityBusy(true);
    setError("");
    try {
      setVulnerabilityLookup(await lookupVulnerabilities(credentials, cves));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setVulnerabilityBusy(false);
    }
  }

  async function loadNvd(cve: string) {
    if (!connected || nvdBusyCve || nvdBatchBusy) return;
    const session = workspaceSession.current;
    setNvdBusyCve(cve);
    setError("");
    try {
      const result = await lookupNvd(credentials, cve);
      if (session === workspaceSession.current) {
        setNvdLookups((current) => ({ ...current, [result.cve]: result }));
      }
    } catch (caught) {
      if (session === workspaceSession.current) setError(errorMessage(caught));
    } finally {
      if (session === workspaceSession.current) setNvdBusyCve(null);
    }
  }

  async function loadNvdBatch(cves: string[]) {
    if (!connected || nvdBusyCve || nvdBatchBusy) return;
    const ids = [...new Set(cves.map((cve) => cve.trim().toUpperCase()).filter(Boolean))];
    if (ids.length === 0 || ids.length > 25) {
      setError("Choose between one and 25 exact CVE IDs for an NVD lookup.");
      return;
    }
    const session = workspaceSession.current;
    setNvdBatchBusy(true);
    setError("");
    try {
      const response: NvdBatchLookupResponse = await lookupNvdBatch(credentials, ids);
      if (session === workspaceSession.current) {
        setNvdLookups((current) => ({
          ...current,
          ...Object.fromEntries(response.results.map((result) => [result.cve, result])),
        }));
      }
    } catch (caught) {
      if (session === workspaceSession.current) setError(errorMessage(caught));
    } finally {
      if (session === workspaceSession.current) setNvdBatchBusy(false);
    }
  }

  async function importDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!connected || !documentFile) return;
    if (documentFile.size > 1024 * 1024) {
      setError("Document files must be 1 MB or smaller.");
      return;
    }
    const session = workspaceSession.current;
    setDocumentUploading(true);
    setError("");
    try {
      const uploaded = await uploadDocument(credentials, documentFile, documentTitle);
      if (session !== workspaceSession.current) return;
      setDocuments((current) => [uploaded, ...current.filter((item) => item.id !== uploaded.id)].slice(0, 100));
      setOpenedDocument(null);
      setDocumentHits([]);
      setDocumentFile(null);
      setDocumentTitle("");
      setDocumentInputKey((current) => current + 1);
    } catch (caught) {
      if (session === workspaceSession.current) setError(errorMessage(caught));
    } finally {
      if (session === workspaceSession.current) setDocumentUploading(false);
    }
  }

  async function searchDocumentLibrary(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!connected) return;
    const session = workspaceSession.current;
    setDocumentSearching(true);
    setError("");
    try {
      const results = await searchDocuments(credentials, documentQuery);
      if (session === workspaceSession.current) {
        setDocumentHits(results);
        setOpenedDocument(null);
      }
    } catch (caught) {
      if (session === workspaceSession.current) setError(errorMessage(caught));
    } finally {
      if (session === workspaceSession.current) setDocumentSearching(false);
    }
  }

  async function openDocument(id: string) {
    if (!connected) return;
    const session = workspaceSession.current;
    setOpeningDocumentId(id);
    setError("");
    try {
      const result = await getDocument(credentials, id);
      if (session === workspaceSession.current) setOpenedDocument(result);
    } catch (caught) {
      if (session === workspaceSession.current) setError(errorMessage(caught));
    } finally {
      if (session === workspaceSession.current) setOpeningDocumentId(null);
    }
  }

  async function removeDocument(item: ResearchDocument) {
    if (!window.confirm(`Delete “${item.title}” and its stored text?`)) return;
    setError("");
    try {
      await deleteDocument(credentials, item.id);
      setDocuments((current) => current.filter((document) => document.id !== item.id));
      setDocumentHits((current) => current.filter((hit) => hit.documentId !== item.id));
      if (openedDocument?.id === item.id) setOpenedDocument(null);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  function downloadDocument(item: ResearchDocument) {
    if (item.content == null) return;
    const isExtractedPdf = item.mediaType === "application/pdf";
    const url = URL.createObjectURL(new Blob([item.content], {
      type: isExtractedPdf ? "text/plain;charset=utf-8" : item.mediaType,
    }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = isExtractedPdf ? `${item.originalFilename}.extracted.txt` : item.originalFilename;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  function downloadClaimComparison(format: "json" | "md") {
    if (!claimComparison || !job) return;
    const evidenceSnapshot = claimComparison.citations.map((citation) => {
      const source = sources.find((item) => item.id === citation.id);
      return {
        sourceId: citation.id,
        title: citation.title,
        url: citation.url,
        domain: citation.domain,
        kind: citation.kind,
        provider: source?.provider ?? null,
        publishedAt: source?.publishedAt ?? null,
        snippet: source?.description ?? null,
      };
    });
    const snapshot = {
      schemaVersion: 1,
      exportedAt: new Date().toISOString(),
      research: { jobId: job.id, query: search?.query ?? job.subject, mode: job.mode },
      evidence: evidenceSnapshot,
      comparison: claimComparison,
    };
    let blob: Blob;
    if (format === "json") {
      blob = new Blob([JSON.stringify(snapshot, null, 2)], { type: "application/json;charset=utf-8" });
    } else {
      const citationById = new Map(claimComparison.citations.map((citation) => [citation.id, citation]));
      const escape = (value: string) => value.replace(/[\r\n]+/g, " ").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("`", "\\`").replaceAll("*", "\\*").replaceAll("_", "\\_");
      const cite = (ids: string[]) => ids.map((id) => {
        const citation = citationById.get(id);
        const number = claimComparison.citations.findIndex((item) => item.id === id) + 1;
        return citation ? ` [${number}](<${citation.url}>)` : "";
      }).join("");
      const lines = [
        "# Claim comparison",
        "",
        `Claim: ${escape(claimComparison.claim)}`,
        `Assessment: ${assessmentLabel(claimComparison.assessment)}`,
        `Generated: ${formatDate(claimComparison.generatedAt)} · Model: ${escape(claimComparison.model)}`,
        "",
        escape(claimComparison.explanation),
        "",
        "## Evidence from selected snippets",
        "",
        ...claimComparison.evidence.map((item) => `- **${stanceLabel(item.stance)}:** ${escape(item.text)}${cite(item.sourceIds)}`),
        "",
        "## Limitations",
        "",
        ...claimComparison.limitations.map((item) => `- ${escape(item)}`),
        `- ${escape(claimComparison.coverageNote)}`,
        "",
      ];
      blob = new Blob([lines.join("\n")], { type: "text/markdown;charset=utf-8" });
    }
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `aura-claim-comparison-${job.id}.${format}`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  function downloadVulnerabilityLookup(format: "json" | "csv") {
    if (!vulnerabilityLookup) return;
    let content: string;
    let mimeType: string;
    if (format === "json") {
      const relevantNvdDetails = Object.fromEntries(vulnerabilityLookup.results
        .filter((finding) => nvdLookups[finding.cve])
        .map((finding) => [finding.cve, nvdLookups[finding.cve]]));
      content = JSON.stringify({ ...vulnerabilityLookup, nvdDetails: relevantNvdDetails }, null, 2);
      mimeType = "application/json;charset=utf-8";
    } else {
      const columns: Array<[string, (finding: VulnerabilityFinding) => string | number | boolean | null]> = [
        ["priorityRank", (finding) => finding.priorityRank],
        ["cve", (finding) => finding.cve],
        ["knownExploited", (finding) => finding.knownExploited],
        ["vendorProject", (finding) => finding.vendorProject],
        ["product", (finding) => finding.product],
        ["epssScore", (finding) => finding.epssScore],
        ["epssPercentile", (finding) => finding.epssPercentile],
        ["epssScoreDate", (finding) => finding.epssScoreDate],
        ["knownRansomwareCampaignUse", (finding) => finding.knownRansomwareCampaignUse],
        ["dateAdded", (finding) => finding.dateAdded],
        ["dueDate", (finding) => finding.dueDate],
        ["priorityRationale", (finding) => finding.priorityRationale],
        ["cisaUrl", (finding) => finding.cisaUrl],
        ["nvdUrl", (finding) => finding.nvdUrl],
        ["epssUrl", (finding) => finding.epssUrl],
      ];
      const headers = [...columns.map(([name]) => name), "nvdStatus", "cvssVersion", "cvssScore", "cvssSeverity", "cwes"];
      content = [headers.map(csvCell).join(","), ...vulnerabilityLookup.results.map((finding) => {
        const nvd = nvdLookups[finding.cve];
        const values: Array<string | number | boolean | null> = [
          ...columns.map(([, value]) => value(finding)),
          nvd?.status ?? null,
          nvd?.cvssVersion ?? null,
          nvd?.cvssScore ?? null,
          nvd?.cvssSeverity ?? null,
          nvd?.cwes.join("; ") ?? null,
        ];
        return values.map(csvCell).join(",");
      })].join("\r\n");
      mimeType = "text/csv;charset=utf-8";
    }
    const url = URL.createObjectURL(new Blob([content], { type: mimeType }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `aura-vulnerability-lookup.${format}`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  async function analyzeSbom(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!connected || !sbomFile) return;
    if (sbomFile.size > 2 * 1024 * 1024) {
      setError("CycloneDX JSON SBOM files must be 2 MB or smaller.");
      return;
    }
    setSbomBusy(true);
    setSbomLookup(null);
    setError("");
    try {
      setSbomLookup(await lookupSbom(credentials, sbomFile));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSbomBusy(false);
    }
  }

  function downloadSbom(format: "json" | "csv") {
    if (!sbomLookup) return;
    let content: string;
    let mimeType: string;
    if (format === "json") {
      const relevantNvdDetails = Object.fromEntries(sbomLookup.vulnerabilities
        .filter((vulnerability) => nvdLookups[vulnerability.cve])
        .map((vulnerability) => [vulnerability.cve, nvdLookups[vulnerability.cve]]));
      content = JSON.stringify({ ...sbomLookup, nvdDetails: relevantNvdDetails }, null, 2);
      mimeType = "application/json;charset=utf-8";
    } else {
      const findings = new Map((sbomLookup.intelligence?.results ?? []).map((finding) => [finding.cve, finding]));
      const columns = ["cve", "affectedBomRefs", "componentNames", "componentVersions", "purls", "knownExploited", "epssScore", "priorityRank", "nvdStatus", "cvssVersion", "cvssScore", "cvssSeverity", "cwes"];
      const rows = sbomLookup.vulnerabilities.map((vulnerability) => {
        const finding = findings.get(vulnerability.cve);
        const nvd = nvdLookups[vulnerability.cve];
        const values: Array<string | number | boolean | null> = [
          vulnerability.cve,
          vulnerability.affectedBomRefs.join("; "),
          vulnerability.affectedComponents.map((component) => component.name).filter(Boolean).join("; "),
          vulnerability.affectedComponents.map((component) => component.version).filter(Boolean).join("; "),
          vulnerability.affectedComponents.map((component) => component.purl).filter(Boolean).join("; "),
          finding?.knownExploited ?? null,
          finding?.epssScore ?? null,
          finding?.priorityRank ?? null,
          nvd?.status ?? null,
          nvd?.cvssVersion ?? null,
          nvd?.cvssScore ?? null,
          nvd?.cvssSeverity ?? null,
          nvd?.cwes.join("; ") ?? null,
        ];
        return values.map(csvCell).join(",");
      });
      content = [columns.map(csvCell).join(","), ...rows].join("\r\n");
      mimeType = "text/csv;charset=utf-8";
    }
    const url = URL.createObjectURL(new Blob([content], { type: mimeType }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `aura-sbom-lookup.${format}`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  function disconnect() {
    workspaceSession.current += 1;
    setConnected(false);
    setUsername("");
    setPassword("");
    setJobs([]);
    setJob(null);
    setSearch(null);
    setSources([]);
    setResearchReport(null);
    setClaimComparison(null);
    setClaimDraft("");
    setSbomFile(null);
    setSbomLookup(null);
    setNvdLookups({});
    setNvdBusyCve(null);
    setNvdBatchBusy(false);
    setDocumentUploading(false);
    setDocumentSearching(false);
    setOpeningDocumentId(null);
    setDocuments([]);
    setDocumentFile(null);
    setDocumentInputKey((current) => current + 1);
    setDocumentTitle("");
    setDocumentQuery("");
    setDocumentHits([]);
    setOpenedDocument(null);
    setCanRetry(false);
    setError("");
  }

  const provider = search?.provider ?? job?.searchProvider;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#top" aria-label="AURA AI home">
          <span className="brand-mark" aria-hidden="true"><span /></span>
          <span className="brand-name">AURA<span> AI</span></span>
        </a>
        <div className="workspace-label">WORKSPACE</div>
        <button className="nav-item nav-item-active" type="button">
          <span className="nav-icon">⌕</span> Research
          <span className="nav-indicator" />
        </button>
        <a className="nav-item nav-link" href="#cyber-intelligence" aria-label="Cybersecurity vulnerability lookup">
          <span className="nav-icon">⌖</span> Cyber intelligence
        </a>
        <a className="nav-item nav-link" href="#documents" aria-label="Document library">
          <span className="nav-icon">▤</span> Documents
        </a>
        <div className="sidebar-divider" />
        <div className="workspace-label">YOUR WORK <span className="history-count">{jobs.length}</span></div>
        {jobs.length > 0 ? <div className="history-list" aria-label="Recent research briefs">
          {jobs.map((item) => <div className={`history-row ${job?.id === item.id ? "history-row-active" : ""}`} key={item.id}>
            <button type="button" className="history-open" onClick={() => void openHistoryItem(item)} disabled={openingJobId === item.id}>
              <span className="history-title">{item.subject}</span>
              <span className="history-subline"><i className={`history-status history-status-${item.status.toLowerCase()}`} />{openingJobId === item.id ? "Opening…" : item.status.toLowerCase()} · {formatDate(item.createdAt)}</span>
            </button>
            <button type="button" className="history-delete" aria-label={`Delete ${item.subject}`} title="Delete brief and its saved sources" onClick={() => void removeHistoryItem(item)}>×</button>
          </div>)}
        </div> : <div className="empty-history">
          <span className="history-glyph">◷</span>
          <p>Research history</p>
          <span>{connected ? "No saved briefs yet." : "Connect the local API to view saved briefs."}</span>
        </div>}
        <div className="sidebar-foot">
          <span className="small-orbit">✳</span>
          <div><strong>Built for careful research</strong><span>Evidence first. Scope always.</span></div>
        </div>
      </aside>

      <main className="main-area" id="top">
        <header className="topbar">
          <div className="breadcrumb"><span>Workspace</span><b>/</b><strong>Research</strong></div>
          <div className="topbar-actions">
            <span className="environment-pill"><i /> LOCAL WORKSPACE</span>
            {connected ? (
              <button className="connection-button is-connected" type="button" onClick={disconnect}>
                <i /> API connected <span className="button-caret">⌄</span>
              </button>
            ) : (
              <a className="connection-button" href="#connection"><i /> Connect API</a>
            )}
            <div className="avatar" aria-label="Local user">A</div>
          </div>
        </header>

        <div className="page-content">
          <section className="hero-row" aria-labelledby="page-title">
            <div>
              <div className="eyebrow"><span className="eyebrow-line" /> OPEN SOURCE INTELLIGENCE</div>
              <h1 id="page-title">Research, with <em>receipts.</em></h1>
              <p className="hero-copy">Explore a question across configured public sources. Every finding stays connected to where it came from.</p>
            </div>
            <div className="hero-art" aria-hidden="true">
              <div className="orbit orbit-one" /><div className="orbit orbit-two" />
              <div className="orbit-core"><span>✳</span></div>
              <i className="orbit-dot dot-one" /><i className="orbit-dot dot-two" /><i className="orbit-dot dot-three" />
            </div>
          </section>

          {jobs.length > 0 && <details className="mobile-history">
            <summary>Recent briefs <span>{jobs.length}</span></summary>
            <div className="mobile-history-list">
              {jobs.map((item) => <div className="mobile-history-row" key={item.id}>
                <button type="button" onClick={(event) => { void openHistoryItem(item); event.currentTarget.closest("details")?.removeAttribute("open"); }}>
                  <span>{item.subject}</span><small>{item.status.toLowerCase()} · {formatDate(item.createdAt)}</small>
                </button>
                <button type="button" aria-label={`Delete ${item.subject}`} onClick={() => void removeHistoryItem(item)}>×</button>
              </div>)}
            </div>
          </details>}

          <div className="content-grid">
            <div className="primary-column">
              <section className="panel search-panel" aria-labelledby="search-title">
                <div className="panel-heading">
                  <div className="step-number">01</div>
                  <div><h2 id="search-title">Start a research brief</h2><p>Tell AURA what you want to understand.</p></div>
                  <span className="panel-tag">NEW BRIEF</span>
                </div>
                <form onSubmit={startSearch}>
                  <label className="field-label" htmlFor="research-subject">Research question or topic</label>
                  <div className="query-box">
                    <span className="query-icon" aria-hidden="true">⌕</span>
                    <input id="research-subject" maxLength={300} value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="e.g. recent developments in passkey adoption" required />
                    <span className="character-count">{subject.length}/300</span>
                  </div>

                  <div className="query-planner">
                    <div className="query-planner-heading"><div><strong>Search angles</strong><span>Add up to two focused versions of the topic.</span></div><span>{plannedQueries.length}/3 queries</span></div>
                    {additionalQueries.map((query, index) => <div className="planned-query-input" key={index}>
                      <label className="sr-only" htmlFor={`planned-query-${index}`}>Additional search query {index + 1}</label>
                      <input id={`planned-query-${index}`} maxLength={300} value={query} onChange={(event) => setAdditionalQueries((current) => current.map((item, itemIndex) => itemIndex === index ? event.target.value : item))} placeholder="e.g. official response, timeline, analysis" />
                      <button type="button" aria-label={`Remove search angle ${index + 1}`} onClick={() => setAdditionalQueries((current) => current.filter((_, itemIndex) => itemIndex !== index))}>×</button>
                    </div>)}
                    {additionalQueries.length < 2 && <button className="add-query-button" type="button" onClick={() => setAdditionalQueries((current) => current.length < 2 ? [...current, ""] : current)}>+ Add search angle</button>}
                    <p className="query-plan-preview"><strong>Plan:</strong> {plannedQueries.length ? plannedQueries.map((query, index) => `${index + 1}. ${query}`).join(" · ") : "Enter a topic to preview the plan."}</p>
                    <p className="query-plan-disclosure">Each query is sent to the selected public provider(s). “All configured sources” can make up to 9 requests (3 queries × 3 providers); the result limit applies to each provider and query. Provider quotas or charges may apply. Results cover provider indexes, not the whole internet.</p>
                  </div>

                  <div className="form-row">
                    <div className="field-group">
                      <label className="field-label" htmlFor="research-mode">Brief category</label>
                      <div className="select-wrap"><select id="research-mode" value={mode} onChange={(event) => setMode(event.target.value as ResearchMode)}>
                        <option value="GENERAL">General research</option>
                        <option value="CYBERSECURITY">Cybersecurity intelligence</option>
                      </select><span>⌄</span></div>
                    </div>
                    <div className="field-group">
                      <label className="field-label" htmlFor="search-provider">Search source</label>
                      <div className="select-wrap"><select id="search-provider" value={searchProvider} onChange={(event) => setSearchProvider(event.target.value as SearchProviderChoice)}>
                        <option value="">Automatic</option>
                        <option value="all">All configured sources · broader coverage</option>
                        <option value="brave">Brave · web and discussions</option>
                        <option value="gdelt">GDELT · global news</option>
                        <option value="bluesky">Bluesky · public posts</option>
                      </select><span>⌄</span></div>
                    </div>
                    <div className="field-group">
                      <label className="field-label" htmlFor="freshness">Time window</label>
                      <div className="select-wrap"><select id="freshness" value={freshness} onChange={(event) => setFreshness(event.target.value)}>
                        <option value="">Provider default (varies by source)</option>
                        <option value="pd">Past 24 hours</option>
                        <option value="pw">Past week</option>
                        <option value="pm">Past month</option>
                        <option value="py">Past year (GDELT: max 3 months)</option>
                      </select><span>⌄</span></div>
                    </div>
                    <div className="field-group field-limit">
                      <label className="field-label" htmlFor="result-limit">Maximum per result type</label>
                      <div className="select-wrap"><select id="result-limit" value={resultLimit} onChange={(event) => setResultLimit(Number(event.target.value))}>
                        {[5, 10, 20].map((limit) => <option key={limit} value={limit}>{limit} max</option>)}
                      </select><span>⌄</span></div>
                    </div>
                  </div>

                  <details className="advanced-options">
                    <summary><span className="sliders-icon">☷</span> More search options <span className="summary-chevron">⌄</span></summary>
                    <div className="form-row advanced-row">
                      <div className="field-group"><label className="field-label" htmlFor="country">Country code</label><input id="country" className="text-input" maxLength={2} value={country} onChange={(event) => setCountry(event.target.value.toUpperCase())} placeholder="e.g. US" /></div>
                      <div className="field-group"><label className="field-label" htmlFor="language">Language code</label><input id="language" className="text-input" maxLength={2} value={language} onChange={(event) => setLanguage(event.target.value.toLowerCase())} placeholder="e.g. en" /></div>
                    </div>
                  </details>

                  <div className="form-footer">
                    <span className="provider-note"><i className="shield-icon">◇</i> Public results only · no page fetching</span>
                    <button className="primary-button" type="submit" disabled={!connected || searching || !subject.trim()}>
                      {searching ? <><span className="spinner" /> Searching sources</> : <>Start research <span aria-hidden="true">↗</span></>}
                    </button>
                  </div>
                  {!connected && <p className="inline-hint">Connect your local API to begin. Credentials stay in this page's memory.</p>}
                </form>
              </section>

              <section className="results-section" aria-live="polite">
                <div className="results-heading">
                  <div><div className="section-kicker">YOUR EVIDENCE</div><h2>Research results{search ? <span className="result-total">{sources.length}</span> : null}</h2></div>
                  {search && <div className="export-actions" aria-label="Research actions and downloads">
                    <button type="button" onClick={() => void createResearchReport()} disabled={!connected || reportBusy || claimBusy || sources.length === 0}>{reportBusy ? "Generating…" : `✳ Report (${Math.min(sources.length, 20)})`}</button>
                    <button type="button" onClick={() => void exportResults("csv")} disabled={Boolean(exporting)}>{exporting === "csv" ? "Preparing…" : "↓ CSV"}</button>
                    <button type="button" onClick={() => void exportResults("json")} disabled={Boolean(exporting)}>{exporting === "json" ? "Preparing…" : "↓ JSON"}</button>
                    <button type="button" onClick={() => void exportResults("md")} disabled={Boolean(exporting)}>{exporting === "md" ? "Preparing…" : "↓ MD"}</button>
                  </div>}
                </div>

                {search && job && <>
                  <div className="research-meta">
                    <span className="meta-query"><span>↳</span> {search.query}</span>
                    <span className="meta-divider" />
                    <span className="meta-item"><i className="status-dot" /> {provider ? providerLabel(provider) : "Provider pending"}</span>
                    <span className="meta-item">Updated {formatDate(search.searchedAt)}</span>
                    <span className="meta-item">{search.status === "PARTIAL" ? "Partial results" : "Complete"}</span>
                  </div>
                  <form className="filter-bar" onSubmit={applyFilters}>
                    <label className="filter-search"><span aria-hidden="true">⌕</span><input aria-label="Search within results" placeholder="Search titles and queries" value={draftFilters.q} onChange={(event) => setDraftFilters({ ...draftFilters, q: event.target.value })} /></label>
                    <label className="filter-select"><span className="sr-only">Source type</span><select value={draftFilters.kind} onChange={(event) => setDraftFilters({ ...draftFilters, kind: event.target.value as ResultFilters["kind"] })}><option value="">All sources</option><option value="WEB">Web</option><option value="NEWS">News</option><option value="DISCUSSION">Discussions</option></select><span>⌄</span></label>
                    <label className="filter-select"><span className="sr-only">Provider</span><select aria-label="Filter by provider" value={draftFilters.provider} onChange={(event) => setDraftFilters({ ...draftFilters, provider: event.target.value })}><option value="">All providers</option>{[...new Set((search?.results ?? []).map((source) => source.provider))].sort().map((name) => <option key={name} value={name}>{providerLabel(name)}</option>)}</select><span>⌄</span></label>
                    <label className="filter-host"><span aria-hidden="true">◎</span><input aria-label="Filter by domain" placeholder="Filter domain" value={draftFilters.host} onChange={(event) => setDraftFilters({ ...draftFilters, host: event.target.value })} /></label>
                    <details className="date-filter"><summary>Dates <span>⌄</span></summary><div className="date-filter-popover"><label>From<input type="date" value={draftFilters.publishedFrom} onChange={(event) => setDraftFilters({ ...draftFilters, publishedFrom: event.target.value })} /></label><label>To<input type="date" value={draftFilters.publishedTo} onChange={(event) => setDraftFilters({ ...draftFilters, publishedTo: event.target.value })} /></label></div></details>
                    <button className="filter-button" type="submit" disabled={filtering}>{filtering ? "…" : "Apply"}</button>
                  </form>
                  {search.coverageNote && <p className="coverage-note"><span>ⓘ</span> {search.coverageNote}</p>}
                  {search.providerRuns?.length > 0 && <p className="report-scope-note">Provider outcomes: {search.providerRuns.map((run) => `“${run.query}” → ${providerLabel(run.provider)} ${run.status.toLowerCase()} · ${run.resultCount} results${run.failureCode ? ` · ${run.failureCode}` : ""}`).join("  |  ")}</p>}
                  <p className="report-scope-note">A report or claim comparison uses up to 20 visible results after filtering. Their metadata and snippets go to your configured local model only after you choose an action. AURA never fetches pages automatically; Read page sends one unauthenticated HTTPS request after you choose it. Fetched text stays in browser memory and is excluded from reports and comparisons.</p>
                  <section className="claim-comparison-panel" aria-label="Compare a claim against selected sources">
                    <div className="claim-comparison-intro"><div className="section-kicker">EVIDENCE TOOL</div><h3>Compare a claim</h3><p>Enter one statement to compare with up to 20 visible source snippets. This is an AI assessment of those excerpts, not a fact-check.</p></div>
                    <label className="sr-only" htmlFor="claim-to-compare">Claim to compare</label>
                    <textarea id="claim-to-compare" maxLength={500} rows={2} value={claimDraft} onChange={(event) => setClaimDraft(event.target.value)} placeholder="Type a specific claim to assess against the sources above" disabled={!connected || claimBusy || reportBusy} />
                    <div className="claim-comparison-actions"><span>{claimDraft.length}/500 · first {Math.min(sources.length, 20)} visible sources</span><button type="button" onClick={() => void compareClaim()} disabled={!connected || claimBusy || reportBusy || sources.length === 0 || !claimDraft.trim()}>{claimBusy ? "Comparing…" : "Compare claim"}</button></div>
                  </section>
                  {claimComparison && <section className="research-report claim-comparison-result" aria-label="AI claim comparison result">
                    <div className="report-heading"><div><div className="section-kicker">LOCAL AI ASSESSMENT · {claimComparison.assessment}</div><h3>{assessmentLabel(claimComparison.assessment)}</h3><p>{claimComparison.provider} · {claimComparison.model} · {formatDate(claimComparison.generatedAt)}</p></div><div className="report-download-actions"><button className="nvd-lookup-button" type="button" onClick={() => downloadClaimComparison("md")}>↓ Markdown</button><button className="nvd-lookup-button" type="button" onClick={() => downloadClaimComparison("json")}>↓ JSON</button></div></div>
                    <p className="claim-comparison-explanation"><strong>Claim:</strong> {claimComparison.claim}<br />{claimComparison.explanation}</p>
                    {claimComparison.evidence.length > 0 && <div className="report-findings"><strong>Evidence from selected snippets</strong>{claimComparison.evidence.map((item, index) => <p key={`${index}-${item.text}`}><span className={`claim-stance stance-${item.stance.toLowerCase()}`}>{stanceLabel(item.stance)}</span> {item.text}{item.sourceIds.map((id) => {
                      const citation = claimComparison.citations.find((source) => source.id === id);
                      const citationNumber = claimComparison.citations.findIndex((source) => source.id === id) + 1;
                      return citation ? <a key={id} href={citation.url} target="_blank" rel="noopener noreferrer" title={citation.title}> [{citationNumber}]</a> : null;
                    })}</p>)}</div>}
                    {claimComparison.limitations.length > 0 && <div className="report-findings"><strong>Limitations</strong>{claimComparison.limitations.map((item, index) => <p key={`${index}-${item}`}>{item}</p>)}</div>}
                    <div className="report-citations"><strong>Candidate sources</strong>{claimComparison.citations.map((citation, index) => <a key={citation.id} href={citation.url} target="_blank" rel="noopener noreferrer">[{index + 1}] {citation.title} · {citation.domain}</a>)}</div>
                    <p className="coverage-note"><span>ⓘ</span> {claimComparison.coverageNote}</p>
                  </section>}
                  {researchReport && <section className="research-report" aria-label="Generated cited report">
                    <div className="report-heading">
                      <div><div className="section-kicker">LOCAL AI REPORT</div><h3>{researchReport.title}</h3><p>{researchReport.provider} · {researchReport.model} · {formatDate(researchReport.generatedAt)}</p></div>
                      <div className="report-download-actions"><button className="nvd-lookup-button" type="button" onClick={downloadResearchReport}>↓ Download Markdown</button><button className="nvd-lookup-button" type="button" onClick={downloadResearchReportJson}>↓ Download JSON</button></div>
                    </div>
                    <p className="report-summary">{researchReport.summary}{researchReport.summarySourceIds.map((id) => {
                      const citation = researchReport.citations.find((item) => item.id === id);
                      const citationNumber = researchReport.citations.findIndex((item) => item.id === id) + 1;
                      return citation ? <a key={id} href={citation.url} target="_blank" rel="noopener noreferrer" title={citation.title}> [{citationNumber}]</a> : null;
                    })}</p>
                    {researchReport.findings.length > 0 && <div className="report-findings"><strong>Findings</strong>{researchReport.findings.map((finding, index) => <p key={`${index}-${finding.text}`}>{finding.text}{finding.sourceIds.map((id) => {
                      const citation = researchReport.citations.find((item) => item.id === id);
                      const citationNumber = researchReport.citations.findIndex((item) => item.id === id) + 1;
                      return citation ? <a key={id} href={citation.url} target="_blank" rel="noopener noreferrer" title={citation.title}> [{citationNumber}]</a> : null;
                    })}</p>)}</div>}
                    {researchReport.limitations.length > 0 && <div className="report-findings"><strong>Limitations</strong>{researchReport.limitations.map((limitation, index) => <p key={`${index}-${limitation}`}>{limitation}</p>)}</div>}
                    <div className="report-citations"><strong>Candidate sources</strong>{researchReport.citations.map((citation, index) => <a key={citation.id} href={citation.url} target="_blank" rel="noopener noreferrer">[{index + 1}] {citation.title} · {citation.domain}</a>)}</div>
                    <p className="coverage-note"><span>ⓘ</span> {researchReport.coverageNote}</p>
                  </section>}
                  {sources.length > 0 ? <div className="source-list">{sources.map((source) => <article className="source-card" key={source.id}>
                    <div className="source-monogram" aria-hidden="true">{sourceInitial(source.domain)}</div>
                    <div className="source-content">
                      <div className="source-meta"><span className={`type-pill type-${source.kind.toLowerCase()}`}>{kindLabel(source.kind)}</span><span className="source-domain">{source.domain}</span><span className="source-date">{formatDate(source.publishedAt)}</span></div>
                      <h3><a href={source.url} target="_blank" rel="noopener noreferrer">{source.title}<span aria-hidden="true"> ↗</span></a></h3>
                      {source.searchQuery && <p className="source-query-label">Matched query: {source.searchQuery}</p>}
                      {source.description && <p>{source.description}</p>}
                      <div className="source-page-actions"><button className="nvd-lookup-button" type="button" onClick={() => void readSourcePage(source)} disabled={readingSourceId === source.id}>{readingSourceId === source.id ? "Reading…" : "Read page"}</button><button className="nvd-lookup-button" type="button" onClick={() => void loadPageReadHistory(source)} disabled={loadingPageHistorySourceId !== null}>{loadingPageHistorySourceId === source.id ? "Loading history…" : "Read history"}</button></div>
                      {pageReadHistorySourceId === source.id && pageReadHistory && <div className="source-page-history" aria-live="polite">
                        <strong>Page-read audit history</strong>
                        {pageReadHistory.length === 0 ? <p>No page reads have been recorded for this source.</p> : <ul>{pageReadHistory.map((read) => <li key={read.id}><span>{read.status} · {new Date(read.requestedAt).toLocaleString()}{read.completedAt ? " · completed " + new Date(read.completedAt).toLocaleString() : " · still running"}</span>{read.characterCount != null && <span>{read.characterCount.toLocaleString()} characters{read.truncated ? " · excerpt capped" : ""}</span>}{read.contentHash && <span>SHA‑256 {read.contentHash}</span>}{read.failureCode && <span>Failure code {read.failureCode}</span>}</li>)}</ul>}
                        <p>Audit history stores request metadata and content hashes, never fetched page text.</p>
                      </div>}
                      {researchPage?.sourceId === source.id && <div className="source-page" aria-live="polite">
                        <div className="source-page-heading"><strong>{researchPage.title || source.title}</strong><button className="nvd-lookup-button" type="button" onClick={() => downloadResearchPage(researchPage)}>↓ Download text</button></div>
                        <p>{researchPage.characterCount.toLocaleString()} characters · Read {researchPage.readId.slice(0, 8)} · SHA‑256 {researchPage.contentHash.slice(0, 12)}… · {formatDate(researchPage.fetchedAt)}{researchPage.truncated ? " · excerpt capped at 100,000 characters" : ""}</p>
                        <label className="field-label source-page-search-label" htmlFor={`source-page-search-${source.id}`}>Find exact text in this page (case-sensitive)</label>
                        <div className="source-page-search"><input id={`source-page-search-${source.id}`} type="search" maxLength={MAX_PAGE_SEARCH_LENGTH} value={pageSearchQuery} onChange={(event) => setPageSearchQuery(event.target.value)} placeholder="Enter a phrase from the page" /><span>{pageSearchQuery.length}/{MAX_PAGE_SEARCH_LENGTH}</span></div>
                        {pageSearchQuery && researchPageMatches && <div className="source-page-matches" aria-live="polite">
                          <p>{researchPageMatches.matches.length === 0 ? "No exact matches." : `${researchPageMatches.matches.length}${researchPageMatches.hasMore ? "+" : ""} exact match${researchPageMatches.matches.length === 1 && !researchPageMatches.hasMore ? "" : "es"}${researchPageMatches.hasMore ? " · showing first 200" : ""}`}</p>
                          {researchPageMatches.matches.length > 0 && <>
                            <ol>{researchPageMatches.matches.map((match, index) => <li key={`${match.start}-${index}`}><span>Character offset {match.start} · length {match.end - match.start}</span><p>{match.before}<mark>{match.text}</mark>{match.after}</p></li>)}</ol>
                            <button className="nvd-lookup-button" type="button" onClick={() => downloadResearchPageMatches(researchPage, pageSearchQuery, researchPageMatches.matches, researchPageMatches.hasMore)}>↓ Download matching excerpts</button>
                          </>}
                        </div>}
                        <pre>{researchPage.text}</pre>
                        <p>{researchPage.coverageNote}</p>
                      </div>}
                      <div className="publisher-line">{providerLabel(source.provider)}<span>·</span> {source.publisher || source.domain}<span>·</span> Retrieved {formatDate(source.retrievedAt)}</div>
                    </div>
                    <span className="source-rank">{String(source.rank).padStart(2, "0")}</span>
                  </article>)}</div> : <div className="empty-results"><span>⌕</span><strong>No sources match these filters</strong><p>Try clearing a filter or widening the date range.</p></div>}
                </>}
                {!search && job && <div className="empty-results welcome-results"><span>{job.status === "FAILED" ? "!" : "◷"}</span><strong>{job.status === "FAILED" ? "This search did not finish" : job.status === "SEARCHING" ? "This search is still running" : "This brief has not been searched yet"}</strong><p>{job.status === "FAILED" ? `The saved failure code is ${job.failureCode ?? "unavailable"}. Start a new brief to try again.` : job.status === "SEARCHING" ? "Reload this brief in a moment to see whether the provider returned." : "If the provider key was just configured, press Start research to retry this saved brief."}</p>{job.providerRuns && job.providerRuns.length > 0 && <p>Provider outcomes: {job.providerRuns.map((run) => `${providerLabel(run.provider)} ${run.status.toLowerCase()}${run.failureCode ? ` · ${run.failureCode}` : ""}`).join("  |  ")}</p>}</div>}
                {!search && !job && <div className="empty-results welcome-results"><span>✳</span><strong>Your sources will appear here</strong><p>Start a research brief to gather public links, filter them, and download your findings.</p></div>}
              </section>
            </div>

            <aside className="right-column">
              <section className="connection-panel panel" id="connection">
                <div className="connection-heading"><div className="step-number">02</div><div><h2>Local API</h2><p>Connect to your AURA instance.</p></div></div>
                {connected ? <div className="connected-card"><div className="connected-check">✓</div><div><strong>Connected securely</strong><span>{username}</span></div><button type="button" onClick={disconnect}>Disconnect</button></div> : <form className="connection-form" onSubmit={connect}>
                  <label className="field-label" htmlFor="api-username">Username</label>
                  <input id="api-username" className="text-input" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Your .env username" required />
                  <label className="field-label password-label" htmlFor="api-password">Password</label>
                  <div className="password-wrap"><input id="api-password" className="text-input" type={showPassword ? "text" : "password"} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Your .env password" required /><button type="button" aria-label={showPassword ? "Hide password" : "Show password"} onClick={() => setShowPassword(!showPassword)}>{showPassword ? "Hide" : "Show"}</button></div>
                  <button className="connect-submit" type="submit" disabled={connecting || !username || !password}>{connecting ? <><span className="spinner" /> Checking…</> : <>Connect to API <span>→</span></>}</button>
                  <p className="privacy-note"><span>◈</span> Sent only to your local AURA API. Cleared when you disconnect or reload.</p>
                </form>}
              </section>

              <section className="source-scope panel">
                <div className="scope-head"><span className="scope-icon">◎</span><div><h2>Source scope</h2><p>What this search can reach</p></div></div>
                <div className="scope-provider"><span className="provider-mark">{searchProvider === "gdelt" ? "G" : searchProvider === "brave" ? "B" : searchProvider === "bluesky" ? "S" : searchProvider === "all" ? "+" : "A"}</span><div><strong>{searchProvider === "gdelt" ? "GDELT News" : searchProvider === "brave" ? "Brave Search" : searchProvider === "bluesky" ? "Bluesky Public Search" : searchProvider === "all" ? "All configured sources" : "Automatic provider"}</strong><span>{searchProvider === "gdelt" ? "Public global news" : searchProvider === "brave" ? "Web · News · Discussions" : searchProvider === "bluesky" ? "Public posts · no account connection" : searchProvider === "all" ? "Brave if keyed · GDELT + Bluesky if enabled" : "Brave when configured · otherwise GDELT"}</span></div><span className="scope-live"><i /> {connected ? "API LINKED" : "LOCAL API"}</span></div>
                <div className="scope-divider" />
                <p className="scope-copy">AURA searches selected public providers and saves each returned link. All configured sources queries Brave when its key is set, plus GDELT and Bluesky when enabled. Brave covers web, news, and discussions; GDELT covers indexed global news from at most the past three months; Bluesky returns bounded public posts. Country filters do not apply to Bluesky. Coverage is partial, not a crawl of the entire internet.</p>
                <p className="scope-copy">Automatic uses Brave when its key is configured, then GDELT, then Bluesky if earlier providers are disabled. These public APIs need no account connection. Queries leave your computer only when you start a search.</p>
                <div className="scope-foot"><span>⌁</span> Public Bluesky posts only · no private feeds or connected accounts</div>
              </section>

              <section className="responsible-card">
                <span className="responsible-symbol">✳</span>
                <div><strong>Research responsibly</strong><p>Use public-interest sources. Avoid doxxing, private-account access, and sensitive-trait profiling.</p></div>
              </section>
            </aside>
          </div>
          <section className="panel documents-panel" id="documents" aria-labelledby="documents-title">
            <div className="panel-heading">
              <div className="step-number">03</div>
              <div><h2 id="documents-title">Document library</h2><p>Store and search selected text, Markdown, and PDF documents.</p></div>
              <span className="panel-tag">MAX 1 MB</span>
            </div>
            <form className="document-upload-form" onSubmit={importDocument}>
              <label className="field-label" htmlFor="document-file">Choose a .txt, .md, or .pdf file</label>
              <input key={documentInputKey} id="document-file" className="text-input sbom-file-input" type="file" accept=".txt,.md,.pdf,text/plain,text/markdown,application/pdf" onChange={(event) => setDocumentFile(event.currentTarget.files?.[0] ?? null)} />
              <label className="field-label document-title-label" htmlFor="document-title">Title (optional)</label>
              <input id="document-title" className="text-input" maxLength={300} value={documentTitle} onChange={(event) => setDocumentTitle(event.target.value)} placeholder="Use the file name if left blank" />
              <div className="cyber-form-footer">
                <span className="provider-note"><i className="shield-icon">◇</i> Local upload · UTF‑8 only · no external provider calls</span>
                <button className="primary-button" type="submit" disabled={!connected || documentUploading || !documentFile}>
                  {documentUploading ? <><span className="spinner" /> Importing</> : <>Import document <span aria-hidden="true">↗</span></>}
                </button>
              </div>
              <p className="cyber-egress-note">AURA stores extracted text in its local database with a SHA‑256 text hash. Search returns exact phrase and page citations; documents are not sent to Brave or an AI model. PDF text extraction is limited to 100 pages and 250,000 characters. Scanned PDFs are unsupported because OCR is not enabled; PDF downloads are marked as extracted text.</p>
            </form>

            <form className="document-search-form" onSubmit={searchDocumentLibrary}>
              <label className="filter-search"><span aria-hidden="true">⌕</span><input aria-label="Search documents" placeholder="Search your documents" value={documentQuery} onChange={(event) => setDocumentQuery(event.target.value)} /></label>
              <button className="filter-button" type="submit" disabled={!connected || documentSearching || documentQuery.trim().length < 2}>{documentSearching ? "Searching…" : "Search"}</button>
            </form>
            {documentHits.length > 0 && <div className="document-hit-list" aria-label="Document search results">{documentHits.map((hit) => <article className="document-hit" key={hit.documentId}>
              <div><button className="document-open-button" type="button" onClick={() => void openDocument(hit.documentId)}>{hit.title}</button><p>{hit.originalFilename}{hit.page == null ? "" : ` · page ${hit.page}`} · characters {hit.startOffset}–{hit.endOffset}</p><blockquote>{hit.snippet}</blockquote><code>{hit.citation}</code></div>
            </article>)}</div>}
            {documentHits.length === 0 && documentQuery && <p className="cyber-egress-note">No matching document results yet. Run the search to check this library.</p>}
            <div className="document-list-heading"><strong>Your documents</strong><span>{documents.length} shown</span></div>
            {documents.length > 0 ? <div className="document-list">{documents.map((item) => <article className="document-row" key={item.id}>
              <button type="button" className="document-open-button" onClick={() => void openDocument(item.id)} disabled={openingDocumentId === item.id}>{openingDocumentId === item.id ? "Opening…" : item.title}</button>
              <span>{item.originalFilename} · {item.characterCount.toLocaleString()} characters · {item.contentHash.slice(0, 12)}…</span>
              <button type="button" className="history-delete" aria-label={`Delete document ${item.title}`} onClick={() => void removeDocument(item)}>×</button>
            </article>)}</div> : <div className="empty-results"><strong>No documents imported</strong><p>Upload a UTF‑8 text, Markdown, or text-based PDF file to start a private library.</p></div>}
            {openedDocument && <div className="document-preview" aria-live="polite">
              <div className="document-list-heading"><strong>{openedDocument.title}</strong><div><button className="document-open-button" type="button" onClick={() => downloadDocument(openedDocument)}>Download</button><button className="document-open-button" type="button" onClick={() => setOpenedDocument(null)}>Close</button></div></div>
              <p>{openedDocument.originalFilename} · SHA‑256 {openedDocument.contentHash} · {openedDocument.characterCount.toLocaleString()} characters</p>
              <pre>{(openedDocument.content ?? "").slice(0, 20000)}{openedDocument.characterCount > 20000 ? "\n\n[Preview limited to 20,000 characters. Download the complete file.]" : ""}</pre>
            </div>}
          </section>
          <section className="panel cyber-panel" id="cyber-intelligence" aria-labelledby="cyber-title">
            <div className="panel-heading">
              <div className="step-number">04</div>
              <div><h2 id="cyber-title">Cybersecurity vulnerability lookup</h2><p>Check specific CVE IDs against CISA KEV and FIRST EPSS.</p></div>
              <span className="panel-tag">READ ONLY</span>
            </div>
            <form className="cyber-lookup-form" onSubmit={searchVulnerabilities}>
              <label className="field-label" htmlFor="cve-list">CVE IDs</label>
              <textarea id="cve-list" className="text-input cyber-cve-input" rows={3} maxLength={600} value={cveInput} onChange={(event) => setCveInput(event.target.value)} placeholder="CVE-2024-12345, CVE-2023-67890" required />
              <div className="cyber-form-footer">
                <span className="provider-note"><i className="shield-icon">◇</i> One to 25 exact IDs · no scanning or asset probing</span>
                <button className="primary-button" type="submit" disabled={!connected || vulnerabilityBusy || !cveInput.trim()}>
                  {vulnerabilityBusy ? <><span className="spinner" /> Checking sources</> : <>Check vulnerabilities <span aria-hidden="true">↗</span></>}
                </button>
              </div>
              <p className="cyber-egress-note">AURA checks the CISA KEV catalog and sends the CVE IDs to FIRST EPSS. CISA data is cached locally for six hours; results show source dates and retrieval times.</p>
            </form>

            {vulnerabilityLookup && <div className="cyber-results" aria-live="polite">
              <div className="cyber-results-header">
                <div><div className="section-kicker">SOURCE STATUS</div><p className="cyber-source-status"><a href={vulnerabilityLookup.kev.sourceUrl} target="_blank" rel="noopener noreferrer">CISA KEV</a> {vulnerabilityLookup.kev.status}{vulnerabilityLookup.kev.dataAsOf ? ` · data ${vulnerabilityLookup.kev.dataAsOf}` : ""}{vulnerabilityLookup.kev.note ? ` · ${vulnerabilityLookup.kev.note}` : ""}</p>
                  <p className="cyber-source-status"><a href={vulnerabilityLookup.epss.sourceUrl} target="_blank" rel="noopener noreferrer">FIRST EPSS</a> {vulnerabilityLookup.epss.status}{vulnerabilityLookup.epss.dataAsOf ? ` · data ${vulnerabilityLookup.epss.dataAsOf}` : ""}{vulnerabilityLookup.epss.note ? ` · ${vulnerabilityLookup.epss.note}` : ""}</p>
                  <p className="cyber-retrieved">Checked {formatDate(vulnerabilityLookup.requestedAt)}</p>
                  {vulnerabilityLookup.results.length > 0 && <button className="nvd-lookup-button" type="button" disabled={!connected || nvdBusyCve !== null || nvdBatchBusy} onClick={() => void loadNvdBatch(vulnerabilityLookup.results.map((finding) => finding.cve))}>{nvdBatchBusy ? "Loading NVD details…" : `Load NVD details for all ${vulnerabilityLookup.results.length} CVEs`}</button>}
                </div>
                <div className="export-actions" aria-label="Download vulnerability lookup">
                  <button type="button" onClick={() => downloadVulnerabilityLookup("csv")}>↓ CSV</button>
                  <button type="button" onClick={() => downloadVulnerabilityLookup("json")}>↓ JSON</button>
                </div>
              </div>
              <p className="coverage-note"><span>ⓘ</span> {vulnerabilityLookup.coverageNote}</p>
              {vulnerabilityLookup.results.length ? <div className="cyber-findings">
                {vulnerabilityLookup.results.map((finding) => <article className="cyber-finding" key={finding.cve}>
                  <div className="cyber-finding-rank">{String(finding.priorityRank).padStart(2, "0")}</div>
                  <div className="cyber-finding-main">
                    <div className="cyber-finding-heading"><a href={finding.nvdUrl} target="_blank" rel="noopener noreferrer">{finding.cve} ↗</a><span className={finding.knownExploited ? "kev-badge kev-badge-active" : "kev-badge"}>{finding.knownExploited ? "CISA KEV" : finding.knownExploited === null ? "KEV unavailable" : vulnerabilityLookup.kev.status === "STALE" ? "Not in stale snapshot" : "Not in KEV"}</span></div>
                    <h3>{finding.vulnerabilityName || "No KEV name in current source"}</h3>
                    <p className="cyber-product">{[finding.vendorProject, finding.product].filter(Boolean).join(" · ") || "Product not listed in KEV"}</p>
                    <p className="cyber-rationale">{finding.priorityRationale}</p>
                    <div className="cyber-metrics"><span>EPSS <strong>{formatProbability(finding.epssScore)}</strong></span><span>Percentile <strong>{formatProbability(finding.epssPercentile)}</strong></span><span>KEV added <strong>{finding.dateAdded || "—"}</strong></span><span>Due date <strong>{finding.dueDate || "—"}</strong></span><span>Known ransomware use <strong>{finding.knownRansomwareCampaignUse || "—"}</strong></span></div>
                    {finding.requiredAction && <p className="cyber-action">CISA action: {finding.requiredAction}</p>}
                    <div className="cyber-source-links"><a href={finding.cisaUrl} target="_blank" rel="noopener noreferrer">CISA catalog</a><a href={finding.epssUrl} target="_blank" rel="noopener noreferrer">EPSS record</a></div>
                    <button className="nvd-lookup-button" type="button" disabled={!connected || nvdBusyCve !== null || nvdBatchBusy} onClick={() => void loadNvd(finding.cve)}>{nvdBusyCve === finding.cve ? "Loading NVD…" : nvdLookups[finding.cve] ? "Refresh NVD details" : "Load NVD details"}</button>
                    {nvdLookups[finding.cve] && <NvdDetails data={nvdLookups[finding.cve]} />}
                  </div>
                </article>)}
              </div> : <div className="empty-results"><strong>No data matched those CVE IDs</strong><p>Check the identifier or retry when the public data sources are available.</p></div>}
            </div>}

            <div className="sbom-divider" />
            <div className="sbom-heading"><div><h3>CycloneDX SBOM review</h3><p>Enrich CVEs already listed in a CycloneDX JSON file.</p></div><span className="panel-tag">MAX 2 MB</span></div>
            <form className="sbom-form" onSubmit={analyzeSbom}>
              <label className="field-label" htmlFor="sbom-file">CycloneDX JSON file</label>
              <input
                id="sbom-file"
                className="text-input sbom-file-input"
                type="file"
                accept=".json,application/json"
                onChange={(event) => {
                  setSbomFile(event.currentTarget.files?.[0] ?? null);
                  setSbomLookup(null);
                }}
              />
              <div className="cyber-form-footer">
                <span className="provider-note"><i className="shield-icon">◇</i> Declared CVEs only · no package matching or scanning</span>
                <button className="primary-button" type="submit" disabled={!connected || sbomBusy || !sbomFile}>
                  {sbomBusy ? <><span className="spinner" /> Reviewing SBOM</> : <>Review SBOM <span aria-hidden="true">↗</span></>}
                </button>
              </div>
              <p className="cyber-egress-note">AURA parses this file locally in memory and does not save it. Only declared CVE IDs are sent to FIRST EPSS. NVD details are requested only after you click a per-CVE or batch lookup button.</p>
            </form>

            {sbomLookup && <div className="cyber-results" aria-live="polite">
              <div className="cyber-results-header">
                <div>
                  <div className="section-kicker">SBOM SUMMARY</div>
                  <p className="cyber-source-status">{sbomLookup.format} {sbomLookup.specVersion} · {sbomLookup.componentCount} components · {sbomLookup.cveCount} declared CVEs</p>
                  {sbomLookup.intelligence && <p className="cyber-source-status">KEV {sbomLookup.intelligence.kev.status} · EPSS {sbomLookup.intelligence.epss.status}</p>}
                </div>
                <div className="export-actions" aria-label="Download SBOM review">
                  <button type="button" onClick={() => downloadSbom("csv")}>↓ CSV</button>
                  <button type="button" onClick={() => downloadSbom("json")}>↓ JSON</button>
                </div>
              </div>
              <p className="coverage-note"><span>ⓘ</span> {sbomLookup.coverageNote}</p>
              {sbomLookup.vulnerabilities.length > 0 && <button className="nvd-lookup-button" type="button" disabled={!connected || nvdBusyCve !== null || nvdBatchBusy} onClick={() => void loadNvdBatch(sbomLookup.vulnerabilities.map((vulnerability) => vulnerability.cve))}>{nvdBatchBusy ? "Loading NVD details…" : `Load NVD details for all ${sbomLookup.vulnerabilities.length} declared CVEs`}</button>}
              {sbomLookup.vulnerabilities.length ? <div className="cyber-findings">
                {sbomLookup.vulnerabilities.map((declared) => {
                  const finding = sbomLookup.intelligence?.results.find((result) => result.cve === declared.cve);
                  const visibleRefs = declared.affectedBomRefs.slice(0, 50);
                  const visibleComponents = declared.affectedComponents.slice(0, 50);
                  return <article className="cyber-finding" key={declared.cve}>
                    <div className="cyber-finding-rank">{finding ? String(finding.priorityRank).padStart(2, "0") : "—"}</div>
                    <div className="cyber-finding-main">
                      <div className="cyber-finding-heading"><a href={`https://nvd.nist.gov/vuln/detail/${encodeURIComponent(declared.cve)}`} target="_blank" rel="noopener noreferrer">{declared.cve} ↗</a><span className={finding?.knownExploited ? "kev-badge kev-badge-active" : "kev-badge"}>{finding?.knownExploited ? "CISA KEV" : finding?.knownExploited === false ? "Not in KEV" : "KEV unavailable"}</span></div>
                      <p className="cyber-product">Affected BOM references: {visibleRefs.join(" · ") || "None declared"}{declared.affectedBomRefs.length > visibleRefs.length ? ` · and ${declared.affectedBomRefs.length - visibleRefs.length} more` : ""}</p>
                      {visibleComponents.map((component) => <p className="sbom-component" key={component.bomRef}><strong>{component.name || component.bomRef}</strong>{component.version ? ` · ${component.version}` : ""}{component.purl ? ` · ${component.purl}` : ""}</p>)}
                      {declared.affectedComponents.length > visibleComponents.length && <p className="sbom-component">Showing {visibleComponents.length} of {declared.affectedComponents.length} resolved components. Download the full JSON or CSV for the complete list.</p>}
                      {declared.affectedBomRefs.length > declared.affectedComponents.length && <p className="sbom-component">Some references did not resolve to a component in this file.</p>}
                      {finding && <div className="cyber-metrics"><span>EPSS <strong>{formatProbability(finding.epssScore)}</strong></span><span>Percentile <strong>{formatProbability(finding.epssPercentile)}</strong></span><span>Priority <strong>{finding.priorityRank}</strong></span></div>}
                      <button className="nvd-lookup-button" type="button" disabled={!connected || nvdBusyCve !== null || nvdBatchBusy} onClick={() => void loadNvd(declared.cve)}>{nvdBusyCve === declared.cve ? "Loading NVD…" : nvdLookups[declared.cve] ? "Refresh NVD details" : "Load NVD details"}</button>
                      {nvdLookups[declared.cve] && <NvdDetails data={nvdLookups[declared.cve]} />}
                    </div>
                  </article>;
                })}
              </div> : <div className="empty-results"><strong>No CVE IDs were declared in this SBOM</strong><p>AURA does not infer vulnerabilities from package names or versions.</p></div>}
            </div>}
          </section>
          {error && <div className="toast-error" role="alert"><span>!</span><p>{error}</p><button type="button" onClick={() => setError("")} aria-label="Dismiss error">×</button></div>}
          <footer className="page-footer"><span>© AURA AI · LOCAL RESEARCH WORKSPACE</span><span>Evidence linked to source · Search scope shown clearly</span></footer>
        </div>
      </main>
    </div>
  );
}
