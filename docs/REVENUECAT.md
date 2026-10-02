# RevenueCat configuration and live verification

Dashboard project identifier: `proj5f7132ef`, confirmed by the supplied screenshot. This identifier is administrative metadata and is not an SDK/API key or public SafeCircle website URL.

## Configure the project

1. Verify the Android app package `com.harshapriya.safecircle` and the optional Apple bundle ID in `ios/project.yml` match the store apps.
2. Connect Play/App Store credentials in RevenueCat; create actual store products and billing periods. No prices or product IDs are invented by this code change.
3. Map purchased products to exactly `safecircle_pro`; create a current offering with monthly/annual packages as appropriate and publish its paywall. Configure Customer Center support and subscription management.
4. Set Android public SDK key in ignored `local.properties` or CI secret `REVENUECAT_ANDROID_PUBLIC_KEY`. Set Apple public SDK key via the Xcode build setting `REVENUECAT_PUBLIC_SDK_KEY`. Use platform public keys, never server secrets. Android/iOS billing is disabled for an unconfigured build.
5. Set server-only `REVENUECAT_SECRET_API_KEY` to a credential permitted to read the v1 subscriber endpoint for this project. Configure webhook URL `/webhooks/revenuecat` and exact authorization value `Bearer <REVENUECAT_WEBHOOK_SECRET>`.

Native billing customer IDs follow the authenticated SafeCircle account. Android configures persisted identity at startup and logs in/out on account changes; iOS also changes billing identity with app authentication. CustomerInfo determines the native entitlement state. Android refreshes after purchase/restore/Customer Center; iOS refreshes on sheet dismissal and restore. Essential safety remains usable without billing.

## Server processing

Webhook fallback handles the supported entitlement's lifecycle without ending a paid period on renewal cancellation. It ignores older/repeated state updates and applies read-time expiration. Production ignores sandbox webhook access grants. Every affected customer, including aliases and both sides of a transfer, gets a durable reconciliation job. Unknown future event types can still request reconciliation.

The worker reads `GET https://api.revenuecat.com/v1/subscribers/{customer}`. Authoritative entitlement absence removes mirror access; expiration/lifetime and sandbox state are handled. Failures keep the last mirror state, retry later and never log credentials or customer response bodies. New events arriving during a fetch retain a subsequent job. Owner-only POST `/v1/subscriptions/{owner}/reconcile` queues a refresh; it is not a client assertion of purchase.

The backend mirror is not a complete purchase history. Production premium server endpoints must use authoritative reconciled state; current essential endpoints are free. Test-store and sandbox purchases are evidence of test behavior, not revenue.

## Required device/store test matrix (currently pending)

| Test | Required evidence |
| --- | --- |
| Offering/paywall loads | Actual product IDs, localized store prices and published paywall on device |
| Purchase success | Store test transaction, CustomerInfo `safecircle_pro`, server refresh and webhook |
| Cancel/error/pending | Correct UI without granting access for failure or pending transaction |
| Restore on fresh install | Same store account; entitlement restored under intended customer identity |
| Account change and transfer | No previous-account access leak; both server mirrors reconciled |
| Renewal/cancellation/expiry/refund | Correct access through paid/grace period and removal afterward |
| Customer Center | Opens, shows actual subscription and completes supported management flow |
| iOS, if submitted | Signed device/TestFlight build, Apple sandbox purchase/restore and Customer Center |

Code/CI checks cannot verify dashboard offerings, products, credentials or a real purchase. No RevenueCat account tool is callable in the current session; the screenshot verifies only the project ID.

References: [webhook guidance](https://www.revenuecat.com/docs/integrations/webhooks), [event flows](https://www.revenuecat.com/docs/integrations/webhooks/event-flows), [customer info](https://www.revenuecat.com/docs/customers/customer-info).
