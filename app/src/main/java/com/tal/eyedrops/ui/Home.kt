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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

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

// ------------------------------------------------------------ home: the wheel

/** One row of the wheel: a day label, or one reminder time with its doses. */
private sealed class Entry {
    data class Day(val date: LocalDate) : Entry()
    data class Slot(val time: LocalDateTime, val doses: List<Dose>, val state: SlotState) : Entry()
    data object Prn : Entry()
}
private enum class SlotState { DONE, SKIPPED, NEXT, LATER }

private fun slotFull(doses: List<Dose>) = doses.isNotEmpty() && doses.all { Store.isDone(it) }

/**
 * Yesterday to the day after tomorrow. The "next" time is the earliest one not taken
 * (and not skipped) since the plan was loaded; it stays next until it is marked.
 */
private fun buildRows(now: LocalDateTime): Pair<List<Entry>, Int> {
    val since = Store.since()
    val today = now.toLocalDate()
    val slots = ArrayList<Entry.Slot>()
    var next: LocalDateTime? = null
    for (d in -1..2) {
        val date = today.plusDays(d.toLong())
        Schedule.slotsOn(date).forEach { (t, doses) ->
            val full = slotFull(doses); val skipped = Store.isSkipped(t)
            if (t.isBefore(since) && !full) return@forEach
            val state = when {
                full -> SlotState.DONE
                skipped -> SlotState.SKIPPED
                next == null -> { next = t; SlotState.NEXT }
                else -> SlotState.LATER
            }
            slots.add(Entry.Slot(t, doses, state))
        }
    }
    if (next == null) Schedule.nextSlotAfter(today.plusDays(2).atTime(23, 59))?.let { t ->
        slots.add(Entry.Slot(t, Schedule.dosesAt(t), SlotState.NEXT))
    }
    // Done and skipped stay above the next time even when they are later (marked early).
    val nextT = slots.firstOrNull { it.state == SlotState.NEXT }?.time
    val ordered = slots.sortedWith(compareBy({ if (it.state == SlotState.DONE || it.state == SlotState.SKIPPED) 0 else 1 }, { it.time }))
        .let { list -> if (nextT == null) list else list.sortedBy { if (it.state == SlotState.DONE || it.state == SlotState.SKIPPED) -1 else if (it.time == nextT) 0 else 1 } }
    val rows = ArrayList<Entry>()
    var lastDay: LocalDate? = null
    ordered.forEach { s ->
        val d = s.time.toLocalDate()
        if (d != lastDay) { rows.add(Entry.Day(d)); lastDay = d }
        rows.add(s)
    }
    if (Store.plan().meds.any { it.prn != null }) rows.add(Entry.Prn)
    return rows to rows.indexOfFirst { it is Entry.Slot && it.state == SlotState.NEXT }
}

private fun dayLabel(d: LocalDate, today: LocalDate) = when (d) {
    today -> "היום"; today.plusDays(1) -> "מחר"; today.minusDays(1) -> "אתמול"; else -> Fmt.weekday(d)
} + " · " + Fmt.dateYear(d)

@Composable
fun HomeScreen() {
    Store.version.intValue // recompose when data changes
    val ctx = LocalContext.current
    val pickPlan = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { importFromFile(ctx, it) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = LocalDateTime.now() } }
    val today = now.toLocalDate()
    val plan = Store.plan()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Brand), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.tal.eyedrops.R.drawable.ic_launcher_fg), null,
                    modifier = Modifier.size(46.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                val greet = when (now.hour) { in 5..11 -> "בוקר טוב"; in 12..16 -> "צהריים טובים"; in 17..20 -> "ערב טוב"; else -> "לילה טוב" }
                Text(greet, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("${Fmt.weekday(today)}, ${Fmt.dateYear(today)}", fontSize = 18.sp, color = Muted)
            }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(CardBg)
                    .border(if (Store.highContrast.value) 3.dp else 1.dp, Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { Nav.openSettings() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Settings, "הגדרות", tint = Ink, modifier = Modifier.size(30.dp))
                }
            }
        }

        if (plan.meds.isEmpty()) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardBg).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("עוד אין תוכנית תרופות", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("הוסף טיפות, כדורים, זריקות או מדידות, או קבל תוכנית שמישהו שלח לך.", fontSize = 18.sp, color = Muted)
                BigButton("הוסף תרופה") { Nav.screen = Screen.Edit(null) }
                OutlinedButton(onClick = { importFromClipboard(ctx) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("הדבק תוכנית שהעתקתי", fontSize = 20.sp)
                }
                OutlinedButton(onClick = { pickPlan.launch(arrayOf("text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("טען תוכנית מקובץ", fontSize = 20.sp)
                }
            }
        } else {
            Wheel(now)
        }
    }
}

/** The timeline as a wheel: the next time sits in the middle; taken times above, later times below. */
@Composable
private fun Wheel(now: LocalDateTime) {
    Store.version.intValue
    val ctx = LocalContext.current
    val (rows, nextIndex) = buildRows(now)
    val nextKey = (rows.getOrNull(nextIndex) as? Entry.Slot)?.time
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var undo by remember { mutableStateOf<Entry.Slot?>(null) }
    var skipAsk by remember { mutableStateOf<Entry.Slot?>(null) }

    suspend fun center(animate: Boolean) {
        if (nextIndex < 0) return
        if (state.layoutInfo.visibleItemsInfo.none { it.index == nextIndex }) state.scrollToItem(nextIndex)
        val li = state.layoutInfo
        val info = li.visibleItemsInfo.firstOrNull { it.index == nextIndex } ?: return
        val top = li.viewportStartOffset + li.beforeContentPadding
        val bottom = li.viewportEndOffset - li.afterContentPadding
        val delta = if (info.size > bottom - top) info.offset - top
        else info.offset + info.size / 2 - (top + bottom) / 2
        if (animate) state.animateScrollBy(delta.toFloat()) else state.scrollBy(delta.toFloat())
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val half = maxHeight / 2
        LaunchedEffect(nextKey, maxHeight) { center(false); center(false) }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = half, bottom = half),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(rows, key = { i, r -> when (r) { is Entry.Day -> "d$i-" + r.date; is Entry.Slot -> "s" + r.time; Entry.Prn -> "prn" } }) { i, r ->
                val wheelLook = if (i == nextIndex) Modifier else Modifier.graphicsLayer {
                    val li = state.layoutInfo
                    val info = li.visibleItemsInfo.firstOrNull { it.index == i }
                    if (info != null) {
                        val mid = (li.viewportStartOffset + li.viewportEndOffset) / 2f
                        val dist = (kotlin.math.abs(info.offset + info.size / 2f - mid) / (li.viewportEndOffset - li.viewportStartOffset) * 2f).coerceIn(0f, 1f)
                        val s = 1f - dist * 0.12f
                        scaleX = s; scaleY = s
                        alpha = 1f - dist * 0.5f
                    }
                }
                Box(wheelLook) {
                    when (r) {
                        is Entry.Day -> Text(dayLabel(r.date, now.toLocalDate()), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = Muted, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
                        is Entry.Slot -> if (r.state == SlotState.NEXT) NextCard(r, now, onSkip = { skipAsk = r })
                            else MiniSlot(r) { if (r.state != SlotState.LATER) undo = r }
                        Entry.Prn -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("לפי הצורך", color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Store.plan().meds.filter { it.prn != null }.forEach { PrnCard(it, now) }
                        }
                    }
                }
            }
        }

        val away by remember(nextIndex) {
            derivedStateOf {
                val li = state.layoutInfo
                val info = li.visibleItemsInfo.firstOrNull { it.index == nextIndex }
                nextIndex >= 0 && (info == null || kotlin.math.abs(info.offset + info.size / 2 - (li.viewportStartOffset + li.viewportEndOffset) / 2) > li.viewportEndOffset / 3)
            }
        }
        if (away) {
            Button(
                onClick = { scope.launch { center(true) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
            ) { Text("חזרה לבא", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        }
    }

    undo?.let { r ->
        AlertDialog(
            onDismissRequest = { undo = null },
            title = { Text(if (r.state == SlotState.SKIPPED) "להחזיר את ${Fmt.time(r.time.toLocalTime())}?" else "לבטל את הסימון של ${Fmt.time(r.time.toLocalTime())}?") },
            text = { Text("המועד יחזור להיות לא מסומן.") },
            confirmButton = {
                Button(onClick = {
                    if (r.state == SlotState.SKIPPED) Store.setSkipped(r.time, false)
                    else Store.setDone(r.doses, false)
                    Alarms.scheduleNext(ctx); undo = null
                }) { Text("כן") }
            },
            dismissButton = { TextButton(onClick = { undo = null }) { Text("לא") } },
        )
    }
    skipAsk?.let { r ->
        AlertDialog(
            onDismissRequest = { skipAsk = null },
            title = { Text("לדלג על ${Fmt.time(r.time.toLocalTime())}?") },
            text = { Text("המועד יסומן \"לא נלקח\" ולא \"בוצע\". המועד הבא יעבור למרכז.") },
            confirmButton = {
                Button(onClick = {
                    Store.setSkipped(r.time, true); Alarms.cancelNotification(ctx, r.time); Alarms.scheduleNext(ctx); skipAsk = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Warn)) { Text("דלג") }
            },
            dismissButton = { TextButton(onClick = { skipAsk = null }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun MiniSlot(r: Entry.Slot, onClick: () -> Unit) {
    val names = r.doses.map { it.med.name }.distinct()
    val faded = r.state != SlotState.LATER
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (faded) DoneBg else CardBg)
            .border(BorderW.dp, Line, RoundedCornerShape(18.dp))
            .clickable(enabled = faded, onClick = onClick)
            .alpha(if (faded) 0.6f else 1f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Fmt.time(r.time.toLocalTime()), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = if (faded) Muted else Ink, modifier = Modifier.width(86.dp))
        Text(if (names.size <= 2) names.joinToString(", ") else "${names.size} תרופות",
            fontSize = 18.sp, color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
        when (r.state) {
            SlotState.DONE -> Text("✓ בוצע", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ok)
            SlotState.SKIPPED -> Text("✗ לא נלקח", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Warn)
            else -> {}
        }
    }
}

/** The next time, in the middle: the whole time is taken with one button. */
@Composable
private fun NextCard(r: Entry.Slot, now: LocalDateTime, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    val mins = Duration.between(r.time, now).toMinutes() // >0 = in the past
    val late = mins >= 60
    val due = mins in 0..59
    val today = now.toLocalDate()
    val day = r.time.toLocalDate()
    val dayText = when (day) { today -> "היום"; today.plusDays(1) -> "מחר"; today.minusDays(1) -> "אתמול"; else -> Fmt.weekday(day) } + ", " + Fmt.dateYear(day)
    val whenText = when {
        late -> "$dayText · עברו ${mins / 60} ש׳ ${mins % 60} ד׳"
        due -> dayText
        else -> { val m = -mins; "$dayText · בעוד " + (if (m >= 60) "${m / 60} ש׳ " else "") + "${m % 60} ד׳" }
    }
    val accent = if (late) Warn else Amber
    val groups = r.doses.groupBy { it.med.id }.values.toList()
    val many = groups.size > 4
    val pendingMeasure = r.doses.filter { it.med.type == MedType.MEASURE && !Store.isDone(it) }
    val drops = groups.count { it.first().med.type == MedType.DROPS }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(NowBg)
            .border(3.dp, accent, RoundedCornerShape(30.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(if (many) 8.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (late) "לא סומן" else if (due) "עכשיו" else "הבא", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = if (late) Color.White else Ink,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(accent).padding(horizontal = 14.dp, vertical = 2.dp))
            Text(whenText, fontSize = 17.sp, fontWeight = if (late) FontWeight.Bold else FontWeight.Normal,
                color = if (late) Warn else Muted, modifier = Modifier.weight(1f))
        }
        Text(Fmt.time(r.time.toLocalTime()), fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 62.sp)
        groups.forEach { ds -> MedLine(ds, nameSize = if (many) 22 else 26) }
        pendingMeasure.forEach { m -> BigButton("רשום ${m.med.name}") { Nav.measuring = m } }
        if (r.doses.any { it.med.type != MedType.MEASURE && !Store.isDone(it) }) {
            BigButton("לקחתי", check = true) { markSlot(ctx, r.time, r.doses) }
        }
        SecondButton("תזכיר לי בעוד 10 דקות") {
            Alarms.cancelNotification(ctx, r.time); Alarms.scheduleFollow(ctx, r.time, 1, 10)
            android.widget.Toast.makeText(ctx, "נזכיר שוב בעוד 10 דקות", android.widget.Toast.LENGTH_SHORT).show()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (drops > 1) TextButton(onClick = { Nav.screen = Screen.Focus(r.time.toString()) }) { Text("שלב אחר שלב", fontSize = 18.sp) }
            else Spacer(Modifier.width(1.dp))
            if (late) TextButton(onClick = onSkip) { Text("לא לקחתי, דלג", fontSize = 18.sp, color = Warn) }
        }
    }
}

@Composable
fun BigButton(label: String, color: Color = Brand, check: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(84.dp),
        shape = RoundedCornerShape(42.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        if (check) { Icon(Icons.Filled.Check, null, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SecondButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(29.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SoftFill, contentColor = Ink),
        border = if (Store.highContrast.value) androidx.compose.foundation.BorderStroke(3.dp, Line) else null,
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
private fun MedLine(ds: List<Dose>, nameSize: Int) {
    val med = ds.first().med
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 13.dp, height = 42.dp).clip(RoundedCornerShape(6.dp)).background(Color(Palette[med.color])))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(med.name, fontSize = nameSize.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = (nameSize + 4).sp)
            val sub = subLine(ds)
            if (sub.isNotEmpty()) Text(sub, fontSize = 19.sp, color = Muted)
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

