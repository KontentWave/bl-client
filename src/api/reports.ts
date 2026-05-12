import { z } from "zod";

import { apiClient, toApiError } from "./client";
import { parseSuccessEnvelope } from "./envelopes";

const reportRequestSchema = z.object({
  client_phone_number: z.string().min(1),
  feature: z.string().min(1),
  public_key: z.string().min(1),
  signature: z.string().min(1),
});

const reportDataSchema = z.object({
  client_hash: z.string().length(64),
  reporter_hash: z.string().length(64),
  feature: z.string(),
  feature_label: z.string(),
  unique_reporter_count: z.number().int().nonnegative(),
  level: z.string(),
  ready_for_sync: z.boolean(),
});

export type CreateReportRequest = z.infer<typeof reportRequestSchema>;
export type CreateReportResponse = z.infer<typeof reportDataSchema>;

export async function createReport(
  input: CreateReportRequest,
): Promise<CreateReportResponse> {
  const payload = reportRequestSchema.parse(input);

  try {
    const response = await apiClient.post("/reports", payload);
    return parseSuccessEnvelope(reportDataSchema, response.data).data;
  } catch (error) {
    throw toApiError(error);
  }
}
