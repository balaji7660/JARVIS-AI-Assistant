package com.jarvis.assistant.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPrivacyFilterTest {

    @Test
    fun containsSensitiveData_detectsCardNumbers() {
        assertTrue(ContextPrivacyFilter.containsSensitiveData("My card is 4111 2222 3333 4444"))
        assertTrue(ContextPrivacyFilter.containsSensitiveData("Card: 5500123456789012"))
    }

    @Test
    fun containsSensitiveData_detectsOTPandPasscode() {
        assertTrue(ContextPrivacyFilter.containsSensitiveData("Your OTP is 482910"))
        assertTrue(ContextPrivacyFilter.containsSensitiveData("Security code: 9281"))
        assertTrue(ContextPrivacyFilter.containsSensitiveData("passcode 123456"))
    }

    @Test
    fun containsSensitiveData_detectsPINs() {
        assertTrue(ContextPrivacyFilter.containsSensitiveData("enter pin: 4321"))
        assertTrue(ContextPrivacyFilter.containsSensitiveData("ATM pin 1234"))
    }

    @Test
    fun containsSensitiveData_detectsPasswords() {
        assertTrue(ContextPrivacyFilter.containsSensitiveData("password is secret123!"))
        assertTrue(ContextPrivacyFilter.containsSensitiveData("passwd: mySuperPassword"))
    }

    @Test
    fun containsSensitiveData_detectsAuthTokens() {
        assertTrue(ContextPrivacyFilter.containsSensitiveData("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0"))
        assertTrue(ContextPrivacyFilter.containsSensitiveData("ghp_123456789012345678901234567890"))
    }

    @Test
    fun containsSensitiveData_returnsFalseForNormalQueries() {
        assertFalse(ContextPrivacyFilter.containsSensitiveData("Open YouTube and search for Spring Boot"))
        assertFalse(ContextPrivacyFilter.containsSensitiveData("What time is it in Tokyo?"))
        assertFalse(ContextPrivacyFilter.containsSensitiveData("Click the first result"))
    }

    @Test
    fun sanitize_replacesSensitivePatternsWithRedactedTokens() {
        val sanitizedCard = ContextPrivacyFilter.sanitize("Payment with card 4111 2222 3333 4444")
        assertTrue(sanitizedCard.contains("[REDACTED_CARD]"))
        assertFalse(sanitizedCard.contains("4111"))

        val sanitizedPassword = ContextPrivacyFilter.sanitize("My password is superSecretPassword123")
        assertTrue(sanitizedPassword.contains("[REDACTED_PASSWORD]"))
        assertFalse(sanitizedPassword.contains("superSecretPassword123"))

        val sanitizedOtp = ContextPrivacyFilter.sanitize("Your OTP is 849201")
        assertTrue(sanitizedOtp.contains("[REDACTED_OTP]"))
        assertFalse(sanitizedOtp.contains("849201"))
    }
}
