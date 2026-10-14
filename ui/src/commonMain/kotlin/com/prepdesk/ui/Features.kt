package com.prepdesk.ui

/** Release switches for features that are built but not offered yet. */
object Features {
    /**
     * Email sign-in and cloud sync. Off for v1: sign-in codes need a custom SMTP sender in Supabase.
     * AI tools do not depend on it; they use an anonymous guest session.
     */
    const val EMAIL_SIGN_IN = false
}
