# VERITY Lab deployment gates — do not skip

This repository is an initial source foundation. It is not a release APK and is not ready to take payment.

## Gate A — Android build and device QA
- Build with Android Studio / Android Gradle Plugin 8.7.3 / Gradle 8.9 / JDK 17 and Android SDK 35.
- Install on multiple real devices and Android versions; verify permissions, lifecycle interruption, screen rotation, low storage and process death.
- Validate touch test coordinate normalization, screen sizes and gesture sampling. The current 20-cell threshold is experimental only and must not drive certification.
- Replace the external camera-preview check with separate front/rear camera capture, focus and repeatable image checks. Current camera test does not identify which physical lens the external camera app used.
- Implement actual microphone recording/playback and routing checks. Current microphone module only asks permission.
- Add audio/speaker, multi-camera, connectivity, GPS/NFC, buttons/ports physical checklist, thermal observations and supported API-based checks with explicit per-device limitations.
- Add crash-safe test checkpoints and resumable test state for every module.

## Gate B — backend and report security
- Deploy migrations only to a fresh development Supabase project first. Review SQL and RLS with a security reviewer.
- Add server-only identifier normalization/HMAC and encryption; do not store raw serials in logs or expose them publicly.
- Ensure the only browser-facing lookup is `verify_current_report`, which returns the currently mapped report only.
- Wire the server-side retest transaction before accepting any new evidence for a previously registered serial. Drain `private_object_cleanup_queue` with a trusted worker using the Storage API and retry failed deletions.
- Create a report only after evidence is uploaded, evaluated server-side and payment is confirmed. The client must never choose `certificate_status`, `public_id`, payment status, issue time or signature.
- Add signing key rotation, private PDF storage, rate limits, idempotency, monitoring, abuse controls and backup/retention policy.

## Gate C — genuine testing standard
- Publish test definitions and version each one.
- Validate each threshold on a matrix of known-good and known-fault devices. Measure false positives and false negatives.
- Keep both attempts whenever a questionable result is repeated. Best-of-two applies only to valid higher/lower-is-better metrics; defect detection uses repeatability and worst-observed evidence so intermittent defects are not hidden.
- Do not show a VERITY Tested stamp until mandatory tests and coverage rules are defined and passed.
- Reports should use plain-language findings and say only what the evidence supports.

## Gate D — payment and ₹0 constraint
- For a zero-fee pilot, manual UPI reconciliation may be possible but is not automatic. Never treat a screenshot or client callback as confirmation.
- Automated payment provider integration may incur charges. Do not enable a paid plan or add a paid billing method without explicit owner approval.
- Set usage limits/alerts where available. If a free quota is exhausted, pause new issuance safely; do not silently incur costs.
