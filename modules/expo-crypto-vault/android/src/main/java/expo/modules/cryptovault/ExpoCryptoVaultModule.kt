package expo.modules.cryptovault

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

private const val DEFAULT_KEY_ALIAS = "phase1_device_key"

class ExpoCryptoVaultModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("ExpoCryptoVault")

        AsyncFunction("getAvailability") {
            mapOf(
                "isAvailable" to true,
                "isHardwareBacked" to false,
                "description" to "ExpoCryptoVault Android module loaded. Hardware-backing is validated during ensureKeyPair().",
            )
        }

        AsyncFunction("ensureKeyPair") { options: Map<String, Any?>? ->
            val keyAlias = options.keyAlias()
            val requireHardwareBacked = options.requireHardwareBacked()
            val manager = AndroidKeystoreCryptoVault(
                keyAlias = keyAlias,
                requireHardwareBacked = requireHardwareBacked,
            )
            val keyStatus = manager.ensureKeyPair()

            mapOf(
                "keyAlias" to keyStatus.keyAlias,
                "publicKeyPem" to keyStatus.publicKeyPem,
                "isHardwareBacked" to keyStatus.isHardwareBacked,
                "description" to keyStatus.description,
            )
        }

        AsyncFunction("getPublicKeyPem") {
            AndroidKeystoreCryptoVault(keyAlias = DEFAULT_KEY_ALIAS).getPublicKeyPem()
        }

        AsyncFunction("signPayload") { canonicalJson: String ->
            AndroidKeystoreCryptoVault(keyAlias = DEFAULT_KEY_ALIAS).signPayload(canonicalJson)
        }

        AsyncFunction("deleteKeyPair") { options: Map<String, Any?>? ->
            AndroidKeystoreCryptoVault(keyAlias = options.keyAlias()).deleteKeyPair()
        }
    }
}

private fun Map<String, Any?>?.keyAlias(): String =
    this?.get("keyAlias") as? String ?: DEFAULT_KEY_ALIAS

private fun Map<String, Any?>?.requireHardwareBacked(): Boolean =
    this?.get("requireHardwareBacked") as? Boolean ?: true