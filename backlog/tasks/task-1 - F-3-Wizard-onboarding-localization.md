---
id: TASK-1
title: 'F-3: Wizard onboarding + localization'
status: Done
assignee: []
created_date: '2026-06-23 05:00'
labels:
  - phase-1
  - F-feature
  - ui
  - wizard
milestone: m-0
dependencies: []
references:
  - specs/015-wizard-localization-senior-ui/
priority: high
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Setup wizard для пожилого пользователя + локализация (RU base, 9 auto-managed locales). Merged PR #19.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Wizard manifest schemaVersion=1 с roundtrip-тестом
- [ ] #2 Senior-safe defaults применены (text scale, tap targets)
- [ ] #3 9 локалей сгенерированы через procedure-translate-spec-strings
- [ ] #4 TalkBack labels на всех wizard-экранах
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Merged 2026-06-17 (PR #19). Foundation для всех последующих UI.
<!-- SECTION:FINAL_SUMMARY:END -->
