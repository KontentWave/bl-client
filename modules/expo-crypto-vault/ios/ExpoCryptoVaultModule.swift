import ExpoModulesCore
import Foundation
import Security

public final class ExpoCryptoVaultModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoCryptoVault")

    AsyncFunction("getAvailability") { () -> [String: Any] in
      let vault = SecureEnclaveCryptoVault()
      return vault.getAvailability()
    }

    AsyncFunction("ensureKeyPair") { (options: [String: Any]?) throws -> [String: Any] in
      let vault = SecureEnclaveCryptoVault(options: options)
      let status = try vault.ensureKeyPair()

      return [
        "keyAlias": status.keyAlias,
        "publicKeyPem": status.publicKeyPem,
        "isHardwareBacked": status.isHardwareBacked,
        "description": status.description,
      ]
    }

    AsyncFunction("getPublicKeyPem") { () throws -> String in
      return try SecureEnclaveCryptoVault().getPublicKeyPem()
    }

    AsyncFunction("signPayload") { (canonicalJson: String) throws -> String in
      return try SecureEnclaveCryptoVault().signPayload(canonicalJson)
    }

    AsyncFunction("deleteKeyPair") { (options: [String: Any]?) throws in
      try SecureEnclaveCryptoVault(options: options).deleteKeyPair()
    }
  }
}

private let defaultKeyAlias = "phase1_device_key"

private struct SecureEnclaveKeyPairStatus {
  let keyAlias: String
  let publicKeyPem: String
  let isHardwareBacked: Bool
  let description: String
}

private final class SecureEnclaveCryptoVault {
  private let keyAlias: String
  private let requireHardwareBacked: Bool

  init(options: [String: Any]? = nil) {
    self.keyAlias = options?["keyAlias"] as? String ?? defaultKeyAlias
    self.requireHardwareBacked = options?["requireHardwareBacked"] as? Bool ?? true
  }

  func getAvailability() -> [String: Any] {
    #if targetEnvironment(simulator)
      return [
        "isAvailable": false,
        "isHardwareBacked": false,
        "description": "Secure Enclave is unavailable on the iOS simulator. Use a physical iPhone for hardware-backed signing.",
      ]
    #else
      return [
        "isAvailable": true,
        "isHardwareBacked": true,
        "description": "Secure Enclave signing is available on supported physical iOS devices.",
      ]
    #endif
  }

  func ensureKeyPair() throws -> SecureEnclaveKeyPairStatus {
    #if targetEnvironment(simulator)
      throw cryptoVaultError(
        code: 100,
        description: "Secure Enclave key generation is unavailable on the iOS simulator.",
      )
    #else
      let privateKey = try getOrCreatePrivateKey()
      let attributes = copyAttributes(for: privateKey)
      let isHardwareBacked = isSecureEnclaveKey(attributes)

      if requireHardwareBacked && !isHardwareBacked {
        try deleteKeyPair()
        throw cryptoVaultError(
          code: 101,
          description: "A Secure Enclave-backed iOS key is required, but the current key is not hardware-backed.",
        )
      }

      let publicKeyPem = try getPublicKeyPem(from: privateKey)

      return SecureEnclaveKeyPairStatus(
        keyAlias: keyAlias,
        publicKeyPem: publicKeyPem,
        isHardwareBacked: isHardwareBacked,
        description: isHardwareBacked
          ? "Secure Enclave-backed P-256 signing key is ready."
          : "Software-backed key is present.",
      )
    #endif
  }

  func getPublicKeyPem() throws -> String {
    #if targetEnvironment(simulator)
      throw cryptoVaultError(
        code: 102,
        description: "Secure Enclave public-key export is unavailable on the iOS simulator.",
      )
    #else
      return try getPublicKeyPem(from: try getOrCreatePrivateKey())
    #endif
  }

  func signPayload(_ canonicalJson: String) throws -> String {
    #if targetEnvironment(simulator)
      throw cryptoVaultError(
        code: 103,
        description: "Secure Enclave signing is unavailable on the iOS simulator.",
      )
    #else
      let privateKey = try getOrCreatePrivateKey()
      let algorithm = SecKeyAlgorithm.ecdsaSignatureMessageX962SHA256

      guard SecKeyIsAlgorithmSupported(privateKey, .sign, algorithm) else {
        throw cryptoVaultError(
          code: 104,
          description: "The current Secure Enclave key does not support SHA-256 ECDSA signing.",
        )
      }

      var error: Unmanaged<CFError>?
      guard let signature = SecKeyCreateSignature(
        privateKey,
        algorithm,
        canonicalJson.data(using: .utf8)! as CFData,
        &error
      ) as Data? else {
        throw wrapSecurityError(
          error?.takeRetainedValue(),
          code: 105,
          fallback: "Secure Enclave signing failed.",
        )
      }

      return signature.base64EncodedString()
    #endif
  }

  func deleteKeyPair() throws {
    let query: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: keyTagData(),
      kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
    ]

    let status = SecItemDelete(query as CFDictionary)

    guard status == errSecSuccess || status == errSecItemNotFound else {
      throw cryptoVaultError(
        code: 106,
        description: "Failed to delete Secure Enclave key pair (status: \(status)).",
      )
    }
  }

  private func getOrCreatePrivateKey() throws -> SecKey {
    if let existing = try findPrivateKey() {
      return existing
    }

    return try createPrivateKey()
  }

  private func findPrivateKey() throws -> SecKey? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: keyTagData(),
      kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
      kSecReturnRef as String: true,
    ]

    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)

    if status == errSecItemNotFound {
      return nil
    }

    guard status == errSecSuccess, let key = item else {
      throw cryptoVaultError(
        code: 107,
        description: "Failed to read Secure Enclave key pair (status: \(status)).",
      )
    }

    return key as! SecKey
  }

  private func createPrivateKey() throws -> SecKey {
    let accessControl = try makeAccessControl()

    let attributes: [String: Any] = [
      kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
      kSecAttrKeySizeInBits as String: 256,
      kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
      kSecPrivateKeyAttrs as String: [
        kSecAttrIsPermanent as String: true,
        kSecAttrApplicationTag as String: keyTagData(),
        kSecAttrAccessControl as String: accessControl,
      ],
    ]

    var error: Unmanaged<CFError>?
    guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
      throw wrapSecurityError(
        error?.takeRetainedValue(),
        code: 108,
        fallback: "Secure Enclave key generation failed.",
      )
    }

    return key
  }

  private func makeAccessControl() throws -> SecAccessControl {
    var error: Unmanaged<CFError>?
    guard let accessControl = SecAccessControlCreateWithFlags(
      nil,
      kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
      [.privateKeyUsage],
      &error
    ) else {
      throw wrapSecurityError(
        error?.takeRetainedValue(),
        code: 109,
        fallback: "Failed to create iOS access control for Secure Enclave key generation.",
      )
    }

    return accessControl
  }

  private func getPublicKeyPem(from privateKey: SecKey) throws -> String {
    guard let publicKey = SecKeyCopyPublicKey(privateKey) else {
      throw cryptoVaultError(
        code: 110,
        description: "Secure Enclave public key could not be derived from the private key.",
      )
    }

    var error: Unmanaged<CFError>?
    guard let publicKeyData = SecKeyCopyExternalRepresentation(publicKey, &error) as Data? else {
      throw wrapSecurityError(
        error?.takeRetainedValue(),
        code: 111,
        fallback: "Secure Enclave public key export failed.",
      )
    }

    let subjectPublicKeyInfo = try createSubjectPublicKeyInfo(from: publicKeyData)
    return pemEncode(subjectPublicKeyInfo)
  }

  private func createSubjectPublicKeyInfo(from x963PublicKey: Data) throws -> Data {
    guard x963PublicKey.count == 65 else {
      throw cryptoVaultError(
        code: 112,
        description: "Unexpected public key length for P-256 export.",
      )
    }

    let algorithmIdentifier: [UInt8] = [
      0x30, 0x13,
      0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01,
      0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07,
    ]

    let bitStringPayload = Data([0x00]) + x963PublicKey
    let bitString = Data([0x03]) + derLength(bitStringPayload.count) + bitStringPayload
    let sequenceBody = Data(algorithmIdentifier) + bitString

    return Data([0x30]) + derLength(sequenceBody.count) + sequenceBody
  }

  private func pemEncode(_ data: Data) -> String {
    let base64 = data.base64EncodedString()
    let lines = stride(from: 0, to: base64.count, by: 64).map { offset -> String in
      let start = base64.index(base64.startIndex, offsetBy: offset)
      let end = base64.index(start, offsetBy: min(64, base64.count - offset), limitedBy: base64.endIndex) ?? base64.endIndex
      return String(base64[start..<end])
    }

    return (["-----BEGIN PUBLIC KEY-----"] + lines + ["-----END PUBLIC KEY-----"])
      .joined(separator: "\n")
      .trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private func derLength(_ length: Int) -> Data {
    if length < 0x80 {
      return Data([UInt8(length)])
    }

    var value = length
    var bytes: [UInt8] = []

    while value > 0 {
      bytes.insert(UInt8(value & 0xFF), at: 0)
      value >>= 8
    }

    return Data([0x80 | UInt8(bytes.count)] + bytes)
  }

  private func copyAttributes(for key: SecKey) -> [String: Any] {
    return SecKeyCopyAttributes(key) as? [String: Any] ?? [:]
  }

  private func isSecureEnclaveKey(_ attributes: [String: Any]) -> Bool {
    let tokenId = attributes[kSecAttrTokenID as String] as? String
    return tokenId == (kSecAttrTokenIDSecureEnclave as String)
  }

  private func keyTagData() -> Data {
    return Data(keyAlias.utf8)
  }

  private func cryptoVaultError(code: Int, description: String) -> NSError {
    return NSError(
      domain: "ExpoCryptoVault",
      code: code,
      userInfo: [NSLocalizedDescriptionKey: description],
    )
  }

  private func wrapSecurityError(
    _ error: CFError?,
    code: Int,
    fallback: String,
  ) -> NSError {
    let description = (error as Error?)?.localizedDescription ?? fallback
    return cryptoVaultError(code: code, description: description)
  }
}