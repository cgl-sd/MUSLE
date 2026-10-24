package com.musclejournal.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ScheduleCompletionTest {
    private val now=Instant.parse("2026-10-24T08:00:00Z").toEpochMilli()
    private val day=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun plan(vararg ids:String)=TrainingSchedule.save(Journal(),day,ids.toList(),now=now-1000)

    @Test fun saveCompletesAllMatchingPlansAndDoesNotPreselectThemAgain(){
        val source=plan("plank","bodyweight_squat")
        val saved=TrainingRecord.save(source,listOf("plank","bodyweight_squat"),now)
        assertTrue(saved.occurrences.all {it.status==OccurrenceStatus.COMPLETED})
        assertEquals(source.occurrences.map {it.id}.toSet(),saved.sessions.single().completedOccurrenceIds.toSet())
        assertNull(TodayTrainingPlan.scheduled(saved,day));assertTrue(saved.sets.isEmpty());saved.validate()
    }
    @Test fun unselectedActionsAndOtherDatesAndCancelledPlansStayUntouched(){
        val source=plan("plank","bodyweight_squat")
        val pending=source.occurrences.last()
        val extra=pending.copy(id="tomorrow",date=day.plusDays(1).toString())
        val cancelled=pending.copy(id="cancelled",status=OccurrenceStatus.CANCELLED)
        val original=source.copy(occurrences=source.occurrences+extra+cancelled)
        val saved=TrainingRecord.save(original,listOf("plank"),now)
        assertEquals(OccurrenceStatus.COMPLETED,saved.occurrences.first().status)
        assertEquals(listOf(pending,extra,cancelled),saved.occurrences.drop(1))
        assertEquals(listOf("bodyweight_squat"),TodayTrainingPlan.scheduled(saved,day)!!.exercises.map {it.exerciseId})
    }
    @Test fun aMultiActionCourseIsNotCompletedWhenOnlyPartWasRecorded(){
        val course=Course(id="course",name="course",exercises=listOf(CourseExercise("plank"),CourseExercise("bodyweight_squat")))
        val source=Journal(courses=listOf(course),occurrences=listOf(Occurrence(courseId=course.id,date=day.toString(),override=course)))
        val partial=TrainingRecord.save(source,listOf("plank"),now)
        assertEquals(source.occurrences,partial.occurrences)
        assertTrue(partial.sessions.single().completedOccurrenceIds.isEmpty())
        val complete=TrainingRecord.save(source,listOf("plank","bodyweight_squat"),now)
        assertEquals(OccurrenceStatus.COMPLETED,complete.occurrences.single().status)
    }
    @Test fun deletingTheLinkedRecordReopensOnlyItsCompletedPlans(){
        val saved=TrainingRecord.save(plan("plank","bodyweight_squat"),listOf("plank"),now)
        val reopened=Actions.deleteSession(saved,saved.sessions.single().id,now+1000)
        assertTrue(reopened.occurrences.all {it.status==OccurrenceStatus.PLANNED})
        assertTrue(reopened.sessions.isEmpty());assertEquals(saved.occurrences.last(),reopened.occurrences.last())
        assertNotNull(TodayTrainingPlan.scheduled(reopened,day));reopened.validate()
    }
    @Test fun legacyActiveLinkStillCompletesAndCannotBeReused(){
        val source=plan("plank")
        val occurrence=source.occurrences.single()
        val active=Actions.start(source,occurrence.override,occurrence.id,now-500)
        val saved=TrainingRecord.save(active,listOf("plank"),now)
        assertEquals(occurrence.id,saved.sessions.single().occurrenceId)
        assertEquals(OccurrenceStatus.COMPLETED,saved.occurrences.single().status)
        assertTrue(runCatching {TrainingSchedule.cancel(saved,occurrence.id,now+1000)}.isFailure)
    }
    @Test fun existingOldDayCompletionDoesNotCompleteTodaysPlan(){
        val source=plan("plank")
        val active=Session(id="active",title="old",startedAt=now-86_400_000,exerciseIds=listOf("plank"),completedActions=listOf(CompletedAction("plank",now-86_400_000)))
        val saved=TrainingRecord.save(source.copy(sessions=listOf(active)),listOf("plank"),now)
        assertEquals(source.occurrences,saved.occurrences)
    }
    @Test fun invalidSelectionLeavesTheSourceFactsAndPlansUntouched(){
        val source=plan("plank")
        assertTrue(runCatching {TrainingRecord.save(source,listOf("not-real"),now)}.isFailure)
        assertTrue(source.sessions.isEmpty());assertEquals(OccurrenceStatus.PLANNED,source.occurrences.single().status)
    }
    @Test fun invalidCompletionLinksCannotBePersisted(){
        val saved=TrainingRecord.save(plan("plank"),listOf("plank"),now)
        val session=saved.sessions.single()
        assertTrue(runCatching {saved.copy(sessions=listOf(session.copy(completedOccurrenceIds=listOf("missing")))).validate()}.isFailure)
        assertTrue(runCatching {saved.copy(sessions=listOf(session.copy(status=SessionStatus.ACTIVE))).validate()}.isFailure)
    }
}
