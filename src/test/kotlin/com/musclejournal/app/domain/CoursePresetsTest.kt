package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CoursePresetsTest {
    private val day=LocalDate.of(2026,10,24)

    @Test fun presetsMatchTheOfferedCourses(){
        assertEquals(listOf("上肢力量A","下肢力量B","全身基础"),CoursePresets.all.map {it.name})
        assertEquals(listOf(4,4,5),CoursePresets.all.map {it.exerciseIds.size})
        assertTrue(CoursePresets.all.all {preset->preset.exerciseIds.all {it in Catalog.byId}})
    }

    @Test fun savingPresetSchedulesOnlyTheChosenDateWithoutTrainingFacts(){
        val preset=CoursePresets.all.first().course()
        val next=CoursePlanning.save(Journal(),preset,day,now=2000)

        assertEquals(preset.id,next.courses.single().id)
        assertEquals(day.toString(),next.occurrences.single().date)
        assertEquals(OccurrenceStatus.PLANNED,next.occurrences.single().status)
        assertEquals(preset.exercises,next.occurrences.single().override!!.exercises)
        assertEquals(listOf(day),next.occurrences.map {LocalDate.parse(it.date)})
        assertTrue(next.sessions.isEmpty()&&next.sets.isEmpty()&&next.achievements.isEmpty())
        next.validate()
    }

    @Test fun editingPresetReplacesTheSameCourseAndPendingDate(){
        val preset=CoursePresets.all[1].course()
        val first=CoursePlanning.save(Journal(),preset,day,now=2000)
        val edited=preset.copy(
            name="我的下肢",
            exercises=preset.exercises.filterNot {it.exerciseId=="calf_raise"}+CourseExercise("hip_thrust"),
        )
        val next=CoursePlanning.save(first,edited,day,now=3000)

        assertEquals(1,next.courses.size)
        assertEquals("我的下肢",next.courses.single().name)
        assertEquals(1,next.occurrences.size)
        assertEquals(first.occurrences.single().id,next.occurrences.single().id)
        assertEquals(edited.exercises,next.occurrences.single().override!!.exercises)
        assertTrue(next.sessions.isEmpty()&&next.sets.isEmpty())
        next.validate()
    }

    @Test fun editingPresetPreservesOtherDatesAndExistingHistory(){
        val preset=CoursePresets.all.last().course()
        val other=day.plusDays(1)
        val first=CoursePlanning.save(Journal(),preset,day,now=2000)
        val second=CoursePlanning.save(first,preset,other,now=3000)
        val edited=preset.copy(exercises=preset.exercises.take(3))
        val next=CoursePlanning.save(second,edited,day,now=4000)

        assertEquals("全身基础",next.occurrences.first {it.date==other.toString()}.override!!.name)
        assertEquals(5,next.occurrences.first {it.date==other.toString()}.override!!.exercises.size)
        assertEquals(3,next.occurrences.first {it.date==day.toString()}.override!!.exercises.size)
        assertTrue(next.sessions.isEmpty()&&next.sets.isEmpty())
        next.validate()
    }

    @Test fun emptyOrDuplicateActionsCannotBeScheduled(){
        assertTrue(runCatching {
            CoursePlanning.save(Journal(),Course(name="空",exercises=emptyList()),day)
        }.isFailure)
        assertTrue(runCatching {
            CoursePlanning.save(Journal(),Course(name="重复",exercises=listOf(CourseExercise("plank"),CourseExercise("plank"))),day)
        }.isFailure)
    }
}
