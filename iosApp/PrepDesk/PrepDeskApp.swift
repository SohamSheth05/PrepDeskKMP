import SwiftUI
import ComposeApp

@main
struct PrepDeskApp: App {
    init() {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--verify-secure-session") {
            // A separate test service never overwrites a user's auth record.
            let vault = KeychainCheckKt.keychainVault(service: "com.prepdesk.session.test")
            vault.write(value: "prepdesk-storage-check")
            precondition(vault.read() == "prepdesk-storage-check", "Keychain round trip failed")
            vault.write(value: nil)
            precondition(vault.read() == nil, "Keychain deletion failed")
            let marker = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("prepdesk-keychain-check.txt")
            try? "PASSED".write(to: marker, atomically: true, encoding: .utf8)
        }
        #endif
    }
    // Compose applies the safe-area and keyboard insets itself, so SwiftUI must not inset the view as well.
    var body: some Scene { WindowGroup { PrepDeskView().ignoresSafeArea() } }
}

struct PrepDeskView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
