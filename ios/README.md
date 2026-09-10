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

This reference client intentionally keeps RevenueCat iOS as the next store-specific wiring step; the server and product architecture use the same `safecircle_pro` entitlement.
