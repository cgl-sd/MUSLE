package com.musclejournal.app.domain

import com.musclejournal.app.rendering.*
import org.junit.Assert.*
import org.junit.Test

class MuscleGuideTest {
    @Test fun bothViewsTogetherCoverEveryExistingMuscleGroup(){
        assertEquals(MuscleId.entries.toSet(),BodyView.entries.flatMap {BodyAnnotations.labels(it)}.map {it.muscle}.toSet())
        assertEquals(8,BodyAnnotations.labels(BodyView.FRONT).size);assertEquals(9,BodyAnnotations.labels(BodyView.BACK).size)
    }
    @Test fun eachViewHasUniqueLabelsAndOnlyClothedGlutesUseCoveredMarker(){
        BodyView.entries.forEach {v->val labels=BodyAnnotations.labels(v);assertEquals(labels.size,labels.map {it.muscle}.distinct().size)
            assertTrue(labels.all {it.x in 0f..1024f && it.y in 0f..1536f && it.labelY in 0f..1536f})}
        assertEquals(listOf(MuscleId.GLUTES),BodyView.entries.flatMap {BodyAnnotations.labels(it)}.filter {it.covered}.map {it.muscle})
    }
    @Test fun everyGroupHasAnIntroductionAndRelevantExistingExercise(){
        MuscleId.entries.forEach {m->assertTrue(MuscleGuide.introduction(m).isNotBlank());assertTrue(BodyAnnotations.title(m).isNotBlank());assertTrue(Catalog.exercises.any {m in it.primary})}
    }
}
