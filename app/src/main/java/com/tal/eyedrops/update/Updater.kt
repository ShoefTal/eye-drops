package com.tal.eyedrops.update

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tal.eyedrops.BuildConfig
import com.tal.eyedrops.R
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * Self-update from the public GitHub release: checks version.json once a day and when the app opens,
 * downloads the APK, verifies its SHA-256 and hands it to the system installer.
 * Android only installs it if it is signed with the same key as the installed app.
 * The first update asks the user to confirm; on Android 12+ later ones may install without asking.
 */
object Updater {
    const val ACTION_CHECK = "com.tal.eyedrops.UPDATE_CHECK"
    const val ACTION_STATUS = "com.tal.eyedrops.INSTALL_STATUS"
    private const val CH = "updates"
    private const val NOTIF = 4242
    private const val REQ_CHECK = 5

    @Volatile private var busy = false

    fun enabled() = BuildConfig.UPDATE_BASE.isNotBlank()

    fun currentCode(c: Context): Long {
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    fun canInstall(c: Context) = c.packageManager.canRequestPackageInstalls()

    fun scheduleDaily(c: Context) {
        if (!enabled()) return
        var next = LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.of(13, 30))
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val pi = PendingIntent.getBroadcast(
            c, REQ_CHECK, Intent(c, UpdateReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        c.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    /** Runs a check on a background thread. [interactive] shows short messages (used by the "check now" button). */
    fun checkAsync(c: Context, interactive: Boolean, done: (() -> Unit)? = null) {
        if (!enabled() || busy) { done?.invoke(); return }
        busy = true
        val app = c.applicationContext
        thread {
            val msg = runCatching { check(app) }.getOrElse { "בדיקת העדכון נכשלה. נסה שוב כשיש אינטרנט." }
            busy = false
            Handler(Looper.getMainLooper()).post {
                if (interactive && msg != null) Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                done?.invoke()
            }
        }
    }

    private fun get(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }

    /** Returns a message for the user, or null when an install was started. */
    private fun check(c: Context): String? {
        val base = BuildConfig.UPDATE_BASE.trimEnd('/')
        val info = JSONObject(get("$base/version.json").inputStream.bufferedReader().use { it.readText() })
        val code = info.getLong("versionCode")
        if (code <= currentCode(c)) return "יש לך את הגרסה האחרונה"
        val name = info.optString("versionName", "")
        if (!canInstall(c)) {
            notify(c, "עדכון זמין: גרסה $name", "פתח את האפליקציה > הגדרות > אפשר עדכונים", null)
            return "יש גרסה חדשה ($name). צריך קודם לאשר עדכונים בהגדרות."
        }
        val apk = File(c.cacheDir, "update.apk")
        get("$base/${info.optString("apk", "Toma.apk")}").inputStream.use { inp -> apk.outputStream().use { inp.copyTo(it) } }
        val expected = info.optString("sha256", "")
        val actual = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { String.format(java.util.Locale.US, "%02x", it) }
        if (expected.isNotEmpty() && !expected.equals(actual, ignoreCase = true)) {
            apk.delete()
            return "קובץ העדכון לא תקין, לא הותקן"
        }
        install(c, apk)
        return null
    }

    private fun install(c: Context, apk: File) {
        val installer = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(c.packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("app.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(c, id, Intent(c, UpdateReceiver::class.java).setAction(ACTION_STATUS), flags)
            s.commit(status.intentSender)
        }
    }

    fun notify(c: Context, title: String, text: String, tap: PendingIntent?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "עדכוני אפליקציה", NotificationManager.IMPORTANCE_DEFAULT))
        val b = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        if (tap != null) b.setContentIntent(tap)
        NotificationManagerCompat.from(c).notify(NOTIF, b.build())
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            Updater.ACTION_CHECK -> {
                val pending = goAsync()
                Updater.checkAsync(c, interactive = false) { pending.finish() }
                Updater.scheduleDaily(c)
            }
            Updater.ACTION_STATUS -> {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Works when the app is open; otherwise the notification lets the user start it.
                        runCatching { c.startActivity(confirm) }
                        val tap = PendingIntent.getActivity(c, 77, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        Updater.notify(c, "עדכון מוכן להתקנה", "לחץ כאן כדי לעדכן את Toma", tap)
                    }
                    PackageInstaller.STATUS_SUCCESS -> Unit
                    else -> Updater.notify(c, "העדכון לא הותקן", intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "", null)
                }
            }
        }
    }
}
