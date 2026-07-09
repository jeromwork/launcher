---
id: TASK-6
title: Root Key Hierarchy + Owner Recovery
status: Done
assignee: []
created_date: '2026-06-23 05:01'
updated_date: '2026-07-09 09:20'
labels:
  - phase-1
  - F-feature
  - crypto
  - recovery
  - one-way-door
  - f-5
milestone: m-0
dependencies:
  - TASK-2
  - TASK-3
  - TASK-4
  - TASK-5
  - TASK-49
references:
  - specs/task-6-root-key-hierarchy-recovery/
priority: high
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Самый важный «ключ от всех замков» для одного пользователя.

**Что происходит по шагам (нормальный сценарий):**
1. Пользователь устанавливает приложение, заходит через Google-вход.
2. Придумывает свой пароль (passphrase — секретное слово, которое знает только он, нигде не сохраняется в открытом виде).
3. Приложение из Google-аккаунта + пароля собирает «главный ключ» (root key).
4. От главного ключа автоматически рождаются «дочерние» ключи: один для шифрования настроек, второй — для фото, третий — для будущих модулей.
5. Все дочерние ключи изолированы друг от друга (KeyRegistry — внутренний справочник этих ключей).

**Что происходит при восстановлении (бабушка потеряла телефон):**
1. Покупает новый телефон, ставит приложение.
2. Заходит через тот же Google-аккаунт.
3. Вводит свой пароль (тот же что придумала первоначально).
4. Главный ключ собирается заново — точно такой же.
5. Старые зашифрованные настройки и контакты расшифровываются — ничего не потеряно.

**Если устройство без Google-сервисов (например, Huawei):**
- Приложение работает в локальном режиме (только это устройство, без облака).
- Никаких ошибок и зависаний — просто часть cloud-функций недоступна.

## Зачем

Без этого пользователь, потерявший телефон, теряет всё: конфигурацию, контакты с фото, историю. С этим — заменяет устройство и продолжает работу. Также готовит фундамент для всех будущих cloud-фич (мессенджер, фотоальбом, caregiver).

## Что входит технически (для AI-агента)

- `KeyRegistry` port + impl с per-identity namespacing (изоляция ключей разных пользователей в Android Keystore).
- Миграция `ConfigCipher2` (шифрование конфига из TASK-4) на `KeyRegistry` без изменения уже зашифрованных данных (byte-equal preserved).
- Recovery flow: 3 Compose-экрана (Setup при первом запуске / Entry при восстановлении / Fallback при забытом пароле).
- Интеграция с Android Autofill (Google Password Manager автоматически предлагает сохранить/подставить пароль).
- `NoOpRecoveryKeyBackup` adapter для устройств без GMS (Huawei) + автоматическое определение в DI.
- Identity isolation cascade wipe — при logout вытираются все ключи этого Google UID.

## Состояние

**В работе.** Спека написана 2026-06-22, реализация идёт на ветке `020-f5-root-key-hierarchy-recovery`. Закрывает Phase 1.

---

## Готовый промт для `/speckit.specify`

> Можно скопировать целиком и вставить в `/speckit.specify`. Этого достаточно для понимания фичи без дополнительных вопросов.

```
Реализуй F-5: Root Key Hierarchy + Owner Recovery.

ЧТО СТРОИМ:
Иерархию криптографических ключей одного пользователя: Google-вход + пароль → главный ключ (root key) → производные ключи для разных частей системы (конфиг, фото, будущие модули) через KeyRegistry.
Восстановление доступа на новом устройстве: тот же Google-аккаунт + вспомнить пароль → главный ключ собирается заново → все зашифрованные данные снова доступны.

ЗАЧЕМ:
Закрывает Phase 1. Без этого пользователь, потерявший телефон, теряет всё. Фундамент для всех cloud-фич (мессенджер, фотоальбом, caregiver).

SCOPE ВКЛЮЧАЕТ:
- KeyRegistry port + Android-impl с per-identity namespacing.
- Migration ConfigCipher2 (из TASK-4 / spec 018) на KeyRegistry без break-changes в ciphertext.
- Recovery Compose screens: Setup / Entry / Fallback.
- Android Autofill интеграция (newPassword + password autofill hints).
- NoOpRecoveryKeyBackup adapter для non-GMS (Huawei) + GMS detection в DI.
- Identity isolation cascade wipe при logout.
- Документация recovery-flow.md + key-hierarchy.md на простом русском.

SCOPE НЕ ВКЛЮЧАЕТ:
- Social recovery («друг помогает вспомнить пароль») — TASK-39 в Phase 5.
- Multi-admin envelope — отложено в S-2 enhancement notes.
- Key rotation / forward secrecy — TASK-41 в Phase 5.
- Pair-key recovery через 2FA escrow — TASK-21 (P-6) в Phase 3.

DEPENDENCIES:
- TASK-2 (F-CRYPTO core) — done.
- TASK-3 (F-4 AuthProvider + Google Sign-In) — done.
- TASK-4 (F-5b config E2E encryption) — done.
- TASK-5 (F-5c FCM push) — done.

ACCEPTANCE CRITERIA (проверяет пользователь):
- Зашёл через Google + ввёл пароль на новом устройстве — конфиг и контакты восстановились.
- Забыл пароль — экран Fallback позволяет начать заново без потери Google-аккаунта.
- Huawei без Google-сервисов — приложение работает в локальном режиме без падений.
- Перешёл со старого варианта шифрования — старые зашифрованные данные читаются.
- Android Autofill подхватывает пароль автоматически.
- Документация recovery-flow.md написана простым русским.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — Setup wizard от первого запуска до восстановления.
- physical device #1 (currently Xiaomi 11T) (physical) — миграция ciphertext из spec 018 byte-equal.
- Fake adapter NoOpRecoveryKeyBackup — non-GMS path юнит-тестами.

CONSTITUTION GATES:
- Rule 1 (domain isolation): KeyRegistry — port в core/keys/, adapter в android/.
- Rule 2 (ACL): Android Keystore не вытекает в domain.
- Rule 3 (one-way door): иерархия ключей — фиксируется навсегда; exit ramp — key rotation TASK-41.
- Rule 5 (wire format): RecoveryKeyBackupBlob schemaVersion=1, roundtrip + backward-compat test.
- Rule 6 (mock-first): FakeKeyRegistry + FakeRecoveryKeyBackup для тестов.

EFFORT: Large (~2-3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Upload-half: зашёл через Google + ввёл пароль → recovery blob улетел на Cloudflare Workers KV. DONE 2026-06-30 на Xiaomi 11T (`backup/<firebase-sub>/v1.json` verified). **Cross-device half** (recovery на втором устройстве с тем же паролем) → перенесён в **TASK-118 AC #1**.
- [x] #2 [hand] Забыл пароль — увидел экран Fallback, могу начать с чистого листа без потери Google-аккаунта. **DONE 2026-07-09 на Xiaomi 11T** — 3 wrong passphrases → RecoveryFallbackScreen → «Настроить как новое устройство» → **double-confirm AlertDialog «Начать с нуля?»** → «Да, начать заново» → `F5Wipe: recovery blob deleted from Worker` + `F5Wipe: attempt counter cleared` → RecoveryPassphraseSetupScreen открывается заново. Google-аккаунт сохранён.
- [→ TASK-58 AC #1] #3 [hand] Huawei без Google-сервисов — приложение работает в локальном режиме. **Перенесён в TASK-58** (нет Huawei устройства).
- [N/A] #4 [hand] Migration со старого варианта шифрования — N/A. MVP, в production никто не пользуется spec-018 ciphertext'ом — мигрировать нечего. Снято.
- [→ TASK-118 AC #2] #5 [hand] Android Autofill cross-device — **перенесён в TASK-118** (требует два физических устройства).
- [x] #6 [hand] Документация recovery-flow.md написана простым русским, бабушка-админ может прочитать и понять — DONE (T673 committed 2026-06-29).
- [x] #7 [auto:deferred-local-emulator] tasks.md 14/14 emulator-gated tasks — все закрыты (T642, T649-T652, T669-T670, T672, T630 N/A).
- [x] #8 [auto:deferred-physical-device] tasks.md 6/6 physical-device tasks — закрыты T643, T681, T684, T685; **T682 closed 2026-07-09** (Fallback flow smoke на Xiaomi 11T — включает double-confirm dialog + server blob delete + counter clear + Setup screen re-open; server KV пустой после wipe verified через `wrangler kv key list --namespace-id 13e77e09a77a4faba6e743f1a59feff0 --remote`).
- [x] #9 [auto:deferred-external] tasks.md 1/1 — **T686 closed 2026-07-09** (owner peer-review `docs/recovery-flow.md` — принцип и логика recovery/fallback потока описаны корректно. Минорные copy-расхождения в UI-названиях экранов и кнопок (owner подтвердил не важно, docs всё равно будут переписаны) не влияют на архитектурную корректность документа).
<!-- AC:END -->

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
**Done 2026-07-09** — all AC closed on Xiaomi 11T against the deployed
auth-worker + backup-worker (post-TASK-119 architecture).

Two-branch history:
- Upload half (AC #1) closed 2026-06-30 during initial task-6 wiring.
- Fallback half (AC #2, #8, #9) closed 2026-07-09 after TASK-119 merged.
  Discovered a gap in the pre-existing Fallback wiring:
  `renderRecoveryFallbackStep.onSetupAsNewDevice` was a direct
  `::renderRecoverySetupStep` reference — no double-confirm dialog, no
  server-side blob delete, no attempt-counter reset. Fixed by:
  1. Adding an `AlertDialog` inside `RecoveryFallbackScreen` («Начать с
     нуля?» / «Да, начать заново» / «Отмена») so a single mis-tap can't
     wipe the blob.
  2. New `wipeAndRestartSetup()` in `FirstLaunchActivity` — after the
     user confirms, calls `recoveryKeyBackup.deleteBlob(stableId)`,
     `attemptCounter.clear(stableId)`, then `renderRecoverySetupStep()`.
  3. Failure on deleteBlob is logged and the wizard still advances —
     the next Setup upload overwrites the orphan blob under the same
     `authed.stableId` in the backup Worker's KV.

Verification evidence:
- Fresh Setup + Recovery both work in a single fetchBlob call (no
  retries, no races) — inherited from TASK-119 server rework.
- Fallback flow verified: 3 wrong passphrases → `RecoveryFallbackScreen`
  → destructive button → confirm dialog → `F5Wipe: recovery blob
  deleted from Worker for stableId=f808632f-...` + `F5Wipe: attempt
  counter cleared` → `RecoveryPassphraseSetupScreen` re-opens.
- Server KV empty after wipe verified via `wrangler kv key list
  --namespace-id 13e77e09a77a4faba6e743f1a59feff0 --remote` → `[]`.

Deferred to follow-ups (not blockers):
- AC #3 → TASK-58 (Huawei / non-GMS smoke, no hardware available).
- AC #5 → TASK-118 (Autofill cross-device, needs two physical devices).
- Old FirebaseUID-keyed blob (`backup/GUOJK.../v1.json`) already
  cleaned up during TASK-119 verification — no migration needed.
<!-- SECTION:FINAL_SUMMARY:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
On Paused: spec 020 написана и лежит в untracked файлах ветки 020-f5-root-key-hierarchy-recovery (stash при switch ветки). Ждёт закрытия TASK-49 (Cloud Feature Inventory + Offline-First Architecture), потому что TASK-49 определяет 'что считать первым cloud-action' для FR-008 setup trigger. Vault → RecoveryKeyBackup переименование уже применено в spec 020. После закрытия TASK-49: возврат на ветку 020, git stash pop, продолжить с /speckit.clarify.
<!-- SECTION:NOTES:END -->
