package com.musclejournal.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Saved sets by their actual completion date, including a workout that crossed midnight. */
object HomeTraining {
    fun today(j:Journal,date:LocalDate,now:Long):List<SetEntry> {
        val sessions=j.sessions.associateBy {it.id}
        return j.sets.filter {row->
            val session=sessions[row.sessionId]
            session!=null && row.completedAt<=now && Instant.ofEpochMilli(row.completedAt).atZone(ZoneId.of(session.zone)).toLocalDate()==date
        }.sortedByDescending {it.completedAt}
    }
    /** A rendering coordinate within the original scale, not an RIR/RPE conversion or fatigue score. */
    fun effortTone(row:SetEntry):Float? = row.effort?.let {effort->
        when(row.scale){EffortScale.RIR->(10-effort)/10f;EffortScale.RPE->(effort-1)/9f}
    }
    fun muscleTone(j:Journal,rows:List<SetEntry>,muscle:MuscleId,side:String):Float? {
        val latest=rows.asSequence().filter {!it.warmup && (it.side=="双侧" || it.side==side)}
            .filter {val e=j.exercise(it.exerciseId);e.reviewed && muscle in e.primary}
            .maxByOrNull {it.completedAt}?:return null
        return effortTone(latest)
    }
}
