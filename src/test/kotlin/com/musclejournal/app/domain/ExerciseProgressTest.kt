package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ExerciseProgressTest {
    private fun active()=Session(id="s",title="training",startedAt=1000,exerciseIds=listOf("plank"),completedActions=emptyList())
    @Test fun completingAnActionNeverFabricatesASet(){
        val j=ExerciseProgress.toggle(Journal(sessions=listOf(active())),"plank",2000)
        assertEquals(listOf(CompletedAction("plank",2000)),j.active()!!.completedActions);assertTrue(j.sets.isEmpty());j.validate()
        val ended=Actions.finish(j,now=3000);assertEquals(SessionStatus.COMPLETED,ended.sessions.single().status);assertTrue(ended.sets.isEmpty())
    }
    @Test fun togglingAgainUndoesCompletion(){
        val once=ExerciseProgress.toggle(Journal(sessions=listOf(active())),"plank",2000)
        val twice=ExerciseProgress.toggle(once,"plank",2100);assertTrue(twice.active()!!.completedActions!!.isEmpty());assertTrue(twice.sets.isEmpty())
    }
    @Test(expected=IllegalArgumentException::class) fun emptyChecklistCannotBecomeFinished(){Actions.finish(Journal(sessions=listOf(active())),now=3000)}
    @Test fun oldRecordsStayReadableAndUncheckingDoesNotDeleteFacts(){
        val s=active().copy(completedActions=null);val row=SetEntry(id="old",sessionId="s",exerciseId="plank",completedAt=1500,seconds=30)
        val j=Journal(sessions=listOf(s),sets=listOf(row));assertEquals(listOf(CompletedAction("plank",1500)),ExerciseProgress.completed(j,s))
        val unchecked=ExerciseProgress.toggle(j,"plank",2000);assertTrue(ExerciseProgress.completed(unchecked,unchecked.active()!!).isEmpty());assertEquals(listOf(row),unchecked.sets)
    }
    @Test fun completionDateHandlesMidnightAndExcludesFuture(){
        val zone=ZoneId.of("Asia/Shanghai");val date=LocalDate.parse("2026-10-20");val at=date.atStartOfDay(zone).toInstant().toEpochMilli()
        val s=active().copy(startedAt=at-60000,zone=zone.id,completedActions=listOf(CompletedAction("plank",at+60000)))
        val j=Journal(sessions=listOf(s));assertTrue(ExerciseProgress.onDates(j,date.minusDays(1),date.minusDays(1),at+120000).isEmpty());assertEquals(1,ExerciseProgress.onDates(j,date,date,at+120000).size);assertTrue(ExerciseProgress.onDates(j,date,date,at).isEmpty())
    }
    @Test fun legacyWarmupAloneDoesNotAwardCompletion(){
        val s=active().copy(completedActions=null,status=SessionStatus.COMPLETED,endedAt=3000)
        val j=Journal(sessions=listOf(s),sets=listOf(SetEntry(sessionId="s",exerciseId="plank",completedAt=2000,seconds=30,warmup=true)))
        assertTrue(ExerciseProgress.completed(j,s).isEmpty());assertTrue(MedalCatalog.states(j).all {it.acquiredAt==null})
    }
    @Test fun checklistFactsCanEarnTrainingMedal(){
        val s=active().copy(status=SessionStatus.COMPLETED,endedAt=3000,completedActions=listOf(CompletedAction("plank",2000)))
        val j=Journal(sessions=listOf(s));assertEquals(3000L,MedalCatalog.states(j).first {it.definition.id=="training-1"}.acquiredAt)
    }
    @Test(expected=IllegalArgumentException::class) fun cannotCompleteAnUnselectedAction(){ExerciseProgress.toggle(Journal(sessions=listOf(active())),"bodyweight_squat",2000)}
    @Test fun legacyActionOutsideCurrentPlanIsRetainedWhenCheckingAnotherAction(){
        val s=active().copy(completedActions=null)
        val legacy=SetEntry(id="legacy",sessionId=s.id,exerciseId="barbell_bench",completedAt=1500,loadKg=20.0,reps=10)
        val result=ExerciseProgress.toggle(Journal(sessions=listOf(s),sets=listOf(legacy)),"plank",2000)
        result.validate();assertEquals(listOf(legacy),result.sets)
        assertEquals(setOf("barbell_bench","plank"),result.active()!!.completedActions!!.map {it.exerciseId}.toSet())
    }
    @Test fun legacySetsAreIndexedOnceAndKeepUnilateralSide(){
        val sessions=(1..500).map {active().copy(id="s$it",exerciseIds=listOf("one_arm_row"),completedActions=null)}
        val rows=sessions.mapIndexed {index,session->
            SetEntry(id="r$index",sessionId=session.id,exerciseId="one_arm_row",completedAt=2000L+index,loadKg=20.0,reps=10,side="右侧")
        }
        val index=ExerciseProgress.index(Journal(sessions=sessions,sets=rows))
        assertEquals(sessions.size,index.let {indexed->sessions.count {indexed.forSession(it).size==1 }})
        assertEquals("右侧",index.forSession(sessions.last()).single().side)
    }
    @Test fun newCompletedActionPreservesSide(){
        val result=ExerciseProgress.toggle(Journal(sessions=listOf(active().copy(exerciseIds=listOf("one_arm_row")))),"one_arm_row",2000,"左侧")
        assertEquals("左侧",result.active()!!.completedActions!!.single().side)
    }
}
