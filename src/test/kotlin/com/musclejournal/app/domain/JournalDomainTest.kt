package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class JournalDomainTest {
    private val now=Instant.parse("2026-10-17T10:00:00Z").toEpochMilli()
    private fun session(id:String="s",at:Long=now-3600000,status:SessionStatus=SessionStatus.ACTIVE)=Session(id=id,title="训练",startedAt=at,endedAt=if(status==SessionStatus.ACTIVE)null else at+1800000,status=status,exerciseIds=listOf("dumbbell_bench_press"),zone="Asia/Shanghai")
    private fun row(id:String="set",sid:String="s",at:Long=now-1800000,warm:Boolean=false,kg:Double=24.0)=SetEntry(id,sid,"dumbbell_bench_press",at,kg,8,warmup=warm)
    @Test fun startIsIdempotent(){val j=Actions.start(Journal(),now=now);assertEquals(1,Actions.start(j,now=now+1).sessions.size)}
    @Test fun duplicateSaveUpdatesOneFact(){val j=Journal(sessions=listOf(session()));val a=Actions.saveSet(j,row(),now);val b=Actions.saveSet(a,row(kg=30.0),now);assertEquals(1,b.sets.size);assertEquals(30.0,b.sets.single().loadKg!!,0.0)}
    @Test fun planDoesNotCountAsTraining(){val c=Course(name="上肢",exercises=listOf(CourseExercise("dumbbell_bench_press")));val j=Actions.schedule(Journal(courses=listOf(c)),c.id,LocalDate.parse("2026-10-19"),emptySet(),LocalDate.parse("2026-10-19"));assertTrue(j.sets.isEmpty());assertEquals(0,Metrics.muscle(j,MuscleId.CHEST,j.sets).direct)}
    @Test fun templateSnapshotDoesNotMutate(){val c=Course(name="旧",exercises=listOf(CourseExercise("dumbbell_bench_press")));val j=Actions.start(Journal(courses=listOf(c)),c,now=now);val edited=j.copy(courses=listOf(c.copy(name="新",exercises=emptyList())));assertEquals("旧",edited.active()!!.snapshot!!.name)}
    @Test fun scheduleRetainsSnapshot(){val c=Course(name="旧",exercises=listOf(CourseExercise("plank")));val j=Actions.schedule(Journal(courses=listOf(c)),c.id,LocalDate.now(),emptySet(),LocalDate.now());assertEquals("旧",j.occurrences.single().override!!.name)}
    @Test fun warmupsAndAuxiliaryAreSeparate(){val j=Journal(sessions=listOf(session()),sets=listOf(row(),row("warm",warm=true)));assertEquals(1,Metrics.muscle(j,MuscleId.CHEST,j.sets).direct);assertEquals(0,Metrics.muscle(j,MuscleId.TRICEPS,j.sets).direct);assertEquals(1,Metrics.muscle(j,MuscleId.TRICEPS,j.sets).auxiliary)}
    @Test fun unknownEffortDoesNotBecomeFailure(){assertNull(row().effort);assertEquals(EffortScale.RIR,row().scale)}
    @Test fun timedAndBodyweightHaveNoInventedLoad(){SetEntry(sessionId="s",exerciseId="plank",completedAt=now,seconds=30).validate(Catalog.exercises.first {it.id=="plank"});SetEntry(sessionId="s",exerciseId="bodyweight_squat",completedAt=now,reps=10).validate(Catalog.exercises.first {it.id=="bodyweight_squat"})}
    @Test(expected=IllegalArgumentException::class) fun invalidTimedSetRejected(){SetEntry(sessionId="s",exerciseId="plank",completedAt=now,loadKg=10.0,reps=8).validate(Catalog.exercises.first {it.id=="plank"})}
    @Test(expected=IllegalArgumentException::class) fun nanRejected(){row(kg=Double.NaN)}
    @Test fun pauseTimeIsExcluded(){val j=Journal(sessions=listOf(session(at=now-10000)));val paused=Actions.togglePause(j,now-5000);val resumed=Actions.togglePause(paused,now);assertEquals(5000,resumed.active()!!.elapsed(now))}
    private fun goal()=Goal(id="g",title="本周一次",start="2026-10-12",end="2026-10-18",zone="Asia/Shanghai",target=1.0)
    @Test fun goalOnlyCountsEndedWorkingSession(){val j=Journal(sessions=listOf(session()),sets=listOf(row()),goals=listOf(goal()));assertEquals(0.0,Metrics.goalProgress(j,goal()),0.0);val ended=Actions.finish(j,now=now);assertEquals(1.0,Metrics.goalProgress(ended,goal()),0.0)}
    @Test fun rewardsDeduplicateAndCorrectionRevokes(){val j=Journal(sessions=listOf(session(status=SessionStatus.COMPLETED)),sets=listOf(row()),goals=listOf(goal()));val first=Metrics.reconcile(j,now);val twice=Metrics.reconcile(first,now+1);assertEquals(1,twice.achievements.size);val corrected=Metrics.reconcile(Actions.deleteSet(twice,"set"),now+2);assertFalse(corrected.achievements.single().valid);assertEquals(first.achievements.single().awardedAt,corrected.achievements.single().awardedAt)}
    @Test fun warmupOnlyDoesNotEarnReward(){val j=Journal(sessions=listOf(session(status=SessionStatus.COMPLETED)),sets=listOf(row(warm=true)),goals=listOf(goal()));assertTrue(Metrics.reconcile(j,now).achievements.isEmpty())}
    @Test fun restDayDoesNotDemandStreak(){val j=Actions.schedule(Journal(),null,LocalDate.now(),emptySet(),LocalDate.now());assertEquals(OccurrenceStatus.REST,j.occurrences.single().status);assertTrue(j.sets.isEmpty())}
    @Test fun dateUsesOccurrenceTimezone(){val s=session(at=Instant.parse("2026-10-16T18:00:00Z").toEpochMilli());assertEquals(LocalDate.parse("2026-10-17"),s.date())}
    @Test fun baselineExcludesCurrentAndUsesPersonalHistory(){
        fun build(kg:Double):Journal {
            val past=(1..3).map {session("past$it",now-it*86400000L,SessionStatus.COMPLETED)}
            return Journal(profile=Profile(adult="已满18岁"),sessions=past+session(),sets=past.mapIndexed {i,s->row("h$i",s.id,s.startedAt+1000,kg=kg)}+row())
        }
        assertEquals(1.0,Metrics.baseline(build(24.0),row()).ratio!!,0.0)
        assertEquals(0.6,Metrics.baseline(build(40.0),row()).ratio!!,0.0001)
        assertEquals(3,Metrics.baseline(build(24.0),row()).matchingSets)
        assertEquals("待校准",Metrics.baseline(build(24.0).copy(profile=Profile()),row()).status)
    }
    @Test fun recoveryReferenceDecaysWithoutClaimingBiology(){val r=row(at=now-48*3600000L);val j=Journal(sessions=listOf(session(at=now-50*3600000L)),sets=listOf(r));assertEquals(0.5,Metrics.recoveryReference(j,MuscleId.CHEST,now).directWeight,0.000001);assertEquals(0.25,Metrics.recoveryReference(j,MuscleId.CHEST,now+48*3600000L).directWeight,0.000001);assertEquals(0.0,Metrics.recoveryReference(j,MuscleId.CHEST,r.completedAt-1).directWeight,0.0);assertEquals(0,Metrics.recoveryReference(j.copy(sets=emptyList()),MuscleId.CHEST,now).observations)}
    @Test fun muscleReferenceIsPersonalAndDoesNotUseCurrentWeek(){
        fun j(n:Int):Journal{val old=(1..3).map {session("h$it",now-it*7*86400000L,SessionStatus.COMPLETED)};val rows=old.flatMap {s->(1..n).map {row("${s.id}-$it",s.id,s.startedAt+1000)}};return Journal(profile=Profile(adult="已满18岁"),sessions=old+session(),sets=rows+row())}
        val today=LocalDate.parse("2026-10-17");val a=j(2);val b=j(4);val current=listOf(row())
        assertEquals(0.5,Metrics.personalMuscleReference(a,MuscleId.CHEST,current,today).ratio!!,0.0)
        assertEquals(0.25,Metrics.personalMuscleReference(b,MuscleId.CHEST,current,today).ratio!!,0.0)
        assertNull(Metrics.personalMuscleReference(a.copy(profile=Profile()),MuscleId.CHEST,current,today).ratio)
    }
    @Test fun editingAnOldSetDoesNotRestartRest(){val j=Journal(sessions=listOf(session()),sets=listOf(row()),restUntil=now+1234);assertEquals(j.restUntil,Actions.saveSet(j,row(kg=26.0),now+100).restUntil)}
    @Test fun courseRestAppliesToNewSets(){val c=Course(name="课程",exercises=listOf(CourseExercise("dumbbell_bench_press",listOf(TargetSet(restSeconds=45)))));val j=Journal(sessions=listOf(session().copy(snapshot=c)));assertEquals(now+45000,Actions.saveSet(j,row(),now).restUntil)}
    @Test fun unilateralRowsDoNotColorTheOtherSide(){val s=session();val r=SetEntry(sessionId=s.id,exerciseId="one_arm_row",completedAt=now,loadKg=20.0,reps=10,side="右侧");val j=Journal(sessions=listOf(s.copy(exerciseIds=listOf(r.exerciseId))),sets=listOf(r));assertEquals(1,Metrics.muscle(j,MuscleId.LATS,j.sets,"右侧").direct);assertEquals(0,Metrics.muscle(j,MuscleId.LATS,j.sets,"左侧").direct)}
    @Test(expected=IllegalArgumentException::class) fun unilateralExerciseNeedsExplicitSide(){SetEntry(sessionId="s",exerciseId="one_arm_row",completedAt=now,loadKg=20.0,reps=10).validate(Catalog.exercises.first {it.id=="one_arm_row"})}
    @Test fun weeklyScheduleIsBoundedAndCorrect(){val c=Course(name="上肢",exercises=listOf(CourseExercise("plank")));val j=Actions.schedule(Journal(courses=listOf(c)),c.id,LocalDate.parse("2026-10-19"),setOf(1,5),LocalDate.parse("2026-11-01"));assertEquals(listOf("2026-10-19","2026-10-23","2026-10-26","2026-10-30"),j.occurrences.map {it.date})}
    @Test fun replacementKeepsSavedFactsAndOriginalPlan(){
        val c=Course(name="计划",exercises=listOf(CourseExercise("dumbbell_bench_press")))
        val s=session().copy(snapshot=c);val original=row();val j=Journal(sessions=listOf(s),sets=listOf(original))
        val changed=Actions.replaceExercise(j,"dumbbell_bench_press","plank",now+1)
        assertEquals(listOf("plank"),changed.active()!!.exerciseIds);assertEquals(listOf(original),changed.sets);assertEquals(c,changed.active()!!.snapshot);changed.validate()
    }
}
