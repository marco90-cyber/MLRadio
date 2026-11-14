package com.marco.mlradio.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log dell'app: tiene le ultime righe in memoria, le scrive su file e le mostra
 * nel menu Debug. Il file si può esportare/condividere.
 *
 * - All'avvio vengono ricaricate le ultime righe della sessione precedente, così un crash
 *   (che chiude l'app) resta visibile nel log esportato alla riapertura.
 * - I crash vengono intercettati e scritti con lo stack trace completo prima della chiusura.
 */
object AppLog {
    private const val MAX_LINES = 4000
    private const val PREVIOUS_LINES = 800
    private const val MAX_FILE = 1024 * 1024
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private var crashMarker: File? = null
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale.ITALY)

    /** Se vero registra anche i messaggi di dettaglio (tocchi, ghiera, ecc.). */
    @Volatile
    var verbose: Boolean = false

    /** Cambia a ogni nuova riga: serve alla UI per aggiornarsi. */
    var revision by mutableIntStateOf(0)
        private set

    fun init(context: Context, versionName: String = "?", versionCode: Int = 0) {
        val f = File(context.filesDir, "mlradio.log")
        file = f
        crashMarker = File(context.filesDir, "mlradio.crash")
        // righe della sessione precedente (servono soprattutto dopo un crash)
        try {
            if (f.exists()) {
                val old = f.readLines()
                val tail = old.takeLast(PREVIOUS_LINES)
                synchronized(this) { tail.forEach { lines.addLast(it) } }
                if (f.length() > MAX_FILE) f.writeText(tail.joinToString("\n", postfix = "\n"))
            }
        } catch (_: Exception) {
        }
        add("I", "App", "════════ NUOVA SESSIONE ════════")
        i(
            "App",
            "MLRadio $versionName ($versionCode) avviata - Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val rt = Runtime.getRuntime()
            i(
                "App",
                "Lingua ${Locale.getDefault()}, ABI ${Build.SUPPORTED_ABIS.joinToString()}, " +
                    "heap max ${rt.maxMemory() / 1048576} MB, classe memoria ${am.memoryClass} MB, pid ${Process.myPid()}"
            )
        } catch (_: Exception) {
        }
        val cm = crashMarker
        if (cm != null && cm.exists()) {
            val what = try { cm.readText().trim() } catch (_: Exception) { "" }
            w("App", "La sessione precedente si è chiusa per un errore: $what (dettagli più sopra, cerca CRASH)")
            try { cm.delete() } catch (_: Exception) { }
        }
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            try {
                add("E", "CRASH", "Errore non gestito nel thread «${thread.name}»: ${t.javaClass.name}: ${t.message}")
                stackOf(t).lineSequence().forEach { if (it.isNotBlank()) add("E", "CRASH", it) }
                crashMarker?.writeText("${t.javaClass.simpleName}: ${t.message}")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, t)
        }
    }

    fun stackOf(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    fun d(tag: String, msg: String) {
        if (verbose) add("D", tag, msg)
    }

    fun i(tag: String, msg: String) = add("I", tag, msg)

    /** Avviso: con un errore si aggiungono il tipo, il messaggio e le prime righe dello stack. */
    fun w(tag: String, msg: String, t: Throwable? = null) {
        add("W", tag, if (t != null) "$msg (${describe(t)})" else msg)
        if (t != null) shortStack(t).forEach { add("W", tag, "    $it") }
    }

    /** Errore: stack trace completo. */
    fun e(tag: String, msg: String, t: Throwable? = null) {
        add("E", tag, if (t != null) "$msg (${describe(t)})" else msg)
        if (t != null) stackOf(t).lineSequence().drop(1).forEach { if (it.isNotBlank()) add("E", tag, it) }
    }

    private fun describe(t: Throwable): String {
        val sb = StringBuilder("${t.javaClass.simpleName}: ${t.message}")
        var c = t.cause
        var n = 0
        while (c != null && n < 3) {
            sb.append(" ← ${c.javaClass.simpleName}: ${c.message}")
            c = c.cause; n++
        }
        return sb.toString()
    }

    private fun shortStack(t: Throwable): List<String> =
        t.stackTrace.take(4).map { "at $it" }

    @Synchronized
    private fun add(level: String, tag: String, msg: String) {
        val thread = Thread.currentThread().name.let { if (it == "main") "" else " [$it]" }
        val line = "${fmt.format(Date())} $level/$tag$thread: $msg"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        try {
            android.util.Log.println(
                when (level) {
                    "E" -> android.util.Log.ERROR
                    "W" -> android.util.Log.WARN
                    "D" -> android.util.Log.DEBUG
                    else -> android.util.Log.INFO
                },
                "MLRadio/$tag",
                msg
            )
            file?.appendText(line + "\n")
        } catch (_: Exception) {
        }
        try {
            revision++
        } catch (_: Throwable) {
            // lo stato Compose può non essere scrivibile durante un crash o da thread particolari
        }
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        try {
            file?.writeText("")
        } catch (_: Exception) {
        }
        revision++
    }

    /** Scrive il log in un file temporaneo condivisibile. */
    fun exportFile(context: Context): File {
        val out = File(context.cacheDir, "mlradio-log.txt")
        out.writeText(text() + "\n")
        return out
    }
}
