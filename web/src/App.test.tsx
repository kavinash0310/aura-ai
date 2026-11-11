import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("AURA research workspace", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("validates local API credentials without storing them", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ authenticated: true }), { status: 200 }),
    ).mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }));
    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "local-owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "local-secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await waitFor(() => expect(screen.getByText("Connected securely")).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/session", expect.objectContaining({ cache: "no-store", credentials: "omit" }));
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it("lets the owner review a local-model search suggestion before searching", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        generatedAt: "2026-11-08T00:00:00Z",
        provider: "ollama",
        model: "local-test-model",
        suggestions: ["official public response"],
        modelRun: { promptVersion: "research-query-plan-v1", durationMs: 100, promptTokenCount: 10, outputTokenCount: 5 },
        note: "Draft only; no search providers contacted.",
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    fireEvent.change(screen.getByLabelText("Research question or topic"), { target: { value: "topic" } });

    fireEvent.click(screen.getByRole("button", { name: "Suggest with local AI" }));
    expect(await screen.findByText("official public response")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[3]?.[0]).toBe("/api/v1/research/query-plan");
    expect(JSON.parse(String(fetchMock.mock.calls[3]?.[1]?.body))).toEqual({ topic: "topic" });

    fireEvent.click(screen.getByRole("button", { name: "Add suggestion: official public response" }));
    expect(screen.getByLabelText("Additional search query 1")).toHaveValue("official public response");
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("imports a private text file and searches it with a stable character citation", async () => {
    const document = {
      id: "doc-1", title: "Local notes", originalFilename: "notes.md", mediaType: "text/markdown; charset=utf-8",
      contentHash: "a".repeat(64), characterCount: 32, uploadedAt: "2026-11-07T00:00:00Z", content: null,
    };
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(document), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{
        documentId: "doc-1", title: "Local notes", originalFilename: "notes.md", contentHash: "a".repeat(64),
        uploadedAt: "2026-11-07T00:00:00Z", snippet: "AURA notes for citation testing.",
        startOffset: 0, endOffset: 4, citation: "aura-doc://doc-1#chars=0-4",
      }]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...document, content: "AURA notes for citation testing." }), { status: 200 }));

    render(<App />);
    expect(screen.getByLabelText("Choose a .txt, .md, .pdf, .csv, or .json file"))
      .toHaveAttribute("accept", expect.stringContaining(".csv"));
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");

    const file = new File(["AURA notes for citation testing."], "notes.md", { type: "text/markdown" });
    fireEvent.change(screen.getByLabelText("Choose a .txt, .md, .pdf, .csv, or .json file"), { target: { files: [file] } });
    fireEvent.change(screen.getByLabelText("Title (optional)"), { target: { value: "Local notes" } });
    fireEvent.click(screen.getByRole("button", { name: /Import document/ }));
    await screen.findByText(/notes\.md · 32 characters/);
    const uploadCall = fetchMock.mock.calls[3]!;
    expect(uploadCall[0]).toBe("/api/v1/documents");
    expect(uploadCall[1]?.body).toBeInstanceOf(FormData);
    expect((uploadCall[1]?.body as FormData).get("file")).toBeInstanceOf(File);

    fireEvent.change(screen.getByLabelText("Search documents"), { target: { value: "AURA" } });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("aura-doc://doc-1#chars=0-4")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Local notes" })[0]!);
    expect(await screen.findByRole("button", { name: "Download" })).toBeInTheDocument();
    expect(fetchMock.mock.calls[5]?.[0]).toBe("/api/v1/documents/doc-1");
  });

  it("shows public coverage and responsible-use limits before a search", () => {
    render(<App />);
    expect(screen.getAllByText(/not a crawl of the entire internet/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Avoid doxxing, private-account access/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Start research/i })).toBeDisabled();
  });

  it("explains the Bluesky provider without asking to connect a social account", () => {
    render(<App />);
    fireEvent.change(screen.getByLabelText("Search source"), { target: { value: "bluesky" } });
    expect(screen.getByText("Bluesky Public Search")).toBeInTheDocument();
    expect(screen.getByText("Public posts · no account connection")).toBeInTheDocument();
    expect(screen.getByText(/Public Bluesky posts only · no private feeds or connected accounts/i)).toBeInTheDocument();
  });

  it("creates a job, renders linked evidence, and applies server-side filters", async () => {
    const source = {
      id: "source-1",
      provider: "gdelt",
      searchQuery: "passkeys",
      kind: "WEB",
      rank: 1,
      title: "Passkeys overview",
      url: "https://example.com/passkeys",
      domain: "example.com",
      description: "A public overview of passkeys.",
      publisher: "Example",
      publishedAt: null,
      retrievedAt: "2026-11-07T00:00:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: "job-1", subject: "passkeys", mode: "GENERAL", status: "REQUESTED",
        createdAt: "2026-11-07T00:00:00Z", searchProvider: null, searchResultCount: 0,
      }), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: "job-1", query: "passkeys", status: "PARTIAL", provider: "all",
        country: null, language: null, freshness: null, requestedLimit: 10,
        providerResultCount: 1, searchedAt: "2026-11-07T00:00:00Z", results: [source],
        providerRuns: [
          { provider: "gdelt", status: "SUCCEEDED", resultCount: 1, failureCode: null, startedAt: "2026-11-07T00:00:00Z", completedAt: "2026-11-07T00:00:01Z" },
          { provider: "bluesky", status: "FAILED", resultCount: 0, failureCode: "SEARCH_PROVIDER_RATE_LIMITED", startedAt: "2026-11-07T00:00:01Z", completedAt: "2026-11-07T00:00:02Z" },
        ],
        coverageNote: "Partial coverage: some providers did not return results.",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: "job-1", query: "passkeys", status: "PARTIAL", provider: "all",
        country: null, language: null, freshness: null, requestedLimit: 10,
        providerResultCount: 1, searchedAt: "2026-11-07T00:00:00Z", results: [source],
        providerRuns: [
          { provider: "gdelt", status: "SUCCEEDED", resultCount: 1, failureCode: null, startedAt: "2026-11-07T00:00:00Z", completedAt: "2026-11-07T00:00:01Z" },
          { provider: "bluesky", status: "FAILED", resultCount: 0, failureCode: "SEARCH_PROVIDER_RATE_LIMITED", startedAt: "2026-11-07T00:00:01Z", completedAt: "2026-11-07T00:00:02Z" },
        ],
        coverageNote: "Partial coverage: some providers did not return results.",
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");

    fireEvent.change(screen.getByLabelText("Research question or topic"), { target: { value: "passkeys" } });
    fireEvent.change(screen.getByLabelText("Search source"), { target: { value: "all" } });
    fireEvent.click(screen.getByRole("button", { name: /Add search angle/ }));
    fireEvent.change(screen.getByLabelText("Additional search query 1"), { target: { value: "passkey adoption timeline" } });
    fireEvent.click(screen.getByRole("button", { name: /Start research/ }));
    const resultLink = await screen.findByRole("link", { name: /Passkeys overview/ });
    const searchCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/search"));
    expect(JSON.parse(String(searchCall?.[1]?.body))).toMatchObject({
      queries: ["passkeys", "passkey adoption timeline"],
      provider: "all",
    });
    expect(resultLink).toHaveAttribute("href", "https://example.com/passkeys");
    expect(resultLink).toHaveAttribute("rel", "noopener noreferrer");
    expect(screen.getAllByText("GDELT").length).toBeGreaterThan(0);
    expect(screen.getByText("Partial results")).toBeInTheDocument();
    expect(screen.getByText(/Bluesky failed · 0 results · SEARCH_PROVIDER_RATE_LIMITED/)).toBeInTheDocument();
    const searchRequest = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/v1/research-jobs/job-1/search"));
    expect(JSON.parse(String(searchRequest?.[1]?.body))).toMatchObject({ provider: "all" });

    fireEvent.change(screen.getByLabelText("Filter by domain"), { target: { value: "example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(6));
    expect(fetchMock.mock.calls[5]?.[0]).toContain("host=example.com");

    fireEvent.change(screen.getByRole("combobox", { name: "Filter by provider" }), { target: { value: "gdelt" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(7));
    expect(fetchMock.mock.calls[6]?.[0]).toContain("provider=gdelt");
  });

  it("reads one selected HTTPS source only after the user requests it", async () => {
    const source = {
      id: "source-page-1", provider: "brave", kind: "WEB", rank: 1,
      title: "Public research page", url: "https://example.com/report", domain: "example.com",
      description: "A short provider snippet.", publisher: "Example", publishedAt: null,
      retrievedAt: "2026-11-08T00:00:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: "job-page", subject: "public research", mode: "GENERAL", status: "REQUESTED",
        createdAt: "2026-11-08T00:00:00Z", searchProvider: null, searchResultCount: 0,
      }), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: "job-page", query: "public research", status: "COMPLETED", provider: "brave",
        country: null, language: null, freshness: null, requestedLimit: 10, providerResultCount: 1,
        searchedAt: "2026-11-08T00:00:00Z", results: [source], providerRuns: [], coverageNote: "Public-source coverage is partial.",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        readId: "page-read-1", sourceId: source.id, sourceUrl: source.url, host: source.domain, title: "Full page title",
        contentHash: "b".repeat(64), characterCount: 28, truncated: false,
        fetchedAt: "2026-11-08T00:01:00Z", text: "Selected page evidence text.",
        coverageNote: "Held in browser memory; not stored or sent to the model.",
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    fireEvent.change(screen.getByLabelText("Research question or topic"), { target: { value: "public research" } });
    fireEvent.click(screen.getByRole("button", { name: /Start research/ }));
    await screen.findByRole("link", { name: /Public research page/ });

    fireEvent.click(screen.getByRole("button", { name: "Read page" }));
    expect(await screen.findByText("Selected page evidence text.")).toBeInTheDocument();
    expect(screen.getByText(/Held in browser memory/)).toBeInTheDocument();
    expect(fetchMock.mock.calls[5]?.[0]).toBe("/api/v1/research-jobs/job-page/sources/source-page-1/page");
    expect(fetchMock.mock.calls[5]?.[1]?.method).toBe("POST");

    fireEvent.change(screen.getByLabelText("Find exact text in this page (case-sensitive)"), { target: { value: "evidence" } });
    expect(screen.getByText("Character offset 14 · length 8")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "↓ Download matching excerpts" })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });

  it("reopens a saved brief and deletes it after an explicit confirmation", async () => {
    const savedJob = {
      id: "job-old", subject: "old research brief", mode: "GENERAL", status: "COMPLETED",
      createdAt: "2026-11-06T00:00:00Z", searchProvider: "brave", searchResultCount: 1,
    };
    const savedSource = {
      id: "source-old", provider: "brave", kind: "WEB", rank: 1,
      title: "Saved source", url: "https://example.org/saved", domain: "example.org",
      description: "A saved public source.", publisher: "Example", publishedAt: null,
      retrievedAt: "2026-11-06T00:00:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([savedJob]), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: savedJob.id, query: savedJob.subject, status: "COMPLETED", provider: "brave",
        country: null, language: null, freshness: null, requestedLimit: 10,
        providerResultCount: 1, searchedAt: "2026-11-06T00:00:00Z", results: [savedSource],
        providerRuns: [{ provider: "brave", status: "SUCCEEDED", resultCount: 1, failureCode: null, startedAt: "2026-11-06T00:00:00Z", completedAt: "2026-11-06T00:00:01Z" }],
        coverageNote: "Partial provider coverage.",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    const historyButtons = await screen.findAllByRole("button", { name: /old research brief completed/ });
    fireEvent.click(historyButtons[0]!);
    expect(await screen.findByRole("link", { name: /Saved source/ })).toHaveAttribute("href", "https://example.org/saved");

    fireEvent.click(screen.getAllByRole("button", { name: "Delete old research brief" })[0]!);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));
    expect(fetchMock.mock.calls[4]?.[1]).toMatchObject({ method: "DELETE" });
    expect(await screen.findByText("No saved briefs yet.")).toBeInTheDocument();
  });

  it("looks up only submitted CVE IDs and shows source attribution", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:00Z",
        coverageNote: "Read-only CVE lookup. This is not a complete asset-specific risk rating.",
        kev: { status: "AVAILABLE", retrievedAt: "2026-11-07T00:00:00Z", dataAsOf: "2026-11-07", sourceUrl: "https://github.com/CISAGov/kev-data", note: "" },
        epss: { status: "AVAILABLE", retrievedAt: "2026-11-07T00:00:00Z", dataAsOf: "2026-11-07", sourceUrl: "https://api.first.org/epss/", note: "" },
        results: [{
          cve: "CVE-2026-12345", priorityRank: 1, knownExploited: true, vendorProject: "Example", product: "Server",
          vulnerabilityName: "Example issue", dateAdded: "2026-11-06", dueDate: "2026-11-20", requiredAction: "Apply the patch.",
          knownRansomwareCampaignUse: "Unknown", epssScore: 0.8, epssPercentile: 0.95, epssScoreDate: "2026-11-07",
          priorityRationale: "CISA KEV-listed: CISA reports known exploitation in the wild.",
          cisaUrl: "https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
          nvdUrl: "https://nvd.nist.gov/vuln/detail/CVE-2026-12345",
          epssUrl: "https://api.first.org/data/v1/epss?cve=CVE-2026-12345",
        }],
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    fireEvent.change(screen.getByLabelText("CVE IDs"), { target: { value: "cve-2026-12345, CVE-2026-12345" } });
    fireEvent.click(screen.getByRole("button", { name: /Check vulnerabilities/ }));

    expect(await screen.findByText("CISA KEV-listed: CISA reports known exploitation in the wild.")).toBeInTheDocument();
    expect(screen.getByText("80.0%")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "CISA catalog" })).toHaveAttribute("href", "https://www.cisa.gov/known-exploited-vulnerabilities-catalog");
    expect(JSON.parse(String(fetchMock.mock.calls[3]?.[1]?.body))).toEqual({ cves: ["CVE-2026-12345"] });
  });

  it("uploads a CycloneDX file as multipart and shows only its declared CVEs", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:00Z",
        format: "CycloneDX JSON",
        specVersion: "1.7",
        componentCount: 1,
        cveCount: 1,
        vulnerabilities: [{
          cve: "CVE-2026-12345",
          affectedBomRefs: ["pkg:npm/demo@1.0"],
          affectedComponents: [{ bomRef: "pkg:npm/demo@1.0", name: "demo", version: "1.0", purl: "pkg:npm/demo@1.0" }],
        }],
        intelligence: null,
        coverageNote: "Only CVEs declared in the SBOM are checked.",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:01Z",
        results: [{
          requestedAt: "2026-11-07T00:00:01Z", cve: "CVE-2026-12345", status: "AVAILABLE",
          retrievedAt: "2026-11-07T00:00:01Z", sourceUrl: "https://nvd.nist.gov/vuln/detail/CVE-2026-12345",
          note: "NVD data is evidence, not proof that an asset is affected.", publishedAt: null, lastModifiedAt: null,
          vulnerabilityStatus: "Analyzed", description: "Batch NVD vulnerability description.", cvssVersion: null,
          cvssScore: null, cvssSeverity: null, cvssVector: null, cwes: [], applicability: [],
          applicabilityTruncated: false, references: [], referencesTruncated: false,
        }],
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    const file = new File([JSON.stringify({ bomFormat: "CycloneDX" })], "inventory.json", { type: "application/json" });
    fireEvent.change(screen.getByLabelText("CycloneDX, SPDX, or SARIF JSON file"), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: /Review file/ }));

    expect(await screen.findByText("CVE-2026-12345 ↗")).toBeInTheDocument();
    expect(screen.getByText("demo")).toBeInTheDocument();
    const [url, options] = fetchMock.mock.calls[3]!;
    expect(url).toBe("/api/v1/cyber/sbom/lookup");
    expect(options?.body).toBeInstanceOf(FormData);
    expect((options?.body as FormData).get("file")).toBeInstanceOf(File);
    expect(new Headers(options?.headers).has("Content-Type")).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "Load NVD details for all 1 declared CVEs" }));
    expect(await screen.findByText("Batch NVD vulnerability description.")).toBeInTheDocument();
    expect(fetchMock.mock.calls[4]?.[0]).toBe("/api/v1/cyber/vulnerabilities/nvd/lookup/batch");
    expect(JSON.parse(String(fetchMock.mock.calls[4]?.[1]?.body))).toEqual({ cves: ["CVE-2026-12345"] });
  });

  it("loads NVD details only after an explicit single-CVE request", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:00Z",
        coverageNote: "Read-only exact CVE lookup.",
        kev: { status: "AVAILABLE", retrievedAt: "2026-11-07T00:00:00Z", dataAsOf: "2026-11-07", sourceUrl: "https://github.com/CISAGov/kev-data", note: "" },
        epss: { status: "AVAILABLE", retrievedAt: "2026-11-07T00:00:00Z", dataAsOf: "2026-11-07", sourceUrl: "https://api.first.org/epss/", note: "" },
        results: [{
          cve: "CVE-2026-12345", priorityRank: 1, knownExploited: false, vendorProject: null, product: null,
          vulnerabilityName: null, dateAdded: null, dueDate: null, requiredAction: null,
          knownRansomwareCampaignUse: null, epssScore: 0.2, epssPercentile: 0.6, epssScoreDate: "2026-11-07",
          priorityRationale: "Not listed in the returned KEV snapshot; sorted by EPSS estimate.",
          cisaUrl: "https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
          nvdUrl: "https://nvd.nist.gov/vuln/detail/CVE-2026-12345", epssUrl: "https://api.first.org/data/v1/epss?cve=CVE-2026-12345",
        }],
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:01Z", cve: "CVE-2026-12345", status: "AVAILABLE",
        retrievedAt: "2026-11-07T00:00:01Z", sourceUrl: "https://nvd.nist.gov/vuln/detail/CVE-2026-12345",
        note: "NVD describes severity and CPE applicability; these are not proof that a user's software is affected.",
        publishedAt: "2026-11-06T00:00:00.000", lastModifiedAt: "2026-11-07T00:00:00.000",
        vulnerabilityStatus: "Analyzed", description: "Example vulnerability description.", cvssVersion: "3.1",
        cvssScore: 8.2, cvssSeverity: "HIGH", cvssVector: "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
        cwes: ["CWE-79"], applicability: [{ criteria: "cpe:2.3:a:example:server:*:*:*:*:*:*:*:*", vulnerable: true,
          versionStartIncluding: null, versionStartExcluding: null, versionEndIncluding: null, versionEndExcluding: null }],
        applicabilityTruncated: false, references: [], referencesTruncated: false,
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    fireEvent.change(screen.getByLabelText("CVE IDs"), { target: { value: "CVE-2026-12345" } });
    fireEvent.click(screen.getByRole("button", { name: /Check vulnerabilities/ }));
    await screen.findByRole("button", { name: "Load NVD details" });
    expect(fetchMock).toHaveBeenCalledTimes(4);

    fireEvent.click(screen.getByRole("button", { name: "Load NVD details" }));
    expect(await screen.findByText("8.2 HIGH")).toBeInTheDocument();
    expect(screen.getByText("Example vulnerability description.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View NVD record ↗" })).toHaveAttribute("href", "https://nvd.nist.gov/vuln/detail/CVE-2026-12345");
    expect(JSON.parse(String(fetchMock.mock.calls[4]?.[1]?.body))).toEqual({ cve: "CVE-2026-12345" });
  });

  it("loads reviewed GitHub advisories only after an explicit exact-CVE request", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:00Z",
        coverageNote: "Read-only exact CVE lookup.",
        kev: { status: "AVAILABLE", retrievedAt: "2026-11-07T00:00:00Z", dataAsOf: "2026-11-07", sourceUrl: "https://github.com/CISAGov/kev-data", note: "" },
        epss: { status: "AVAILABLE", retrievedAt: "2026-11-07T00:00:00Z", dataAsOf: "2026-11-07", sourceUrl: "https://api.first.org/epss/", note: "" },
        results: [{
          cve: "CVE-2026-12345", priorityRank: 1, knownExploited: false, vendorProject: null, product: null,
          vulnerabilityName: null, dateAdded: null, dueDate: null, requiredAction: null,
          knownRansomwareCampaignUse: null, epssScore: 0.2, epssPercentile: 0.6, epssScoreDate: "2026-11-07",
          priorityRationale: "Not listed in the returned KEV snapshot; sorted by EPSS estimate.",
          cisaUrl: "https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
          nvdUrl: "https://nvd.nist.gov/vuln/detail/CVE-2026-12345", epssUrl: "https://api.first.org/data/v1/epss?cve=CVE-2026-12345",
        }],
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:01Z", cve: "CVE-2026-12345", status: "AVAILABLE",
        retrievedAt: "2026-11-07T00:00:01Z", advisoriesTruncated: false,
        note: "GitHub-reviewed advisory evidence is shown as published.",
        advisories: [{
          ghsaId: "GHSA-7fhm-mqm4-2wp7", cveId: "CVE-2026-12345",
          sourceUrl: "https://github.com/advisories/GHSA-7FHM-MQM4-2WP7", type: "reviewed", severity: "HIGH",
          summary: "Example GitHub advisory", description: "Reviewed advisory details.",
          publishedAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-02T00:00:00Z", reviewedAt: "2026-01-02T00:00:00Z", withdrawnAt: "",
          cvssV3: { vector: "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H", score: 9.8 },
          cvssV4: { vector: "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N", score: 9.3 },
          cwes: ["CWE-79"], cwesTruncated: false, affectedPackages: [{ ecosystem: "npm", name: "example", vulnerableVersionRange: "< 2.0.0", firstPatchedVersion: "2.0.0" }],
          affectedPackagesTruncated: false, references: [{ url: "https://github.com/example/project/security/advisories/GHSA-7fhm-mqm4-2wp7" }], referencesTruncated: false,
        }],
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    fireEvent.change(screen.getByLabelText("CVE IDs"), { target: { value: "CVE-2026-12345" } });
    fireEvent.click(screen.getByRole("button", { name: /Check vulnerabilities/ }));
    await screen.findByRole("button", { name: "Load GitHub advisories" });
    expect(fetchMock).toHaveBeenCalledTimes(4);

    fireEvent.click(screen.getByRole("button", { name: "Load GitHub advisories" }));
    expect(await screen.findByText("Reviewed advisory details.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View GHSA-7fhm-mqm4-2wp7 ↗" })).toHaveAttribute("href", "https://github.com/advisories/GHSA-7FHM-MQM4-2WP7");
    expect(fetchMock.mock.calls[4]?.[0]).toBe("/api/v1/cyber/vulnerabilities/github-advisories/lookup");
    expect(JSON.parse(String(fetchMock.mock.calls[4]?.[1]?.body))).toEqual({ cve: "CVE-2026-12345" });
  });

  it("correlates OSV package PURLs with SBOM identities locally without deciding version exposure", async () => {
    const downloads: Blob[] = [];
    vi.stubGlobal("URL", {
      createObjectURL: (blob: Blob) => { downloads.push(blob); return "blob:aura-sbom"; },
      revokeObjectURL: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:00Z", format: "CycloneDX JSON", specVersion: "1.7",
        componentCount: 1, cveCount: 1,
        vulnerabilities: [{
          cve: "CVE-2026-12345", affectedBomRefs: ["pkg:npm/demo@1.2.0"],
          affectedComponents: [{ bomRef: "pkg:npm/demo@1.2.0", name: "demo", version: "1.2.0", purl: "pkg:npm/demo@1.2.0" }],
        }],
        intelligence: null, coverageNote: "Only CVEs declared in the SBOM are checked.",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestedAt: "2026-11-07T00:00:01Z", cve: "CVE-2026-12345", status: "AVAILABLE",
        retrievedAt: "2026-11-07T00:00:01Z", sourceUrl: "https://osv.dev/vulnerability/CVE-2026-12345",
        osvId: "GHSA-7fhm-mqm4-2wp7", summary: "Example package advisory", details: "Advisory details.",
        publishedAt: "2026-01-01T00:00:00Z", modifiedAt: "2026-01-02T00:00:00Z", withdrawnAt: null,
        aliases: ["CVE-2026-12345"], affectedPackages: [{
          name: "demo", ecosystem: "npm", purl: "pkg:npm/demo",
          affectedVersions: [], affectedVersionsTruncated: false,
          versionRanges: ["SEMVER · introduced 1.0.0 · fixed 2.0.0"],
          versionRangeDetails: [{ type: "SEMVER", events: [
            { introduced: "1.0.0", fixed: null, lastAffected: null, limit: null, validShape: true },
            { introduced: null, fixed: "2.0.0", lastAffected: null, limit: null, validShape: true },
          ] }],
          versionRangesTruncated: false,
        }],
        affectedPackagesTruncated: false, references: [], referencesTruncated: false,
        note: "Source evidence only.",
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    const file = new File([JSON.stringify({ bomFormat: "CycloneDX" })], "inventory.json", { type: "application/json" });
    fireEvent.change(screen.getByLabelText("CycloneDX, SPDX, or SARIF JSON file"), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: /Review file/ }));
    await screen.findByRole("button", { name: "Load OSV advisory" });
    expect(fetchMock).toHaveBeenCalledTimes(4);

    fireEvent.click(screen.getByRole("button", { name: "Load OSV advisory" }));
    expect(await screen.findByText(/exact OSV affected-version strings for any ecosystem/)).toBeInTheDocument();
    expect(screen.getByText(/inventory version 1\.2\.0/)).toBeInTheDocument();
    expect(screen.getByText(/Affected by supported OSV version evidence/)).toBeInTheDocument();
    expect(screen.getByText(/Evidence: SEMVER · introduced 1\.0\.0 · fixed 2\.0\.0/)).toBeInTheDocument();
    expect(JSON.parse(String(fetchMock.mock.calls[4]?.[1]?.body))).toEqual({ cve: "CVE-2026-12345" });

    fireEvent.click(screen.getByRole("button", { name: "↓ JSON" }));
    expect(fetchMock).toHaveBeenCalledTimes(5);
    const downloadedText = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(downloads[0]!);
    });
    const downloadedJson = JSON.parse(downloadedText);
    expect(downloadedJson.osvPackageCorrelations["CVE-2026-12345"].matchBasis).toBe("EXACT_PURL_IDENTITY_AFTER_REMOVING_VERSION");
    expect(downloadedJson.osvPackageCorrelations["CVE-2026-12345"].versionAssessmentPolicy)
      .toBe("EXACT_OSV_VERSIONS_ANY_ECOSYSTEM_NPM_GO_SEMVER_2_RANGES");
    expect(downloadedJson.osvPackageCorrelations["CVE-2026-12345"].matches[0].components[0].version).toBe("1.2.0");
    expect(downloadedJson.osvPackageCorrelations["CVE-2026-12345"].matches[0].versionAssessments[0].status).toBe("AFFECTED");
    expect(downloadedJson.osvPackageCorrelations["CVE-2026-12345"].limitation).toContain("not proof of safety");

    fireEvent.click(screen.getByRole("button", { name: "↓ CSV" }));
    const downloadedCsv = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(downloads[1]!);
    });
    expect(downloadedCsv).toContain('"osvPurlIdentityMatchBasis","osvVersionAssessmentPolicy","osvInventoryVersionAssessments"');
    expect(downloadedCsv).toContain('"EXACT_PURL_IDENTITY_AFTER_REMOVING_VERSION","EXACT_OSV_VERSIONS_ANY_ECOSYSTEM_NPM_GO_SEMVER_2_RANGES","pkg:npm/demo@1.2.0:AFFECTED"');
  });

  it("generates a report from visible source IDs and downloads a source-linked JSON snapshot", async () => {
    const reportBlobs: Blob[] = [];
    vi.stubGlobal("URL", {
      createObjectURL: (blob: Blob) => { reportBlobs.push(blob); return "blob:aura-report"; },
      revokeObjectURL: vi.fn(),
    });
    const clickAnchor = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const source = {
      id: "source-report-1", provider: "brave", kind: "NEWS", rank: 1,
      title: "Passkey adoption report", url: "https://example.com/passkeys", domain: "example.com",
      description: "A provider summary about passkey adoption.", publisher: "Example News",
      publishedAt: null, retrievedAt: "2026-11-07T00:00:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: "job-report", subject: "passkey adoption", mode: "GENERAL", status: "REQUESTED",
        createdAt: "2026-11-07T00:00:00Z", searchProvider: null, searchResultCount: 0,
      }), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: "job-report", query: "passkey adoption", status: "COMPLETED", provider: "brave",
        country: null, language: null, freshness: null, requestedLimit: 10, providerResultCount: 1,
        searchedAt: "2026-11-07T00:00:00Z", results: [source],
        providerRuns: [{ provider: "brave", status: "SUCCEEDED", resultCount: 1, failureCode: null, startedAt: "2026-11-07T00:00:00Z", completedAt: "2026-11-07T00:00:01Z" }],
        coverageNote: "Partial provider coverage.",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        generatedAt: "2026-11-07T00:01:00Z", provider: "ollama", model: "test-model",
        title: "Passkey adoption", summary: "The selected source describes adoption.",
        summarySourceIds: [source.id], findings: [{ text: "A report covers passkeys.", sourceIds: [source.id] }],
        limitations: ["Only one source was selected."],
        citations: [{ id: source.id, title: source.title, url: source.url, domain: source.domain, kind: source.kind }],
        coverageNote: "Model-generated; pages were not fetched.",
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    fireEvent.change(screen.getByLabelText("Research question or topic"), { target: { value: "passkey adoption" } });
    fireEvent.click(screen.getByRole("button", { name: /Start research/ }));
    await screen.findByRole("link", { name: /Passkey adoption report/ });
    fireEvent.click(screen.getByRole("button", { name: "✳ Report (1)" }));

    expect(await screen.findByRole("heading", { name: "Passkey adoption" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "[1] Passkey adoption report · example.com" })).toHaveAttribute("href", source.url);
    expect(JSON.parse(String(fetchMock.mock.calls[5]?.[1]?.body))).toEqual({ sourceIds: [source.id] });

    vi.spyOn(window, "setTimeout").mockImplementation(() => 1);
    fireEvent.click(screen.getByRole("button", { name: "↓ Download JSON" }));
    expect(clickAnchor).toHaveBeenCalledOnce();
    const downloadedBlob = reportBlobs[0]!;
    expect(downloadedBlob).toBeInstanceOf(Blob);
    expect(downloadedBlob.type).toBe("application/json;charset=utf-8");
    const downloadedText = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(downloadedBlob);
    });
    const downloaded = JSON.parse(downloadedText);
    expect(downloaded).toMatchObject({
      schemaVersion: 1,
      research: { jobId: "job-report", query: "passkey adoption", provider: "brave" },
      evidence: [{ sourceId: source.id, provider: "brave", description: source.description }],
      report: { model: "test-model", summarySourceIds: [source.id] },
    });
    expect(downloaded.report).not.toHaveProperty("pageText");
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });
});
