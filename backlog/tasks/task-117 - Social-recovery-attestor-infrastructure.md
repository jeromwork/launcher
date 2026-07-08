---
id: TASK-117
title: 'Decision: Social recovery + attestor infrastructure'
status: Discussion
assignee: []
created_date: '2026-07-08 06:17'
updated_date: '2026-07-08'
labels:
  - decision
  - crypto
  - recovery
  - phase-3
milestone: m-2
dependencies:
  - TASK-101
  - TASK-105
  - TASK-108
  - TASK-116
priority: high
ordinal: 117000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Общий криптографический механизм** для случаев когда «уже доверенный участник подтверждает что заявителю можно доверять».

Ключевое наблюдение (владелец, mentor-сессия 2026-07-08): **два use case имеют одинаковую структуру**:

**Case 1 — Recovery через доверенных родственников**:
Валентина потеряла планшет. Ставит лаунчер на новом устройстве. Хочет **не вводить passphrase** (бабушка их теряет). Дочка Таня на своём устройстве видит уведомление: «мама восстанавливает доступ. Это она?». Таня нажимает «да» → её launcher подписывает attestation → передаёт на сервер восстановления мамы → мама восстанавливается **без passphrase**.

**Case 2 — Cross-app trust на том же устройстве**:
Бабушка ставит мессенджер на своём планшете (лаунчер уже стоит). Мессенджер запрашивает подтверждение доверия. **Лаунчер на том же устройстве** выступает как attestor (он уже unlocked и знает бабушкину identity). Бабушка тапает подтверждение в лаунчере → мессенджер восстанавливается.

**Криптографически это одно и то же**: attestor подписывает attestation о заявителе. Проверяющий (сервер / другое устройство) верифицирует подпись. Разница только **где живёт attestor** (на другом устройстве человека vs на том же устройстве что заявитель).

**Что происходит по шагам (нормальный сценарий Case 1):**

1. Валентина ставит лаунчер на новом планшете. Выбирает «Восстановить через родственников» (вместо «Восстановить через passphrase»).
2. Лаунчер идёт на сервер: «нужна attestation для identity=X на устройстве Y». Сервер регистрирует `AttestationRequest`.
3. Сервер шлёт push уведомления attestor'ам Валентины (список хранится encrypted, лаунчер Валентины знает только hash списка при recovery — pub/sub через identity, не через plaintext contact list).
4. У дочки Тани приходит push: «мама Валентина восстанавливает доступ на устройстве X. Подтвердить?».
5. Таня открывает свой лаунчер, видит **iconic pairing challenge** (TASK-116) — три иконки. Таня и мама должны увидеть одну и ту же (мама видит одну в своём лаунчере).
6. По телефону: «мам, что видишь?» — «огонёк красный». Таня нажимает 🔥. Attestation подписана.
7. Attestation отправляется на сервер. Сервер: threshold достигнут (например, 1-of-2 достаточно для family segment).
8. Сервер отдаёт лаунчеру Валентины **зашифрованный recovery key**, зашифрованный для набора attestor'ов Валентины. Recovery key объединяется из shares attestor'ов (или sealed для каждого — зависит от threshold scheme).
9. Лаунчер Валентины расшифровывает recovery key через свой attestor set → расшифровывает backup → restored.

**Что происходит по шагам (Case 2 — cross-app trust)**:

1. Бабушка в лаунчере тапает плитку «Установить мессенджер» (см. TASK-115).
2. После install мессенджера — мессенджер запрашивает `AttestationRequest` через сервер.
3. Лаунчер на том же устройстве (local attestor) видит запрос через FCM push / local IPC.
4. Лаунчер показывает overlay: **iconic pairing challenge** — «Выберите эту иконку в мессенджере: 🔥».
5. Бабушка тапает 🔥 в мессенджере.
6. Мессенджер отправляет `chosen_index` → сервер сравнивает с seed → match → attestation approved.
7. Сервер отдаёт мессенджеру его recovery key.
8. Мессенджер восстанавливается.

Обе процедуры используют **тот же protocol**. Разница — где attestor (remote peer vs local launcher) и какая threshold policy (family 1-of-2, clinic может быть 2-of-3).

## Зачем

**Заменяет passphrase для recovery**. Бабушки теряют бумажки, забывают passphrases. Social recovery — «дочка помнит вместо меня». Uses proven pattern (Matrix cross-signing, Signal linked device, Google account recovery via trusted contacts).

**Cross-app trust — bonus use case того же механизма**. Не нужно строить **две отдельные infrastructure**. Один protocol обслуживает два user-visible flows.

**Threshold configurable per preset**:
- Family MVP: 1-of-2 (одна дочка достаточна) или even 1-of-1 (сама бабушка через launcher на другом её устройстве).
- Clinic: 2-of-3 (два врача из клинического staff'а).
- Self-managed: launcher на другом устройстве самого пользователя = его же attestor.

## Что входит технически (для AI-агента)

**Domain layer**:
- `Attestor` role — value type, `{ identity_id, attestor_type: HUMAN | APP, cross_app_attestation_key_pub }`.
- `AttestationRequest` — `{ request_id, requester_identity_id, target_action, threshold_policy, ttl_seconds }`.
- `AttestationResponse` — `{ request_id, attestor_identity_id, signed_attestation, timestamp }`.
- `ThresholdPolicy` — `N-of-M` scheme per preset.
- Ports: `AttestationRequester`, `AttestationResponder`, `AttestationVerifier`.

**Wire format** (per TASK-16 discipline):
- Attestation payload — `{ schemaVersion, request_id, target_action, target_device, iconic_challenge_seed, valid_until }`.
- Signed with `cross_app_attestation_key` — separate key from identity_key, wrapped in root_key, published in identity-link.

**Server endpoints**:
- `POST /v1/attestations/request` — requester создаёт request, сервер fanouts push attestor'ам.
- `POST /v1/attestations/respond` — attestor publishes signed attestation.
- `GET /v1/attestations/status/{request_id}` — polling для requester (threshold reached?).
- `POST /v1/attestations/recovery-key/claim` — requester claims decrypted recovery key when threshold reached.

**Recovery key storage model** (critical):
- Root key backup зашифрован **не одним ключом**, а **N-of-M threshold scheme** (Shamir's Secret Sharing или aggregate sealed_boxes для attestor set).
- При setup: root_key разделён на shares → каждый attestor получает свой share (sealed для его public key).
- При recovery: attestors ответили → shares агрегируются → root_key recovered.

**Consumed components**:
- **TASK-116** — iconic pairing challenge для UI визуального сравнения между requester и attestor.
- **TASK-105** — zero-trust baseline для attestation endpoints.
- **TASK-108** — metadata privacy T0 (сервер видит `identity_id ↔ attestor_identity_id` — mapping без имён, что уже leak, но acceptable T0).

**Preset fields**:
- `threshold_policy` per action type (recovery / cross-app / sensitive-action).
- `default_attestor_set` — attestor'ы по умолчанию (для self-managed — сам пользователь на других устройствах).
- `attestation_ttl_seconds` — сколько attestor может ответить.

## Состояние

**Discussion, 2026-07-08.** Концепция сформулирована в mentor-сессии TASK-115 после наблюдения владельца что social recovery и cross-app trust — one and the same криптографически.

**Relationship to TASK-101** (Peer confirmation on recovery, Draft):
- TASK-101 = **specific recovery case** (peer confirmation flow).
- TASK-117 = **general attestation infrastructure** обслуживающая multiple use cases.
- При закрытии Decision block TASK-117: либо TASK-101 supersedes → TASK-117 (redirect), либо TASK-101 остаётся как preset-specific policy, TASK-117 = mechanism. Решение в Session 2.

**Ещё открыто**:
- Формальный Decision block не написан.
- Threshold scheme — Shamir's Secret Sharing vs aggregate sealed_boxes.
- Attestor discovery при recovery (как лаунчер новой Валентины узнаёт кто её attestor'ы если он ещё ничего не расшифровал).
- Metadata leak оценка (сервер видит attestor mapping — что защищает).
- Attestation key rotation / invalidation (взаимодействие с TASK-103 remote lock).
- UI для attestor'а — точная формулировка вопроса чтобы избежать auto-approve.
- Anti-coercion mechanism (если attestor'а физически заставляют — как выявить).

---

## Пример сценария (use-case)

**Family segment — recovery без passphrase**:

Валентина потеряла планшет. Купила новый Xiaomi Pad. Ставит лаунчер. При первом запуске лаунчер спрашивает:
- «Восстановить существующий аккаунт?» → «Да»
- «Как восстановить?» → «Через родственников» (альтернатива — «Через кодовую фразу»)
- «Введите ваш Google account» → Google Sign-In (это identity anchor)

Лаунчер отправляет `AttestationRequest`. Дочка Таня и внучка Аня получают push «мама/бабушка восстанавливает доступ. Подтвердить?».

Таня открывает свой лаунчер, видит iconic challenge, звонит маме: «выбери из трёх иконок — какая у тебя?». Мама (на планшете видит одну иконку): «огонёк». Таня нажимает 🔥. Аня отдельно делает то же самое.

Threshold 2-of-3 достигнут (Таня + Аня, третий attestor — сама Валентина на своём умершем планшете не отвечает). Лаунчер Валентины получает recovery key, расшифровывает backup. Contacts, темы, настройки восстановлены.

**Family segment — cross-app trust**:

Валентина через 3 дня хочет мессенджер. Плитка в лаунчере (TASK-115) → Play Store → мессенджер установлен → attestation request. **Лаунчер на том же устройстве** — attestor. Threshold 1-of-1 (одного лаунчера достаточно для family segment cross-app). Overlay bubble показывает иконку. Бабушка тапает в мессенджере. Мессенджер восстановлен.

**Clinic segment (hypothetical Phase-3+)**:

Пациент клиники. При setup клиника задала `threshold_policy = 2-of-3` из `[patient_launcher_other_device, clinic_admin_1, clinic_admin_2]`. Recovery требует 2 подтверждения из этих 3. Iconic challenge между устройствами позволяет verify identity face-to-face или через звонок.

**Self-managed segment**:

Пользователь с двумя устройствами. Attestor'ом одного устройства выступает его же лаунчер на другом устройстве. Threshold 1-of-1 достаточно (сам подтвердил себе). Iconic challenge защищает от attack scenario когда одно устройство украдено — attacker без второго не может пройти challenge.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — 2026-07-08 (concept)

Ключевое наблюдение владельца в контексте обсуждения TASK-115 (cross-app trust bootstrap):

> «Social recovery — это когда группа знакомых подтверждает, что это именно пользователь. Cross-app trust — это когда лаунчер подтверждает, что это именно бабушкин мессенджер. Криптографически это одно и то же — уже доверенный участник подписывает утверждение о заявителе.»

Значит **не создаём два отдельных механизма**, а один общий attestation protocol с двумя use cases (remote peer attestor vs local launcher attestor).

**Threat model preliminary**:
- Google Sign-In compromised alone → attacker не имеет cross_app_attestation_key бабушки → не может выпустить attestation.
- Attacker с телефоном скомпрометированного attestor'а → закрывается TASK-103 remote lock.
- Coercion → open problem, не решается механикой.
- Metadata leak на сервере (`identity_id ↔ attestor_identity_id` visible) → T0 acceptable per TASK-108, T2 future.

**Relationship to TASK-101** — не superseded pattern, оба останутся. TASK-101 = policy для recovery specific, TASK-117 = mechanism universal. Session 2 конкретизирует.

### Decision (English, mutable pre-implementation) 🔒

*Not yet written. Session 2 formalizes when moving toward implementation of TASK-115 или recovery UX.*

<!-- SECTION:DISCUSSION:END -->
