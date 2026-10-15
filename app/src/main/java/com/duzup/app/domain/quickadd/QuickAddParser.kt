package com.duzup.app.domain.quickadd

import com.duzup.app.core.model.IntervalUnit
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * What a line of free text probably means. It only PREFILLS the normal form; the user always confirms.
 *
 * [date] / [time] are null when the text did not say. [dateHasYear] matters for birthdays (a birth year means the
 * age can be shown).
 */
data class QuickAddResult(
    val kind: ItemKind,
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val extraTimes: List<LocalTime> = emptyList(),
    val recurrence: Recurrence = Recurrence.None,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val amountText: String? = null,
    val dateHasYear: Boolean = false,
)

/**
 * Offline, rule-based parser for lines such as "call mom tomorrow 6pm", "pay rent rs 45000 on the 1st",
 * "gym every mon wed fri 7am" or "sara's birthday mar 5". No network, no AI: a handful of regexes and word lists.
 * Unknown text simply becomes a note.
 */
object QuickAddParser {

    private val weekdayWords = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val monthWords = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private const val WEEKDAY = "monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun"
    private const val MONTH = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private val I = RegexOption.IGNORE_CASE

    private val reminderPrefix = Regex("""^\s*(?:please\s+)?(?:remind me to|remind me|reminder to|reminder|remember to|don'?t forget to)\s+""", I)
    private val everyCustom = Regex("""\bevery\s+(\d+)\s+(day|week|month|year)s?\b""", I)
    private val everyWeekdays = Regex("""\bevery\s+((?:$WEEKDAY)\b(?:\s*(?:,|and|&)?\s*(?:$WEEKDAY)\b)*)""", I)
    private val everyUnit = Regex("""\bevery\s+(day|week|month|year)\b""", I)
    private val everyPartOfDay = Regex("""\bevery\s+(morning|afternoon|evening|night)\b""", I)
    private val adverbs = Regex("""\b(daily|weekly|monthly|yearly|annually)\b""", I)
    private val relative = Regex("""\bin\s+(\d+|an?|half an?)\s*(min|mins|minutes?|hrs?|hours?|days?|weeks?)\b""", I)
    private val moneyBefore = Regex("""(?:\b(?:rs\.?|pkr|usd|inr|aed|eur|gbp)\s?|[$€£₹])(\d[\d,]*(?:\.\d+)?)""", I)
    private val moneyAfter = Regex("""(\d[\d,]*(?:\.\d+)?)\s?(?:\b(?:rs\.?|pkr|usd|dollars?|bucks)\b|[$€£₹])""", I)
    private val dayAfterTomorrow = Regex("""\bday after tomorrow\b""", I)
    private val tomorrow = Regex("""\b(?:tomorrow|tmrw|tmr)\b""", I)
    private val tonight = Regex("""\btonight\b""", I)
    private val today = Regex("""\btoday\b""", I)
    private val nextWeek = Regex("""\bnext week\b""", I)
    private val nextMonth = Regex("""\bnext month\b""", I)
    private val weekdayDate = Regex("""\b(?:(next|this|on)\s+)?($WEEKDAY)\b""", I)
    private val isoDate = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val monthDay = Regex("""\b(?:on\s+)?($MONTH)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""", I)
    private val dayMonth = Regex("""\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTH)\b\.?(?:,?\s+(\d{4}))?""", I)
    private val dayOfMonth = Regex("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)\b""", I)
    private val timeColon = Regex("""(?:\bat\s+)?\b(\d{1,2}):(\d{2})\s*(a\.?m\.?|p\.?m\.?)?""", I)
    private val timeSuffix = Regex("""(?:\bat\s+)?\b(\d{1,2})\s*(a\.?m\.?|p\.?m\.?)""", I)
    private val timeBare = Regex("""\bat\s+(\d{1,2})\b""", I)
    private val timeWords = Regex("""\b(?:in the |this )?(noon|midnight|morning|afternoon|evening)\b""", I)

    private val birthdayWords = Regex("""\b(birthday|bday|b-day|anniversary)\b""", I)
    private val billWords = Regex("""\b(pay|bill|rent|due|subscription|premium|instal+ment|emi|fees?|tuition|loan|invoice|electricity|internet|netflix|spotify|insurance|mortgage)\b""", I)
    private val routineVerbs = Regex("""\b(take|drink|gym|workout|work out|meds?|medicine|vitamins?|stretch|walk|meditate|run|yoga|journal|brush|floss|read|sleep|wake)\b""", I)
    private val eventWords = Regex("""\b(meeting|dinner|lunch|breakfast|party|appointment|flight|exam|class|concert|match|interview|trip|hangout|movie|show|game|sync|standup|catch ?up|meetup|wedding|graduation|doctor|dentist|date night|call with)\b""", I)

    fun parse(input: String, now: LocalDateTime): QuickAddResult? {
        val original = input.trim()
        if (original.isEmpty()) return null
        var text = original.replace(reminderPrefix, "")
        val today0 = now.toLocalDate()

        fun take(regex: Regex): MatchResult? {
            val m = regex.find(text) ?: return null
            text = text.replaceRange(m.range, " ")
            return m
        }

        // ---- amount ----
        var amount: String? = null
        take(moneyBefore)?.let { amount = it.groupValues[1].replace(",", "") }
        if (amount == null) take(moneyAfter)?.let { amount = it.groupValues[1].replace(",", "") }

        // ---- recurrence ----
        var recurrence = Recurrence.None
        val weekdays = linkedSetOf<DayOfWeek>()
        var impliedTime: LocalTime? = null
        take(everyCustom)?.let { m ->
            val unit = when (m.groupValues[2].lowercase()) {
                "day" -> IntervalUnit.DAY
                "week" -> IntervalUnit.WEEK
                "month" -> IntervalUnit.MONTH
                else -> IntervalUnit.YEAR
            }
            recurrence = Recurrence(RecurrenceRule.CUSTOM, m.groupValues[1].toInt().coerceIn(1, Recurrence.MAX_INTERVAL), unit)
        }
        if (!recurrence.isRecurring) {
            take(everyWeekdays)?.let { m ->
                Regex(WEEKDAY, I).findAll(m.groupValues[1]).forEach { weekdays += weekdayWords.getValue(it.value.lowercase()) }
                recurrence = Recurrence(RecurrenceRule.WEEKLY)
            }
        }
        if (!recurrence.isRecurring) {
            take(everyPartOfDay)?.let { m ->
                recurrence = Recurrence(RecurrenceRule.DAILY)
                impliedTime = partOfDayTime(m.groupValues[1])
            }
        }
        if (!recurrence.isRecurring) {
            val m = take(everyUnit) ?: take(adverbs)
            if (m != null) recurrence = Recurrence(ruleFor(m.groupValues[1]))
        }

        // ---- date ----
        var date: LocalDate? = null
        var dateHasYear = false
        var time: LocalTime? = null

        take(relative)?.let { m ->
            val n = when (val raw = m.groupValues[1].lowercase()) {
                "a", "an" -> 1L
                "half a", "half an" -> 0L
                else -> raw.toLong()
            }
            val unit = m.groupValues[2].lowercase()
            when {
                unit.startsWith("min") -> now.plusMinutes(n).also { date = it.toLocalDate(); time = it.toLocalTime().truncatedTo(ChronoUnit.MINUTES) }
                unit.startsWith("h") -> now.plusMinutes(if (m.groupValues[1].startsWith("half", true)) 30 else n * 60)
                    .also { date = it.toLocalDate(); time = it.toLocalTime().truncatedTo(ChronoUnit.MINUTES) }
                unit.startsWith("d") -> date = today0.plusDays(n)
                else -> date = today0.plusWeeks(n)
            }
        }
        if (date == null) {
            when {
                take(dayAfterTomorrow) != null -> date = today0.plusDays(2)
                take(tomorrow) != null -> date = today0.plusDays(1)
                take(tonight) != null -> { date = today0; if (impliedTime == null) impliedTime = LocalTime.of(20, 0) }
                take(today) != null -> date = today0
                take(nextWeek) != null -> date = today0.plusWeeks(1)
                take(nextMonth) != null -> date = today0.plusMonths(1)
            }
        }
        if (date == null) {
            take(isoDate)?.let { m ->
                date = runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
                dateHasYear = date != null
            }
        }
        if (date == null) {
            val m = take(monthDay)
            if (m != null) {
                date = resolveMonthDay(monthWords.getValue(m.groupValues[1].lowercase().take(3)), m.groupValues[2].toInt(), m.groupValues[3], today0)
                dateHasYear = m.groupValues[3].isNotEmpty()
            } else {
                val d = take(dayMonth)
                if (d != null) {
                    date = resolveMonthDay(monthWords.getValue(d.groupValues[2].lowercase().take(3)), d.groupValues[1].toInt(), d.groupValues[3], today0)
                    dateHasYear = d.groupValues[3].isNotEmpty()
                }
            }
        }
        if (date == null) {
            take(dayOfMonth)?.let { m -> date = nextDayOfMonth(m.groupValues[1].toInt(), today0) }
        }
        if (date == null && weekdays.isEmpty()) {
            take(weekdayDate)?.let { m ->
                val day = weekdayWords.getValue(m.groupValues[2].lowercase())
                date = nextWeekday(day, today0)
            }
        }

        // ---- times ----
        val times = mutableListOf<LocalTime>()
        fun collectTimes() {
            while (true) {
                val m = take(timeColon) ?: take(timeSuffix) ?: take(timeBare) ?: break
                val t = when (m.groupValues.size) {
                    4 -> toTime(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3])
                    3 -> toTime(m.groupValues[1].toInt(), 0, m.groupValues[2])
                    else -> toTime(m.groupValues[1].toInt(), 0, "")
                }
                if (t != null) times += t
            }
        }
        collectTimes()
        take(timeWords)?.let { m -> if (times.isEmpty()) impliedTime = wordTime(m.groupValues[1]) }
        if (time != null) times.add(0, time!!)
        if (times.isEmpty() && impliedTime != null) times += impliedTime!!
        val distinctTimes = times.distinct().sorted()
        val first = if (time != null) time else distinctTimes.firstOrNull()

        // ---- fill gaps ----
        if (date == null && weekdays.isNotEmpty() && recurrence.isRecurring) {
            date = weekdays.map { nextWeekday(it, today0, includeToday = true) }.minOrNull()
        }
        if (date == null && first != null) {
            date = if (first.isAfter(now.toLocalTime())) today0 else today0.plusDays(1)
        }

        val remaining = tidyTitle(text, original)
        val lower = remaining.lowercase()

        // ---- kind ----
        val isEvent = eventWords.containsMatchIn(lower)
        val hasWhen = date != null || first != null
        val kind = when {
            birthdayWords.containsMatchIn(lower) -> ItemKind.BIRTHDAY
            amount != null || billWords.containsMatchIn(lower) -> ItemKind.BILL
            weekdays.size > 1 -> ItemKind.ROUTINE
            recurrence.rule == RecurrenceRule.DAILY && !isEvent -> ItemKind.ROUTINE
            weekdays.size == 1 && !isEvent -> ItemKind.ROUTINE
            isEvent && hasWhen -> ItemKind.EVENT
            hasWhen || recurrence.isRecurring -> ItemKind.TASK
            else -> ItemKind.MEMO
        }

        var finalRecurrence = recurrence
        var finalDate = date
        var finalWeekdays: Set<DayOfWeek> = emptySet()
        when (kind) {
            ItemKind.BIRTHDAY -> finalRecurrence = Recurrence(RecurrenceRule.YEARLY)
            ItemKind.ROUTINE -> {
                finalRecurrence = Recurrence(RecurrenceRule.DAILY)
                finalWeekdays = weekdays
                if (finalDate == null) finalDate = today0
            }
            else -> Unit
        }

        return QuickAddResult(
            kind = kind,
            title = remaining,
            date = finalDate,
            time = first,
            extraTimes = distinctTimes.filter { it != first },
            recurrence = finalRecurrence,
            weekdays = finalWeekdays,
            amountText = if (kind == ItemKind.BILL) amount else null,
            dateHasYear = dateHasYear,
        )
    }

    private fun ruleFor(word: String): RecurrenceRule = when (word.lowercase()) {
        "day", "daily" -> RecurrenceRule.DAILY
        "week", "weekly" -> RecurrenceRule.WEEKLY
        "month", "monthly" -> RecurrenceRule.MONTHLY
        else -> RecurrenceRule.YEARLY
    }

    private fun partOfDayTime(word: String): LocalTime = when (word.lowercase()) {
        "morning" -> LocalTime.of(9, 0)
        "afternoon" -> LocalTime.of(15, 0)
        "evening" -> LocalTime.of(19, 0)
        else -> LocalTime.of(21, 0)
    }

    private fun wordTime(word: String): LocalTime = when (word.lowercase()) {
        "noon" -> LocalTime.NOON
        "midnight" -> LocalTime.MIDNIGHT
        else -> partOfDayTime(word)
    }

    /**
     * 12-hour clock with am/pm, otherwise 24-hour. A bare "at 5" is read as evening for 1-6 and morning for 7-11,
     * which is how people usually mean it.
     */
    private fun toTime(hour: Int, minute: Int, suffix: String): LocalTime? {
        if (minute !in 0..59) return null
        val s = suffix.lowercase().replace(".", "")
        val h = when {
            s == "am" -> if (hour in 1..12) hour % 12 else return null
            s == "pm" -> if (hour in 1..12) hour % 12 + 12 else return null
            hour in 0..23 && s.isEmpty() && hour in 1..6 && minute == 0 -> hour + 12
            hour in 0..23 -> hour
            else -> return null
        }
        return LocalTime.of(h, minute)
    }

    private fun resolveMonthDay(month: Int, day: Int, yearText: String, today: LocalDate): LocalDate? {
        if (yearText.isNotEmpty()) return runCatching { LocalDate.of(yearText.toInt(), month, day) }.getOrNull()
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (thisYear.isBefore(today)) runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() else thisYear
    }

    private fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        var candidate = today.withDayOfMonth(minOf(day, today.lengthOfMonth()))
        if (candidate.isBefore(today)) {
            val next = today.plusMonths(1)
            candidate = next.withDayOfMonth(minOf(day, next.lengthOfMonth()))
        }
        return candidate
    }

    private fun nextWeekday(day: DayOfWeek, today: LocalDate, includeToday: Boolean = false): LocalDate {
        var diff = (day.value - today.dayOfWeek.value + 7) % 7
        if (diff == 0 && !includeToday) diff = 7
        return today.plusDays(diff.toLong())
    }

    /** Removes filler and capitalises: "  to call mom at " becomes "Call mom". */
    private fun tidyTitle(rest: String, original: String): String {
        var t = rest.replace(Regex("""\s+"""), " ").trim()
        val filler = Regex("""^(?:to|on|at|by|for|in|the|due|and|this|next|every|me)\b\s*|\s*\b(?:to|on|at|by|for|in|the|due|and|this|next|every|me)$""", I)
        var previous: String
        do {
            previous = t
            t = t.replace(filler, "").trim().trim(',', '.', '-', ';', ':')
        } while (t != previous && t.isNotEmpty())
        if (t.isEmpty()) t = original.trim()
        return t.replaceFirstChar { it.uppercase() }
    }
}
