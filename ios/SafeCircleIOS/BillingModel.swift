import Foundation
import RevenueCat

@MainActor
final class BillingModel: ObservableObject {
    static let projectID = "proj5f7132ef" // dashboard identifier, never an SDK key
    static let entitlementID = "safecircle_pro"
    @Published var isPro = false
    @Published var message: String?
    @Published var configured = false

    init() {
        let key = (Bundle.main.object(forInfoDictionaryKey: "RevenueCatPublicSDKKey") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, !key.contains("$(") else {
            message = "Billing is not configured for this build. Basic safety remains available."
            return
        }
        Purchases.configure(withAPIKey: key)
        configured = true
        Task { await refresh() }
    }

    func apply(_ info: CustomerInfo) {
        isPro = info.entitlements.active[Self.entitlementID] != nil
    }

    func refresh() async {
        guard configured else { return }
        do { apply(try await Purchases.shared.customerInfo()) }
        catch { message = error.localizedDescription }
    }

    func identify(_ userID: String?) async {
        guard configured else { return }
        // Drop cached access immediately while account identity changes.
        isPro = false
        do {
            if let userID { apply(try await Purchases.shared.logIn(userID).customerInfo) }
            else if !Purchases.shared.isAnonymous { apply(try await Purchases.shared.logOut()) }
            else { await refresh() }
        } catch { message = error.localizedDescription }
    }

    func restore() async {
        guard configured else { return }
        do {
            apply(try await Purchases.shared.restorePurchases())
            message = isPro ? "SafeCircle+ restored" : "No active purchase found"
        } catch { message = error.localizedDescription }
    }
}
