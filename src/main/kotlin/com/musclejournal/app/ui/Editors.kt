package com.musclejournal.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musclejournal.app.domain.*
import java.time.*

@Composable internal fun ProfileEditor(j:Journal,busy:Boolean,close:()->Unit,save:(Profile)->Unit){
    val p=j.profile
    var age by remember {mutableStateOf(ProfileOptions.ageLabel(p))};var experience by remember {mutableStateOf(ProfileOptions.experienceValue(p.experience))}
    var height by remember {mutableStateOf(p.heightCm?.let {weightText(it,"kg")}?:"")}
    var mass by remember {mutableStateOf(p.bodyMassKg?.let {weightText(it,"kg")}?:"")}
    var equipment by remember {mutableStateOf(ProfileOptions.equipmentValues(p.equipment).toSet())}
    var restrictions by remember {mutableStateOf(ProfileOptions.restrictionIds(j.exercises(),p.restrictions))}
    Editor("编辑身体信息",busy,close,{
        val weight=mass.takeIf {it.isNotBlank()}?.toDoubleOrNull();val cm=height.takeIf {it.isNotBlank()}?.toDoubleOrNull()
        require(mass.isBlank()||weight!=null){"请检查体重"};require(height.isBlank()||cm!=null && cm.isFinite() && cm in 50.0..250.0){"请检查身高"}
        val orderedEquipment=ProfileOptions.equipmentOptions(p.equipment).filter {it in equipment}
        save(p.copy(adult=ProfileOptions.adultFor(age),ageBand=age,experience=experience,heightCm=cm,bodyMassKg=weight,measuredOn=if(weight!=null)LocalDate.now().toString()else null,equipment=ProfileOptions.encodeSelectedValues(orderedEquipment),restrictions=ProfileOptions.encodeRestrictionIds(j.exercises(),restrictions),onboardingDone=true))
    },"保存资料",cancelLabel=null){
        SystemWheelChoice("年龄段",age,ProfileOptions.ageBands){age=it}
        SystemWheelChoice("训练经验",experience,ProfileOptions.experience){experience=it}
        InlineInput("身高（cm）",height,{height=it});InlineInput("体重（kg）",mass,{mass=it})
        InlineEquipmentChoice(equipment,ProfileOptions.equipmentOptions(p.equipment)){equipment=it}
        InlineExerciseChoice(j,restrictions){restrictions=it}
    }
}

@Composable private fun InlineEquipmentChoice(selected:Set<String>,options:List<String>,change:(Set<String>)->Unit){
    val dirty=LocalFormDirty.current
    var choosing by remember {mutableStateOf(false)}
    var draft by remember {mutableStateOf(selected)}
    val summary=options.filter {it in selected}.joinToString("、").ifBlank {"未选择"}
    InlinePickerRow("常用器械",summary){draft=selected;choosing=true}
    if(choosing)AlertDialog(
        onDismissRequest={choosing=false},
        containerColor=Panel,
        title={Text("常用器械")},
        text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(2.dp)){
                options.forEach {option->
                    val checked=option in draft
                    Row(Modifier.fillMaxWidth().toggleable(checked,role=Role.Checkbox,onValueChange={on->
                        draft=if(on)draft+option else draft-option
                    }).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=checked,onCheckedChange=null,colors=CheckboxDefaults.colors(checkedColor=Accent,uncheckedColor=Muted,checkmarkColor=Color.White))
                        Text(option,fontSize=16.sp)
                    }
                }
            }
        },
        confirmButton={TextButton({if(draft!=selected){dirty();change(draft)};choosing=false}){Text("确定")}},
        dismissButton={TextButton({choosing=false}){Text("取消",color=Muted)}},
    )
}

@Composable private fun InlineExerciseChoice(j:Journal,selected:List<String>,change:(List<String>)->Unit){
    val dirty=LocalFormDirty.current
    var choosing by remember {mutableStateOf(false)}
    var draft by remember {mutableStateOf(selected)}
    InlinePickerRow("希望避开的动作",if(selected.isEmpty())"未选择" else "已选 ${selected.size} 项"){draft=selected;choosing=true}
    if(choosing)Dialog(onDismissRequest={choosing=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Surface(color=Ink,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
            Column(Modifier.padding(horizontal=20.dp)){
                CompositionLocalProvider(LocalPageBack provides {choosing=false}){
                    TrainingRecordPage(j,draft,busy=false,change={draft=it},save={if(draft!=selected){dirty();change(draft)};choosing=false},title="选择动作",saveLabel="确认选择",requireSelection=false,enforceSides=false)
                }
            }
        }
    }
}

@Composable private fun InlinePickerRow(label:String,value:String,click:()->Unit){
    InlineFieldRow(label,click){
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Text(value,color=Color.White,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(18.dp),tint=Muted)
        }
    }
}

@Composable internal fun CourseEditor(j:Journal,initial:Course?,startAsCopy:Boolean=false,busy:Boolean,close:()->Unit,save:(Course,LocalDate)->Unit){
    val id=remember(initial?.id,startAsCopy){if(initial==null||startAsCopy)newId()else initial.id}
    var name by remember(initial?.id){mutableStateOf(initial?.name?:"")}
    var notes by remember(initial?.id){mutableStateOf(initial?.notes?:"")}
    var rows by remember(initial?.id){mutableStateOf(initial?.exercises?:emptyList())}
    var date by remember {mutableStateOf(LocalDate.now())}
    var query by remember {mutableStateOf("")}
    var adding by remember {mutableStateOf(false)}
    val centers=remember {mutableMapOf<String,Float>()}
    Editor(if(initial==null)"新建课程" else "安排课程",busy,close,{
        require(name.isNotBlank()&&rows.isNotEmpty()){"请填写课程名称并添加动作"}
        save(Course(id,name.trim(),rows,notes,false),date)
    },"确认安排",spacing=8.dp){
        val dirty=LocalFormDirty.current
        CompactInput("课程名称",name,{name=it},false,MAX_COURSE_NAME_LENGTH)
        DateField("训练日期",date){date=it}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("课程动作",fontSize=14.sp,color=Muted,modifier=Modifier.weight(1f))
            Text("${rows.size} 个",fontSize=13.sp,color=Muted)
        }
        if(rows.isEmpty())Note("还没有动作，请从动作库添加。")
        rows.forEachIndexed {index,row->key(row.exerciseId){
            TrainingCard(
                Modifier
                    .onGloballyPositioned {centers[row.exerciseId]=it.boundsInWindow().center.y}
                    .pointerInput(rows){var distance=0f;detectDragGesturesAfterLongPress(
                        onDrag={event,amount->event.consume();distance+=amount.y},
                        onDragEnd={
                            val y=(centers[row.exerciseId]?:0f)+distance
                            val nearest=rows.indices.minByOrNull {kotlin.math.abs((centers[rows[it].exerciseId]?:y)-y)}?:index
                            rows=rows.toMutableList().also {it.add(nearest,it.removeAt(index))}
                            dirty()
                            distance=0f
                        },
                    )}
            ){
                TrainingCardRow(
                    j.exercise(row.exerciseId).name,
                    j.exercise(row.exerciseId),
                    "第${index+1}项",
                    trailing={TapIcon(Icons.Outlined.RemoveCircleOutline,"移除${j.exercise(row.exerciseId).name}"){
                        dirty()
                        rows=rows.filterNot {it.exerciseId==row.exerciseId}
                    }},
                )
            }
        }}
        Secondary(if(adding)"收起动作列表" else "添加动作"){adding=!adding}
        if(adding){
            Input("搜索动作",query,{query=it},trackChange=false)
            j.exercises()
                .filter {it.name.contains(query)&&rows.none {row->row.exerciseId==it.id}}
                .forEach {exercise->
                    DesignRow(
                        exercise.name,
                        subtitle=exercise.equipment,
                        click={dirty();rows=rows+CourseExercise(exercise.id);adding=false},
                        leading={ExerciseThumbnail(exercise,Modifier.size(56.dp,44.dp))},
                    )
                }
        }
    }
}
