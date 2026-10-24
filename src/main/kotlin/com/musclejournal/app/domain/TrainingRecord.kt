package com.musclejournal.app.domain

import java.time.Instant
import java.time.ZoneId

/** Selection stays a draft until one atomic save records the performed actions. */
object TrainingRecord {
    fun save(j:Journal,ids:List<String>,now:Long=System.currentTimeMillis()):Journal {
        return saveSelections(j,ids.map {Selection(it)},now)
    }

    data class Selection(val exerciseId:String,val side:String?=null)

    fun saveSelections(j:Journal,selections:List<Selection>,now:Long=System.currentTimeMillis()):Journal {
        val ids=selections.map {it.exerciseId}
        require(ids.isNotEmpty() && ids.distinct().size==ids.size)
        selections.forEach {selection->
            val exercise=j.exercise(selection.exerciseId)
            if(exercise.requiresSide)require(selection.side in listOf("左侧","右侧")){"${exercise.name}需要选择左侧或右侧"}
        }
        val source=if(j.active()==null)Actions.start(j,Course(name="今日训练",exercises=ids.map {CourseExercise(it)}),now=now)else j
        val active=source.active()!!
        val existing=ExerciseProgress.completed(source,active).associateBy {it.exerciseId}
        val done=selections.map {selection->
            existing[selection.exerciseId]?.copy(side=selection.side ?: existing[selection.exerciseId]?.side)
                ?: CompletedAction(selection.exerciseId,now,selection.side)
        }
        val zone=ZoneId.of(active.zone)
        val day=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val performed=done.filter {it.completedAt<=now && Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate()==day}.map {it.exerciseId}.toSet()
        val linkedElsewhere=source.sessions.filterNot {it.id==active.id}.flatMap {it.linkedOccurrenceIds()}.toSet()
        val completed=source.occurrences.filter {o->
            val actions=(o.override?:source.courses.firstOrNull {it.id==o.courseId && !it.archived})?.exercises.orEmpty()
            o.status==OccurrenceStatus.PLANNED && o.date==day.toString() && o.id !in linkedElsewhere &&
                actions.isNotEmpty() && actions.all {it.exerciseId in performed}
        }.map {it.id}.toSet()
        val selected=active.copy(exerciseIds=ids,completedActions=done,completedOccurrenceIds=(active.completedOccurrenceIds+completed).distinct(),modifiedAt=now)
        return Actions.finish(source.copy(
            sessions=source.sessions.map {if(it.id==active.id)selected else it},
            occurrences=source.occurrences.map {if(it.id in completed)it.copy(status=OccurrenceStatus.COMPLETED,modifiedAt=now)else it},
        ),now=now)
    }
}
