import Foundation
import SwiftUI
import Shared

/// Keep the typed reference; SwiftUI's locale environment chooses presentation.
struct LocalizedHeading: View {
    let message: UiText
    let resolver: AppleMessagesResolver
    @Environment(\.locale) private var locale

    var body: some View {
        Text(verbatim: resolver.resolve(text: message, requestedLocale: locale.identifier))
            .font(.system(size: 20, design: .monospaced))
            .fixedSize()
    }
}
