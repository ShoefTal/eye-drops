package com.tal.eyedrops.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Drug(val key: String, val label: String, val he: String, val color: Long) {
    STERODEX("ster", "Sterodex", "סטרודקס", 0xFFC2416A),
    VIGAMOX("viga", "Vigamox", "ויגמוקס", 0xFFB7791F),
    BROMSITE("brom", "Bromsite / Nevanac", "ברומסייט", 0xFF4C5FB0),
    BEPHANTEN("beph", "Bephanten", "בפנטן", 0xFF1F8A7D),
}

/** One operated eye. [start] is the first full treatment day (the 8-times day). */
data class Eye(val name: String, val start: LocalDate)

data class Dose(val eye: Int, val drug: Drug, val time: LocalDateTime) {
    val key: String get() = "${time.toLocalDate()}|${time.toLocalTime()}|${drug.key}|$eye"
}

/**
 * The doctor's plan, relative to the eye's start day (day 0 = the 8-times day).
 * Every rule covers days [fromDay .. until(start)] inclusive.
 */
object Plan {
    private fun t(vararg h: String) = h.map { LocalTime.parse(it) }

    val EIGHT = t("08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00", "22:00")
    val FOUR = t("08:00", "12:00", "16:00", "20:00")
    val THREE = t("08:00", "14:00", "20:00")
    val TWO = t("10:00", "16:00")

    class Rule(
        val drug: Drug,
        val fromDay: Long,
        val until: (LocalDate) -> LocalDate,
        val times: List<LocalTime>,
        val label: String,
    ) {
        fun from(start: LocalDate): LocalDate = start.plusDays(fromDay)
        fun to(start: LocalDate): LocalDate = until(start)
        fun covers(start: LocalDate, d: LocalDate) = !d.isBefore(from(start)) && !d.isAfter(to(start))
    }

    val rules: List<Rule> = listOf(
        Rule(Drug.STERODEX, 0, { it }, EIGHT, "יום ראשון"),
        Rule(Drug.STERODEX, 1, { it.plusDays(14) }, FOUR, "שבועות 1–2"),
        Rule(Drug.STERODEX, 15, { it.plusDays(21) }, THREE, "שבוע 3"),
        Rule(Drug.STERODEX, 22, { it.plusDays(28) }, TWO, "שבוע 4"),
        Rule(Drug.VIGAMOX, 0, { it }, EIGHT, "יום ראשון"),
        Rule(Drug.VIGAMOX, 1, { it.plusDays(14) }, FOUR, "שבועות 1–2"),
        Rule(Drug.BROMSITE, 0, { it.plusDays(21) }, THREE, "3 שבועות"),
        Rule(Drug.BEPHANTEN, 0, { it.plusMonths(6).minusDays(1) }, FOUR, "חצי שנה"),
    )

    fun dosesOn(date: LocalDate, eyes: List<Eye>): List<Dose> {
        val out = ArrayList<Dose>()
        eyes.forEachIndexed { i, eye ->
            rules.filter { it.covers(eye.start, date) }.forEach { r ->
                r.times.forEach { tm -> out.add(Dose(i, r.drug, LocalDateTime.of(date, tm))) }
            }
        }
        return out.sortedWith(compareBy({ it.time }, { it.drug.ordinal }, { it.eye }))
    }

    /** Doses grouped by time of day. */
    fun slotsOn(date: LocalDate, eyes: List<Eye>): List<Pair<LocalDateTime, List<Dose>>> =
        dosesOn(date, eyes).groupBy { it.time }.toList().sortedBy { it.first }

    fun dosesAt(slot: LocalDateTime, eyes: List<Eye>): List<Dose> =
        dosesOn(slot.toLocalDate(), eyes).filter { it.time == slot }

    fun nextSlotAfter(now: LocalDateTime, eyes: List<Eye>): LocalDateTime? {
        var d = now.toLocalDate()
        val last = lastDay(eyes) ?: return null
        while (!d.isAfter(last)) {
            slotsOn(d, eyes).firstOrNull { it.first.isAfter(now) }?.let { return it.first }
            d = d.plusDays(1)
        }
        return null
    }

    fun lastDay(eyes: List<Eye>): LocalDate? =
        eyes.flatMap { e -> rules.map { it.to(e.start) } }.maxOrNull()

    /** Short description of what applies to an eye on a date, e.g. "יום 3 · שבועות 1–2". */
    fun phaseText(date: LocalDate, eye: Eye): String {
        val day = java.time.temporal.ChronoUnit.DAYS.between(eye.start, date)
        if (day < 0) return "הטיפול מתחיל ב־${Fmt.date(eye.start)}"
        val ster = rules.firstOrNull { it.drug == Drug.STERODEX && it.covers(eye.start, date) }
        val label = ster?.label ?: if (rules.any { it.covers(eye.start, date) }) "בפנטן בלבד" else "הטיפול הסתיים"
        return "יום ${day + 1} · $label"
    }
}

object Fmt {
    private val days = arrayOf("שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת", "ראשון")
    fun date(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}"
    fun dateYear(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}.${d.year % 100}"
    fun weekday(d: LocalDate) = "יום " + days[d.dayOfWeek.value - 1]
    fun time(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
}
