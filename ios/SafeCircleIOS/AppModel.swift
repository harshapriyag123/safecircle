import Foundation
import UIKit

@MainActor
final class AppModel: ObservableObject {
    @Published var session: SafetySession?
    let billing = BillingModel()
    @Published var auth: AuthState? {
        didSet { Task { await billing.identify(auth?.userId) } }
    }
    @Published var history: [HistoryEntry] = []
    @Published var message: String?
    @Published var isLoading = false

    let api = SafeCircleAPI()

    init() {
        UIDevice.current.isBatteryMonitoringEnabled = true
    }

    private func currentBatteryPercent() -> Int? {
        UIDevice.current.isBatteryMonitoringEnabled = true
        let level = UIDevice.current.batteryLevel
        guard level >= 0 else { return nil }
        return Int((level * 100).rounded())
    }

    func start(mode: String, minutes: Int, destination: String?) async -> Bool {
        guard !isLoading else { return false }
        guard let auth else { message = "Sign in before starting a monitored session."; return false }
        guard session == nil || session?.resolved == true else { message = "Resolve the existing session first."; return false }
        guard (5...1440).contains(minutes), (destination?.count ?? 0) <= 256 else { message = "Check the duration and destination."; return false }
        isLoading = true; defer { isLoading = false }
        let now = Date()
        let next = SafetySession(id: UUID().uuidString, ownerId: auth.userId, mode: mode,
            destination: destination, startedAt: now, expectedEndAt: now.addingTimeInterval(Double(minutes * 60)),
            lastCheckInAt: now, state: "NORMAL", batteryPercent: currentBatteryPercent(), resolved: false)
        do {
            try await api.upsertSession(next, token: auth.accessToken)
            self.session = try await api.session(next.id, token: auth.accessToken)
            message = "Session confirmed by the server. Delivery still requires configured providers."
            return true
        } catch { message = error.localizedDescription; return false }
    }

    func refresh() async {
        guard let auth else { return }
        do {
            session = try await api.activeSession(token: auth.accessToken)
            history = try await api.history(token: auth.accessToken)
        } catch { message = error.localizedDescription }
    }

    func extend() async {
        guard let session, let auth, !session.resolved, !isLoading else { return }
        isLoading = true; defer { isLoading = false }
        do {
            self.session = try await api.patch(session.id, body: ["expected_end_at": Int(max(session.expectedEndAt, Date()).addingTimeInterval(900).timeIntervalSince1970 * 1000)], token: auth.accessToken)
            message = "ETA extension confirmed. Obsolete pending alerts cancelled."
        } catch { message = error.localizedDescription }
    }

    func concern() async {
        guard let session, let auth, !session.resolved, !isLoading else { message = "Start a signed-in active session first."; return }
        isLoading = true; defer { isLoading = false }
        do {
            self.session = try await api.patch(session.id, body: ["state": "CONCERN"], token: auth.accessToken)
            message = "Concern recorded. This does not confirm notification delivery."
        } catch { message = error.localizedDescription }
    }

    func checkIn() async {
        guard let session, let auth, !session.resolved, !isLoading else { return }
        isLoading = true; defer { isLoading = false }
        do {
            try await api.checkIn(session.id, token: auth.accessToken)
            self.session = try await api.session(session.id, token: auth.accessToken)
            message = "Check-in confirmed with the server's current ETA."
        } catch { message = error.localizedDescription }
    }

    func refreshBatteryAndSync() async {
        guard let session, let auth, !session.resolved else { return }
        do {
            self.session = try await api.patch(session.id, body: ["battery_percent": currentBatteryPercent() as Any? ?? NSNull()], token: auth.accessToken)
        } catch { message = error.localizedDescription }
    }

    func resolve() async {
        guard let session, let auth, !session.resolved, !isLoading else { return }
        isLoading = true; defer { isLoading = false }
        do {
            try await api.resolve(session.id, token: auth.accessToken)
            self.session = try await api.session(session.id, token: auth.accessToken)
            history = try await api.history(token: auth.accessToken)
            message = "Server confirmed safe; pending alerts cancelled."
        } catch { message = error.localizedDescription }
    }
}
