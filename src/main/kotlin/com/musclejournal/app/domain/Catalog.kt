package com.musclejournal.app.domain

import com.musclejournal.app.domain.MuscleId.*

object Catalog {
    private const val UPPER="https://openstax.org/books/anatomy-and-physiology-2e/pages/11-5-muscles-of-the-pectoral-girdle-and-upper-limbs"
    private const val LOWER="https://openstax.org/books/anatomy-and-physiology-2e/pages/11-6-appendicular-muscles-of-the-pelvic-girdle-and-lower-limbs"
    private const val BACK="https://openstax.org/books/anatomy-and-physiology-2e/pages/11-3-axial-muscles-of-the-head-neck-and-back"
    private const val AXIAL="https://openstax.org/books/anatomy-and-physiology-2e/pages/11-4-axial-muscles-of-the-abdominal-wall-and-thorax"
    private fun e(id:String,name:String,p:List<MuscleId>,s:List<MuscleId> = emptyList(),type:RecordType=RecordType.LOAD_REPS,equipment:String="自由重量",convention:String="器械标示或杠铃总重量kg",source:String=UPPER,conditions:String="解剖/关节动作的间接推断，经映射审查；不是精确刺激比例。标准可控幅度，疼痛不作为训练目标。") = Exercise(id,name,type,equipment,convention,p,s,source,conditions)
    val exercises = listOf(
        e("barbell_bench","杠铃平板卧推",listOf(CHEST),listOf(SHOULDERS,TRICEPS),source="https://pubmed.ncbi.nlm.nih.gov/33049982/",conditions="平板杠铃卧推；研究为受训成年人、特定负重的EMG。参与依据，非增肌百分比；映射v1。"),
        e("dumbbell_bench_press","哑铃卧推",listOf(CHEST),listOf(SHOULDERS,TRICEPS),convention="两只哑铃合计kg"),
        e("incline_press","上斜杠铃卧推",listOf(CHEST),listOf(SHOULDERS,TRICEPS),source="https://pubmed.ncbi.nlm.nih.gov/33049982/",conditions="约30°上斜变式；角度改变参与，EMG不等于长期效果。记录准确角度于备注。"),
        e("pushup","俯卧撑",listOf(CHEST),listOf(TRICEPS,SHOULDERS,ABS),RecordType.REPS,"自重","仅次数，不将体重当完整负重"),
        e("chest_fly","器械夹胸",listOf(CHEST),equipment="固定器械",convention="同一器械标示kg，不能跨器械比较"),
        e("shoulder_press","哑铃推举",listOf(SHOULDERS),listOf(TRICEPS),convention="两只哑铃合计kg"),
        e("lateral_raise","哑铃侧平举",listOf(SHOULDERS),convention="两只哑铃合计kg"),
        e("rear_fly","俯身反向飞鸟",listOf(SHOULDERS),listOf(TRAPEZIUS),convention="两只哑铃合计kg"),
        e("lat_pulldown","高位下拉",listOf(LATS),listOf(BICEPS,FOREARMS),equipment="固定器械",convention="同一器械标示kg"),
        e("pullup","引体向上",listOf(LATS),listOf(BICEPS,FOREARMS),RecordType.REPS,"自重","仅次数，辅助重量请另建动作"),
        e("cable_row","坐姿绳索划船",listOf(LATS,TRAPEZIUS),listOf(BICEPS,SHOULDERS),equipment="绳索",convention="同一器械标示kg"),
        e("one_arm_row","单臂哑铃划船",listOf(LATS),listOf(BICEPS,TRAPEZIUS),convention="单只哑铃kg，须选择左/右侧").copy(requiresSide=true),
        e("shrug","哑铃耸肩",listOf(TRAPEZIUS),convention="两只哑铃合计kg"),
        e("curl","哑铃弯举",listOf(BICEPS),listOf(FOREARMS),convention="两只哑铃合计kg；单臂另建变式"),
        e("hammer_curl","锤式弯举",listOf(BICEPS,FOREARMS),convention="两只哑铃合计kg；肌群显示为粗分类"),
        e("pushdown","绳索下压",listOf(TRICEPS),equipment="绳索",convention="同一器械标示kg"),
        e("overhead_extension","绳索过顶臂屈伸",listOf(TRICEPS),equipment="绳索",convention="同一器械标示kg",source="https://pubmed.ncbi.nlm.nih.gov/35819335/",conditions="研究比较肩过顶和中立位置的肘伸展；姿势影响结果，不外推精确倍数。"),
        e("wrist_curl","腕弯举",listOf(FOREARMS),convention="双手总重量kg"),
        e("squat","杠铃深蹲",listOf(QUADRICEPS,GLUTES),listOf(LOWER_BACK,ABS),source=LOWER),
        e("bodyweight_squat","徒手深蹲",listOf(QUADRICEPS,GLUTES),type=RecordType.REPS,equipment="自重",convention="仅次数",source=LOWER),
        e("leg_press","腿举",listOf(QUADRICEPS,GLUTES),equipment="固定器械",convention="同一器械标示kg，不与深蹲等价",source=LOWER),
        e("leg_extension","腿屈伸",listOf(QUADRICEPS),equipment="固定器械",source=LOWER),
        e("leg_curl","坐姿腿弯举",listOf(HAMSTRINGS),equipment="固定器械",source=LOWER),
        e("romanian_deadlift","罗马尼亚硬拉",listOf(HAMSTRINGS,GLUTES),listOf(LOWER_BACK,FOREARMS),source=LOWER),
        e("hip_thrust","杠铃臀推",listOf(GLUTES),listOf(HAMSTRINGS),source=LOWER),
        e("calf_raise","站姿提踵",listOf(CALVES),source=LOWER),
        e("back_extension","背伸展",listOf(LOWER_BACK),listOf(GLUTES,HAMSTRINGS),RecordType.REPS,"自重","仅次数",source=BACK),
        e("plank","平板支撑",listOf(ABS),listOf(OBLIQUES),RecordType.DURATION,"自重","持续秒数，不折算负重",source=AXIAL),
        e("side_plank","侧平板支撑",listOf(OBLIQUES),listOf(ABS),RecordType.DURATION,"自重","持续秒数，选择左/右侧",source=AXIAL).copy(requiresSide=true),
        e("crunch","卷腹",listOf(ABS),type=RecordType.REPS,equipment="自重",convention="仅次数",source=AXIAL),
    )
    val byId:Map<String,Exercise> = exercises.associateBy {it.id}
}
