package com.prepdesk.domain

import kotlinx.serialization.Serializable

// Models shared by the repository contracts and their online implementations.

/** The question bank, its sources, and curated reading. */
data class Library(val questions: List<Q> = emptyList(), val sources: List<Source> = emptyList(), val resources: List<StudyResource> = emptyList())

/** [is_anonymous] marks a guest session: it unlocks AI tools, while email sign-in adds cloud sync. */
@Serializable data class SignedInUser(val id: String, val email: String = "", val is_anonymous: Boolean = false)
@Serializable data class AuthSession(val access_token: String, val refresh_token: String, val expires_at: Long = 0, val expires_in: Long = 3600, val user: SignedInUser) {
    fun withExpiry() = if (expires_at > 0) this else copy(expires_at = nowMs() / 1000 + expires_in)
}
@Serializable data class CloudProgress(val revision: Long, val document: ProgressBackup)
@Serializable data class CodeResult(val token: String, val status: Int, val description: String, val stdout: String = "", val stderr: String = "", val compileOutput: String = "")
@Serializable data class CodeLanguage(val id: Int, val name: String)
@Serializable data class ServiceCapabilities(val assessment: Boolean = false, val transcription: Boolean = false, val code: Boolean = false, val tutor: Boolean = false)

/** Where learners find the privacy policy and ask for help; blank values are hidden. */
data class SupportContact(val privacyUrl: String = "", val supportEmail: String = "")
