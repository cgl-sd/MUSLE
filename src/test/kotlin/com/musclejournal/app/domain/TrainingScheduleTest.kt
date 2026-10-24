package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingScheduleTest {
    private val day=LocalDate.of(2026,10,24)
    @Test fun addingActionsOnlyCreatesPlansOnSelectedDate(){
        val source=TrainingRecord.save(Journal(),listOf("plank"),2000)
        val next=TrainingSchedule.save(source,day,listOf("plank","bodyweight_squat"),now=3000)
        assertEquals(source.sessions,next.sessions);assertEquals(source.sets,next.sets)
        assertEquals(source.achievements,next.achievements);assertTrue(next.courses.isEmpty())
        assertEquals(listOf(day.toString(),day.toString()),next.occurrences.map {it.date})
        assertTrue(next.occurrences.all {it.status==OccurrenceStatus.PLANNED})
        assertEquals(listOf("plank","bodyweight_squat"),TodayTrainingPlan.scheduled(next,day)!!.exercises.map {it.exerciseId})
        assertNull(TodayTrainingPlan.scheduled(next,day.minusDays(1)));next.validate()
    }
    @Test fun duplicatePendingActionsAreNotAddedAgainButOtherDatesRemainIndependent(){
        val source=TrainingSchedule.save(Journal(),day,listOf("plank"),now=2000)
        val next=TrainingSchedule.save(source,day,listOf("plank","bodyweight_squat"),now=3000)
        assertEquals(2,next.occurrences.size)
        assertEquals(3,TrainingSchedule.save(next,day.plusDays(1),listOf("plank"),now=4000).occurrences.size)
    }
    @Test fun editingPreservesIdentityAndChangesOnlyThisPlan(){
        val source=TrainingSchedule.save(Journal(),day,listOf("plank","bodyweight_squat"),now=2000)
        val first=source.occurrences.first()
        val next=TrainingSchedule.save(source,day,listOf("barbell_bench"),first.id,3000)
        assertEquals(first.id,next.occurrences.first().id)
        assertEquals("barbell_bench",next.occurrences.first().override!!.exercises.single().exerciseId)
        assertEquals(source.occurrences.last(),next.occurrences.last());assertTrue(next.sessions.isEmpty());next.validate()
    }
    @Test fun cancelKeepsHistoryAndAllowsAddingAgain(){
        val source=TrainingSchedule.save(Journal(),day,listOf("plank"),now=2000)
        val next=TrainingSchedule.cancel(source,source.occurrences.single().id,3000)
        assertEquals(OccurrenceStatus.CANCELLED,next.occurrences.single().status)
        assertNull(TodayTrainingPlan.scheduled(next,day));assertTrue(next.sessions.isEmpty())
        assertEquals(2,TrainingSchedule.save(next,day,listOf("plank"),now=4000).occurrences.size)
    }
    @Test fun startedPlansCannotBeModifiedOrCancelled(){
        val plan=TrainingSchedule.save(Journal(),day,listOf("plank"),now=2000)
        val row=plan.occurrences.single()
        val started=Actions.start(plan,row.override,row.id,3000)
        assertTrue(runCatching {TrainingSchedule.cancel(started,row.id,4000)}.isFailure)
        assertTrue(runCatching {TrainingSchedule.save(started,day,listOf("bodyweight_squat"),row.id,4000)}.isFailure)
        assertEquals(started.sessions.single().completedActions,emptyList<CompletedAction>())
    }
    @Test fun emptyAndDuplicateInputCannotWrite(){
        assertTrue(runCatching {TrainingSchedule.save(Journal(),day,emptyList())}.isFailure)
        assertTrue(runCatching {TrainingSchedule.save(Journal(),day,listOf("plank","plank"))}.isFailure)
    }
}
