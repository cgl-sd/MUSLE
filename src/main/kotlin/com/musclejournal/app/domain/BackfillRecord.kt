package com.musclejournal.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A date supplied by the user is distinct from the actual time the record is saved. */
object BackfillRecord {
    const val DEFAULT_TITLE="养肌一日"

    fun hasChanges(initialDate:LocalDate,currentDate:LocalDate,title:String,ids:List<String>):Boolean =
        currentDate!=initialDate || title!=DEFAULT_TITLE || ids.isNotEmpty()

    fun create(j:Journal,date:LocalDate,title:String,ids:List<String>,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):Session {
        require(date<=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()){"请选择今天或之前的日期"}
        require(ids.isNotEmpty() && ids.distinct().size==ids.size){"请选择锻炼项目"}
        ids.forEach {j.exercise(it)}
        return Session(title=title.trim().ifBlank {DEFAULT_TITLE},startedAt=now,endedAt=now,zone=zone.id,
            status=SessionStatus.COMPLETED,exerciseIds=ids,completedActions=ids.map {CompletedAction(it,now)},recordedDate=date.toString(),modifiedAt=now)
    }
}
