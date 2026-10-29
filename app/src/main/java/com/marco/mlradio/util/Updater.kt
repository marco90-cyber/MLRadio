package com.marco.mlradio.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.marco.mlradio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Aggiornamenti dalle Releases di GitHub del progetto. */
object Updater {
    data class Release(val build: Int, val name: String, val apkUrl: String, val pageUrl: String, val notes: String)

    private const val API = "https://api.github.com/repos/marco90-cyber/MLRadio/releases/latest"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MLRadio/${BuildConfig.VERSION_NAME} (Android)")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    /** Ultima versione pubblicata (numero di build preso dal tag «build-N»). */
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val c = open(API)
        try {
            if (c.responseCode != 200) throw IllegalStateException("GitHub ha risposto ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val build = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull()
                ?: Regex("(\\d+)$").find(o.optString("name"))?.value?.toIntOrNull() ?: 0
            val assets = o.optJSONArray("assets")
            var apk = ""
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url"); break
                }
            }
            Release(build, o.optString("name").ifBlank { tag }, apk, o.optString("html_url"), o.optString("body"))
        } finally {
            c.disconnect()
        }
    }

    val currentBuild: Int get() = BuildConfig.VERSION_CODE

    /** Scarica l'APK nella cache dell'app; [progress] va da 0 a 1 (o -1 se la dimensione è ignota). */
    suspend fun download(context: Context, url: String, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "MLRadio-update.apk")
        val c = open(url)
        c.setRequestProperty("Accept", "application/octet-stream")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Download non riuscito (${c.responseCode})")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        progress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        out
    }

    /** true se l'app può già aprire l'installatore; altrimenti apre l'impostazione da attivare. */
    fun ensureInstallPermission(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
