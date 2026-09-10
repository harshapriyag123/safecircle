import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            TodayView()
                .tabItem { Label("Today", systemImage: "shield.fill") }
            AutomateView()
                .tabItem { Label("Automate", systemImage: "bolt.fill") }
            CircleView()
                .tabItem { Label("Circle", systemImage: "person.3.fill") }
            VaultView()
                .tabItem { Label("Vault", systemImage: "lock.fill") }
            ProView()
                .tabItem { Label("Pro", systemImage: "star.fill") }
        }
        .tint(Color(red: 0.19, green: 0.37, blue: 0.35))
    }
}
