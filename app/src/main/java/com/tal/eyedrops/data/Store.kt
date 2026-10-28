package com.tal.eyedrops.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate
import java.time.LocalDateTime

/** Everything is kept on the phone in SharedPreferences. [version] bumps on every change so the UI recomposes. */
object Store {
    private lateinit var prefs: SharedPreferences
    val version = mutableIntStateOf(0)

    val DEFAULT_START: LocalDate = LocalDate.of(2026, 10, 29)

    fun init(c: Context) {
        if (!::prefs.isInitialized) prefs = c.applicationContext.getSharedPreferences("drops", Context.MODE_PRIVATE)
    }

    private fun bump() { version.intValue++ }

    // ---- eyes ----
    fun eyes(): List<Eye> {
        val n = prefs.getInt("eyeCount", 1).coerceIn(1, 2)
        return (0 until n).map { i ->
            Eye(
                name = prefs.getString("eye${i}Name", if (i == 0) "ימין" else "שמאל") ?: "ימין",
                start = prefs.getString("eye${i}Start", null)?.let { LocalDate.parse(it) }
                    ?: if (i == 0) DEFAULT_START else DEFAULT_START.plusDays(14),
            )
        }
    }

    fun setEyeCount(n: Int) { prefs.edit().putInt("eyeCount", n).apply(); bump() }
    fun setEyeName(i: Int, name: String) { prefs.edit().putString("eye${i}Name", name).apply(); bump() }
    fun setEyeStart(i: Int, d: LocalDate) { prefs.edit().putString("eye${i}Start", d.toString()).apply(); bump() }

    // ---- alert settings ----
    /** true = sound (alarm tone), false = quiet (vibration only). */
    fun soundOn(): Boolean = prefs.getBoolean("sound", true)
    fun setSound(on: Boolean) { prefs.edit().putBoolean("sound", on).apply(); bump() }

    /** Repeat the reminder every 15 minutes (up to 2 times) while not marked done. */
    fun followUp(): Boolean = prefs.getBoolean("followUp", true)
    fun setFollowUp(on: Boolean) { prefs.edit().putBoolean("followUp", on).apply(); bump() }

    // ---- done marks ----
    private fun doneSet(): Set<String> = prefs.getStringSet("done", emptySet()) ?: emptySet()

    fun isDone(d: Dose) = d.key in doneSet()

    fun setDone(doses: List<Dose>, done: Boolean) {
        val s = HashSet(doneSet())
        doses.forEach { if (done) s.add(it.key) else s.remove(it.key) }
        prefs.edit().putStringSet("done", s).apply()
        bump()
    }

    fun toggle(d: Dose) = setDone(listOf(d), !isDone(d))

    fun slotDone(slot: LocalDateTime): Boolean {
        val doses = Plan.dosesAt(slot, eyes())
        return doses.isNotEmpty() && doses.all { isDone(it) }
    }
}
