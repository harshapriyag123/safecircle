import SwiftUI
import RevenueCatUI

struct ProView: View {
    @EnvironmentObject var model: AppModel
    @State private var email = ""
    @State private var password = ""
    @State private var createAccount = false
    @State private var showPaywall = false
    @State private var showCustomerCenter = false

    var body: some View {
        NavigationStack {
            Form {
                Section("SafeCircle+") {
                    Text("Advanced automations, multiple Guardians, Family Circles, SafePhrase workflows, and richer privacy controls.")
                }
                Section("Account") {
                    if let auth = model.auth {
                        Text(auth.userId).font(.caption)
                        Button("Sign out") { model.auth = nil }
                    } else {
                        TextField("Email", text: $email).textInputAutocapitalization(.never)
                        SecureField("Password", text: $password)
                        Toggle("Create new account", isOn: $createAccount)
                        Button(createAccount ? "Create account" : "Sign in") {
                            Task {
                                model.isLoading = true
                                defer { model.isLoading = false }
                                do {
                                    model.auth = try await (createAccount
                                        ? model.api.register(email: email, password: password)
                                        : model.api.login(email: email, password: password))
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
            }.navigationTitle("SafeCircle+")
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
