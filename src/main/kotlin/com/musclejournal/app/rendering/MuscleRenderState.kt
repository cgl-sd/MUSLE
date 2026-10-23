package com.musclejournal.app.rendering

import com.musclejournal.app.domain.MuscleId

enum class MuscleDisplayMode {
    SESSION_LOAD,
    TRAINING_STATE,
    PERSONAL_REFERENCE,
    RECOVERY_ESTIMATE,
}

/** A null score means unavailable; it must never be displayed as zero load or full recovery. */
data class MuscleVisualState(
    val normalizedScore: Float? = null,
    val normalizedLeftScore: Float? = normalizedScore,
    val normalizedRightScore: Float? = normalizedScore,
    val completedDirectSets: Int = 0,
    val completedLeftSets: Int = completedDirectSets,
    val completedRightSets: Int = completedDirectSets,
    val personalLeftRatio: Float? = null,
    val personalRightRatio: Float? = null,
    val recordedLeftEffort: Float? = null,
    val recordedRightEffort: Float? = null,
) {
    init {
        require(listOfNotNull(recordedLeftEffort,recordedRightEffort).all {it.isFinite() && it in 0f..1f})
        require(completedDirectSets >= 0 && completedLeftSets >= 0 && completedRightSets >= 0)
        require(
            listOfNotNull(normalizedScore, normalizedLeftScore, normalizedRightScore)
                .all {it.isFinite() && it in 0f..1f}
        )
    }
}

/** Rendering input only. Values are supplied by the domain layer and are not inferred while drawing. */
data class MuscleRenderState(
    val mode: MuscleDisplayMode,
    val muscles: Map<MuscleId, MuscleVisualState> = emptyMap(),
    val selectedMuscle: MuscleId? = null,
    val selectedSide: String = "双侧",
)
