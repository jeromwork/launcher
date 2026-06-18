# Checklist: device-self-sufficiency — spec 016 F-CRYPTO

Run date: 2026-06-17.

## Local viability

- [x] CHK-DSS-001 — Mode explicit: **LOCAL-only**. F-CRYPTO работает offline, без Sign-In, без server-state. Это infrastructure для LOCAL devices.
- [x] CHK-DSS-002 — F-CRYPTO requires no network, no Sign-In, no server. Работает full на свежеустановленном app сразу после wizard.
- [N/A] CHK-DSS-003 — F-CRYPTO не CLOUD-only.
- [N/A] CHK-DSS-004 — F-CRYPTO не HYBRID; LOCAL-only.

## Sign-In trigger point

- [N/A] CHK-DSS-005..007 — F-CRYPTO не требует Sign-In. Future cloud features (recovery в future multi-device-recovery spec (TBD)) — отдельный спека.

## Local→cloud promotion

- [N/A] CHK-DSS-008..009 — F-CRYPTO не имеет local state, который мерджить в cloud. **Cross-ref**: cloud key escrow в future multi-device-recovery spec (TBD) будет использовать `VersionedConfigViewer`? — нет, recovery flow свой (ADR-008). Не merge — restore.

## Cloud→local downgrade

- [N/A] CHK-DSS-010..011 — F-CRYPTO не имеет cloud features.

## Anti-patterns

- [x] CHK-DSS-012 — Не requires mandatory Sign-In at first launch.
- [x] CHK-DSS-013 — Не requires mandatory pairing.
- [x] CHK-DSS-014 — F-CRYPTO **enables** local features (F-5 ConfigCipher works locally with F-CRYPTO без cloud).
- [x] CHK-DSS-015 — F-CRYPTO не использует anonymous Firebase Auth — не использует никакой auth вообще.

## Cross-spec consistency

- [x] CHK-DSS-016 — F-CRYPTO enables both local (F-5 ConfigCipher local mode) и cloud (S-5 Contact Photos cloud) crypto. Если cloud недоступен — F-CRYPTO продолжает работать local.
- [x] CHK-DSS-017 — F-CRYPTO не assumes cloud data. `KeyDerivation` uses device-local salt (Clarifications Q6).

## Дополнительно

- [x] **F-CRYPTO support deferred-cloud architecture**: F-4 dependency снят. F-CRYPTO работает без Google UID, device-local salt. Когда F-4 activates (S-5 onwards), UID подмешивается через rotation (project_deferred_cloud_architecture memory).
- [x] **Cloud recovery как opt-in upgrade**: future multi-device-recovery spec (TBD) — value proposition «sign in → recovery enabled», не mandatory.

## Open issues

None.

## Result

**8/8 actionable PASS, 9 N/A**.

**Verdict**: PASS. F-CRYPTO архитектурно built для self-sufficient devices — никакого forced Sign-In, никакого forced server contact.

---

## TL;DR простым языком

F-CRYPTO работает **полностью оффлайн**, без Google Sign-In, без интернета. Это и есть «device self-sufficiency». Когда пользователь захочет облачные функции (например, восстановление при потере телефона) — он сам выберет это и залогинится; до этого момента всё работает локально. F-CRYPTO **является** инфраструктурой, которая позволяет этому быть так.
