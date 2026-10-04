# ADR-002: Shared phone normalization before report signing and caller hashing

**Recorded:** 2026-10-04 21:55:23 CEST (UTC+02:00).
**Updated:** 2026-10-04 22:06:44 CEST (UTC+02:00) - source review/publication approved; implementation and prior validation unchanged.
**Status:** Accepted for CB-02 and supported CB-08 implementation; source publication/PR merge approved, deployment and beta distribution not approved.
**Scope:** Active native Kotlin/Compose client. Backend source/configuration remain read-only.

## Context

At client `5507fced83603f069293ead0264468a2a25f8cbc`, reporting signed trimmed raw input. At inspected backend `6c96c1d4cee869a7cfbe878c24def13206715181`, `ReportSubmissionService` resolves the normalized PEM binding, normalizes the target, verifies `DeviceSignatureService.reportPayload`, then hashes the normalized target. Noncanonical input therefore produced different signed bytes. The report contract explicitly signs `normalizedClientPhoneNumber`.

Backend `EscortPhoneNumberNormalizer` maps explicit +, 00, bare 421 and Slovak local 0; it rejects unprefixed local digits but strips arbitrary non-digits and applies no country/national-length rules. The old caller normalizer guessed +421 for bare nine-digit input and rejected bare 421. Neither implementation establishes worldwide number validity.

The user explicitly selected preserving existing prefix mappings with a strict formatting-only guard and coordinating country/length validation, rather than imposing a new Slovak-only numbering plan.

## Decision

Use one `PhoneNumberNormalizer` for report UI validation, defensive repository validation and the existing caller-normalizer adapter. Accept ASCII digits, spaces and internal hyphens; compact before applying existing +/00/421/0 mappings. Do not silently discard labels, extensions, Unicode digits, parentheses or arbitrary punctuation. Reject missing numbers, bare local digits, misplaced/repeated +, leading/trailing hyphens, empty prefix tails, zero-leading international digits and country-only 421.

Examples using synthetic numbers:

| Input | Canonical output |
| --- | --- |
| `+421900000001`, `+421 900 000 001` | `+421900000001` |
| `00421 900-000-001` | `+421900000001` |
| `0900 000 001`, `421900000001` | `+421900000001` |
| `+447700900001`, `0044 7700 900001` | `+447700900001` |
| `900000001`, `447700900001`, caller-unavailable text, extensions | Rejected; no signing/request |

Normalize before report signing and transmit exactly that canonical value. The production report factory requires already-canonical input before existing-key lookup. Do not independently normalize inside signing while sending a different value. Caller SHA-256 uses explicit UTF-8 bytes of the same normalized value.

Keep canonical report field order (`client_phone_number`, `feature`, `public_key`), compact JSON escaping including `\/`, UTF-8 signing and Base64 DER ECDSA unchanged. Match the inspected PHP PEM policy: convert CRLF, trim each LF-separated line using PHP's whitespace set, remove blank lines. Compact generated PEM remains identical.

Invalid report input produces a visible field failure before the repository signs or calls HTTP. Invalid/unavailable callers retain the existing diagnostic outcome without querying. Edits, normalization, construction, local recovery and cooldown expiry do not submit or replay work. Preserve existing-key-only signing, authorization-rejection matching, key-bound recovery, retained challenges and monotonic manual cooldowns.

## Evidence and limitations

Shared JSON golden vectors cover eight accepted representations and 30 rejected inputs. Boundary trimming uses the ASCII PHP whitespace set; incoming calls pass original input to the shared normalizer instead of first removing Unicode whitespace. Local mock/fake tests cover wire normalization, actual ephemeral P-256 signatures, equivalent canonical bytes/hashes, invalid input without signing/request, caller-query preparation and no replay. The opt-in JVM test pipes synthetic wire fixtures into client-owned PHP that loads the actual backend normalizer and signature service, comparing canonical bytes/hashes, normalized/dirty PEM, valid signatures and raw-input/tampered rejection. It does not boot Laravel or touch a database, binding, cache, network or SMS.

Local evidence: initial and final repeated runs each passed 41 targeted JVM tests across nine suites and 90 full JVM tests across 16 suites, zero failures/errors/skips with PHP interoperability enabled; eight report and eight query signature verifications through actual PHP service methods per run. The final repeated runs include the boundary-whitespace correction. Both debug APK targets build; lint has zero errors and 35 warnings. No new dependency or device execution was required.

Country coverage, national lengths, full E.164 validity and additional formatting remain an explicit backend-owner contract decision. No length rule is invented or silently advertised as validation. The safe client subset is intentionally stricter than backend arbitrary-character stripping; bare local inference is deliberately removed. Future expansion must update shared vectors and both sides' contract, not add an independent caller-only heuristic.

This evidence is not HTTP/binding/database interoperability, actual Android Keystore signing, physical-device/carrier caller-ID coverage, reboot/backup validation, hosted deployment or live OTP. CB-03/04/05/09 and backend CB-07 remain separate. BETA-SMS-001 and BETA-BACKEND-001 remain OPEN/BLOCKED; this decision is not overall closed-beta readiness.
