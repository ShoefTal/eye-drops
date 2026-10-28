package com.tal.eyedrops.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One dosing stage: from [start] for [days] days, at [times] each day. */
data class Stage(val start: LocalDate, val days: Int, val times: List<LocalTime>) {
    val end: LocalDate get() = start.plusDays((days - 1).toLong())
    fun covers(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
}

/** A medication with its stages. [eyes] = indices of the eyes it is given to. */
data class Med(
    val id: String,
    val name: String,
    val color: Int,
    val eyes: List<Int>,
    val stages: List<Stage>,
)

/** The whole plan as it is saved and shared. Meds are listed in the order the drops are given. */
data class PlanData(val eyes: List<String>, val meds: List<Med>) {
    companion object {
        val EMPTY = PlanData(listOf("ימין"), emptyList())
        private const val PREFIX = "TIPOT1:"

        fun fromJson(s: String): PlanData {
            val o = JSONObject(s)
            val eyesArr = o.optJSONArray("eyes")
            val eyes = if (eyesArr == null || eyesArr.length() == 0) listOf("ימין")
            else (0 until eyesArr.length()).map { eyesArr.getString(it) }.take(2)
            val meds = o.getJSONArray("meds").let { a ->
                (0 until a.length()).map { i ->
                    val m = a.getJSONObject(i)
                    val e = m.optJSONArray("e")
                    Med(
                        id = m.getString("id"),
                        name = m.getString("n").take(60),
                        color = m.optInt("c", 0).coerceIn(0, Palette.size - 1),
                        eyes = if (e == null) listOf(0) else (0 until e.length()).map { e.getInt(it) }.filter { it in 0..1 },
                        stages = m.getJSONArray("s").let { sa ->
                            (0 until sa.length()).map { j ->
                                val st = sa.getJSONObject(j)
                                val t = st.getJSONArray("t")
                                Stage(
                                    LocalDate.parse(st.getString("d")),
                                    st.getInt("n").coerceIn(1, 3650),
                                    (0 until t.length()).map { LocalTime.parse(t.getString(it)) }.distinct().sorted(),
                                )
                            }
                        },
                    )
                }
            }
            return PlanData(eyes, meds)
        }

        /** Finds a shared plan code anywhere in a text (e.g. a forwarded WhatsApp message). */
        fun fromShareText(text: String): PlanData? {
            val m = Regex("TIPOT1:([A-Za-z0-9_\\-]+=*)").find(text) ?: return null
            return runCatching {
                val json = String(Base64.decode(m.groupValues[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                fromJson(json)
            }.getOrNull()
        }
    }

    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("eyes", JSONArray(eyes))
        put("meds", JSONArray().apply {
            meds.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("n", m.name); put("c", m.color); put("e", JSONArray(m.eyes))
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

    fun lastDay(): LocalDate? = meds.flatMap { m -> m.stages.map { it.end } }.maxOrNull()
}

data class Dose(val med: Med, val eye: Int, val time: LocalDateTime) {
    val key: String get() = "${time.toLocalDate()}|${time.toLocalTime()}|${med.id}|$eye"
}

val Palette = listOf(0xFFC2416A, 0xFFB7791F, 0xFF4C5FB0, 0xFF1F8A7D, 0xFF7B4FB5, 0xFF2E7D4F, 0xFFD0602A, 0xFF5D6B75)

/** Turns the current plan into dated doses. */
object Schedule {
    fun dosesOn(date: LocalDate, plan: PlanData = Store.plan()): List<Dose> {
        val eyeCount = plan.eyes.size
        val out = ArrayList<Dose>()
        plan.meds.forEach { m ->
            val eyes = m.eyes.filter { it < eyeCount }.ifEmpty { listOf(0) }
            m.stages.filter { it.covers(date) }.forEach { s ->
                s.times.forEach { t -> eyes.forEach { e -> out.add(Dose(m, e, LocalDateTime.of(date, t))) } }
            }
        }
        val order = plan.meds.withIndex().associate { it.value.id to it.index }
        return out.distinctBy { it.key }.sortedWith(compareBy({ it.time }, { order[it.med.id] ?: 0 }, { it.eye }))
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
}

object Fmt {
    private val days = arrayOf("שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת", "ראשון")
    fun date(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}"
    fun dateYear(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}.${d.year % 100}"
    fun weekday(d: LocalDate) = "יום " + days[d.dayOfWeek.value - 1]
    fun time(t: LocalTime) = String.format(java.util.Locale.US, "%02d:%02d", t.hour, t.minute)
}
