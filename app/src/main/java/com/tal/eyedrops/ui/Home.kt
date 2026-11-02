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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

// ------------------------------------------------------------ home: days to swipe, today as a wheel

private enum class SlotState { RESOLVED, NEXT, LATER }

/** One row of a day: a day label, or one reminder time with its doses. */
private sealed class Entry {
    data class Day(val date: LocalDate) : Entry()
    data class Slot(val time: LocalDateTime, val doses: List<Dose>, val state: SlotState) : Entry()
    data object Prn : Entry()
}

private fun resolved(doses: List<Dose>) = doses.isNotEmpty() && doses.all { Store.isMarked(it) }

/**
 * The next time: the earliest one since the plan was loaded where some dose has no mark yet.
 * It stays next, with its own date, until every dose in it is marked taken or not taken.
 */
private fun nextSlot(now: LocalDateTime): LocalDateTime? {
    val since = Store.since()
    val today = now.toLocalDate()
    for (d in -7..2) {
        val date = today.plusDays(d.toLong())
        if (date.plusDays(1).atStartOfDay().isBefore(since)) continue
        Schedule.slotsOn(date).forEach { (t, doses) -> if (!t.isBefore(since) && !resolved(doses)) return t }
    }
    return Schedule.nextSlotAfter(today.plusDays(2).atTime(23, 59))
}

/** Today's page: today's marked times above, the next time, then the rest of today. */
private fun todayRows(now: LocalDateTime): Pair<List<Entry>, Int> {
    val today = now.toLocalDate()
    val next = nextSlot(now)
    val rows = ArrayList<Entry>()
    val day = Schedule.slotsOn(today)
    day.filter { resolved(it.second) && it.first != next }.forEach { (t, ds) -> rows.add(Entry.Slot(t, ds, SlotState.RESOLVED)) }
    var nextIndex = -1
    if (next != null) {
        if (next.toLocalDate() != today) rows.add(Entry.Day(next.toLocalDate()))
        nextIndex = rows.size
        rows.add(Entry.Slot(next, Schedule.dosesAt(next), SlotState.NEXT))
        if (next.toLocalDate() != today && day.any { !resolved(it.second) && it.first != next }) rows.add(Entry.Day(today))
    }
    day.filter { !resolved(it.second) && it.first != next }.forEach { (t, ds) -> rows.add(Entry.Slot(t, ds, SlotState.LATER)) }
    if (Store.plan().meds.any { it.prn != null }) rows.add(Entry.Prn)
    return rows to nextIndex
}

private fun dayName(d: LocalDate, today: LocalDate) = when (d) {
    today -> "היום"; today.plusDays(1) -> "מחר"; today.minusDays(1) -> "אתמול"; else -> Fmt.weekday(d)
}

private const val DAYS_BACK = 60
private const val DAYS_FWD = 60

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val pager = rememberPagerState(initialPage = DAYS_BACK) { DAYS_BACK + DAYS_FWD + 1 }
    val scope = rememberCoroutineScope()
    val shown = today.plusDays((pager.currentPage - DAYS_BACK).toLong())

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
            return@Column
        }

        // Day strip: arrows and the day shown; swiping the page does the same.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "יום קודם", tint = Ink, modifier = Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${dayName(shown, today)} · ${Fmt.dateYear(shown)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                if (shown != today) TextButton(onClick = { scope.launch { pager.animateScrollToPage(DAYS_BACK) } }) {
                    Text("חזרה להיום", fontSize = 17.sp)
                }
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "יום הבא", tint = Ink, modifier = Modifier.size(34.dp))
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
            val date = today.plusDays((page - DAYS_BACK).toLong())
            if (date == today) TodayWheel(now) else DayPage(date, now)
        }
    }
}

/** Today: the next time sits in the middle; today's marked times above, the rest of today below. */
@Composable
private fun TodayWheel(now: LocalDateTime) {
    Store.version.intValue
    val ctx = LocalContext.current
    val (rows, nextIndex) = todayRows(now)
    val nextKey = (rows.getOrNull(nextIndex) as? Entry.Slot)?.time
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var undo by remember { mutableStateOf<Entry.Slot?>(null) }

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
            if (rows.none { it is Entry.Slot }) item {
                Text("אין תרופות היום", fontSize = 20.sp, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
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
                        is Entry.Day -> Text("${dayName(r.date, now.toLocalDate())} · ${Fmt.dateYear(r.date)}", fontSize = 17.sp,
                            fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center)
                        is Entry.Slot -> if (r.state == SlotState.NEXT) NextCard(r, now)
                            else MiniSlot(r) { if (r.state == SlotState.RESOLVED) undo = r }
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
            title = { Text("לבטל את הסימונים של ${Fmt.time(r.time.toLocalTime())}?") },
            text = { Text("כל התרופות במועד הזה יחזרו להיות לא מסומנות, והוא יחזור להיות הבא.") },
            confirmButton = {
                Button(onClick = { Store.setStatus(r.doses, 0); Alarms.scheduleNext(ctx); undo = null }) { Text("כן") }
            },
            dismissButton = { TextButton(onClick = { undo = null }) { Text("לא") } },
        )
    }
}

/** Another day: every time of that day, each med marked taken or not taken. */
@Composable
private fun DayPage(date: LocalDate, now: LocalDateTime) {
    Store.version.intValue
    val slots = Schedule.slotsOn(date)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (slots.isEmpty()) item {
            Text("אין תרופות ביום הזה", fontSize = 20.sp, color = Muted, modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                textAlign = TextAlign.Center)
        }
        items(slots, key = { it.first.toString() }) { (t, doses) -> DayCard(t, doses, now) }
    }
}

@Composable
private fun DayCard(time: LocalDateTime, doses: List<Dose>, now: LocalDateTime) {
    val allTaken = doses.all { Store.isDone(it) }
    val open = !resolved(doses)
    val missed = open && time.isBefore(now.minusMinutes(59))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (open) CardBg else DoneBg)
            .border(if (missed) 3.dp else BorderW.dp, if (missed) Warn else Line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.time(time.toLocalTime()), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
            when {
                allTaken -> Text("✓ בוצע", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ok)
                !open -> Text(markSummary(doses), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Muted)
                missed -> Text("לא סומן", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Warn)
            }
        }
        doses.groupBy { it.med.id }.values.forEach { ds -> MedMarkRow(ds, nameSize = 20) }
    }
}

private fun markSummary(doses: List<Dose>): String {
    val groups = doses.groupBy { it.med.id }.values
    val taken = groups.count { g -> g.all { Store.isDone(it) } }
    val notTaken = groups.size - taken
    return if (notTaken == 0) "✓ בוצע" else "✓ $taken · ✗ $notTaken"
}

@Composable
private fun MiniSlot(r: Entry.Slot, onClick: () -> Unit) {
    val names = r.doses.map { it.med.name }.distinct()
    val faded = r.state == SlotState.RESOLVED
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
        if (faded) {
            val s = markSummary(r.doses)
            Text(s, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (s.contains("✗")) Warn else Ok)
        }
    }
}

/** The next time, in the middle: each med marked taken / not taken, or all at once. */
@Composable
private fun NextCard(r: Entry.Slot, now: LocalDateTime) {
    val ctx = LocalContext.current
    val mins = Duration.between(r.time, now).toMinutes() // >0 = in the past
    val late = mins >= 60
    val due = mins in 0..59
    val today = now.toLocalDate()
    val day = r.time.toLocalDate()
    val dayText = dayName(day, today) + ", " + Fmt.dateYear(day)
    val whenText = when {
        late -> "$dayText · עברו ${mins / 60} ש׳ ${mins % 60} ד׳"
        due -> dayText
        else -> { val m = -mins; "$dayText · בעוד " + (if (m >= 60) "${m / 60} ש׳ " else "") + "${m % 60} ד׳" }
    }
    val accent = if (late) Warn else Amber
    val groups = r.doses.groupBy { it.med.id }.values.toList()
    val many = groups.size > 4
    val unmarked = r.doses.filter { !Store.isMarked(it) && it.med.type != MedType.MEASURE }
    val drops = groups.count { it.first().med.type == MedType.DROPS }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(NowBg)
            .border(3.dp, accent, RoundedCornerShape(30.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(if (many) 8.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (late) "לא סומן" else if (due) "עכשיו" else "הבא", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = if (late) Color.White else Ink,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(accent).padding(horizontal = 14.dp, vertical = 2.dp))
            Text(whenText, fontSize = 17.sp, fontWeight = if (late) FontWeight.Bold else FontWeight.Normal,
                color = if (late) Warn else Muted, modifier = Modifier.weight(1f))
        }
        Text(Fmt.time(r.time.toLocalTime()), fontSize = 58.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 60.sp)
        groups.forEach { ds -> MedMarkRow(ds, nameSize = if (many) 21 else 24) }
        if (unmarked.isNotEmpty()) {
            val partial = unmarked.size < r.doses.count { it.med.type != MedType.MEASURE }
            BigButton(if (partial) "לקחתי את השאר" else "לקחתי הכול", check = true) {
                Store.setStatus(unmarked, 1).forEach { Alarms.notifyRefill(ctx, it) }
                if (Store.slotDone(r.time)) Alarms.cancelNotification(ctx, r.time)
                Alarms.scheduleNext(ctx)
            }
        }
        SecondButton("תזכיר לי בעוד 10 דקות") {
            Alarms.cancelNotification(ctx, r.time); Alarms.scheduleFollow(ctx, r.time, 1, 10)
            android.widget.Toast.makeText(ctx, "נזכיר שוב בעוד 10 דקות", android.widget.Toast.LENGTH_SHORT).show()
        }
        if (drops > 1) TextButton(onClick = { Nav.screen = Screen.Focus(r.time.toString()) }) { Text("שלב אחר שלב", fontSize = 18.sp) }
    }
}

/** One med at one time, with "taken" and "not taken" buttons. A second tap clears the mark. */
@Composable
private fun MedMarkRow(ds: List<Dose>, nameSize: Int) {
    val ctx = LocalContext.current
    val med = ds.first().med
    val taken = ds.all { Store.isDone(it) }
    val notTaken = !taken && ds.all { Store.isMissed(it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Color(Palette[med.color])))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(med.name, fontSize = nameSize.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (nameSize + 4).sp,
                color = if (notTaken) Muted else Ink)
            val sub = if (med.type == MedType.MEASURE) (ds.firstNotNullOfOrNull { Store.measurement(it) } ?: "") else subLine(ds)
            if (sub.isNotEmpty()) Text(sub, fontSize = 17.sp, color = Muted)
        }
        Spacer(Modifier.width(8.dp))
        fun tap(st: Int) {
            Store.setStatus(ds, st).forEach { Alarms.notifyRefill(ctx, it) }
            if (Store.slotDone(ds.first().time)) Alarms.cancelNotification(ctx, ds.first().time)
            Alarms.scheduleNext(ctx)
        }
        if (med.type == MedType.MEASURE && !taken) {
            MarkButton("רשום", on = false, color = Brand) { Nav.measuring = ds.first() }
        } else {
            MarkButton("✓", on = taken, color = Ok, label = "נלקח: ${med.name}") { tap(if (taken) 0 else 1) }
        }
        Spacer(Modifier.width(8.dp))
        MarkButton("✗", on = notTaken, color = Warn, label = "לא נלקח: ${med.name}") { tap(if (notTaken) 0 else -1) }
    }
}

@Composable
private fun MarkButton(text: String, on: Boolean, color: Color, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 60.dp, height = 56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) color else CardBg)
            .border(if (on) 0.dp else 2.dp, if (on) color else Line, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { if (label != null) contentDescription = label + if (on) ", מסומן" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = if (text.length > 1) 18.sp else 26.sp, fontWeight = FontWeight.ExtraBold,
            color = if (on) Color.White else if (text == "✗") Warn else Ink)
    }
}

@Composable
fun BigButton(label: String, color: Color = Brand, check: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        if (check) { Icon(Icons.Filled.Check, null, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
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
    val current = steps.firstOrNull { !Store.isMarked(it) }
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
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(if (Store.isDone(d)) Brand else if (Store.isMissed(d)) Warn else Line))
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
                val next = steps.firstOrNull { !Store.isMarked(it) }
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

