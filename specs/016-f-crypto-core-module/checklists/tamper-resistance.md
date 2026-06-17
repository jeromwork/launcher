# Checklist: tamper-resistance — spec 016 F-CRYPTO

Run date: 2026-06-17.

F-CRYPTO **сама** не имеет subscription/entitlement logic. Применяется по cross-reference — F-CRYPTO будет foundation для S-10 (subscription server timer) **через** другие spec'и. Чек-лист уточняет, что F-CRYPTO готова поддержать tamper-resistant entitlement.

## Server-validated entitlement

- [N/A] CHK-TAM-001..007 — F-CRYPTO не gating cloud features. Эти гейты применятся к S-10 spec'е.
- [x] **Косвенно**: F-CRYPTO предоставляет `AsymmetricCrypto.verify(...)` — будущий subscription endpoint в Cloudflare Worker подпишет JWT entitlement через Ed25519, client использует `verify(...)` для validation public key. Public key embed in app, private key on server. Это и есть standard JWT signature flow.

## Platform integrity

- [N/A] CHK-TAM-008 — Play Integrity API — application-level, не F-CRYPTO.
- [x] CHK-TAM-009 — R8 obfuscation — application-level. F-CRYPTO лишь предоставляет primitives, не зависит от obfuscation. **Note**: если F-CRYPTO будет публикован как Maven artifact для extract'нутого репо — R8 не применяется (library code).
- [N/A] CHK-TAM-010 — Code attestation — application-level future spec.

## Local-mode is free

- [N/A] CHK-TAM-011..013 — F-CRYPTO не gating local features. Local mode полностью использует F-CRYPTO без subscription.

## Anti-patterns

- [N/A] CHK-TAM-014 — Client-side `isPremium` flag — F-CRYPTO не знает про subscription.
- [N/A] CHK-TAM-015 — Sign-In ≠ paid — F-CRYPTO не знает про Sign-In.
- [N/A] CHK-TAM-016 — Embedded key signing — F-CRYPTO не делает это.
- [x] **Косвенно**: F-CRYPTO даёт `RandomSource.nextBytes()` который **не должен** использоваться для генерации client-side subscription tokens. Документировать в plan-phase KDoc «MUST NOT use for entitlement claims — server-side issuance only».
- [N/A] CHK-TAM-017 — Trial day counter — F-CRYPTO не считает.

## Cloudflare Worker contract

- [N/A] CHK-TAM-018..020 — F-CRYPTO не имеет Worker endpoints.

## Audit trail

- [N/A] CHK-TAM-021..022 — F-CRYPTO offline, no audit log.

## Дополнительно — связь с F-CRYPTO

- [x] **F-CRYPTO как enabler для tamper-resistance**: будущий S-10 будет использовать F-CRYPTO `AsymmetricCrypto.verify(...)` для server-issued JWT (Ed25519 signature). Это standard pattern.
- [x] **F-CRYPTO не нарушает tamper-resistance**: его primitives **не могут** быть использованы для local-only entitlement bypass без сговора с сервером (нет embedded private signing keys).

## Open issues

| # | Issue | Severity |
|---|---|---|
| O-1 | KDoc warning «RandomSource MUST NOT be used for entitlement claims» | Minor — plan-phase |
| O-2 | Plan для S-10: использовать F-CRYPTO `verify()` для JWT — note for cross-spec coordination | Future — записать в server-roadmap или в S-10 spec |

## Result

**Все actionable PASS (2 explicit, 1 documented warning), 18 N/A (F-CRYPTO не subscription-touching)**.

**Verdict**: PASS. F-CRYPTO нейтральна к subscription/entitlement — её primitives не дают local bypass, но enable server-validated signature flow для S-10.

---

## TL;DR простым языком

F-CRYPTO **не имеет** подписки/билинга — это infrastructure. Но её **будут использовать** для проверки подписки в спеке S-10 (server-validated JWT entitlement). F-CRYPTO для этого подходит: server подписывает Ed25519, клиент через F-CRYPTO `verify()` проверяет. **Один момент в plan-фазе**: добавить KDoc-предупреждение «не используйте `RandomSource` для генерации subscription token'ов на клиенте — это не работает, server должен выдавать».
