# Checklist: requirements-quality

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 12/16 ✓ — 4 open items, none blocking

---

## Content Quality

- [ ] **CHK001** — implementation details в spec.md
  - **Status**: ⚠️ partial.
  - **Найдено**: `gradle/libs.versions.toml:6` ссылка, `Context.filesDir`, `Build.VERSION.SDK_INT`, `androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()`, `ACTION_PICK_IMAGES`, `ACTION_OPEN_DOCUMENT`, `ContactsContract.Contacts.Photo.PHOTO`, упоминание Konsist / KDoc / Compose.
  - **Mitigating**: spec 012 продолжает спек 011, который уже зафиксировал доменные порты (`AeadCipher`, `PrivateMediaUploader`, etc.) как часть **доменного словаря проекта**. Их использование в spec.md — допустимо.
  - **Действие**: Перенести **именно платформенные API** (`ACTION_PICK_IMAGES`, `ACTION_OPEN_DOCUMENT`, `ContactsContract.*`, `Build.VERSION.SDK_INT`, `Context.filesDir`, gradle-paths) в plan.md при `speckit-plan`. В spec.md заменить на нейтральные формулировки: «выбор реализации picker'а по уровню Android API», «локальная app-private папка для расшифрованных файлов».
- [x] **CHK002** — focus on user value: ✓. User Stories формулируют value (фото внучки, плитка-документ); FR'ы технические, но это admissible для multi-layer feature с фиксированными доменными портами.
- [x] **CHK003** — non-technical stakeholder: ✓. TL;DR простыми словами в конце spec'а, User Stories разворачивают сценарии на бытовом уровне.
- [ ] **CHK004** — mandatory sections present
  - **Status**: ⚠️ partial. **User Stories ✓**, **Functional Requirements ✓**, **Success Criteria ✓**, **Out of Scope ✓** — но **In Scope явного раздела нет**, он implicit через US + FR. Spec-kit canonical template требует и In, и Out.
  - **Действие**: при `speckit-plan` добавить короткий явный `## In Scope` параграф (3-5 буллетов), либо явно отметить «In Scope = всё, что выше до раздела Out-of-Scope» в spec.md.

## Requirement Completeness

- [x] **CHK005** — `[NEEDS CLARIFICATION]` markers: 0. ✓ (все вопросы закрыты в `## Clarifications`).
- [x] **CHK006** — every requirement testable: ✓. Каждый FR имеет observable assertion (есть acceptance scenario или SC, который проверяется).
- [x] **CHK007** — unambiguous: ✓. Все vague-термины operationalised: «medium-tier» = Snapdragon 6xx + 3 Mbit/s, «mgnoвенно» = ≤100 мс, «senior-safe» = ≥56dp + ≥4.5:1 контраст.
- [x] **CHK008** — measurable SC: ✓. Все 11 SC с числами / процентами / временными границами.
- [ ] **CHK009** — technology-agnostic SC
  - **Status**: ⚠️ partial. **SC-009** ссылается на конкретные имена крипто-портов (`AeadCipher / AsymmetricCrypto / EncryptedMediaStorage / DigitalSignature / HashFunction / SecureKeystore`) и термин «KDoc». Это **technology-leaking** в спек-канонике.
  - **Mitigating**: эти имена — часть доменного словаря (см. CHK001 rationale).
  - **Действие**: на спек-уровне можно переформулировать «Все доменные порты крипто-инфраструктуры спека 011 и оба фасада спека 012 содержат документационный комментарий о допустимом use-site». На plan-уровне — оставить точные имена.
- [x] **CHK010** — Given/When/Then explicit: ✓. Все 4 US имеют по 3+ acceptance scenarios в формате GWT.
- [x] **CHK011** — edge cases: ✓. 8 пунктов, покрывают empty state (фото нет — US-1 sc.2), error (decrypt failed — US-3), retry (network loss — edge cases), double-action (overwrite — US-1 sc.3 / FR-013), boundary (>500 KB compression, quota exceeded, MIME mismatch, gesture failure).
- [x] **CHK012** — scope bounded: ✓. Out-of-Scope — 12 явных пунктов, Dependencies — 4 спека явных + 4 backlog cross-link'a (TODO-DESIGN-001 + ARCH-017/018/019). In Scope implicit (см. CHK004).
- [x] **CHK013** — dependencies & assumptions explicit: ✓. 10 assumptions, в т.ч. minSdk=26 (проверенный), production-statu «не в production до spec 030+», threat model явная.

## Feature Readiness

- [ ] **CHK014** — FR mapped to US
  - **Status**: ⚠️ implicit. FR-001..010 — архитектурные (три слоя), не привязаны к одному US напрямую; FR-011..014 → US-1, FR-015..020 → US-2, FR-021..022 → US-3, FR-023 → US-4. **Mapping не прописан явно**.
  - **Действие**: при `speckit-tasks` сделать таблицу `FR → US/independent` (это и так делается procedure-cross-artifact-trace). На спек-уровне опционально добавить хедер `### FR groups` с краткой группировкой.
- [x] **CHK015** — error path per US: ✓. US-1 sc.2 (фото отсутствует), US-3 — целая error story, US-4 sc.3 (refCount > 0 case), edge cases — 8 шт.
- [x] **CHK016** — SC have producing FRs: ✓ для 9/11. **SC-006** (APK delta ≤500 KB) — plan-phase metric, нет direct FR (это OK, измеряется в build). **SC-011** (i18n ru+en) — нет явного FR; покрывается ассампшеном «ADR-004 compliance». Не критично.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 12 |
| ⚠️ partial / action-needed | 4 |
| ✗ blocking | 0 |

**Verdict**: clarify-фаза проведена корректно, спек **готов** к `speckit-plan`. Четыре частичных пункта (CHK001, CHK004, CHK009, CHK014) — это **косметические правки уровня plan**, не блокеры. Они отслеживаются как action items при `speckit-plan` / `speckit-tasks`.

**Next step**: после прохождения остальных 13 чеклистов — `speckit-plan`.
