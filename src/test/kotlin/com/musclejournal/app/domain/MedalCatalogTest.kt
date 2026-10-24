package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MedalCatalogTest {
    private val origin=Instant.parse("2026-10-12T01:00:00Z").toEpochMilli()
    private fun session(id:String,day:Int=0,status:SessionStatus=SessionStatus.COMPLETED,snapshot:Course?=null)=Session(id=id,title="训练",startedAt=origin+day*86400000L,endedAt=if(status==SessionStatus.ACTIVE)null else origin+day*86400000L+3600000,status=status,snapshot=snapshot,exerciseIds=listOf("dumbbell_bench_press"),zone="Asia/Shanghai")
    private fun row(s:Session,exercise:String="dumbbell_bench_press",id:String=s.id,warmup:Boolean=false)=SetEntry(id=id,sessionId=s.id,exerciseId=exercise,completedAt=s.startedAt+1000,loadKg=20.0,reps=10,warmup=warmup)
    private fun medal(j:Journal,id:String)=MedalCatalog.states(j).single {it.definition.id==id}
    @Test fun noAwardsForEmptyActiveWarmupOrRest(){
        val active=session("active",status=SessionStatus.ACTIVE);val empty=session("empty");val warm=session("warm")
        val j=Journal(sessions=listOf(active,empty,warm),sets=listOf(row(active),row(warm,warmup=true)),occurrences=listOf(Occurrence(date="2026-10-12",courseId=null,status=OccurrenceStatus.REST)))
        assertEquals(20,MedalCatalog.definitions.map {it.id}.distinct().size)
        assertTrue(MedalCatalog.states(j).all {it.acquiredAt==null})
    }
    @Test fun countsSessionsButWeeklyMedalNeedsDifferentDaysAndUsesFirstQualifyingDate(){
        val same=(1..3).map {session("s$it")};val j=Journal(sessions=same,sets=same.map {row(it)})
        assertEquals(same[2].endedAt,medal(j,"training-3").acquiredAt);assertNull(medal(j,"week-days-3").acquiredAt)
        val later=listOf(session("s4",1),session("s5",2));val full=j.copy(sessions=same+later,sets=j.sets+later.map {row(it)})
        assertEquals(later.last().endedAt,medal(full,"week-days-3").acquiredAt)
        val corrected=full.copy(sets=full.sets.filterNot {it.sessionId=="s5"});assertNull(medal(corrected,"week-days-3").acquiredAt)
    }
    @Test fun courseRequiresCompletedSnapshotAndWorkingSets(){
        val c=Course(name="课程",exercises=listOf(CourseExercise("dumbbell_bench_press")))
        val early=session("early",snapshot=c,status=SessionStatus.EARLY);val empty=session("empty",snapshot=c);val free=session("free")
        val j=Journal(sessions=listOf(early,empty,free),sets=listOf(row(early),row(free)))
        assertNull(medal(j,"course-1").acquiredAt)
        val done=session("done",1,snapshot=c);assertEquals(done.endedAt,medal(j.copy(sessions=j.sessions+done,sets=j.sets+row(done)),"course-1").acquiredAt)
    }
    @Test fun explorationDeduplicatesExercisesAndIgnoresActiveRecords(){
        val s=session("one");val active=session("active",status=SessionStatus.ACTIVE)
        val j=Journal(sessions=listOf(s,active),sets=listOf(row(s,id="a"),row(s,id="b"),row(active,"lateral_raise",id="c")))
        assertNull(medal(j,"exercise-3").acquiredAt)
        val done=session("two",1);val full=j.copy(sessions=j.sessions+done,sets=j.sets+row(done,"lateral_raise",id="d")+row(done,"cable_row",id="e"))
        assertEquals(done.endedAt,medal(full,"exercise-3").acquiredAt)
        assertEquals(done.endedAt,medal(full,"equipment-2").acquiredAt)
    }
    @Test fun monthsAndRecordTypesUseTrainingFactsInsteadOfLegacyGoals(){
        val dates=listOf("2026-01-12T01:00:00Z","2026-02-12T01:00:00Z","2026-03-12T01:00:00Z")
        val sessions=dates.mapIndexed {i,v->val at=Instant.parse(v).toEpochMilli();session("month$i").copy(startedAt=at,endedAt=at+3600000)}
        val rows=sessions.map {row(it)}
        val j=Journal(sessions=sessions,sets=rows)
        assertEquals(sessions[1].endedAt,medal(j,"months-2").acquiredAt);assertEquals(sessions[2].endedAt,medal(j,"months-3").acquiredAt)
        assertNull(medal(j,"record-types-2").acquiredAt)
        val withReps=j.copy(sets=rows+SetEntry(id="reps",sessionId=sessions[2].id,exerciseId="pushup",completedAt=sessions[2].startedAt+1000,reps=10))
        assertEquals(sessions[2].endedAt,medal(withReps,"record-types-2").acquiredAt)
        val goal=Goal(id="legacy",title="旧目标",start="2026-01-01",end="2026-12-31")
        assertEquals(MedalCatalog.states(withReps),MedalCatalog.states(withReps.copy(goals=listOf(goal),achievements=listOf(Achievement("old","旧目标",origin,"legacy",1)))))
    }
}
