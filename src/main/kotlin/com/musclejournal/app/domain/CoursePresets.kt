package com.musclejournal.app.domain

import java.time.LocalDate

data class CoursePreset(val id:String,val name:String,val exerciseIds:List<String>){
    init {
        require(id.isNotBlank() && name.isNotBlank() && exerciseIds.isNotEmpty())
        require(exerciseIds.distinct().size==exerciseIds.size)
        exerciseIds.forEach {Catalog.byId.getValue(it)}
    }

    fun course():Course=Course(
        id="preset:$id",
        name=name,
        exercises=exerciseIds.map(::CourseExercise),
    )
}

object CoursePresets {
    val all=listOf(
        CoursePreset(
            "upper-strength-a",
            "上肢力量A",
            listOf("barbell_bench","cable_row","shoulder_press","pushdown"),
        ),
        CoursePreset(
            "lower-strength-b",
            "下肢力量B",
            listOf("squat","leg_press","leg_curl","calf_raise"),
        ),
        CoursePreset(
            "full-body-basics",
            "全身基础",
            listOf("bodyweight_squat","pushup","lat_pulldown","plank","calf_raise"),
        ),
    )

    val ids:Set<String> = all.map {it.course().id}.toSet()
}

/** Saves the edited course and exactly one planned occurrence as one atomic fact. */
object CoursePlanning {
    fun save(j:Journal,course:Course,date:LocalDate,now:Long=System.currentTimeMillis()):Journal {
        require(course.name.isNotBlank()&&course.exercises.isNotEmpty()){"请填写课程名称并添加动作"}
        require(course.name.trim().length<=MAX_COURSE_NAME_LENGTH){"课程名称过长"}
        require(course.notes.length<=MAX_COURSE_NOTES_LENGTH){"课程备注过长"}
        require(course.exercises.map {it.exerciseId}.distinct().size==course.exercises.size){"课程动作不能重复"}
        course.exercises.forEach {j.exercise(it.exerciseId)}

        val saved=course.copy(
            name=course.name.trim(),
            archived=false,
            modifiedAt=now,
        )
        val currentCourses=j.courses.filterNot {it.id==saved.id}+saved
        val linked=j.sessions.flatMap {it.linkedOccurrenceIds()}.toSet()
        val pending=j.occurrences.firstOrNull {
            it.courseId==saved.id&&it.date==date.toString()&&
                it.status==OccurrenceStatus.PLANNED&&it.id !in linked
        }
        val occurrences=if(pending==null){
            j.occurrences+Occurrence(
                courseId=saved.id,
                date=date.toString(),
                status=OccurrenceStatus.PLANNED,
                override=saved,
                modifiedAt=now,
            )
        }else{
            j.occurrences.map {
                if(it.id==pending.id)it.copy(override=saved,modifiedAt=now)else it
            }
        }
        return j.copy(courses=currentCourses,occurrences=occurrences)
    }
}
