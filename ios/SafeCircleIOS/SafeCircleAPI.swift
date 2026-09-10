import Foundation

struct SafeCircleAPI {
    var baseURL: URL {
        if let raw = UserDefaults.standard.string(forKey: "api_base_url"),
           let url = URL(string: raw) { return url }
        return URL(string: "http://127.0.0.1:8080")!
    }

    func register(email: String, password: String) async throws -> AuthState {
        try await auth(path: "/v1/auth/register", email: email, password: password)
    }

    func login(email: String, password: String) async throws -> AuthState {
        try await auth(path: "/v1/auth/login", email: email, password: password)
    }

    private func auth(path: String, email: String, password: String) async throws -> AuthState {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["email": email, "password": password])
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response, data: data)
        return try JSONDecoder().decode(AuthState.self, from: data)
    }

    func upsertSession(_ session: SafetySession, token: String) async throws {
        var body: [String: Any] = [
            "id": session.id,
            "owner_id": session.ownerId,
            "mode": session.mode,
            "started_at": Int(session.startedAt.timeIntervalSince1970 * 1000),
            "expected_end_at": Int(session.expectedEndAt.timeIntervalSince1970 * 1000),
            "last_check_in_at": Int(session.lastCheckInAt.timeIntervalSince1970 * 1000),
            "state": session.state,
            "privacy_mode": "PRECISE_ON_ESCALATION",
            "resolved": session.resolved
        ]
        if let destination = session.destination { body["destination"] = destination }
        try await post(path: "/v1/sessions", body: body, token: token)
    }

    func checkIn(_ sessionId: String, token: String) async throws {
        try await post(path: "/v1/sessions/(sessionId)/check-in", body: [:], token: token)
    }

    func resolve(_ sessionId: String, token: String) async throws {
        try await post(path: "/v1/sessions/(sessionId)/resolve", body: [:], token: token)
    }

    func createGuardianInvite(sessionId: String, ownerId: String, token: String) async throws -> GuardianInviteResponse {
        var request = URLRequest(url: baseURL.appending(path: "/v1/guardian-invites"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer (token)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "session_id": sessionId,
            "owner_id": ownerId,
            "role": "guardian",
            "ttl_minutes": 1440
        ])
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response, data: data)
        return try JSONDecoder().decode(GuardianInviteResponse.self, from: data)
    }

    private func post(path: String, body: [String: Any], token: String) async throws {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer (token)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response, data: data)
    }

    private func validate(_ response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let text = String(data: data, encoding: .utf8) ?? "Request failed"
            throw NSError(domain: "SafeCircleAPI", code: 1, userInfo: [NSLocalizedDescriptionKey: text])
        }
    }
}
