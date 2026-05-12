import { createVerifyCanonicalPayload } from "@/security/canonicalPayload";
import { CryptoVault } from "@/modules/cryptovault";

export async function signCanonicalPayload(payload: string): Promise<string> {
  return CryptoVault.signPayload(payload);
}

export async function createAuthVerifySignature(challengeId: string): Promise<{
  publicKey: string;
  signature: string;
}> {
  const availability = await CryptoVault.getAvailability();

  if (!availability.isAvailable) {
    throw new Error(availability.description);
  }

  await CryptoVault.ensureKeyPair();

  const publicKey = await CryptoVault.getPublicKeyPem();

  if (!publicKey.trim()) {
    throw new Error("CryptoVault returned an empty public key.");
  }

  const canonicalPayload = createVerifyCanonicalPayload(challengeId, publicKey);
  const signature = await signCanonicalPayload(canonicalPayload);

  if (!signature.trim()) {
    throw new Error("CryptoVault returned an empty signature.");
  }

  return {
    publicKey,
    signature,
  };
}
