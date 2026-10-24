package com.musclejournal.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Action completion is a fact in its own right, never a fabricated set or load. */
data class CompletedAction(val exerciseId:String,val completedAt:Long,val side:String?=null) {
    init { require(side == null || side == "左侧" || side == "右侧") }
}
data class ActionRecord(val sessionId:String,val exerciseId:String,val completedAt:Long)

/** One linear pass over sessions and sets, reused by history, medals and fatigue. */
class ExerciseActionIndex internal constructor(
    private val actionsBySession: Map<String, List<CompletedAction>>,
) {
    fun forSession(session: Session): List<CompletedAction> = actionsBySession[session.id].orEmpty()
}

object ExerciseProgress {
    fun index(j: Journal): ExerciseActionIndex {
        val legacyBySession = j.sets.asSequence().filter {!it.warmup}.groupBy {it.sessionId}
        val actions = j.sessions.associate { session ->
            val explicit = session.completedActions
            val derived = if (explicit != null) emptyList() else legacyBySession[session.id].orEmpty()
                .groupBy {it.exerciseId}
                .map { (exerciseId, rows) ->
                    CompletedAction(
                        exerciseId = exerciseId,
                        completedAt = rows.maxOf {it.completedAt},
                        side = rows.map {it.side}.filter {it != "双侧"}.distinct().singleOrNull(),
                    )
                }
            session.id to (explicit ?: derived)
        }
        return ExerciseActionIndex(actions)
    }

    fun completed(j:Journal,s:Session):List<CompletedAction> = index(j).forSession(s)

    fun onDates(j:Journal,from:LocalDate,to:LocalDate,now:Long=System.currentTimeMillis()):List<ActionRecord> {
        val actions=index(j)
        return j.sessions.flatMap {session->
            actions.forSession(session).filter {
                it.completedAt<=now && (session.recordedDate?.let(LocalDate::parse)?:Instant.ofEpochMilli(it.completedAt).atZone(ZoneId.of(session.zone)).toLocalDate()) in from..to
            }.map {ActionRecord(session.id,it.exerciseId,it.completedAt)}
        }.sortedByDescending {it.completedAt}
    }

    fun toggle(j:Journal,exerciseId:String,now:Long=System.currentTimeMillis(),side:String?=null):Journal {
        val s=j.active()?:error("没有进行中训练");require(exerciseId in s.exerciseIds)
        val done=completed(j,s)
        val updated=if(done.any {it.exerciseId==exerciseId})done.filterNot {it.exerciseId==exerciseId}else done+CompletedAction(exerciseId,now,side)
        return j.copy(sessions=j.sessions.map {if(it.id==s.id)it.copy(completedActions=updated,modifiedAt=now)else it})
    }
}
