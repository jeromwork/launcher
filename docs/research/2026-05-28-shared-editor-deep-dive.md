# Research: Shared Editor Pattern Deep-Dive

**Date**: 2026-05-28
**Context**: Pre-spec-014 (Contact Sharing UX Refinements) decision document
**Purpose**: Record analysis of "admin sees what grandma sees" UX pattern for remote config; reference for future ecosystem app design.

---

## Question we asked

What is the minimum-friction UX for admin to add a contact onto grandma's tile grid remotely? Is the "admin sees the same UI as grandma" pattern (structured shared editor) proven, novel, or a rabbit hole?

## Methodology

Web research across senior-care apps (GrandPad, Wiser, BIG Launcher, Doro), parental-control apps (Google Family Link, Apple Screen Time, Roblox Parent Dashboard), MDM kiosk vendors (Scalefusion, KioWare), collaborative editors (Figma, Home Assistant), and Android system Contact Picker behavior.

---

## Key findings

### 1. Tap count comparison

| App | Add contact to remote device | Taps from app open | Auto-fill |
|---|---|---|---|
| Google Family Link | N/A — no contact management | — | — |
| Apple Communication Limits | Settings → Screen Time → Communication Limits → Choose From Contacts → pick → Done | ~7 | Full auto-fill from iCloud |
| GrandPad Companion | Add Family Contact → manual entry of all fields | 8–10 + typing | Low — all typed |
| Wiser Helper | mirror view, low adoption | ~5–6 | Medium |
| BIG Launcher (via AirDroid) | pixel mirror | 10+ | system picker on senior side |
| Doro 8050 (TeamViewer-based) | session, mirror | 6–8 | mirror UX |
| **Our spec 009 (admin Editor + ACTION_PICK)** | Editor → empty cell → contact picker → done | **3–5** (steady-state, after first permission grant) | High — name + phone + thumbnail auto |

**Our current design is already at state-of-the-art.**

### 2. The "shared editor" pattern in senior-care vs adjacent domains

- **GrandPad Companion** (most successful senior app): **abstract list** UI. No mirror. Tabs: Contacts / Photos / Calendar. **Rejected** mirror pattern.
- **Wiser Helper**: claimed mirror view; ~3.0★ rating, 6 reviews → very low adoption. Not a positive precedent.
- **Family Link, Roblox Parent, Apple Screen Time**: all abstract list, no mirror.
- **Scalefusion / KioWare (MDM)**: **structured shared editor** — admin drags app icons into device-profile layout on web dashboard, topologically matches what kiosk sees. ✅ **Proven pattern**.
- **Figma multiplayer**: structured CRDT, render locally. Two users → same canvas. ✅ **Proven pattern**.
- **Home Assistant**: dashboard editor (structured, single-user).

**Verdict**: "Admin sees the same structured layout as grandma" is **novel in senior-care, proven in MDM and collaborative editors**. Wiser tried and didn't polish; GrandPad rejected. Our application of the pattern is an **architecture choice**, not pixel mirroring — we sync the ConfigDocument state, both sides render locally.

User confirmed (2026-05-28): "Не пиксельное соответствие — просто примерно как выглядит на телефоне, 2×3 раскладка, что-то похожее. В MVP мы не закладываемся на точное соответствие."

### 3. Photo fetching from ACTION_PICK Contact

Ranked by reliability without READ_CONTACTS permission (temporary URI grant from picker):

1. `Contacts.PHOTO_THUMBNAIL_URI` — ~100% when photo exists in system Contacts. 96×96px (low-res).
2. `Contacts.PHOTO_URI` — full quality via `openAssetFileDescriptor`. ~100% when present, ~50% contacts have it (rough estimate).
3. Manual upload from gallery (admin selects) — always works, +3 taps.

**No need for READ_CONTACTS permission** if we only use picker-returned URIs.

### 4. Manual operations minimum bar

Best case for our flow:
1. Tap empty cell.
2. Tap "Contact" in action sheet.
3. (First time only) permission grant for system picker.
4. Tap contact in system picker.

**Steady-state: 3 taps, 0 typing**. Already at floor for Android. Modal confirm → snackbar undo (Gmail pattern) keeps it at 3 taps with reversibility.

### 5. Failure modes

- vCard photo embedding: production-known bug (WhatsApp drops).
- Voice add ("Hey Google add Dr. Ivanov to grandma's home"): no precedent in senior-care apps. Skip.

---

## Recommendations adopted

- **Structured shared editor** at config-level, not pixel level. Admin's app renders grandma's layout from same `ConfigDocument` (spec 008 already does this). Bidirectional sync via Firestore. Not "approximately same look" only — actually same `ConfigDocument` rendered with edit affordances on admin side.
- **System contact picker** via `ACTION_PICK` (already in spec 009).
- **Background prefetch high-res photo** while admin can already see snackbar.
- **No multi-recipient bulk send** — user explicitly opted out 2026-05-28.
- **No voice add** — no precedent.

---

## Sources (verified)

- [Google Family Link — Get started](https://support.google.com/families/answer/7101025)
- [MacRumors — Communication Limits on Screen Time](https://www.macrumors.com/how-to/set-communication-limits-screen-time-downtime-ios/)
- [GrandPad — Using the Companion App](https://www.grandpad.net/tablet-features/companion/using-the-companion-app)
- [Wiser Helper](https://apkgk.com/com.uiu.companion)
- [BIG Launcher blog — easier for elderly parents](https://blog.biglauncher.com/how-to-make-android-easier-for-elderly-parents/)
- [Doro 8080](https://www.doro.com/en-gb/news/doro8080/)
- [CommonsWare — Runtime Permissions, ACTION_PICK and Contacts](https://commonsware.com/blog/2015/10/12/runtime-permissions-action-pick-contacts.html)
- [Android Developers — ContactsContract.Contacts.Photo](https://developer.android.com/reference/android/provider/ContactsContract.Contacts.Photo)
- [Scalefusion — Kiosk Features](https://scalefusion.com/kiosk-solution/features)
- [Liveblocks — Understanding sync engines (Figma, Linear, Google Docs)](https://liveblocks.io/blog/understanding-sync-engines-how-figma-linear-and-google-docs-work)
- [Roblox Parental Controls](https://en.help.roblox.com/hc/en-us/articles/30428310121620)

## TL;DR на русском

Исследовали, как успешные продукты реализуют «admin видит то же что подопечный». В senior-care этого почти нет: GrandPad (самый успешный) выбрал abstract list, Wiser попытался mirror — провал по adoption. В смежных доменах (MDM kiosk-configurators Scalefusion, Figma multiplayer) — proven pattern: синхронизируется **состояние**, не пиксели. Наша архитектура (рендер из общего ConfigDocument через спеку 008) — это правильная реализация этого паттерна. Tap-count: 3-5 тапов от «открыл app» до «контакт на сетке бабушки» — уже state-of-the-art, лучше чем у GrandPad (8-10 + ручной ввод) и Apple Screen Time (7). Фото подтягивается из системного picker'а без READ_CONTACTS permission через temporary URI grant. Multi-recipient bulk send (одному контакту нескольким бабушкам) пользователь отверг — каждой бабушке настраивается отдельно.
