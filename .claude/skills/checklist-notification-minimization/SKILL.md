---
name: checklist-notification-minimization
description: Verifies that every push notification introduced by the spec is justified by an explicit severity criterion (actionable + time-sensitive + user-relevant) per CLAUDE.md rule 10. Catches push events without severity, gamification pings, redundant nags, and missing actionable destinations. Triggered by mentions of push, notification, FCM, system tray, banner, badge, alert, ping, reminder, in-app indicator, notification center.
---

# Checklist: notification-minimization

Verifies the spec respects **CLAUDE.md rule 10 — Notification minimization (push hygiene)**.

Hierarchy of preference: **in-app indicator** > **in-app notification center** > **system push** (last resort only).

Every system push notification must justify its existence against three criteria simultaneously: **actionable** + **time-sensitive** + **user-relevant**. Without that justification, the push does not exist.

Reference: `CLAUDE.md` §10, refuse pattern #13.

---

## Inventory

- [ ] CHK001 Spec lists every notification event it introduces (push + in-app indicator + in-app notification center, separately).
- [ ] CHK002 For each event, the spec declares which tier it lives in (indicator / center / push) — no event left ambiguous.

## System push justification (one row per push)

For every event the spec routes to a **system push notification**:

- [ ] CHK003 **Severity criterion declared**: spec states explicitly why this event meets all three of: actionable, time-sensitive, user-relevant.
- [ ] CHK004 **Actionable destination declared**: spec states what the user can do directly from the notification (deep link to a specific screen, action button) — not a generic "open app".
- [ ] CHK005 **Time-sensitivity justified**: spec states what value is lost if the user only sees this when they next open the app.
- [ ] CHK006 **User-relevance justified**: spec states why this event is about the user personally, not aggregate system state.
- [ ] CHK007 No push is gamification, streak, social, or "engagement" — these belong in in-app indicators only.
- [ ] CHK008 No push is a "you might have missed X" digest unless the digest itself meets all three criteria.

## In-app indicators

- [ ] CHK009 Low-urgency events (new photo from family member, friend came online, new tile available) route to **in-app indicator** — never push.
- [ ] CHK010 Indicator placement is declared (badge on icon / banner on home / dot on list item).
- [ ] CHK011 Indicators are dismissible without forced acknowledgement.

## In-app notification center

- [ ] CHK012 Accumulated low-urgency events (collected since last app open) are surfaced in an **in-app notification center**, not as a system push.
- [ ] CHK013 Notification center entries link to their source (the photo, the contact, the album) — not a generic feed.

## Edge handling

- [ ] CHK014 The spec declares what happens if the system push fails (permission denied, DND, OEM kill) — feature still works via the in-app path.
- [ ] CHK015 Critical events (SOS, security incident, account loss) have **non-push fallbacks** — system phone API, screen banner, persistent notification — because system push is unreliable.
- [ ] CHK016 No notification depends on the user having previously enabled `POST_NOTIFICATIONS` to function.

## Privacy / lock-screen

- [ ] CHK017 Push payloads do not reveal PII on the lock screen (no full message text, no contact name in title for sensitive flows).
- [ ] CHK018 Sensitive flows (e.g., SOS, account-deletion confirmation) are explicit about lock-screen visibility.

## Acceptance evidence

- [ ] CHK019 The spec includes a notification matrix (event × tier × severity criterion × destination).
- [ ] CHK020 Tests cover the "push permission denied" path for every push the spec introduces.

---

## How to apply

1. List every notification event in the spec.
2. For each: walk through CHK001-CHK020.
3. If a push fails any of CHK003-CHK006, propose the alternative: in-app indicator or in-app notification center.
4. Block the spec if any push has no declared severity criterion — that is refuse pattern #13.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-notification-minimization: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/notification-minimization.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
