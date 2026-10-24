package com.musclejournal.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

data class MuscleFatigueScore(
    val left: Int = TrainingFatigue.NO_FATIGUE_SCORE,
    val right: Int = TrainingFatigue.NO_FATIGUE_SCORE,
) {
    init {
        require(left in TrainingFatigue.MIN_SCORE..TrainingFatigue.MAX_SCORE)
        require(right in TrainingFatigue.MIN_SCORE..TrainingFatigue.MAX_SCORE)
    }

    val display: Int get() = maxOf(left, right)

    fun score(side: String = "双侧"): Int = when (side) {
        "左侧" -> left
        "右侧" -> right
        else -> display
    }

    fun normalized(side: String = "双侧"): Float =
        (score(side) - TrainingFatigue.MIN_SCORE).toFloat() /
            (TrainingFatigue.MAX_SCORE - TrainingFatigue.MIN_SCORE)
}

data class FatigueState(
    val muscles: Map<MuscleId, MuscleFatigueScore> = emptyMap(),
) {
    fun score(muscle: MuscleId, side: String = "双侧"): Int =
        muscles[muscle]?.score(side) ?: TrainingFatigue.NO_FATIGUE_SCORE

    fun normalized(muscle: MuscleId, side: String = "双侧"): Float =
        muscles[muscle]?.normalized(side) ?: 0f

    fun overall(muscles: Iterable<MuscleId> = MuscleId.entries): Int =
        muscles.maxOfOrNull { score(it) } ?: TrainingFatigue.NO_FATIGUE_SCORE
}

/**
 * A deterministic, product-level training-state reference.
 *
 * Each saved action contributes once. It is not a physiological fatigue measurement,
 * a set count, or an estimate of recovery time.
 */
object TrainingFatigue {
    const val VERSION = "training-fatigue-v1"
    const val MIN_SCORE = 1
    const val MAX_SCORE = 10
    const val NO_FATIGUE_SCORE = MIN_SCORE
    const val PRIMARY_WEIGHT = 1.0
    const val SECONDARY_WEIGHT = 0.4
    const val HALF_LIFE_HOURS = 72.0
    const val SCORE_RESPONSE = 1.4
    const val MAX_AGE_HOURS = 14.0 * 24.0

    private data class SideContribution(
        val muscle: MuscleId,
        val weight: Double,
    )

    fun calculate(
        journal: Journal,
        now: Long = System.currentTimeMillis(),
    ): FatigueState {
        val raw = mutableMapOf<Pair<MuscleId, String>, Double>()

        val sessions = journal.sessions.associateBy { it.id }
        val actions = ExerciseProgress.index(journal)
        val legacySets = journal.sets.asSequence().filter {!it.warmup}.groupBy {it.sessionId}
        journal.sessions
            .asSequence()
            .filter { it.status != SessionStatus.ACTIVE }
            .flatMap { session ->
                completedContributions(
                    session,
                    legacySets[session.id].orEmpty(),
                    actions.forSession(session),
                    now,
                ).asSequence()
            }
            .forEach { contribution ->
                val exercise = runCatching { journal.exercise(contribution.exerciseId) }.getOrNull()
                    ?.takeIf { it.reviewed } ?: return@forEach
                val anchor = actionAnchor(
                    sessions.getValue(contribution.sessionId),
                    contribution.completedAt,
                    now,
                )
                    ?: return@forEach
                val elapsedHours = (now - anchor).coerceAtLeast(0L) / 3_600_000.0
                if (elapsedHours >= MAX_AGE_HOURS) return@forEach

                val decay = 0.5.pow(elapsedHours / HALF_LIFE_HOURS)
                val sides = when (contribution.side) {
                    "左侧" -> listOf("左侧")
                    "右侧" -> listOf("右侧")
                    else -> listOf("左侧", "右侧")
                }
                val targets = buildList {
                    exercise.primary.distinct().forEach {
                        add(SideContribution(it, PRIMARY_WEIGHT))
                    }
                    exercise.secondary.distinct()
                        .filterNot { it in exercise.primary }
                        .forEach {
                            add(SideContribution(it, SECONDARY_WEIGHT))
                        }
                }
                targets.forEach { target ->
                    sides.forEach { side ->
                        val key = target.muscle to side
                        raw[key] = (raw[key] ?: 0.0) + target.weight * decay
                    }
                }
            }

        val normalized = raw.mapValues { (_, value) -> scoreForRaw(value) }
        val muscles = MuscleId.entries.associateWith { muscle ->
            MuscleFatigueScore(
                left = normalized[muscle to "左侧"] ?: NO_FATIGUE_SCORE,
                right = normalized[muscle to "右侧"] ?: NO_FATIGUE_SCORE,
            )
        }
        return FatigueState(muscles)
    }

    private data class SessionContribution(
        val sessionId: String,
        val exerciseId: String,
        val completedAt: Long,
        val side: String,
    )

    private fun completedContributions(
        session: Session,
        legacyRows: List<SetEntry>,
        explicitActions: List<CompletedAction>,
        now: Long,
    ): List<SessionContribution> {
        if (session.completedActions != null) {
            return explicitActions
                .filter { it.completedAt <= now }
                .groupBy { it.exerciseId }
                .map { (exerciseId, rows) ->
                    SessionContribution(
                        sessionId = session.id,
                        exerciseId = exerciseId,
                        completedAt = rows.maxOf { it.completedAt },
                        side = rows.mapNotNull {it.side}.distinct().singleOrNull() ?: "双侧",
                    )
                }
        }

        return legacyRows
            .asSequence()
            .filter { it.completedAt <= now }
            .flatMap { row ->
                (if (row.side == "双侧") listOf("左侧", "右侧") else listOf(row.side))
                    .asSequence().map { side -> row.copy(side = side) }
            }
            .groupBy { it.exerciseId to it.side }
            .map { (key, rows) ->
                SessionContribution(
                    sessionId = session.id,
                    exerciseId = key.first,
                    completedAt = rows.maxOf { it.completedAt },
                    side = key.second,
                )
            }
    }

    /**
     * Backfilled records have a calendar day but no known hour. Anchor them to the end
     * of that day, capped at now so the UI never makes a future action appear fresh.
     */
    private fun actionAnchor(
        session: Session,
        completedAt: Long,
        now: Long,
    ): Long? {
        val recordedDate = session.recordedDate ?: return completedAt
        val zone = ZoneId.of(session.zone)
        val date = LocalDate.parse(recordedDate)
        if (date > Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) return null
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        return minOf(endOfDay, now)
    }

    private fun scoreForRaw(raw: Double): Int {
        if (raw <= 0.0) return NO_FATIGUE_SCORE
        val score = MIN_SCORE + (MAX_SCORE - MIN_SCORE) * (1.0 - exp(-raw / SCORE_RESPONSE))
        return score.roundToInt().coerceIn(MIN_SCORE, MAX_SCORE)
    }
}
