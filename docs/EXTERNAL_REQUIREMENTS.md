# Inputs and live checks still needed

These are external prerequisites, not completed implementation claims. Do not paste secrets into public issues, PRs, screenshots or client code.

1. **Free backend hosting:** provide an always-running host with persistent storage (a computer you already own can use `compose.selfhost.yml`) or authorized account access to a compatible host. No paid resources may be created. The public static preview is deployed; it is not an API/backend. Cloudflare Quick Tunnel was network-blocked here. Render connection was not confirmed, and its free sleeping/ephemeral service cannot run the current durable safety backend unchanged. See `FREE_HOSTING.md`; verify HTTPS routes, account/Guardian workflows and restart persistence before rebuilding Android with its origin.
2. **RevenueCat and stores:** Android public SDK key (and Apple public key if iOS is submitted), connected store credentials and actual product IDs mapped to `safecircle_pro`, a current offering/published paywall and configured Customer Center. A server-only key authorized for v1 subscriber reads and independent webhook authorization are needed for reconciliation. Project ID `proj5f7132ef` supplies none of these credentials. Verify the device purchase/restore/account-change/refund matrix in `REVENUECAT.md` using store test accounts; code tests do not establish purchases.
3. **Delivery providers:** Twilio SID/auth token/sender, permitted consenting test destinations and signed receipt callback configuration. If push is enabled, provide an HTTPS adapter with opted-in Guardian device registration/routing, idempotent delivery and authenticated receipts, plus separate adapter/receipt credentials. Verify accepted vs delivered, failures, ETA cancellation and resolution on the deployed service. This repository currently supplies an adapter contract rather than its own push registration service.
4. **Submission eligibility and assets:** qualifying public store listing/release-date/US availability evidence, or confirmed eligible Next Gen student status with academic verification and parental consent if required. Provide Play signing/test-track access; Apple signing/TestFlight access only if iOS is submitted. Record/upload a public native demo using actual configured premium flows (script in `SHIPATON.md`); native emulator screenshots and the 1024px icon can support assets but do not prove store publication. Supply real growth/build-in-public evidence only for awards requiring it.

No downloads, revenue, customer feedback, production deliveries or store publication have been established. Peace Prize is an intended-purpose fit; Design needs polished native evidence; Next Gen is conditional. Optional unrelated sponsor categories should remain blank.

## Current Vercel continuation

1. Approve Vercel Marketplace/Neon terms before creating a dedicated free database
   and confirm that the available plan remains $0 without paid overages.
2. Configure its private TLS PostgreSQL URL and independent production secrets
   listed in VERCEL_DEPLOYMENT.md. Server secrets can be generated securely; they
   must not be posted in chat or committed.
3. Provide an always-on worker host for the 15-second tick runner (or complete a
   tested durable scheduler integration). Daily free cron cannot meet alert timing.
4. Authorized notification provider configuration and consenting test contacts.
5. RevenueCat SDK/store credentials, offerings/products, webhook/reconciliation
   credentials and device/store test access for purchase/restore/Customer Center.
