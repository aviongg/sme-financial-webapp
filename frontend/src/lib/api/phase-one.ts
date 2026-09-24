import { apiClient } from "./client";
import { isValidUserId } from "./session";
import type {
  BusinessProfileRequest,
  BusinessProfileResponse,
  LanguagePreference,
  MonthlyRecordRequest,
  MonthlyRecordResponse,
} from "./contracts";

function userPath(userId: string): string {
  if (!isValidUserId(userId)) throw new Error("A valid profile UUID is required.");
  return encodeURIComponent(userId);
}

function monthPath(month: string): string {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) throw new Error("Month must use YYYY-MM format.");
  return encodeURIComponent(month);
}

function saveMonthlyRecord(request: MonthlyRecordRequest): Promise<MonthlyRecordResponse> {
  userPath(request.userId);
  monthPath(request.month);
  return apiClient<MonthlyRecordResponse>("/records/monthly", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export const phaseOneApi = {
  getProfile(userId: string): Promise<BusinessProfileResponse> {
    return apiClient(`/profile/${userPath(userId)}`);
  },

  createProfile(request: BusinessProfileRequest): Promise<BusinessProfileResponse> {
    userPath(request.userId);
    return apiClient("/profile", { method: "POST", body: JSON.stringify(request) });
  },

  updateLanguage(userId: string, language: LanguagePreference): Promise<BusinessProfileResponse> {
    return apiClient(`/profile/${userPath(userId)}/language`, {
      method: "PATCH",
      body: JSON.stringify({ languagePreference: language }),
    });
  },

  getMonthlyRecords(userId: string): Promise<MonthlyRecordResponse[]> {
    return apiClient(`/records/monthly/${userPath(userId)}`);
  },

  getMonthlyRecord(userId: string, month: string): Promise<MonthlyRecordResponse> {
    return apiClient(`/records/monthly/${userPath(userId)}/${monthPath(month)}`);
  },

  createMonthlyRecord: saveMonthlyRecord,

  updateMonthlyRecord(
    userId: string,
    month: string,
    request: Omit<MonthlyRecordRequest, "userId" | "month">
  ): Promise<MonthlyRecordResponse> {
    // The backend upserts by (userId, month), including full replacement of optional fields.
    return saveMonthlyRecord({ ...request, userId, month });
  },
};
