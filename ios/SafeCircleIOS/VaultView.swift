import SwiftUI

struct VaultView: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("location_privacy") private var privacy = "STATUS_ONLY"

    var body: some View {
        NavigationStack {
            List {
                Section("Privacy Center") {
                    Picker("Location privacy", selection: $privacy) {
                        Text("Status only").tag("STATUS_ONLY")
                        Text("Approximate").tag("APPROXIMATE")
                        Text("Precise on escalation").tag("PRECISE_ON_ESCALATION")
                    }
                }
                Section("Safety Capsule") {
                    Label("Android capsule workflow; iOS capsule creation is pending", systemImage: "lock.shield.fill")
                    Text("Only pre-authorized fields should be released after the configured escalation threshold.")
                }
                Section("Safety history") {
                    Text("Extended history and shareable receipts are not yet implemented in this iOS reference client.")
                }
            }.navigationTitle("Safety Vault")
                .onChange(of: privacy) { _, _ in
                    Task {
                        if let session = model.session, let auth = model.auth {
                            do { try await model.api.upsertSession(session, token: auth.accessToken) }
                            catch { model.message = error.localizedDescription }
                        }
                    }
                }
        }
    }
}
