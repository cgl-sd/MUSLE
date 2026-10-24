package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TrainingHistoryTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val day=LocalDate.parse("2026-10-23")
    private fun at(date:LocalDate=day,hour:Int=12)=date.atTime(hour,0).atZone(zone).toInstant().toEpochMilli()
    private fun session(id:String="s",date:LocalDate=day,actions:List<CompletedAction>?=listOf(CompletedAction("plank",at(date))))=Session(id=id,title="核心训练",zone=zone.id,startedAt=at(date,0),endedAt=at(date,23),status=SessionStatus.COMPLETED,exerciseIds=listOf("plank","bodyweight_squat"),completedActions=actions)
    @Test fun plansAndUnsavedDraftsDoNotCountAsRecordedActions(){
        val saved=session();val active=session("draft").copy(status=SessionStatus.ACTIVE,endedAt=null)
        val plan=Occurrence(courseId=null,date=day.toString(),override=Course(name="计划",exercises=listOf(CourseExercise("plank"))))
        val j=Journal(sessions=listOf(saved,active),occurrences=listOf(plan))
        val records=TrainingHistory.records(j,day,day,now=at(hour=23))
        assertEquals(listOf("s"),records.map {it.sessionId});assertEquals(1,records.size)
    }
    @Test fun legacySetsCountActionsOnceAndExcludeWarmup(){
        val s=session(actions=null);val rows=listOf(
            SetEntry(sessionId="s",exerciseId="plank",completedAt=at(hour=9),seconds=30),
            SetEntry(sessionId="s",exerciseId="plank",completedAt=at(hour=10),seconds=30),
            SetEntry(sessionId="s",exerciseId="bodyweight_squat",completedAt=at(hour=11),reps=10,warmup=true))
        val j=Journal(sessions=listOf(s),sets=rows)
        assertEquals(listOf(10),TrainingHistory.records(j,day,day,now=at(hour=23)).map {it.hour})
        assertTrue(TrainingHistory.records(j.copy(sessions=listOf(s.copy(completedActions=emptyList()))),day,day,now=at(hour=23)).isEmpty())
        assertEquals(rows,j.sets)
    }
    @Test fun midnightUsesCompletionDateInSessionTimeZoneAndExcludesFuture(){
        val next=day.plusDays(1);val time=at(next,0)
        val s=session().copy(completedActions=listOf(CompletedAction("plank",time)),endedAt=time+60000)
        val j=Journal(sessions=listOf(s))
        assertTrue(TrainingHistory.records(j,day,day,now=time+1).isEmpty())
        assertTrue(TrainingHistory.records(j,next,next,now=time-1).isEmpty())
        assertEquals(next,TrainingHistory.records(j,next,next,now=time+1).single().date)
        assertEquals(0,TrainingHistory.records(j,next,next,now=time+1).single().hour)
    }
    @Test fun searchFiltersChartFactsAndSessionTitlesMatchAllTheirFacts(){
        val s=session(actions=listOf(CompletedAction("plank",at()),CompletedAction("bodyweight_squat",at(hour=13))))
        val j=Journal(sessions=listOf(s));val exerciseName=j.exercise("plank").name
        assertEquals(listOf("plank"),TrainingHistory.records(j,day,day,exerciseName,at(hour=23)).map {it.exerciseId})
        assertEquals(2,TrainingHistory.records(j,day,day,"核心",at(hour=23)).size)
        assertTrue(TrainingHistory.records(j,day,day,"不存在",at(hour=23)).isEmpty())
    }
    @Test fun dailySeriesIncludesZeroDaysAndKeepsRepeatedActionsInSeparateSavedSessions(){
        val j=Journal(sessions=listOf(session("one"),session("two")))
        val records=TrainingHistory.records(j,day.minusDays(1),day.plusDays(1),now=at(hour=23))
        val days=TrainingHistory.days(records,day.minusDays(1),day.plusDays(1))
        assertEquals(listOf(0,2,0),days.map {it.count});assertEquals(day.minusDays(1),days.first().date)
        assertEquals(31,TrainingHistory.days(emptyList(),day.withDayOfMonth(1),day.withDayOfMonth(31)).size)
    }
}
