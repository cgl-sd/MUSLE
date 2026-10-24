package com.musclejournal.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HistoryPeriodTest {
    @Test fun dayHasExactDate(){
        val date=LocalDate.parse("2026-10-20")
        assertEquals(date..date,HistoryPeriod.DAY.bounds(date))
        assertEquals(LocalDate.parse("2026-10-19"),HistoryPeriod.DAY.shift(date,-1))
    }
    @Test fun weekCrossesYearAndStartsMonday(){
        val range=HistoryPeriod.WEEK.bounds(LocalDate.parse("2027-01-01"))
        assertEquals(LocalDate.parse("2026-12-28"),range.start)
        assertEquals(LocalDate.parse("2027-01-03"),range.endInclusive)
        assertFalse(LocalDate.parse("2027-01-04") in range)
        assertEquals(LocalDate.parse("2027-01-08"),HistoryPeriod.WEEK.shift(LocalDate.parse("2027-01-01"),1))
    }
    @Test fun monthSupportsLeapYearsAndShortMonths(){
        val range=HistoryPeriod.MONTH.bounds(LocalDate.parse("2024-02-15"))
        assertEquals(LocalDate.parse("2024-02-01"),range.start)
        assertEquals(LocalDate.parse("2024-02-29"),range.endInclusive)
        assertEquals(LocalDate.parse("2026-02-28"),HistoryPeriod.MONTH.shift(LocalDate.parse("2026-01-31"),1))
    }
}
