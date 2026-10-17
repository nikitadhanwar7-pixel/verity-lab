# VERITY Lab — Evidence and Test Coverage Matrix

This is a design contract, not a claim that every listed test is already implemented or validated. The native Android source foundation currently implements only a small subset. The browser matrix below documents the legacy prototype and is not the chosen production testing path.

## Legacy browser diagnostics (no app install)

| Test area | Browser can establish | Cannot establish | Honest result language |
|---|---|---|---|
| Display | User can view test patterns; record visual observations | Burn-in, calibrated color/brightness, dead pixels automatically with certainty, panel originality | `USER_CHECKED` / `ATTENTION`; record observations |
| Touch | Touch coordinates/events arrive in the page | Digitizer performance outside the active page, pressure/latency calibration, all edge defects | Measured event coverage plus user observations |
| Camera | Camera stream opens; selected stream settings; optional user capture | Every rear lens, optical stabilization, focus accuracy, sensor defects, original module | `PASS` only for stream-open criterion; explicitly name selected camera route |
| Microphone | Permission works and an input signal is present | Every mic, calibrated frequency response, noise cancellation quality, physical mic identity | Signal detected / no signal / inconclusive |
| Speaker/audio | Browser can play a tone; user can report hearing it | Calibrated SPL, distortion, each speaker separately unless routed and verified | User-confirmed playback, not calibrated speaker pass |
| Vibration | Browser request accepted; user reports feeling vibration | Motor amplitude, frequency, actuator wear | Request accepted + user observation |
| Motion/orientation | Events and values exposed by browser | Calibration, exact sensor inventory, guaranteed gyro/magnetometer health | Values observed / unavailable |
| Browser storage | Specific browser storage write/read-back succeeds | Entire flash chip, wear, bad blocks, physical storage health | Browser storage round-trip only |
| JavaScript performance | A bounded workload completed in a measured duration | CPU/GPU health, thermal throttling diagnosis, comparable cross-device benchmark | Timed workload; environment strongly affects result |
| WebGL | Context and limited draw operations succeed | Full GPU health, GPU memory integrity, sustained performance or thermals | WebGL smoke check only |
| Network | Browser online status and optional network API hints | RF sensitivity, antenna quality, modem health, SIM/IMEI authenticity, guaranteed speed | Browser-reported network hints only |
| Device profile | User agent, screen metrics and available browser hints | Trusted exact model, serial, IMEI, genuine parts or tamper-proof identity | Unauthenticated self-reported profile |
| Battery | At most a charge level/state if the API exists | Capacity, cycle count, voltage, internal resistance, battery safety, IC temperature | API-reported level only; battery health `UNSUPPORTED` |
| Buttons / ports / biometric / NFC / SIM / radio / proximity | Guided manual observations may be recorded | Electrical state, secure element, radio performance, biometric integrity or internal faults | Manual observation or `UNSUPPORTED` |

## Android app: additional potential coverage

The native Android app can be more capable than a browser, but only for documented APIs available to the app and the device. Each test must be prototyped and validated against real devices before being advertised.

- Device/build profile from Android APIs: useful metadata, not proof of genuine identity; some identifiers are restricted or unavailable.
- Sensor inventory and event sampling: can enumerate sensors exposed to the app and collect readings; cannot prove calibration or all hardware integrity.
- Camera2 camera enumeration/capabilities and capture: can test cameras exposed to the OS and run capture checks; may miss hidden/disabled/defective modules or vendor-specific issues.
- Audio input/output routing and loopback where supported: can collect more structured evidence; no calibrated acoustic verdict without known equipment.
- Vibration API: request/observation only; no calibrated actuator measurement.
- Storage benchmark: can measure a defined app-level read/write workload; cannot establish flash wear or full storage integrity.
- BatteryManager: may expose charge, voltage, temperature and status; health/cycle data vary by device/OS and are not universal. Do not claim battery capacity unless an approved method supports it.
- Thermal APIs: may expose OS thermal status, not a direct, calibrated temperature for every chip.
- Connectivity: can check available transports and basic requests, not certify antenna/RF hardware.

## iPhone/iOS path

Third-party iOS apps have strict sandbox and privacy limits. Do not promise access to battery cycles, full hardware identity, internal temperature, all component health, or low-level radio/port tests. Use browser checks plus Apple-provided diagnostics/service workflows where eligible and clearly label any external/manual checks. A web app cannot become a full iPhone hardware diagnostic tool just by being installed to the Home Screen.

## Physical or external-equipment checks

Claims such as calibrated brightness, speaker level/distortion, USB electrical integrity, RF/antenna performance, internal water damage, board faults, and battery capacity may require fixtures, calibrated instruments, authorized service diagnostics, or teardown. These should be a separate future tier, not implied by a browser or app test.

## Validation gate before publishing a test

For every test code, document: supported OS/model/browser matrix, method, preconditions, pass/fail threshold, false-positive/negative risks, evidence payload, limitations, manual QA results, and version. If thresholds are subjective, label the result as user-reported rather than measured PASS.
