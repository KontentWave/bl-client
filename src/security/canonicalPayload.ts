import { normalizePublicKeyPem } from "./pem";

function phpCompatibleJsonStringify(payload: Record<string, string>): string {
  return JSON.stringify(payload).replace(/\//g, "\\/");
}

export function createVerifyCanonicalPayload(
  challengeId: string,
  publicKey: string,
): string {
  return phpCompatibleJsonStringify({
    challenge_id: challengeId,
    public_key: normalizePublicKeyPem(publicKey),
  });
}

export function createReportCanonicalPayload(
  clientPhoneNumber: string,
  feature: string,
  publicKey: string,
): string {
  return phpCompatibleJsonStringify({
    client_phone_number: clientPhoneNumber,
    feature,
    public_key: normalizePublicKeyPem(publicKey),
  });
}

export function createBlacklistCanonicalPayload(
  targetHash: string,
  publicKey: string,
): string {
  return phpCompatibleJsonStringify({
    target_hash: targetHash.toLowerCase(),
    public_key: normalizePublicKeyPem(publicKey),
  });
}
