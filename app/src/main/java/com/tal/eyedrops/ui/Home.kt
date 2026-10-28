package com.tal.eyedrops.ui

import android.content.Context
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tal.eyedrops.data.Dose
import com.tal.eyedrops.data.Fmt
import com.tal.eyedrops.data.MedType
import com.tal.eyedrops.data.Palette
import com.tal.eyedrops.data.Schedule
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.reminders.Alarms
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

private fun hasSeveral(ds: List<Dose>) = ds.size > 1

/** What is due for one med at one time, in words: "טיפה אחת · עין ימין · עם אוכל". */
private fun subLine(ds: List<Dose>): String {
    val m = ds.first().med
    return listOfNotNull(
        m.doseText().ifEmpty { null },
        ds.mapNotNull { Store.siteFor(it) }.distinct().joinToString(", ").ifEmpty { null },
        m.note.ifBlank { null },
    ).joinToString(" · ")
}

private fun markSlot(ctx: Context, slot: LocalDateTime, doses: List<Dose>) {
    Store.setDone(doses.filter { it.med.type != MedType.MEASURE }, true).forEach { Alarms.notifyRefill(ctx, it) }
    Alarms.cancelNotification(ctx, slot)
    Alarms.scheduleNext(ctx)
}

// ------------------------------------------------------------ home (option A)

@Composable
fun HomeScreen() {
    Store.version.intValue // recompose when data changes
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = LocalDateTime.now() } }
    val today = now.toLocalDate()
    val plan = Store.plan()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("היום, ${Fmt.weekday(today).removePrefix("יום ")}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(Fmt.dateYear(today), fontSize = 19.sp, color = Muted)
            }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(CardBg)
                    .border(BorderW.dp, Line, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { Nav.openSettings() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Settings, "הגדרות", tint = Ink, modifier = Modifier.size(30.dp))
                }
            }
        }

        if (plan.meds.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardBg).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("עוד אין תוכנית תרופות", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("הוסף טיפות, כדורים, זריקות או מדידות, או קבל תוכנית שמישהו שלח לך.", fontSize = 18.sp, color = Muted)
                BigButton("הוסף תרופה") { Nav.screen = Screen.Edit(null) }
                OutlinedButton(onClick = { importFromClipboard(ctx) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("קבל תוכנית ששלחו לי", fontSize = 20.sp)
                }
            }
        } else {
            NowCard(now)
            DayList(now)
            val prn = plan.meds.filter { it.prn != null }
            if (prn.isNotEmpty()) {
                Text("לפי הצורך", color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                prn.forEach { PrnCard(it, now) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BigButton(label: String, color: Color = Brand, check: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        if (check) { Icon(Icons.Filled.Check, null, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SecondButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

/** The one thing to do now, or the next thing coming up. */
@Composable
private fun NowCard(now: LocalDateTime) {
    Store.version.intValue
    val ctx = LocalContext.current
    val today = now.toLocalDate()
    val due = Schedule.slotsOn(today).filter { !it.first.isAfter(now) && !it.second.all { d -> Store.isDone(d) } }
    val current = due.lastOrNull()
    val upcoming = if (current == null) Schedule.nextSlotAfter(now) else null

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (current != null) NowBg else CardBg)
            .border(if (current != null) 3.dp else BorderW.dp, if (current != null) Brand else Line, RoundedCornerShape(28.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            current != null -> {
                val (slot, doses) = current
                Text("עכשיו", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand)
                Text(Fmt.time(slot.toLocalTime()), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 66.sp)
                doses.groupBy { it.med.id }.values.forEach { ds -> MedLine(ds, nameSize = 30) }
                val pending = doses.filter { !Store.isDone(it) }
                if (hasSeveral(pending.filter { it.med.type != MedType.MEASURE })) {
                    BigButton("התחל", check = false) { Nav.screen = Screen.Focus(slot.toString()) }
                } else if (pending.any { it.med.type == MedType.MEASURE }) {
                    BigButton("רשום מדידה") { Nav.measuring = pending.first { it.med.type == MedType.MEASURE } }
                } else {
                    BigButton("לקחתי", check = true) { markSlot(ctx, slot, doses) }
                }
                SecondButton("תזכיר לי בעוד 10 דקות") {
                    Alarms.cancelNotification(ctx, slot); Alarms.scheduleFollow(ctx, slot, 1, 10)
                    android.widget.Toast.makeText(ctx, "נזכיר שוב בעוד 10 דקות", android.widget.Toast.LENGTH_SHORT).show()
                }
                if (due.size > 1) Text("יש עוד ${due.size - 1} מועדים קודמים שלא סומנו, ברשימה למטה.", fontSize = 17.sp, color = Warn)
            }
            upcoming != null -> {
                val mins = Duration.between(now, upcoming).toMinutes()
                val inText = when {
                    upcoming.toLocalDate() != today -> if (upcoming.toLocalDate() == today.plusDays(1)) "מחר" else Fmt.date(upcoming.toLocalDate())
                    mins >= 60 -> "בעוד ${mins / 60} ש׳ ${mins % 60} ד׳"
                    else -> "בעוד $mins דקות"
                }
                Text("הבא · $inText", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Muted)
                Text(Fmt.time(upcoming.toLocalTime()), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 58.sp)
                Schedule.dosesAt(upcoming).groupBy { it.med.id }.values.forEach { ds -> MedLine(ds, nameSize = 26) }
            }
            else -> {
                Text("אין תרופות מתוכננות", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
        }
    }
}

@Composable
private fun MedLine(ds: List<Dose>, nameSize: Int) {
    val med = ds.first().med
    val done = ds.all { Store.isDone(it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 44.dp).clip(RoundedCornerShape(6.dp)).background(Color(Palette[med.color])))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(med.name, fontSize = nameSize.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = (nameSize + 4).sp)
            val sub = subLine(ds)
            if (sub.isNotEmpty()) Text(sub, fontSize = 20.sp, color = Muted)
        }
        if (done) Icon(Icons.Filled.Check, "בוצע", tint = Ok, modifier = Modifier.size(34.dp))
    }
}

/** All times of a day, with big check boxes. Arrows move to other days. */
@Composable
private fun DayList(now: LocalDateTime) {
    Store.version.intValue
    val ctx = LocalContext.current
    var day by remember { mutableStateOf(now.toLocalDate()) }
    val today = now.toLocalDate()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day = day.minusDays(1) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.KeyboardArrowRight, "יום קודם", tint = Ink, modifier = Modifier.size(36.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val title = when (day) { today -> "כל היום"; today.plusDays(1) -> "מחר"; today.minusDays(1) -> "אתמול"; else -> Fmt.weekday(day) }
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                if (day != today) TextButton(onClick = { day = today }) { Text("חזרה להיום", fontSize = 17.sp) }
                else Text(Fmt.dateYear(day), fontSize = 17.sp, color = Muted)
            }
            IconButton(onClick = { day = day.plusDays(1) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.KeyboardArrowLeft, "יום הבא", tint = Ink, modifier = Modifier.size(36.dp))
            }
        }
        val slots = Schedule.slotsOn(day)
        if (slots.isEmpty()) Text("אין תרופות ביום הזה", fontSize = 19.sp, color = Muted)
        slots.forEach { (time, doses) ->
            val allDone = doses.all { Store.isDone(it) }
            val missed = !allDone && !time.isAfter(now)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (allDone) DoneBg else CardBg)
                    .border(if (missed) 3.dp else BorderW.dp, if (missed) Warn else Line, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.time(time.toLocalTime()), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (allDone) Muted else Ink, modifier = Modifier.weight(1f))
                    when {
                        allDone -> Text("בוצע ✓", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ok)
                        missed -> Text("לא סומן", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Warn)
                    }
                }
                doses.groupBy { it.med.id }.values.forEach { ds ->
                    val med = ds.first().med
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 12.dp, height = 40.dp).clip(RoundedCornerShape(5.dp)).background(Color(Palette[med.color])))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(med.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                            val sub = subLine(ds)
                            if (sub.isNotEmpty()) Text(sub, fontSize = 17.sp, color = Muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ds.sortedBy { it.site }.forEach { d ->
                                if (med.type == MedType.MEASURE) {
                                    OutlinedButton(onClick = { Nav.measuring = d }, modifier = Modifier.height(56.dp)) {
                                        Text(Store.measurement(d) ?: "רשום", fontSize = 18.sp)
                                    }
                                } else {
                                    val label = if (med.sites.size > 1) d.siteName?.split(" ")?.lastOrNull() else null
                                    CheckChip(on = Store.isDone(d), label = label) {
                                        Store.toggle(d).forEach { Alarms.notifyRefill(ctx, it) }
                                        if (Store.slotDone(time)) Alarms.cancelNotification(ctx, time)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ step by step (option C)

/** Speaks Hebrew when the phone has a Hebrew voice; silently does nothing otherwise. */
private class Speaker(ctx: Context) {
    private var ready = false
    private var pending: String? = null
    private lateinit var tts: TextToSpeech
    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts.setLanguage(Locale("he", "IL"))
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                pending?.let { if (ready) say(it) }; pending = null
            }
        }
    }
    fun say(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "toma")
    }
    fun close() { runCatching { tts.stop(); tts.shutdown() } }
}

private const val WAIT_SECONDS = 5 * 60L

@Composable
fun FocusScreen(slotText: String) {
    Store.version.intValue
    val ctx = LocalContext.current
    val slot = remember(slotText) { runCatching { LocalDateTime.parse(slotText) }.getOrNull() }
    if (slot == null) {
        LaunchedEffect(Unit) { Nav.screen = Screen.Home }
        return
    }
    val speaker = remember { Speaker(ctx) }
    DisposableEffect(Unit) { onDispose { speaker.close() } }
    var lastGiven by remember(slotText) { mutableStateOf<LocalDateTime?>(null) }
    var lastWasDrop by remember(slotText) { mutableStateOf(false) }
    var skipWait by remember(slotText) { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = LocalDateTime.now() } }

    val doses = Schedule.dosesAt(slot)
    val steps = doses.filter { it.med.type != MedType.MEASURE }
    val current = steps.firstOrNull { !Store.isDone(it) }
    val measures = doses.filter { it.med.type == MedType.MEASURE && !Store.isDone(it) }
    val stepNo = steps.indexOf(current) + 1

    val waitLeft = lastGiven?.let { WAIT_SECONDS - Duration.between(it, now).seconds } ?: 0L
    val mustWait = current != null && lastWasDrop && current.med.type == MedType.DROPS && waitLeft > 0 && !skipWait

    fun spoken(d: Dose) = listOfNotNull(d.med.name, d.med.doseText().ifEmpty { null }, Store.siteFor(d), d.med.note.ifBlank { null })
        .joinToString(", ")
    LaunchedEffect(current?.key, mustWait) {
        if (current != null && !mustWait && Store.voice()) speaker.say("עכשיו: " + spoken(current))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("התרופות של ${Fmt.time(slot.toLocalTime())}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        if (steps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEach { d ->
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(if (Store.isDone(d)) Brand else Line))
                }
            }
        }

        if (current == null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(OkSoft).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Check, null, tint = Ok, modifier = Modifier.size(72.dp))
                Text("סיימת, כל הכבוד", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
            measures.firstOrNull()?.let { m -> BigButton("רשום ${m.med.name}") { Nav.measuring = m } }
            BigButton("חזרה למסך הראשי", color = Brand) { Nav.screen = Screen.Home }
            LaunchedEffect(Unit) { if (Store.voice() && steps.isNotEmpty()) speaker.say("סיימת. כל הכבוד.") }
        } else {

        Text("שלב $stepNo מתוך ${steps.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Muted)
        val med = current.med
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(Palette[med.color])).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (mustWait) "הבא בתור" else "עכשיו לתת", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(med.name, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 48.sp)
            med.doseText().takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
        Store.siteFor(current)?.let { site ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardBg)
                    .border(BorderW.dp, Line, RoundedCornerShape(22.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(site, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
        }
        if (med.note.isNotBlank()) Text(med.note, fontSize = 22.sp, color = Ink)

        if (mustWait) {
            val m = waitLeft / 60; val s = waitLeft % 60
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardBg)
                    .border(2.dp, Warn, RoundedCornerShape(22.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ממתינים בין טיפות", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Warn)
                Text(String.format(Locale.US, "%d:%02d", m, s), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("נצלצל כשאפשר להמשיך", fontSize = 19.sp, color = Muted)
            }
            TextButton(onClick = { skipWait = true }) { Text("לא לחכות", fontSize = 19.sp) }
        } else {
            BigButton("נתתי", check = true) {
                Store.setDone(listOf(current), true).forEach { Alarms.notifyRefill(ctx, it) }
                lastGiven = LocalDateTime.now(); lastWasDrop = med.type == MedType.DROPS; skipWait = false
                val next = steps.firstOrNull { !Store.isDone(it) }
                if (next == null) {
                    Alarms.cancelNotification(ctx, slot); Alarms.scheduleNext(ctx)
                } else if (lastWasDrop && next.med.type == MedType.DROPS) {
                    Alarms.scheduleFollow(ctx, slot, 1, 5) // rings again when the wait is over
                }
            }
        }
        SecondButton("הקרא שוב") { speaker.say(spoken(current)) }
        SecondButton("לא עכשיו") {
            Alarms.cancelNotification(ctx, slot); Alarms.scheduleFollow(ctx, slot, 1, 10)
            Nav.screen = Screen.Home
        }
        }
    }
}

// ------------------------------------------------------------ caregiver PIN

@Composable
fun PinDialog() {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { Nav.askPin = false },
        title = { Text("קוד מטפל") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ההגדרות נעולות. הכנס את הקוד כדי לשנות.")
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); wrong = false },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (wrong) Text("קוד שגוי", color = Warn)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pin == Store.pin()) { Nav.unlocked = true; Nav.askPin = false; Nav.screen = Screen.Settings } else wrong = true
            }) { Text("פתח") }
        },
        dismissButton = { TextButton(onClick = { Nav.askPin = false }) { Text("ביטול") } },
    )
}

