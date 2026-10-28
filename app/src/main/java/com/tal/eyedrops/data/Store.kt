package com.tal.eyedrops.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDateTime

/** Everything is kept on the phone in SharedPreferences. [version] bumps on every change so the UI recomposes. */
object Store {
    private lateinit var prefs: SharedPreferences
    val version = mutableIntStateOf(0)
    private var cached: PlanData? = null

    fun init(c: Context) {
        if (::prefs.isInitialized) return
        prefs = c.applicationContext.getSharedPreferences("drops", Context.MODE_PRIVATE)
    }

    private fun bump() { version.intValue++ }

    // ---- plan ----
    fun plan(): PlanData {
        cached?.let { return it }
        val p = prefs.getString("plan", null)?.let { runCatching { PlanData.fromJson(it) }.getOrNull() } ?: PlanData.EMPTY
        cached = p
        return p
    }

    fun savePlan(p: PlanData) {
        cached = p
        prefs.edit().putString("plan", p.toJson()).apply()
        bump()
    }

    fun eyeNames(): List<String> = plan().eyes

    fun upsertMed(m: Med) {
        val p = plan()
        val meds = if (p.meds.any { it.id == m.id }) p.meds.map { if (it.id == m.id) m else it } else p.meds + m
        savePlan(p.copy(meds = meds))
    }

    fun deleteMed(id: String) { val p = plan(); savePlan(p.copy(meds = p.meds.filter { it.id != id })) }

    fun moveMed(id: String, delta: Int) {
        val p = plan(); val list = p.meds.toMutableList()
        val i = list.indexOfFirst { it.id == id }; val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        savePlan(p.copy(meds = list))
    }

    fun setEyes(names: List<String>) { savePlan(plan().copy(eyes = names)) }

    // ---- alert settings ----
    /** true = sound (alarm tone), false = quiet (vibration only). */
    fun soundOn(): Boolean = prefs.getBoolean("sound", true)
    fun setSound(on: Boolean) { prefs.edit().putBoolean("sound", on).apply(); bump() }

    /** Repeat the reminder every 15 minutes (up to 2 times) while not marked done. */
    fun followUp(): Boolean = prefs.getBoolean("followUp", true)
    fun setFollowUp(on: Boolean) { prefs.edit().putBoolean("followUp", on).apply(); bump() }

    /** Custom tone picked by the user (null = phone's default alarm tone). */
    fun toneUri(): String? = prefs.getString("tone", null)
    /** Bumped on every tone change: a new channel id is needed because Android locks a channel's sound. */
    fun toneVersion(): Int = prefs.getInt("toneVersion", 1)
    fun setTone(uri: String?) {
        prefs.edit().putString("tone", uri).putInt("toneVersion", toneVersion() + 1).apply(); bump()
    }

    /** A one-off test alarm the user scheduled, or null. */
    fun testAt(): LocalDateTime? = prefs.getString("testAt", null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    fun setTestAt(t: LocalDateTime?) { prefs.edit().putString("testAt", t?.toString()).apply(); bump() }

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
        val doses = Schedule.dosesAt(slot)
        return doses.isNotEmpty() && doses.all { isDone(it) }
    }
}
