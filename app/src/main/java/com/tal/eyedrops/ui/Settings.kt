package com.tal.eyedrops.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tal.eyedrops.data.Fmt
import com.tal.eyedrops.data.Palette
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.reminders.Alarms
import com.tal.eyedrops.update.Updater
import java.time.LocalDateTime

@Composable
fun SettingsScreen() {
    @Suppress("UNUSED_VARIABLE") val v = Store.version.intValue
    val ctx = LocalContext.current
    val plan = Store.plan()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.screen = Screen.Home }) { Icon(Icons.Filled.ArrowBack, "חזרה", tint = Ink) }
            Text("הגדרות", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        }

        Section("התוכנית") {
            if (plan.meds.isEmpty()) Text("עוד אין תרופות.", color = Muted)
            plan.meds.forEachIndexed { i, m ->
                Row(
                    Modifier.fillMaxWidth().clickable { Nav.screen = Screen.Edit(m.id) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(Palette[m.color])))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${m.name}", color = Ink, fontWeight = FontWeight.SemiBold)
                        val first = m.stages.minOfOrNull { it.start }; val last = m.stages.maxOfOrNull { it.end }
                        val sub = buildList {
                            add(m.type.label)
                            if (m.prn != null) add("לפי הצורך")
                            else if (first != null && last != null) add("${Fmt.date(first)}–${Fmt.dateYear(last)}")
                            Store.stock(m.id)?.let { add("מלאי ${Fmt.num(it)}") }
                        }.joinToString(" · ")
                        Text(sub, color = Muted, fontSize = 13.sp)
                        if (m.type == com.tal.eyedrops.data.MedType.MEASURE) TextButton(onClick = { Nav.screen = Screen.History(m.id) }) {
                            Text("היסטוריה")
                        }
                    }
                    IconButton(onClick = { Store.moveMed(m.id, -1); Alarms.scheduleNext(ctx) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "הזז למעלה", tint = if (i > 0) Ink else Line)
                    }
                    IconButton(onClick = { Store.moveMed(m.id, 1); Alarms.scheduleNext(ctx) }, enabled = i < plan.meds.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, "הזז למטה", tint = if (i < plan.meds.size - 1) Ink else Line)
                    }
                }
            }
            if (plan.meds.size > 1) Text("הסדר ברשימה הוא הסדר באותה שעה.", color = Muted, fontSize = 12.sp)
            Button(onClick = { Nav.screen = Screen.Edit(null) }) { Text("הוסף תרופה או מדידה") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { sharePlan(ctx) }) { Text("שלח תוכנית") }
                OutlinedButton(onClick = { importFromClipboard(ctx) }) { Text("קבל תוכנית") }
            }
        }

        Section("התראה") {
            Segmented(listOf("קולית", "שקטה (רטט)"), if (Store.soundOn()) 0 else 1) { Store.setSound(it == 0) }
            if (Store.soundOn()) ToneRow(ctx)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("תזכורת חוזרת", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("אם לא סומן, מזכיר שוב אחרי 15 ו־30 דקות", color = Muted, fontSize = 13.sp)
                }
                Switch(checked = Store.followUp(), onCheckedChange = { Store.setFollowUp(it) })
            }
            TestRow(ctx)
            ReliabilityRow(ctx)
        }

        Section("עדכונים") { UpdateRow(ctx) }

        Spacer(Modifier.height(24.dp))
    }
}

private fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

@Composable
private fun UpdateRow(ctx: Context) {
    Store.version.intValue // recompose when data changes
    var checking by remember { mutableStateOf(false) }
    Text("גרסה ${appVersion(ctx)}", color = Ink, fontWeight = FontWeight.SemiBold)
    if (!Updater.enabled()) {
        Text("בגרסה הזו אין עדכון אוטומטי.", color = Muted, fontSize = 13.sp)
        return
    }
    Text("האפליקציה בודקת עדכונים פעם ביום ומתקינה אותם.", color = Muted, fontSize = 13.sp)
    if (!Updater.canInstall(ctx)) {
        Text("צריך לאשר פעם אחת שהאפליקציה תוכל לעדכן את עצמה.", color = Warn, fontSize = 13.sp)
        Button(onClick = {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
            }
        }) { Text("אפשר עדכונים") }
    }
    OutlinedButton(enabled = !checking, onClick = {
        checking = true
        Updater.checkAsync(ctx, interactive = true) { checking = false }
    }) { Text(if (checking) "בודק…" else "בדוק עדכון עכשיו") }
}

@Composable
private fun ToneRow(ctx: Context) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Store.setTone(uri?.toString())
            Alarms.createChannels(ctx)
        }
    }
    val tone = Alarms.currentTone()
    val name = remember(Store.version.intValue) {
        runCatching { tone?.let { RingtoneManager.getRingtone(ctx, it)?.getTitle(ctx) } }.getOrNull() ?: "ברירת מחדל"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("צלצול", color = Ink, fontWeight = FontWeight.SemiBold)
            Text(name, color = Muted, fontSize = 13.sp)
        }
        OutlinedButton(onClick = {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "צלצול לתזכורות")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Alarms.defaultTone())
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, tone)
            runCatching { picker.launch(i) }
        }) { Text("בחר צלצול") }
    }
}

@Composable
private fun TestRow(ctx: Context) {
    Store.version.intValue // recompose when data changes
    val testAt = Store.testAt()?.takeIf { it.isAfter(LocalDateTime.now()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("בדיקה", color = Ink, fontWeight = FontWeight.SemiBold)
        if (testAt != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "בדיקה מתוזמנת ל־${Fmt.weekday(testAt.toLocalDate())} ${Fmt.date(testAt.toLocalDate())} ב־${Fmt.time(testAt.toLocalTime())}",
                    color = Brand, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { Alarms.cancelTest(ctx) }) { Text("בטל") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Alarms.scheduleTest(ctx, LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0)) }) {
                Text("בעוד דקה")
            }
            OutlinedButton(onClick = {
                val now = LocalDateTime.now()
                DatePickerDialog(ctx, { _, y, m, d ->
                    TimePickerDialog(ctx, { _, h, min ->
                        val at = LocalDateTime.of(y, m + 1, d, h, min)
                        if (at.isAfter(LocalDateTime.now())) Alarms.scheduleTest(ctx, at)
                        else Toast.makeText(ctx, "בחר זמן עתידי", Toast.LENGTH_SHORT).show()
                    }, now.hour, now.minute, true).show()
                }, now.year, now.monthValue - 1, now.dayOfMonth).show()
            }) { Text("בחר תאריך ושעה") }
        }
        Text("הבדיקה מצלצלת בדיוק כמו התראה אמיתית, גם כשהמסך כבוי.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ReliabilityRow(ctx: Context) {
    Store.version.intValue // recompose when data changes
    val pm = ctx.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("כדי שההתראות לא יתעכבו, אפשר לאפליקציה לפעול ברקע בלי הגבלת סוללה.", color = Warn, fontSize = 13.sp)
            Button(onClick = {
                @SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
                runCatching { ctx.startActivity(i) }
            }) { Text("אפשר פעולה ברקע") }
        }
    }
    if (!Alarms.canExact(ctx) && Build.VERSION.SDK_INT >= 31) {
        Button(onClick = {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
        }) { Text("אפשר התראות בזמן מדויק") }
    }
}

