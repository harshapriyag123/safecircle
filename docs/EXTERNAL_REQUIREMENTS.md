# Remaining external requirements — 2026-10-02 UTC

The existing Vercel Hobby domain hosts the full website/backend. A dedicated
Neon Free database and private secrets are configured after the owner's explicit
terms approval. Controlled production API tests passed; this is not evidence of
continuous monitoring or delivery. No Railway setup is required.

1. **Continuous scheduler account/access:** authorize an existing Cloudflare
   Workers Free account to provision `scheduler/cloudflare`, or supply another
   reviewed free platform capable of minute-level execution. The adapter and
   tests are complete; deployment and at least two platform-scheduled ticks are
   not verified. Cloudflare dashboard access was blocked by its browser security verification; use an authorized account/CLI route once access is available. Manual diagnostic ticks are not a scheduler. Track Vercel/Neon/
   Cloudflare free quotas; exhausted quotas can pause service. No paid overage,
   payment method or automatic upgrade is authorized. Continuous database traffic
   consumes Neon compute allowance and cannot be promised indefinitely for $0.
2. **Notification provider and consenting contacts:** configure Twilio account,
   sender and signed callbacks privately for SMS testing. Push requires an
   authenticated HTTPS adapter with opted-in Guardian registration/routing,
   idempotency and delivery receipts; the repo supplies the adapter contract,
   not a production push registration service. Obtain explicit test-contact
   consent before live delivery. Verify queued, accepted, delivered, uncertain
   outcomes and cancellation using actual receipts. No real messages were sent.
3. **RevenueCat/store access:** Android public SDK key and Apple key if iOS is
   submitted; connected store credentials/product IDs mapped to `safecircle_pro`,
   current offering/published paywall and Customer Center. Supply a server-only
   credential for subscriber reads and configure the generated webhook auth
   privately in RevenueCat. `proj5f7132ef` is metadata only. Store test accounts,
   Play signing/test-track access and Apple signing/TestFlight if applicable are
   needed for actual purchase/restore/refund/account-transfer tests. No purchase
   is claimed from SDK compilation or webhook unit tests.
4. **Device and submission evidence:** physical-device background/reconnect and
   permission testing; qualifying store publication/US availability/release-date
   evidence, or confirmed eligible Next Gen student status/academic verification
   and guardian consent if a minor. Record/upload the actual native premium
   demo video. The 2026 deadline has passed; organizer acceptance/eligibility
   cannot be inferred from this implementation. Supply actual launch/growth and
   dated public-feedback evidence only for categories requiring it.

Secrets must stay out of chat, git, screenshots and native client bundles except
public SDK keys. No downloads, revenue, customer feedback, store publication,
continuous alert delivery or successful purchase has been established.
