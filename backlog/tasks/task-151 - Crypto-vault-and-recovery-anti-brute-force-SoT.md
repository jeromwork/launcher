---
id: TASK-151
title: Crypto key-vault + recovery anti-brute-force zone SoT (research-grounded)
status: Done
updated_date: '2026-07-22 00:00'
assignee: []
created_date: '2026-07-22 14:10'
labels:
  - architecture
  - crypto
  - docs
dependencies: []
milestone: m-1
priority: high
ordinal: 151000
decision-supersedes:
  - TASK-59
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

crypto-key-hierarchy.md имел две open-зоны, помеченные «read TASK-112/TASK-59»: (1) key-vault boundary (как хранить/использовать ключи не отдавая приватные наружу), (2) recovery anti-brute-force (восстановление vault по паролю без server-brute-force). Эта задача доводит обе до ecs-полноты **по подтверждённому порядку**: сначала research стандартов индустрии + юзкейсов, потом валидация против наших тасков, потом запись в файл.

Только документы, ноль кода.

## Зачем

Убрать «read TASK-X» из key-hierarchy; дать архитектуру, основанную на индустрии, а не на наших недорешённых тасках.

## Что входит технически (для AI-агента)

- **Key vault** (в crypto-key-hierarchy.md §Key vault): operation-on-vault consensus (Apple SecureEnclave / AWS KMS / WebAuthn); Curve25519-not-in-HW → wrapped-raw fallback (Signal pattern); `KeyVaultPort` (operations, typed handles, narrow export hatch, capability level surfaced); libs (Android Keystore/CryptoKit platform, Tink Apache, libsodium ISC). **Подтверждает** TASK-112 решение (operation-on-vault + narrow exportDerivedKey + newtype) как industry-standard.
- **Recovery anti-brute-force** (§Recovery anti-brute-force): landscape (SVR/HSM · OPAQUE · threshold-OPRF · Argon2-client · SSS) с честными лимитами; MVP-выбор (no HSM) = Argon2id над HIGH-entropy recovery-code + optional SSS; инвариант «single-Worker PIN recovery ≠ dump-resistance»; exit ramp через VaultRecoveryPort → threshold-OPRF (opaque-ke MIT) → HSM (SVR3 design). **Supersedes TASK-59** incomplete framing. Residual (PIN vs recovery-code) → mentor/preset.

## Состояние

**In Progress.** Research (2 subagents, industry standards + use cases) проведён; обе секции написаны в crypto-key-hierarchy.md. Ноль кода.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] §Key vault написан: operation-on-vault consensus + Curve25519 wrapped-raw + KeyVaultPort + typed keys + capability level + libs; grounded в industry (SecureEnclave/KMS/WebAuthn с URL)
- [x] #2 [hand] §Recovery anti-brute-force написан: полный landscape с честными лимитами + MVP-выбор (Argon2-over-high-entropy) + инвариант «single-Worker≠dump-resistance» + exit ramp + ready libs (opaque-ke/libsodium с лицензиями)
- [x] #3 [hand] Валидация против наших тасков: TASK-112 подтверждён как industry-standard; TASK-59 superseded (research resolves); residual product-choice помечен → mentor/preset
- [x] #4 [hand] Open-буллет в key-hierarchy обновлён (архитектура в файле, не «read TASK-X»)
- [x] #5 [hand] Zero production-code
<!-- AC:END -->
