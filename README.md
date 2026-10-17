# VERITY Lab — Native Android foundation (v0.1)

This is the start of the **native Android APK** track, not the old browser-only prototype. It is an auditable development foundation, not a finished or independently validated certification product. Do not charge customers or use the VERITY Tested stamp until the documented release gates are met.

## Non-negotiable product rules
- ₹0 ongoing infrastructure spend is the target. Free tiers have quotas and can change; no silent paid upgrades.
- Tests record evidence and limitations. A missing permission/API is `NOT_TESTED`, never proof of a broken component.
- Retest questionable outcomes. Keep both attempts internally. Choose the best result only for metrics where that is methodologically valid; recurring/intermittent defects must not be hidden by cherry-picking.
- Public report text is short and plain-English. Technical outcomes remain internal.
- ₹67 report issuance requires trusted payment verification on the server, never a client-side success screen or screenshot alone.
- A new test for the same serial supersedes the old public report. The previous report becomes un-verifiable and its detailed test data/PDF are removed from active storage. A minimal non-sensitive audit tombstone is retained to record that a report was superseded; this prevents silent history manipulation without keeping the old report available.

## Project layout
- `android/`: native Kotlin Android app source
- `supabase/migrations/`: PostgreSQL schema and serial retest/supersession policy
- `docs/`: product truth policy, test matrix and certification protocol

## Android setup
Open `android/` in Android Studio (current stable), allow Gradle sync, then run on a real Android phone. Minimum SDK 26, target SDK 35. This repository does not include the Android SDK or a built APK. Device tests must be validated on physical devices, including known-good and known-fault devices.

## Free deployment plan (not yet connected)
- Android app: open-source Kotlin; user installs a signed APK.
- Public verification frontend: static hosting free tier.
- API/database: Supabase free tier or equivalent, with RLS and server-side functions. Configure spend limits/alerts where available and never attach a paid billing account without explicit approval.
- PDF: private storage bucket; server-generated report; report ID public, serial masked by default.
- Payment: manual UPI reconciliation can be a zero-fee pilot, but it is not instant/automatic. Automated provider verification may introduce transaction or service fees. No report is issued until server-side payment status is confirmed.

## What this first foundation does not yet do
No live backend, payment verification, report signing, production PDF, public verification site, or complete camera/microphone/screen test suite is wired in. Do not interpret the source scaffold as a completed diagnostic system.

## Report replacement for a retested serial
The retest migration makes the previous report cease to be the current public report before the new test proceeds. It removes the old certificate row, detailed test results, old session and delivery jobs, and queues the old private PDF for deletion. Only a minimal audit tombstone remains (hash of old public report ID + replacement event), with no old measurements or serial. This is deliberate: the old report cannot be shown as valid, but the platform retains minimal evidence that it was superseded. The trusted backend must call `begin_device_retest` before accepting new evidence and must drain `private_object_cleanup_queue` via the Storage API.

## Current limitations
The Android app is an early source scaffold; it has not been compiled into an APK in this environment. The touch threshold is experimental, the camera module launches the system camera app, and microphone testing is not yet implemented beyond permission flow. Do not market or sell this build as a certification tool.
