@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.prepdesk.data

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.CoreFoundation.*

actual fun platformHttpClient() = HttpClient(Darwin) { followRedirects = false; install(HttpTimeout) }
private inline fun <T> withDictionary(value: Map<Any?, Any?>, action: (CFDictionaryRef) -> T): T {
    val ref = CFBridgingRetain(value) as CFDictionaryRef
    return try { action(ref) } finally { CFRelease(ref) }
}
// Convert CFString constants to NSString-backed Kotlin strings before NSDictionary bridging.
// Raw CPointer values in a Kotlin Map become boxed pointers, which Security rejects.
private fun securityString(value: CFStringRef?): String = CFBridgingRelease(CFRetain(value)).toString()
fun iosSessionVault(): SessionVault = keychainVault("com.prepdesk.session")

fun keychainVault(service: String): SessionVault = object : SessionVault {
    private fun query(): MutableMap<Any?, Any?> = mutableMapOf(securityString(kSecClass) to securityString(kSecClassGenericPassword), securityString(kSecAttrService) to service, securityString(kSecAttrAccount) to "auth")
    override fun read(): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val options = query().apply { put(securityString(kSecReturnData), true); put(securityString(kSecMatchLimit), securityString(kSecMatchLimitOne)) }
        val status = withDictionary(options) { SecItemCopyMatching(it, result.ptr) }
        if (status == errSecItemNotFound) return@memScoped null
        check(status == errSecSuccess) { "Could not read your secure sign-in session ($status)" }
        val data = CFBridgingRelease(result.value) as NSData
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }
    override fun write(value: String?) {
        val options = query()
        if (value == null) {
            val status = withDictionary(options) { SecItemDelete(it) }
            check(status == errSecSuccess || status == errSecItemNotFound); return
        }
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val attributes = mapOf<Any?, Any?>(securityString(kSecValueData) to data, securityString(kSecAttrAccessible) to securityString(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly))
        val status = withDictionary(options) { queryRef -> withDictionary(attributes) { SecItemUpdate(queryRef, it) } }
        if (status == errSecItemNotFound) {
            options.putAll(attributes)
            check(withDictionary(options) { SecItemAdd(it, null) } == errSecSuccess) { "Could not save your secure sign-in session" }
        } else check(status == errSecSuccess) { "Could not update your secure sign-in session" }
    }
}
