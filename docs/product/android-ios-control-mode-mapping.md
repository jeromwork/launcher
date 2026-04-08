# Android/iOS Safe Control Mode Mapping

## Goal
Keep product meaning aligned across platforms while staying honest about OS limits.

## Mode Mapping
- Android `STRICT` -> iOS `Supervised + MDM + Single App Mode` (maximum enforced lock-down).
- Android `STANDARD` -> iOS unmanaged mode (best effort reminders and guided settings, no full system lock-down).

## Capability Equivalence
- Default home ownership:
  - Android: launcher role (`CATEGORY_HOME`).
  - iOS: not equivalent; use supervised single-app experience.
- Escape protection:
  - Android strict: lock task + policy restrictions when Device Owner exists.
  - iOS strict: single app mode via MDM supervision.
- Permission hub:
  - Android: deep links to system settings from app.
  - iOS: MDM profile compliance checklist and Settings deep links where available.

## Parity Boundary
- Product parity is defined as "user does not get lost outside safe surface".
- Technical parity is not guaranteed for unmanaged iOS or unmanaged Android devices.

