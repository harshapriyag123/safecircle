# Shipaton 2026 submission preparation

Checked October 1, 2026 against [official rules](https://revenuecat-shipaton-2026.devpost.com/rules), [submission guide](https://www.shipaton.com/blog/how-to-submit-your-app-for-shipaton), [Next Gen](https://www.shipaton.com/next-gen) and [deadline extension](https://revenuecat-shipaton-2026.devpost.com/updates). Submissions close October 1 at noon Pacific (19:00 UTC). The standard first-release window is August 1–September 30, 2026; verify any extension's effect on store release eligibility directly with organizers. Do not assume a code update or APK changes that window.

## Requirements and evidence

| Requirement | Current state |
| --- | --- |
| Qualifying new native app released in supported store, available in US | No store listing/public release evidence supplied. Material blocker for standard entry. Website/debug APK are insufficient. |
| RevenueCat-powered in-app purchase | SDK, paywall, entitlement, restore and Customer Center implemented. Dashboard/store product configuration and live flow unverified. |
| Project ID | `proj5f7132ef`, supplied dashboard screenshot. |
| Public native demo video (YouTube/Vimeo), premium features early | Script below; actual native recording/upload pending. Keep video about two minutes and show premium before minute three. |
| App icon and screenshots | 1024px icon exported from the native vector and attached to website CI. Website demo screenshot is captured from actual UI and labeled simulated. Native emulator screenshots and offline workflow verification are attached in `VERIFICATION.md`; prepare final submission dimensions; do not substitute a website screenshot for a native app screenshot. |
| Written project story and relevant award responses | Explain actual implementation; describe pending verification honestly. No revenue/download/customer claims established. |
| Testing access/promo if needed | CI debug APK exists. Play/App Store testing access and premium review access still need configuration. Demo does not prove a purchase. |
| Next Gen exception | Conditional on active eligible student status, public source/demo and academic verification; guardian consent if a minor. User eligibility not confirmed. |
| Public source | Existing public repository and reviewable PR #2. |
| Public website/privacy/support | Public static preview published; real Guardian/backend deployment and complete native video remain pending. |

## Two-minute native demo script

- **0:00–0:15:** “SafeCircle helps a trusted circle notice when a check-in is missed without permanent location sharing.” Show Home and free safety controls.
- **0:15–0:40:** Start a short test session, show expected-safe time, choose Status Only, configure consenting Primary/Backup test contacts and create a signed Guardian link. Do not expose phone numbers or token strings in the recording.
- **0:40–1:05:** Open the signed link on a second device. Show the actual session. Use Judge Mode only if needed and label the screen “Simulated escalation; no delivery evidence.” Explain +5 Primary, +10 Backup, +15 both.
- **1:05–1:35:** Show the actual published RevenueCat paywall with localized prices, a verified sandbox purchase or restore if configured, and the optional advanced automation/family convenience UI. State explicitly if the build has no billing key; do not narrate a simulated unlock as a successful purchase.
- **1:35–1:50:** Show Customer Center. Return to the session, check in or extend ETA, then mark safe. Guardian view should show resolution and pending jobs cancel.
- **1:50–2:00:** Close on privacy, essential safety remaining free, and the real testing/store URL. State material limitations concisely.

Record a real configured native build. The web simulated demo is useful for explanation but is not the required native video or payment evidence.

## Awards supported by available evidence

RevenueCat Peace Prize is the strongest product-purpose fit: privacy-respecting check-ins and consented trusted contacts. Describe intended benefit, not proven outcomes. Design Award is a candidate after native interaction/screenshots demonstrate polish. Next Gen is conditional on student eligibility. Grand Prize requires general eligibility plus actual launch/growth evidence. HAMM requires verified product/pricing/paywall decisions and transaction evidence; current code alone does not establish monetization results. Build in Public requires actual dated public progress posts and resulting feedback; none supplied. No influencer category is an obvious supported fit.

Leave optional sponsor categories blank unless the app actually implements and verifies that integration. Do not claim Kotlin Multiplatform, RevenueCat Ads, Noise, Replit, OneSignal, gaming or Samsung-specific optimization from this Android/iOS codebase.

## Honest form fields

RevenueCat Project ID: `proj5f7132ef`. Public product website must be the verified deployed `/site/` origin, not a RevenueCat dashboard URL. Store URLs, promo code, academic email, OneSignal ID and sponsor identifiers must be real or left blank where optional. For growth and feedback: “No verified launch metrics/customer feedback are available yet” until actual evidence exists. Never turn test purchases into revenue figures.
