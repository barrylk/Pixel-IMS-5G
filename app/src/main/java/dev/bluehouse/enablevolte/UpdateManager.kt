package dev.bluehouse.enablevolte

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val notes: String,
)

object UpdateManager {
    const val REPOSITORY_URL = "https://github.com/barrylk/Pixel-IMS-5G"
    const val ISSUES_URL = "$REPOSITORY_URL/issues"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/barrylk/Pixel-IMS-5G/releases/latest"
    private const val PREFS = "github_updater"
    private const val DOWNLOAD_ID = "download_id"

    fun latestRelease(): ReleaseInfo {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Pixel-IMS-5G/${BuildConfig.VERSION_NAME}")
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = release.getJSONArray("assets")
            var apkUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            return ReleaseInfo(
                version = release.getString("tag_name").removePrefix("v"),
                pageUrl = release.getString("html_url"),
                apkUrl = apkUrl,
                notes = release.optString("body"),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(candidate: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        fun parts(value: String) = value.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val candidateParts = parts(candidate)
        val currentParts = parts(current)
        for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    fun download(context: Context, release: ReleaseInfo): Long {
        val apkUrl = release.apkUrl ?: throw IllegalStateException("This release has no APK asset")
        val fileName = "Pixel-IMS-5G-v${release.version}.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Pixel IMS 5G ${release.version}")
            .setDescription("Downloading app update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(DOWNLOAD_ID, id).apply()
        return id
    }

    fun open(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val expected = context.getSharedPreferences("github_updater", Context.MODE_PRIVATE).getLong("download_id", -1)
        val completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (completed != expected) return
        val manager = context.getSystemService(DownloadManager::class.java)
        val uri = manager.getUriForDownloadedFile(completed) ?: return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Update downloaded. Open Downloads to install it.", Toast.LENGTH_LONG).show()
        }
    }
}
