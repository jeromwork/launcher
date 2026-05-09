# Research: Provider Capabilities and Health

**Spec:** [`spec.md`](./spec.md) · **Plan:** [`plan.md`](./plan.md) · **Date:** 2026-05-09

This document captures the **alternatives considered** для each one-way door per CLAUDE.md rule §3. Each entry: alternatives, chosen path, why others rejected, exit ramp (cost to reverse).

---

## R1. `versionCode` Long vs `versionName` String

**Source:** Clarification C1, FR-001.

**Alternatives:**
- (A) `versionName: String` (e.g. "WhatsApp 2.24.18") — user-facing.
- (B) `versionCode: Long?` — internal, for feature-detection.
- (C) Both (`versionName` + `versionCode`).

**Chosen:** (B) — `versionCode: Long?` only.

**Why others rejected:**
- (A): no current consumer in спеке 006 needs to display version to user; speculative UX.
- (C): adds wire-format size for no current benefit; violates CLAUDE.md rule 4 (don't add for hypothetical).

**Exit ramp:** добавить optional `versionName: String? = null` в schema — non-breaking (FR-042 default).

---

## R2. Triggers for «нет связи» reactions — late deferral to спек 013

**Source:** Clarification C2/C3/C4 → Late deferral 2026-05-09.

**Alternatives initially considered:**
- (A) Trigger on any `connectivity == None` >1h.
- (B) Trigger only on full offline (no WiFi AND no mobile data) >1h.
- (C) Differentiate: WiFi-only offline ≠ mobile-only offline ≠ full offline; different reactions.

**Chosen:** **all deferred to спек 013** (`offline-detection-and-emergency-reachability`).

**Why deferred:**
- (A) noisy: domestic WiFi router restart triggers false alarm every week.
- (B) better but doesn't account for «metered mobile + unlimited WiFi» scenarios where WiFi loss matters.
- (C) correct but requires real-user observation data to choose default thresholds.

**Exit ramp:** none needed for спек 006 — feature simply absent here. Spec 013 will design from scratch with user-observation data.

---

## R3. `IconStorage` port — single implementation now vs sealed `Bundled | Remote`

**Source:** Clarification C8, FR-008..012.

**Alternatives:**
- (A) `iconRef: String` + hardcoded bundled lookup (no port).
- (B) Sealed `IconRef = Bundled(name) | Remote(url, sha256)` в Capability.
- (C) Port `IconStorage` + `iconId: String` namespace convention (`bundled:` / `custom:` / `private:`).

**Chosen:** (C).

**Why others rejected:**
- (A): wire-format breaking change в спеке 007 when `Remote` added (must bump schemaVersion + migration).
- (B): `Remote` variant has no consumer в спеке 006 → premature abstraction (CLAUDE.md rule 4 violation, flagged by checklist-meta-minimization). Also, sealed locks namespaces — adding `private` for спека 011 requires sealed-edit + migration path.
- (C): `Capability` doesn't know icon source — pure separation of concerns. Wire-format encodes namespace in string prefix — adding new namespace is non-breaking. `IconStorage` port supports multiple impls without `Capability` changes.

**Exit ramp:** if (C) proves overengineered, downgrade to (A) — delete `IconStorage` interface, inline lookup в Composable; wire-format `iconId` string stays unchanged. ~1 day work.

**Note on `IconResolution.Drawable.androidResourceId: Int`**: this is the only Android-type leak в `commonMain`. Acceptable до iOS implementation. When iOS comes, becomes `expect class`. Cost ~half-day refactor.

---

## R4. Cache mechanism for remote icons — спек 006 (forward-compat) vs спек 007 (when needed)

**Source:** Clarification C9, NFR-N08.

**Alternatives:**
- (A) Implement LRU + sha-invalidation + pinning сейчас «впрок».
- (B) Wire-format reserves `iconSha256` field, mechanism implemented в спеке 007.

**Chosen:** (B).

**Why (A) rejected:** no current consumer (спек 006 has only `BundledIconStorage` which doesn't cache). Code paths без tests = bug breeding ground. CLAUDE.md rule 4.

**Exit ramp:** reverse trivial — if кеш needed earlier, спец 007 brings code. Wire-format already ready.

---

## R5. DataStore Proto vs Preferences для snapshot persistence

**Source:** Clarification C6, FR-005, FR-033.

**Alternatives:**
- (A) Proto DataStore — typed serializer, requires `.proto` file + codegen + protobuf-kotlin lib.
- (B) Preferences DataStore — string-keyed, store JSON-as-string, reuse спека 005 `Json` instance.

**Chosen:** (B).

**Why (A) rejected:**
- New build complexity (codegen step).
- KMP-Proto ecosystem fragmented; Preferences DataStore Multiplatform is more mature.
- Спек 005 already standardised on kotlinx-serialization-json — reusing keeps single serialization story.

**Exit ramp:** migrate Proto когда KMP-Proto matures (probably not before спек 015+). Migration: write Proto adapter, dual-read for one release, switch. ~3 days.

---

## R6. Settings sync — local-only vs cloud-mirrored from day one

**Source:** Clarification C7.

**Alternatives:**
- (A) Local-only в спеке 006, cloud sync added in спек 008.
- (B) Cloud sync designed up-front в спеке 006 — `LauncherSettings` writes to /config when online.

**Chosen:** (A).

**Why (B) rejected:**
- Спек 006 doesn't have cloud channel (Firebase appears in спеке 007).
- Conflict resolution rules (admin-changed vs user-changed) require pairing context (спек 007).

**Exit ramp:** wire-format `LauncherSettings` with `schemaVersion` is ready for cloud sync. Spec 008 adds sync layer, no wire-format change needed.

---

## R7. Backup policy — auto-backup vs exclude-all

**Source:** FR-057 (added by checklist-security CHK024).

**Alternatives:**
- (A) Exclude all 3 DataStore files from Android Auto Backup.
- (B) Include all 3.
- (C) Selective: exclude per-device transient state (capability/health), include user prefs (settings).

**Chosen:** (C).

**Why others rejected:**
- (A): user loses banner preferences when restoring on new device — bad UX.
- (B): old `capability_snapshot_json` from old device shows wrong app list on new device (different installed apps); old `health_snapshot_json` shows misleading lastSeen and battery state.

**Exit ramp:** trivial — XML edit. No data migration needed (snapshots rebuild on first RESUMED).

---

## R8. `<queries>` declaration — explicit list vs `QUERY_ALL_PACKAGES`

**Source:** FR-053 (added by checklist-permissions-platform CHK008 — CRITICAL).

**Alternatives:**
- (A) `QUERY_ALL_PACKAGES` permission — see all installed apps.
- (B) `<queries>` element with explicit `<package>` entries for known providers only.

**Chosen:** (B).

**Why (A) rejected:**
- Google Play policy: `QUERY_ALL_PACKAGES` requires justification (launcher exception exists, но Play submission review is unpredictable).
- Privacy-by-design: we only need to know about specific providers per spec 005 ProviderId list, not all packages.

**Exit ramp:** if future spec needs broader detection (e.g. spec 011 contacts may inspect more apps), evaluate `QUERY_ALL_PACKAGES` justification then. Per-spec decision.

---

## R9. `LauncherSettings` corruption recovery — preset-defaults vs empty fallback

**Source:** FR-051 (added by checklist-failure-recovery CHK014).

**Alternatives:**
- (A) Empty fallback — all banners OFF if file corrupted.
- (B) Preset-aware defaults — if `simple-launcher`, banners ON; otherwise OFF.

**Chosen:** (B).

**Why (A) rejected:**
- Senior user (likely on `simple-launcher`) loses safety-net banners after corruption — defeats the point of having defaults at all.

**Exit ramp:** XML default editing. No migration needed.

---

## R10. Banner UI — system notifications vs in-app overlay

**Source:** FR-026 (per US-5 design choice in clarify, confirmed 2026-05-09).

**Alternatives:**
- (A) System notification (shade pull-down).
- (B) In-launcher overlay banner (Card on HomeScreen).

**Chosen:** (B).

**Why (A) rejected:**
- Notifications get lost in clutter; senior may not pull down shade.
- Notifications require `POST_NOTIFICATIONS` permission on Android 13+ (NFR-008 forbids new permissions).
- Notifications have inconsistent appearance across OEMs.
- Banner inside launcher is in user's primary view — exactly where they look when checking phone.

**Exit ramp:** add notifications channel later if user research shows banners aren't seen. Не ломает существующее.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Документ фиксирует 10 решений, которые **трудно отменить** (one-way doors) или которые имеют значимые последствия. Для каждого: какие были альтернативы, что выбрали, почему остальное отклонили, сколько стоит передумать.

**Конкретика, которую стоит запомнить:**
- R1: `versionCode: Long?` only — не показываем имя версии пользователю; добавление `versionName` потом — non-breaking.
- R2: реакции на «нет интернета» вообще не в спеке 006 — отложены в спек 013, потому что «нет интернета» зашумлённый сигнал.
- R3: иконки через **port `IconStorage` + namespace string `iconId`**, а не sealed class — для расширяемости (`custom:`, `private:` готовы для будущих спеков). `IconResolution.Drawable.androidResourceId: Int` — единственная Android-протечка в commonMain, осознанная, починим при iOS.
- R4: кэш облачных иконок **не реализуется в спеке 006** — wire-format поле `iconSha256` зарезервировано, реализация в 007.
- R5: **Preferences DataStore + JSON** (не Proto) — переиспользуем kotlinx-serialization из спека 005.
- R6: настройки local-only в 006; cloud sync будет в 008 без переделки wire-format.
- R7: backup правила — **исключить** capability/health snapshots (они per-device), **включить** settings (пользователь не хочет потерять).
- R8: `<queries>` блок с **явным списком** провайдеров (НЕ `QUERY_ALL_PACKAGES`) — Play policy + privacy.
- R9: при corruption файла настроек — fallback на **preset-aware defaults** (для `simple-launcher` баннеры ON), не пустой fallback.
- R10: баннеры — **внутри лаунчера**, не системные нотификации (последние пожилой не увидит + требуют Android 13+ permission).

**На что смотреть с осторожностью:**
- R3 `IconResolution.Drawable.androidResourceId` — единственное Android-имя в commonMain. Принято осознанно (iOS deferred). Не позволять добавлять ещё подобные «just for convenience».
- R8 `<queries>` — критично для работы capability detection на Android 11+. Без него фича сломана.
- R5 → R7 — все три про DataStore. Если меняем хранилище в будущем, надо помнить про backup правила и про «3 DataStore files namespaced as `com.launcher.<feature>.<key>_v1`» (FR-046).
