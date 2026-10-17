# Retest and report replacement policy

## Retest of a previously tested serial
1. Normalize the supplied serial and compute its lookup HMAC on the trusted backend. Never use raw serial text as a public key.
2. Create a new session and confirm it belongs to the same device record.
3. In one database transaction, call `begin_device_retest(device_id, new_session_id)` before accepting new evidence.
4. The previous report is immediately absent from active verification. Its certificate row, test results, old session and delivery jobs are deleted. The private PDF is queued for deletion from object storage.
5. Keep only a minimal audit tombstone containing a hash of the prior public report ID and the fact that replacement occurred. It must not include the serial, test measurements, PDF or old public ID.
6. Do not issue a new report until tests are complete and payment is independently verified. If the new test is abandoned, do not resurrect the old report; the phone has no current valid report until it is tested and issued again.

## Repeat attempts
- Retest a questionable/borderline/inconclusive result once, under comparable conditions, and store both attempts.
- Best-of-two is valid only for a defined metric where better is meaningful (e.g. lower duration in a controlled benchmark). Defect detection cannot simply discard the worse attempt.
- For intermittent faults, retain the worst valid observation and evaluate repeatability. If attempts conflict, mark the internal result inconclusive and run a targeted test where possible.
- Camera, touch and other interactive checks require a real second interaction; do not duplicate a prior measurement and call it a retest.
- Customer-facing reports use plain-language findings. Internal outcomes and evidence remain structured and auditable.
