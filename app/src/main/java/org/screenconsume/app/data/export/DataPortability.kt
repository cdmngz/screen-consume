package org.screenconsume.app.data.export

import org.json.JSONArray
import org.json.JSONObject
import org.screenconsume.app.data.database.PortableUsageRow
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object DataPortability {
    private const val VERSION = 1
    const val MAX_RECORDS = 250_000
    const val MAX_PACKAGE_NAME_LENGTH = 255
    const val MAX_DISPLAY_NAME_LENGTH = 512
    const val MAX_CATEGORY_LENGTH = 256
    private val magic = byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())

    fun toCsv(rows: List<PortableUsageRow>): ByteArray = buildString {
        appendLine("date,packageName,displayName,category,usageSeconds,launchCount,morningUsageSeconds,afternoonUsageSeconds,eveningUsageSeconds,nightUsageSeconds")
        rows.forEach { row ->
            appendLine(listOf(row.date, row.packageName, row.displayName, row.category.orEmpty(), row.usageSeconds, row.launchCount, row.morningUsageSeconds, row.afternoonUsageSeconds, row.eveningUsageSeconds, row.nightUsageSeconds).joinToString(",") { csvCell(it.toString()) })
        }
    }.toByteArray()

    fun toJson(rows: List<PortableUsageRow>): ByteArray {
        val records = JSONArray()
        rows.forEach { row ->
            records.put(JSONObject().apply {
                put("date", row.date); put("packageName", row.packageName); put("displayName", row.displayName)
                put("category", row.category ?: JSONObject.NULL); put("usageSeconds", row.usageSeconds); put("launchCount", row.launchCount)
                put("morningUsageSeconds", row.morningUsageSeconds); put("afternoonUsageSeconds", row.afternoonUsageSeconds)
                put("eveningUsageSeconds", row.eveningUsageSeconds); put("nightUsageSeconds", row.nightUsageSeconds)
            })
        }
        return JSONObject().put("format", "screen-consume-backup").put("version", VERSION).put("records", records).toString(2).toByteArray()
    }

    fun fromJson(bytes: ByteArray): List<PortableUsageRow> {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getString("format") == "screen-consume-backup") { "Not a ScreenConsume data file" }
        require(root.getInt("version") == VERSION) { "Unsupported backup version" }
        val records = root.getJSONArray("records")
        require(records.length() <= MAX_RECORDS) { "Backup exceeds the $MAX_RECORDS record limit" }
        return buildList {
            for (index in 0 until records.length()) {
                val item = records.getJSONObject(index)
                val packageName = item.getString("packageName")
                val displayName = item.getString("displayName")
                val category = item.optString("category").takeIf { !item.isNull("category") }
                require(packageName.isNotBlank() && packageName.length <= MAX_PACKAGE_NAME_LENGTH) { "Backup contains an invalid package name" }
                require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) { "Backup contains an oversized app name" }
                require(category == null || category.length <= MAX_CATEGORY_LENGTH) { "Backup contains an oversized category" }
                add(PortableUsageRow(item.getString("date"), packageName, displayName, category, item.getLong("usageSeconds"), item.getInt("launchCount"), item.getLong("morningUsageSeconds"), item.getLong("afternoonUsageSeconds"), item.getLong("eveningUsageSeconds"), item.getLong("nightUsageSeconds")))
            }
        }
    }

    fun encrypt(json: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(json)
        return ByteBuffer.allocate(magic.size + salt.size + iv.size + ciphertext.size).put(magic).put(salt).put(iv).put(ciphertext).array()
    }

    fun decrypt(backup: ByteArray, password: CharArray): ByteArray {
        require(backup.size > 48 && backup.copyOfRange(0, 4).contentEquals(magic)) { "Not an encrypted ScreenConsume backup" }
        val salt = backup.copyOfRange(4, 20)
        val iv = backup.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(backup.copyOfRange(32, backup.size))
    }

    fun isEncrypted(bytes: ByteArray) = bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(magic)

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }

    private fun csvCell(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
