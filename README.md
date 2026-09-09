# SafeCircle — Personal Safety OS

> **Someone should notice.** A programmable safety network for walks, rideshares, meetups, commutes, and time alone.

SafeCircle is a hackathon-ready Android prototype built around personal safety and RevenueCat monetization. It keeps essential safety functionality free while using `safecircle_pro` for advanced features.

## Why this can stand out

Most safety apps are built around a panic button. SafeCircle starts earlier: a user creates a **Safety Session**, defines when they expect to be safe, chooses who should notice, and configures what should happen if they miss a check-in. The app uses an explainable state engine rather than claiming to "detect danger" with AI.

### Hero systems

- **Safety Sessions** — Walk Home, Rideshare, Meet Someone, Stay With Me
- **Explainable Safety Engine** — NORMAL → ATTENTION → CHECK_IN → CONCERN → ESCALATED → RESOLVED
- **Readiness Score** — based on ETA, check-ins, battery signal, and route-deviation signal
- **Guardian Circle** — primary, backup, and group escalation contacts
- **Safety Automations** — progressive escalation ladder
- **Privacy-first Safety Capsule** — designed for pre-authorized information release
- **SafeCircle+** — RevenueCat entitlement-gated advanced features

## RevenueCat integration

- `com.revenuecat.purchases:purchases:10.20.0`
- `com.revenuecat.purchases:purchases-ui:10.20.0`
- Entitlement: `safecircle_pro`
- Offering: `default`
- Packages: `monthly`, `yearly`
- RevenueCat hosted Paywall
- CustomerInfo entitlement refresh
- Restore Purchases
- RevenueCat Customer Center

### Configure your public SDK key

Never commit secret backend keys. Add your RevenueCat **public Android SDK key** locally:

```properties
# local.properties
REVENUECAT_API_KEY=test_your_public_sdk_key_here
```

## RevenueCat dashboard checklist

1. Create entitlement `safecircle_pro`.
2. Create/import monthly and yearly store products.
3. Attach both products to `safecircle_pro`.
4. Add packages `monthly` and `yearly` to the `default` offering.
5. Build and publish a RevenueCat Paywall for the current offering.
6. Test with a Google Play license tester / RevenueCat test configuration.

## Architecture

```text
MainApplication
  └─ RevenueCat configuration + CustomerInfo listener

MainActivity
  ├─ Bottom navigation
  └─ RevenueCat Paywall launcher

SafetyRepository
  └─ session persistence

SafetyEngine
  └─ explainable risk/readiness evaluation

SafetyViewModel
  └─ shared session state

HomeFragment
  ├─ readiness score
  ├─ safety session modes
  ├─ check-in / I'm Safe
  └─ escalation simulator

CircleFragment
  ├─ guardians
  └─ privacy model

ProfileFragment
  ├─ safecircle_pro status
  ├─ hosted RevenueCat paywall
  └─ restore purchases
```

## Demo script

1. Open SafeCircle and show the readiness score.
2. Start **Walk Home**.
3. Explain expected-safe time and Guardian Circle.
4. Simulate a concern state to show explainable score/state change.
5. Open **My Circle** and show escalation/privacy.
6. Open **SafeCircle+** and launch the RevenueCat-hosted paywall.
7. Close with: **“You decide who should notice, what they should know, and what should happen when you don’t check in.”**

## Privacy principle

SafeCircle should never imply it can determine that a person is in danger. It reports observable session conditions and executes the user's preconfigured escalation plan. Precise location sharing should be opt-in, scoped, and time-limited.

## Production roadmap

Before real-world deployment: encrypted backend storage, verified guardian invites, authenticated accounts, push delivery, consented location permissions, audit logs, offline reconciliation, abuse prevention, accessibility testing, and emergency-services policy review.
