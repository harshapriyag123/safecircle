import SwiftUI

struct VaultView: View {
    @State private var privacy = "Precise on escalation"

    var body: some View {
        NavigationStack {
            List {
                Section("Privacy Center") {
                    Picker("Location privacy", selection: $privacy) {
                        Text("Status only").tag("Status only")
                        Text("Approximate").tag("Approximate")
                        Text("Precise on escalation").tag("Precise on escalation")
                    }
                }
                Section("Safety Capsule") {
                    Label("Encrypted locally by the platform", systemImage: "lock.shield.fill")
                    Text("Only pre-authorized fields should be released after the configured escalation threshold.")
                }
                Section("Safety history") {
                    Text("Resolved sessions and shareable Safety Receipts appear here.")
                }
            }.navigationTitle("Safety Vault")
        }
    }
}
