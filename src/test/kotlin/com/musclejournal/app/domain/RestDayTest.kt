package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RestDayTest {
    private val date=LocalDate.parse("2026-10-20")
    @Test fun cancelsOnlyUnstartedPlansForSelectedDay(){
        val today=Occurrence(id="today",courseId="course",date=date.toString())
        val tomorrow=today.copy(id="tomorrow",date=date.plusDays(1).toString())
        val result=Actions.restDay(Journal(occurrences=listOf(today,tomorrow)),date,1234)
        assertEquals(OccurrenceStatus.CANCELLED,result.occurrences.first {it.id=="today"}.status)
        assertEquals(tomorrow,result.occurrences.first {it.id=="tomorrow"})
        assertEquals(1,result.occurrences.count {it.status==OccurrenceStatus.REST})
    }
    @Test fun preservesStartedAndCompletedTrainingFacts(){
        val active=Occurrence(id="active",courseId="course",date=date.toString())
        val completed=active.copy(id="completed")
        val sessions=listOf(Session(id="a",title="active",startedAt=1,exerciseIds=listOf("plank"),occurrenceId=active.id),Session(id="b",title="done",startedAt=1,endedAt=100,status=SessionStatus.COMPLETED,exerciseIds=listOf("plank"),occurrenceId=completed.id))
        val sets=listOf(SetEntry(id="set",sessionId="b",exerciseId="plank",completedAt=50,seconds=30))
        val original=Journal(occurrences=listOf(active,completed),sessions=sessions,sets=sets)
        val result=Actions.restDay(original,date)
        assertEquals(original.sessions,result.sessions);assertEquals(original.sets,result.sets)
        assertEquals(listOf(active,completed),result.occurrences.filter {it.status!=OccurrenceStatus.REST})
    }
    @Test fun repeatedRestDoesNotDuplicateAndCancelsNewPlans(){
        val once=Actions.restDay(Journal(),date)
        val plan=Occurrence(courseId="course",date=date.toString())
        val twice=Actions.restDay(once.copy(occurrences=once.occurrences+plan),date)
        assertEquals(1,twice.occurrences.count {it.status==OccurrenceStatus.REST})
        assertEquals(OccurrenceStatus.CANCELLED,twice.occurrences.first {it.id==plan.id}.status)
        assertEquals(twice,Actions.restDay(twice,date))
    }
}
