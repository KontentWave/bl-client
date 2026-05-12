import { z } from "zod";

import { apiClient, toApiError } from "./client";
import { parseSuccessEnvelope } from "./envelopes";

const initiateAuthRequestSchema = z.object({
  ad_url: z.string().url(),
});

const initiateAuthDataSchema = z.object({
  challenge_id: z.string().uuid(),
  masked_phone_number: z.string(),
  otp_expires_at: z.string(),
});

const verifyAuthRequestSchema = z.object({
  challenge_id: z.string().uuid(),
  otp: z.string().length(6),
  public_key: z.string().min(1),
  signature: z.string().min(1),
});

const verifyAuthDataSchema = z.object({
  challenge_id: z.string().uuid(),
  masked_phone_number: z.string(),
  verified_at: z.string(),
});

export type InitiateAuthRequest = z.infer<typeof initiateAuthRequestSchema>;
export type InitiateAuthResponse = z.infer<typeof initiateAuthDataSchema>;
export type VerifyAuthRequest = z.infer<typeof verifyAuthRequestSchema>;
export type VerifyAuthResponse = z.infer<typeof verifyAuthDataSchema>;

export async function initiateAuth(
  input: InitiateAuthRequest,
): Promise<InitiateAuthResponse> {
  const payload = initiateAuthRequestSchema.parse(input);

  try {
    const response = await apiClient.post("/auth/initiate", payload);
    return parseSuccessEnvelope(initiateAuthDataSchema, response.data).data;
  } catch (error) {
    throw toApiError(error);
  }
}

export async function verifyAuth(
  input: VerifyAuthRequest,
): Promise<VerifyAuthResponse> {
  const payload = verifyAuthRequestSchema.parse(input);

  try {
    const response = await apiClient.post("/auth/verify", payload);
    return parseSuccessEnvelope(verifyAuthDataSchema, response.data).data;
  } catch (error) {
    throw toApiError(error);
  }
}
