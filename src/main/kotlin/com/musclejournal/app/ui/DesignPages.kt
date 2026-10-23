package com.musclejournal.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.musclejournal.app.domain.*
import java.time.*

@Composable internal fun MemoryPreview(j:Journal,a:Achievement,close:()->Unit,export:()->Unit){
    Editor("纪念",false,close,close,"关闭"){Text(a.title,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(Instant.ofEpochMilli(a.awardedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),color=Muted)}
}
@Composable internal fun Onboarding(close:()->Unit,complete:(Profile)->Unit){var step by remember {mutableIntStateOf(0)};var age by remember {mutableStateOf("未填写")};var height by remember {mutableStateOf("")};var experience by remember {mutableStateOf("不确定")};var mass by remember {mutableStateOf("")};var frequency by remember {mutableStateOf("")};var returning by remember {mutableStateOf(false)};var minutes by remember {mutableStateOf("45")};var equipment by remember {mutableStateOf(setOf<String>())};var direction by remember {mutableStateOf("规律训练")};var restrictions by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
    fun finish(){runCatching {val cm=height.takeIf {it.isNotBlank()}?.toDoubleOrNull();require(height.isBlank()||cm!=null && cm.isFinite() && cm in 50.0..250.0){"请检查身高"};complete(Profile(ProfileOptions.adultFor(age),experience,0,returning,mass.takeIf {it.isNotBlank()}?.let {loadValue(it,"kg")},direction=direction,days=emptyList(),minutes=45,equipment=equipment.joinToString(" / "),restrictions=restrictions,onboardingDone=true,ageBand=age,heightCm=cm))}.onFailure {error=it.message}}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){Surface(color=Ink,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()){Column(Modifier.padding(20.dp)){
        if(step==0){Heading("养肌宝");Spacer(Modifier.height(32.dp));Heading("记录每一次训练");Note("从身体开始");Art("body-art/front.png",Modifier.weight(1f).fillMaxWidth());Action("设置身体资料"){step=1};TextButton(close,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("先记录",color=Muted)}}else{
            Row(verticalAlignment=Alignment.CenterVertically){TapIcon(Icons.Outlined.ArrowBack,"上一步"){step--};Spacer(Modifier.weight(1f));Text("$step / 2",color=Muted)};Heading(if(step==1)"身体资料"else "训练偏好");Spacer(Modifier.height(20.dp));Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(22.dp)){
                when(step){1->{SystemWheelChoice("年龄段",age,ProfileOptions.ageBands){age=it};SystemWheelChoice("训练经验",experience,ProfileOptions.experience){experience=it};InlineInput("身高（cm）（可选）",height,{height=it});InlineInput("体重（kg）（可选）",mass,{mass=it});InlineSwitch("近期停训",returning){returning=it}}
                    2->{Choices(listOf("规律训练","力量进步","肌群训练量"),direction){direction=it};Text("可用器械",color=Muted);Row{listOf("哑铃","绳索","自重").forEach {v->FilterChip(v in equipment,{equipment=if(v in equipment)equipment-v else equipment+v},label={Text(v)})}};Input("避开动作（可选）",restrictions,{restrictions=it})}
                };error?.let {Text(it,color=Accent)}
            };Action(if(step<2)"下一步"else "进入首页"){if(step<2)step++ else finish()};TextButton({if(step==2)finish()else step++},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("跳过",color=Muted)}
        }
    }}}
}

@Composable internal fun MusclesPage(j:Journal,date:String,range:String,pick:(MuscleId)->Unit){var query by remember {mutableStateOf("")};PageTitle("肌群列表");Input("搜索肌群",query,{query=it},trackChange=false);val to=LocalDate.parse(date);val rows=Metrics.setsInRange(j,if(range=="近7天")to.minusDays(6)else to,to);MuscleId.entries.filter {it.displayName.contains(query)}.forEach {m->DesignRow(m.displayName,click={pick(m)},leading={AnatomyThumbnail(m,Modifier.size(76.dp,64.dp).clip(RoundedCornerShape(12.dp)))});HorizontalDivider(color=Outline)}}

@Composable internal fun SettingsPage(open:(String)->Unit){
    PageTitle("设置")
    UpdateSettingsRow()
    listOf(Triple("显示设置",Icons.Outlined.Tune,"preferences"),Triple("关于",Icons.Outlined.Info,"about")).forEach {(label,icon,target)->DesignRow(label,icon=icon,click={open(target)});HorizontalDivider(color=Outline)}
}
@Composable internal fun AboutPage(){
    PageTitle("关于")
    Spacer(Modifier.height(32.dp))
    Heading("养肌宝")
    Note("Yang Ji Bao")
    DesignRow("版本",com.musclejournal.app.BuildConfig.VERSION_NAME)
}
