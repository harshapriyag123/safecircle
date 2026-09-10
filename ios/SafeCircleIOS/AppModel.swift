import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published var session: SafetySession?
    @Published var auth: AuthState?
    @Published var message: String?
    @Published var isLoading = false

    let api = SafeCircleAPI()

    func start(mode: String, minutes: Int, destination: String?) async {
        let now = Date()
        let session = SafetySession(
            id: UUID().uuidString,
            ownerId: auth?.userId ?? "local-ios",
            mode: mode,
            destination: destination,
            startedAt: now,
            expectedEndAt: now.addingTimeInterval(Double(minutes * 60)),
            lastCheckInAt: now,
            state: "NORMAL",
            batteryPercent: nil,
            resolved: false
        )
        self.session = session
        guard let auth else { return }
        do { try await api.upsertSession(session, token: auth.accessToken) }
        catch { message = error.localizedDescription }
    }

    func checkIn() async {
        guard let session, let auth else { return }
        do {
            try await api.checkIn(session.id, token: auth.accessToken)
            self.session?.lastCheckInAt = Date()
            self.session?.state = "NORMAL"
        } catch { message = error.localizedDescription }
    }

    func resolve() async {
        guard let session, let auth else { return }
        do {
            try await api.resolve(session.id, token: auth.accessToken)
            self.session?.resolved = true
            self.session?.state = "RESOLVED"
        } catch { message = error.localizedDescription }
    }
}
