package com.musclejournal.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.*
import com.musclejournal.app.domain.*

@Composable internal fun TrainingRecordPage(j:Journal,ids:List<String>,busy:Boolean,change:(List<String>)->Unit,save:()->Unit,title:String="记录训练",saveLabel:String="保存记录 · ${ids.size}个动作",requireSelection:Boolean=true){
    var query by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("全部")}
    val centers=remember {mutableMapOf<String,Float>()}
    var dragged by remember {mutableStateOf<String?>(null)}
    var offset by remember {mutableFloatStateOf(0f)}
    fun toggle(id:String){if(!busy)change(if(id in ids)ids-id else ids+id)}
    val visible=TodayTrainingPlan.orderedExercises(j,ids).filter {e->
        (e.name.contains(query)||e.equipment.contains(query)) && when(category){
            "胸"->MuscleId.CHEST in e.primary
            "背"->e.primary.any {it in listOf(MuscleId.LATS,MuscleId.TRAPEZIUS,MuscleId.LOWER_BACK)}
            "肩"->MuscleId.SHOULDERS in e.primary
            "腿"->e.primary.any {it in listOf(MuscleId.QUADRICEPS,MuscleId.HAMSTRINGS,MuscleId.CALVES,MuscleId.GLUTES)}
            "手臂"->e.primary.any {it in listOf(MuscleId.BICEPS,MuscleId.TRICEPS,MuscleId.FOREARMS)}
            else->true
        }
    }
    Column(Modifier.fillMaxSize()){
        PageTitle(title)
        Spacer(Modifier.height(8.dp))
        ExerciseSearch(query){query=it}
        Row(Modifier.padding(vertical=8.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("全部","胸","背","肩","腿","手臂").forEach {v->FilterChip(category==v,{category=v},label={Text(v)})}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
            if(visible.isEmpty())Text("没有匹配的动作",color=Muted,modifier=Modifier.padding(vertical=24.dp))
            visible.forEach {e->key(e.id){
                val selected=e.id in ids
                val moving=dragged==e.id
                Row(Modifier.fillMaxWidth().onGloballyPositioned {centers[e.id]=it.positionInWindow().y+it.size.height/2f}
                    .zIndex(if(moving)1f else 0f).graphicsLayer {translationY=if(moving)offset else 0f}
                    .background(if(selected)Panel else Color.Transparent)
                    .pointerInput(ids,busy,visible.map {it.id}){var origin=0f;detectDragGesturesAfterLongPress(
                        onDragStart={if(selected&&!busy){origin=centers[e.id]?:0f;offset=0f;dragged=e.id}},
                        onDrag={event,delta->if(dragged==e.id){event.consume();offset+=delta.y}},
                        onDragCancel={dragged=null;offset=0f},
                        onDragEnd={if(dragged==e.id){val y=origin+offset;val target=ids.filter {id->visible.any {it.id==id}}.minByOrNull {kotlin.math.abs((centers[it]?:y)-y)};if(target!=null&&!busy){val next=ids.toMutableList();next.add(ids.indexOf(target),next.removeAt(ids.indexOf(e.id)));change(next)}};dragged=null;offset=0f})}
                    .clickable(enabled=!busy){toggle(e.id)}.heightIn(min=56.dp).padding(start=8.dp),verticalAlignment=Alignment.CenterVertically){
                    ExerciseThumbnail(e,Modifier.size(44.dp,42.dp))
                    Text(e.name,color=Color.White,fontSize=16.sp,modifier=Modifier.weight(1f).padding(horizontal=12.dp))
                    IconButton({toggle(e.id)},enabled=!busy){Icon(if(selected)Icons.Outlined.RemoveCircle else Icons.Outlined.AddCircleOutline,if(selected)"取消选择${e.name}"else "选择${e.name}",tint=Color.White,modifier=Modifier.size(26.dp))}
                }
                HorizontalDivider(color=Outline,thickness=0.5.dp)
            }}
        }
        Spacer(Modifier.height(12.dp))
        Action(saveLabel,!busy&&(ids.isNotEmpty()||!requireSelection),save)
        Spacer(Modifier.height(12.dp))
    }
}
