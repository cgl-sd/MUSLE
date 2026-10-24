package com.musclejournal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musclejournal.app.domain.*
import java.time.LocalDate

@Composable internal fun BackfillEditor(j:Journal,initialDate:LocalDate,busy:Boolean,close:()->Unit,save:(Session)->Unit){
    val startDate=initialDate.coerceAtMost(LocalDate.now())
    var date by rememberSaveable(stateSaver=LocalDateSaver){mutableStateOf(startDate)}
    var title by rememberSaveable {mutableStateOf(BackfillRecord.DEFAULT_TITLE)}
    var selected by rememberSaveable {mutableStateOf(emptyList<String>())}
    var selectedSides by rememberSaveable {mutableStateOf(emptyMap<String,String>())}
    var pickerIds by rememberSaveable {mutableStateOf(emptyList<String>())}
    var pickerSides by rememberSaveable {mutableStateOf(emptyMap<String,String>())}
    var choosing by rememberSaveable {mutableStateOf(false)}
    var askExit by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    fun persist(){
        if(busy)return
        error=null;askExit=false
        runCatching {save(BackfillRecord.create(j,date,title,selected,sides=selectedSides))}.onFailure {error=it.message?:"请检查填写内容"}
    }
    fun leave(){
        if(busy)return
        when{
            choosing->choosing=false
            BackfillRecord.hasChanges(startDate,date,title,selected,selectedSides)->askExit=true
            else->close()
        }
    }
    Dialog(onDismissRequest={leave()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Surface(color=Ink,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()){
            Column(Modifier.padding(horizontal=20.dp)){
                if(choosing){
                    CompositionLocalProvider(LocalPageBack provides {choosing=false}){
                        TrainingRecordPage(j,pickerIds,busy,{pickerIds=it;pickerSides=pickerSides.filterKeys {id->id in it}},{selected=pickerIds;selectedSides=pickerSides.filterKeys {it in pickerIds};choosing=false},selectedSides=pickerSides,onSideChange={id,side->pickerSides=if(side==null)pickerSides-id else pickerSides+(id to side!!)},title="添加锻炼项目",saveLabel="确认添加")
                    }
                }else{
                    PageTitle("补录训练",back={leave()})
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                        Input("训练名称",title,{title=it},maxLength=MAX_SESSION_TITLE_LENGTH)
                        DateField("日期",date){date=it}
                        Text("锻炼项目")
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                            selected.forEach {id->val exercise=j.exercise(id)
                                TrainingCard{
                                    TrainingCardRow(exercise.name,exercise,trailing={TapIcon(Icons.Outlined.RemoveCircleOutline,"移除${exercise.name}"){selected=selected-id;selectedSides=selectedSides-id}})
                                    if(exercise.requiresSide){
                                        Row(Modifier.padding(start=12.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                            listOf("左侧","右侧").forEach {side->
                                                FilterChip(selected=selectedSides[id]==side,onClick={selectedSides=selectedSides+(id to side)},label={Text(side)},enabled=!busy)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Secondary("添加锻炼项目",!busy){pickerIds=selected;pickerSides=selectedSides;choosing=true}
                        error?.let {Text(it,color=Accent)}
                    }
                    Box(Modifier.padding(vertical=14.dp)){Action(if(busy)"保存中…"else "保存训练",!busy && selected.isNotEmpty()){persist()}}
                }
            }
        }
    }
    if(askExit)AlertDialog(onDismissRequest={if(!busy)askExit=false},containerColor=Panel,
        title={Text("是否保存所填写的内容？")},
        confirmButton={TextButton(onClick={persist()},enabled=!busy && selected.isNotEmpty()){Text("是")}},
        dismissButton={TextButton(onClick={if(!busy){askExit=false;close()}},enabled=!busy){Text("直接退出",color=Accent)}})
}
