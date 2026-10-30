package com.tal.eyedrops.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
    /** Step-by-step screen for one reminder time (ISO date-time). */
    data class Focus(val slot: String) : Screen()
}

object Nav {
    var screen by mutableStateOf<Screen>(Screen.Home)
    /** A plan received from another phone, waiting for the user to confirm. */
    var pendingImport by mutableStateOf<PlanData?>(null)
    /** A measurement dose waiting for its value. */
    var measuring by mutableStateOf<Dose?>(null)
    /** The caregiver PIN was entered in this session. */
    var unlocked by mutableStateOf(false)
    var askPin by mutableStateOf(false)

    /** Opens settings, asking for the caregiver PIN when one is set. */
    fun openSettings() {
        if (Store.pin().isEmpty() || unlocked) screen = Screen.Settings else askPin = true
    }
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
        handleSlot(intent)
        setContent { AppTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
        handleSlot(intent)
    }

    override fun onStop() {
        super.onStop()
        Nav.unlocked = false
    }

    /** Opened from a reminder: go to its step-by-step screen, over the lock screen if it came as an alarm. */
    private fun handleSlot(i: Intent?) {
        val slot = i?.getStringExtra(Alarms.EXTRA_OPEN_SLOT) ?: return
        if (i.getBooleanExtra(Alarms.EXTRA_ALARM, false) && Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        Nav.screen = Screen.Focus(slot)
    }

    override fun onResume() {
        super.onResume()
        Alarms.scheduleNext(this)
        Store.refresh() // permissions may have changed in system settings
        Updater.checkAsync(this, interactive = false)
    }

    /** A plan shared as text, as a .txt file, or opened from a file manager. */
    private fun handleShare(i: Intent?) {
        if (i == null) return
        val text: String? = when (i.action) {
            Intent.ACTION_SEND ->
                i.getStringExtra(Intent.EXTRA_TEXT)
                    ?: streamUri(i)?.let { readText(this, it) }
            Intent.ACTION_VIEW -> i.data?.let { readText(this, it) }
            else -> return
        }
        val plan = text?.let { PlanData.fromShareText(it) }
        if (plan == null) Toast.makeText(this, "לא נמצאה תוכנית. שלח את ההודעה כטקסט, או בחר את הקובץ מההגדרות.", Toast.LENGTH_LONG).show()
        else { Nav.pendingImport = plan; Nav.screen = Screen.Home }
    }

    @Suppress("DEPRECATION")
    private fun streamUri(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)
}

/** Reads a small text file the user picked or shared (max 200 KB). */
fun readText(c: Context, uri: Uri): String? = runCatching {
    c.contentResolver.openInputStream(uri)?.use { s ->
        val buf = s.readBytes().let { if (it.size > 200_000) it.copyOf(200_000) else it }
        String(buf, Charsets.UTF_8)
    }
}.getOrNull()

/** Called with the file the user picked in the "choose file" dialog. */
fun importFromFile(c: Context, uri: Uri?) {
    uri ?: return
    val plan = readText(c, uri)?.let { PlanData.fromShareText(it) }
    if (plan == null) Toast.makeText(c, "בקובץ הזה אין תוכנית של Toma", Toast.LENGTH_LONG).show()
    else Nav.pendingImport = plan
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
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (screen) {
            Screen.Home -> HomeScreen()
            Screen.Settings -> SettingsScreen()
            is Screen.Edit -> MedEditor(screen.medId)
            is Screen.History -> HistoryScreen(screen.medId)
            is Screen.Focus -> FocusScreen(screen.slot)
        }
        }
    }
    Nav.pendingImport?.let { ImportDialog(it) }
    Nav.measuring?.let { MeasureDialog(it) }
    if (Nav.askPin) PinDialog()
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

@Composable
fun PrnCard(m: Med, now: LocalDateTime) {
    Store.version.intValue // recompose when data changes
    val ctx = LocalContext.current
    val prn = m.prn ?: return
    val log = Store.prnLog(m.id)
    val last = log.lastOrNull()
    val today = log.count { it.toLocalDate() == now.toLocalDate() }
    val nextOk = last?.plusHours(prn.minHours.toLong())
    val tooSoon = nextOk != null && nextOk.isAfter(now)
    val maxed = prn.maxPerDay > 0 && today >= prn.maxPerDay
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg)
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
fun MeasureDialog(d: Dose) {
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text((date?.let { Fmt.weekday(it) + " " + Fmt.dateYear(it) } ?: parts[0]) + " · " + parts.getOrElse(1) { "" },
                    color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
