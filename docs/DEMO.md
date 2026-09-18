# SafeCircle Hackathon Demo

Use this script for a protected-journey walkthrough that fits inside the two-minute submission video.

Guardian acknowledgement and check-in requests are real persisted backend events. The missed check-in signal remains an explicitly labelled judge simulation so the sequence is reliable without waiting for the real ETA.

## 0. Before the demo

1. Open **Pro → System health**.
2. Confirm Android permissions and RevenueCat/backend status.
3. Tap **1 · Seed judge demo**.
4. Return to **Today**.

## 1. The problem — 20 seconds

Pitch:

> “SafeCircle turns ‘text me when you get home’ into a programmable, privacy-first Safety Session.”

Show:
- Safety Readiness
- Walk Home session
- expected-safe time
- live battery
- destination
- session-scoped location state

## 2. The normal flow — 35 seconds

On **Today**:
- show the live countdown;
- tap **Check in**;
- explain that WorkManager schedules escalation checkpoints;
- show **+5 min / +15 min** ETA controls.

Key line:

> “The app does not claim to predict danger. It reacts to observable session conditions and a user-defined plan.”

## 3. Automation depth — 35 seconds

Open **Automate**:
- show IF → THEN rules;
- open the Automation Builder;
- show triggers and multiple actions;
- show SafePhrase;
- show recurring commute reminders.

Explain that `safecircle_pro` gates advanced automation rather than core safety.

## 4. Trigger a concern — 25 seconds

Open **Pro → System health** and tap **2 · Simulate concern**.

Return to **Today** and show:
- readiness score change;
- CONCERN state;
- missed-check-in / route-deviation reasoning;
- progressive escalation preview.

## 5. Guardian experience — 35 seconds

Open **Circle**:
- enter the real Guardian's name and persist the contact;
- show Guardian Live session summary;
- create and share a signed Guardian link;
- on the Guardian device, select **I'm watching**;
- show **Acknowledged** and its server timestamp appear in Android;
- on the Guardian device, select **Request check-in**;
- show **Check-in requested** and its server timestamp appear in Android.

Explain:
- Guardian token is signed and expiring;
- precise location is hidden until the configured privacy threshold;
- Safety Capsule is withheld until escalation.
- failed synchronization is displayed as failed, never as acknowledged.

## 6. Privacy story — 35 seconds

Open **Vault**:
- Android Keystore + AES-GCM;
- Safety Capsule;
- privacy mode;
- audit trail;
- session history;
- Safety Receipt.

Key line:

> “Membership in a Circle never means permanent tracking. Each Safety Session decides what can be seen and when.”

## 7. Monetization — 25 seconds

Open **Pro**:
- RevenueCat `safecircle_pro`;
- monthly/yearly offering;
- hosted Paywall;
- restore purchases;
- Customer Center;
- Emergency Profile;
- account sign-in.

Explain that core check-in and basic safety remain available without Pro.

## 8. Resolution — 15 seconds

Open **System health** and tap **3 · Resolve as safe**.

Return to **Today** and show RESOLVED / completed session behavior.

## Judge-ready architecture proof

If judges ask “is this just UI?”, show:

- `domain/SafetyEngine.kt`
- `domain/EscalationEngine.kt`
- `background/SafetyCheckWorker.kt`
- `security/CryptoBox.kt`
- `security/SafetyCapsuleStore.kt`
- `sync/HttpSafeCircleGateway.kt`
- `backend/app/main.py`
- `backend/app/security.py`
- `backend/app/providers.py`
- `web/guardian/index.html`
- `ios/SafeCircleIOS/`

## CI proof

The repository includes:
- Android unit-test + assemble CI
- Backend pytest CI
- native iOS simulator build CI

## Production caveat

The hackathon build includes provider adapters and deployment contracts, but real SMS/push delivery requires deployment credentials. External accessibility/security reviews and final store dashboard product configuration are intentionally not represented as completed by code.
