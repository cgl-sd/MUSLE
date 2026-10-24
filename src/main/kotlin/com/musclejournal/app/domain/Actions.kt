package com.musclejournal.app.domain

import java.time.*

object Actions {
    fun start(j: Journal, course: Course? = null, occurrenceId: String? = null, now: Long = System.currentTimeMillis()): Journal {
        if (j.active()!=null) return j
        require(course==null || !course.archived)
        return j.copy(sessions=j.sessions+Session(title=course?.name?:"自由训练",startedAt=now,snapshot=course,exerciseIds=course?.exercises?.map { it.exerciseId }?:emptyList(),occurrenceId=occurrenceId,completedActions=emptyList()))
    }
    fun replaceExercise(j:Journal,oldId:String,newId:String,now:Long=System.currentTimeMillis()):Journal {
        val active=j.active()?:error("没有进行中训练")
        require(oldId in active.exerciseIds){"待替换动作已不在执行列表"};j.exercise(newId)
        val ids=active.exerciseIds.map {if(it==oldId)newId else it}.distinct()
        // Historical facts and the original course snapshot remain intact.
        return j.copy(sessions=j.sessions.map {if(it.id==active.id)it.copy(exerciseIds=ids,modifiedAt=now) else it})
    }
    fun saveSet(j:Journal,row:SetEntry,now:Long=System.currentTimeMillis()):Journal {
        row.validate(j.exercise(row.exerciseId));val s=j.sessions.first { it.id==row.sessionId }
        require(row.completedAt>=s.startedAt && (s.endedAt==null || row.completedAt<=s.endedAt)){"组完成时间必须在训练时间内"}
        val isNew=j.sets.none {it.id==row.id}
        val index=j.sets.count {it.sessionId==s.id && it.exerciseId==row.exerciseId && !it.warmup}
        val targets=s.snapshot?.exercises?.firstOrNull {it.exerciseId==row.exerciseId}?.targets
        val rest=targets?.getOrNull(index)?.restSeconds ?: targets?.lastOrNull()?.restSeconds ?: j.settings.defaultRest
        val side=row.side.takeIf {it!="双侧"}
        val updated=s.copy(exerciseIds=(s.exerciseIds+row.exerciseId).distinct(),completedActions=s.completedActions?.let {done->if(row.warmup)done else done.filterNot {it.exerciseId==row.exerciseId}+CompletedAction(row.exerciseId,row.completedAt,side)},modifiedAt=now)
        return j.copy(sessions=j.sessions.map { if(it.id==s.id)updated else it },sets=j.sets.filterNot { it.id==row.id }+row,restUntil=if(isNew && s.status==SessionStatus.ACTIVE)now+rest*1000L else j.restUntil,deletedIds=j.deletedIds-row.id)
    }
    fun finish(j:Journal,early:Boolean=false,now:Long=System.currentTimeMillis()):Journal {
        val active=j.active()?:return j
        require(ExerciseProgress.completed(j,active).isNotEmpty() || j.sets.any {it.sessionId==active.id}){"请先完成一个动作，或放弃本次训练"}
        val paused=active.pausedMs+(active.pauseStarted?.let { (now-it).coerceAtLeast(0) }?:0)
        return j.copy(sessions=j.sessions.map { if(it.id==active.id)it.copy(endedAt=now,status=if(early)SessionStatus.EARLY else SessionStatus.COMPLETED,pauseStarted=null,pausedMs=paused,modifiedAt=now) else it },restUntil=null)
    }
    fun deleteSession(j:Journal,id:String,now:Long=System.currentTimeMillis()):Journal {
        val ids=j.sets.filter { it.sessionId==id }.map { it.id }.toSet()+id
        val linked=j.sessions.firstOrNull {it.id==id}?.linkedOccurrenceIds().orEmpty().toSet()
        val remaining=j.sessions.filterNot {it.id==id}
        val stillLinked=remaining.flatMap {it.linkedOccurrenceIds()}.toSet()
        return j.copy(sessions=remaining,sets=j.sets.filterNot { it.sessionId==id },restUntil=if(j.active()?.id==id)null else j.restUntil,
            occurrences=j.occurrences.map {if(it.id in linked && it.id !in stillLinked && it.status==OccurrenceStatus.COMPLETED)it.copy(status=OccurrenceStatus.PLANNED,modifiedAt=now)else it})
    }
    fun deleteSet(j:Journal,id:String)=j.copy(sets=j.sets.filterNot { it.id==id })
    fun togglePause(j:Journal,now:Long=System.currentTimeMillis()):Journal {
        val a=j.active()?:return j
        return j.copy(sessions=j.sessions.map { if(it.id!=a.id)it else if(it.pauseStarted==null)it.copy(pauseStarted=now,modifiedAt=now) else it.copy(pauseStarted=null,pausedMs=it.pausedMs+(now-it.pauseStarted).coerceAtLeast(0),modifiedAt=now) })
    }
    fun restDay(j:Journal,date:LocalDate,now:Long=System.currentTimeMillis()):Journal {
        val day=date.toString()
        val linked=j.sessions.flatMap {it.linkedOccurrenceIds()}.toSet()
        val occurrences=j.occurrences.map {o->
            if(o.date==day && o.status==OccurrenceStatus.PLANNED && o.id !in linked)
                o.copy(status=OccurrenceStatus.CANCELLED,modifiedAt=now)
            else o
        }
        return j.copy(occurrences=if(occurrences.any {it.date==day && it.status==OccurrenceStatus.REST})occurrences
            else occurrences+Occurrence(courseId=null,date=day,status=OccurrenceStatus.REST,modifiedAt=now))
    }
    fun schedule(j:Journal,courseId:String?,date:LocalDate,weekdays:Set<Int>,until:LocalDate):Journal {
        require(!until.isBefore(date) && until<=date.plusYears(1)){"周期须在一年内"}
        require(courseId==null || j.courses.any { it.id==courseId && !it.archived })
        val series=if(weekdays.isEmpty())null else newId()
        val dates=if(weekdays.isEmpty())listOf(date) else generateSequence(date){it.plusDays(1)}.takeWhile { it<=until }.filter { it.dayOfWeek.value in weekdays }.toList()
        require(dates.isNotEmpty()){"所选日期范围内没有匹配的训练日"}
        return j.copy(occurrences=j.occurrences+dates.map { Occurrence(courseId=courseId,date=it.toString(),override=j.courses.firstOrNull { c -> c.id==courseId }?.copy(),status=if(courseId==null)OccurrenceStatus.REST else OccurrenceStatus.PLANNED,seriesId=series) })
    }
}
