package com.jarvis.assistant.tools.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult

/**
 * Tool to resolve a contact from the device's address book and initiate a phone call.
 * Privacy-first: Contact lookup is performed 100% locally on device.
 * Contacts are NEVER uploaded to any remote server.
 */
class CallContactTool(
    private val context: Context? = null,
    private val contactLookupOverride: ((String) -> List<ContactInfo>)? = null,
    private val callActionOverride: ((String) -> Boolean)? = null
) : JarvisTool {

    companion object {
        private const val TAG = "CallContactTool"
    }

    override val name: String = "call_contact"
    override val description: String = "Initiates a phone call to a named contact from the local address book (e.g. 'Call Daddy')."
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM

    data class ContactInfo(
        val displayName: String,
        val phoneNumber: String
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val contactName = (arguments["contactName"] as? String)?.trim()
        if (contactName.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "Please specify the name of the contact you want to call, boss.",
                data = mapOf("directResponse" to true)
            )
        }

        // 1. Permission check
        if (contactLookupOverride == null &&
            (context == null || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
        ) {
            return ToolResult(
                success = false,
                message = "JARVIS needs contact access permission to find $contactName.",
                data = mapOf("needsPermission" to "READ_CONTACTS", "directResponse" to true)
            )
        }

        // 2. Perform local contact resolution
        val matches = contactLookupOverride?.invoke(contactName) ?: queryLocalContacts(contactName)

        if (matches.isEmpty()) {
            return ToolResult(
                success = false,
                message = "I couldn't find any contact matching \"$contactName\" in your address book, boss.",
                data = mapOf("directResponse" to true)
            )
        }

        // 3. Disambiguate if multiple distinct contact names match
        val distinctContacts = matches.distinctBy { it.displayName.lowercase() }
        if (distinctContacts.size > 1) {
            val candidateNames = distinctContacts.take(3).map { it.displayName }
            val formattedCandidates = candidateNames.joinToString(", ")
            return ToolResult(
                success = false,
                message = "I found multiple contacts named $contactName (multiple contacts matching: $formattedCandidates). Which $contactName should I call?",
                data = mapOf(
                    "isAmbiguous" to true,
                    "candidates" to candidateNames,
                    "directResponse" to true
                )
            )
        }

        // 4. Exactly one matching contact
        val targetContact = distinctContacts[0]
        val cleanNumber = targetContact.phoneNumber.replace("[^0-9+]".toRegex(), "")

        if (cleanNumber.isBlank()) {
            return ToolResult(
                success = false,
                message = "Contact \"${targetContact.displayName}\" does not have a valid phone number, boss.",
                data = mapOf("directResponse" to true)
            )
        }

        // 5. Place the call
        val callPlaced = callActionOverride?.invoke(cleanNumber) ?: placeCall(cleanNumber)

        return if (callPlaced) {
            ToolResult(
                success = true,
                message = "Calling ${targetContact.displayName} now, boss.",
                data = mapOf(
                    "contactName" to targetContact.displayName,
                    "directResponse" to true
                )
            )
        } else {
            ToolResult(
                success = false,
                message = "Failed to open phone dialer for ${targetContact.displayName}, boss.",
                data = mapOf("directResponse" to true)
            )
        }
    }

    private fun queryLocalContacts(nameQuery: String): List<ContactInfo> {
        val results = mutableListOf<ContactInfo>()
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
                        results.add(ContactInfo(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}")
        }

        return results
    }

    private fun placeCall(phoneNumber: String): Boolean {
        if (context == null) return false
        return try {
            val hasCallPhone = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            // Use ACTION_CALL if CALL_PHONE permission is granted, otherwise use safe ACTION_DIAL
            val intentAction = if (hasCallPhone) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(intentAction).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error placing call: ${e.message}")
            try {
                // Fallback to ACTION_DIAL if ACTION_CALL failed
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                true
            } catch (dialEx: Exception) {
                Log.e(TAG, "Fallback to dialer failed: ${dialEx.message}")
                false
            }
        }
    }
}
