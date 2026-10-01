import axios, { AxiosError } from "axios";

import { appEnv } from "@/config/env";

import { ErrorMap, FailureEnvelope, parseFailureEnvelope } from "./envelopes";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number | undefined,
    readonly errors: ErrorMap,
    readonly meta: Record<string, unknown>,
  ) {
    super(message);
  }
}

function toFallbackApiError(
  payload: unknown,
  status: number | undefined,
): ApiError {
  if (payload && typeof payload === "object") {
    const message =
      "message" in payload && typeof payload.message === "string"
        ? payload.message
        : "Request failed.";
    const code =
      "code" in payload && typeof payload.code === "string"
        ? payload.code
        : "api_error";

    return new ApiError(message, code, status, {}, {});
  }

  return new ApiError("Request failed.", "api_error", status, {}, {});
}

export const apiClient = axios.create({
  baseURL: appEnv.apiBaseUrl,
  headers: {
    Accept: "application/json",
    "Content-Type": "application/json",
  },
  timeout: 20000,
});

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }

  if (error instanceof AxiosError && error.response?.data) {
    try {
      const payload = parseFailureEnvelope(
        error.response.data,
      ) as FailureEnvelope;

      return new ApiError(
        payload.message,
        payload.code,
        error.response.status,
        payload.errors,
        payload.meta,
      );
    } catch {
      return toFallbackApiError(error.response.data, error.response.status);
    }
  }

  return new ApiError(
    "Unexpected network failure.",
    "network_error",
    undefined,
    {},
    {},
  );
}
