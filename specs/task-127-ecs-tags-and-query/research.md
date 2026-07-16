# Research: ECS Tags Foundation — one-way doors and alternatives

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Documents one-way-door decisions per CLAUDE.md §3 and rejected alternatives per Article XII (Required Context Review) invariants. Each section: choice, alternatives, rationale, exit ramp.

---

## R-1: Query API shape — extension functions vs member methods

**Choice**: Extension functions in `core/preset/query/ProfileQuery.kt`.

**Alternatives considered**:
- **A. Member methods on `Profile`** — `profile.byTag(...)`, `profile.homeScreenTiles()` directly on class.
- **B. Separate `ProfileQueryService` class** injected via DI, holds Profile reference.
- **C. Extension functions in feature-specific files** (chosen).

**Rationale**:
- Profile.kt already grows with each Component addition. Member methods would bloat it further; each selector = one line in class body + method noise.
- `ProfileQueryService` = single-implementation port — violates rule 4 (MVA). No polymorphism need, no test benefit, no DI seam benefit.
- Extension functions are Kotlin-idiomatic for read-side selectors: keep `Profile.kt` slim, group related selectors by feature (`ProfileQuery.kt` core, future `SafetyQuery.kt` when Phase-2 safety features land).

**Exit ramp**: If extensions prove hard to discover (IDE navigation issues, autocomplete noise), promote hot selectors (`homeScreenTiles`, `toolbar`) to member methods on `Profile`; keep esoteric ones as extensions. Cost: ~1 hour edit; no wire-format impact, no behaviour change.

---

## R-2: Migration writer approach — versioned classes vs field defaults

**Choice**: Dedicated `ProfileMigrationV2toV3` object with hardcoded subtype→tags mapping.

**Alternatives considered**:
- **A. kotlinx.serialization `@Serializable` field default** on `Component.tags` — deserialiser fills default automatically on missing field.
- **B. Dedicated migration class `ProfileMigrationV2toV3`** with explicit mapping (chosen).
- **C. Runtime lazy compute** — never persist `tags`, compute on every access from Component subtype.

**Rationale**:
- Option A works trivially — `@Serializable data class` with `val tags: Set<Tag> = defaultForSubtype`. But: default is on the constructor, not on the migration path. When v3 Profile is later saved to disk, the default is included; but the migration path is silent — no place to hook idempotency check or unit test. Also: kotlinx.serialization default requires the default to be a compile-time constant OR a factory. For sealed hierarchy with per-subtype defaults, factory function needed → same as Option B but hidden.
- Option C avoids migration entirely but has hidden cost: every query allocates fresh `Set<Tag>` on each access; performance breaks NFR-003 (< 1 ms) at even moderate scale. Also breaks pool `ComponentDeclaration` override (spec authors want to add extra tags to a specific instance — not possible if tags are computed).
- Option B is explicit, testable, idempotent, exhaustive (Kotlin `when` over sealed forces coverage). schemaVersion field is the standard rule-5 pattern.

**Exit ramp**: If per-subtype mapping grows large (dozens of subtypes), extract to a table (map or resource file). Keep the migration writer as thin wrapper. Cost: ~1 day. No wire-format impact.

---

## R-3: `schemaVersion` bump v2 → v3 — one-way door justification

**Nature**: rule 5 wire-format bump. After ship, older app versions (v2 reader) cannot read v3 Profiles.

**Alternatives to avoid the bump**:
- **A. Store `tags` in a side-car file** — `profile.json` (v2 unchanged) + `profile-tags.json` (new). No schemaVersion bump.
- **B. Encode tags as a suffix in existing `id` field** — `"tile-settings#Presentation,Tile"`. Parse at runtime.
- **C. Bump schemaVersion (chosen).**

**Rationale**:
- Option A introduces file-set atomicity risk: writing two files sequentially can leave inconsistent state if process dies between writes. Adds complexity for cosmetic gain (avoiding a version bump). Also: two files must be kept in sync; a bug in the sync logic = silent tag loss.
- Option B is a documented anti-pattern (encoding structured data in string primary keys). Breaks free-form `id` semantics. Migration back is impossible (parse ambiguity if id itself contains `#`).
- Option C is the standard practice. Cost per rule 5: migration writer + backward-compat roundtrip test + schemaVersion field. All three are ~1 day of work; already scoped in plan.md test strategy.

**Exit ramp**: If v3 rollout has field incidents (migration writer bug), forward-fix via v3.1 (add a fallback path in migration writer + release a hotfix). Downgrading a released schemaVersion is not supported (standard Android practice). Users on older APK versions can install newer APK; opposite direction not supported.

---

## R-4: Query indexing — linear scan vs pre-computed index

**Choice**: Linear scan (`components.filter { predicate }`).

**Alternatives considered**:
- **A. Pre-computed `Map<Tag, List<ProfileComponent>>`** rebuilt on every Profile change.
- **B. Linear scan (chosen).**

**Rationale**:
- MVP Profile scale: ~10-20 Components. Linear filter with lambda call is ~100ns per element on JVM → total ~2µs, well under NFR-003 (< 1 ms).
- Pre-computed index: allocates ~9 `List` instances per Profile change (one per Tag value); rebuild cost > linear scan cost until Profile size exceeds ~1000 elements — not remotely MVP scale.
- Index adds a synchronisation surface (Profile immutable but index derived; stale index if Profile mutated then Index not rebuilt).

**Exit ramp**: When Profile grows past 500 Components (Phase-3+ hypothetical: full contact list mapped as Components? Not planned) — add `Map<Tag, List<ProfileComponent>>` as internal cache on `Profile`. Cache invalidation = "Profile is immutable, no cache invalidation" — trivial. Cost: ~half day; additive change.

---

## R-5: `ConfigBackedFlowRepository` — remove vs retain

**Choice**: Retain in codebase, unbind from `FlowRepository` in DI, mark with `TODO(config-deprecation, SRV-CONFIG-DEPRECATION)`.

**Alternatives considered**:
- **A. Delete `ConfigBackedFlowRepository` entirely.**
- **B. Retain, unbind, TODO comment (chosen).**

**Rationale**:
- `ConfigDocument` is spec-009 admin push wire format. Removing `ConfigBackedFlowRepository` before spec-009 admin push migrates to Profile-based sync = orphaning the admin-push code. Cost of orphaning: cross-file compile errors, test coverage loss, revision when admin push is next touched.
- Retaining is nearly free: class stays, tests stay green, one comment added. Owner sees explicit deprecation intent; future maintainer knows the path.
- SRV-CONFIG-DEPRECATION entry in `docs/dev/server-roadmap.md` documents the removal trigger (unified Profile-sync spec).

**Exit ramp**: When SRV-CONFIG-DEPRECATION triggers (unified Profile-sync ships), delete `ConfigBackedFlowRepository.kt` + its tests + `ConfigDocument.kt` + `ConfigEditor.kt` + related fixtures. Cost: ~1 day; large diff, no behaviour change (path already unbound from FlowRepository).

---

## R-6: `Tag.Tile` + `Tag.Toolbar` separation — how to distinguish tile from Toolbar

**Choice**: Two separate marker tags. `Tag.Tile` for HomeScreen tile-grid membership; `Tag.Toolbar` for bottom Toolbar panel marker. `homeScreenTiles() = byAllTags({Presentation, Tile})`; `toolbar() = byTag(Toolbar).firstOrNull()`. **Both queries are tag-based — zero `is Toolbar` type checks anywhere.**

**Alternatives considered** (Clarifications Q3/Q4 + deep audit 2026-07-16):
- **A. Rely on Component subtype check** — `homeScreenTiles = byTag(Presentation).filter { it.component !is Toolbar }`. No marker tags.
- **B. Separate `Tag.Tile` only; `toolbar()` uses `is Toolbar` filter** (initial choice, later rejected).
- **C. Separate `Tag.Tile` + `Tag.Toolbar`; both queries tag-based (chosen).**

**Rationale**:
- Option A hardcodes the "which subtypes are tiles" list at every query call site. Adding a hint overlay in Phase-2 requires editing every filter. Rejected.
- Option B fixes the tile-side (declarative tag) but leaves toolbar-side stuck on `is Toolbar` — **paradigm mix** flagged by deep-audit 2026-07-16 (industry ECS report). Every new Presentation-not-Tile component must document «I am/am not the toolbar» somewhere else. R-6 promise was to make additive changes touch only defaults; option B breaks that on the toolbar-side.
- Option C completes the tag-based access story. `toolbar()` becomes a pure query. Adding a second-toolbar-like element (e.g., a floating panel) works additively — declare `Tag.Presentation` alone, no touch to `toolbar()`.
- Cost of option C: **one extra Tag enum value + one extra tag on Toolbar Component**. Additive to migration writer (exhaustive `when` catches it). Trivial.

**Exit ramp**: If we later need multiple toolbars (top + bottom), promote to `Tag.TopToolbar` / `Tag.BottomToolbar` or use `Tag.Toolbar` + a positional field on Component. Additive change; existing `toolbar()` renames to `bottomToolbar()`. If `Tag.Tile` proves too coarse (need row/column grid positioning) — that's UI layout metadata, not semantic tags — solved by separate Layout Preset mechanism.

---

## Cross-references

- ADR-011 §5 (chat-only checklists): plan.md and this research.md are English (AI-only artefacts) per language-by-audience rule.
- CLAUDE.md rule §3 (one-way doors): three one-way doors documented above (R-2, R-3, R-4-boundary if we go index).
- CLAUDE.md rule §4 (MVA): R-1 rejects `ProfileQueryService` explicitly; R-4 rejects premature index.
- CLAUDE.md rule §5 (wire-format versioning): R-3 justifies schemaVersion bump.
- CLAUDE.md rule §9 (shareability-readiness): Profile v3 stays personally-identifiable (contains phone numbers, package labels), so not directly shareable; Preset (`presetId` field) is the shareable layer. Out of scope for this task; noted for future preset-sharing spec.

---

## TL;DR для владельца

- **Query API как extension-функции** — не «сервис», не «класс запросов»; просто набор функций рядом с `Profile`. Ротация: если станут неудобны — перевесим горячие (`homeScreenTiles`, `toolbar`) на методы `Profile`. День работы, ничего не сломается.
- **Миграция через отдельный класс** `ProfileMigrationV2toV3` — потому что sealed exhaustive Kotlin не даст забыть новый субтип компонента. Компилятор ругнётся сразу.
- **Bump v2 → v3** — необратимый шаг после релиза (стандартно для Android). Alternative "два файла на диске" — anti-pattern, atomicity boom.
- **Линейный поиск по тегам** — по 20 компонентам это ~2 микросекунды. Индексация — когда будет 500+ компонентов (не MVP; хоть Phase-3+). Exit ramp есть.
- **`ConfigBackedFlowRepository` не удаляем** — он нужен для будущего сценария «админ пушит настройки» (spec-009). Удалим когда админ-пуш переедет на Profile-based sync. Пометили `TODO(SRV-CONFIG-DEPRECATION)` в коде и в server-roadmap.
- **Два маркер-тега — `Tag.Tile` и `Tag.Toolbar`** — чтобы обе выборки (плитки и тулбар) работали через теги, без `is Toolbar` в коде. Плитки: `byAllTags({Presentation, Tile})`; тулбар: `byTag(Toolbar).firstOrNull()`. Добавление любого нового Presentation-компонента (hint overlay в Phase-2) не требует правки query — только объявить нужный набор тегов.
- **`byNotTag(tag)` в API** — эквивалент canonical ECS `Without<T>` / Flecs `!tag`. Добавлен сейчас (30 строк кода), чтобы будущие «презентационные без экстренных» запросы не изобретали велосипед.
- **Terminology honesty**: это **tagged-component-model, ECS-inspired**, не canonical ECS. Sealed hierarchy = один Component на entity; canonical ECS = N компонентов на entity. Для нашего масштаба (~20 entities, редкие правки) ок. См. [ADR-012](../../docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md) с latent one-way door risk.
