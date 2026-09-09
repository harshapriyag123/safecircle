# SafeCircle Architecture

SafeCircle is designed as a layered personal-safety system rather than a single SOS button.

## Safety state machine

`NORMAL → ATTENTION → CHECK_IN → CONCERN → ESCALATED → RESOLVED`

Transitions are based on observable session conditions such as overdue ETA, missed check-ins, battery state, and an optional route-deviation signal. The prototype deliberately does not claim to determine whether a person is in danger.

## Progressive escalation

- 0 minutes: gentle user check-in
- +5 minutes: primary guardian notification
- +10 minutes: backup guardian notification
- +15 minutes: pre-authorized Safety Capsule release

Production implementations should execute these steps through a reliable backend job system with idempotency, delivery receipts, retries, and an audit trail.

## Safety Capsule

A Safety Capsule is a time-limited package of information the user authorizes before the session. It can contain session metadata, destination, battery state, guardian instructions, and location information according to the user's privacy mode.

## SafePhrase

SafePhrase supports two user-defined signals:

- Yellow phrase: quietly request a guardian check-in.
- Red phrase: silently execute the user's configured escalation policy.

## Subscription boundary

Core check-in and personal-safety functionality remains free. The RevenueCat `safecircle_pro` entitlement is intended for advanced automation, multiple guardians, family circles, extended history, and premium privacy/workflow controls.

## Production backend

Recommended production components:

- Authentication with stable non-email user IDs
- Encrypted session store
- Guardian invite/verification service
- Push notification provider
- Background escalation scheduler
- Audit/event log
- Rate limiting and abuse prevention
- Consent and location-privacy controls
- Offline event reconciliation
