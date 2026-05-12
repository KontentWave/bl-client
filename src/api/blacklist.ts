import { z } from "zod";

import { apiClient, toApiError } from "./client";
import { parseSuccessEnvelope } from "./envelopes";

const blacklistRequestSchema = z.object({
  target_hash: z.string().length(64),
  public_key: z.string().min(1),
  signature: z.string().min(1),
});

const blacklistDataSchema = z.object({
  target_hash: z.string().length(64),
  features: z.array(z.string()),
});

export type CheckBlacklistRequest = z.infer<typeof blacklistRequestSchema>;
export type CheckBlacklistResponse = z.infer<typeof blacklistDataSchema>;

export async function checkBlacklist(
  input: CheckBlacklistRequest,
): Promise<CheckBlacklistResponse> {
  const payload = blacklistRequestSchema.parse(input);

  try {
    const response = await apiClient.post("/blacklist/check", payload);
    return parseSuccessEnvelope(blacklistDataSchema, response.data).data;
  } catch (error) {
    throw toApiError(error);
  }
}
