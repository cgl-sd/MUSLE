package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TrainingFatigueTest {
    private val now=Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
    private val hour=3_600_000L
    private fun session(id:String="s",at:Long=now,ids:List<String> = listOf("dumbbell_bench_press"))=Session(
        id=id,title="测试",startedAt=at,endedAt=at,status=SessionStatus.COMPLETED,zone="Asia/Shanghai",
        exerciseIds=ids,completedActions=ids.map {CompletedAction(it,at)},
    )
    private fun state(vararg sessions:Session,at:Long=now)=TrainingFatigue.calculate(Journal(sessions=sessions.toList()),at)

    @Test fun noTrainingMeansOneAndNoTintForEveryMuscleAndSide(){
        val empty=state()
        for(muscle in MuscleId.entries)for(side in listOf("双侧","左侧","右侧")){
            assertEquals(1,empty.score(muscle,side));assertEquals(0f,empty.normalized(muscle,side),0f)
        }
        assertEquals(1,empty.overall())
    }
    @Test fun benchTargetsChestMoreThanShouldersAndTricepsAndLeavesLegsUntinted(){
        val trained=state(session())
        assertEquals(6,trained.score(MuscleId.CHEST))
        assertEquals(3,trained.score(MuscleId.SHOULDERS))
        assertEquals(3,trained.score(MuscleId.TRICEPS))
        assertEquals(1,trained.score(MuscleId.QUADRICEPS))
        assertTrue(trained.normalized(MuscleId.CHEST)>trained.normalized(MuscleId.TRICEPS))
    }
    @Test fun everyCatalogActionUsesItsReviewedPrimaryAndSecondaryMapping(){
        Catalog.exercises.forEach {exercise->
            val trained=state(session(ids=listOf(exercise.id)))
            MuscleId.entries.forEach {muscle->
                val expected=when(muscle){in exercise.primary->6;in exercise.secondary->3;else->1}
                assertEquals("${exercise.id}/$muscle",expected,trained.score(muscle))
            }
        }
    }
    @Test fun moreActionsAndConsecutiveTrainingAccumulateButStayWithinTen(){
        val one=state(session())
        val more=state(session(ids=listOf("dumbbell_bench_press","barbell_bench")))
        val days=(0..3).map {session("day$it",now-it*24*hour)}
        val consecutive=state(*days.toTypedArray())
        assertTrue(more.overall()>one.overall())
        assertTrue(consecutive.overall()>one.overall())
        assertEquals(10,state(*(0..30).map {session("$it")}.toTypedArray()).overall())
    }
    @Test fun restingDeclinesAndFourteenDaysClearsEvenAnAccumulatedState(){
        val s=session()
        val fresh=state(s);val rested=state(s,at=now+72*hour)
        assertTrue(rested.score(MuscleId.CHEST)<fresh.score(MuscleId.CHEST))
        assertTrue(rested.normalized(MuscleId.CHEST)<fresh.normalized(MuscleId.CHEST))
        val old=(1..30).map {session("$it")}
        val cleared=state(*old.toTypedArray(),at=now+14*24*hour)
        assertEquals(1,cleared.overall());assertEquals(0f,cleared.normalized(MuscleId.CHEST),0f)
    }
    @Test fun backfillUsesArchivedDayInsteadOfSaveTimestampAndRejectsFutureArchivedDay(){
        val s=session().copy(recordedDate="2026-10-20")
        val anchor=LocalDate.parse("2026-10-21").atStartOfDay(ZoneId.of(s.zone)).toInstant().toEpochMilli()-1
        assertEquals(state(session(at=anchor)),state(s))
        assertTrue(state(s).overall()<state(session()).overall())
        assertEquals(1,state(session().copy(recordedDate="2026-10-24")).overall())
        assertEquals(1,state(session().copy(recordedDate="2026-09-01")).overall())
    }
    @Test fun draftsPlansFutureAndWarmupDoNotContribute(){
        val active=session("active").copy(status=SessionStatus.ACTIVE,endedAt=null)
        val future=session("future",now+hour)
        val legacy=session("legacy").copy(completedActions=null)
        val j=Journal(sessions=listOf(active,future,legacy),
            sets=listOf(SetEntry(sessionId=legacy.id,exerciseId="dumbbell_bench_press",completedAt=now,reps=10,warmup=true)),
            courses=listOf(Course(id="c",name="课程",exercises=listOf(CourseExercise("squat")))),
            occurrences=listOf(Occurrence(courseId="c",date="2026-10-23")))
        assertEquals(1,TrainingFatigue.calculate(j,now).overall())
    }
    @Test fun explicitActionsDeduplicateAndOverrideLegacySetsIncludingEmptySelection(){
        val s=session();val legacy=SetEntry(sessionId=s.id,exerciseId="dumbbell_bench_press",completedAt=now,reps=10)
        val duplicate=s.copy(completedActions=s.completedActions!!+s.completedActions!!)
        assertEquals(state(s),TrainingFatigue.calculate(Journal(sessions=listOf(duplicate),sets=listOf(legacy)),now))
        assertEquals(1,TrainingFatigue.calculate(Journal(sessions=listOf(s.copy(completedActions=emptyList())),sets=listOf(legacy)),now).overall())
    }
    @Test fun legacyActionsCountOncePerSideAndNeverDoubleCountBilateralPlusSingleSide(){
        val s=session().copy(completedActions=null)
        val left=SetEntry(sessionId=s.id,exerciseId="dumbbell_bench_press",completedAt=now,reps=10,side="左侧")
        val onlyLeft=TrainingFatigue.calculate(Journal(sessions=listOf(s),sets=listOf(left,left.copy(id="second"))),now)
        assertEquals(6,onlyLeft.score(MuscleId.CHEST,"左侧"));assertEquals(1,onlyLeft.score(MuscleId.CHEST,"右侧"))
        val both=left.copy(id="both",side="双侧")
        assertEquals(state(session()),TrainingFatigue.calculate(Journal(sessions=listOf(s),sets=listOf(left,both)),now))
    }
    @Test fun unknownAndUnreviewedMappingsDoNotInventMuscleState(){
        val custom=Catalog.exercises.first().copy(id="custom",reviewed=false)
        assertEquals(1,TrainingFatigue.calculate(Journal(customExercises=listOf(custom),sessions=listOf(session(ids=listOf("custom","missing")))),now).overall())
    }
    @Test fun overallIncludesAllMusclesAndIsIndependentOfBodyView(){
        val trained=state(session(ids=listOf("hip_thrust")))
        assertEquals(6,trained.overall())
        assertEquals(1,trained.score(MuscleId.CHEST))
    }
    @Test fun newUnilateralActionsOnlyTintTheSelectedSide(){
        val unilateral=session(ids=listOf("one_arm_row")).copy(
            completedActions=listOf(CompletedAction("one_arm_row",now,"左侧")),
        )
        val trained=TrainingFatigue.calculate(Journal(sessions=listOf(unilateral)),now)
        assertEquals(6,trained.score(MuscleId.LATS,"左侧"))
        assertEquals(1,trained.score(MuscleId.LATS,"右侧"))
    }
}
