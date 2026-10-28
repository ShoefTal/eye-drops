package com.tal.eyedrops.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tal.eyedrops.data.Dose
import com.tal.eyedrops.data.Fmt
import com.tal.eyedrops.data.Med
import com.tal.eyedrops.data.MedType
import com.tal.eyedrops.data.Palette
import com.tal.eyedrops.data.PlanData
import com.tal.eyedrops.data.Schedule
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.reminders.Alarms
import com.tal.eyedrops.update.Updater
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/** Screens: home, settings, or the editor for one medication (null id = new). */
sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data class Edit(val medId: String?) : Screen()
    data class History(val medId: String) : Screen()
}

object Nav {
    var screen by mutableStateOf<Screen>(Screen.Home)
    /** A plan received from another phone, waiting for the user to confirm. */
    var pendingImport by mutableStateOf<PlanData?>(null)
    /** A measurement dose waiting for its value. */
    var measuring by mutableStateOf<Dose?>(null)
}

class MainActivity : ComponentActivity() {
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Alarms.scheduleNext(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        handleShare(intent)
        setContent { AppTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        Alarms.scheduleNext(this)
        Updater.checkAsync(this, interactive = false)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val plan = PlanData.fromShareText(text)
        if (plan == null) Toast.makeText(this, "לא נמצאה תוכנית בהודעה", Toast.LENGTH_LONG).show()
        else { Nav.pendingImport = plan; Nav.screen = Screen.Home }
    }
}

/** Reads a shared plan from the clipboard (the user copied the WhatsApp message). */
fun importFromClipboard(c: Context) {
    val cm = c.getSystemService(ClipboardManager::class.java)
    val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(c)?.toString() }.getOrNull()
    val plan = text?.let { PlanData.fromShareText(it) }
    if (plan == null) Toast.makeText(c, "לא נמצאה תוכנית. העתק את כל ההודעה שקיבלת ונסה שוב.", Toast.LENGTH_LONG).show()
    else Nav.pendingImport = plan
}

fun sharePlan(c: Context) {
    val p = Store.plan()
    if (p.meds.isEmpty()) { Toast.makeText(c, "אין עדיין תרופות בתוכנית", Toast.LENGTH_SHORT).show(); return }
    val text = "תוכנית תרופות (${p.meds.joinToString(", ") { it.name }})\n" +
        "לקבלה: לחיצה ארוכה על ההודעה > שיתוף > Toma.\n" +
        "או: העתק את ההודעה, ובאפליקציה Toma > הגדרות > קבל תוכנית.\n\n" + p.shareCode()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    c.startActivity(Intent.createChooser(send, "שלח תוכנית"))
}

@Composable
private fun Root() {
    val screen = Nav.screen
    BackHandler(enabled = screen != Screen.Home) {
        Nav.screen = if (screen is Screen.Edit || screen is Screen.History) Screen.Settings else Screen.Home
    }
    Surface(Modifier.fillMaxSize(), color = Bg) {
        when (screen) {
            Screen.Home -> HomeScreen()
            Screen.Settings -> SettingsScreen()
            is Screen.Edit -> MedEditor(screen.medId)
            is Screen.History -> HistoryScreen(screen.medId)
        }
    }
    Nav.pendingImport?.let { ImportDialog(it) }
    Nav.measuring?.let { MeasureDialog(it) }
}

@Composable
private fun ImportDialog(plan: PlanData) {
    val ctx = LocalContext.current
    val hasPlan = Store.plan().meds.isNotEmpty()
    AlertDialog(
        onDismissRequest = { Nav.pendingImport = null },
        title = { Text("לקבל את התוכנית?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.meds.forEach { m ->
                    val first = m.stages.minOfOrNull { it.start }; val last = m.stages.maxOfOrNull { it.end }
                    Text("• ${m.name}" + if (first != null && last != null) " (${Fmt.date(first)}–${Fmt.date(last)})" else "")
                }
                if (hasPlan) Text("התוכנית הנוכחית תוחלף.", color = Warn)
            }
        },
        confirmButton = {
            Button(onClick = {
                Store.savePlan(plan); Nav.pendingImport = null; Alarms.scheduleNext(ctx)
                Toast.makeText(ctx, "התוכנית התקבלה", Toast.LENGTH_SHORT).show()
            }) { Text("קבל") }
        },
        dismissButton = { TextButton(onClick = { Nav.pendingImport = null }) { Text("ביטול") } },
    )
}

// ------------------------------------------------------------ home

@Composable
private fun HomeScreen() {
    @Suppress("UNUSED_VARIABLE") val v = Store.version.intValue
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    val today = now.toLocalDate()
    val plan = Store.plan()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Toma", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            IconButton(onClick = { Nav.screen = Screen.Settings }) { Icon(Icons.Filled.Settings, "הגדרות", tint = Ink) }
        }

        if (plan.meds.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("עוד אין תוכנית תרופות", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("הוסף טיפות, כדורים, זריקות או מדידות לפי ההוראות של הרופא, או קבל תוכנית שמישהו שלח לך.", color = Muted)
                Button(onClick = { Nav.screen = Screen.Edit(null) }, modifier = Modifier.fillMaxWidth()) { Text("הוסף תרופה") }
                OutlinedButton(onClick = { importFromClipboard(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("קבל תוכנית ששלחו לי") }
            }
        } else {
            DayView(now, today)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayView(now: LocalDateTime, today: LocalDate) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    val plan = Store.plan()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NextCard(now)

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day = day.minusDays(1) }) { Icon(Icons.Filled.KeyboardArrowRight, "יום קודם", tint = Ink) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val title = when (day) {
                    today -> "היום"; today.plusDays(1) -> "מחר"; today.minusDays(1) -> "אתמול"; else -> Fmt.weekday(day)
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                Text(Fmt.dateYear(day), color = Muted, fontSize = 13.sp)
                if (day != today) TextButton(onClick = { day = today }) { Text("חזרה להיום") }
            }
            IconButton(onClick = { day = day.plusDays(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "יום הבא", tint = Ink) }
        }

        val slots = Schedule.slotsOn(day)
        if (slots.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("אין טיפות ביום הזה", color = Muted) }
        }
        val nextSlot = slots.firstOrNull { it.first.isAfter(now) && !it.second.all { d -> Store.isDone(d) } }?.first
        slots.forEach { (time, doses) -> SlotCard(time, doses, now, highlight = time == nextSlot) }

        val prn = plan.meds.filter { it.prn != null }
        if (prn.isNotEmpty()) {
            Text("לפי הצורך", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            prn.forEach { PrnCard(it, now) }
        }
        if (plan.meds.count { it.type == MedType.DROPS && it.prn == null } > 1) Text(
            "בטיפות שונות באותה שעה: 5 דקות בין טיפה לטיפה, לפי הסדר ברשימה.",
            color = Muted, fontSize = 13.sp,
        )
    }
}

@Composable
private fun NextCard(now: LocalDateTime) {
    val ctx = LocalContext.current
    val today = now.toLocalDate()
    val slots = Schedule.slotsOn(today)
    val due = slots.filter { !it.first.isAfter(now) && !it.second.all { d -> Store.isDone(d) } }
    val upcoming = Schedule.nextSlotAfter(now)
    fun names(ds: List<Dose>) = ds.map { it.med.name }.distinct().joinToString(" · ")
    val bg: Color; val label: String; val big: String; val what: String
    when {
        due.isNotEmpty() -> {
            val s = due.last()
            bg = Warn; label = if (due.size > 1) "ממתינות לסימון (${due.size})" else "עכשיו"
            big = Fmt.time(s.first.toLocalTime()); what = names(s.second)
        }
        upcoming != null -> {
            val mins = java.time.Duration.between(now, upcoming).toMinutes()
            val inText = if (mins >= 60) "בעוד ${mins / 60} ש׳ ${mins % 60} ד׳" else "בעוד $mins דקות"
            val dayText = when (upcoming.toLocalDate()) { today -> ""; today.plusDays(1) -> " · מחר"; else -> " · " + Fmt.date(upcoming.toLocalDate()) }
            bg = Brand; label = "הטיפות הבאות · $inText$dayText"
            big = Fmt.time(upcoming.toLocalTime()); what = names(Schedule.dosesAt(upcoming))
        }
        else -> { bg = Ok; label = "אין טיפות מתוכננות"; big = "✓"; what = "" }
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
                onClick = {
                    val s = due.last()
                    Store.setDone(s.second.filter { it.med.type != MedType.MEASURE }, true).forEach { Alarms.notifyRefill(ctx, it) }
                    Alarms.cancelNotification(ctx, s.first); Alarms.scheduleNext(ctx)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Warn),
            ) { Text("סמן הכול כבוצע", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SlotCard(time: LocalDateTime, doses: List<Dose>, now: LocalDateTime, highlight: Boolean) {
    val ctx = LocalContext.current
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            doses.groupBy { it.med.id }.values.forEach { ds ->
                val med = ds.first().med
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(Palette[med.color])))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(med.name, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
                        val sub = listOfNotNull(
                            med.doseText().ifEmpty { null },
                            if (med.rotation.isNotEmpty()) Store.siteFor(ds.first()) else null,
                            med.note.ifBlank { null },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ds.sortedBy { it.site }.forEach { d ->
                            val label = if (med.sites.size > 1 || (med.sites.size == 1 && med.rotation.isEmpty())) d.siteName else null
                            if (med.type == MedType.MEASURE) {
                                val v = Store.measurement(d)
                                OutlinedButton(onClick = { Nav.measuring = d }) { Text(v ?: "רשום") }
                            } else {
                                CheckChip(on = Store.isDone(d), label = label) {
                                    Store.toggle(d).forEach { Alarms.notifyRefill(ctx, it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrnCard(m: Med, now: LocalDateTime) {
    val ctx = LocalContext.current
    val prn = m.prn ?: return
    val log = Store.prnLog(m.id)
    val last = log.lastOrNull()
    val today = log.count { it.toLocalDate() == now.toLocalDate() }
    val nextOk = last?.plusHours(prn.minHours.toLong())
    val tooSoon = nextOk != null && nextOk.isAfter(now)
    val maxed = prn.maxPerDay > 0 && today >= prn.maxPerDay
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(Palette[m.color])))
            Spacer(Modifier.width(8.dp))
            Text(m.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
            Text(m.doseText(), color = Muted, fontSize = 13.sp)
        }
        val status = buildString {
            append(if (last == null) "עוד לא נלקח" else "נלקח לאחרונה: ${if (last.toLocalDate() == now.toLocalDate()) "היום" else Fmt.date(last.toLocalDate())} ${Fmt.time(last.toLocalTime())}")
            append(" · היום: $today")
            if (prn.maxPerDay > 0) append(" מתוך ${prn.maxPerDay}")
        }
        Text(status, color = Muted, fontSize = 13.sp)
        if (tooSoon) Text("אפשר שוב מ־${Fmt.time(nextOk!!.toLocalTime())}", color = Warn, fontSize = 13.sp)
        if (maxed) Text("הגעת למקסימום היומי", color = Warn, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (Store.takePrn(m, LocalDateTime.now())) Alarms.notifyRefill(ctx, m)
            }) { Text("לקחתי עכשיו") }
            if (last != null) TextButton(onClick = { Store.undoLastPrn(m) }) { Text("בטל אחרון") }
        }
    }
}

@Composable
private fun MeasureDialog(d: Dose) {
    var value by remember(d.key) { mutableStateOf(Store.measurement(d) ?: "") }
    AlertDialog(
        onDismissRequest = { Nav.measuring = null },
        title = { Text("${d.med.name} · ${Fmt.time(d.time.toLocalTime())}") },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it.take(40) }, singleLine = true,
                label = { Text("תוצאה (למשל 130/85)") }, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { Store.setMeasurement(d, value); Nav.measuring = null }) { Text("שמור") } },
        dismissButton = { TextButton(onClick = { Nav.measuring = null }) { Text("ביטול") } },
    )
}

@Composable
private fun HistoryScreen(medId: String) {
    @Suppress("UNUSED_VARIABLE") val v = Store.version.intValue
    val med = Store.med(medId)
    val entries = Store.measurementsOf(medId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.screen = Screen.Settings }) { Icon(Icons.Filled.ArrowBack, "חזרה", tint = Ink) }
            Text("היסטוריה: ${med?.name ?: ""}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        if (entries.isEmpty()) Text("עוד אין מדידות.", color = Muted)
        entries.forEach { (key, value) ->
            val parts = key.split("|")
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text((date?.let { Fmt.weekday(it) + " " + Fmt.dateYear(it) } ?: parts[0]) + " · " + parts.getOrElse(1) { "" },
                    color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
