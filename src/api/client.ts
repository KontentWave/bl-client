import axios, { AxiosError } from "axios";

import { appEnv } from "@/config/env";

import { FailureEnvelope, parseFailureEnvelope } from "./envelopes";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number | undefined,
    readonly errors: Record<string, string[]>,
    readonly meta: Record<string, unknown>,
  ) {
    super(message);
  }
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
  }

  return new ApiError(
    "Unexpected network failure.",
    "network_error",
    undefined,
    {},
    {},
  );
}
