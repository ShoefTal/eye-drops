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
import com.tal.eyedrops.data.Palette
import com.tal.eyedrops.data.Schedule
import com.tal.eyedrops.data.Stage
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.reminders.Alarms
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedEditor(medId: String?) {
    val ctx = LocalContext.current
    val plan = Store.plan()
    val existing = medId?.let { id -> plan.meds.firstOrNull { it.id == id } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember {
        mutableStateOf(existing?.color ?: (Palette.indices.firstOrNull { c -> plan.meds.none { it.color == c } } ?: 0))
    }
    var eyes by remember { mutableStateOf(existing?.eyes ?: plan.eyes.indices.toList()) }
    val stages = remember {
        mutableStateListOf<Stage>().apply {
            addAll(existing?.stages ?: listOf(Stage(LocalDate.now(), 7, Schedule.presetTimes(4))))
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val n = name.trim()
        when {
            n.isEmpty() -> { Toast.makeText(ctx, "כתוב את שם התרופה", Toast.LENGTH_SHORT).show(); return }
            stages.isEmpty() -> { Toast.makeText(ctx, "הוסף לפחות שלב אחד", Toast.LENGTH_SHORT).show(); return }
            stages.any { it.times.isEmpty() } -> { Toast.makeText(ctx, "בכל שלב צריכה להיות לפחות שעה אחת", Toast.LENGTH_SHORT).show(); return }
        }
        val eyeList = eyes.filter { it < plan.eyes.size }.ifEmpty { listOf(0) }
        Store.upsertMed(Med(existing?.id ?: UUID.randomUUID().toString().take(8), n, color, eyeList, stages.sortedBy { it.start }))
        Alarms.scheduleNext(ctx)
        Nav.screen = Screen.Settings
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.screen = Screen.Settings }) { Icon(Icons.Filled.ArrowBack, "חזרה בלי לשמור", tint = Ink) }
            Text(if (existing == null) "תרופה חדשה" else "עריכת תרופה", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.weight(1f))
            Button(onClick = { save() }) { Text("שמור") }
        }

        Section("תרופה") {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) },
                label = { Text("שם התרופה") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
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
            if (plan.eyes.size > 1) {
                Text("לאיזו עין", color = Muted, fontSize = 13.sp)
                Segmented(listOf(plan.eyes[0], plan.eyes[1], "שתיהן"), when (eyes.sorted()) { listOf(0) -> 0; listOf(1) -> 1; else -> 2 }) {
                    eyes = when (it) { 0 -> listOf(0); 1 -> listOf(1); else -> listOf(0, 1) }
                }
            }
        }

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

        if (existing != null) {
            TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Warn)) {
                Text("מחק את התרופה")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את ${existing.name}?") },
            text = { Text("כל התזכורות של התרופה הזו יימחקו.") },
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                DatePickerDialog(ctx, { _, y, m, d -> onChange(stage.copy(start = LocalDate.of(y, m + 1, d))) },
                    stage.start.year, stage.start.monthValue - 1, stage.start.dayOfMonth).show()
            }) { Text("מ־${Fmt.weekday(stage.start)} ${Fmt.dateYear(stage.start)}") }
        }
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
            listOf(1 to "יום", 7 to "שבוע", 14 to "שבועיים", 21 to "3 שבועות").forEach { (d, label) ->
                QuickChip(label, selected = stage.days == d) { onChange(stage.copy(days = d)) }
            }
        }

        Text("פעמים ביום (ממלא שעות אוטומטית, אפשר לשנות)", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4, 5, 6, 8).forEach { n ->
                QuickChip("$n", selected = stage.times.size == n) { onChange(stage.copy(times = Schedule.presetTimes(n))) }
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
