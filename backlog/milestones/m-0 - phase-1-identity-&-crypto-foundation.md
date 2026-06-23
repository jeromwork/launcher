---
id: m-0
title: "Phase-1: Identity & Crypto Foundation"
---

## О чём эта фаза

Базовый слой identity и криптографии, на котором стоят все cloud-фичи. После Phase-1 любой `primary user` может настроить cloud-доступ (Google-вход), `remote administrator` может его удалённо настраивать (через pairing), и оба могут восстановить аккаунт на новом устройстве без потери данных.

**До этой фазы** — устройство работает только локально, никаких cloud-функций нет.
**После этой фазы** — фундамент готов, Phase-2 строит видимые продуктовые слайсы.

## Какие задачи входят

- **TASK-1** F-3 — Wizard onboarding + localization (мастер настройки + 9 локалей).
- **TASK-2** F-CRYPTO — Core crypto module (libsodium-kmp, AeadCipher, Curve25519, Ed25519, HKDF).
- **TASK-3** F-4 — AuthProvider + Google Sign-In (identity foundation).
- **TASK-4** F-5b — Own config E2E encryption envelope (шифрование настроек end-to-end).
- **TASK-5** F-5c — FCM config-updated push trigger (механизм доставки обновлений конфига).
- **TASK-6** F-5 — Root Key Hierarchy + Owner Recovery (главный ключ + восстановление).

## Какие ключевые решения зафиксированы в этой фазе

- **Cloud-deferred architecture** — sign-in происходит при первом cloud-action, не при запуске. Local-only mode всегда бесплатен.
- **Каждое устройство = свой Google UID** (anonymous Firebase Auth удалён).
- **ConfigDocument E2E encryption через hybrid envelope** (Curve25519 + AEAD), сервер видит только blob.
- **F-CRYPTO validation strategy** = RFC KAT + Wycheproof + property + industrial reference (заменяет «friend review»).
- **F-5 Root Key переопределён** как single-owner encryption + recovery (Google Sign-In + passphrase в Firestore + Android Autofill); multi-admin envelope перенесён в S-2 enhancement notes.
- **ionspin libsodium-kmp** как primary crypto library; library живёт в launcher-репо до появления 2-го потребителя.

## Где искать решения и обсуждения

- **Decisions**: `docs/product/decisions/2026-06-15-deferred-cloud/` (6 решений), `docs/product/decisions/2026-06-17-f-crypto-validation/`, `docs/product/decisions/2026-06-19-2fa-admin-migration/`.
- **Спеки**: `specs/015-wizard-localization-senior-ui/`, `specs/016-f-crypto-core-module/`, `specs/017-f4-auth-provider/`, `specs/018-f5-config-e2e-encryption/`, `specs/019-f5c-fcm-config-updated/`, `specs/020-f5-root-key-hierarchy-recovery/`.
- **Git history**: `git log --grep="F-CRYPTO\|F-4\|F-5"` для конкретных коммитов.

## Состояние

**5/6 Done** (TASK-1..5). TASK-6 — In Progress на ветке `020-f5-root-key-hierarchy-recovery`.

Фаза близка к закрытию.
