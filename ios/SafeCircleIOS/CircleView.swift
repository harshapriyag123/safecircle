import SwiftUI

struct CircleView: View {
    @EnvironmentObject var model: AppModel
    @State private var primaryURL: URL?
    @State private var backupURL: URL?
    @State private var primaryPhone = ""
    @State private var backupPhone = ""
    @State private var primaryConsent = false
    @State private var backupConsent = false
    @State private var busy = false

    private func invite(_ role: String) async {
        guard let session = model.session, !session.resolved, let auth = model.auth else { model.message = "Start a signed-in active session first."; return }
        busy = true; defer { busy = false }
        do {
            let result = try await model.api.createGuardianInvite(sessionId: session.id, ownerId: auth.userId, role: role, token: auth.accessToken)
            if role == "primary" { primaryURL = URL(string: result.guardianUrl) } else { backupURL = URL(string: result.guardianUrl) }
            model.message = "Signed link created. This does not imply acknowledgement or delivery."
        } catch { model.message = error.localizedDescription }
    }

    private func saveContacts() async {
        guard let session = model.session, !session.resolved, let auth = model.auth else { model.message = "Start a signed-in active session first."; return }
        let entries = [("primary", primaryPhone, primaryConsent), ("backup", backupPhone, backupConsent)]
        var contacts: [[String: Any]] = []
        for (role, phone, consent) in entries where !phone.isEmpty {
            guard phone.range(of: "^\\+[1-9][0-9]{7,14}$", options: .regularExpression) != nil, consent else { model.message = "Use E.164 phone numbers and obtain each Guardian's consent."; return }
            contacts.append(["role": role, "phone": phone, "consented": consent])
        }
        busy = true; defer { busy = false }
        do {
            model.session = try await model.api.patch(session.id, body: ["guardian_contacts": contacts], token: auth.accessToken)
            model.message = "Contacts saved. Live SMS still requires a configured provider."
        } catch { model.message = error.localizedDescription }
    }

    private func loadContacts() {
        let primary = model.session?.guardianContacts?.first { $0.role == "primary" }
        let backup = model.session?.guardianContacts?.first { $0.role == "backup" }
        primaryPhone = primary?.phone ?? ""; primaryConsent = primary?.consented ?? false
        backupPhone = backup?.phone ?? ""; backupConsent = backup?.consented ?? false
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Guardian links") {
                    Text(model.session?.state ?? "No active session")
                    Button("Create Primary link") { Task { await invite("primary") } }.disabled(busy)
                    if let primaryURL { ShareLink("Share Primary link", item: primaryURL) }
                    Button("Create Backup link") { Task { await invite("backup") } }.disabled(busy)
                    if let backupURL { ShareLink("Share Backup link", item: backupURL) }
                    Text("Links expire and expose only session-authorized fields. Link sharing is separate from SMS delivery.").font(.caption)
                }
                Section("Consenting SMS test contacts") {
                    TextField("Primary +countrycode number", text: $primaryPhone).keyboardType(.phonePad)
                    Toggle("Primary has consented", isOn: $primaryConsent)
                    TextField("Backup +countrycode number", text: $backupPhone).keyboardType(.phonePad)
                    Toggle("Backup has consented", isOn: $backupConsent)
                    Button("Save session contacts") { Task { await saveContacts() } }.disabled(busy)
                    Text("Blank numbers remove the corresponding contact. +5m Primary; +10m Backup; +15m both.").font(.caption)
                }
                if let message = model.message { Section { Text(message) } }
            }.navigationTitle("My Circle")
                .onAppear { loadContacts() }
                .onChange(of: model.auth?.userId) { _, _ in primaryURL = nil; backupURL = nil; primaryPhone = ""; backupPhone = ""; primaryConsent = false; backupConsent = false }
                .onChange(of: model.session?.id) { _, _ in primaryURL = nil; backupURL = nil; loadContacts() }
                .onChange(of: model.session?.resolved) { _, resolved in if resolved == true { primaryURL = nil; backupURL = nil } }
        }
    }
}
