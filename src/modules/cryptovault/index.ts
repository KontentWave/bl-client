import { NativeModulesProxy, requireNativeModule } from "expo-modules-core";

import type {
  CryptoVaultAvailability,
  CryptoVaultEnsureKeyPairOptions,
  CryptoVaultKeyPairStatus,
  CryptoVaultModule,
} from "./types";

const MODULE_NAME = "ExpoCryptoVault";

function loadNativeModule(): CryptoVaultModule | null {
  if (!(MODULE_NAME in NativeModulesProxy)) {
    return null;
  }

  return requireNativeModule<CryptoVaultModule>(MODULE_NAME);
}

class MissingCryptoVaultModule implements CryptoVaultModule {
  async getAvailability(): Promise<CryptoVaultAvailability> {
    return {
      isAvailable: false,
      isHardwareBacked: false,
      description:
        "ExpoCryptoVault native module is not linked yet. Build a development client after adding the local module.",
    };
  }

  async ensureKeyPair(
    _options?: CryptoVaultEnsureKeyPairOptions,
  ): Promise<CryptoVaultKeyPairStatus> {
    throw new Error(
      "ExpoCryptoVault native module is not linked yet. Cannot create an Android Keystore key pair.",
    );
  }

  async getPublicKeyPem(): Promise<string> {
    throw new Error(
      "ExpoCryptoVault native module is not linked yet. Cannot read the public key.",
    );
  }

  async signPayload(_canonicalJson: string): Promise<string> {
    throw new Error(
      "ExpoCryptoVault native module is not linked yet. Cannot sign payloads.",
    );
  }

  async deleteKeyPair(
    _options?: CryptoVaultEnsureKeyPairOptions,
  ): Promise<void> {
    throw new Error(
      "ExpoCryptoVault native module is not linked yet. Cannot delete the key pair.",
    );
  }
}

export const CryptoVault: CryptoVaultModule =
  loadNativeModule() ?? new MissingCryptoVaultModule();
