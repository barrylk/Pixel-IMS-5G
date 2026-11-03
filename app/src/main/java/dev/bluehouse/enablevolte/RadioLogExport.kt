package dev.bluehouse.enablevolte

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RadioLogCaptureResult(
    val uri: Uri,
    val fileName: String,
    val fileCount: Int,
    val bytes: Long,
    val rilFiles: Int,
    val rilDirectory: String,
    val skipped: Int,
    val endcEvents: List<String>,
    val uecap: List<String>,
)

/**
 * Saves a root radio-log ZIP to Downloads/Pixel IMS 5G, next to Field Test reports.
 *
 * The app owns the MediaStore entry and hands the root service a writable descriptor for
 * it, so the root process never creates files the app cannot read or share.
 */
object RadioLogExport {
    suspend fun capture(context: Context, redact: Boolean): RadioLogCaptureResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "PixelIMS5G-radio-logs-$timestamp.zip"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Pixel IMS 5G")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "Android could not create the log archive in Downloads"
        }
        try {
            val descriptor = requireNotNull(resolver.openFileDescriptor(uri, "w")) {
                "Android could not open the log archive"
            }
            // Binder hands the root service its own copy of the descriptor, which it closes
            // after writing; this process closes its copy here.
            val json = JSONObject(descriptor.use { PrivilegeManager.captureRadioLogs(it, redact) })
            check(json.optBoolean("ok")) { json.optString("message").ifBlank { "Radio log capture failed." } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            fun strings(key: String) = json.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            RadioLogCaptureResult(
                uri = uri,
                fileName = fileName,
                fileCount = json.optJSONArray("files")?.length() ?: 0,
                bytes = json.optLong("bytes"),
                rilFiles = json.optInt("rilFiles"),
                rilDirectory = json.optString("rilDirectory"),
                skipped = json.optJSONArray("skipped")?.length() ?: 0,
                endcEvents = strings("endcEvents"),
                uecap = strings("uecap"),
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
