import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  ApiError,
  createResearchJob,
  deleteResearchJob,
  downloadExport,
  lookupVulnerabilities,
  listResearchJobs,
  listSources,
  runResearch,
  validateConnection,
} from "./api";
import type {
  Credentials,
  ResearchJob,
  ResearchMode,
  ResearchSource,
  ResultFilters,
  SearchResponse,
  VulnerabilityFinding,
  VulnerabilityLookupResponse,
} from "./types";

const emptyFilters: ResultFilters = {
  kind: "",
  q: "",
  host: "",
  publishedFrom: "",
  publishedTo: "",
};

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Those API credentials were not accepted. Check your local .env values.";
    if (error.code === "SEARCH_PROVIDER_NOT_CONFIGURED") {
      return "Public search is not configured yet. Add your own AURA_BRAVE_API_KEY to .env, restart the stack, then retry this search.";
    }
    if (error.code === "SEARCH_PROVIDER_RATE_LIMITED") return "The search provider rate limited this request. Wait a little and try again.";
    if (error.code === "VALIDATION_FAILED") return "Enter one to 25 CVE IDs, such as CVE-2024-12345.";
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

export default function App() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [subject, setSubject] = useState("");
  const [mode, setMode] = useState<ResearchMode>("GENERAL");
  const [country, setCountry] = useState("");
  const [language, setLanguage] = useState("");
  const [freshness, setFreshness] = useState("");
  const [resultLimit, setResultLimit] = useState(10);
  const [jobs, setJobs] = useState<ResearchJob[]>([]);
  const [job, setJob] = useState<ResearchJob | null>(null);
  const [search, setSearch] = useState<SearchResponse | null>(null);
  const [sources, setSources] = useState<ResearchSource[]>([]);
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

  const credentials = useMemo<Credentials>(() => ({ username, password }), [username, password]);

  async function connect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setConnecting(true);
    setError("");
    try {
      await validateConnection(credentials);
      setJobs(await listResearchJobs(credentials));
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
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    try {
      const pendingJob = canRetry && job?.subject === subject.trim() && job.mode === mode ? job : null;
      if (!pendingJob) setJob(null);
      setCanRetry(false);
      const currentJob = pendingJob ?? await createResearchJob(credentials, subject.trim(), mode);
      setJob(currentJob);
      setJobs((current) => [currentJob, ...current.filter((item) => item.id !== currentJob.id)].slice(0, 25));
      const response = await runResearch(credentials, currentJob.id, {
        country,
        language,
        freshness,
        resultLimit,
      });
      setSearch(response);
      setSources(response.results);
      const completedJob = {
        ...currentJob,
        status: response.status,
        searchProvider: response.provider,
        searchResultCount: response.providerResultCount,
      };
      setJob(completedJob);
      setJobs((current) => current.map((item) => item.id === completedJob.id ? completedJob : item));
      setCanRetry(false);
    } catch (caught) {
      const apiError = caught instanceof ApiError ? caught : null;
      setCanRetry(apiError?.code === "SEARCH_PROVIDER_NOT_CONFIGURED");
      setError(errorMessage(caught));
    } finally {
      setSearching(false);
    }
  }

  async function openHistoryItem(item: ResearchJob) {
    setOpeningJobId(item.id);
    setError("");
    setJob(item);
    setSubject(item.subject);
    setMode(item.mode);
    setSearch(null);
    setSources([]);
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setCanRetry(item.status === "REQUESTED");
    try {
      if (item.status === "COMPLETED") {
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

  function downloadVulnerabilityLookup(format: "json" | "csv") {
    if (!vulnerabilityLookup) return;
    let content: string;
    let mimeType: string;
    if (format === "json") {
      content = JSON.stringify(vulnerabilityLookup, null, 2);
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
      content = [columns.map(([name]) => csvCell(name)).join(","), ...vulnerabilityLookup.results.map((finding) =>
        columns.map(([, value]) => csvCell(value(finding))).join(","),
      )].join("\r\n");
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

  function disconnect() {
    setConnected(false);
    setUsername("");
    setPassword("");
    setJobs([]);
    setJob(null);
    setSearch(null);
    setSources([]);
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

                  <div className="form-row">
                    <div className="field-group">
                      <label className="field-label" htmlFor="research-mode">Brief category</label>
                      <div className="select-wrap"><select id="research-mode" value={mode} onChange={(event) => setMode(event.target.value as ResearchMode)}>
                        <option value="GENERAL">General research</option>
                        <option value="CYBERSECURITY">Cybersecurity intelligence</option>
                      </select><span>⌄</span></div>
                    </div>
                    <div className="field-group">
                      <label className="field-label" htmlFor="freshness">Time window</label>
                      <div className="select-wrap"><select id="freshness" value={freshness} onChange={(event) => setFreshness(event.target.value)}>
                        <option value="">Any time</option>
                        <option value="pd">Past 24 hours</option>
                        <option value="pw">Past week</option>
                        <option value="pm">Past month</option>
                        <option value="py">Past year</option>
                      </select><span>⌄</span></div>
                    </div>
                    <div className="field-group field-limit">
                      <label className="field-label" htmlFor="result-limit">Results per source</label>
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
                    <span className="provider-note"><i className="shield-icon">◇</i> SafeSearch enabled · Public sources only</span>
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
                  {search && <div className="export-actions" aria-label="Download filtered results">
                    <button type="button" onClick={() => void exportResults("csv")} disabled={Boolean(exporting)}>{exporting === "csv" ? "Preparing…" : "↓ CSV"}</button>
                    <button type="button" onClick={() => void exportResults("json")} disabled={Boolean(exporting)}>{exporting === "json" ? "Preparing…" : "↓ JSON"}</button>
                    <button type="button" onClick={() => void exportResults("md")} disabled={Boolean(exporting)}>{exporting === "md" ? "Preparing…" : "↓ MD"}</button>
                  </div>}
                </div>

                {search && job && <>
                  <div className="research-meta">
                    <span className="meta-query"><span>↳</span> {search.query}</span>
                    <span className="meta-divider" />
                    <span className="meta-item"><i className="status-dot" /> {provider ?? "Provider pending"}</span>
                    <span className="meta-item">Updated {formatDate(search.searchedAt)}</span>
                  </div>
                  <form className="filter-bar" onSubmit={applyFilters}>
                    <label className="filter-search"><span aria-hidden="true">⌕</span><input aria-label="Search within results" placeholder="Search results" value={draftFilters.q} onChange={(event) => setDraftFilters({ ...draftFilters, q: event.target.value })} /></label>
                    <label className="filter-select"><span className="sr-only">Source type</span><select value={draftFilters.kind} onChange={(event) => setDraftFilters({ ...draftFilters, kind: event.target.value as ResultFilters["kind"] })}><option value="">All sources</option><option value="WEB">Web</option><option value="NEWS">News</option><option value="DISCUSSION">Discussions</option></select><span>⌄</span></label>
                    <label className="filter-host"><span aria-hidden="true">◎</span><input aria-label="Filter by domain" placeholder="Filter domain" value={draftFilters.host} onChange={(event) => setDraftFilters({ ...draftFilters, host: event.target.value })} /></label>
                    <details className="date-filter"><summary>Dates <span>⌄</span></summary><div className="date-filter-popover"><label>From<input type="date" value={draftFilters.publishedFrom} onChange={(event) => setDraftFilters({ ...draftFilters, publishedFrom: event.target.value })} /></label><label>To<input type="date" value={draftFilters.publishedTo} onChange={(event) => setDraftFilters({ ...draftFilters, publishedTo: event.target.value })} /></label></div></details>
                    <button className="filter-button" type="submit" disabled={filtering}>{filtering ? "…" : "Apply"}</button>
                  </form>
                  {search.coverageNote && <p className="coverage-note"><span>ⓘ</span> {search.coverageNote}</p>}
                  {sources.length > 0 ? <div className="source-list">{sources.map((source) => <article className="source-card" key={source.id}>
                    <div className="source-monogram" aria-hidden="true">{sourceInitial(source.domain)}</div>
                    <div className="source-content">
                      <div className="source-meta"><span className={`type-pill type-${source.kind.toLowerCase()}`}>{kindLabel(source.kind)}</span><span className="source-domain">{source.domain}</span><span className="source-date">{formatDate(source.publishedAt)}</span></div>
                      <h3><a href={source.url} target="_blank" rel="noopener noreferrer">{source.title}<span aria-hidden="true"> ↗</span></a></h3>
                      {source.description && <p>{source.description}</p>}
                      <div className="publisher-line">{source.publisher || source.domain}<span>·</span> Retrieved {formatDate(source.retrievedAt)}</div>
                    </div>
                    <span className="source-rank">{String(source.rank).padStart(2, "0")}</span>
                  </article>)}</div> : <div className="empty-results"><span>⌕</span><strong>No sources match these filters</strong><p>Try clearing a filter or widening the date range.</p></div>}
                </>}
                {!search && job && <div className="empty-results welcome-results"><span>{job.status === "FAILED" ? "!" : "◷"}</span><strong>{job.status === "FAILED" ? "This search did not finish" : job.status === "SEARCHING" ? "This search is still running" : "This brief has not been searched yet"}</strong><p>{job.status === "FAILED" ? `The saved failure code is ${job.failureCode ?? "unavailable"}. Start a new brief to try again.` : job.status === "SEARCHING" ? "Reload this brief in a moment to see whether the provider returned." : "If the provider key was just configured, press Start research to retry this saved brief."}</p></div>}
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
                <div className="scope-provider"><span className="provider-mark">B</span><div><strong>Brave Search</strong><span>Web · News · Discussions</span></div><span className="scope-live"><i /> {connected ? "API LINKED" : "LOCAL API"}</span></div>
                <div className="scope-divider" />
                <p className="scope-copy">AURA searches the configured provider and keeps each returned link. The brief category is saved as metadata; both categories use the same search scope today. Coverage is partial, not a crawl of the entire internet.</p>
                <p className="scope-copy">Search needs your own Brave key in the local .env file.</p>
                <div className="scope-foot"><span>⌁</span> Strict SafeSearch is always on</div>
              </section>

              <section className="responsible-card">
                <span className="responsible-symbol">✳</span>
                <div><strong>Research responsibly</strong><p>Use public-interest sources. Avoid doxxing, private-account access, and sensitive-trait profiling.</p></div>
              </section>
            </aside>
          </div>
          <section className="panel cyber-panel" id="cyber-intelligence" aria-labelledby="cyber-title">
            <div className="panel-heading">
              <div className="step-number">03</div>
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
                  </div>
                </article>)}
              </div> : <div className="empty-results"><strong>No data matched those CVE IDs</strong><p>Check the identifier or retry when the public data sources are available.</p></div>}
            </div>}
          </section>
          {error && <div className="toast-error" role="alert"><span>!</span><p>{error}</p><button type="button" onClick={() => setError("")} aria-label="Dismiss error">×</button></div>}
          <footer className="page-footer"><span>© AURA AI · LOCAL RESEARCH WORKSPACE</span><span>Evidence linked to source · Search scope shown clearly</span></footer>
        </div>
      </main>
    </div>
  );
}
