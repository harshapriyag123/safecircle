# SafeCircle Architecture

SafeCircle is a layered personal-safety platform built around temporary Safety Sessions, explainable state transitions, Guardian escalation, privacy-preserving data release, and subscription-based premium automation.

---

## 1. System architecture

```mermaid
flowchart TB
    subgraph Mobile["📱 SafeCircle Android App"]
      UI["UI Layer
Home • My Circle • SafeCircle+"]
      VM["State Layer
SafetyViewModel"]
      DOMAIN["Domain Layer
SafetyEngine • EscalationEngine • SafePhraseEngine"]
      DATA["Data Layer
SafetyRepository • AutomationRepository • FamilyCircleRepository"]
      PLATFORM["Platform Services
Battery • Location • Notifications • WorkManager"]
      SECURITY["Security
Android Keystore • AES-GCM • SafetyCapsuleStore"]
      RELIABILITY["Reliability
AuditLog • OfflineEventQueue • RateLimiter"]
      BILLING["RevenueCat
Entitlement • Paywall • Restore • Customer Center"]
    end

    subgraph Cloud["☁️ Production Cloud Boundary"]
      API["SafeCircleGateway"]
      AUTH["Authentication / Device Identity"]
      SESSION["Session Sync Service"]
      JOBS["Idempotent Escalation Scheduler"]
      PUSH["Push / Messaging Provider"]
      AUDIT["Remote Audit + Delivery Receipts"]
    end

    subgraph Guardian["🛡️ Guardian Experience"]
      WEB["Guardian Web View"]
      PHONE["Guardian Mobile / Push"]
    end

    UI --> VM
    VM --> DOMAIN
    VM --> DATA
    DATA --> PLATFORM
    DATA --> SECURITY
    DOMAIN --> RELIABILITY
    UI --> BILLING

    RELIABILITY --> API
    DATA --> API
    API --> AUTH
    API --> SESSION
    API --> JOBS
    JOBS --> PUSH
    JOBS --> AUDIT
    PUSH --> PHONE
    SESSION --> WEB
```

---

## 2. Safety Session lifecycle

```mermaid
stateDiagram-v2
    [*] --> NORMAL: Session starts
    NORMAL --> ATTENTION: Low battery / weak signal
    NORMAL --> CHECK_IN: ETA threshold reached
    ATTENTION --> CHECK_IN: Check-in required
    CHECK_IN --> NORMAL: User confirms
    CHECK_IN --> CONCERN: Check-in missed
    CONCERN --> NORMAL: User confirms
    CONCERN --> ESCALATED: Threshold / repeated misses
    ESCALATED --> RESOLVED: User / Guardian resolves
    NORMAL --> RESOLVED: User marks safe
    ATTENTION --> RESOLVED: User marks safe
    CHECK_IN --> RESOLVED: User marks safe
    CONCERN --> RESOLVED: User marks safe
    RESOLVED --> [*]
```

The engine does **not** claim to predict danger. It maps observable session conditions into an explainable state.

---

## 3. Escalation sequence

```mermaid
sequenceDiagram
    actor U as User
    participant A as SafeCircle App
    participant W as WorkManager
    participant Q as Offline Queue
    participant G1 as Primary Guardian
    participant G2 as Backup Guardian
    participant C as Safety Capsule

    U->>A: Start Safety Session
    A->>W: Schedule 0 / +5 / +10 / +15 min checks

    W-->>A: Expected-safe time reached
    A-->>U: Gentle check-in

    alt User responds
      U->>A: I'm Safe / Extend ETA
      A->>W: Cancel or reschedule work
    else No response
      W-->>A: +5 min
      A->>Q: Queue Guardian event
      Q-->>G1: Notify primary Guardian

      W-->>A: +10 min
      A->>Q: Queue backup event
      Q-->>G2: Notify backup Guardian

      W-->>A: +15 min
      A->>C: Release pre-authorized fields
      C-->>G1: Safety Capsule access
    end
```

---

## 4. Privacy architecture

```mermaid
flowchart LR
    RAW["Sensitive session data"] --> CONSENT["User consent + session scope"]
    CONSENT --> MIN["Minimum necessary fields"]
    MIN --> LOCK["Encrypted Safety Capsule
AES-GCM + Android Keystore"]
    LOCK --> POLICY{"Escalation threshold reached?"}
    POLICY -- No --> PRIVATE["Remain private"]
    POLICY -- Yes --> SHARE["Release authorized fields"]
    SHARE --> EXP["Expiry / retention timer"]
    EXP --> DELETE["Automatic deletion"]
```

Key rules:

- location sharing is session-scoped;
- precise location is opt-in;
- Safety Capsules expire;
- local capsule data is encrypted with Android Keystore-backed AES-GCM;
- core safety features do not depend on subscription state.

---

## 5. Offline-first reliability

```mermaid
flowchart LR
    EVENT["Safety event"] --> LOCAL["Local state update"]
    LOCAL --> AUDIT["AuditLog"]
    LOCAL --> QUEUE["OfflineEventQueue"]
    QUEUE --> NET{"Network / backend available?"}
    NET -- No --> RETRY["Keep queued"]
    RETRY --> QUEUE
    NET -- Yes --> GATEWAY["SafeCircleGateway"]
    GATEWAY --> ACK["Server acknowledgement"]
    ACK --> RECEIPT["DeliveryReceipt"]
    ACK --> REMOVE["Remove queued event"]
```

The app has local queue, rate limiting, audit events, and a cloud gateway contract. A real backend implementation can replace the demo gateway without changing Safety Engine logic.

---

## 6. RevenueCat entitlement boundary

```mermaid
flowchart LR
    MONTHLY["Monthly"] --> ENT["safecircle_pro"]
    YEARLY["Yearly"] --> ENT

    ENT --> AUTO["Advanced automations"]
    ENT --> MULTI["Multiple Guardians"]
    ENT --> FAMILY["Family Circles"]
    ENT --> CAPSULE["Premium Safety Capsule controls"]
    ENT --> HISTORY["Extended history"]

    FREE["Free tier"] --> BASIC["Basic Safety Sessions"]
    FREE --> CHECK["Check-ins"]
    FREE --> ONE["One Guardian"]
```

This prevents monetization from becoming a single-product-ID dependency.

---

## 7. Code map

```text
app/src/main/java/com/harshapriya/safecircle/
├── auth/
│   └── AccountRepository.kt
├── automation/
│   ├── AutomationRule.kt
│   └── AutomationRepository.kt
├── background/
│   ├── SafetyScheduler.kt
│   ├── SafetyCheckWorker.kt
│   ├── RecurringCommuteScheduler.kt
│   └── RecurringCommuteWorker.kt
├── billing/
│   └── SubscriptionManager.kt
├── data/
│   ├── Constants.kt
│   └── SafetyRepository.kt
├── domain/
│   ├── SafetyEngine.kt
│   ├── EscalationEngine.kt
│   └── SafePhraseEngine.kt
├── family/
│   └── FamilyCircleRepository.kt
├── guardian/
│   └── GuardianInviteService.kt
├── platform/
│   ├── BatteryMonitor.kt
│   ├── LocationProvider.kt
│   └── NotificationService.kt
├── reliability/
│   ├── AuditLog.kt
│   ├── DeliveryReceipt.kt
│   ├── OfflineEventQueue.kt
│   └── RateLimiter.kt
├── security/
│   ├── CryptoBox.kt
│   └── SafetyCapsuleStore.kt
├── sync/
│   ├── SafeCircleGateway.kt
│   └── LocalDemoGateway.kt
└── ui/
    ├── home/
    ├── circle/
    ├── profile/
    └── shared/
```

---

## 8. Phase coverage

### Phase 1 — Core
Implemented: Safety Sessions, readiness score, state engine, Guardian model, RevenueCat entitlement, paywall, restore flow, Customer Center.

### Phase 2 — Connected Safety
Implemented in Android/local form: Guardian invitation sharing, WorkManager escalation jobs, device battery, consented location adapter, editable ETA API, offline event queue, recurring commute scheduler, Guardian web prototype.

Remote push delivery and live multi-device synchronization are represented by `SafeCircleGateway` and require a real backend/provider configuration.

### Phase 3 — Privacy & Reliability
Implemented: Android Keystore encryption, Safety Capsule store, expiration purge support, audit log, rate limiter, delivery receipt model, idempotent-stage keys, offline queue, threat model.

### Phase 4 — Family & Multi-surface
Implemented: Family Circle repository, Guardian web prototype, recurring commute scheduler, automation rule repository, stable local cross-device account identifier contract.

A production multi-device account system still requires authenticated cloud identity and server-side authorization.

---

## 9. Production boundary

The repository now contains working mobile-side implementations and explicit integration contracts for all roadmap phases. It does **not** pretend that local demo adapters are a production emergency network.

Before real-world deployment, SafeCircle still needs:

- authenticated cloud backend;
- verified Guardian acceptance;
- real push/SMS provider credentials;
- server-side idempotency and delivery acknowledgement;
- secure multi-device session sync;
- penetration/security review;
- privacy/legal review;
- accessibility and reliability testing;
- emergency-services policy review.
