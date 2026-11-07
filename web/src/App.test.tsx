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
});
