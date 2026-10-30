package com.tal.eyedrops.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What kind of medication; decides the unit, the site options and whether a value is recorded. */
enum class MedType(val key: String, val label: String, val unit: String, val unitOne: String) {
    DROPS("drops", "טיפות", "טיפות", "טיפה"),
    PILL("pill", "כדורים", "כדורים", "כדור"),
    INJECTION("inj", "זריקה", "יח׳", "יח׳"),
    PATCH("patch", "מדבקה", "מדבקות", "מדבקה"),
    CREAM("cream", "משחה / קרם", "", ""),
    INHALER("inh", "משאף", "שאיפות", "שאיפה"),
    LIQUID("liquid", "סירופ / נוזל", "מ״ל", "מ״ל"),
    MEASURE("measure", "מדידה", "", ""),
    OTHER("other", "אחר", "", "");

    /** Sites are cycled dose after dose (injections, patches). */
    val rotates get() = this == INJECTION || this == PATCH
    val hasStock get() = this != MEASURE && this != CREAM
    companion object { fun of(k: String?) = values().firstOrNull { it.key == k } ?: OTHER }
}

/** One dosing stage: from [start] for [days] days, at [times] each day. */
data class Stage(val start: LocalDate, val days: Int, val times: List<LocalTime>) {
    val end: LocalDate get() = start.plusDays((days - 1).toLong())
    fun covers(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
}

/** "As needed": no fixed times, at least [minHours] apart, at most [maxPerDay] a day (0 = no limit). */
data class Prn(val minHours: Int, val maxPerDay: Int)

/**
 * A medication (or a measurement).
 * [sites]: each dose is given at every one of these and checked separately (e.g. both eyes). Empty = one check.
 * [rotation]: sites used in turn, one per dose (injections, patches).
 */
data class Med(
    val id: String,
    val name: String,
    val color: Int,
    val type: MedType,
    val amount: Double,
    val sites: List<String>,
    val rotation: List<String>,
    val note: String,
    val stages: List<Stage>,
    val prn: Prn?,
    val refillAt: Double?,
) {
    fun doseText(): String {
        if (type.unit.isEmpty() || amount <= 0) return ""
        return "${Fmt.num(amount)} ${if (amount == 1.0) type.unitOne else type.unit}"
    }
    /** The check boxes of one dose: one per site, or a single unnamed one. */
    val checks: List<String?> get() = if (sites.isEmpty()) listOf(null) else sites
}

/** The whole plan as it is saved and shared. Meds are listed in the order they are taken. */
data class PlanData(val meds: List<Med>) {
    companion object {
        val EMPTY = PlanData(emptyList())
        private const val PREFIX = "TOMA1:"

        private fun strs(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it).take(40) }

        fun fromJson(s: String): PlanData {
            val o = JSONObject(s)
            val meds = o.getJSONArray("meds").let { a ->
                (0 until a.length()).map { i ->
                    val m = a.getJSONObject(i)
                    val p = m.optJSONObject("prn")
                    Med(
                        id = m.getString("id").take(40),
                        name = m.getString("n").take(60),
                        color = m.optInt("c", 0).coerceIn(0, Palette.size - 1),
                        type = MedType.of(m.optString("t")),
                        amount = m.optDouble("a", 1.0).takeIf { !it.isNaN() } ?: 1.0,
                        sites = strs(m.optJSONArray("sites")).take(4),
                        rotation = strs(m.optJSONArray("rot")).take(12),
                        note = m.optString("note", "").take(200),
                        stages = m.optJSONArray("s").let { sa ->
                            if (sa == null) emptyList() else (0 until sa.length()).map { j ->
                                val st = sa.getJSONObject(j)
                                val t = st.getJSONArray("t")
                                Stage(
                                    LocalDate.parse(st.getString("d")),
                                    st.getInt("n").coerceIn(1, 3650),
                                    (0 until t.length()).map { LocalTime.parse(t.getString(it)) }.distinct().sorted(),
                                )
                            }
                        },
                        prn = p?.let { Prn(it.optInt("h", 4).coerceIn(0, 72), it.optInt("max", 0).coerceIn(0, 24)) },
                        refillAt = m.optDouble("refill").takeIf { !it.isNaN() },
                    )
                }
            }
            return PlanData(meds)
        }

        /** Finds a shared plan code anywhere in a text (e.g. a forwarded WhatsApp message). */
        fun fromShareText(text: String): PlanData? {
            // Some apps wrap long lines: drop any whitespace that follows the prefix.
            val start = text.indexOf("TOMA1:"); if (start < 0) return null
            val tail = text.substring(start).replace(Regex("\\s"), "")
            val m = Regex("TOMA1:([A-Za-z0-9_\\-]+=*)").find(tail) ?: return null
            return runCatching {
                fromJson(String(Base64.decode(m.groupValues[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
            }.getOrNull()
        }
    }

    fun toJson(): String = JSONObject().apply {
        put("v", 2)
        put("meds", JSONArray().apply {
            meds.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("n", m.name); put("c", m.color); put("t", m.type.key); put("a", m.amount)
                    put("sites", JSONArray(m.sites)); put("rot", JSONArray(m.rotation)); put("note", m.note)
                    m.prn?.let { put("prn", JSONObject().put("h", it.minHours).put("max", it.maxPerDay)) }
                    m.refillAt?.let { put("refill", it) }
                    put("s", JSONArray().apply {
                        m.stages.forEach { s ->
                            put(JSONObject().apply {
                                put("d", s.start.toString()); put("n", s.days)
                                put("t", JSONArray(s.times.map { Fmt.time(it) }))
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun shareCode(): String =
        PREFIX + Base64.encodeToString(toJson().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

    fun lastDay(): LocalDate? = meds.filter { it.prn == null }.flatMap { m -> m.stages.map { it.end } }.maxOrNull()
}

/** One check box: [med] at [time], for site number [site] (0 when the med has no sites). */
data class Dose(val med: Med, val site: Int, val time: LocalDateTime) {
    val key: String get() = "${time.toLocalDate()}|${time.toLocalTime()}|${med.id}|$site"
    val siteName: String? get() = med.sites.getOrNull(site)
}

// Medication colors; none close to the amber used for "now".
val Palette = listOf(0xFFC2416A, 0xFF2F7FC1, 0xFF7B4FB5, 0xFF1F8A7D, 0xFF8C4A2F, 0xFF2E7D4F, 0xFF4C5FB0, 0xFF5D6B75)

/** Turns the current plan into dated doses. "As needed" meds are not scheduled. */
object Schedule {
    fun dosesOn(date: LocalDate, plan: PlanData = Store.plan()): List<Dose> {
        val out = ArrayList<Dose>()
        plan.meds.filter { it.prn == null }.forEach { m ->
            m.stages.filter { it.covers(date) }.forEach { s ->
                s.times.forEach { t -> m.checks.indices.forEach { i -> out.add(Dose(m, i, LocalDateTime.of(date, t))) } }
            }
        }
        val order = plan.meds.withIndex().associate { it.value.id to it.index }
        return out.distinctBy { it.key }.sortedWith(compareBy({ it.time }, { order[it.med.id] ?: 0 }, { it.site }))
    }

    fun slotsOn(date: LocalDate): List<Pair<LocalDateTime, List<Dose>>> =
        dosesOn(date).groupBy { it.time }.toList().sortedBy { it.first }

    fun dosesAt(slot: LocalDateTime): List<Dose> = dosesOn(slot.toLocalDate()).filter { it.time == slot }

    fun nextSlotAfter(now: LocalDateTime): LocalDateTime? {
        val plan = Store.plan()
        val last = plan.lastDay() ?: return null
        var d = now.toLocalDate()
        while (!d.isAfter(last)) {
            dosesOn(d, plan).firstOrNull { it.time.isAfter(now) }?.let { return it.time }
            d = d.plusDays(1)
        }
        return null
    }

    /** Evenly spread times for "N times a day" (doctor-style: 2→08/20, 3→08/14/20, 4→08/12/16/20, 8→every 2h). */
    fun presetTimes(n: Int): List<LocalTime> {
        if (n <= 1) return listOf(LocalTime.of(8, 0))
        val endMin = if (n <= 4) 20 * 60 else 22 * 60
        val step = (endMin - 8 * 60) / (n - 1)
        return (0 until n).map { i ->
            val m = 8 * 60 + i * step
            val rounded = (m + 15) / 30 * 30
            LocalTime.of(rounded / 60, rounded % 60)
        }.distinct()
    }

    /** Round-the-clock "every [hours] hours" starting at [first]. */
    fun everyHours(hours: Int, first: LocalTime): List<LocalTime> =
        (0 until 24 / hours.coerceIn(1, 24)).map { first.plusHours((it * hours).toLong()) }.distinct().sorted()
}

object Fmt {
    private val days = arrayOf("שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת", "ראשון")
    fun date(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}"
    fun dateYear(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}.${d.year % 100}"
    fun weekday(d: LocalDate) = "יום " + days[d.dayOfWeek.value - 1]
    fun time(t: LocalTime) = String.format(java.util.Locale.US, "%02d:%02d", t.hour, t.minute)
    fun num(x: Double): String =
        if (x == Math.floor(x)) x.toLong().toString() else String.format(java.util.Locale.US, "%.1f", x).trimEnd('0').trimEnd('.')

    /** Presets for site choices. */
    val dropTargets = listOf("עין", "אוזן", "אף")
    val rotationSites = listOf("בטן ימין", "בטן שמאל", "ירך ימין", "ירך שמאל", "זרוע ימין", "זרוע שמאל", "ישבן ימין", "ישבן שמאל", "גב תחתון ימין", "גב תחתון שמאל")
}
