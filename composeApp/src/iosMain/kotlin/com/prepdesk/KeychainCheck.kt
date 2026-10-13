package com.prepdesk

import com.prepdesk.data.SessionVault

/** Exposes the Keychain store to the iOS host's debug self-check; the app itself receives it through Koin. */
fun keychainVault(service: String): SessionVault = com.prepdesk.data.keychainVault(service)
