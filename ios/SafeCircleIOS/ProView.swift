import SwiftUI
import RevenueCatUI

struct ProView: View {
    @EnvironmentObject var model: AppModel
    @State private var email = ""
    @State private var password = ""
    @State private var createAccount = false
    @State private var showDelete = false
    @State private var deletePassword = ""
    @State private var showPaywall = false
    @State private var showCustomerCenter = false

    var body: some View {
        NavigationStack {
            Form {
                Section("SafeCircle+") {
                    Text("Optional Android convenience tools use SafeCircle+. Core sessions, both Guardians and privacy remain free. The iOS client currently provides billing and basic sessions; advanced feature parity is pending.")
                }
                Section("Account") {
                    if let auth = model.auth {
                        Text(auth.userId).font(.caption)
                        Button("Sign out") { Task { do { try await model.api.logout(token: auth.accessToken); model.auth = nil; model.session = nil; model.history = []; password = ""; model.message = "Signed out; local account data cleared." } catch { model.message = error.localizedDescription } } }
                        Button("Delete account", role: .destructive) { showDelete = true }
                    } else {
                        TextField("Email", text: $email).textInputAutocapitalization(.never)
                        SecureField("Password", text: $password)
                        Toggle("Create new account", isOn: $createAccount)
                        Button(createAccount ? "Create account" : "Sign in") {
                            Task {
                                model.isLoading = true
                                defer { model.isLoading = false }
                                do {
                                    model.session = nil; model.history = []
                                    model.auth = try await (createAccount
                                        ? model.api.register(email: email, password: password)
                                        : model.api.login(email: email, password: password))
                                    password = ""
                                    await model.refresh()
                                } catch {
                                    model.message = error.localizedDescription
                                }
                            }
                        }
                    }
                }
                Section("Subscription") {
                    BillingControls(billing: model.billing, showPaywall: $showPaywall,
                                    showCustomerCenter: $showCustomerCenter)
                }
                if let message = model.message { Section { Text(message) } }
            }.navigationTitle("SafeCircle+")
                .alert("Delete SafeCircle account?", isPresented: $showDelete) {
                    SecureField("Current password", text: $deletePassword)
                    Button("Delete account", role: .destructive) {
                        Task {
                            guard let auth = model.auth else { return }
                            do {
                                try await model.api.deleteAccount(email: email, password: deletePassword, token: auth.accessToken)
                                model.auth = nil; model.session = nil; model.history = []; password = ""; deletePassword = ""
                                model.message = "Account deleted. Store subscriptions must be cancelled separately."
                            } catch { model.message = error.localizedDescription }
                        }
                    }
                    Button("Cancel", role: .cancel) { deletePassword = "" }
                } message: { Text("Deletes SafeCircle data and pending monitoring. This does not cancel a store subscription or recall sent alerts.") }
                .sheet(isPresented: $showPaywall, onDismiss: { Task { await model.billing.refresh() } }) {
                    PaywallView(displayCloseButton: true)
                }
                .sheet(isPresented: $showCustomerCenter, onDismiss: { Task { await model.billing.refresh() } }) {
                    CustomerCenterView()
                }
        }
    }
}


private struct BillingControls: View {
    @ObservedObject var billing: BillingModel
    @Binding var showPaywall: Bool
    @Binding var showCustomerCenter: Bool
    var body: some View {
        Text(billing.isPro ? "SafeCircle+ active" : "Free plan")
        if let message = billing.message { Text(message).font(.caption) }
        Button("View SafeCircle+ plans") { showPaywall = true }.disabled(!billing.configured)
        Button("Restore purchases") { Task { await billing.restore() } }.disabled(!billing.configured)
        Button("Manage subscription") { showCustomerCenter = true }.disabled(!billing.configured)
    }
}
