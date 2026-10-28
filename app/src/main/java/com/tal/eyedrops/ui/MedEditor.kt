package com.tal.eyedrops.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tal.eyedrops.data.Fmt
import com.tal.eyedrops.data.Med
import com.tal.eyedrops.data.MedType
import com.tal.eyedrops.data.Palette
import com.tal.eyedrops.data.Prn
import com.tal.eyedrops.data.Schedule
import com.tal.eyedrops.data.Stage
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.reminders.Alarms
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private fun parseNum(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedEditor(medId: String?) {
    val ctx = LocalContext.current
    val plan = Store.plan()
    val existing = medId?.let { id -> plan.meds.firstOrNull { it.id == id } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: MedType.DROPS) }
    var color by remember {
        mutableStateOf(existing?.color ?: (Palette.indices.firstOrNull { c -> plan.meds.none { it.color == c } } ?: 0))
    }
    var amountText by remember { mutableStateOf(existing?.amount?.let { Fmt.num(it) } ?: "1") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    // drops: target organ + side
    val oldSite = existing?.sites?.firstOrNull() ?: ""
    var target by remember { mutableStateOf(Fmt.dropTargets.firstOrNull { oldSite.startsWith(it) } ?: if (existing == null) "עין" else "") }
    var side by remember {
        mutableStateOf(
            when {
                (existing?.sites?.size ?: 0) > 1 -> 2
                oldSite.endsWith("שמאל") -> 1
                else -> 0
            }
        )
    }
    val rotation = remember { mutableStateListOf<String>().apply { addAll(existing?.rotation ?: emptyList()) } }
    var prnMode by remember { mutableStateOf(existing?.prn != null) }
    var minHoursText by remember { mutableStateOf((existing?.prn?.minHours ?: 6).toString()) }
    var maxDayText by remember { mutableStateOf((existing?.prn?.maxPerDay ?: 0).let { if (it == 0) "" else it.toString() }) }
    var stockText by remember { mutableStateOf(existing?.id?.let { Store.stock(it) }?.let { Fmt.num(it) } ?: "") }
    var refillText by remember { mutableStateOf(existing?.refillAt?.let { Fmt.num(it) } ?: "") }
    val stages = remember {
        mutableStateListOf<Stage>().apply {
            addAll(existing?.stages?.takeIf { it.isNotEmpty() } ?: listOf(Stage(LocalDate.now(), 7, Schedule.presetTimes(4))))
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    fun sitesNow(): List<String> {
        if (type != MedType.DROPS || target.isEmpty()) return emptyList()
        return when (side) { 0 -> listOf("$target ימין"); 1 -> listOf("$target שמאל"); else -> listOf("$target ימין", "$target שמאל") }
    }

    fun save() {
        val n = name.trim()
        val amount = if (type.unit.isEmpty()) 0.0 else parseNum(amountText) ?: -1.0
        fun err(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        when {
            n.isEmpty() -> return err("כתוב שם")
            amount < 0 -> return err("כמות לא תקינה")
            !prnMode && stages.isEmpty() -> return err("הוסף לפחות שלב אחד")
            !prnMode && stages.any { it.times.isEmpty() } -> return err("בכל שלב צריכה להיות לפחות שעה אחת")
            type.rotates && rotation.size == 1 -> return err("להחלפת מיקום בחר לפחות שני מקומות, או אף אחד")
        }
        val id = existing?.id ?: UUID.randomUUID().toString().take(8)
        val med = Med(
            id = id, name = n, color = color, type = type, amount = amount,
            sites = sitesNow(),
            rotation = if (type.rotates) rotation.toList() else emptyList(),
            note = note.trim(),
            stages = if (prnMode) emptyList() else stages.sortedBy { it.start },
            prn = if (prnMode) Prn(minHoursText.toIntOrNull() ?: 0, maxDayText.toIntOrNull() ?: 0) else null,
            refillAt = if (type.hasStock) parseNum(refillText) else null,
        )
        Store.upsertMed(med)
        if (type.hasStock) Store.setStock(id, parseNum(stockText))
        Alarms.scheduleNext(ctx)
        Nav.screen = Screen.Settings
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.screen = Screen.Settings }) { Icon(Icons.Filled.ArrowBack, "חזרה בלי לשמור", tint = Ink) }
            Text(if (existing == null) "חדש" else "עריכה", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.weight(1f))
            Button(onClick = { save() }) { Text("שמור") }
        }

        Section("מה") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MedType.values().forEach { t -> QuickChip(t.label, selected = type == t) { type = t } }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) },
                label = { Text(if (type == MedType.MEASURE) "מה מודדים (למשל לחץ דם)" else "שם התרופה") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (type.unit.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amountText, onValueChange = { amountText = it.take(6) },
                        label = { Text("כמות בכל פעם") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(150.dp),
                    )
                    Text(type.unit, color = Muted)
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(200) },
                label = { Text("הוראות (למשל: עם אוכל)") }, modifier = Modifier.fillMaxWidth(),
            )
            Text("צבע", color = Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                            .border(3.dp, if (i == color) Ink else Color.Transparent, CircleShape)
                            .clickable { color = i },
                        contentAlignment = Alignment.Center,
                    ) { if (i == color) Icon(Icons.Filled.Check, "נבחר", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }

        if (type == MedType.DROPS) {
            Section("לאן") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Fmt.dropTargets + "").forEach { t -> QuickChip(if (t.isEmpty()) "בלי" else t, selected = target == t) { target = t } }
                }
                if (target.isNotEmpty()) {
                    Segmented(listOf("ימין", "שמאל", "שתיהן"), side) { side = it }
                    if (side == 2) Text("בכל פעם יופיעו שני סימונים, אחד לכל צד.", color = Muted, fontSize = 13.sp)
                }
            }
        }

        if (type.rotates) {
            Section("החלפת מקום") {
                Text("בחר את המקומות לפי הסדר. בכל פעם תופיע התזכורת עם המקום הבא בתור.", color = Muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fmt.rotationSites.forEach { site ->
                        val idx = rotation.indexOf(site)
                        QuickChip(if (idx >= 0) "${idx + 1}. $site" else site, selected = idx >= 0) {
                            if (idx >= 0) rotation.removeAt(idx) else rotation.add(site)
                        }
                    }
                }
            }
        }

        Section("מתי") {
            Segmented(listOf("בשעות קבועות", "לפי הצורך"), if (prnMode) 1 else 0) { prnMode = it == 1 }
            if (prnMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minHoursText, onValueChange = { minHoursText = it.filter(Char::isDigit).take(2) },
                        label = { Text("שעות בין מנות") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxDayText, onValueChange = { maxDayText = it.filter(Char::isDigit).take(2) },
                        label = { Text("מקסימום ביום") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                Text("אין תזכורות. במסך הראשי יש כפתור \"לקחתי עכשיו\", והאפליקציה מראה מתי מותר שוב.", color = Muted, fontSize = 13.sp)
            }
        }

        if (!prnMode) {
            stages.forEachIndexed { idx, st ->
                StageCard(
                    index = idx, stage = st, canDelete = stages.size > 1,
                    onChange = { stages[idx] = it },
                    onDelete = { stages.removeAt(idx) },
                )
            }
            OutlinedButton(onClick = {
                val last = stages.lastOrNull()
                stages.add(
                    if (last == null) Stage(LocalDate.now(), 7, Schedule.presetTimes(4))
                    else Stage(last.end.plusDays(1), 7, last.times)
                )
            }, modifier = Modifier.fillMaxWidth()) { Text("הוסף שלב (למשל הורדת מינון)") }
        }

        if (type.hasStock) {
            Section("מלאי (לא חובה)") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = stockText, onValueChange = { stockText = it.take(6) },
                        label = { Text("יש עכשיו (${type.unit})") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = refillText, onValueChange = { refillText = it.take(6) },
                        label = { Text("התראה כשנשארים") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                Text("כל סימון \"בוצע\" מוריד מהמלאי. כשקונים חדש, מעדכנים כאן את הכמות.", color = Muted, fontSize = 13.sp)
            }
        }

        if (existing != null) {
            TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Warn)) {
                Text("מחק")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את ${existing.name}?") },
            text = { Text("כל התזכורות שלה יימחקו.") },
            confirmButton = {
                Button(onClick = {
                    Store.deleteMed(existing.id); Alarms.scheduleNext(ctx); confirmDelete = false
                    Nav.screen = Screen.Settings
                }, colors = ButtonDefaults.buttonColors(containerColor = Warn)) { Text("מחק") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageCard(index: Int, stage: Stage, canDelete: Boolean, onChange: (Stage) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var daysText by remember(stage.days) { mutableStateOf(stage.days.toString()) }

    Section("שלב ${index + 1}") {
        OutlinedButton(onClick = {
            DatePickerDialog(ctx, { _, y, m, d -> onChange(stage.copy(start = LocalDate.of(y, m + 1, d))) },
                stage.start.year, stage.start.monthValue - 1, stage.start.dayOfMonth).show()
        }) { Text("מ־${Fmt.weekday(stage.start)} ${Fmt.dateYear(stage.start)}") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = daysText,
                onValueChange = { v ->
                    daysText = v.filter { it.isDigit() }.take(4)
                    daysText.toIntOrNull()?.takeIf { it in 1..3650 }?.let { onChange(stage.copy(days = it)) }
                },
                label = { Text("ימים") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
            )
            Text("עד ${Fmt.weekday(stage.end)} ${Fmt.dateYear(stage.end)}", color = Muted, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "יום", 7 to "שבוע", 14 to "שבועיים", 30 to "חודש").forEach { (d, label) ->
                QuickChip(label, selected = stage.days == d) { onChange(stage.copy(days = d)) }
            }
        }

        Text("פעמים ביום (ממלא שעות, אפשר לשנות)", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4, 5, 6, 8).forEach { n ->
                QuickChip("$n", selected = stage.times.size == n) { onChange(stage.copy(times = Schedule.presetTimes(n))) }
            }
        }
        val first = stage.times.firstOrNull() ?: LocalTime.of(8, 0)
        Text("או כל X שעות מסביב לשעון, החל מ־${Fmt.time(first)}", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 6, 8, 12).forEach { h ->
                QuickChip("כל $h", selected = false) { onChange(stage.copy(times = Schedule.everyHours(h, first))) }
            }
        }

        Text("שעות", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stage.times.forEach { t ->
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Bg).border(1.dp, Line, RoundedCornerShape(10.dp))
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Fmt.time(t), color = Ink, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            TimePickerDialog(ctx, { _, h, m ->
                                onChange(stage.copy(times = (stage.times - t + LocalTime.of(h, m)).distinct().sorted()))
                            }, t.hour, t.minute, true).show()
                        },
                    )
                    TextButton(onClick = { onChange(stage.copy(times = stage.times - t)) }) { Text("✕", color = Muted) }
                }
            }
            OutlinedButton(onClick = {
                TimePickerDialog(ctx, { _, h, m ->
                    onChange(stage.copy(times = (stage.times + LocalTime.of(h, m)).distinct().sorted()))
                }, 12, 0, true).show()
            }) { Text("+ שעה") }
        }

        if (canDelete) {
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Warn)) { Text("מחק שלב") }
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) Brand else Color.Transparent)
            .border(1.dp, if (selected) Brand else Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (selected) Color.White else Ink, fontSize = 14.sp) }
}
