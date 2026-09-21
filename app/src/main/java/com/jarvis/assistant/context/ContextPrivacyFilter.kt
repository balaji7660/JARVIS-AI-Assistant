package com.jarvis.assistant.context

import java.util.regex.Pattern

/**
 * Sanitizes and strips sensitive information (passwords, OTPs, PINs, tokens, card numbers)
 * before any textual data is stored inside [ConversationContext].
 *
 * Reuses established security constraints and regex patterns to ensure conversational context
 * never leaks credentials or banking details.
 */
object ContextPrivacyFilter {

    private val CARD_NUMBER_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b")
    private val OTP_PATTERN = Pattern.compile("(?i)\\b(otp|one[- ]time password|verification code|security code|passcode)\\b.*?(?:is|:)?\\s*([0-9]{4,8})")
    private val STANDALONE_OTP_PATTERN = Pattern.compile("(?i)\\b(?:otp|passcode)\\s*[:=]?\\s*([0-9]{4,8})\\b")
    private val PIN_PATTERN = Pattern.compile("(?i)\\b(enter pin|atm pin|secret pin|pin)\\s*[:=]?\\s*([0-9]{4,8})\\b")
    private val PASSWORD_PATTERN = Pattern.compile("(?i)\\b(password|passwd|pwd)\\s*(?:is|[:=])\\s*(\\S+)")
    private val TOKEN_PATTERN = Pattern.compile("(?i)\\b(bearer\\s+[A-Za-z0-9\\-_=.]+)|(ghp_[A-Za-z0-9]{20,})|(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})")

    /**
     * Inspects text for sensitive patterns.
     * @return true if sensitive credentials or tokens are present.
     */
    fun containsSensitiveData(text: String): Boolean {
        if (text.isBlank()) return false
        return CARD_NUMBER_PATTERN.matcher(text).find() ||
                OTP_PATTERN.matcher(text).find() ||
                STANDALONE_OTP_PATTERN.matcher(text).find() ||
                PIN_PATTERN.matcher(text).find() ||
                PASSWORD_PATTERN.matcher(text).find() ||
                TOKEN_PATTERN.matcher(text).find()
    }

    /**
     * Sanitizes input text by redacting sensitive data patterns.
     * Replaces sensitive matches with safe placeholders like [REDACTED_CREDENTIAL].
     */
    fun sanitize(text: String): String {
        if (text.isBlank()) return text

        var sanitized = text
        sanitized = CARD_NUMBER_PATTERN.matcher(sanitized).replaceAll("[REDACTED_CARD]")
        sanitized = OTP_PATTERN.matcher(sanitized).replaceAll("[REDACTED_OTP]")
        sanitized = STANDALONE_OTP_PATTERN.matcher(sanitized).replaceAll("[REDACTED_OTP]")
        sanitized = PIN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PIN]")
        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PASSWORD]")
        sanitized = TOKEN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]")

        return sanitized
    }
}
