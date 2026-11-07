import type {
  Credentials,
  ResearchJob,
  ResearchMode,
  ResearchSource,
  ResultFilters,
  SearchResponse,
} from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function authHeader(credentials: Credentials): string {
  const raw = new TextEncoder().encode(`${credentials.username}:${credentials.password}`);
  const binary = Array.from(raw, (byte) => String.fromCharCode(byte)).join("");
  return `Basic ${btoa(binary)}`;
}

async function request<T>(
  path: string,
  credentials: Credentials,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", authHeader(credentials));
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");

  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers,
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
    });
  } catch {
    throw new ApiError("AURA API is unreachable. Check that the local stack is running.", 0);
  }

  if (!response.ok) {
    let body: { detail?: string; code?: string } = {};
    try {
      body = (await response.json()) as typeof body;
    } catch {
      // Use a stable fallback when a proxy or server returns a non-JSON error.
    }
    throw new ApiError(
      body.detail ?? `The API request failed (${response.status}).`,
      response.status,
      body.code,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const jobPath = (id: string) => `/api/v1/research-jobs/${encodeURIComponent(id)}`;

export async function validateConnection(credentials: Credentials): Promise<void> {
  await request<{ authenticated: boolean }>("/api/v1/session", credentials);
}

export function createResearchJob(
  credentials: Credentials,
  subject: string,
  mode: ResearchMode,
): Promise<ResearchJob> {
  return request<ResearchJob>("/api/v1/research-jobs", credentials, {
    method: "POST",
    body: JSON.stringify({ subject, mode }),
  });
}

export function listResearchJobs(credentials: Credentials, limit = 25): Promise<ResearchJob[]> {
  return request<ResearchJob[]>(`/api/v1/research-jobs?limit=${limit}`, credentials);
}

export function deleteResearchJob(credentials: Credentials, id: string): Promise<void> {
  return request<void>(jobPath(id), credentials, { method: "DELETE" });
}

export function runResearch(
  credentials: Credentials,
  id: string,
  options: { country: string; language: string; freshness: string; resultLimit: number },
): Promise<SearchResponse> {
  return request<SearchResponse>(`${jobPath(id)}/search`, credentials, {
    method: "POST",
    body: JSON.stringify({
      country: options.country || null,
      language: options.language || null,
      freshness: options.freshness || null,
      resultLimit: options.resultLimit,
    }),
  });
}

function filterParams(filters: ResultFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.kind) params.set("kind", filters.kind);
  if (filters.q.trim()) params.set("q", filters.q.trim());
  if (filters.host.trim()) params.set("host", filters.host.trim());
  if (filters.publishedFrom) params.set("publishedFrom", filters.publishedFrom);
  if (filters.publishedTo) params.set("publishedTo", filters.publishedTo);
  return params;
}

export async function listSources(
  credentials: Credentials,
  id: string,
  filters: ResultFilters,
): Promise<SearchResponse> {
  const params = filterParams(filters);
  const query = params.size ? `?${params.toString()}` : "";
  return request<SearchResponse>(`${jobPath(id)}/sources${query}`, credentials);
}

export async function downloadExport(
  credentials: Credentials,
  id: string,
  filters: ResultFilters,
  format: "json" | "csv" | "md",
): Promise<Blob> {
  const params = filterParams(filters);
  params.set("format", format);
  const response = await fetch(`${jobPath(id)}/export?${params.toString()}`, {
    headers: { Authorization: authHeader(credentials) },
    cache: "no-store",
    credentials: "omit",
    redirect: "error",
  }).catch(() => {
    throw new ApiError("AURA API is unreachable. Check that the local stack is running.", 0);
  });
  if (!response.ok) {
    let detail = `The export failed (${response.status}).`;
    try {
      const body = (await response.json()) as { detail?: string };
      detail = body.detail ?? detail;
    } catch {
      // Keep the status-only fallback.
    }
    throw new ApiError(detail, response.status);
  }
  return response.blob();
}

export type { ResearchSource };
