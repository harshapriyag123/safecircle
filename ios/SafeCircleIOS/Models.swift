import Foundation

struct AuthState: Codable {
    let userId: String
    let accessToken: String
    let tokenType: String

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case accessToken = "access_token"
        case tokenType = "token_type"
    }
}

struct SafetySession: Identifiable, Codable {
    var id: String
    var ownerId: String
    var mode: String
    var destination: String?
    var startedAt: Date
    var expectedEndAt: Date
    var lastCheckInAt: Date
    var state: String
    var batteryPercent: Int?
    var resolved: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case ownerId = "owner_id"
        case mode, destination
        case startedAt = "started_at"
        case expectedEndAt = "expected_end_at"
        case lastCheckInAt = "last_check_in_at"
        case state
        case batteryPercent = "battery_percent"
        case resolved
    }
}

struct GuardianInviteResponse: Codable {
    let inviteId: String
    let guardianToken: String
    let expiresAt: Int64
    let guardianUrl: String

    enum CodingKeys: String, CodingKey {
        case inviteId = "invite_id"
        case guardianToken = "guardian_token"
        case expiresAt = "expires_at"
        case guardianUrl = "guardian_url"
    }
}
