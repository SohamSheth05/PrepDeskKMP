package com.prepdesk.data

import com.prepdesk.domain.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Secure storage for the sign-in session; each platform supplies one through its Koin module. */
interface SessionVault { fun read(): String?; fun write(value: String?); }
expect fun platformHttpClient(): HttpClient

@Serializable data class ContentPack(val format: Int = 1, val questions: List<Q>, val sources: List<Source>, val resources: List<ResourceRecord> = emptyList())
@Serializable data class ResourceRecord(val sourceId: String, val areas: List<String>, val levels: List<Int>, val note: String) {
    fun resource() = StudyResource(sourceId, areas, levels, note)
}
class ServiceException(val status: Int, message: String) : Exception(message)

class SupabaseApi(val baseUrl: String, private val publicKey: String, private val client: HttpClient) {
    val configured = baseUrl.matches(Regex("https://[a-zA-Z0-9.-]+")) && publicKey.isNotBlank()
    private suspend fun request(path: String, method: HttpMethod, body: String? = null, token: String? = null): String {
        check(configured) { "Online services are not configured for this build" }
        val response = client.request(baseUrl + path) {
            this.method = method
            header("apikey", publicKey)
            if (token != null) bearerAuth(token)
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = 65_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 65_000 }
            if (body != null) setBody(body)
        }
        if (response.status.value !in 200..299) {
            val message = when (response.status.value) {
                401 -> "Your session expired. Sign in again."
                409 -> "Cloud progress changed on another device. Resolve the conflict before syncing."
                429 -> "Too many requests. Please wait and try again."
                503 -> "This service is currently unavailable. Your local work is saved."
                else -> "The service could not complete the request (${response.status.value}). Please try again."
            }
            throw ServiceException(response.status.value, message)
        }
        val text = response.bodyAsText()
        require(text.encodeToByteArray().size <= 4_000_000) { "Service response exceeds the supported size" }
        return text
    }
    suspend fun requestCode(email: String) { request("/auth/v1/otp", HttpMethod.Post, buildJsonObject { put("email", email); put("create_user", true) }.toString()) }
    suspend fun verifyCode(email: String, code: String) = CONTENT_JSON.decodeFromString<AuthSession>(request("/auth/v1/verify", HttpMethod.Post,
        buildJsonObject { put("email", email); put("token", code); put("type", "email") }.toString())).withExpiry()
    suspend fun signInAnonymously() = CONTENT_JSON.decodeFromString<AuthSession>(request("/auth/v1/signup", HttpMethod.Post, "{}")).withExpiry()
    suspend fun refresh(session: AuthSession) = CONTENT_JSON.decodeFromString<AuthSession>(request("/auth/v1/token?grant_type=refresh_token", HttpMethod.Post,
        buildJsonObject { put("refresh_token", session.refresh_token) }.toString())).withExpiry()
    suspend fun logout(token: String) { request("/auth/v1/logout?scope=local", HttpMethod.Post, "{}", token) }
    suspend fun cloud(token: String): CloudProgress? {
        val rows = CONTENT_JSON.decodeFromString<List<CloudProgress>>(request("/rest/v1/progress?select=revision,document", HttpMethod.Get, token = token))
        return rows.singleOrNull()
    }
    suspend fun push(token: String, expected: Long, backup: ProgressBackup): Long = CONTENT_JSON.parseToJsonElement(request("/rest/v1/rpc/save_progress", HttpMethod.Post,
        buildJsonObject { put("expected_revision", expected); put("new_document", CONTENT_JSON.encodeToJsonElement(backup)) }.toString(), token)).jsonPrimitive.long
    suspend fun content(): ContentPack = CONTENT_JSON.decodeFromString(request("/rest/v1/content_releases?select=document&active=eq.true&order=created_at.desc&limit=1", HttpMethod.Get).let {
        val rows = CONTENT_JSON.parseToJsonElement(it).jsonArray
        check(rows.isNotEmpty()) { "No content update is available" }
        rows.first().jsonObject.getValue("document").toString()
    })
    suspend fun function(token: String, action: String, body: JsonObject = buildJsonObject {}): String = request("/functions/v1/prepdesk", HttpMethod.Post,
        buildJsonObject { put("action", action); put("input", body) }.toString(), token)
    suspend fun capabilities(token: String) = CONTENT_JSON.decodeFromString<ServiceCapabilities>(function(token, "capabilities"))
    suspend fun deleteAccount(token: String) { function(token, "delete-account") }
    fun close() = client.close()
}

fun validateContent(pack: ContentPack) {
    require(pack.format == 1 && pack.questions.size in 1..10_000 && pack.sources.size in 1..2_000)
    val ids = pack.sources.map { it.id }.toSet()
    require(ids.size == pack.sources.size && pack.questions.map { it.id }.distinct().size == pack.questions.size)
    require(pack.sources.all { it.id.isNotBlank() && it.name.isNotBlank() && Url(it.url).protocol == URLProtocol.HTTPS && Url(it.url).user.isNullOrEmpty() })
    require(pack.questions.all { q -> q.id.isNotBlank() && q.q.isNotBlank() && q.a.isNotBlank() && q.level in 1..4 && q.version > 0 && q.sourceIds.all(ids::contains) && q.q.length <= 20_000 && q.a.length <= 100_000 })
    require(pack.questions.all { q -> q.practical?.let { p -> validPractical(p) } ?: true }) { "Invalid practical exercise" }
    val areas = pack.questions.map { it.area }.toSet()
    require(pack.resources.all { it.sourceId in ids && it.areas.isNotEmpty() && it.areas.all(areas::contains) && it.levels.isNotEmpty() && it.levels.all { level -> level in 1..4 } })
}
