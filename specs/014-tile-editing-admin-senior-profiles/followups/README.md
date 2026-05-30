# Spec 014 follow-ups

Эта папка содержит **отложенные phases** спеки 014. F-014.0 закрыта как foundation; всё что не сделано — здесь.

## Index

| File | Phase | Status | Blockers |
|---|---|---|---|
| [F-014.0b-production-wire-up.md](F-014.0b-production-wire-up.md) | Production wire-up F-014 composables в HomeScreen/FlowScreen/EditorComponent | 🟡 deferred | architectural decision needed + 5-7 дней работы + 2-emulator smoke |
| [F-014.1-server-backup.md](F-014.1-server-backup.md) | Firestore backup + cross-device sync + Multi-config UI | 🟡 deferred | F-4 (Google Sign-In) + TODO-RESEARCH-009/010 |
| [F-014.2-encryption.md](F-014.2-encryption.md) | E2E encryption Config Document перед push на Firestore | 🟡 deferred | F-5 (encryption spec) + F-014.1 |

## Чем НЕ являются эти файлы

- Не tasks.md — нет executable task IDs.
- Не plan.md — нет binding architectural decisions.
- Не spec.md — концептуальный scope наследуется из parent spec.md.

Это **placeholder follow-up docs** для resumption работы позже.

## Как resumе work

Каждый файл содержит «How to resume work» section с:
1. Какие blockers закрыть.
2. Какие architectural decisions clarify.
3. Какую ветку открыть.
4. В каком порядке implement.

Не подменяет полноценный `/speckit.clarify` → `/speckit.plan` → `/speckit.tasks` cycle на resume.

## Cross-reference в parent spec.md

См. [../spec.md](../spec.md) §Phase Dependencies для overarching context.

См. [../tasks.md](../tasks.md) §"Deferred to follow-ups" (см. update от 2026-05-30).
