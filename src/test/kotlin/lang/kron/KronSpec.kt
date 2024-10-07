package lang.kron

import kotlin.test.Ignore
import kotlin.test.Test

sealed interface Kron {
    sealed interface Expr {

    }
}

fun <A> A.mustBe(expected: A) {
    if (this != expected) {
        throw AssertionError("Expected $expected but got $this")
    }
}

class KronSpec {
    fun parse(input: String): Kron = TODO()

    // # Absolute Time Expressions
    @Ignore
    @Test fun `absolute time expressions`() {
        // Full ISO 8601 (e.g., "2023-09-15T14:30:00Z")
        parse("2021-01-01T00:00:00Z") // mustBe Kron.AbsoluteTime("2021-01-01T00:00:00Z")
        // Date only (e.g., "2023-09-15", "15/09/2023", "09/15/2023")
        parse("2023-09-15") // mustBe Kron.AbsoluteTime("2023-09-15")
        parse("15/09/2023") // mustBe Kron.AbsoluteTime("2023-09-15")
        parse("09/15/2023") // mustBe Kron.AbsoluteTime("2023-09-15")
        // Time only (e.g., "14:30", "2:30 PM")
        parse("14:30") // mustBe Kron.Expr.AbsoluteTime("14:30")
        parse("2:30 PM") // mustBe Kron.Expr.AbsoluteTime("14:30")
        // Named dates (e.g., "Christmas 2023", "New Year's Eve")
        parse("Christmas 2023") // mustBe Kron.Expr.AbsoluteTime("2023-12-25")
        parse("New Year's Eve") // mustBe Kron.Expr.AbsoluteTime("2023-12-31")
    }

    // Relative Time Expressions
    @Ignore
    @Test fun `relative time expressions`() {
        // Immediate (e.g., "now", "immediately", "right away")
        parse("now") // mustBe Kron.Now
        parse("immediately") // mustBe Kron.Now
        parse("right away") // mustBe Kron.Now

        // Near future (e.g., "soon", "in a bit", "shortly")
        parse("soon") // mustBe Kron.Expr.RelativeTime.Soon
        parse("in a bit") // mustBe Kron.Expr.RelativeTime.Soon
        parse("shortly") // mustBe Kron.Expr.RelativeTime.Soon

        // Near past (e.g., "just now", "a moment ago")
        parse("just now") // mustBe Kron.RelativeTime.JustNow
        parse("a moment ago") // mustBe Kron.RelativeTime.JustNow

        // Vague future (e.g., "later", "afterwards")
        parse("later") // mustBe Kron.RelativeTime.Later
        parse("afterwards") // mustBe Kron.RelativeTime.Later

        // Vague past (e.g., "earlier", "before")
        parse("earlier") // mustBe Kron.RelativeTime.Earlier
        parse("before") // mustBe Kron.RelativeTime.Earlier

        // Additional: "By the end of the day", "Later today", "Earlier today"
        parse("By the end of the day") // mustBe Kron.Before(Kron.EndOfDay)
        parse("Later today") // mustBe Kron.Before(Kron.EndOfDay)
        parse("Earlier today") // mustBe Kron.InBetween(Kron.StartOfDay, Kron.Now)
    }

    // Day-relative
    @Ignore
    @Test fun `day-relative expressions`() {
        //Next day (e.g., "tomorrow", "the day after tomorrow")
        parse("tomorrow") // mustBe Kron.Expr.DayRelative(1)
        parse("the day after tomorrow") // mustBe Kron.Expr.DayRelative(2)
        //Previous day (e.g., "yesterday", "the day before yesterday")
        parse("yesterday") // mustBe Kron.Expr.DayRelative(-1)
        parse("the day before yesterday") // mustBe Kron.Expr.DayRelative(-2)
        //Additional: "Today afternoon", "Tonight", "Tomorrow morning", "Tomorrow afternoon"

    }
}
