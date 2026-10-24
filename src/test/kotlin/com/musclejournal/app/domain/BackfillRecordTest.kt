package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class BackfillRecordTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val today=LocalDate.parse("2026-10-23")
    private val now=today.atTime(20,0).atZone(zone).toInstant().toEpochMilli()
    @Test fun exitPromptOnlyAfterBackfillDraftChanges(){
        val start=today.minusDays(1)
        assertFalse(BackfillRecord.hasChanges(start,start,BackfillRecord.DEFAULT_TITLE,emptyList()))
        assertTrue(BackfillRecord.hasChanges(start,start," 自定义名称",emptyList()))
        assertTrue(BackfillRecord.hasChanges(start,start.minusDays(2),BackfillRecord.DEFAULT_TITLE,emptyList()))
        assertTrue(BackfillRecord.hasChanges(start,start,BackfillRecord.DEFAULT_TITLE,listOf("plank")))
        assertFalse(BackfillRecord.hasChanges(start,start,BackfillRecord.DEFAULT_TITLE,emptyList()))
    }
    @Test fun defaultNameAndDateAreSeparateFromActualSaveTimestamp(){
        val date=today.minusDays(3)
        val s=BackfillRecord.create(Journal(),date," ",listOf("plank"),now,zone)
        assertEquals(BackfillRecord.DEFAULT_TITLE,s.title);assertEquals(date,s.date());assertEquals(date.toString(),s.recordedDate)
        assertEquals(now,s.startedAt);assertEquals(now,s.endedAt);assertEquals(listOf(CompletedAction("plank",now)),s.completedActions)
        Journal(sessions=listOf(s)).validate()
    }
    @Test fun pastRecordDoesNotAppearAsTodayOrInventAnHour(){
        val date=today.minusDays(1);val s=BackfillRecord.create(Journal(),date,"补录",listOf("plank"),now,zone)
        val j=Journal(sessions=listOf(s))
        assertTrue(ExerciseProgress.onDates(j,today,today,now).isEmpty())
        assertEquals(1,ExerciseProgress.onDates(j,date,date,now).size)
        val r=TrainingHistory.records(j,date,date,now=now).single()
        assertEquals(date,r.date);assertNull(r.hour)
        assertTrue(TrainingHistory.records(j,today,today,now=now).isEmpty());assertTrue(j.sets.isEmpty())
    }
    @Test fun selectedOrderAndExplicitTitleAreRetained(){
        val ids=listOf("bodyweight_squat","plank")
        val s=BackfillRecord.create(Journal(),today," 自定义名称 ",ids,now,zone)
        assertEquals("自定义名称",s.title);assertEquals(ids,s.exerciseIds);assertEquals(ids,s.completedActions!!.map {it.exerciseId})
    }
    @Test fun noActionsDuplicatesUnknownActionsAndFutureDatesAreRejected(){
        assertTrue(runCatching {BackfillRecord.create(Journal(),today,"",emptyList(),now,zone)}.isFailure)
        assertTrue(runCatching {BackfillRecord.create(Journal(),today,"",listOf("plank","plank"),now,zone)}.isFailure)
        assertTrue(runCatching {BackfillRecord.create(Journal(),today,"",listOf("missing-action"),now,zone)}.isFailure)
        assertTrue(runCatching {BackfillRecord.create(Journal(),today.plusDays(1),"",listOf("plank"),now,zone)}.isFailure)
    }
    @Test fun oldRecordsKeepOriginalTimestampDateAndHour(){
        val old=Session(title="旧记录",startedAt=now-60000,endedAt=now,status=SessionStatus.COMPLETED,zone=zone.id,exerciseIds=listOf("plank"),completedActions=listOf(CompletedAction("plank",now)))
        assertNull(old.recordedDate);assertEquals(today,old.date())
        assertEquals(20,TrainingHistory.records(Journal(sessions=listOf(old)),today,today,now=now).single().hour)
    }
    @Test fun pastEntryStaysBelowNewerTrainingDateEvenWhenSavedLater(){
        val past=BackfillRecord.create(Journal(),today.minusDays(2),"过去",listOf("plank"),now,zone)
        val recent=Session(title="最近",startedAt=now-60000,endedAt=now-1000,status=SessionStatus.COMPLETED,zone=zone.id,exerciseIds=listOf("plank"),completedActions=listOf(CompletedAction("plank",now-1000)))
        val records=TrainingHistory.records(Journal(sessions=listOf(past,recent)),today.minusDays(3),today,now=now)
        assertEquals(listOf(recent.id,past.id),records.map {it.sessionId})
    }
    @Test fun unilateralBackfillRequiresSideAndPersistsIt(){
        assertTrue(runCatching {BackfillRecord.create(Journal(),today,"",listOf("one_arm_row"),now,zone)}.isFailure)
        val session=BackfillRecord.create(Journal(),today,"",listOf("one_arm_row"),now,zone,mapOf("one_arm_row" to "右侧"))
        assertEquals("右侧",session.completedActions!!.single().side)
    }
}
