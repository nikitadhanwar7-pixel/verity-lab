# VERITY Lab — Product Truth Policy

Version: 0.4 draft

This policy is mandatory for every test, report, listing, API response, marketing page, and support answer.

## Non-negotiable rules

1. Never infer a whole-device health result from one successful test.
2. A test is `PASS` only when a documented criterion is measured and the evidence meets it. A user saying “it seemed fine” is recorded as `USER_CHECKED`, not an instrument-measured pass.
3. `FAIL` means a defined criterion failed. It does not automatically mean the component is broken: permission, routing, software, environment, or accessories can affect results.
4. `ATTENTION` means evidence is limited, subjective, inconsistent, or requires review.
5. `NOT_TESTED` means no reliable result was obtained. It must never count as a pass.
6. `UNSUPPORTED` means the platform/browser cannot perform this test. It is not a failure.
7. `INCONCLUSIVE` means evidence exists but cannot support a defensible conclusion.
8. Report the method, measured values, timestamp, platform/API, permissions, and limitation for each result where available.
9. Do not use “100% tested”, “all hardware certified”, “original parts verified”, “battery health certified”, or “fully genuine” unless an independently validated method actually supports that exact claim.
10. No global green PASS badge. A summary must show coverage and counts, including NOT_TESTED/UNSUPPORTED, and must say what was not covered.
11. Client-side results are untrusted input until validated and stored by the backend. A browser or app can be modified; evidence collection alone is not proof of an untampered device.
12. A report is not an independent certification until the production service, identity checks, evidence rules, security review, and supported-device validation are complete.

## Report language

Use: “The browser successfully opened a camera stream at the reported resolution.”
Do not use: “All cameras are working perfectly.”

Use: “A microphone signal was detected through the selected input route.”
Do not use: “The microphone hardware is 100% healthy.”

Use: “Battery level was reported by the operating system/browser API; battery capacity and cycle count were not available.”
Do not use: “Battery health passed.”

## Aggregate summary

Do not calculate an overall pass percentage by treating untested modules as passing. Display at minimum:
- completed checks
- measured PASS count
- FAIL count
- ATTENTION / INCONCLUSIVE count
- NOT_TESTED count
- UNSUPPORTED count
- coverage statement

Any future aggregate score must be validated, explain its formula, and must not hide unavailable or subjective tests.
