package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.CallLogEntity
import com.example.data.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class CallNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "CallNotifListener"
        private var lastLoggedKey: String? = null
        private var lastLoggedTime: Long = 0L

        fun isPermissionGranted(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(pkgName)
        }

        fun openPermissionSettings(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                        putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            ComponentName(context, CallNotificationListenerService::class.java).flattenToString()
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } else {
                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Cannot open notification listener settings", ex)
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val category = notification.category
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        val combinedContent = "$title $text $subText $bigText".lowercase()
        val pkg = sbn.packageName.lowercase()

        // Detect if this is a call or missed call notification
        val isMissedCategory = category == Notification.CATEGORY_MISSED_CALL
        val isCallCategory = category == Notification.CATEGORY_CALL

        val isMissedKeyword = combinedContent.contains("manqué") ||
                combinedContent.contains("missed") ||
                combinedContent.contains("non abouti") ||
                combinedContent.contains("occupé")

        val isCallKeyword = combinedContent.contains("appel") ||
                combinedContent.contains("calling") ||
                combinedContent.contains("entrant") ||
                combinedContent.contains("incoming") ||
                combinedContent.contains("reçu")

        val isDialerPkg = pkg.contains("dialer") ||
                pkg.contains("telecom") ||
                pkg.contains("phone") ||
                pkg.contains("incall") ||
                pkg.contains("telephony")

        val isRelevantCall = isMissedCategory || isCallCategory || ((isMissedKeyword || isCallKeyword) && isDialerPkg) || isMissedKeyword

        if (!isRelevantCall) return

        val callType = when {
            isMissedCategory || isMissedKeyword -> 3 // Missed
            else -> 1 // Incoming
        }

        // Debounce repeated notification posts within 4 seconds
        val now = System.currentTimeMillis()
        val currentKey = "$callType:$title:$text"
        if (currentKey == lastLoggedKey && (now - lastLoggedTime) < 4000L) {
            return
        }
        lastLoggedKey = currentKey
        lastLoggedTime = now

        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val contacts = db.contactDao().getAllContactsList()

                // Try to find matching contact by phone number or name
                val matchedContact = findMatchingContact(title, text, contacts)

                val logName = matchedContact?.name?.ifBlank { matchedContact.phoneNumber }
                    ?: title.ifBlank { text.ifBlank { "Appel inconnu" } }
                val logPhone = matchedContact?.phoneNumber ?: extractPhoneNumber(title, text)
                val logImage = matchedContact?.imageUri

                val logEntry = CallLogEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = matchedContact?.id,
                    name = logName,
                    phoneNumber = logPhone,
                    imageUri = logImage,
                    timestamp = now,
                    callType = callType
                )

                db.callLogDao().insertCallLog(logEntry)
                Log.d(TAG, "Successfully logged call: $logName ($callType)")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving call log from notification", e)
            }
        }
    }

    private fun findMatchingContact(title: String, text: String, contacts: List<Contact>): Contact? {
        val candidateNumbers = listOf(
            extractDigits(title),
            extractDigits(text)
        ).filter { it.length >= 6 }

        for (contact in contacts) {
            val contactDigits = extractDigits(contact.phoneNumber)
            if (contactDigits.isNotBlank()) {
                for (cand in candidateNumbers) {
                    if (cand == contactDigits || cand.endsWith(contactDigits) || contactDigits.endsWith(cand)) {
                        return contact
                    }
                }
            }
        }

        return null
    }

    private fun extractDigits(input: String): String {
        return input.filter { it.isDigit() }
    }

    private fun extractPhoneNumber(title: String, text: String): String {
        val titleDigits = extractDigits(title)
        if (titleDigits.length >= 6) return title
        val textDigits = extractDigits(text)
        if (textDigits.length >= 6) return text
        return ""
    }
}
