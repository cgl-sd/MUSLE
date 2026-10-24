package com.musclejournal.app.domain

import org.junit.Assert.*
import org.junit.Test

class ProfileOptionsTest {
    @Test fun compactAgeOptionsKeepAdultRulesSeparate(){
        assertEquals(listOf("未填写","未满18岁","18–39岁","40岁及以上"),ProfileOptions.ageBands)
        ProfileOptions.ageBands.filter {it !in listOf("未填写","未满18岁")}.forEach {assertEquals("已满18岁",ProfileOptions.adultFor(it))}
        assertEquals("未满18岁",ProfileOptions.adultFor("未满18岁"))
        assertEquals("未填写",ProfileOptions.adultFor("未填写"))
        assertTrue(ProfileOptions.ageBands.size<=4)
        assertEquals("已满18岁",ProfileOptions.adultFor("35–39岁"))
    }
    @Test fun oldUnspecifiedAdultAndExperienceRemainReadable(){
        val old=Profile(adult="已满18岁",experience="规律训练",days=listOf(2,4))
        assertEquals("18–39岁",ProfileOptions.ageLabel(old))
        assertEquals("3年以上",ProfileOptions.experienceValue(old.experience))
        val updated=old.copy(heightCm=175.5,ageBand="35–39岁",experience="5–10年")
        assertEquals(listOf(2,4),updated.days)
        assertEquals("18–39岁",ProfileOptions.ageLabel(updated))
        assertEquals("3年以上",ProfileOptions.experienceValue(updated.experience))
        assertEquals(listOf("不确定","不足1年","1–3年","3年以上"),ProfileOptions.experience)
        assertTrue(ProfileOptions.experience.size<=4)
    }
    @Test fun invalidHeightAndAgeCannotBeCommitted(){
        listOf(Double.NaN,Double.POSITIVE_INFINITY,49.0,251.0).forEach {height->assertTrue(runCatching {Profile(heightCm=height)}.isFailure)}
        assertTrue(runCatching {Profile(adult="已满18岁",ageBand="未满18岁")}.isFailure)
    }
    @Test fun equipmentSelectionPreservesSavedCustomValues(){
        assertEquals(listOf("哑铃","绳索"),ProfileOptions.selectedValues("哑铃 / 绳索"))
        assertTrue(ProfileOptions.equipmentOptions("家用拉力器 / 绳索").contains("家用拉力器"))
        assertEquals("哑铃 / 绳索",ProfileOptions.encodeSelectedValues(setOf("哑铃","绳索","哑铃")))
    }
    @Test fun freeWeightMigratesToBodyweightWithoutRemovingCustomEquipment(){
        assertFalse("自由重量" in ProfileOptions.equipment)
        assertEquals(listOf("自重","绳索"),ProfileOptions.equipmentValues("自由重量 / 绳索"))
        assertEquals(listOf("自重","家用拉力器"),ProfileOptions.equipmentValues("自重 / 自由重量 / 家用拉力器"))
        assertTrue("家用拉力器" in ProfileOptions.equipmentOptions("自由重量 / 家用拉力器"))
        assertFalse("自由重量" in ProfileOptions.equipmentOptions("自由重量 / 家用拉力器"))
    }
    @Test fun restrictionsAcceptLegacyNamesAndStableIds(){
        val exercises=Catalog.exercises
        val saved=ProfileOptions.restrictionIds(exercises,"barbell_bench / 哑铃卧推 / 已删除动作")
        assertEquals(listOf("barbell_bench","dumbbell_bench_press"),saved)
        assertEquals("barbell_bench / dumbbell_bench_press",ProfileOptions.encodeRestrictionIds(exercises,saved.toSet()))
    }
}
