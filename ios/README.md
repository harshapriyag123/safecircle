# SafeCircle iOS

Native SwiftUI reference client using the same SafeCircle API contract as Android.

## Included

- Five-tab SwiftUI product shell: Today, Automate, Circle, Vault, Pro
- Safety Session setup
- Check-in / resolve API calls
- Per-user register/login API
- Signed Guardian invite creation + ShareLink
- Privacy/Vault surface
- Family/automation product surfaces
- XcodeGen project definition

## Generate the Xcode project

```bash
brew install xcodegen
cd ios
xcodegen generate
open SafeCircleIOS.xcodeproj
```

Set the API base URL at runtime with:

```swift
UserDefaults.standard.set("https://your-api.example.com", forKey: "api_base_url")
```

RevenueCat and RevenueCatUI are pinned through Swift Package Manager. Set the `REVENUECAT_PUBLIC_SDK_KEY` Xcode build setting to the Apple app public SDK key for project `proj5f7132ef`. The project ID is not a key. Pro includes hosted Paywall, Restore Purchases and Customer Center; billing identity follows the signed-in SafeCircle account. Enable the In-App Purchase capability and configure App Store Connect products before sandbox/device testing.

This client is not yet verified on a device or published. Do not submit it as a shipped iOS app until simulator compilation, sandbox purchases, restore, account switching, and store release are evidenced.
