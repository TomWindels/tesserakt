import dev.tesserakt.sparql.types.DateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class DateTimeTest {

    /* parsing test */

    @Test
    fun standard() {
        val dateTime = DateTime.parse("2000-01-01T01:00:00")
        assertEquals(dateTime.date.year, 2000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun explicitUtc() {
        val dateTime = DateTime.parse("2000-01-01T01:00:00Z")
        assertEquals(dateTime.date.year, 2000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun explicitTimezonePositive() {
        val dateTime = DateTime.parse("2000-01-01T01:00:00+00:00")
        assertEquals(dateTime.date.year, 2000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun explicitTimezoneNegative() {
        val dateTime = DateTime.parse("2000-01-01T01:00:00-00:00")
        assertEquals(dateTime.date.year, 2000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun longerYear() {
        val dateTime = DateTime.parse("20000-01-01T01:00:00")
        assertEquals(dateTime.date.year, 20000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun negativeYear() {
        val dateTime = DateTime.parse("-2000-01-01T01:00:00")
        assertEquals(dateTime.date.year, -2000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun longerNegativeYear() {
        val dateTime = DateTime.parse("-20000-01-01T01:00:00")
        assertEquals(dateTime.date.year, -20000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun fractionSeconds() {
        val dateTime = DateTime.parse("2000-01-01T01:00:00.123")
        assertEquals(dateTime.date.year, 2000)
        assertEquals(dateTime.date.month, 1)
        assertEquals(dateTime.date.day, 1)
        assertEquals(dateTime.time.hours, 1)
        assertEquals(dateTime.time.minutes, 0)
        assertEquals(dateTime.time.seconds, 0)
        assertEquals(dateTime.time.fraction, 0.123f)
        assertEquals(dateTime.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun badFormat() {
        assertFails {
            DateTime.parse("2000/1/01T01:00:00")
        }
    }

    @Test
    fun badMonth() {
        assertFails {
            DateTime.parse("2000-13-01T01:00:00")
        }
    }

    @Test
    fun badTime() {
        assertFails {
            DateTime.parse("2000-1-01T24:10:00")
        }
    }

    /* manipulation tests */

    @Test
    fun timeIncrement1() {
        val base = DateTime.parse("2000-01-01T01:00:00")
        val new = base.add(0, 30)
        assertEquals(new.date.year, 2000)
        assertEquals(new.date.month, 1)
        assertEquals(new.date.day, 1)
        assertEquals(new.time.hours, 1)
        assertEquals(new.time.minutes, 30)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun timeIncrement2() {
        val base = DateTime.parse("2000-01-01T01:00:00")
        val new = base.add(24, 0)
        assertEquals(new.date.year, 2000)
        assertEquals(new.date.month, 1)
        assertEquals(new.date.day, 2)
        assertEquals(new.time.hours, 1)
        assertEquals(new.time.minutes, 0)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun timeIncrement3() {
        val base = DateTime.parse("2000-01-01T01:00:00")
        val new = base.add(48, 0)
        assertEquals(new.date.year, 2000)
        assertEquals(new.date.month, 1)
        assertEquals(new.date.day, 3)
        assertEquals(new.time.hours, 1)
        assertEquals(new.time.minutes, 0)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun timeIncrement4() {
        val base = DateTime.parse("2000-01-01T01:00:00+05:00")
        val new = base.add(2, 30)
        assertEquals(new.date.year, 2000)
        assertEquals(new.date.month, 1)
        assertEquals(new.date.day, 1)
        assertEquals(new.time.hours, 3)
        assertEquals(new.time.minutes, 30)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone(5 * 60))
    }

    @Test
    fun timeDecrement1() {
        val base = DateTime.parse("2000-01-01T01:00:00")
        val new = base.subtract(0, 30)
        assertEquals(new.date.year, 2000)
        assertEquals(new.date.month, 1)
        assertEquals(new.date.day, 1)
        assertEquals(new.time.hours, 0)
        assertEquals(new.time.minutes, 30)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun timeDecrement2() {
        val base = DateTime.parse("2000-01-01T01:00:00")
        val new = base.subtract(24, 0)
        assertEquals(new.date.year, 1999)
        assertEquals(new.date.month, 12)
        assertEquals(new.date.day, 31)
        assertEquals(new.time.hours, 1)
        assertEquals(new.time.minutes, 0)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun timeDecrement3() {
        val base = DateTime.parse("2000-01-01T01:00:00")
        val new = base.subtract(48, 0)
        assertEquals(new.date.year, 1999)
        assertEquals(new.date.month, 12)
        assertEquals(new.date.day, 30)
        assertEquals(new.time.hours, 1)
        assertEquals(new.time.minutes, 0)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone.UTC)
    }

    @Test
    fun timeDecrement4() {
        val base = DateTime.parse("2000-01-01T01:00:00+05:00")
        val new = base.subtract(2, 30)
        assertEquals(new.date.year, 1999)
        assertEquals(new.date.month, 12)
        assertEquals(new.date.day, 31)
        assertEquals(new.time.hours, 22)
        assertEquals(new.time.minutes, 30)
        assertEquals(new.time.seconds, 0)
        assertEquals(new.timezone, DateTime.Timezone(5 * 60))
    }

    /* comparison tests */

    @Test
    fun comparison1() {
        val one = DateTime.parse("2000-01-01T01:02:00")
        val two = DateTime.parse("2000-01-01T01:01:00")
        assertTrue { one > two }
    }

    @Test
    fun comparison2() {
        val one = DateTime.parse("2000-01-01T01:00:00+05:00")
        val two = DateTime.parse("2000-01-01T01:00:00+05:00")
        assertTrue { one == two }
    }

    @Test
    fun comparison3() {
        val one = DateTime.parse("2000-01-01T01:00:00+05:00")
        val two = DateTime.parse("2000-01-01T01:00:00Z")
        assertTrue { one < two }
    }

}
