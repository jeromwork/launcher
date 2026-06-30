---
id: TASK-59
title: >-
  Research: Recovery vault anti-brute-force counter — SVR vs OPAQUE vs simple
  HMAC
status: Draft
assignee: []
created_date: '2026-06-26 13:57'
labels:
  - phase-2
  - crypto
  - research
  - server
milestone: m-1
dependencies:
  - TASK-57
priority: high
ordinal: 59000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Recovery vault = зашифрованный root key пользователя, лежит на сервере, открывается через passphrase. Без anti-brute-force защиты атакующий может бесконечно подбирать passphrase (4-6 цифр) локально → взлом. Counter **должен** быть server-side, иначе обходится через Clear App Data / factory reset / root.

Три подхода:

1. **Signal SVR (Secure Value Recovery)** — counter в Intel SGX enclave (специальное hardware на CPU). Maximum security, **но требует SGX hardware** на хостинге. У Cloudflare/обычного VPS — нет.

2. **OPAQUE protocol** — асимметричный PAKE, server **не видит passphrase даже в HMAC форме**. Counter всё ещё server-side, но утечка минимальна. RFC draft, мало production deployments.

3. **Simple server-side HMAC counter** — client отправляет `HMAC(passphrase-derived-key, vaultId)`, server проверяет против заранее сохранённого, инкрементит counter. Просто, работает на любом hosting, **но если server insider — может попытаться brute-force HMAC offline**.

## Зачем

Это **foundation для TASK-6 (Root Key Hierarchy)** и **TASK-21 (Account Recovery + 2FA escrow)**. Выбрать надо до того, как любая из этих фич запустится в production — иначе wire format vault'а фиксируется.

## Состояние

Draft. Зависит от TASK-57. Блокирует TASK-6, TASK-21, частично TASK-39.

---

## Что входит технически

### Signal SVR (Secure Value Recovery) — детальный scope

- **Architecture**: SVR использует Intel SGX enclave на серверах Signal. SGX = специальная hardware-isolated execution environment в CPU, защищена даже от server OS / hosting provider / Signal сотрудников.
- **Flow**: client отправляет HMAC от derived key + verifier blob. SVR enclave проверяет verifier, counter++. После 10 неудачных попыток — данные **физически уничтожаются** в enclave.
- **Crucial property**: даже Signal не может прочитать ваш backup. Только владелец passphrase. Counter / verifier защищены SGX от offline attack.
- **Hardware dependency**: **обязателен SGX-capable CPU**. AWS supports (instance types m5n.metal etc), GCP supports (Confidential VMs with Intel SGX). Cloudflare Workers / typical VPS / Hetzner — **не поддерживают**.
- **Production**: Signal Messenger, миллиарды backup'ов.
- **Spec**: [Signal SVR documentation](https://signal.org/blog/improving-registration/), [SVR2 архитектура](https://signal.org/blog/svr2/), [Source code](https://github.com/signalapp/SecureValueRecovery2).

### OPAQUE protocol — детальный scope

- **Что это**: asymmetric Password-Authenticated Key Exchange (aPAKE). Защищает от offline dictionary attack даже если server compromised.
- **Why стоит знать**: server НЕ видит password. Не видит HMAC от password. Видит только OPAQUE protocol messages, из которых **невозможно** восстановить password offline.
- **Counter property**: counter всё равно server-side, но даже если counter bypass — offline brute-force невозможен. Двойная защита.
- **Status**: [RFC draft](https://datatracker.ietf.org/doc/draft-irtf-cfrg-opaque/) (CFRG), **не финализирован** на 2026-06. В active development.
- **Production deployments**: very limited. WhatsApp заявлял в talks про E2E backup но не подтверждённо. Most implementations — academic.
- **Library availability на Kotlin/JVM**: **минимальная**. C / Rust implementations exist ([opaque-ke](https://github.com/facebook/opaque-ke)), Kotlin = от JNI wrap.
- **Сложность**: high. Protocol описан в 60+ страниц RFC draft'а. Реализация требует careful elliptic curve operations.

### Simple server-side HMAC counter — детальный scope

- **Архитектура**: client отправляет `proof = HMAC(derived_key, vaultId)`. Server сохранил `expectedProof` при setup. Compare → counter++ on mismatch, return blob on match.
- **Counter window**: например, 3 attempts per hour sliding window. После exceeded — 24h cooldown.
- **What it protects**: client-side bypass via Clear App Data / factory reset / root (counter не на устройстве, привязан к serverId).
- **What it does NOT protect**: server insider может offline brute-force HMAC (since they have `expectedProof` + know that password есть короткий PIN). Если `derived_key = Argon2id(passphrase, salt, hard params)` — offline attack expensive but **possible** for short PIN.
- **Mitigation для insider**: использовать Argon2id с очень expensive params (память 512 MiB, iterations 5) — offline attack становится costly. Но это нагружает client при каждом attempt.
- **Production**: most password managers (1Password, Bitwarden) используют похожий pattern + server-side counter. **Working at scale**.
- **Simplicity**: implementation 100-200 LOC. Минимальные hardware requirements.

### Decision criteria (наш специфический контекст)

**Hosting reality** (важно для AC#2):
- Нет SGX на Cloudflare Workers (где сейчас Worker'ы).
- Нет SGX на typical VPS / Hetzner / DigitalOcean.
- SGX доступен на AWS m5n.metal (~$2.50/hr), GCP Confidential VMs.
- Owner planning: own server cutover после spec ~35, possibly self-hosted.

**Если SGX недоступен** — Signal SVR-style **физически невозможен**. Это leaves OPAQUE (immature) или simple HMAC counter (mature but weaker insider story).

**Threat model в нашем продукте**:
- Primary threat = client-side bypass через Clear App Data / factory reset / root → закрывается **любым** server-side counter (HMAC enough).
- Secondary threat = server insider → закрывается **только** OPAQUE или SGX.
- Insider threat is real для commercial hosting, less real для self-hosted после cutover.

**Возможный compromise**: simple HMAC counter с очень expensive Argon2id (memory-hard, GPU-resistant) делает insider offline attack экономически непрактичным for short passwords. Это **pragmatic middle ground**.

### Threat model breakdown (AC#3 detail)

Для каждого threat — что закрывает каждый подход:

| Threat | Simple HMAC | OPAQUE | Signal SVR (SGX) |
|---|---|---|---|
| Clear App Data bypass | ✅ | ✅ | ✅ |
| Factory reset bypass | ✅ | ✅ | ✅ |
| Root bypass | ✅ | ✅ | ✅ |
| Network sniff | ✅ (TLS) | ✅ (TLS + PAKE) | ✅ (TLS + enclave) |
| Server admin insider | ⚠️ (offline brute-force possible if PIN short) | ✅ | ✅ |
| Hosting provider compromise | ⚠️ (DB dump + offline brute-force) | ✅ | ✅ |
| Cold storage backup leak | ⚠️ | ✅ | ✅ |
| Government request (legal) | ⚠️ (gov может потребовать DB) | ✅ (DB не помогает, OPAQUE state insufficient) | ✅ (SGX enclave опечатан) |
| Forced shutdown by attacker | ⚠️ (counter resetable если DB) | ⚠️ | ✅ (SGX destroys data) |

### API sketch (AC#4 detail)

```
PUT /vaults/{vaultId}
  Header: X-Sig: <Ed25519 signature от ownerSigningKey>
  body: {
    schemaVersion: 1,
    ciphertext: <opaque blob>,
    expectedProof: <HMAC(derived_key, vaultId) — saved by server for verification>,
    kdfParams: { algorithm: "argon2id", memory: 524288, iterations: 5, salt: <bytes> }
  }
  → 200 OK

POST /vaults/{vaultId}/attempt
  body: { proofBytes: <HMAC(derived_key_attempt, vaultId)> }
  → 200 { blob: <ciphertext>, remaining: <int> } on match
  → 429 Too Many Requests on rate-limit (with Retry-After header)
  → 403 Forbidden + counter exceeded (e.g. 3 in 1 hour sliding)

GET /vaults/{vaultId}/status
  → 200 { remainingAttempts: int, cooldownUntil: timestamp | null }
```

**Rate-limit window**: 3 attempts / 1 hour sliding. After 3 failed → 24h cooldown.

**Counter reset**: только на successful attempt.

### Decision document structure (AC#5 detail)

`docs/dev/decisions/2026-XX-XX-recovery-vault-counter.md`:

- Context: zero-knowledge model, recovery vault required, hosting реальность (no SGX).
- Options: 3 (Simple HMAC, OPAQUE, Signal SVR).
- Decision: с обоснованием.
- Consequences: что меняется в T2 endpoint в server-requirements, что в client-requirements RecoveryKeyVault port.
- Exit ramp: если выбрали simple HMAC сейчас, как мигрировать на OPAQUE / SVR позже (server-side migration job, не требует client re-onboarding).
- Regret conditions: когда переосмыслить.

---

## Контекст из обсуждения 2026-06-26

Mentor-сессия выявила, что T2 vault counter в server-requirements.md v2 — это **единственный sanctioned exception** из zero-knowledge принципа, поэтому он требует особо тщательного дизайна.

Я в обсуждении упомянул Signal SVR как reference, но владелец справедливо заметил: **«SVR требует SGX, у нас его нет»**. Это закрытие важного gap в V2 sketch'е.

Я также упомянул OPAQUE protocol как альтернативу, но **не разобрал детали**. Production deployments OPAQUE минимальны, library availability на Kotlin плохая. Без deep research нельзя коммититься.

Simple HMAC counter — **наиболее реалистичный путь для MVP**, но это нужно **подтвердить** через research, не предположить.

### Связь с TASK-6 и TASK-21

- **TASK-6 (Root Key Hierarchy + Owner Recovery)** — currently paused. Включает recovery vault flow. При re-start этой task'и **обязательна** ссылка на decision из TASK-59. Иначе TASK-6 завязнет на wrong protocol choice.
- **TASK-21 (Account Recovery + 2FA escrow)** — multi-factor recovery. Использует vault counter как один из факторов. Тоже нужна decision.
- **TASK-39 (Social recovery)** — частично использует vault counter pattern для peer attempt limiting. Менее direct dependency, но related.

## Что НЕ делать в TASK-59

- НЕ реализовывать vault counter — это будет внутри TASK-6 / TASK-21 implementation.
- НЕ выбирать KDF parameters (Argon2id memory/iterations) — это уже зафиксировано в spec 018 F-5 (см. SRV-CRYPTO-PARAMS-REVIEW).
- НЕ проектировать full recovery flow (UI / passphrase entry / social recovery flow) — это application-level concern.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Comparison: Signal SVR (Intel SGX enclave) vs OPAQUE protocol (RFC draft) vs simple server-side HMAC counter — security guarantees, complexity, hosting requirements, production deployments
- [ ] #2 Decision: какой подход берём для T2 vault counter в нашем сервере, с учётом что нашего hosting (Cloudflare/VPS) **нет SGX** — что лучшее доступное
- [ ] #3 Threat model: что закрывается каждым подходом (Clear App Data bypass, factory reset bypass, root bypass, insider attack, hosting compromise, DB dump)
- [ ] #4 API sketch: PUT /vaults/{id}, POST /vaults/{id}/attempt — точный protocol с proof bytes, response codes, rate-limit window
- [ ] #5 Документ docs/dev/decisions/2026-XX-XX-recovery-vault-counter.md с rationale
- [ ] #6 TASK-6 (Root Key Hierarchy) и TASK-21 (Account Recovery) descriptions обновлены ссылкой на этот research
<!-- AC:END -->
