# Research: Contact Sharing UX Patterns

**Date**: 2026-05-28
**Context**: Pre-spec-014 (Contact Sharing UX Refinements) decision document
**Purpose**: Record proven patterns from consumer Android apps, avoid re-running the same research when working on related future features (messenger, family album).

---

## Question we asked

How does contact sharing UX work in real consumer Android apps (admin curating contacts for elderly's launcher)? What patterns are proven and which are rabbit holes?

## Methodology

Web research across WhatsApp / Telegram / Signal / Bitwarden / Google Contacts / iOS Contacts production behavior + bug trackers (GitHub Issues, Bugzilla) + Android Developers documentation + senior-care apps (GrandPad, Wiser, BIG Launcher, Doro). Cross-referenced 20+ sources.

---

## Key findings

### 1. Share-target patterns

- **Pattern A — ACTION_SEND inline picker** (WhatsApp / Telegram / Viber / Gmail / Outlook): standard Android share sheet, recipient picker inside our app. ⚠️ WhatsApp drops vCard with embedded photo ([ez-vcard #120](https://github.com/mangstadt/ez-vcard/issues/120)). Signal does not support vCard at all ([Signal-Android #6520](https://github.com/signalapp/Signal-Android/issues/6520) — open since 2016).
- **Pattern B — Inbox / clipboard**: NOT used in any consumer contact-sharing app. Found zero precedents. Mental model mismatch.
- **Pattern C — Share-source only (internal contact picker)**: WhatsApp attach-contact-to-chat (system picker inside app), Google Pay, most CRMs. Android 17 brought privacy-first Contact Picker without READ_CONTACTS ([Android Developers Blog](https://android-developers.googleblog.com/2026/03/contact-picker-privacy-first-contact.html)).

### 2. Multi-target bulk send

- **WhatsApp**: hard limit 5 recipients per forward, 1 for "highly forwarded" ([WhatsApp Help](https://faq.whatsapp.com/1053543185312573)).
- **Telegram**: native multi-select recipient picker (Android/iOS/Desktop). WebK/WebZ lacks it — users actively complain.
- **Verdict**: Multi-select recipient screen (Telegram-style) is the natural UX for 1–5 recipients. Sequential forwarding is friction.

### 3. Persistent inbox vs transient handoff

- **Persistent (inbox)**: Gmail, Outlook, WhatsApp chats. Reason: conversational continuation.
- **Transient (drop-and-done)**: Save to Drive, Save to Photos, Google Pay add-card. Reason: single-purpose, no dialog.
- **Deciding factor**: NOT frequency. Conversational continuation. Contact-sharing has no continuation → transient pattern.

### 4. Contact deduplication

- **Google Contacts**: silent suggested merge, inbox-card merge button.
- **iOS Contacts**: "Duplicate Contact: Keep Both / Merge" dialog at manual add time.
- **WhatsApp**: no dedup on receive; relies on system Contacts dedup.
- **CRM (Salesforce)**: weighted match `phone exact > email exact > (name fuzzy + phone last-4)`.
- **Pattern winner**: dedup by **normalized phone E.164**; silent merge during sync; explicit dialog on manual add.

### 5. Production failure modes

- **vCard with embedded photo**: WhatsApp drops it ([Technipages](https://www.technipages.com/whatsapp-the-file-format-is-not-supported/)).
- **vCard versions 2.1/3.0/4.0 incompatibility**: photo + title lost on convert ([Mozilla bug 888156](https://bugzilla.mozilla.org/show_bug.cgi?id=888156)).
- **Signal no-vCard**: workaround = manual field copy, has been so since 2016.
- **WhatsApp forwarding limit**: 5 recipients max. If we use WhatsApp as transport, fails at 6+ grandmas.
- **Multi-device partial delivery**: one device receives, another doesn't ([Manychat](https://help.manychat.com/hc/en-us/articles/15580879039388-Why-WhatsApp-messages-are-sometimes-not-delivered)).

---

## Recommendations adopted into spec 014

1. **Primary pattern: Pattern C** (admin opens our app, internal contact picker via system `ACTION_PICK`, custom recipient choice within our app).
2. **Fallback pattern: Pattern A** (register as share-target for `text/x-vcard`, accept vCard from WhatsApp/Phone Contacts, ignore embedded photo, fetch photo separately).
3. **Transit storage, no persistent inbox** — single-purpose drop-and-done UX.
4. **Dedup by normalized phone E.164**, explicit modal dialog on collision ("Keep Both / Replace / Cancel" iOS-style).
5. **NEVER use vCard as wire format between our devices** — use our JSON Contact (spec 008/011) with `schemaVersion`. vCard is input-only (parsed at share-target time).
6. **Photo via separate file**, encrypted blob in B2 (spec 011/012). NOT embedded in vCard.

---

## What we explicitly do NOT build

- Persistent inbox of received contacts (no mental model match).
- vCard photo embedding (production-known bug).
- vCard as wire format between our devices (CLAUDE.md rule 5 + cross-version compatibility issues).
- WhatsApp/Telegram as transport (rate limits, vCard incompatibilities).

---

## Sources (verified)

- [WhatsApp Help: forwarding limits](https://faq.whatsapp.com/1053543185312573)
- [WhatsApp Blog: forwarding changes](https://blog.whatsapp.com/more-changes-to-forwarding)
- [ez-vcard #120 — VCF with image to WhatsApp](https://github.com/mangstadt/ez-vcard/issues/120)
- [Signal-Android #6520 — vCard send/receive](https://github.com/signalapp/Signal-Android/issues/6520)
- [Signal-Android #535 — import vCard](https://github.com/signalapp/Signal-Android/issues/535)
- [Telegram bugs #1669 — forward multiple recipients](https://bugs.telegram.org/c/1669/7)
- [Google Contacts: merge duplicates](https://support.google.com/contacts/answer/7078226)
- [Android 17 Contact Picker docs](https://developer.android.com/about/versions/17/features/contact-picker)
- [Android Developers Blog: Contact Picker privacy-first](https://android-developers.googleblog.com/2026/03/contact-picker-privacy-first-contact.html)
- [Mozilla bug 888156 — vCard photo missing on import](https://bugzilla.mozilla.org/show_bug.cgi?id=888156)
- [Manychat: WhatsApp not delivered](https://help.manychat.com/hc/en-us/articles/15580879039388-Why-WhatsApp-messages-are-sometimes-not-delivered)

## TL;DR на русском

Исследовали, как контакты шарятся в популярных consumer-приложениях. Главные выводы для нашего лаунчера: (1) приём контактов через системный share sheet — стандарт, но WhatsApp ломает vCard с фото, Signal вообще не понимает vCard; (2) inbox-паттерна для контактов не существует в успешных продуктах; (3) bulk multi-select получателей — единственный приемлемый UX для нескольких бабушек одновременно (но мы решили multi-recipient не делать в MVP, отправляем каждой бабушке отдельно); (4) дедупликация по нормализованному телефону + iOS-style modal при collision; (5) vCard — только для input (приём share), между нашими устройствами — наш JSON со schemaVersion; фото — отдельным зашифрованным файлом в B2.
