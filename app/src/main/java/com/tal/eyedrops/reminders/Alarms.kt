package com.tal.eyedrops.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tal.eyedrops.R
import com.tal.eyedrops.data.Fmt
import com.tal.eyedrops.data.Plan
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.ui.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One alarm for the next dose time (setAlarmClock, the most reliable kind, survives Doze),
 * plus one follow-up alarm that re-reminds 15 minutes later while the dose is not marked done.
 * Two notification channels because Android fixes a channel's sound once it is created.
 */
object Alarms {
    const val ACTION_SLOT = "com.tal.eyedrops.SLOT"
    const val ACTION_FOLLOW = "com.tal.eyedrops.FOLLOW"
    const val ACTION_DONE = "com.tal.eyedrops.DONE"
    const val ACTION_SNOOZE = "com.tal.eyedrops.SNOOZE"
    const val ACTION_TEST = "com.tal.eyedrops.TEST"
    private const val REQ_TEST = 3
    private const val TEST_NOTIF = 777
    const val EXTRA_SLOT = "slot"
    const val EXTRA_N = "n"
    private const val REQ_SLOT = 1
    private const val REQ_FOLLOW = 2
    private const val CH_QUIET = "drops_quiet"
    private const val MAX_FOLLOW = 2

    private fun soundChannel() = "drops_sound_v${Store.toneVersion()}"

    fun defaultTone(): Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun currentTone(): Uri? = Store.toneUri()?.let { Uri.parse(it) } ?: defaultTone()

    fun createChannels(c: Context) {
        Store.init(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        // Drop sound channels from earlier tone choices.
        nm.notificationChannels.filter { it.id.startsWith("drops_sound") && it.id != soundChannel() }
            .forEach { nm.deleteNotificationChannel(it.id) }
        val alarmTone = currentTone()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val vib = longArrayOf(0, 400, 200, 400, 200, 400)
        nm.createNotificationChannel(
            NotificationChannel(soundChannel(), "טיפות – התראה קולית", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(alarmTone, attrs)
                enableVibration(true)
                vibrationPattern = vib
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_QUIET, "טיפות – התראה שקטה (רטט)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = vib
            }
        )
    }

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)
    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun broadcast(c: Context, action: String, req: Int, slot: LocalDateTime, n: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            c, req,
            Intent(c, AlarmReceiver::class.java).setAction(action)
                .putExtra(EXTRA_SLOT, slot.toString()).putExtra(EXTRA_N, n),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(c: Context, req: Int): PendingIntent = PendingIntent.getActivity(
        c, req,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(c).canScheduleExactAlarms()

    private fun set(c: Context, at: LocalDateTime, pi: PendingIntent) {
        val ms = millis(at)
        if (canExact(c)) {
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(ms, openApp(c, 9)), pi)
        } else {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    /** (Re)schedule the alarm for the next dose time after now. Safe to call any time. */
    fun scheduleNext(c: Context) {
        Store.init(c)
        val now = LocalDateTime.now()
        val next = Plan.nextSlotAfter(now, Store.eyes())
        val pi = broadcast(c, ACTION_SLOT, REQ_SLOT, next ?: now)
        am(c).cancel(pi)
        if (next != null) set(c, next, broadcast(c, ACTION_SLOT, REQ_SLOT, next))
    }

    fun scheduleFollow(c: Context, slot: LocalDateTime, n: Int, minutes: Long) {
        set(c, LocalDateTime.now().plusMinutes(minutes), broadcast(c, ACTION_FOLLOW, REQ_FOLLOW, slot, n))
    }

    fun notifId(slot: LocalDateTime) = slot.toString().hashCode()

    fun cancelNotification(c: Context, slot: LocalDateTime) =
        NotificationManagerCompat.from(c).cancel(notifId(slot))

    fun notifySlot(c: Context, slot: LocalDateTime, repeat: Int) {
        Store.init(c)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val eyes = Store.eyes()
        val doses = Plan.dosesAt(slot, eyes).filter { !Store.isDone(it) }
        if (doses.isEmpty()) return
        val byDrug = doses.groupBy { it.drug }.toList().sortedBy { it.first.ordinal }
        val lines = byDrug.mapIndexed { i, (drug, ds) ->
            val who = if (eyes.size > 1) " (" + ds.joinToString(", ") { eyes[it.eye].name } + ")" else ""
            "${i + 1}. ${drug.label}$who"
        }
        val body = lines.joinToString("\n") + if (byDrug.size > 1) "\n5 דקות בין טיפה לטיפה" else ""
        val title = "טיפות ${Fmt.time(slot.toLocalTime())}" + if (repeat > 0) " · תזכורת" else ""
        val n = NotificationCompat.Builder(c, if (Store.soundOn()) soundChannel() else CH_QUIET)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle(title)
            .setContentText(byDrug.joinToString(" · ") { it.first.label })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(c, notifId(slot)))
            .setAutoCancel(true)
            .addAction(0, "בוצע ✓", broadcast(c, ACTION_DONE, notifId(slot) * 31 + 1, slot))
            .addAction(0, "עוד 10 דקות", broadcast(c, ACTION_SNOOZE, notifId(slot) * 31 + 2, slot, repeat))
            .build()
        NotificationManagerCompat.from(c).notify(notifId(slot), n)
    }

    // ---- test alarm ----
    fun scheduleTest(c: Context, at: LocalDateTime) {
        Store.setTestAt(at)
        set(c, at, broadcast(c, ACTION_TEST, REQ_TEST, at))
    }

    fun cancelTest(c: Context) {
        Store.setTestAt(null)
        am(c).cancel(broadcast(c, ACTION_TEST, REQ_TEST, LocalDateTime.now()))
    }

    fun notifyTest(c: Context) {
        Store.init(c)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(c, if (Store.soundOn()) soundChannel() else CH_QUIET)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle("בדיקה: טיפות ${Fmt.time(LocalDateTime.now().toLocalTime())}")
            .setContentText("כך תיראה ותישמע התראה של טיפות")
            .setStyle(NotificationCompat.BigTextStyle().bigText("1. Sterodex\n2. Vigamox\n3. Bromsite / Nevanac\n4. Bephanten\nזו התראת בדיקה בלבד."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(c, TEST_NOTIF))
            .setAutoCancel(true)
            .addAction(0, "בוצע ✓", broadcast(c, ACTION_DONE, TEST_NOTIF, LocalDateTime.of(2000, 1, 1, 0, 0)))
            .build()
        NotificationManagerCompat.from(c).notify(TEST_NOTIF, n)
    }

    fun followUpsAllowed() = Store.followUp()
    const val FOLLOW_MINUTES = 15L
    const val MAX = MAX_FOLLOW
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Store.init(c)
        val slot = intent.getStringExtra(Alarms.EXTRA_SLOT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val n = intent.getIntExtra(Alarms.EXTRA_N, 0)
        when (intent.action) {
            Alarms.ACTION_SLOT -> {
                if (slot != null && !Store.slotDone(slot)) {
                    Alarms.notifySlot(c, slot, 0)
                    if (Alarms.followUpsAllowed()) Alarms.scheduleFollow(c, slot, 1, Alarms.FOLLOW_MINUTES)
                }
                Alarms.scheduleNext(c)
            }
            Alarms.ACTION_FOLLOW -> {
                if (slot != null && !Store.slotDone(slot)) {
                    Alarms.notifySlot(c, slot, n)
                    if (Alarms.followUpsAllowed() && n < Alarms.MAX) Alarms.scheduleFollow(c, slot, n + 1, Alarms.FOLLOW_MINUTES)
                }
            }
            Alarms.ACTION_TEST -> {
                Store.setTestAt(null)
                Alarms.notifyTest(c)
            }
            Alarms.ACTION_DONE -> {
                slot ?: return
                if (slot.year == 2000) { NotificationManagerCompat.from(c).cancel(777); return }
                Store.setDone(Plan.dosesAt(slot, Store.eyes()), true)
                Alarms.cancelNotification(c, slot)
            }
            Alarms.ACTION_SNOOZE -> {
                slot ?: return
                Alarms.cancelNotification(c, slot)
                Alarms.scheduleFollow(c, slot, maxOf(n, 1), 10)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Alarms.scheduleNext(c)
    }
}
