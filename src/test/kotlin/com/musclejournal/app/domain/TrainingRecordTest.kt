package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test

class TrainingRecordTest {
    @Test fun savingSelectionCreatesOneCompletedRecordWithoutSets(){
        val j=TrainingRecord.save(Journal(),listOf("plank","bodyweight_squat"),2000)
        val s=j.sessions.single();assertEquals(SessionStatus.COMPLETED,s.status)
        assertEquals(listOf("plank","bodyweight_squat"),s.exerciseIds)
        assertEquals(s.exerciseIds,s.completedActions!!.map {it.exerciseId});assertTrue(j.sets.isEmpty());j.validate()
    }
    @Test(expected=IllegalArgumentException::class) fun emptySelectionCannotSave(){TrainingRecord.save(Journal(),emptyList(),2000)}
    @Test(expected=IllegalArgumentException::class) fun duplicateActionsCannotSave(){TrainingRecord.save(Journal(),listOf("plank","plank"),2000)}
    @Test fun legacyPausedSessionIsSavedWithoutLosingRawFacts(){
        val s=Session(id="old",title="old",startedAt=1000,exerciseIds=listOf("plank"),pauseStarted=1500)
        val old=SetEntry(id="row",sessionId="old",exerciseId="plank",completedAt=1200,seconds=30)
        val source=Journal(sessions=listOf(s),sets=listOf(old))
        val saved=TrainingRecord.save(source,listOf("bodyweight_squat","plank"),3000)
        assertEquals(source.sets,saved.sets);assertEquals(1,saved.sessions.size)
        assertEquals(listOf("bodyweight_squat","plank"),saved.sessions.single().exerciseIds)
        assertEquals(1200L,saved.sessions.single().completedActions!!.last().completedAt);saved.validate()
    }
    @Test fun cancelledSelectionIsNotRecordedAndOldRecordsStayUntouched(){
        val old=TrainingRecord.save(Journal(),listOf("plank"),2000)
        val saved=TrainingRecord.save(old,listOf("bodyweight_squat"),3000)
        assertEquals(old.sessions.single(),saved.sessions.first());assertEquals(listOf("bodyweight_squat"),saved.sessions.last().completedActions!!.map {it.exerciseId})
    }
    @Test fun pinnedOrderFollowsSelectionAndContainsNoDuplicates(){
        val order=TodayTrainingPlan.orderedExercises(Journal(),listOf("bodyweight_squat","plank"))
        assertEquals(listOf("bodyweight_squat","plank"),order.take(2).map {it.id});assertEquals(order.size,order.map {it.id}.distinct().size)
    }
    @Test fun unilateralSelectionsRequireAndPersistSide(){
        assertTrue(runCatching {TrainingRecord.save(Journal(),listOf("one_arm_row"),2000)}.isFailure)
        val saved=TrainingRecord.saveSelections(Journal(),listOf(TrainingRecord.Selection("one_arm_row","右侧")),2000)
        assertEquals("右侧",saved.sessions.single().completedActions!!.single().side)
        saved.validate()
    }
}
