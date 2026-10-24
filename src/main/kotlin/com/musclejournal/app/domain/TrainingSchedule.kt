package com.musclejournal.app.domain

import java.time.LocalDate

/** Scheduled actions are intentions, never session or completion facts. */
object TrainingSchedule {
    fun save(j:Journal,date:LocalDate,ids:List<String>,editingId:String?=null,now:Long=System.currentTimeMillis()):Journal {
        require(ids.isNotEmpty() && ids.distinct().size==ids.size){"请选择动作"}
        ids.forEach {j.exercise(it)}
        val linked=j.sessions.flatMap {it.linkedOccurrenceIds()}.toSet()
        val original=editingId?.let {id->j.occurrences.first {it.id==id}.also {
            require(it.status==OccurrenceStatus.PLANNED && id !in linked){"此安排已开始或已取消"}
        }}
        val existing=j.occurrences.filter {it.date==date.toString() && it.status==OccurrenceStatus.PLANNED && it.id!=editingId && it.id !in linked}
            .flatMap {(it.override?:j.courses.firstOrNull {c->c.id==it.courseId})?.exercises.orEmpty()}.map {it.exerciseId}.toSet()
        val selected=ids.filterNot {it in existing}
        require(selected.isNotEmpty()){"所选动作已在当天安排中"}
        val prior=(original?.override?:j.courses.firstOrNull {it.id==original?.courseId})?.exercises.orEmpty().associateBy {it.exerciseId}
        val rows=selected.mapIndexed {index,id->
            val snapshot=Course(name=j.exercise(id).name,exercises=listOf(prior[id]?:CourseExercise(id)),modifiedAt=now)
            if(index==0 && original!=null)original.copy(courseId=null,date=date.toString(),override=snapshot,modifiedAt=now)
            else Occurrence(courseId=null,date=date.toString(),override=snapshot,modifiedAt=now)
        }
        return j.copy(occurrences=j.occurrences.map {if(it.id==editingId)rows.first() else it}+if(original==null)rows else rows.drop(1))
    }
    fun cancel(j:Journal,id:String,now:Long=System.currentTimeMillis()):Journal {
        val row=j.occurrences.first {it.id==id}
        require(row.status==OccurrenceStatus.PLANNED && j.sessions.none {it.occurrenceId==id}){"此安排已开始或已取消"}
        return j.copy(occurrences=j.occurrences.map {if(it.id==id)it.copy(status=OccurrenceStatus.CANCELLED,modifiedAt=now) else it})
    }
}
