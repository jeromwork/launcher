# Spec 015 Localization Glossary

**Purpose**: canonical terminology contract for the F-3 translation pipeline (FR-031c).

This file is consumed by the `procedure-translate-spec-strings` skill on every
run. It tells the Claude API translator how to map English source terms to each
of the 11 target locales **consistently** — without it, the translator might
produce three different Russian words for "tile" across three strings.

If you add a domain term to `strings_wizard.xml`, add it here too, then re-run
the translation skill.

---

## Canonical terms

| EN source | Definition | RU | DE | ES | FR | PT | ZH | JA | AR | HI | KK-Latn |
|-----------|------------|----|----|----|----|----|----|----|----|----|---------|
| **Tile** | A single home-screen icon-button representing one action (call Maria, open messages). NOT "shortcut" — tile is the project-specific noun. | Плитка | Kachel | Mosaico | Tuile | Bloco | 磁贴 | タイル | بلاطة | टाइल | Plitka |
| **Wizard** | The guided first-run / re-config flow. Not "setup" or "assistant". | Мастер | Assistent | Asistente | Assistant | Assistente | 向导 | ウィザード | معالج | विज़ार्ड | Sheber |
| **Tile set** | A bundled, named arrangement of tiles (e.g. "classic-6", "neighbours"). | Набор плиток | Kachel-Set | Conjunto de mosaicos | Ensemble de tuiles | Conjunto de blocos | 磁贴集 | タイルセット | مجموعة البلاطات | टाइल सेट | Plitka jiıny |
| **Senior** | The primary user persona — elderly, low-vision, cognitively conservative. Avoid in literal UI; use "you" framing. | (не показывать в UI) | — | — | — | — | — | — | — | — | — |
| **Admin** | Family member with edit rights. Per spec 014. | Админ | Admin | Admin | Admin | Admin | 管理员 | 管理者 | المسؤول | व्यवस्थापक | Admin |
| **Managed** | The senior user's device, paired with an Admin device. Per spec 007. Internal term — not user-facing in F-3. | (не в UI) | — | — | — | — | — | — | — | — | — |
| **Home / home screen** | The default screen after wizard completion. Standard term across all locales. | Главный экран | Startbildschirm | Pantalla principal | Écran d'accueil | Tela inicial | 主屏幕 | ホーム画面 | الشاشة الرئيسية | होम स्क्रीन | Bas ekran |

---

## Tone guidelines per language

- **RU** (Russian): formal "Вы", not informal "ты". Senior persona expects respect. Keep sentences ≤ 70 chars.
- **DE** (German): formal "Sie", not "du". Compound words discouraged for senior audience — prefer descriptive multi-word phrases.
- **ES** (Spanish): neutral Latin American, not Iberian. Avoid "vos". Use "usted" form.
- **PT** (Portuguese): Brazilian Portuguese, not European. Use "você" form (formal).
- **AR** (Arabic): Modern Standard Arabic (MSA), not regional dialect. RTL layout per FR-032.
- **HI** (Hindi): Devanagari script. Avoid Sanskrit-heavy formal register; use everyday Hindi.
- **JA** (Japanese): polite -masu form (です/ます). Senior audience expects 丁寧語.
- **ZH** (Chinese): Simplified, mainland China. PRC tech vocabulary.
- **FR** (French): metropolitan French. Formal "vous". Avoid franglais.
- **KK-Latn** (Kazakh Latin): use the 2021 official Latin alphabet (Q, Ý, etc.).

---

## What NOT to translate

- `xx.yy.zz` (resource keys) — never appear in user text; if you see one in a value, leave it.
- Brand names: WhatsApp, Telegram, YouTube, Play Store — keep verbatim.
- Numeric literals and units (e.g. "1.0", "1.3", "56dp") — leave verbatim.
- Locale labels in `ui_language_*` — each is the language's own endonym; do NOT translate to the target locale.
- HTML entities and XML escapes — preserve as-is.

---

## Adding a new term

1. Add the English source to `strings_wizard.xml` and `CONTEXT.json`.
2. Add a row to the canonical-terms table above with the agreed translation.
3. Run `procedure-translate-spec-strings` — the skill will refresh the other
   8 stub locales (RU is maintained manually).
4. Verify in screenshot tests at fontScale=2.0 in EN/DE/AR.
