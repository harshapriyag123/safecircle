import SwiftUI

struct VaultView: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("location_privacy") private var privacy = "STATUS_ONLY"
    @State private var instruction = ""
    @State private var busy = false

    private func save(_ capsule: Bool) async {
        guard let session = model.session, !session.resolved, let auth = model.auth else { model.message = "Start a signed-in active session first."; return }
        guard instruction.count <= 1000 else { model.message = "Limit the instruction to 1,000 characters."; return }
        busy = true; defer { busy = false }
        var body: [String: Any] = ["privacy_mode": privacy]
        if capsule { body["capsule"] = instruction.isEmpty ? NSNull() : ["instruction": instruction, "expiresAt": Int(Date().addingTimeInterval(3600).timeIntervalSince1970 * 1000)] as Any }
        do {
            model.session = try await model.api.patch(session.id, body: body, token: auth.accessToken)
            model.message = "Privacy and capsule changes confirmed by the server."
        } catch { model.message = error.localizedDescription }
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Privacy Center") {
                    Picker("Location privacy", selection: $privacy) {
                        Text("Status only").tag("STATUS_ONLY")
                        Text("Approximate").tag("APPROXIMATE")
                        Text("Precise on escalation").tag("PRECISE_ON_ESCALATION")
                    }
                    Button("Apply to current session") { Task { await save(false) } }.disabled(busy)
                    Text("This client does not collect precise location. Choosing a mode does not fabricate coordinates.").font(.caption)
                }
                Section("Safety Capsule") {
                    TextField("Guardian instruction", text: $instruction, axis: .vertical)
                    Button("Save one-hour capsule") { Task { await save(true) } }.disabled(busy)
                    Text("Encrypted on the server. Shared after the authorized escalation stage. Blank instruction clears the capsule. Raw medical/contact details are withheld from Guardian responses.").font(.caption)
                }
                Section("Safety history — latest 100") {
                    Button("Refresh history") { Task { await model.refresh() } }
                    if model.history.isEmpty { Text("No resolved server sessions yet.") }
                    ForEach(model.history) { entry in
                        VStack(alignment: .leading) {
                            Text(entry.mode.replacingOccurrences(of: "_", with: " "))
                            Text("Resolved \(entry.resolvedAt.formatted())").font(.caption)
                        }
                    }
                }
                if let message = model.message { Section { Text(message) } }
            }.navigationTitle("Safety Vault")
                .onAppear { instruction = model.session?.capsule?.instruction ?? ""; privacy = model.session?.privacyMode ?? privacy }
                .onChange(of: model.auth?.userId) { _, _ in instruction = "" }
        }
    }
}
