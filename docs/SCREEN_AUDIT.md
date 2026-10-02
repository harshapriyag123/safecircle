# Screen implementation audit — 2026-10-02 UTC

This maps the existing clients to implemented behavior. Compilation, synthetic
browser testing, deployed API testing, physical-device testing and store billing
are separate evidence. No claim that every platform has feature parity is made.

| Screen | Implemented behavior | Verification limits |
|---|---|---|
| Android Today/session setup | Local safety session, check-in, ETA extension, terminal resolution, background work and authenticated sync | Emulator CI; physical-device suspension/reconnect and live providers pending |
| Android Circle/Guardian journey | Primary/Backup contacts, signed links, session-scoped Guardian views/actions/timeline | Consenting provider delivery/device registration pending |
| Android Vault/history | Privacy choices, encrypted capsule, expiry and receipts | Production provider retention/backup deletion needs operator verification |
| Android Automate/toolkit | Rule builder, phrase, exit aid, diagnostics and labeled Judge simulation | Advanced convenience billing unverified |
| Android Nearby Support | Explicit real map searches; missing maps feedback | Listings and availability belong to the external map provider |
| Android Profile | Register/login/logout/delete, RevenueCat paywall/restore/Customer Center | Actual store products and purchases pending |
| Web Home/session | Authenticated start; canonical server check-in/ETA/resolve; failed requests preserve state; monitoring readiness remains visible on mobile | No monitoring claimed without a current external tick |
| Web Account | Registration/login validation, busy states, token revocation, private local state cleared between accounts | No third-party OAuth or password recovery claimed |
| Web Circle | Distinct signed Primary/Backup links, copy/share/open | Link issuance is separate from consented phone delivery |
| Web Shield/exit aid | Phrase test and server concern, explainable scanner, labeled simulated call | Concern recording does not mean immediate delivered alert; browser timers may suspend |
| Web History/Sync | Owner-only server history (latest 100), native state and actual available battery | Browser cannot fabricate phone location/battery |
| Guardian | Signed expiring view, acknowledged/request-check-in actions, privacy allowlist, terminal read-only state | Real delivery pending provider configuration |
| iOS Today/setup | Requires authentication; active session only after server accepts; refresh, canonical check-in/ETA/resolve, visible errors/busy states | Simulator compilation pending latest CI; physical devices/store pending |
| iOS Circle | Distinct role invites, share links, consented E.164 Primary/Backup session contacts, error feedback | Real delivery and contact refresh across app relaunch pending |
| iOS Vault | Apply privacy, encrypted one-hour instruction capsule/clear, owner-only history | Client does not collect precise location; Android parity incomplete |
| iOS Automate | Device-private phrase test/server concern; actual permission-based local reminder and cancellation | Custom background rules/recurring detection unavailable; local reminder is not server monitoring |
| iOS Pro/Account | Auth, logout clears only after revocation, billing/paywall/restore/Customer Center | Store configuration and actual purchases unavailable |
| Landing/privacy/demo | Product explanation, native/test screenshots, real routes/support, clearly simulated Guardian demo | Native premium demo video and store release pending |

The iOS reference client remains outside any claim of full Android parity. No
unrelated sponsor integrations were added to populate optional award fields.
