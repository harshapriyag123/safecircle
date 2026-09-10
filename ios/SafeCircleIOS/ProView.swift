import SwiftUI

struct ProView: View {
    @EnvironmentObject var model: AppModel
    @State private var email = ""
    @State private var password = ""
    @State private var createAccount = false

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
                    Text("RevenueCat integration can be added to this native iOS target using the same safecircle_pro entitlement.")
                }
            }.navigationTitle("SafeCircle+")
        }
    }
}
