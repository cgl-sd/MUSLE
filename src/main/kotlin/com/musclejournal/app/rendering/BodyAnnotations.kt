package com.musclejournal.app.rendering

import com.musclejournal.app.domain.MuscleId

/** Endpoints are reviewed in the immutable artwork's 1024 x 1536 coordinate system. */
object BodyAnnotations {
    data class Label(val muscle:MuscleId,val x:Float,val y:Float,val left:Boolean,val labelY:Float,val covered:Boolean=false)
    private val front=listOf(
        Label(MuscleId.SHOULDERS,295f,365f,true,300f),
        Label(MuscleId.CHEST,610f,380f,false,345f),
        Label(MuscleId.BICEPS,295f,500f,true,485f),
        Label(MuscleId.ABS,550f,535f,false,550f),
        Label(MuscleId.FOREARMS,220f,630f,true,680f),
        Label(MuscleId.OBLIQUES,622f,600f,false,730f),
        Label(MuscleId.QUADRICEPS,390f,950f,true,990f),
        Label(MuscleId.CALVES,614f,1225f,false,1250f),
    )
    private val back=listOf(
        Label(MuscleId.TRAPEZIUS,480f,260f,true,210f),
        Label(MuscleId.SHOULDERS,725f,350f,false,320f),
        Label(MuscleId.TRICEPS,300f,465f,true,440f),
        Label(MuscleId.LATS,620f,490f,false,485f),
        Label(MuscleId.FOREARMS,214f,650f,true,660f),
        Label(MuscleId.LOWER_BACK,550f,620f,false,650f),
        Label(MuscleId.GLUTES,610f,780f,false,835f,true),
        Label(MuscleId.HAMSTRINGS,395f,965f,true,1000f),
        Label(MuscleId.CALVES,674f,1190f,false,1220f),
    )
    fun labels(view:BodyView):List<Label> = if(view==BodyView.FRONT)front else back
    fun title(muscle:MuscleId):String = when(muscle){
        MuscleId.CHEST->"胸大肌"
        MuscleId.FOREARMS->"前臂肌群"
        MuscleId.LOWER_BACK->"下背肌群"
        else->muscle.displayName
    }
}
