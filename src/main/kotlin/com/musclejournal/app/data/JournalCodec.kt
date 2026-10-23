package com.musclejournal.app.data

import com.musclejournal.app.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, explicit codec. Imported data is validated before a database transaction. */
object JournalCodec {
    /** Database payloads are tagged with the aggregate schema; older ones are migrated on read. */
    private val SCHEMA = JOURNAL_SCHEMA
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k,v)->put(k,v?:JSONObject.NULL) } }
    private fun <T> arr(items: Iterable<T>,encode:(T)->Any):JSONArray = JSONArray().apply { items.forEach { put(encode(it)) } }
    private fun <T> list(o:JSONObject,k:String,decode:(JSONObject)->T):List<T> = (o.optJSONArray(k)?:JSONArray()).let { a-> (0 until a.length()).map { decode(a.getJSONObject(it)) } }
    private fun strings(o:JSONObject,k:String):List<String> = (o.optJSONArray(k)?:JSONArray()).let { a->(0 until a.length()).map { a.getString(it) } }
    private fun JSONObject.strictInt(k:String):Int {
        val n=get(k);val d=(n as? Number)?.toDouble() ?: throw IllegalArgumentException("$k 必须为整数")
        require(d.isFinite() && d>=Int.MIN_VALUE && d<=Int.MAX_VALUE && d%1.0==0.0){"$k 必须为有效整数"};return d.toInt()
    }
    private fun JSONObject.strictLong(k:String):Long {
        val n=get(k);return when(n){is Long->n;is Int->n.toLong();is Number->{val d=n.toDouble();require(d.isFinite() && kotlin.math.abs(d)<=9007199254740991.0 && d%1.0==0.0){"$k 必须为有效时间戳整数"};d.toLong()};else->throw IllegalArgumentException("$k 必须为整数")}
    }
    private fun JSONObject.ns(k:String):String?=if(isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
    private fun JSONObject.nl(k:String):Long?=if(isNull(k)||!has(k)) null else strictLong(k)
    private fun JSONObject.ni(k:String):Int?=if(isNull(k)||!has(k)) null else strictInt(k)
    private fun JSONObject.nd(k:String):Double?=if(isNull(k)||!has(k)) null else getDouble(k)
    private fun encodeTarget(t:TargetSet)=obj("reps" to t.repetitions,"kg" to t.loadKg,"seconds" to t.seconds,"rest" to t.restSeconds,"rir" to t.rir)
    private fun target(o:JSONObject)=TargetSet(o.strictInt("reps"),o.nd("kg"),o.strictInt("seconds"),o.strictInt("rest"),o.ni("rir"))
    private fun encodeCourse(c:Course):JSONObject=obj("id" to c.id,"name" to c.name,"notes" to c.notes,"archived" to c.archived,"modifiedAt" to c.modifiedAt,"exercises" to arr(c.exercises){obj("id" to it.exerciseId,"targets" to arr(it.targets,::encodeTarget))})
    private fun course(o:JSONObject)=Course(o.getString("id"),o.getString("name"),list(o,"exercises"){CourseExercise(it.getString("id"),list(it,"targets",::target))},o.optString("notes"),o.optBoolean("archived"),o.strictLong("modifiedAt"))
    fun encode(j:Journal):String = obj(
        "schema" to SCHEMA,
        "profile" to with(j.profile){obj("adult" to adult,"ageBand" to ageBand,"heightCm" to heightCm,"experience" to experience,"frequency" to recentFrequency,"returning" to returning,"mass" to bodyMassKg,"measuredOn" to measuredOn,"direction" to direction,"days" to arr(days){it},"minutes" to minutes,"equipment" to equipment,"restrictions" to restrictions,"onboarded" to onboardingDone)},
        "settings" to with(j.settings){obj("unit" to unit,"rest" to defaultRest,"weeks" to historyWeeks,"minimum" to minimumSessions,"hours" to referenceHours,"motion" to motion)},
        "exercises" to arr(j.customExercises){e->obj("id" to e.id,"name" to e.name,"type" to e.type.name,"equipment" to e.equipment,"convention" to e.convention,"primary" to arr(e.primary){it.name},"secondary" to arr(e.secondary){it.name},"evidence" to e.evidence,"conditions" to e.conditions,"reviewed" to e.reviewed,"version" to e.mappingVersion)},
        "courses" to arr(j.courses,::encodeCourse),
        "occurrences" to arr(j.occurrences){obj("id" to it.id,"course" to it.courseId,"date" to it.date,"status" to it.status.name,"series" to it.seriesId,"override" to it.override?.let(::encodeCourse),"modifiedAt" to it.modifiedAt)},
        "sessions" to arr(j.sessions){s->obj("id" to s.id,"title" to s.title,"start" to s.startedAt,"zone" to s.zone,"end" to s.endedAt,"status" to s.status.name,"snapshot" to s.snapshot?.let(::encodeCourse),"exercises" to arr(s.exerciseIds){it},"occurrence" to s.occurrenceId,"pause" to s.pauseStarted,"pausedMs" to s.pausedMs,"note" to s.note,"modifiedAt" to s.modifiedAt,"completions" to s.completedActions?.let {arr(it){d->obj("exercise" to d.exerciseId,"at" to d.completedAt)}},"recordedDate" to s.recordedDate)},
        "sets" to arr(j.sets){s->obj("id" to s.id,"session" to s.sessionId,"exercise" to s.exerciseId,"at" to s.completedAt,"kg" to s.loadKg,"reps" to s.reps,"seconds" to s.seconds,"warmup" to s.warmup,"scale" to s.scale.name,"effort" to s.effort,"side" to s.side,"notes" to s.notes,"modifiedAt" to s.modifiedAt)},
        "checkIns" to arr(j.checkIns){obj("id" to it.id,"muscle" to it.muscle.name,"date" to it.date,"soreness" to it.soreness,"modifiedAt" to it.modifiedAt)},
        "goals" to arr(j.goals){g->obj("id" to g.id,"version" to g.version,"title" to g.title,"start" to g.start,"end" to g.end,"zone" to g.zone,"metric" to g.metric,"target" to g.target,"exercise" to g.exerciseId,"reps" to g.repsRequired,"paused" to g.paused,"confirmed" to g.confirmed,"muscle" to g.muscleId?.name,"source" to g.source)},
        "baselines" to arr(j.manualBaselines){obj("exercise" to it.exerciseId,"kg" to it.loadKg,"reps" to it.reps,"source" to it.source,"recordedOn" to it.recordedOn,"modifiedAt" to it.modifiedAt)},
        "achievements" to arr(j.achievements){obj("key" to it.key,"title" to it.title,"at" to it.awardedAt,"goal" to it.goalId,"version" to it.goalVersion,"valid" to it.valid,"ack" to it.acknowledged,"note" to it.note)},
        "restUntil" to j.restUntil,"deletedIds" to arr(j.deletedIds){it}
    ).toString()
    /** Rewrites any supported older payload into the current schema; used by the database upgrade step. */
    fun upgrade(text:String):String = encode(decode(text))
    fun decode(text:String):Journal {
        require(text.length<=8_000_000){"数据过大（上限8MB）"}
        val o=JSONObject(text);val schema=o.strictInt("schema");require(schema in 1..SCHEMA){"不支持的数据版本"}
        val p=o.getJSONObject("profile");val v=o.getJSONObject("settings")
        val j=Journal(profile=Profile(p.getString("adult"),p.getString("experience"),p.strictInt("frequency"),p.getBoolean("returning"),p.nd("mass"),p.ns("measuredOn"),p.getString("direction"),strings(p,"days").map { it.toInt() },p.strictInt("minutes"),p.getString("equipment"),p.getString("restrictions"),p.getBoolean("onboarded"),p.ns("ageBand"),p.nd("heightCm")),settings=Settings(v.getString("unit"),v.strictInt("rest"),v.strictInt("weeks"),v.strictInt("minimum"),v.strictInt("hours"),v.getBoolean("motion")),
            customExercises=list(o,"exercises"){Exercise(it.getString("id"),it.getString("name"),RecordType.valueOf(it.getString("type")),it.getString("equipment"),it.getString("convention"),strings(it,"primary").map(MuscleId::valueOf),strings(it,"secondary").map(MuscleId::valueOf),it.getString("evidence"),it.getString("conditions"),false,it.strictInt("version"))},
            courses=list(o,"courses",::course),occurrences=list(o,"occurrences"){Occurrence(it.getString("id"),it.ns("course"),it.getString("date"),OccurrenceStatus.valueOf(it.getString("status")),it.ns("series"),if(it.isNull("override"))null else course(it.getJSONObject("override")),it.strictLong("modifiedAt"))},
            sessions=list(o,"sessions"){Session(it.getString("id"),it.getString("title"),it.strictLong("start"),it.getString("zone"),it.nl("end"),SessionStatus.valueOf(it.getString("status")),if(it.isNull("snapshot"))null else course(it.getJSONObject("snapshot")),strings(it,"exercises"),it.ns("occurrence"),it.nl("pause"),it.strictLong("pausedMs"),it.getString("note"),it.strictLong("modifiedAt"),if(it.isNull("completions")||!it.has("completions"))null else list(it,"completions"){d->CompletedAction(d.getString("exercise"),d.strictLong("at"))},it.ns("recordedDate"))},
            sets=list(o,"sets"){SetEntry(it.getString("id"),it.getString("session"),it.getString("exercise"),it.strictLong("at"),it.nd("kg"),it.ni("reps"),it.ni("seconds"),it.getBoolean("warmup"),EffortScale.valueOf(it.getString("scale")),it.ni("effort"),it.getString("side"),it.getString("notes"),it.strictLong("modifiedAt"))},
            checkIns=list(o,"checkIns"){CheckIn(it.getString("id"),MuscleId.valueOf(it.getString("muscle")),it.getString("date"),it.ni("soreness"),it.strictLong("modifiedAt"))},
            goals=list(o,"goals"){Goal(it.getString("id"),it.strictInt("version"),it.getString("title"),it.getString("start"),it.getString("end"),it.getString("zone"),it.getString("metric"),it.getDouble("target"),it.ns("exercise"),it.strictInt("reps"),it.getBoolean("paused"),it.getBoolean("confirmed"),it.ns("muscle")?.let(MuscleId::valueOf),it.ns("source")?:"用户手设")},
            manualBaselines=list(o,"baselines"){ManualBaseline(it.getString("exercise"),it.getDouble("kg"),it.strictInt("reps"),it.getString("source"),it.getString("recordedOn"),it.strictLong("modifiedAt"))},
            achievements=list(o,"achievements"){Achievement(it.getString("key"),it.getString("title"),it.strictLong("at"),it.getString("goal"),it.strictInt("version"),it.getBoolean("valid"),it.getBoolean("ack"),it.ns("note")?:"")},restUntil=o.nl("restUntil"),deletedIds=strings(o,"deletedIds").toSet())
        j.validate();return j
    }
}
