package com.musclejournal.app.domain

import java.time.LocalDate

/** A plan is a snapshot, never a completed training fact. */
object TodayTrainingPlan {
    fun merge(courses:List<Course>):Course {
        val rows=linkedMapOf<String,CourseExercise>()
        courses.forEach {course->course.exercises.forEach {row->
            rows[row.exerciseId]=rows[row.exerciseId]?.let {it.copy(targets=it.targets+row.targets)}?:row
        }}
        return Course(name="今日训练",exercises=rows.values.toList())
    }
    fun scheduled(j:Journal,date:LocalDate):Course? {
        val linked=j.sessions.flatMap {it.linkedOccurrenceIds()}.toSet()
        val courses=j.occurrences.filter {it.date==date.toString() && it.status==OccurrenceStatus.PLANNED && it.id !in linked}
            .mapNotNull {it.override?:j.courses.firstOrNull {c->c.id==it.courseId && !c.archived}}
        return courses.takeIf {it.isNotEmpty()}?.let(::merge)?.takeIf {it.exercises.isNotEmpty()}
    }
    fun add(j:Journal,plan:Course,ids:List<String>):Course {
        val existing=plan.exercises.map {it.exerciseId}.toSet()
        val added=j.exercises().filter {it.id in ids && it.id !in existing}.map {CourseExercise(it.id)}
        return plan.copy(exercises=plan.exercises+added)
    }
    fun select(j:Journal,plan:Course,ids:List<String>):Course =
        add(j,plan.copy(exercises=plan.exercises.filter {it.exerciseId in ids}),ids)

    fun orderedExercises(j:Journal,pinned:List<String>):List<Exercise> {
        val exercises=j.exercises();val byId=exercises.associateBy {it.id}
        return pinned.distinct().mapNotNull {byId[it]}+exercises.filter {it.id !in pinned}
    }
}
