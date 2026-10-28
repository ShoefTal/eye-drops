package com.tal.eyedrops.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.media.RingtoneManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tal.eyedrops.data.Dose
import com.tal.eyedrops.data.Drug
import com.tal.eyedrops.data.Fmt
import com.tal.eyedrops.data.Plan
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.reminders.Alarms
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Alarms.scheduleNext(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AppTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        Alarms.scheduleNext(this)
    }
}

@Composable
private fun Root() {
    var settings by remember { mutableStateOf(false) }
    BackHandler(enabled = settings) { settings = false }
    Surface(Modifier.fillMaxSize(), color = Bg) {
        if (settings) SettingsScreen(onBack = { settings = false })
        else HomeScreen(onSettings = { settings = true })
    }
}

// ------------------------------------------------------------ home

@Composable
private fun HomeScreen(onSettings: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Store.version.intValue
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    var day by remember { mutableStateOf(LocalDate.now()) }
    val eyes = Store.eyes()
    val today = now.toLocalDate()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("טיפות עיניים", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    eyes.joinToString(" · ") { (if (eyes.size > 1) it.name + ": " else "") + Plan.phaseText(today, it) },
                    color = Muted, fontSize = 14.sp,
                )
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "הגדרות", tint = Ink) }
        }

        NextCard(now)

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day = day.minusDays(1) }) { Icon(Icons.Filled.KeyboardArrowRight, "יום קודם", tint = Ink) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val title = when (day) {
                    today -> "היום"
                    today.plusDays(1) -> "מחר"
                    today.minusDays(1) -> "אתמול"
                    else -> Fmt.weekday(day)
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                Text(Fmt.dateYear(day), color = Muted, fontSize = 13.sp)
                if (day != today) TextButton(onClick = { day = today }) { Text("חזרה להיום") }
            }
            IconButton(onClick = { day = day.plusDays(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "יום הבא", tint = Ink) }
        }

        val slots = Plan.slotsOn(day, eyes)
        if (slots.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("אין טיפות ביום הזה", color = Muted) }
        }
        val nextSlot = slots.firstOrNull { it.first.isAfter(now) && !it.second.all { d -> Store.isDone(d) } }?.first
        slots.forEach { (time, doses) -> SlotCard(time, doses, eyes.size, now, highlight = time == nextSlot) }

        Text(
            "לפי סדר: Sterodex, Vigamox, Bromsite ובסוף Bephanten. 5 דקות בין טיפה לטיפה.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NextCard(now: LocalDateTime) {
    val ctx = LocalContext.current
    val eyes = Store.eyes()
    val today = now.toLocalDate()
    val slots = Plan.slotsOn(today, eyes)
    val due = slots.filter { !it.first.isAfter(now) && !it.second.all { d -> Store.isDone(d) } }
    val upcoming = Plan.nextSlotAfter(now, eyes)
    val (bg, label, big, what) = when {
        due.isNotEmpty() -> {
            val s = due.last()
            Quad(Warn, if (due.size > 1) "ממתינות לסימון (${due.size})" else "עכשיו", Fmt.time(s.first.toLocalTime()),
                s.second.map { it.drug }.distinct().joinToString(" · ") { it.label })
        }
        upcoming != null -> {
            val mins = java.time.Duration.between(now, upcoming).toMinutes()
            val inText = if (mins >= 60) "בעוד ${mins / 60} ש׳ ${mins % 60} ד׳" else "בעוד $mins דקות"
            val dayText = if (upcoming.toLocalDate() == today) "" else " · " + (if (upcoming.toLocalDate() == today.plusDays(1)) "מחר" else Fmt.date(upcoming.toLocalDate()))
            Quad(Brand, "הטיפות הבאות · $inText$dayText", Fmt.time(upcoming.toLocalTime()),
                Plan.dosesAt(upcoming, eyes).map { it.drug }.distinct().joinToString(" · ") { it.label })
        }
        else -> Quad(Ok, "הטיפול הסתיים", "✓", "")
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        Text(big, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        if (what.isNotEmpty()) Text(what, color = Color.White, fontSize = 15.sp)
        if (due.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { val s = due.last(); Store.setDone(s.second, true); Alarms.scheduleNext(ctx) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Warn),
            ) { Text("סמן הכול כבוצע", fontWeight = FontWeight.Bold) }
        }
    }
}

private data class Quad(val a: Color, val b: String, val c: String, val d: String)


@Composable
private fun SlotCard(time: LocalDateTime, doses: List<Dose>, eyeCount: Int, now: LocalDateTime, highlight: Boolean) {
    val eyes = Store.eyes()
    val allDone = doses.all { Store.isDone(it) }
    val missed = !allDone && !time.isAfter(now)
    val border = when { highlight -> Brand; missed -> Warn; else -> Line }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (allDone) DoneBg else Color.White)
            .border(if (highlight || missed) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(64.dp)) {
            Text(Fmt.time(time.toLocalTime()), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (allDone) Muted else Ink)
            if (missed) Text("לא סומן", color = Warn, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            doses.groupBy { it.drug }.toList().sortedBy { it.first.ordinal }.forEach { (drug, ds) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(drug.color)))
                    Spacer(Modifier.width(8.dp))
                    Text(drug.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ds.sortedBy { it.eye }.forEach { d ->
                            CheckChip(
                                on = Store.isDone(d),
                                label = if (eyeCount > 1) eyes[d.eye].name else null,
                            ) { Store.toggle(d) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckChip(on: Boolean, label: String?, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
            .background(if (on) OkSoft else Color.Transparent)
            .border(1.5.dp, if (on) Ok else Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = Ok, modifier = Modifier.size(20.dp))
        val text = label ?: if (on) "" else "סמן"
        if (text.isNotEmpty()) Text(text, color = if (on) Ok else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------ settings

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Store.version.intValue
    val ctx = LocalContext.current
    val eyes = Store.eyes()
    val changed = { Alarms.scheduleNext(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "חזרה", tint = Ink) }
            Text("הגדרות", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        }

        Section("עיניים") {
            Segmented(listOf("עין אחת", "שתי עיניים"), eyes.size - 1) { Store.setEyeCount(it + 1); changed() }
            eyes.forEachIndexed { i, eye ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (i == 0) "עין ראשונה" else "עין שנייה", color = Muted, fontSize = 13.sp)
                    Segmented(listOf("ימין", "שמאל"), if (eye.name == "שמאל") 1 else 0) {
                        Store.setEyeName(i, if (it == 0) "ימין" else "שמאל")
                    }
                    OutlinedButton(onClick = {
                        DatePickerDialog(ctx, { _, y, m, d -> Store.setEyeStart(i, LocalDate.of(y, m + 1, d)); changed() },
                            eye.start.year, eye.start.monthValue - 1, eye.start.dayOfMonth).show()
                    }) { Text("יום 8 הפעמים: ${Fmt.weekday(eye.start)} ${Fmt.dateYear(eye.start)}") }
                }
            }
        }

        Section("התראה") {
            Segmented(listOf("קולית", "שקטה (רטט)"), if (Store.soundOn()) 0 else 1) { Store.setSound(it == 0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("תזכורת חוזרת", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("אם לא סומן, מזכיר שוב אחרי 15 ו־30 דקות", color = Muted, fontSize = 13.sp)
                }
                Switch(checked = Store.followUp(), onCheckedChange = { Store.setFollowUp(it) })
            }
            if (Store.soundOn()) ToneRow(ctx)
            TestRow(ctx)
            ReliabilityRow(ctx)
        }

        Section("התוכנית") {
            val e = eyes[0]
            Drug.values().forEach { drug ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(drug.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(drug.label, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Plan.rules.filter { it.drug == drug }.forEach { r ->
                        Text(
                            "${Fmt.date(r.from(e.start))}–${Fmt.date(r.to(e.start))} · ${r.times.size} פעמים: " +
                                r.times.joinToString(", ") { Fmt.time(it) },
                            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp),
                        )
                    }
                }
            }
            if (eyes.size > 1) Text("התאריכים לפי העין הראשונה.", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReliabilityRow(ctx: Context) {
    val pm = ctx.getSystemService(PowerManager::class.java)
    val unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    if (!unrestricted) {
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
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "צלצול לטיפות")
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp)),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.background(if (i == selected) Brand else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (i == selected) Color.White else Ink, textAlign = TextAlign.Center)
            }
        }
    }
}
