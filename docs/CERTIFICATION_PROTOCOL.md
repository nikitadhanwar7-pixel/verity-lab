# VERITY Lab — Certification Protocol Draft

## Purpose

A buyer-facing report must describe the evidence actually collected, not imply universal hardware health. The current Android source is an early development build until the test suite is validated and a server-backed trust chain is implemented.

## Result vocabulary

- **PASS** — the specific test ran, evidence was collected, and a defined threshold or criterion was met.
- **ATTENTION** — test ran but evidence is subjective, limited, anomalous or requires human review.
- **FAIL** — a defined criterion was not met. A failure must cite the criterion and measured evidence.
- **NOT TESTED** — unavailable API, missing permission, incompatible model, absent equipment or incomplete user action. Never convert to PASS.

## Coverage levels

- **Level W — Web checks:** browser-accessible API or workload only. Not a hardware teardown or complete component certification.
- **Level G — Guided checks:** user performs a physical or visual action. Report as user-observed, not instrument-verified.
- **Level N — Native/vendor checks:** model-specific operating-system or manufacturer diagnostic interface with recorded provenance.
- **Level E — Equipment checks:** calibrated external optical, acoustic, electrical or battery instruments.

Every report must show coverage by level and identify which components were not tested. A “whole-device certified” label must not be issued solely from Level W/G evidence.

## Identity assurance

Browsers generally cannot authenticate IMEI or hardware serial numbers. Store identity source as one of: user-declared, OS-reported, vendor-attested, or independently verified. If the product deliberately publishes full serial numbers, require explicit, logged public-listing consent, explain the risks, support takedown/correction, and rate-limit searches. A serial entered by the user is still not authenticated by the browser. Do not state “IMEI verified” for user-entered values.

## Report integrity

1. Validate payment from the payment provider's server-to-server webhook; never trust a screenshot, browser callback alone, or client-supplied paid flag.
2. Bind test results to a short-lived session nonce and test-suite version.
3. Generate final PDF on the server from validated result records.
4. Hash exact PDF bytes with SHA-256.
5. Sign a canonical manifest containing certificate ID, PDF hash, suite version, issue time, identity assurance, coverage level and status.
6. Keep private signing keys outside the database and client application.
7. Store original report in private object storage; expose a controlled read-only download.
8. Public verification checks signature, hash, status and revocation state.
9. When the same verified device is tested again, immediately remove the prior report from current public verification, delete the prior detailed test results and private PDF, and issue no replacement until the new test is complete and payment is verified. Keep only a minimal non-sensitive tombstone recording that a report was replaced; never expose the old report as current. The trusted backend must complete the database operation and private PDF deletion queue safely.

## Trust limits

A cryptographic signature proves the issued report has not changed; it does not prove that a browser or user supplied truthful measurements. Browser results can be manipulated by a compromised device. High-assurance claims require model-specific native/vendor evidence, controlled test procedures and/or external instruments.

## Session interruption and resume

The browser prototype checkpoints local state and requests a 30-minute resume window. This is best-effort only: storage can be cleared, evicted or unavailable, and a browser can be killed before pagehide handlers run. Resume must match the saved serial/session token, then re-run any interrupted or time-sensitive checks. Production sessions require a server-side checkpoint with an expiring, high-entropy resume token. A web page cannot guarantee continued sensor/hardware testing while suspended or closed.

## Delivery

Automatic WhatsApp PDF delivery requires an official provider integration, user consent, approved templates where required, retries and delivery-status handling. Browser downloads require an active page/user-agent interaction and cannot be guaranteed after the browser closes. Production should generate the report server-side after all required evidence is uploaded, then queue WhatsApp delivery and offer a secure download link.

## Ten-minute target

Ten minutes is a target for the standard browser suite, not a reason to omit tests. Permission prompts, guided checks and unavailable hardware must be recorded. Long-running or equipment-dependent checks may be separate stages; the report must show actual duration and incomplete checks.
