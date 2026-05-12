export type CryptoVaultAvailability = {
  isAvailable: boolean;
  isHardwareBacked: boolean;
  description: string;
};

export type CryptoVaultEnsureKeyPairOptions = {
  keyAlias?: string;
  requireHardwareBacked?: boolean;
};

export type CryptoVaultKeyPairStatus = {
  keyAlias: string;
  publicKeyPem: string;
  isHardwareBacked: boolean;
  description: string;
};

export interface CryptoVaultModule {
  ensureKeyPair(
    options?: CryptoVaultEnsureKeyPairOptions,
  ): Promise<CryptoVaultKeyPairStatus>;
  getPublicKeyPem(): Promise<string>;
  signPayload(canonicalJson: string): Promise<string>;
  getAvailability(): Promise<CryptoVaultAvailability>;
  deleteKeyPair(options?: CryptoVaultEnsureKeyPairOptions): Promise<void>;
}
