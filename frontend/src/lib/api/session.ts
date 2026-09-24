/** A development profile selector, not authentication or proof of ownership. */
const USER_ID_KEY = "finsight_user_id";
const PENDING_USER_ID_KEY = "finsight_pending_user_id";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isValidUserId(value: string): boolean {
  return UUID_PATTERN.test(value);
}

function readStoredId(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    const value = window.localStorage.getItem(key);
    return value && isValidUserId(value) ? value.toLowerCase() : null;
  } catch {
    return null;
  }
}

export function getUserId(): string | null {
  return readStoredId(USER_ID_KEY);
}

export function setUserId(id: string): void {
  if (!isValidUserId(id)) throw new Error("A valid profile UUID is required.");
  if (typeof window === "undefined") throw new Error("Profile selection is available in the browser only.");
  try {
    window.localStorage.setItem(USER_ID_KEY, id.toLowerCase());
    window.localStorage.removeItem(PENDING_USER_ID_KEY);
  } catch {
    throw new Error("Allow browser storage to keep your profile selection, then try again.");
  }
  window.dispatchEvent(new Event("storage"));
}

export function clearUserId(): void {
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(USER_ID_KEY);
    window.localStorage.removeItem(PENDING_USER_ID_KEY);
    window.dispatchEvent(new Event("storage"));
  }
}

/** Stable candidate for profile creation; activate it only after the backend confirms it. */
export function getOrCreateUserId(): string {
  const pending = readStoredId(PENDING_USER_ID_KEY);
  if (pending) return pending;
  if (typeof window === "undefined") throw new Error("Profile selection is available in the browser only.");
  const id = crypto.randomUUID();
  try {
    window.localStorage.setItem(PENDING_USER_ID_KEY, id);
  } catch {
    throw new Error("Allow browser storage to keep your profile selection, then try again.");
  }
  return id;
}
