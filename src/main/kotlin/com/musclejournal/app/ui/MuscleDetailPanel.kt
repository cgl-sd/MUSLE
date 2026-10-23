package com.musclejournal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclejournal.app.domain.*
import java.time.*

@Composable internal fun MuscleDetailPanel(j:Journal,muscle:MuscleId,side:String,date:String,range:String,open:(String)->Unit,train:()->Unit){
    val to=LocalDate.parse(date);val from=if(range=="近7天")to.minusDays(6)else to
    Text(if(muscle==MuscleId.CHEST)"胸部"else muscle.displayName,fontSize=26.sp,fontWeight=FontWeight.Bold)
    Text("${muscle.displayName} · $side",color=Muted);HorizontalDivider()
    Text("相关训练",fontSize=16.sp,fontWeight=FontWeight.Bold)
    val records=ExerciseProgress.onDates(j,from,to).filter {val e=j.exercise(it.exerciseId);muscle in e.primary+e.secondary}
    if(records.isEmpty())Text("暂无记录",color=Muted)
    records.forEach {r->TrainingCard{TrainingCardRow(j.exercise(r.exerciseId).name,j.exercise(r.exerciseId),"已完成",click={open(r.sessionId)})}}
}

@Composable internal fun MuscleIntroduction(j:Journal,muscle:MuscleId,close:()->Unit){
    HorizontalDivider(color=Outline)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(com.musclejournal.app.rendering.BodyAnnotations.title(muscle),fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        IconButton(close){Icon(Icons.Outlined.Close,"关闭肌群介绍",tint=Muted)}
    }
    Text(MuscleGuide.introduction(muscle),fontSize=14.sp,color=Color.White)
    Text("相关动作",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
    val exercises=j.exercises().filter {muscle in it.primary}.take(3)
    Text(exercises.joinToString(" · "){it.name},fontSize=13.sp,color=Color.White)
    Spacer(Modifier.height(16.dp))
}
