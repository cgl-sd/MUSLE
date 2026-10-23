package com.musclejournal.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Action completion is a fact in its own right, never a fabricated set or load. */
data class CompletedAction(val exerciseId:String,val completedAt:Long)
data class ActionRecord(val sessionId:String,val exerciseId:String,val completedAt:Long)
object ExerciseProgress {
    fun completed(j:Journal,s:Session):List<CompletedAction> = s.completedActions ?: j.sets
        .filter {it.sessionId==s.id && !it.warmup}.groupBy {it.exerciseId}
        .map {(id,rows)->CompletedAction(id,rows.maxOf {it.completedAt})}
    fun onDates(j:Journal,from:LocalDate,to:LocalDate,now:Long=System.currentTimeMillis()):List<ActionRecord> = j.sessions.flatMap {s->
        completed(j,s).filter {it.completedAt<=now && (s.recordedDate?.let(LocalDate::parse)?:Instant.ofEpochMilli(it.completedAt).atZone(ZoneId.of(s.zone)).toLocalDate()) in from..to}
            .map {ActionRecord(s.id,it.exerciseId,it.completedAt)}
    }.sortedByDescending {it.completedAt}
    fun toggle(j:Journal,exerciseId:String,now:Long=System.currentTimeMillis()):Journal {
        val s=j.active()?:error("没有进行中训练");require(exerciseId in s.exerciseIds)
        val done=completed(j,s)
        val updated=if(done.any {it.exerciseId==exerciseId})done.filterNot {it.exerciseId==exerciseId}else done+CompletedAction(exerciseId,now)
        return j.copy(sessions=j.sessions.map {if(it.id==s.id)it.copy(completedActions=updated,modifiedAt=now)else it})
    }
}
