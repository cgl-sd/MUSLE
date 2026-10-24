package com.musclejournal.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HistoryAction(val sessionId:String,val exerciseId:String,val completedAt:Long,val date:LocalDate,val hour:Int?)
data class TrainingDay(val date:LocalDate,val count:Int)

/** Counts saved action facts in their session's time zone, never plans or sets as quantity. */
object TrainingHistory {
    fun records(j:Journal,from:LocalDate,to:LocalDate,query:String="",now:Long=System.currentTimeMillis()):List<HistoryAction> {
        require(to>=from)
        val search=query.trim()
        val actions=ExerciseProgress.index(j)
        return j.sessions.filter {it.status!=SessionStatus.ACTIVE}.flatMap {s->
            val titleMatches=s.title.contains(search,ignoreCase=true)
            actions.forSession(s).filter {it.completedAt<=now}.mapNotNull {action->
                val at=Instant.ofEpochMilli(action.completedAt).atZone(ZoneId.of(s.zone))
                val date=s.recordedDate?.let(LocalDate::parse)?:at.toLocalDate()
                if(date !in from..to || (!titleMatches && !j.exercise(action.exerciseId).name.contains(search,ignoreCase=true)))null
                else HistoryAction(s.id,action.exerciseId,action.completedAt,date,if(s.recordedDate==null)at.hour else null)
            }
        }.sortedWith(compareByDescending<HistoryAction>{it.date}.thenByDescending {it.completedAt})
    }

    fun days(records:List<HistoryAction>,from:LocalDate,to:LocalDate):List<TrainingDay> {
        require(to>=from)
        val counts=records.groupingBy {it.date}.eachCount()
        return generateSequence(from){it.plusDays(1)}.takeWhile {it<=to}.map {TrainingDay(it,counts[it]?:0)}.toList()
    }
}
