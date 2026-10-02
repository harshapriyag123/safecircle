import SwiftUI
import UserNotifications

struct AutomateView: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("safe_phrase") private var safePhrase = "Did the package arrive?"
    @State private var phraseTry = ""
    @State private var minutes = 15
    @State private var message = ""

    private func reminder() async {
        do {
            let center = UNUserNotificationCenter.current()
            guard try await center.requestAuthorization(options: [.alert, .sound]) else { message = "Notification permission denied. No reminder scheduled."; return }
            let content = UNMutableNotificationContent()
            content.title = "SafeCircle check-in reminder"
            content.body = "Open SafeCircle to check in. This local reminder does not alert a Guardian."
            let request = UNNotificationRequest(identifier: "safecircle.local-checkin", content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: Double(minutes * 60), repeats: false))
            try await center.add(request)
            message = "One local reminder scheduled in \(minutes) minutes. System delivery is not guaranteed."
        } catch { message = "Reminder scheduling failed: \(error.localizedDescription)" }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Server safety workflow") {
                    Text("Active sessions use durable +5/+10/+15-minute Guardian jobs on the backend. A configured external scheduler and providers are required.")
                    Text("Custom background automation rules are not available in this iOS client.").font(.caption)
                }
                Section("Device-private SafePhrase") {
                    TextField("Private phrase", text: $safePhrase)
                    TextField("Type phrase", text: $phraseTry)
                    Button("Private test") { message = phraseTry.lowercased() == safePhrase.lowercased() && !safePhrase.isEmpty ? "Phrase matched; no alert sent." : "Phrase did not match." }
                    Button("Record session concern") {
                        guard !safePhrase.isEmpty, phraseTry.lowercased() == safePhrase.lowercased() else { message = "Phrase did not match."; return }
                        Task { await model.concern(); message = model.message ?? "" }
                    }.disabled(model.isLoading)
                }
                Section("Local check-in reminder") {
                    Stepper("In \(minutes) minutes", value: $minutes, in: 5...120, step: 5)
                    Button("Schedule reminder") { Task { await reminder() } }
                    Button("Cancel reminder") { UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["safecircle.local-checkin"]); message = "Pending local reminder cancelled." }
                    Text("This is an exit/check-in aid, separate from server monitoring or recurring commute detection.").font(.caption)
                }
                if !message.isEmpty { Section { Text(message) } }
            }.navigationTitle("Automate")
                .onChange(of: model.auth?.userId) { _, _ in safePhrase = ""; phraseTry = ""; message = ""; UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["safecircle.local-checkin"]) }
        }
    }
}
