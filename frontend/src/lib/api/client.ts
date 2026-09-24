/** HTTP transport for the Spring API. Failed requests never fall back to demo data. */
const API_BASE_URL = "/api";

export class ApiError extends Error {
  constructor(
    public status: number,
    public message: string,
    public fieldErrors?: Record<string, string>
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function readFieldErrors(value: unknown): Record<string, string> | undefined {
  if (!value || typeof value !== "object" || Array.isArray(value)) return undefined;
  const entries = Object.entries(value).filter((entry): entry is [string, string] =>
    typeof entry[1] === "string"
  );
  return entries.length ? Object.fromEntries(entries) : undefined;
}

export async function apiClient<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  if (options.body != null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${endpoint}`, {
      cache: "no-store",
      ...options,
      headers,
    });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new ApiError(0, "Unable to connect to the backend. Check that the service is running and try again.");
  }

  if (response.status === 204) return undefined as T;

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    if (!response.ok) {
      throw new ApiError(response.status, response.statusText || "The backend request failed.");
    }
    throw new ApiError(response.status, "The backend returned an invalid response.");
  }

  if (!response.ok) {
    const error = payload && typeof payload === "object" ? payload as Record<string, unknown> : {};
    throw new ApiError(
      response.status,
      typeof error.message === "string" ? error.message : response.statusText || "The backend request failed.",
      readFieldErrors(error.errors ?? error.fieldErrors)
    );
  }

  return payload as T;
}
