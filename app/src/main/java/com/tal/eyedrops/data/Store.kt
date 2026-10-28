package com.tal.eyedrops.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Everything is kept on the phone in SharedPreferences. [version] bumps on every change so the UI recomposes. */
object Store {
    private lateinit var prefs: SharedPreferences
    val version = mutableIntStateOf(0)
    private var cached: PlanData? = null

    fun init(c: Context) {
        if (::prefs.isInitialized) return
        prefs = c.applicationContext.getSharedPreferences("toma", Context.MODE_PRIVATE)
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

    fun med(id: String): Med? = plan().meds.firstOrNull { it.id == id }

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

    // ---- done marks, rotation, stock, measurements ----
    private fun doneSet(): Set<String> = prefs.getStringSet("done", emptySet()) ?: emptySet()

    fun isDone(d: Dose) = d.key in doneSet()

    private fun jsonMap(key: String): JSONObject = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrDefault(JSONObject())

    /** Marks doses done or not done, and keeps rotation and stock in step. Returns meds whose stock just hit the refill level. */
    fun setDone(doses: List<Dose>, done: Boolean): List<Med> {
        val s = HashSet(doneSet())
        val used = jsonMap("rotUsed")
        val e = prefs.edit()
        val lowNow = ArrayList<Med>()
        doses.forEach { d ->
            val was = d.key in s
            if (done == was) return@forEach
            if (done) s.add(d.key) else s.remove(d.key)
            val m = d.med
            if (m.rotation.isNotEmpty()) {
                val next = prefs.getInt("rot_${m.id}", 0)
                if (done) {
                    used.put(d.key, m.rotation[next % m.rotation.size])
                    e.putInt("rot_${m.id}", next + 1)
                } else if (used.has(d.key)) {
                    used.remove(d.key)
                    e.putInt("rot_${m.id}", maxOf(0, next - 1))
                }
            }
            stock(m.id)?.let { st ->
                val after = if (done) st - m.amount else st + m.amount
                e.putFloat("stock_${m.id}", after.toFloat())
                val r = m.refillAt
                if (done && r != null && st > r && after <= r) lowNow.add(m)
            }
        }
        e.putStringSet("done", s).putString("rotUsed", used.toString()).commit()
        bump()
        return lowNow
    }

    fun toggle(d: Dose): List<Med> = setDone(listOf(d), !isDone(d))

    fun slotDone(slot: LocalDateTime): Boolean {
        val doses = Schedule.dosesAt(slot)
        return doses.isNotEmpty() && doses.all { isDone(it) }
    }

    /** Site to use: the one recorded for a done dose, else the next one in the rotation. */
    fun siteFor(d: Dose): String? {
        val m = d.med
        if (m.rotation.isEmpty()) return d.siteName
        val used = jsonMap("rotUsed")
        if (used.has(d.key)) return used.getString(d.key)
        return m.rotation[prefs.getInt("rot_${m.id}", 0) % m.rotation.size]
    }

    fun stock(medId: String): Double? =
        if (prefs.contains("stock_$medId")) prefs.getFloat("stock_$medId", 0f).toDouble() else null

    fun setStock(medId: String, v: Double?) {
        if (v == null) prefs.edit().remove("stock_$medId").apply() else prefs.edit().putFloat("stock_$medId", v.toFloat()).apply()
        bump()
    }

    // measurements: dose key -> value text
    fun measurement(d: Dose): String? = jsonMap("meas").optString(d.key, "").ifEmpty { null }

    fun setMeasurement(d: Dose, value: String?) {
        val m = jsonMap("meas")
        if (value.isNullOrBlank()) m.remove(d.key) else m.put(d.key, value.trim().take(40))
        prefs.edit().putString("meas", m.toString()).apply()
        setDone(listOf(d), !value.isNullOrBlank())
    }

    /** All recorded values of a measurement, newest first: (date|time, value). */
    fun measurementsOf(medId: String): List<Pair<String, String>> {
        val m = jsonMap("meas")
        return m.keys().asSequence().filter { it.split("|").getOrNull(2) == medId }
            .map { it to m.getString(it) }.sortedByDescending { it.first }.toList()
    }

    // as-needed log: med id -> list of taken times
    fun prnLog(medId: String): List<LocalDateTime> {
        val a = runCatching { JSONArray(prefs.getString("prn_$medId", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { runCatching { LocalDateTime.parse(a.getString(it)) }.getOrNull() }
    }

    fun takePrn(m: Med, at: LocalDateTime): Boolean {
        val log = (prnLog(m.id) + at).takeLast(100)
        val e = prefs.edit().putString("prn_${m.id}", JSONArray(log.map { it.toString() }).toString())
        var low = false
        stock(m.id)?.let { st ->
            val after = st - m.amount
            e.putFloat("stock_${m.id}", after.toFloat())
            val r = m.refillAt
            low = r != null && st > r && after <= r
        }
        e.apply(); bump()
        return low
    }

    fun undoLastPrn(m: Med) {
        val log = prnLog(m.id); if (log.isEmpty()) return
        val e = prefs.edit().putString("prn_${m.id}", JSONArray(log.dropLast(1).map { it.toString() }).toString())
        stock(m.id)?.let { e.putFloat("stock_${m.id}", (it + m.amount).toFloat()) }
        e.apply(); bump()
    }
}
