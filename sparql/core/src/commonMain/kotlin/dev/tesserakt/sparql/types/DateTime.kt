package dev.tesserakt.sparql.types

import kotlin.math.absoluteValue

@ConsistentCopyVisibility
data class DateTime internal constructor(
    val date: Date,
    val time: Time,
    val timezone: Timezone,
) : Comparable<DateTime> {

    @ConsistentCopyVisibility
    data class Date internal constructor(
        // can be negative!
        internal val year: Int,
        internal val month: Int,
        internal val day: Int,
    ) : Comparable<Date> {

        init {
            check(month in 1..12)
            check(day in 1..daysInMonth(month, year))
        }

        override fun compareTo(other: Date): Int {
            return when {
                year > other.year -> 1
                year < other.year -> -1
                month > other.month -> 1
                month < other.month -> -1
                else -> day - other.day
            }
        }

        fun increment(): Date {
            return if (day < daysInMonth(month, year)) {
                copy(
                    day = day + 1,
                )
            } else if (month < 11) {
                copy(
                    month = month + 1,
                    day = 1
                )
            } else {
                copy(
                    year = year + 1,
                    month = 1,
                    day = 1
                )
            }
        }

        fun decrement(): Date {
            return if (day > 1) {
                copy(day = day - 1)
            } else if (month > 1) {
                copy(
                    month = month - 1,
                    day = daysInMonth(month - 1, year),
                )
            } else {
                copy(
                    year = year - 1,
                    month = 12,
                    day = 31,
                )
            }
        }

        override fun toString(): String {
            return "$year-$month-$day"
        }

    }

    @ConsistentCopyVisibility
    data class Time internal constructor(
        internal val hours: Int,
        internal val minutes: Int,
        internal val seconds: Int,
        // [0..1[ seconds
        internal val fraction: Float,
    ) : Comparable<Time> {

        init {
            check(hours in 0..24)
            check(minutes >= 0)
            check(hours <= 23 && minutes <= 60 || minutes < 60)
            check(seconds >= 0)
            check(hours <= 23 && seconds <= 60 || seconds < 60)
        }

        val inSeconds: Float
            get() = hours * 3600 + minutes * 60 + seconds + fraction

        override fun compareTo(other: Time): Int {
            val result = inSeconds - other.inSeconds
            // mapping it to integers w/o dropping the fractional part
            return when {
                result < 0f -> -1
                result > 0f -> 1
                else /* result == 0f */ -> 0
            }
        }

        override fun toString(): String {
            return "$hours:$minutes:${seconds + fraction}"
        }

    }

    @ConsistentCopyVisibility
    data class Timezone internal constructor(
        /** [-14 * 60, +14 * 60] **/
        internal val inWholeMinutes: Int
    ) {

        companion object {
            val UTC = Timezone(
                inWholeMinutes = 0,
            )
        }

        override fun toString(): String {
            return if (inWholeMinutes == 0) {
                "Z"
            } else if (inWholeMinutes > 0) {
                "+${inWholeMinutes % 60}:${inWholeMinutes / 60}"
            } else {
                "-${-inWholeMinutes % 60}:${-inWholeMinutes / 60}"
            }
        }
    }

    override fun compareTo(other: DateTime): Int {
        val a = normalized()
        val b = other.normalized()
        val dates = a.date.compareTo(b.date)
        return if (dates == 0) a.time.compareTo(b.time) else dates
    }

    fun add(hours: Int, minutes: Int): DateTime {
        check(hours >= 0)
        check(minutes >= 0)
        val rawMinutes = time.minutes + minutes
        val rawHours = time.hours + hours + rawMinutes / 60
        val newTime = time.copy(
            // ensuring we end up with positive values, so using `mod` instead of `%`
            hours = rawHours.mod(24),
            minutes = rawMinutes.mod(60)
        )
        val extraDays = rawHours / 24
        var newDate = date
        // not ideal
        repeat(extraDays) {
            newDate = newDate.increment()
        }
        return copy(
            date = newDate,
            time = newTime,
        )
    }

    fun subtract(hours: Int, minutes: Int): DateTime {
        check(hours >= 0)
        check(minutes >= 0)
        val rawMinutes = time.minutes - minutes
        val rawHours = time.hours - hours + if (rawMinutes < 0) {
            rawMinutes / 60 - 1
        } else {
            0
        }
        val newTime = time.copy(
            // ensuring we end up with positive values, so using `mod` instead of `%`
            hours = rawHours.mod(24),
            minutes = rawMinutes.mod(60)
        )
        val missingDays = (-rawHours / 24) + if (rawHours < 0) 1 else 0
        var newDate = date
        // not ideal
        repeat(missingDays) {
            newDate = newDate.decrement()
        }
        return copy(
            date = newDate,
            time = newTime,
        )
    }

    private fun normalized(): DateTime {
        return if (timezone.inWholeMinutes < 0) {
            val hours = (timezone.inWholeMinutes / 60).absoluteValue
            val minutes = (timezone.inWholeMinutes % 60).absoluteValue
            add(hours, minutes).copy(timezone = Timezone.UTC)
        } else if (timezone.inWholeMinutes > 0) {
            val hours = (timezone.inWholeMinutes / 60)
            val minutes = (timezone.inWholeMinutes % 60)
            subtract(hours, minutes).copy(timezone = Timezone.UTC)
        } else {
            this
        }
    }

    /**
     * Returns a string representation. It is **NOT** guaranteed for this representation to match
     *  the spec
     */
    override fun toString(): String {
        return "${date}T${time}$timezone"
    }

    companion object {

        fun parse(xsdDateTime: String): DateTime = try {
            // manually searching for the first `-`, as a year is at least 4 digits long, and can have a minus
            //  sign at the front
            val delimiter = xsdDateTime.indexOf('-', startIndex = 1)
            // also checking if a timezone is present, by checking whether a `+` or `-` is at the expected position
            //  relative to the string end
            val hasUtcTimezone = xsdDateTime.last() == 'Z'
            val hasFullTimeZone = !hasUtcTimezone && run {
                val sign = xsdDateTime[xsdDateTime.length - 6]
                sign == '+' || sign == '-'
            }
            /* date segment */
            val year = xsdDateTime.substring(0, delimiter).toInt()
            // subsequent numbers are a fixed offset from this delimiter, and can be obtained from it directly
            xsdDateTime.expectChar('-', delimiter + 3)
            xsdDateTime.expectChar('T', delimiter + 6)
            // subsequent segments are optional, so are checked later
            val month = xsdDateTime.substring(delimiter + 1, delimiter + 3).toInt()
            val day = xsdDateTime.substring(delimiter + 4, delimiter + 6).toInt()
            val date = Date(
                year = year,
                month = month,
                day = day,
            )

            /* time segment */
            xsdDateTime.expectChar(':', delimiter + 9)
            xsdDateTime.expectChar(':', delimiter + 12)
            val hours = xsdDateTime.substring(delimiter + 7, delimiter + 9).toInt()
            val minutes = xsdDateTime.substring(delimiter + 10, delimiter + 12).toInt()
            val seconds = xsdDateTime.substring(delimiter + 13, delimiter + 15).toInt()
            val fraction = if (xsdDateTime.getOrNull(delimiter + 15) == '.') {
                ('0' + xsdDateTime.substring(
                    startIndex = delimiter + 15,
                    endIndex = if (hasFullTimeZone) {
                        xsdDateTime.length - 6
                    } else if (hasUtcTimezone) {
                        xsdDateTime.length - 1
                    } else {
                        xsdDateTime.length
                    }
                )).toFloat()
            } else {
                0f
            }
            val time = Time(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                fraction = fraction,
            )

            /* timezone segment */
            val timezone = if (hasFullTimeZone) {
                check(xsdDateTime[xsdDateTime.length - 3] == ':')
                val hours = xsdDateTime.substring(xsdDateTime.length - 6, xsdDateTime.length - 3).toInt()
                val minutes = xsdDateTime.substring(xsdDateTime.length - 2).toInt()
                Timezone(inWholeMinutes = hours * 60 + minutes)
            } else {
                Timezone.UTC
            }

            DateTime(
                date = date,
                time = time,
                timezone = timezone,
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date format: `${xsdDateTime}`", e)
        }

        private fun daysInMonth(month: Int, year: Int): Int {
            return when (month) {
                2 -> {
                    val isLeap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
                    if (isLeap) 29 else 28
                }
                1, 3, 5, 7, 8, 10, 12 -> {
                    31
                }
                else -> {
                    30
                }
            }
        }

    }

}

private inline fun String.expectChar(expected: Char, pos: Int) {
    check(this[pos] == expected) { "Expected `$expected` at ${pos}, got `${this[pos]}` instead!" }
}
