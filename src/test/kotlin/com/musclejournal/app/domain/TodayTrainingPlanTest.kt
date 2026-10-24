package com.musclejournal.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TodayTrainingPlanTest {
    private val date=LocalDate.parse("2026-10-20")
    private val a=Course(id="a",name="A",exercises=listOf(CourseExercise("plank",listOf(TargetSet(seconds=45))),CourseExercise("dumbbell_bench_press",listOf(TargetSet(loadKg=20.0)))))
    private val b=Course(id="b",name="B",exercises=listOf(CourseExercise("plank",listOf(TargetSet(seconds=60))),CourseExercise("bodyweight_squat")))
    @Test fun mergesDuplicatesAndPreservesEveryTargetInCourseOrder(){
        val p=TodayTrainingPlan.merge(listOf(a,b))
        assertEquals(listOf("plank","dumbbell_bench_press","bodyweight_squat"),p.exercises.map {it.exerciseId})
        assertEquals(listOf(45,60),p.exercises.first().targets.map {it.seconds})
        assertEquals(listOf(a,b),listOf(a.copy(),b.copy()))
    }
    @Test fun onlyLoadsUnstartedPlannedCoursesForToday(){
        val planned=Occurrence(id="plan",courseId=a.id,date=date.toString(),override=a)
        val j=Journal(courses=listOf(a,b),occurrences=listOf(planned,planned.copy(id="other",courseId=b.id,override=b,date=date.plusDays(1).toString()),planned.copy(id="cancel",status=OccurrenceStatus.CANCELLED,override=b),planned.copy(id="rest",status=OccurrenceStatus.REST,courseId=null,override=null),planned.copy(id="done",override=b)),sessions=listOf(Session(title="done",startedAt=1,endedAt=2,status=SessionStatus.COMPLETED,exerciseIds=listOf("plank"),occurrenceId="done")))
        assertEquals(a.exercises,TodayTrainingPlan.scheduled(j,date)!!.exercises)
    }
    @Test fun archivedTemplateDoesNotDiscardScheduledSnapshot(){
        val j=Journal(courses=listOf(a.copy(archived=true)),occurrences=listOf(Occurrence(courseId=a.id,date=date.toString(),override=a)))
        assertEquals(a.exercises,TodayTrainingPlan.scheduled(j,date)!!.exercises)
        assertFalse(TodayTrainingPlan.scheduled(j,date)!!.archived)
    }
    @Test fun noScheduleDoesNotAutoInsertEveryCourse(){assertNull(TodayTrainingPlan.scheduled(Journal(courses=listOf(a,b)),date))}
    @Test fun manualSelectionUsesCatalogOrderWithoutDuplicates(){
        val j=Journal();val ids=j.exercises().take(3).map {it.id}
        assertEquals(ids,TodayTrainingPlan.add(j,Course(name="today",exercises=emptyList()),ids.reversed()+ids.first()).exercises.map {it.exerciseId})
    }
    @Test fun addingAlreadyPlannedActionDoesNotDuplicateTargets(){
        val p=TodayTrainingPlan.add(Journal(),a,listOf("plank","bodyweight_squat"))
        assertEquals(a.exercises.first(),p.exercises.first());assertEquals(3,p.exercises.size)
    }
    @Test fun confirmingPlanCreatesSnapshotButNoCompletedSets(){
        val original=Journal(courses=listOf(a));val plan=TodayTrainingPlan.merge(listOf(a,b));val ordered=plan.copy(exercises=plan.exercises.reversed())
        val started=Actions.start(original,ordered,now=12345)
        assertEquals(ordered.exercises.map {it.exerciseId},started.active()!!.exerciseIds)
        assertEquals(12345L,started.active()!!.startedAt);assertTrue(started.sets.isEmpty());assertEquals(a,started.courses.single())
    }
    @Test fun scheduledActionsArePinnedWithoutDuplicatingCatalogEntries(){
        val j=Journal();val all=j.exercises();val pinned=listOf("plank","bodyweight_squat","plank")
        val ordered=TodayTrainingPlan.orderedExercises(j,pinned)
        assertEquals(listOf("plank","bodyweight_squat"),ordered.take(2).map {it.id})
        assertEquals(all.size,ordered.size)
        assertEquals(all.filter {it.id !in pinned},ordered.drop(2))
    }
    @Test fun uncheckingScheduledActionRemovesItsTargetsFromConfirmedPlan(){
        val plan=TodayTrainingPlan.merge(listOf(a,b))
        val selected=TodayTrainingPlan.select(Journal(),plan,listOf("bodyweight_squat"))
        assertEquals(listOf("bodyweight_squat"),selected.exercises.map {it.exerciseId})
        assertEquals(3,selected.exercises.single().targets.size)
        assertEquals(2,plan.exercises.first().targets.size)
    }
    @Test fun selectedPlannedTargetsSurviveAlongsideNewActions(){
        val selected=TodayTrainingPlan.select(Journal(),a,listOf("plank","bodyweight_squat"))
        assertEquals(listOf("plank","bodyweight_squat"),selected.exercises.map {it.exerciseId})
        assertEquals(a.exercises.first().targets,selected.exercises.first().targets)
    }
}
