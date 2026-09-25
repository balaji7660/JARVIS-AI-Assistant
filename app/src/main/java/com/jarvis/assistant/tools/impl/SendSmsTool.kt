package com.jarvis.assistant.tools.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult

/**
 * Tool: send_sms
 * Resolves contacts locally and sends an SMS message via official Android APIs.
 * Requires user confirmation prior to transmission.
 * Privacy-first: Contacts and messages are processed 100% locally on device.
 */
class SendSmsTool(
    private val context: Context? = null,
    private val contactLookupOverride: ((String) -> List<CallContactTool.ContactInfo>)? = null,
    private val smsActionOverride: ((String, String) -> Boolean)? = null
) : JarvisTool {

    companion object {
        private const val TAG = "SendSmsTool"
    }

    override val name: String = "send_sms"
    override val description: String = "Sends an SMS text message to a contact or phone number with explicit user confirmation."
    override val riskLevel: RiskLevel = RiskLevel.HIGH

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val contactName = (arguments["contactName"] as? String)?.trim()
        val directPhone = (arguments["phoneNumber"] as? String)?.trim()
        val message = (arguments["message"] as? String)?.trim()
            ?: (arguments["text"] as? String)?.trim()
            ?: (arguments["body"] as? String)?.trim()

        if (message.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "What message would you like me to send, boss?",
                data = mapOf("directResponse" to true)
            )
        }

        if (contactName.isNullOrBlank() && directPhone.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "Who should I send the message to, boss?",
                data = mapOf("directResponse" to true)
            )
        }

        // 1. Resolve recipient number locally
        var targetName = contactName ?: directPhone!!
        var targetNumber = directPhone

        if (targetNumber.isNullOrBlank() && !contactName.isNullOrBlank()) {
            val matches = contactLookupOverride?.invoke(contactName) ?: queryLocalContacts(contactName)
            if (matches.isEmpty()) {
                return ToolResult(
                    success = false,
                    message = "I couldn't find any contact matching \"$contactName\" in your address book, boss.",
                    data = mapOf("directResponse" to true)
                )
            }

            val distinctContacts = matches.distinctBy { it.displayName.lowercase() }
            if (distinctContacts.size > 1) {
                val candidateNames = distinctContacts.take(3).map { it.displayName }
                val candidatesList = candidateNames.joinToString(", ")
                return ToolResult(
                    success = false,
                    message = "I found multiple contacts matching \"$contactName\": $candidatesList. Which one should I message, boss?",
                    data = mapOf("isAmbiguous" to true, "candidates" to candidateNames, "directResponse" to true)
                )
            }

            val matchedContact = distinctContacts.first()
            targetName = matchedContact.displayName
            targetNumber = matchedContact.phoneNumber
        }

        if (targetNumber.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "Could not resolve a valid phone number for $targetName, boss.",
                data = mapOf("directResponse" to true)
            )
        }

        // 2. Custom action override for testing
        if (smsActionOverride != null) {
            val sent = smsActionOverride.invoke(targetNumber, message)
            return if (sent) {
                ToolResult(
                    success = true,
                    message = "Message sent to $targetName: \"$message\", boss.",
                    data = mapOf("recipient" to targetName, "sent" to true, "directResponse" to true)
                )
            } else {
                ToolResult(false, "Failed to send message to $targetName, boss.", mapOf("directResponse" to true))
            }
        }

        if (context == null) {
            return ToolResult(false, "Android context unavailable to send SMS.", mapOf("directResponse" to true))
        }

        // 3. Dispatch via SmsManager if permitted, or fallback to default SMS app
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasSmsPermission) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(targetNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(targetNumber, null, message, null, null)
                }

                ToolResult(
                    success = true,
                    message = "Message sent to $targetName, boss: \"$message\"",
                    data = mapOf("recipient" to targetName, "directResponse" to true)
                )
            } catch (e: Exception) {
                Log.e(TAG, "SmsManager error: ${e.message}. Using SMS app fallback.", e)
                fallbackToMessagingApp(targetNumber, message, targetName)
            }
        } else {
            fallbackToMessagingApp(targetNumber, message, targetName)
        }
    }

    private fun fallbackToMessagingApp(phoneNumber: String, message: String, recipientName: String): ToolResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context?.startActivity(intent)
            ToolResult(
                success = true,
                message = "I've opened your messaging app with the text ready for $recipientName, boss.",
                data = mapOf("recipient" to recipientName, "openedComposer" to true, "directResponse" to true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open messaging app fallback", e)
            ToolResult(
                success = false,
                message = "Could not send SMS to $recipientName: ${e.message}",
                data = mapOf("directResponse" to true)
            )
        }
    }

    private fun queryLocalContacts(nameQuery: String): List<CallContactTool.ContactInfo> {
        val results = mutableListOf<CallContactTool.ContactInfo>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$nameQuery%")

        try {
            val cursor = context?.contentResolver?.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else null
                    if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                        results.add(CallContactTool.ContactInfo(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}")
        }

        return results
    }
}
