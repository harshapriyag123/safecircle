# Build and test evidence

Checks are attached to PR #2: https://github.com/harshapriyag123/safecircle/pull/2.

- Local backend on October 1, 2026: `PYTHONPATH=backend python -m pytest backend/tests -q` — 48 passed, one dependency deprecation warning. Tests include stage routing/deduplication, retries, acceptance vs delivery, signed receipts, lease recovery, uncertain SMS, late check-in grace, ETA/resolution cancellation, consent withdrawal, capsule expiry/privacy, subscriber transfer reconciliation and provider failure recovery. All providers are mocked; no message/purchase was sent.
- Commit `fdd6798d485bde9c4332052105e69131ebffacb5`: Android [run 36898996303](https://github.com/harshapriyag123/safecircle/actions/runs/36898996303) succeeded: wrapper unit tests, lintDebug and assembleDebug, APK/report upload. iOS [run 36898996240](https://github.com/harshapriyag123/safecircle/actions/runs/36898996240) simulator build succeeded. Backend [run 36898996265](https://github.com/harshapriyag123/safecircle/actions/runs/36898996265) succeeded. Website [run 36898996472](https://github.com/harshapriyag123/safecircle/actions/runs/36898996472) succeeded with actual rendered desktop/mobile screenshots, demo interactions, root redirect, privacy and no-token Guardian checks.
- Earlier Android run 36898451318 uploaded APK artifact 11181211395 and lint reports; lint had no blocking errors, 411 warnings (largely string/resources and dependency notices). No lint baseline or error suppression was added.
- `git diff --check` passed locally. Genuine wrapper generated from checksum-verified official Gradle tooling, not a fabricated JAR.

Final privacy/account-control refinements need final-commit CI results below before this draft is marked ready. APK artifacts expire after 30 days and require GitHub sign-in; reports/screenshot artifacts use repository retention.

Local Android task execution was attempted after installing Java/SDK/Gradle but Gradle's Java process could not access its network proxy (connection refused). The same wrapper/configuration succeeded on GitHub-hosted CI. iOS validation uses macOS CI because Xcode is not available locally.

No live RevenueCat/store purchase, production provider delivery, Railway deployment, persistent-volume restart, or public store eligibility has been verified. The supplied screenshot proves project ID `proj5f7132ef`, nothing beyond that.
