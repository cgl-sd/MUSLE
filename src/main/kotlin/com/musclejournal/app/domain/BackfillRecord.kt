package com.musclejournal.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A date supplied by the user is distinct from the actual time the record is saved. */
object BackfillRecord {
    const val DEFAULT_TITLE="养肌一日"

    fun hasChanges(initialDate:LocalDate,currentDate:LocalDate,title:String,ids:List<String>,sides:Map<String,String?> = emptyMap()):Boolean =
        currentDate!=initialDate || title!=DEFAULT_TITLE || ids.isNotEmpty() || sides.isNotEmpty()

    fun create(j:Journal,date:LocalDate,title:String,ids:List<String>,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault(),sides:Map<String,String?> = emptyMap()):Session {
        require(date<=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()){"请选择今天或之前的日期"}
        require(ids.isNotEmpty() && ids.distinct().size==ids.size){"请选择锻炼项目"}
        ids.forEach {
            val exercise=j.exercise(it)
            if(exercise.requiresSide)require(sides[it] in listOf("左侧","右侧")){"${exercise.name}需要选择左侧或右侧"}
        }
        val cleanTitle=title.trim().ifBlank {DEFAULT_TITLE}
        require(cleanTitle.length<=MAX_SESSION_TITLE_LENGTH){"训练名称过长"}
        return Session(title=cleanTitle,startedAt=now,endedAt=now,zone=zone.id,
            status=SessionStatus.COMPLETED,exerciseIds=ids,completedActions=ids.map {CompletedAction(it,now,sides[it])},recordedDate=date.toString(),modifiedAt=now)
    }
}
