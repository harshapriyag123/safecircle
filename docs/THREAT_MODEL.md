# SafeCircle Threat Model

## Assets
- Active Safety Sessions
- Guardian relationships
- Location metadata
- Safety Capsules
- Authentication identifiers
- Subscription state
- Audit trail

## Threats and mitigations

| Threat | Mitigation in architecture |
|---|---|
| Stalker obtains permanent location access | Session-scoped sharing, progressive disclosure, explicit consent |
| Forged Guardian invite | Expiring invite codes; production backend must sign/verify invites |
| Duplicate escalation | Idempotency keys + rate limiting + audit events |
| Lost phone exposes Safety Capsule | Android Keystore AES-GCM encrypted local storage |
| Replay of queued events | Event IDs + server acknowledgement contract |
| Notification spam | RateLimiter + staged escalation |
| Silent failure while offline | OfflineEventQueue + later reconciliation |
| Subscription failure blocks safety | Core safety features are not gated behind Pro |
| App claims false danger prediction | Explainable state engine reports conditions, not danger certainty |

## Production requirements
- Signed authenticated API requests
- Device/session revocation
- Key rotation
- TLS pinning evaluation
- Backend authorization tests
- Privacy retention controls
- Security review before real emergency use
