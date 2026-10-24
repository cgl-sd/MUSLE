package com.musclejournal.app.domain

import java.time.*
import java.util.UUID

fun newId() = UUID.randomUUID().toString()
enum class RecordType { LOAD_REPS, REPS, DURATION }
enum class SessionStatus { ACTIVE, COMPLETED, EARLY }
enum class OccurrenceStatus { PLANNED, SKIPPED, REST, CANCELLED, COMPLETED }
enum class EffortScale { RIR, RPE }

const val MAX_SESSION_TITLE_LENGTH = 120
const val MAX_SESSION_NOTE_LENGTH = 300
const val MAX_COURSE_NAME_LENGTH = 80
const val MAX_COURSE_NOTES_LENGTH = 2_000

data class EvidenceRecord(val source:String,val studyType:String,val population:String,val applicability:String,val limitations:String,val reviewedOn:String="2026-10-17",val version:Int=1)

data class Exercise(
    val id: String, val name: String, val type: RecordType,
    val equipment: String, val convention: String,
    val primary: List<MuscleId>, val secondary: List<MuscleId> = emptyList(),
    val evidence: String = "https://openstax.org/books/anatomy-and-physiology-2e/pages/11-5-muscles-of-the-pectoral-girdle-and-upper-limbs",
    val conditions: String = "标准动作、可控制幅度；实际执行方式会影响参与。", val reviewed: Boolean = true,
    val mappingVersion: Int = 1,
    val requiresSide: Boolean = false,
) {
    fun evidenceRecord():EvidenceRecord = when {
        !reviewed -> EvidenceRecord(evidence,"用户指定，未审查","未知",conditions,"不进入个体刺激评分或已审核基线")
        evidence.contains("33049982") -> EvidenceRecord(evidence,"原始EMG实验","30名受训成年人","五种杠铃卧推倾角、60%1RM实验；当前动作变式需匹配条件", "EMG不等于长期肥大或肌群机械负荷百分比。",version=mappingVersion)
        evidence.contains("35819335") -> EvidenceRecord(evidence,"纵向训练研究","研究中的成年人样本","比较过顶与中立位肘伸展；不外推全部器械与姿势","只支持姿势/肌长条件可能影响结果，不给个体增益倍数。",version=mappingVersion)
        else -> EvidenceRecord(evidence,"解剖教材与关节动作推断","参考人体解剖","主辅肌群为标准动作的粗分类功能映射：$conditions","间接支持，不是该训练动作的人体效果试验。稳定肌和个体技术差异不能由组数精确量化。",version=mappingVersion)
    }
}
data class TargetSet(val repetitions: Int = 10, val loadKg: Double? = null, val seconds: Int = 30, val restSeconds: Int = 90, val rir: Int? = null) {
    init { require(repetitions > 0 && seconds > 0 && restSeconds in 0..3600); require(loadKg == null || loadKg.isFinite() && loadKg >= 0); require(rir == null || rir in 0..10) }
}
data class CourseExercise(val exerciseId: String, val targets: List<TargetSet> = List(3) { TargetSet() })
data class Course(val id: String = newId(), val name: String, val exercises: List<CourseExercise>, val notes: String = "", val archived: Boolean = false, val modifiedAt: Long = System.currentTimeMillis())
data class Occurrence(val id: String = newId(), val courseId: String?, val date: String, val status: OccurrenceStatus = OccurrenceStatus.PLANNED, val seriesId: String? = null, val override: Course? = null, val modifiedAt: Long = System.currentTimeMillis())
data class Session(
    val id: String = newId(), val title: String, val startedAt: Long, val zone: String = ZoneId.systemDefault().id,
    val endedAt: Long? = null, val status: SessionStatus = SessionStatus.ACTIVE,
    val snapshot: Course? = null, val exerciseIds: List<String>, val occurrenceId: String? = null,
    val pauseStarted: Long? = null, val pausedMs: Long = 0, val note: String = "", val modifiedAt: Long = System.currentTimeMillis(),
    val completedActions: List<CompletedAction>? = null,
    val recordedDate:String? = null,
    val completedOccurrenceIds:List<String> = emptyList(),
) {
    init { require(title.isNotBlank() && startedAt > 0); ZoneId.of(zone); require(endedAt == null || endedAt >= startedAt); require(pausedMs >= 0);recordedDate?.let {LocalDate.parse(it)} }
    fun date(): LocalDate = recordedDate?.let(LocalDate::parse)?:Instant.ofEpochMilli(startedAt).atZone(ZoneId.of(zone)).toLocalDate()
    fun elapsed(now: Long): Long = ((pauseStarted ?: endedAt ?: now) - startedAt - pausedMs).coerceAtLeast(0)
    fun linkedOccurrenceIds():List<String> = (listOfNotNull(occurrenceId)+completedOccurrenceIds).distinct()
}
data class SetEntry(
    val id: String = newId(), val sessionId: String, val exerciseId: String,
    val completedAt: Long, val loadKg: Double? = null, val reps: Int? = null, val seconds: Int? = null,
    val warmup: Boolean = false, val scale: EffortScale = EffortScale.RIR, val effort: Int? = null,
    val side: String = "双侧", val notes: String = "", val modifiedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(sessionId.isNotBlank() && exerciseId.isNotBlank() && completedAt > 0)
        require(loadKg == null || loadKg.isFinite() && loadKg >= 0)
        require((reps != null && reps > 0 && seconds == null) || (seconds != null && seconds > 0 && reps == null))
        require(effort == null || (scale == EffortScale.RIR && effort in 0..10) || (scale == EffortScale.RPE && effort in 1..10))
        require(side in listOf("双侧", "左侧", "右侧"))
    }
    fun validate(exercise: Exercise) {
        require(exerciseId == exercise.id)
        if(exercise.requiresSide)require(side!="双侧"){"此动作需明确选择左侧或右侧"}
        when (exercise.type) {
            RecordType.LOAD_REPS -> require(loadKg != null && reps != null && seconds == null)
            RecordType.REPS -> require(loadKg == null && reps != null && seconds == null)
            RecordType.DURATION -> require(loadKg == null && reps == null && seconds != null)
        }
    }
}
data class Profile(val adult: String = "未填写", val experience: String = "不确定", val recentFrequency: Int = 0, val returning: Boolean = false, val bodyMassKg: Double? = null, val measuredOn: String? = null, val direction: String = "规律训练", val days: List<Int> = listOf(1,3,5), val minutes: Int = 45, val equipment: String = "", val restrictions: String = "", val onboardingDone: Boolean = false, val ageBand: String? = null, val heightCm: Double? = null) {
    init { require(ageBand == null || adult == ProfileOptions.adultFor(ageBand)); require(heightCm == null || heightCm.isFinite() && heightCm in 50.0..250.0); require(bodyMassKg == null || bodyMassKg.isFinite() && bodyMassKg in 10.0..500.0); require(recentFrequency in 0..14 && minutes in 5..300); require(days.all { it in 1..7 }) }
}
data class CheckIn(val id: String = newId(), val muscle: MuscleId, val date: String, val soreness: Int?, val modifiedAt: Long = System.currentTimeMillis()) { init { require(soreness == null || soreness in 0..10); LocalDate.parse(date) } }
/** One current aggregate schema carried by the database payload so older data can be migrated. */
const val JOURNAL_SCHEMA = 3
/** A baseline the user typed in, kept apart from baselines derived from history; `source` says where it came from. */
data class ManualBaseline(val exerciseId: String, val loadKg: Double, val reps: Int, val source: String, val recordedOn: String, val modifiedAt: Long = System.currentTimeMillis()) {
    init { require(exerciseId.isNotBlank() && loadKg.isFinite() && loadKg >= 0.0 && reps > 0 && source.isNotBlank()); LocalDate.parse(recordedOn) }
}
data class Goal(val id: String = newId(), val version: Int = 1, val title: String, val start: String, val end: String, val zone: String = ZoneId.systemDefault().id, val metric: String = "sessions", val target: Double = 3.0, val exerciseId: String? = null, val repsRequired: Int = 1, val paused: Boolean = false, val confirmed: Boolean = true, val muscleId: MuscleId? = null, val source: String = "用户手设") {
    init {
        require(title.isNotBlank() && version > 0 && target.isFinite() && target > 0); require(!LocalDate.parse(end).isBefore(LocalDate.parse(start))); ZoneId.of(zone)
        require(metric in listOf("sessions","load","reps","muscle")); require(source.isNotBlank()); require(repsRequired > 0)
        require(metric == "sessions" || (metric == "muscle" && muscleId != null) || (metric != "muscle" && exerciseId != null))
    }
}
data class Achievement(val key: String, val title: String, val awardedAt: Long, val goalId: String, val goalVersion: Int, val valid: Boolean = true, val acknowledged: Boolean = false, val note: String = "")
data class Settings(val unit: String = "kg", val defaultRest: Int = 90, val historyWeeks: Int = 4, val minimumSessions: Int = 3, val referenceHours: Int = 48, val motion: Boolean = true) {
    init { require(unit in listOf("kg","lb")); require(defaultRest in 0..3600); require(historyWeeks in 2..12 && minimumSessions in 2..20 && referenceHours in 12..168) }
}
data class Journal(
    val schema: Int = JOURNAL_SCHEMA, val profile: Profile = Profile(), val settings: Settings = Settings(),
    val customExercises: List<Exercise> = emptyList(), val courses: List<Course> = emptyList(),
    val occurrences: List<Occurrence> = emptyList(), val sessions: List<Session> = emptyList(), val sets: List<SetEntry> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(), val manualBaselines: List<ManualBaseline> = emptyList(), val goals: List<Goal> = emptyList(), val achievements: List<Achievement> = emptyList(),
    val restUntil: Long? = null, val deletedIds: Set<String> = emptySet(),
) {
    fun exercises() = if(customExercises.isEmpty())Catalog.exercises else Catalog.exercises + customExercises
    fun exercise(id: String) = Catalog.byId[id] ?: customExercises.first { it.id==id }
    fun active() = sessions.firstOrNull { it.status == SessionStatus.ACTIVE }
    fun validate() {
        require(schema == JOURNAL_SCHEMA)
        require(sessions.count { it.status == SessionStatus.ACTIVE } <= 1)
        val allExercises=exercises();val exerciseById=allExercises.associateBy {it.id}
        require(exerciseById.size == allExercises.size)
        require(sessions.map { it.id }.distinct().size == sessions.size && sets.map { it.id }.distinct().size == sets.size)
        require(courses.map { it.id }.distinct().size == courses.size && occurrences.map { it.id }.distinct().size == occurrences.size)
        require(goals.map { it.id to it.version }.distinct().size == goals.size)
        require(achievements.map { it.key }.distinct().size == achievements.size)
        require(achievements.all {a->goals.any {it.id==a.goalId && it.version==a.goalVersion}})
        require(checkIns.map {it.muscle to it.date}.distinct().size==checkIns.size)
        require(manualBaselines.map {it.exerciseId}.distinct().size==manualBaselines.size)
        require(manualBaselines.all {it.exerciseId in exerciseById})
        val sessionById=sessions.associateBy {it.id};val setsBySession=sets.groupBy {it.sessionId}
        for (s in sets) {
            require(s.sessionId in sessionById)
            s.validate(requireNotNull(exerciseById[s.exerciseId]){"未知动作 ${s.exerciseId}"})
        }
        for (s in sessions) {
            require(s.exerciseIds.all {it in exerciseById});require(s.status == SessionStatus.ACTIVE || s.endedAt != null)
        }
        for(s in sessions){s.completedActions?.let {done->
            require(done.map {it.exerciseId}.distinct().size==done.size)
            val known=s.exerciseIds+setsBySession[s.id].orEmpty().map {it.exerciseId}
            require(done.all {d->d.exerciseId in known && d.completedAt>=s.startedAt && (s.endedAt==null || d.completedAt<=s.endedAt)})
        }}
        require(sessions.all {it.completedOccurrenceIds.distinct().size==it.completedOccurrenceIds.size})
        val occurrenceById=occurrences.associateBy {it.id}
        require(sessions.all {s->s.completedOccurrenceIds.isEmpty() ||
            (s.status!=SessionStatus.ACTIVE && s.completedOccurrenceIds.all {occurrenceById[it]?.status==OccurrenceStatus.COMPLETED})})
        val courseById=courses.associateBy {it.id}
        for (c in courses + occurrences.mapNotNull { it.override } + sessions.mapNotNull { it.snapshot }) {
            require(c.name.isNotBlank()); require(c.exercises.map {it.exerciseId}.distinct().size==c.exercises.size); require(c.exercises.all {e->e.targets.isNotEmpty() && e.exerciseId in exerciseById})
        }
        occurrences.forEach { LocalDate.parse(it.date); require(it.courseId == null || it.courseId in courseById) }
        require(goals.all {it.exerciseId == null || it.exerciseId in exerciseById})
    }
}

data class MuscleTotals(val direct: Int, val auxiliary: Int, val lastAt: Long?)
data class RecoveryReference(val directWeight:Double,val auxiliaryWeight:Double,val observations:Int,val lastAt:Long?,val halfLifeHours:Int,val explanation:String)
data class MuscleReference(val weeklyMean:Double?,val ratio:Double?,val sessions:Int,val weeks:Int,val explanation:String)
data class Baseline(val status: String, val matchingSets: Int, val typicalLoad: Double?, val ratio: Double?, val explanation: String)
object Metrics {
    const val VERSION = "participation-v1/history-v1"
    fun muscle(j: Journal, muscle: MuscleId, rows: List<SetEntry>, side:String="双侧"): MuscleTotals {
        val working = rows.filter { !it.warmup && (side=="双侧" || it.side=="双侧" || it.side==side) }
        return MuscleTotals(working.count { muscle in j.exercise(it.exerciseId).primary }, working.count { muscle in j.exercise(it.exerciseId).secondary }, working.filter { muscle in j.exercise(it.exerciseId).primary + j.exercise(it.exerciseId).secondary }.maxOfOrNull { it.completedAt })
    }
    fun recoveryReference(j:Journal,muscle:MuscleId,now:Long,side:String="双侧"):RecoveryReference {
        val rows=j.sets.filter {!it.warmup && it.completedAt<=now && (side=="双侧" || it.side=="双侧" || it.side==side) && j.exercise(it.exerciseId).reviewed && muscle in j.exercise(it.exerciseId).primary+j.exercise(it.exerciseId).secondary}
        fun weight(r:SetEntry)=kotlin.math.exp(-kotlin.math.ln(2.0)*(now-r.completedAt).toDouble()/3600000/j.settings.referenceHours)
        return RecoveryReference(rows.filter {muscle in j.exercise(it.exerciseId).primary}.sumOf(::weight),rows.filter {muscle in j.exercise(it.exerciseId).secondary}.sumOf(::weight),rows.size,rows.maxOfOrNull {it.completedAt},j.settings.referenceHours,"组权重衰减v1：每个已保存正式组按2^(-经过小时/参数小时)衰减。主辅分列；参数为产品假设，可调整，不代表生理恢复速度或剩余疲劳。RIR缺失不补值。")
    }
    fun personalMuscleReference(j:Journal,muscle:MuscleId,rows:List<SetEntry>,to:LocalDate,side:String="双侧"):MuscleReference {
        val cutoff=to.with(DayOfWeek.MONDAY)
        val start=cutoff.minusWeeks(j.settings.historyWeeks.toLong())
        val sessions=j.sessions.filter {it.status!=SessionStatus.ACTIVE && it.date()>=start && it.date()<cutoff}
        val byId=sessions.associateBy {it.id}
        val history=j.sets.filter {it.sessionId in byId && !it.warmup && j.exercise(it.exerciseId).reviewed && muscle in j.exercise(it.exerciseId).primary && (side=="双侧" || it.side=="双侧" || it.side==side)}
        val weeks=history.map {byId.getValue(it.sessionId).date().with(DayOfWeek.MONDAY)}.distinct().size
        val count=history.map {it.sessionId}.distinct().size
        val note="个人记录参考v1：排除所选日期所在周，向前${j.settings.historyWeeks}周，按有记录训练周的直接正式组均值比较；至少2周、${j.settings.minimumSessions}场。不是%1RM、肌肉刺激或安全上限。"
        if(j.profile.adult!="已满18岁" || j.profile.returning || weeks<2 || count<j.settings.minimumSessions)return MuscleReference(null,null,count,weeks,"待校准。$note")
        val mean=history.size.toDouble()/weeks
        val current=rows.count {!it.warmup && j.exercise(it.exerciseId).reviewed && muscle in j.exercise(it.exerciseId).primary && (side=="双侧" || it.side=="双侧" || it.side==side)}
        return MuscleReference(mean,current/mean,count,weeks,note)
    }
    fun baseline(j: Journal, row: SetEntry): Baseline {
        val e=j.exercise(row.exerciseId)
        val manual=j.manualBaselines.lastOrNull { it.exerciseId==row.exerciseId }
        if (manual!=null && manual.loadKg>0 && row.loadKg!=null) return Baseline("手动基线",1,manual.loadKg,row.loadKg/manual.loadKg,"手动录入 ${manual.reps} 次 · ${manual.recordedOn} · ${manual.source}")
        if (j.profile.adult != "已满18岁" || j.profile.returning || !e.reviewed || e.type != RecordType.LOAD_REPS) return Baseline("待校准",0,null,null,"需要适用的成人资料、可比动作与稳定训练记录；不估计最大力量。")
        val session=j.sessions.first { it.id==row.sessionId }
        val cutoff=session.startedAt
        val start=Instant.ofEpochMilli(cutoff).minus(Duration.ofDays(j.settings.historyWeeks*7L)).toEpochMilli()
        val allowed=j.sessions.filter { it.id!=row.sessionId && it.status!=SessionStatus.ACTIVE && it.endedAt!! < cutoff }.map { it.id }.toSet()
        val history=j.sets.filter { it.sessionId in allowed && it.exerciseId==row.exerciseId && !it.warmup && it.side==row.side && it.reps==row.reps && it.completedAt>=start && it.loadKg!=null }
        if (history.map { it.sessionId }.distinct().size<j.settings.minimumSessions) return Baseline("数据积累中",history.size,null,null,"需${j.settings.historyWeeks}周内至少${j.settings.minimumSessions}场已结束训练中同动作、同次数、同侧别的正式组；当前场次不入基线。这是产品校准门槛。")
        val loads=history.map { it.loadKg!! }.sorted(); val typical=if(loads.size%2==0)(loads[loads.size/2-1]+loads[loads.size/2])/2 else loads[loads.size/2]
        return Baseline("可比历史",history.size,typical,if(typical>0 && row.loadKg!=null)row.loadKg/typical else null,"与此前同动作同次数的负重中位数比较，不是%1RM、刺激分数或安全阈值；${VERSION}。")
    }
    fun goalProgress(j: Journal,g: Goal): Double {
        val zone=ZoneId.of(g.zone);val start=LocalDate.parse(g.start);val end=LocalDate.parse(g.end)
        val ids=j.sessions.filter { it.status!=SessionStatus.ACTIVE && Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() in start..end }.map { it.id }.toSet()
        val rows=j.sets.filter { it.sessionId in ids && !it.warmup }
        return when(g.metric) { "sessions" -> rows.map { it.sessionId }.distinct().size.toDouble(); "load" -> rows.filter { it.exerciseId==g.exerciseId && (it.reps?:0)>=g.repsRequired }.maxOfOrNull { it.loadKg?:0.0 }?:0.0; "muscle" -> rows.count { g.muscleId!=null && g.muscleId in j.exercise(it.exerciseId).primary }.toDouble(); else -> rows.filter { it.exerciseId==g.exerciseId }.maxOfOrNull { (it.reps?:0).toDouble() }?:0.0 }
    }
    fun reconcile(j: Journal, now: Long): Journal {
        val existing=j.achievements.associateBy { it.key }.toMutableMap()
        j.goals.filter { it.confirmed }.forEach { g ->
            val key="goal:${g.id}:${g.version}:${g.start}:${g.end}"
            val qualified=goalProgress(j,g)>=g.target
            val old=existing[key]
            if(old!=null)existing[key]=old.copy(valid=qualified)
            else if(qualified && !g.paused)existing[key]=Achievement(key,g.title,now,g.id,g.version)
        }
        return j.copy(achievements=existing.values.sortedBy { it.awardedAt })
    }
}
