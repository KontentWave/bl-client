# ADR-001: Key-bound local workspace recovery, not a server session

**Recorded:** 2026-10-04 21:09:35 CEST (UTC+02:00).
**Updated:** 2026-10-04 21:24:44 CEST (UTC+02:00) - clarify rejection handling after failed persistence.
**Status:** Accepted for local CB-01 implementation; publication and beta distribution not approved.
**Scope:** Active native Kotlin/Compose client only.

## Context

Activity-owned onboarding state survives configuration changes but not process death. The Android Keystore key/server binding can remain intact while the UI returns to SMS onboarding. The documented contract exposes initiate, verify, report and blacklist-check operations, but no dedicated read-only session-validation operation. A key's mere existence is not proof that the server ever bound it.

Legacy key getters/signing helpers call `ensureKeyPair`, which creates absent keys; previously it also replaced unacceptable existing keys. Those APIs cannot safely be used to inspect recovery state. Report and Shield/manual query factories share signing and must not silently rotate a bound identity.

## Decision

Persist version, configured backend scope, SHA-256 of the submitted public key's SPKI encoding, and historical `verified_at`, only after successful explicit verification and comparison with the current usable key. These fields are a navigation hint, not a bearer credential or authoritative authentication state. Do not persist recipient metadata, URLs, OTPs, challenge/cooldown state, sensitive requests or private material.

`SessionRecovery` is shared by production onboarding/report/manual-query/Shield factories. Restoration and resume check metadata and the existing key locally without generating, deleting or signing a request. Missing/invalid/changed keys and malformed/incomplete/wrong-backend records fail closed for restored navigation; unavailable keys retain metadata and can be rechecked on resume. Existing-only report/query signing leaves canonical fields, PEM normalization, signature encoding and successful response behavior unchanged.

Do not use reporting as a validation probe or manufacture a validation endpoint. Restoration does not initiate, verify, report or query. The home card describes historical local recovery and unconfirmed current authorization; Shield permission readiness is explicitly not a successful lookup. Incoming-call events remain independent signed-query triggers.

Well-formed HTTP 403 report `device_not_bound` or query `blacklist_query_unauthorized` invalidates the matching hint, even if verification metadata was not persisted. Match against the current live identity first, then stored identity if no live record exists; stale stored metadata cannot invalidate a newer differently keyed live verification. The query discriminator combines signature/binding rejection, so no revocation claim is made. Malformed envelopes, unrelated signature errors, throttling and temporary network/server failures do not clear a valid hint.

Exclude `device_binding_recovery.xml` from legacy backup, API 31+ cloud backup and device transfer. Matching usable key identity is mandatory even if preferences are copied manually. This is not a CB-05 caller-diagnostic backup/retention fix.

Explicitly reopening onboarding removes only the local hint and opens the URL form without SMS. Explicit OTP verification may create an absent key; unacceptable existing keys fail rather than being replaced. No migration can infer an old server binding from an existing key alone.

## Consequences and limits

Ordinary workspace navigation can recover offline without another SMS. Server authorization is still evaluated by ordinary signed endpoints; a future proactive confirmation UX requires a coordinated, documented safe read-only contract decision. This implementation does not invent that behavior.

Preference commits return a durability result so failures can be shown. Failed removal blocks restoration in the current process and is retried locally; if storage cannot persist removal, a later process may still read an old *unconfirmed* hint. Storage failures do not authorize requests at the server and cannot be represented as successful deletion.

Cooldown/challenge retention remains in retained ViewModels only, using monotonic time; process recreation does not restore pending work or replay it. Existing cooldown expiry only enables explicit commands.

Production navigation is exercised through a debug-only fake-backed `MainActivity` subclass with independent synthetic preferences and a fixed public-only key fixture. It is intentionally available only in debug source/manifest; no production API or Keystore factory is constructed. Its process termination/relaunch evidence does not establish hardware-backed key access, physical-device/reboot behavior, hosted authorization or real SMS. Those remain independent gates.
