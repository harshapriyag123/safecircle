import SwiftUI

struct TodayView: View {
    @EnvironmentObject var model: AppModel
    @State private var showingSetup = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Session status").font(.caption.bold())
                        Text(model.session == nil ? "No session" : model.session?.resolved == true ? "Resolved" : "Session active").font(.system(size: 42, weight: .bold))
                        Text(model.session?.state ?? "NORMAL").font(.headline)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding().background(.thinMaterial).clipShape(RoundedRectangle(cornerRadius: 24))

                    if let session = model.session {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(session.mode.replacingOccurrences(of: "_", with: " ")).font(.title2.bold())
                            Text("Expected safe \(session.expectedEndAt.formatted(date: .omitted, time: .shortened))")
                            if let destination = session.destination { Text(destination).foregroundStyle(.secondary) }
                            if !session.resolved {
                            HStack {
                                Button("Check in") { Task { await model.checkIn() } }.buttonStyle(.bordered)
                                Button("+15 min ETA") { Task { await model.extend() } }.buttonStyle(.bordered)
                                Button("I'm safe") { Task { await model.resolve() } }.buttonStyle(.borderedProminent)
                            }.disabled(model.isLoading)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding().background(.thinMaterial).clipShape(RoundedRectangle(cornerRadius: 24))
                    }

                    Button("Start Safety Session") { showingSetup = true }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                    if let message = model.message { Text(message).font(.caption).accessibilityAddTraits(.updatesFrequently) }
                    Button("Refresh server state") { Task { await model.refresh() } }
                }.padding()
            }
            .navigationTitle("SafeCircle")
            .task { await model.refresh() }
            .sheet(isPresented: $showingSetup) { SessionSetupView() }
        }
    }
}

struct SessionSetupView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var mode = "WALK_HOME"
    @State private var minutes = 30
    @State private var destination = ""

    var body: some View {
        NavigationStack {
            Form {
                Picker("Session type", selection: $mode) {
                    Text("Walk Home").tag("WALK_HOME")
                    Text("Rideshare").tag("RIDESHARE")
                    Text("Meet Someone").tag("MEET_SOMEONE")
                    Text("Stay With Me").tag("STAY_WITH_ME")
                }
                Stepper("Expected duration: \(minutes) min", value: $minutes, in: 5...1440, step: 5)
                TextField("Destination or context", text: $destination)
                Section("Escalation") {
                    Text("0m Check-in")
                    Text("+5m Primary Guardian")
                    Text("+10m Backup Guardian")
                    Text("+15m Safety Capsule")
                }
                Button("Start protected session") {
                    Task {
                        if await model.start(mode: mode, minutes: minutes, destination: destination.isEmpty ? nil : destination) { dismiss() }
                    }
                }.disabled(model.isLoading)
                if let message = model.message { Text(message).font(.caption) }
            }.navigationTitle("Safety Session")
        }
    }
}
