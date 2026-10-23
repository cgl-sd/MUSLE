package com.musclejournal.app.domain

object ProfileOptions {
    val ageBands = listOf("未填写", "未满18岁", "18–39岁", "40岁及以上")
    val experience = listOf("不确定", "不足1年", "1–3年", "3年以上")
    val equipment = listOf("哑铃", "杠铃", "固定器械", "绳索", "阻力带", "自重", "壶铃")
    private val legacyAgeBands = setOf("18–24岁", "25–29岁", "30–34岁", "35–39岁", "40–44岁", "45–49岁", "50–54岁", "55–59岁", "60–64岁", "65–69岁", "70岁及以上", "18岁及以上")
    fun adultFor(ageBand:String):String {
        require(ageBand in ageBands || ageBand in legacyAgeBands)
        return when(ageBand){"未填写"->"未填写";"未满18岁"->"未满18岁";else->"已满18岁"}
    }
    fun ageBandValue(value:String):String = when(value) {
        in ageBands -> value
        "18–24岁", "25–29岁", "30–34岁", "35–39岁", "18岁及以上" -> "18–39岁"
        "40–44岁", "45–49岁", "50–54岁", "55–59岁", "60–64岁", "65–69岁", "70岁及以上" -> "40岁及以上"
        else -> "未填写"
    }
    fun ageLabel(profile:Profile)=profile.ageBand?.let(::ageBandValue) ?: when(profile.adult){"已满18岁"->"18–39岁";"未满18岁"->"未满18岁";else->"未填写"}
    fun experienceValue(value:String):String = when(value) {
        in experience -> value
        "从未训练", "不足3个月", "3–6个月", "6–12个月" -> "不足1年"
        "1–2年", "2–3年" -> "1–3年"
        "3–5年", "5–10年", "10年以上", "规律训练" -> "3年以上"
        else -> "不确定"
    }
    fun selectedValues(value:String):List<String> =
        value.split(Regex("\\s*/\\s*|、|，|,")).map {it.trim()}.filter {it.isNotEmpty()}.distinct()
    fun equipmentValues(value:String):List<String> =
        selectedValues(value).map {if(it=="自由重量")"自重" else it}.distinct()
    fun encodeSelectedValues(values:Iterable<String>):String =
        values.map {it.trim()}.filter {it.isNotEmpty()}.distinct().joinToString(" / ")
    fun equipmentOptions(saved:String):List<String> = (equipment+equipmentValues(saved)).distinct()
    fun restrictionIds(exercises:List<Exercise>,saved:String):List<String> {
        val byId=exercises.associateBy {it.id}
        val byName=exercises.associateBy {it.name}
        return selectedValues(saved).mapNotNull {byId[it]?.id ?: byName[it]?.id}.distinct()
    }
    fun encodeRestrictionIds(exercises:List<Exercise>,ids:Collection<String>):String =
        exercises.filter {it.id in ids}.joinToString(" / ") {it.id}
}
