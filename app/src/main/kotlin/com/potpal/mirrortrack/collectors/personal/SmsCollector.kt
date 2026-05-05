package com.potpal.mirrortrack.collectors.personal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Reads SMS metadata for behavioural analysis. **No raw bodies are persisted.**
 *
 * What is stored, per poll window (default 24h):
 *  - inbox/sent counts (last 24h, last 7d, last 30d)
 *  - unique hashed senders
 *  - average length bucket of bodies (short / medium / long)
 *  - count of OTP-like messages (heuristic on body)
 *  - count of bank/financial alerts (heuristic on sender + body keywords)
 *  - hour-of-day histogram of inbound traffic
 *
 * Heuristics run in-process; no body text leaves this function.
 */
@Singleton
class SmsCollector @Inject constructor() : Collector {
    override val id = "sms"
    override val displayName = "SMS Patterns"
    override val rationale =
        "Counts SMS volume and detects bank/OTP/transaction messages. " +
        "Hashes sender numbers; raw message bodies are never stored. " +
        "Requires READ_SMS."
    override val category = Category.PERSONAL
    override val requiredPermissions = listOf(Manifest.permission.READ_SMS)
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 12.hours
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        val cutoff30d = now - 30L * day
        val zone = ZoneId.systemDefault()

        try {
            var inbox24 = 0L
            var inbox7 = 0L
            var inbox30 = 0L
            var sent24 = 0L
            var sent7 = 0L
            var sent30 = 0L
            val sendersHashed = mutableSetOf<String>()
            var shortBodies = 0L
            var mediumBodies = 0L
            var longBodies = 0L
            var otpCount = 0L
            var bankAlertCount = 0L
            var transactionCount = 0L
            var promotionalCount = 0L
            val hourHistogram = LongArray(24)

            // Inbox
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoff30d.toString()),
                null
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val ts = if (dateIdx >= 0) cursor.getLong(dateIdx) else continue
                    val ageDays = (now - ts) / day
                    if (ageDays <= 1) inbox24++
                    if (ageDays <= 7) inbox7++
                    inbox30++

                    val sender = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                    if (sender.isNotBlank()) {
                        sendersHashed.add(sha256(sender.filter { it.isDigit() || it == '+' }))
                    }

                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    when {
                        body.length < 50 -> shortBodies++
                        body.length < 160 -> mediumBodies++
                        else -> longBodies++
                    }
                    if (looksLikeOtp(body)) otpCount++
                    if (looksLikeBankAlert(sender, body)) bankAlertCount++
                    if (looksLikeTransaction(body)) transactionCount++
                    if (looksLikePromotional(sender, body)) promotionalCount++

                    val hour = Instant.ofEpochMilli(ts).atZone(zone).hour.coerceIn(0, 23)
                    hourHistogram[hour]++
                }
            }

            // Sent
            context.contentResolver.query(
                Telephony.Sms.Sent.CONTENT_URI,
                arrayOf(Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoff30d.toString()),
                null
            )?.use { cursor ->
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val ts = if (dateIdx >= 0) cursor.getLong(dateIdx) else continue
                    val ageDays = (now - ts) / day
                    if (ageDays <= 1) sent24++
                    if (ageDays <= 7) sent7++
                    sent30++
                }
            }

            points += DataPoint.long(id, category, "inbox_24h", inbox24)
            points += DataPoint.long(id, category, "inbox_7d", inbox7)
            points += DataPoint.long(id, category, "inbox_30d", inbox30)
            points += DataPoint.long(id, category, "sent_24h", sent24)
            points += DataPoint.long(id, category, "sent_7d", sent7)
            points += DataPoint.long(id, category, "sent_30d", sent30)
            points += DataPoint.long(id, category, "unique_senders_30d", sendersHashed.size.toLong())
            points += DataPoint.long(id, category, "short_bodies_30d", shortBodies)
            points += DataPoint.long(id, category, "medium_bodies_30d", mediumBodies)
            points += DataPoint.long(id, category, "long_bodies_30d", longBodies)
            points += DataPoint.long(id, category, "otp_count_30d", otpCount)
            points += DataPoint.long(id, category, "bank_alert_count_30d", bankAlertCount)
            points += DataPoint.long(id, category, "transaction_count_30d", transactionCount)
            points += DataPoint.long(id, category, "promotional_count_30d", promotionalCount)
            points += DataPoint.json(
                id, category, "hour_histogram_30d",
                hourHistogram.joinToString(prefix = "[", postfix = "]")
            )
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
        } catch (e: Exception) {
            Logger.e(TAG, "SMS read failed", e)
        }
        return points
    }

    private fun looksLikeOtp(body: String): Boolean {
        if (body.length > 240) return false
        val lower = body.lowercase()
        val hasKeyword = OTP_KEYWORDS.any { lower.contains(it) }
        val hasShortCode = OTP_DIGIT_REGEX.containsMatchIn(body)
        return hasKeyword && hasShortCode
    }

    private fun looksLikeBankAlert(sender: String, body: String): Boolean {
        val s = sender.lowercase()
        val b = body.lowercase()
        if (BANK_SENDER_HINTS.any { s.contains(it) }) return true
        return BANK_BODY_HINTS.any { b.contains(it) }
    }

    private fun looksLikeTransaction(body: String): Boolean {
        val lower = body.lowercase()
        val hasMoneySymbol = MONEY_REGEX.containsMatchIn(body)
        val hasKeyword = TRANSACTION_KEYWORDS.any { lower.contains(it) }
        return hasMoneySymbol && hasKeyword
    }

    private fun looksLikePromotional(sender: String, body: String): Boolean {
        val lower = body.lowercase()
        return PROMO_KEYWORDS.any { lower.contains(it) } ||
            sender.length <= 6 && body.length > 80
    }

    private fun sha256(input: String): String {
        if (input.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SmsCollector"
        private val OTP_DIGIT_REGEX = Regex("\\b\\d{4,8}\\b")
        private val MONEY_REGEX = Regex("[\\$£€¥₹]\\s?\\d|USD\\s?\\d|\\d+\\.\\d{2}")
        private val OTP_KEYWORDS = listOf(
            "otp", "verification code", "verify", "one-time", "one time",
            "passcode", "security code", "auth code", "login code", "confirmation code"
        )
        private val BANK_SENDER_HINTS = listOf(
            "chase", "bofa", "wells", "citi", "amex", "discover", "capital",
            "venmo", "zelle", "paypal", "cashapp", "cash-app", "apple", "google",
            "usbank", "pnc", "ally", "truist", "td", "regions", "fifth"
        )
        private val BANK_BODY_HINTS = listOf(
            "transaction", "purchase", "debit", "credit", "deposit", "withdrawal",
            "balance is", "transferred", "card ending", "account ending",
            "alert:", "fraud alert", "suspicious activity", "payment received",
            "payment of", "you sent", "you received", "ach", "direct deposit"
        )
        private val TRANSACTION_KEYWORDS = listOf(
            "purchase", "transaction", "charged", "debit", "credit",
            "spent", "transferred", "deposit", "withdraw", "payment"
        )
        private val PROMO_KEYWORDS = listOf(
            "% off", "discount", "promo", "coupon", "sale", "limited time",
            "ends today", "buy now", "click here", "stop to opt out", "reply stop",
            "exclusive offer", "free shipping"
        )
    }
}
