// Spec 014 — Tile Editing (F-014.0).
//
// Domain layer для tile editing operations + UX profile selection.
// Per plan §3.1: pure-Kotlin commonMain, no platform deps.
//
// TODO(server-roadmap): F-014.1 will add RemoteNamedConfigsStore adapter;
// merge local + remote at use site через MergedNamedConfigsRepository.
package com.launcher.api.edit
