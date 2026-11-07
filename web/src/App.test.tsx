import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("AURA research workspace", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("validates local API credentials without storing them", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ authenticated: true }), { status: 200 }),
    ).mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
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

  it("shows public coverage and responsible-use limits before a search", () => {
    render(<App />);
    expect(screen.getAllByText(/not a crawl of the entire internet/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Avoid doxxing, private-account access/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Start research/i })).toBeDisabled();
  });

  it("creates a job, renders linked evidence, and applies server-side filters", async () => {
    const source = {
      id: "source-1",
      provider: "brave",
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
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: "job-1", subject: "passkeys", mode: "GENERAL", status: "REQUESTED",
        createdAt: "2026-11-07T00:00:00Z", searchProvider: null, searchResultCount: 0,
      }), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: "job-1", query: "passkeys", status: "COMPLETED", provider: "brave",
        country: null, language: null, freshness: null, requestedLimit: 10,
        providerResultCount: 1, searchedAt: "2026-11-07T00:00:00Z", results: [source],
        coverageNote: "Partial provider coverage.",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: "job-1", query: "passkeys", status: "COMPLETED", provider: "brave",
        country: null, language: null, freshness: null, requestedLimit: 10,
        providerResultCount: 1, searchedAt: "2026-11-07T00:00:00Z", results: [source],
        coverageNote: "Partial provider coverage.",
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");

    fireEvent.change(screen.getByLabelText("Research question or topic"), { target: { value: "passkeys" } });
    fireEvent.click(screen.getByRole("button", { name: /Start research/ }));
    const resultLink = await screen.findByRole("link", { name: /Passkeys overview/ });
    expect(resultLink).toHaveAttribute("href", "https://example.com/passkeys");
    expect(resultLink).toHaveAttribute("rel", "noopener noreferrer");

    fireEvent.change(screen.getByLabelText("Filter by domain"), { target: { value: "example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));
    expect(fetchMock.mock.calls[4]?.[0]).toContain("host=example.com");
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
      .mockResolvedValueOnce(new Response(JSON.stringify({
        jobId: savedJob.id, query: savedJob.subject, status: "COMPLETED", provider: "brave",
        country: null, language: null, freshness: null, requestedLimit: 10,
        providerResultCount: 1, searchedAt: "2026-11-06T00:00:00Z", results: [savedSource],
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
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    expect(fetchMock.mock.calls[3]?.[1]).toMatchObject({ method: "DELETE" });
    expect(await screen.findByText("No saved briefs yet.")).toBeInTheDocument();
  });

  it("looks up only submitted CVE IDs and shows source attribution", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
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
    expect(JSON.parse(String(fetchMock.mock.calls[2]?.[1]?.body))).toEqual({ cves: ["CVE-2026-12345"] });
  });

  it("uploads a CycloneDX file as multipart and shows only its declared CVEs", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), { status: 200 }))
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
      }), { status: 200 }));

    render(<App />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /Connect to API/ }));
    await screen.findByText("Connected securely");
    const file = new File([JSON.stringify({ bomFormat: "CycloneDX" })], "inventory.json", { type: "application/json" });
    fireEvent.change(screen.getByLabelText("CycloneDX JSON file"), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: /Review SBOM/ }));

    expect(await screen.findByText("CVE-2026-12345 ↗")).toBeInTheDocument();
    expect(screen.getByText("demo")).toBeInTheDocument();
    const [url, options] = fetchMock.mock.calls[2]!;
    expect(url).toBe("/api/v1/cyber/sbom/lookup");
    expect(options?.body).toBeInstanceOf(FormData);
    expect((options?.body as FormData).get("file")).toBeInstanceOf(File);
    expect(new Headers(options?.headers).has("Content-Type")).toBe(false);
  });
});
