package com.musclejournal.app.rendering

import com.musclejournal.app.domain.MuscleId

/** A null score means unavailable; it must never be displayed as zero load or full recovery. */
data class MuscleVisualState(
    val normalizedScore: Float? = null,
    val normalizedLeftScore: Float? = normalizedScore,
    val normalizedRightScore: Float? = normalizedScore,
) {
    init {
        require(
            listOfNotNull(normalizedScore, normalizedLeftScore, normalizedRightScore)
                .all {it.isFinite() && it in 0f..1f}
        )
    }
}

/** Rendering input only. Values are supplied by the domain layer and are not inferred while drawing. */
data class MuscleRenderState(
    val muscles: Map<MuscleId, MuscleVisualState> = emptyMap(),
    val selectedMuscle: MuscleId? = null,
    val selectedSide: String = "双侧",
)
