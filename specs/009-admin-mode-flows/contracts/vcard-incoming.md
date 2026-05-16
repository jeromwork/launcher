# Wire format (read-only external): incoming VCard payload

**Source of truth**: this document.
**Used by**: spec 009 §FR-027..032 (VCard share intent), §FR-028 (anti-injection adapter), §NFR-002 (parse latency).
**Schema version**: N/A — это **внешний** формат (RFC 2426 / 6350 VCard 3.0 / 4.0), мы его НЕ контролируем. Версионирование живёт у RFC.
**Lifetime**: ephemeral — VCard приходит через intent extras, парсится, отбрасывается; никогда не персистится в этой форме.
**Direction**: **inbound only** — мы только читаем. Мы НИКОГДА не шлём VCard из нашего приложения (out of scope; «share контакт» в обратную сторону — отдельный продуктовый сценарий, который сюда не входит).

---

## Source

External — любое Android-приложение через системный share sheet:
- WhatsApp («Поделиться контактом»)
- Telegram (open-source `LaunchActivity.java` — подтверждено в pre-specify research)
- Viber
- Системный Contacts app
- Любой сторонний app, который умеет `ACTION_SEND` + MIME `text/x-vcard` (включая потенциально вредоносные — см. Security considerations).

User-initiated: admin явно выбирает наше приложение из системного share sheet. Без participation user'а intent не приходит (assumption A-6 / A-8 в spec.md).

---

## Format

Standard VCard text, RFC 2426 (3.0) или RFC 6350 (4.0). Однострочные и multi-line variants. UTF-8 encoding.

Минимальный пример (real-world, WhatsApp):

```text
BEGIN:VCARD
VERSION:3.0
FN:Маша Иванова
TEL;TYPE=CELL:+79161234567
END:VCARD
```

Multi-TEL пример (real-world, Telegram):

```text
BEGIN:VCARD
VERSION:3.0
FN:Александр Петров
TEL;TYPE=CELL:+79169876543
TEL;TYPE=HOME:+74951234567
TEL;TYPE=WORK:+74957654321
END:VCARD
```

---

## What we parse (whitelist — FR-028)

Только два поля. Всё остальное игнорируется без чтения значений (zero attack surface):

| VCard property | Maps to | Notes |
|---|---|---|
| `FN` (Formatted Name) | `displayName: String` | Одно вхождение; первое — используется, остальные игнорируются. Multi-line value (continuation lines с leading space per RFC) — joined. |
| `TEL` (Telephone) | `phoneNumbers: List<String>` | Может быть multiple. TYPE-параметры (`TYPE=CELL`, `TYPE=HOME`, etc.) парсятся, но в эпохе спека 9 не используются — все номера попадают в плоский список. Phone-нормализация делается downstream в `Contact.fromRaw()` (см. spec.md §«Универсальная Anti-Corruption Layer для Contact»). |

## What we IGNORE (whitelist policy — FR-028 anti-injection)

Все остальные VCard properties — silently dropped БЕЗ парсинга значения:

- `PHOTO` — embedded photos (большой payload, не нужен в спеке 9; см. TODO-FUTURE photo support).
- `EMAIL` — out of scope (мы — phone-launcher).
- `ADR` — adresses (out of scope).
- `BDAY` — birthdays (PII без use case).
- `URL`, `ORG`, `TITLE`, `NOTE` — out of scope.
- `X-*` (vendor extensions WhatsApp / iOS / etc.) — silently ignored (FR-028 «нулевая attack surface»).
- Любой unknown property — silently ignored.

Зачем whitelist а не blacklist: новые vendor-extensions появляются регулярно, blacklist неполный → атакующий находит obscure поле для injection. Whitelist — closed set.

---

## Validation rules (FR-028)

Применяются в порядке (fail-fast):

| Check | Threshold | Reject reason |
|---|---|---|
| Payload size | ≤ 10 KB (raw bytes, до парсинга) | `ImportError.PayloadTooLarge` |
| Encoding | strict UTF-8 (не latin-1 fallback) | `ImportError.InvalidEncoding` |
| Wrapper | `BEGIN:VCARD` ... `END:VCARD` present | `ImportError.MalformedWrapper` |
| `FN` field | exists и не-пустое после trim | `ImportError.MissingFN` |
| `TEL` field | ≥ 1 entry | `ImportError.MissingTel` (FR-031: «контакт без номера телефона не может быть добавлен», TODO-ARCH-014) |
| Per-`TEL` length | ≤ 64 chars raw (sanity cap до нормализации в Contact.fromRaw) | `ImportError.TelTooLong` |

Все error variants — sealed class `VCardImportError`, локализованные в UI слое (не в parser'е).

---

## Output type

```kotlin
data class RawVCard(
    val displayName: String,    // raw, до Contact.fromRaw() нормализации
    val phoneNumbers: List<String>   // raw, до E.164 нормализации
)
```

Это **raw** структура — никаких domain invariants. Передаётся в общую Anti-Corruption Layer `Contact.fromRaw(displayName, phoneNumber)` (см. spec.md §«Универсальная ACL для Contact»), которая делает trim / collapse whitespace / length cap / digit normalization / valid result или `ValidationError`.

**Не wire format мы пишем**. Мы только читаем входящие. `RawVCard` НИКОГДА не сериализуется обратно куда-либо.

---

## Security considerations

1. **Exported intent-filter** — `<intent-filter>` на `ACTION_SEND` + MIME `text/x-vcard` означает, что **любое** установленное приложение может слать нам payload. Mitigation:
   - Payload size cap (10 KB) — защита от OOM / DoS.
   - Whitelist field policy — защита от injection через obscure VCard properties.
   - Validation chain (FN/TEL required, strict UTF-8) — защита от malformed payloads.
   - User-initiated через системный share sheet — атакующий не может «протолкнуть» intent без участия user'a (Android security model).
2. **Regex backtracking** (NFR-002) — парсер MUST быть linear-time. Никаких greedy quantifiers / nested optional groups (классическая ReDoS surface).
3. **PII handling** (security checklist FR-031a/b/c) — после import VCard payload сразу discarded; в логи не идёт ни displayName, ни phone.
4. **No code execution surface** — VCard это **plain text**, не executable. Никаких `eval`, никаких embedded scripts. Whitelist гарантирует, что даже если RFC vCard 5.0 добавит executable extension — мы её проигнорируем.

---

## Parser implementation hint (plan-level)

- Hand-written parser в `:core/src/androidMain` (Android-specific intent handling), ~100 LOC. **НЕ использовать** `ezvcard` или другую third-party VCard lib — это вытащило бы vendor types в domain (CLAUDE.md rule 1) и добавило бы dependency для одного narrow use case.
- Linear-time line-based parsing: split на lines (с handling RFC continuation lines: leading space = продолжение предыдущего value), для каждой line — `key:value` split на первое `:`, key normalize uppercase, switch на whitelist set (`FN` / `TEL`), всё остальное skip.
- Никаких regex с backtracking — простой character scan для key/value split.
- Tests запускаются на `Dispatchers.Default`, microbenchmark проверяет p95 < 100 ms (NFR-002).

---

## Tests (androidUnitTest + commonTest)

| Test | What it verifies | Phase |
|---|---|---|
| `VCardAdapterContract.whatsapp_real_sample` | Real WhatsApp VCard export → RawVCard correct (`displayName`, ≥ 1 TEL) | 2 |
| `VCardAdapterContract.telegram_real_sample` | Real Telegram VCard export → RawVCard correct, multi-TEL | 2 |
| `VCardAdapterContract.viber_real_sample` | Real Viber VCard export → RawVCard correct | 2 |
| `VCardAdapterContract.android_contacts_real_sample` | Системный Contacts share → RawVCard correct | 2 |
| `VCardAdapterContract.missing_FN_rejected` | Malformed payload без FN → `ImportError.MissingFN` | 2 |
| `VCardAdapterContract.missing_TEL_rejected` | LINE/WeChat-style payload без TEL → `ImportError.MissingTel` (FR-031) | 2 |
| `VCardAdapterContract.non_utf8_rejected` | Latin-1 bytes → `ImportError.InvalidEncoding` | 2 |
| `VCardAdapterContract.payload_too_large_rejected` | 11 KB VCard → `ImportError.PayloadTooLarge` (FR-028) | 2 |
| `VCardAdapterContract.malformed_wrapper_rejected` | Нет `BEGIN:VCARD` или `END:VCARD` → `ImportError.MalformedWrapper` | 2 |
| `VCardAdapterContract.emoji_in_FN_preserved` | FN `Маша 👵` → displayName preserved as-is | 2 |
| `VCardAdapterContract.multiline_FN_joined` | RFC continuation line (FN value split across two lines с leading space) → joined без leading space | 2 |
| `VCardAdapterContract.multiple_TEL_with_types_extracted` | TEL;TYPE=CELL + TEL;TYPE=HOME + TEL;TYPE=WORK → 3 entries в phoneNumbers list | 2 |
| `VCardAdapterContract.unknown_X_extension_ignored` | Payload с `X-WhatsApp-Internal:something` → парсится OK, X-* поле dropped | 2 |
| `VCardAdapterContract.photo_field_ignored` | Payload с base64 PHOTO field → парсится OK, PHOTO dropped, не учитывается в payload size до dropping | 2 |
| `VCardParserBenchmark.p95_under_100ms` | 10 KB VCard payload → parse < 100 ms p95 on Pixel 4a class (NFR-002) | 4 |

**Fixtures** (`:core/src/androidUnitTest/resources/vcard-samples/`):
- `whatsapp-export.vcf` (real export, anonymized)
- `telegram-export.vcf` (real, multi-TEL)
- `viber-export.vcf` (real)
- `android-contacts-export.vcf` (real, system app)
- `malformed-no-fn.vcf`
- `malformed-no-tel.vcf` (simulates LINE/WeChat)
- `malformed-no-wrapper.vcf`
- `non-utf8.vcf` (raw bytes fixture)
- `oversize-11kb.vcf` (synthetic, padded `NOTE:...` ignored field)
- `emoji-fn.vcf`
- `multiline-fn.vcf` (RFC continuation)
- `multi-tel-with-types.vcf`
- `with-x-extension.vcf`
- `with-photo-base64.vcf`

---

## Versioning / evolution policy

- VCard format itself versioned by RFC (3.0 → 4.0). Naш parser MUST accept оба (по факту они различаются крайне мало для FN + TEL извлечения — оба используют те же property names).
- Если внутреннее представление `RawVCard` data class будет расширено (новое поле `emails` etc.) — это purely-internal change, не wire format bump.
- Если в будущем понадобится поддержать VCard SDK с публичным номером отсутствующим (FR-031 / TODO-ARCH-014 / TODO-FUTURE-SPEC-003) — добавим alternative flow «контакт без номера»; whitelist set расширится (например, `IMPP` для messenger handle). На текущий момент — **closed set из FN + TEL only**.

**TODO comment in code** (`VCardParser.kt`):
> Whitelist parser — только FN + TEL. Расширение whitelist (например, IMPP для messenger handle) — TODO-FUTURE-SPEC-003 (LINE/WeChat без публичного номера). НЕ добавлять ezvcard или другую third-party lib — нарушает CLAUDE.md rule 1 (vendor types в domain).

---

<!-- novice summary -->

## TL;DR

«Договор о том, как мы читаем VCard-файлы, которые админ пересылает нам из WhatsApp / Telegram / Viber / системных Контактов». Это входящий формат — мы только читаем, никогда не пишем. Парсим **только** имя (`FN`) и телефон(ы) (`TEL`), всё остальное (фото, email, адрес, custom extensions от WhatsApp) — молча игнорируем (whitelist-подход — нулевая поверхность атаки). Payload не больше 10 KB, строгий UTF-8, обязательны имя и хотя бы один номер (контакт без номера типа LINE/WeChat — отвергается с понятным сообщением). Парсер ручной, ~100 строк, без сторонних библиотек (чтобы не тянуть vendor-типы в domain), работает за < 100 ms на 10 KB на Pixel 4a class. Выходной `RawVCard {displayName, phoneNumbers}` далее идёт в общий Anti-Corruption Layer `Contact.fromRaw()` для нормализации номера и валидации имени.
