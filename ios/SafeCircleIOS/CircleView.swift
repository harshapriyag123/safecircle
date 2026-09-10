import SwiftUI

struct CircleView: View {
    @EnvironmentObject var model: AppModel
    @State private var inviteURL: URL?

    var body: some View {
        NavigationStack {
            List {
                Section("Guardian Live") {
                    Text(model.session?.mode.replacingOccurrences(of: "_", with: " ") ?? "No active session")
                    Text(model.session?.state ?? "Start a Safety Session first")
                }
                Section("Guardians") {
                    Label("Primary Guardian", systemImage: "star.fill")
                    Label("Backup Guardian", systemImage: "person.fill")
                    Button("Create Guardian invite") {
                        Task {
                            guard let session = model.session, let auth = model.auth else { return }
                            if let response = try? await model.api.createGuardianInvite(
                                sessionId: session.id,
                                ownerId: auth.userId,
                                token: auth.accessToken
                            ) {
                                inviteURL = URL(string: response.guardianUrl)
                            }
                        }
                    }
                    if let inviteURL { ShareLink(item: inviteURL) { Text("Share Guardian link") } }
                }
                Section("Family Circle") {
                    Text("Family groups remain session-scoped; membership does not imply permanent tracking.")
                }
            }.navigationTitle("My Circle")
        }
    }
}
