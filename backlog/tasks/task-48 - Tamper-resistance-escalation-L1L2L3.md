---
id: TASK-48
title: Tamper-resistance escalation L1+L2+L3
status: Draft
assignee: []
created_date: '2026-06-23 05:45'
updated_date: '2026-06-23 06:36'
labels:
  - phase-5
  - l-spec
  - l-15
  - security
  - anti-tamper
  - parking-lot
milestone: m-4
dependencies:
  - TASK-15
priority: low
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **TASK-57 revisit (2026-07-08)**: концепция проверена против CLAUDE.md rule 13 (zero-knowledge server posture). **Rule 13 не пересекается** — TASK-48 про client-side attestation (Play Integrity / SafetyNet / R8 obfuscation) для защиты APK от modification, не про server-side visibility. Rule 12 (zero-trust) уже покрывает server-side JWT validation (L1). Task остаётся в parking-lot (m-4) с priority Low до появления abuse evidence через TASK-15 telemetry. Sketch TASK-57 предполагал возможный close/reset — не требуется.

## Что это простыми словами

Эскалация anti-tamper защиты подписки (TASK-15) с уровня L1 (server JWT validation) до L2 (Play Integrity API) и L3 (SafetyNet + custom obfuscation). Активируется если статистика покажет abuse через модифицированные APK.

## Зачем

L1 (текущий план в TASK-15) защищает от basic patching. L2+L3 — от sophisticated attacks. Build только если evidence показывает что abuse реально есть (не speculative).

## Состояние

**Parking lot.** Per decision 03 §Уровни усиления. Зависит от TASK-15.

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-15: Tamper-resistance escalation L1+L2+L3.

ЧТО СТРОИМ:
Эскалация TASK-15 server JWT validation (L1) до:
- L2: Play Integrity API integration (Google Play attestation).
- L3: SafetyNet + custom obfuscation (R8 + Proguard config + native обфускация в crypto layer).

ЗАЧЕМ:
Активируется если abuse через modified APKs реально измерен (НЕ speculative).

SCOPE ВКЛЮЧАЕТ:
- Play Integrity API client integration.
- Server-side Play Integrity attestation verification.
- SafetyNet API (deprecating, но useful для legacy devices).
- Native crypto obfuscation (R8 + Proguard + native lib hardening).
- ADR с trade-offs (отказ от LineageOS / non-Play distribution users).

DEPENDENCIES:
- TASK-15 (S-10 baseline).
- Measurable abuse statistics (precondition).

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
