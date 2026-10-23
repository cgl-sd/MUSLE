package com.musclejournal.app.domain

/** Plain anatomical summaries: OpenStax Anatomy & Physiology 2e, chapter 11, reviewed 2026-10-20. */
object MuscleGuide {
    const val VERSION="anatomy-20261020-v1"
    fun introduction(m:MuscleId):String = when(m){
        MuscleId.CHEST->"位于胸前，主要参与上臂向身体前方与内侧移动。"
        MuscleId.SHOULDERS->"覆盖肩部，前、中、后束分别参与抬臂向前、向侧方和向后移动。"
        MuscleId.TRAPEZIUS->"位于颈后与上背部，不同部位参与肩胛骨的上提、下压、后收与上回旋。"
        MuscleId.LATS->"位于背部两侧，主要参与上臂后伸、内收与内旋。"
        MuscleId.LOWER_BACK->"主要指下背部的竖脊肌群，参与躯干后伸、侧屈及姿势维持。"
        MuscleId.BICEPS->"位于上臂前侧，主要参与屈肘与前臂旋后，也参与肩部屈曲。"
        MuscleId.TRICEPS->"位于上臂后侧，主要参与伸肘，长头也参与上臂后伸。"
        MuscleId.FOREARMS->"包含前臂屈肌、伸肌及旋转肌群，参与手腕、手指和前臂的运动。"
        MuscleId.ABS->"位于腹部前侧，主要参与躯干前屈，也参与腹壁加压与躯干稳定。"
        MuscleId.OBLIQUES->"位于腹部两侧，参与躯干旋转、侧屈、前屈及腹壁加压。"
        MuscleId.GLUTES->"位于骨盆后外侧，包含臀大肌、臀中肌等，参与伸髋、髋外展和骨盆稳定。"
        MuscleId.QUADRICEPS->"位于大腿前侧，主要参与伸膝，其中股直肌也参与屈髋。"
        MuscleId.HAMSTRINGS->"位于大腿后侧，主要参与屈膝，大部分肌肉也参与伸髋。"
        MuscleId.CALVES->"包含腓肠肌与比目鱼肌等，主要参与足踝跖屈，腓肠肌也参与屈膝。"
    }
}
