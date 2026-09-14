/**
 * FinSight API Client Layer
 * Connects to backend services or switches to the mock adapter during isolated testing.
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

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

export async function apiClient<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json",
    ...options.headers,
  };

  try {
    const res = await fetch(url, { ...options, headers });

    if (!res.ok) {
      let errorMessage = "An error occurred while connecting to the service.";
      let fieldErrors: Record<string, string> | undefined;

      try {
        const errorData = await res.json();
        errorMessage = errorData.message || errorMessage;
        fieldErrors = errorData.errors || errorData.fieldErrors;
      } catch {
        // Fall back to HTTP status text
        errorMessage = res.statusText || errorMessage;
      }

      throw new ApiError(res.status, errorMessage, fieldErrors);
    }

    return (await res.json()) as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(0, "Network connection error. Please check your internet connection.");
  }
}
