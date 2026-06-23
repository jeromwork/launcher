---
id: TASK-31
title: Caregiver Remote Invite + LinkInvitePairingChannel
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
updated_date: '2026-06-23 06:32'
labels:
  - phase-4
  - v-spec
  - v-6
  - caregiver
  - role-based-access
  - b2b-foundation
milestone: m-3
dependencies:
  - TASK-8
  - TASK-21
priority: high
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Расширение модели ролей: помимо `remote administrator` (полный доступ к настройкам), вводится роль `restricted caregiver` (ограниченный помощник). Им можно дать ограниченный доступ через подписанную ссылку-приглашение, без полного pairing.

**Что происходит по шагам:**
1. `Remote administrator` в своём приложении выбирает «Пригласить ограниченного помощника».
2. Указывает уровень доступа: «видит SOS», «может позвонить», «НЕ видит фото», «НЕ редактирует config».
3. Указывает срок действия (например, 30 дней).
4. Получает подписанную invite-ссылку.
5. Отправляет ссылку приглашённому (через любой канал — email, WhatsApp, SMS).
6. Приглашённый открывает ссылку → устанавливает приложение в режиме `restricted caregiver` → автоматически paired с устройством primary user'а с ограниченным envelope.
7. Видит только разрешённую информацию (SOS, кнопку «позвонить»), не видит запрещённую (фото, полный config).
8. Через 30 дней — membership автоматически истекает; permissions revoked.

**Возможные конфигурации (use-cases):**
- Family-вариант: бабушка + дочка как admin + сиделка как caregiver на 3 месяца.
- Clinic-вариант: пациент + opekun-доктор как admin + медсестра-помощник как caregiver на смену.
- Корпоративный: сотрудник + HR как admin + IT-support как caregiver на 1 неделю.

## Зачем

Это **основа B2B-расширения** (TASK-34 L-1 clinic integration). Также для семей: ситуации когда есть третье лицо помогающее ситуативно — нянька, сиделка, временный помощник.

## Что входит технически (для AI-агента)

- `LinkInvitePairingChannel` adapter (signed invite link, второй adapter `PairingChannel` помимо QR из TASK-8).
- Role-based access на сервере + envelope filtering на клиенте (caregiver получает урезанный envelope без некоторых полей).
- TTL membership с auto-expiry (cron / WorkManager).
- `Restricted caregiver UI`: SOS feed + call action + опциональные другие, без album.
- Audit log integration (через TASK-32 V-7).

## Состояние

**Planned.** Зависит от TASK-8 (S-2 admin/pairing foundation), TASK-21 (P-6 recovery extension), TASK-32 (V-7 audit log).

---

## Готовый промт для `/speckit.specify`

```
Реализуй V-6: Restricted Caregiver Remote Invite + LinkInvitePairingChannel.

ЧТО СТРОИМ:
Дополнительная роль `restricted caregiver` с ограниченным доступом, paired через подписанную invite-ссылку (НЕ QR). LinkInvitePairingChannel — второй adapter PairingChannel помимо QRPairingChannel (TASK-8). Role-based access на сервере + envelope filtering на клиенте (caregiver получает урезанный envelope). TTL membership с auto-expiry.

Use-case examples: family (постоянная сиделка), clinic (медсестра на смену), B2B (IT-support на время инцидента).

ЗАЧЕМ:
Foundation для B2B расширения (TASK-34 L-1 clinic). Также для семей с временными помощниками.

SCOPE ВКЛЮЧАЕТ:
- LinkInvitePairingChannel adapter (signed invite link с TTL).
- Role schema extension: roles = [admin, caregiver]; caregiver has accessLevel mask.
- Server-side role check + envelope filtering: caregiver получает blob без полей которые они не имеют права видеть.
- TTL membership: scheduled cleanup в Cloudflare Worker.
- Restricted caregiver UI: SOS feed + call action (опционально других — настраивается админом).
- Audit log integration с TASK-32 V-7: каждое caregiver action логируется.

SCOPE НЕ ВКЛЮЧАЕТ:
- Multi-tenant billing (для B2B clinics) — TASK-34 L-1 в Phase 5.
- Caregiver creation through clinic portal — TASK-34 L-1.
- Dynamic permission changes по ходу work (caregiver permissions фиксированы at invite time).

DEPENDENCIES:
- TASK-8 (S-2 admin foundation, QR pairing).
- TASK-21 (P-6 recovery — extends для caregivers).
- TASK-32 (V-7 audit log — для accountability).

ACCEPTANCE CRITERIA:
- Admin создал invite со scope «видит SOS + может позвонить» на 30 дней → получил ссылку.
- Приглашённый открыл ссылку → установил app → автоматически paired в caregiver-режиме.
- Caregiver видит только SOS feed и кнопку «позвонить», НЕ видит фото и не редактирует config.
- Через 30 дней доступ автоматически отозван (caregiver app показывает «доступ истёк»).
- Каждое caregiver action логируется в audit (TASK-32) — admin может посмотреть «кто что делал».
- Manual revoke: admin отозвал membership → caregiver app мгновенно теряет доступ.

LOCAL TEST PATH:
- Два эмулятора через android-emulator skill (admin + managed) + третий для caregiver.
- Mock Worker cleanup job для TTL expiry test.
- Unit-tests envelope filtering: caregiver декрипт получает только разрешённые поля.

CONSTITUTION GATES:
- Rule 1 (domain isolation): Role, Permission — pure domain.
- Rule 2 (ACL): Firebase Functions client не вытекает в domain.
- Rule 5 (wire format): InviteBlob schemaVersion=1.
- Rule 14 (security): server-side role check (нельзя обойти patch'ем client'а).

EFFORT: Large (~3-4 months — с учётом всей инфры).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Role-based access на сервере + envelope filtering на клиенте
- [ ] #2 TTL membership с auto-expiry
- [ ] #3 Caregiver UI: SOS feed + call action, без album
- [ ] #4 Audit log integration (через V-7)
- [ ] #5 Admin создал invite со scope 'видит SOS + может позвонить' на 30 дней → получил ссылку
- [ ] #6 Приглашённый открыл ссылку → установил app → автоматически paired в caregiver-режиме
- [ ] #7 Caregiver видит только SOS feed и кнопку 'позвонить', НЕ видит фото и не редактирует config
- [ ] #8 Через 30 дней доступ автоматически отозван (caregiver app показывает 'доступ истёк')
- [ ] #9 Каждое caregiver action логируется в audit (TASK-32) — admin может посмотреть 'кто что делал'
- [ ] #10 Manual revoke: admin отозвал membership → caregiver app мгновенно теряет доступ
<!-- AC:END -->
