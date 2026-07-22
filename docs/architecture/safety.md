---
kind: architecture-domain
domain: safety
audience: [owner, ai-agent]
purpose: Safety & location domain — location sharing (static + live) and SOS/emergency. The sensor + foreground-service + SOS orchestration live here; the message SHAPES live in messaging-features. A sibling domain to messaging, care-critical.
components:
  - id: location-port
    choice: LocationPort (sensor ACL) — FusedLocationProvider (GMS) / AOSP LocationManager / microG / LOST behind one port
    port: LocationPort
    status: designed, not built (0 code)
    decision-task: TASK-148
    exit-ramp: FLP↔LocationManager↔microG is an adapter swap (rule 1/2)
  - id: live-location
    choice: periodic ephemeral location beacons over the E2E channel + start/stop control + auto-expiry; shapes = LocationShare / LiveShareStart(duration) / LocationBeacon / LiveShareStop
    blueprint: Matrix MSC3488 (static m.location) + MSC3489 (m.beacon_info start/stop + periodic m.beacon)
    status: designed, not built
  - id: sos
    choice: trigger → high-priority location beacon + OPAQUE push to admin; rule-10-justified (actionable + time-sensitive + user-relevant); server never learns "SOS" (rule 13)
    status: designed, not built
    launcher-tile: ECS Component.Sos (tag Safety/Emergency) — see ecs.md
  - id: privacy-policy
    choice: precision downgrade + who-can-see + auto-expiry (a share is never left on forever) + FGS type=location (Android 14+)
    status: designed
last-synced: 2026-07-22
---

# Домен: Safety & location — umbrella (`LocationPort`)

**This is the single source of truth for location sharing and SOS/emergency.** A **sibling domain to messaging** (care-critical for the primary care use-case). The message *shapes* (a location is a typed message) live in [`messaging-features.md`](messaging-features.md); the **sensor, foreground-service, SOS orchestration, and battery/privacy policy** live here. If it and any other doc disagree on location/SOS, this file wins — except: crypto (E2E of the beacon) is [`crypto.md`](crypto.md), the message transport is [`messaging-substrate.md`](messaging-substrate.md), the opaque push is [`messaging-delivery.md`](messaging-delivery.md), the launcher SOS tile is [`ecs.md`](ecs.md). Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: location is **two things split by concern** — (1) the *message shape* (a location = a typed message, [`messaging-features.md`](messaging-features.md)); (2) the *sensor + service + SOS* (this domain). Live location = **periodic ephemeral location beacons over the E2E channel + a start/stop control + auto-expiry** — design copied from **Matrix MSC3488/3489**. The **sensor sits behind `LocationPort`** (swap FusedLocationProvider ↔ AOSP LocationManager ↔ microG ↔ LOST — rule 1/2). **SOS** = trigger → high-priority beacon + an **opaque** push to the admin: the blind server relays bytes and **must never learn "this is an SOS"** (rule 13); SOS is a genuine rule-10 push (actionable + time-sensitive + user-relevant).

**Why a separate domain, not just a message feature**: location exceeds "typed message + render rule" — it needs a device-sensor port, a foreground service (Android 14+ `location` type), a battery/precision/auto-expiry privacy policy, and SOS orchestration. The taxonomy half stays in features; the machinery is here.

**Build-vs-buy**

| Block | Verdict | Component |
|---|---|---|
| Location sensor | 🟢 import (behind `LocationPort`) | FusedLocationProvider (GMS, proprietary) / **AOSP LocationManager** (native, de-Googled) / microG (FOSS) / LOST (OSS) |
| Location message shapes | 🟡 copy spec | Matrix MSC3488 (static) + MSC3489 (live beacon_info/beacon) → types in [`messaging-features.md`](messaging-features.md) |
| Live-share lifecycle (start/stop/expiry, FGS) | 🟡 our code | foreground service + timer; auto-expiry |
| SOS orchestration | 🟡 our code | trigger → beacon + opaque push (rule 10 + rule 13) |
| E2E of the beacon | 🟢 via crypto | beacon is an opaque blob over the MLS channel |

**Invariants** (SA1–SA5, see §Invariants). **Status**: designed, not built.

**Routing**: sensor / live-share lifecycle / SOS → stay here. Location *message shape* → [`messaging-features.md`](messaging-features.md). E2E → [`crypto.md`](crypto.md). Opaque push → [`messaging-delivery.md`](messaging-delivery.md). Launcher SOS tile → [`ecs.md`](ecs.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **SA1 — split of concern: shape in features, machinery here.** A location message is a typed message ([`messaging-features.md`](messaging-features.md)); the sensor/FGS/SOS/policy live in this domain. Do not put sensor calls in the message layer.
- **SA2 — the sensor is a port (`LocationPort`), never a direct SDK call** (rule 1/2). FusedLocationProvider is proprietary GMS; the port lets a de-Googled build swap to AOSP LocationManager / microG without touching the domain.
- **SA3 — live location auto-expires; a share is never left on forever.** `LiveShareStart` carries a duration; sharing is live only while `start + duration > now`; explicit stop supported. Privacy default, not optional.
- **SA4 — the server stays blind to SOS (rule 13).** Beacons are opaque blobs; the SOS push is an opaque wake-ping with an opaque target token — the server must never learn a push means "SOS". Content/urgency live inside the E2E payload.
- **SA5 — SOS is the rare justified push (rule 10).** It is the textbook actionable + time-sensitive + user-relevant event. Non-emergency location updates are NOT pushes — in-app indicator (rule 10 hierarchy).

## Industry grounding

- **Matrix MSC3488** (static `m.location`) + **MSC3489** (`m.beacon_info` start/stop state + periodic `m.beacon` updates, timeout-based auto-expiry). https://github.com/matrix-org/matrix-spec-proposals/pull/3488 · https://github.com/matrix-org/matrix-spec-proposals/pull/3489
- WhatsApp/Telegram: static + continuous live (fixed windows, sender can stop) as periodic encrypted location messages. **Signal has no live location** (static only) — a deliberate minimalism.
- Sensor libs: [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) (GMS, proprietary); AOSP `LocationManager` (native); [microG UnifiedNlp](https://github.com/microg/UnifiedNlp) (FOSS); [LOST](https://github.com/dhiyaulhaqZA/LOST) (OSS drop-in). Android 14+ requires foreground-service type `location`.

## Rejected (do not re-litigate)

- ❌ Direct FusedLocationProvider call from the domain — behind `LocationPort` (SA2).
- ❌ Server learning that a push/beacon is an SOS — opaque (SA4/rule 13).
- ❌ Location updates as system pushes — in-app indicator unless SOS (SA5/rule 10).
- ❌ Indefinite live shares — auto-expiry mandatory (SA3).

## Related domains

- Message shapes: [`messaging-features.md`](messaging-features.md). E2E: [`crypto.md`](crypto.md). Opaque push: [`messaging-delivery.md`](messaging-delivery.md). Launcher SOS tile (`Component.Sos`): [`ecs.md`](ecs.md). Transport: [`messaging-substrate.md`](messaging-substrate.md).
- Owner tasks (history, not truth): TASK-27 (messenger), and the launcher SOS tile in the ECS pool. Care use-case: [`../product/vision.md`](../product/vision.md).
