package com.potpal.mirrortrack.collectors.personal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class ContactsCollector @Inject constructor() : Collector {
    override val id = "contacts"
    override val displayName = "Contacts"
    override val rationale =
        "Collects hashed contact statistics (count + unique phones/emails). " +
        "Requires READ_CONTACTS."
    override val category = Category.PERSONAL
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 24.hours
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        try {
            // Contact count
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID), null, null, null
            )?.use { cursor ->
                points.add(DataPoint.long(id, category, "contact_count", cursor.count.toLong()))
            }

            // Hashed phones
            val phones = mutableSetOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (idx >= 0) while (cursor.moveToNext()) {
                    val raw = cursor.getString(idx) ?: continue
                    phones.add(sha256(raw.filter { it.isDigit() || it == '+' }))
                }
            }
            points.add(DataPoint.long(id, category, "unique_hashed_phones_count", phones.size.toLong()))

            // Hashed emails
            val emails = mutableSetOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                if (idx >= 0) while (cursor.moveToNext()) {
                    val raw = cursor.getString(idx) ?: continue
                    emails.add(sha256(raw.trim().lowercase()))
                }
            }
            points.add(DataPoint.long(id, category, "unique_hashed_emails_count", emails.size.toLong()))
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
        }
        return points
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ContactsCollector"
    }
}
