import SwiftUI

struct AutomateView: View {
    @State private var safePhrase = "Did the package arrive?"
    @State private var rules = ["Progressive overdue escalation", "Low battery Guardian check"]

    var body: some View {
        NavigationStack {
            List {
                Section("IF → THEN rules") {
                    ForEach(rules, id: \.self) { Text("⚡ " + $0) }
                    Button("Create automation") { rules.append("Missed check-in escalation") }
                }
                Section("SafePhrase") {
                    TextField("Yellow phrase", text: $safePhrase)
                    Text("Red phrase can trigger a silent escalation workflow.")
                }
                Section("Recurring commute") {
                    Button("Schedule usual commute") {}
                }
            }.navigationTitle("Automate")
        }
    }
}
