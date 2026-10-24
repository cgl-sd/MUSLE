package com.musclejournal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.musclejournal.app.domain.*
import com.musclejournal.app.rendering.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Red muscle anatomy above today's saved training; the dock is owned by the app scaffold. */
@Composable internal fun BodyOverview(
    j:Journal,now:Long,viewName:String,heroHeight:androidx.compose.ui.unit.Dp,
    selected:MuscleId?,selectedSide:String,annotations:Boolean,assetFailure:Boolean,
    onView:(String)->Unit,onPick:(MuscleId,String)->Unit,onList:()->Unit,onClose:()->Unit,onRecord:(String)->Unit,
){
    val today=LocalDate.now()
    val currentView=BodyView.valueOf(viewName)
    val fatigue=remember(j,now/60_000L){TrainingFatigue.calculate(j,maxOf(now,System.currentTimeMillis()))}
    val visual=remember(fatigue){MuscleId.entries.associateWith {muscle->
        MuscleVisualState(
            normalizedScore=fatigue.normalized(muscle),
            normalizedLeftScore=fatigue.normalized(muscle,"左侧"),
            normalizedRightScore=fatigue.normalized(muscle,"右侧"),
        )
    }}
    val trainingScore=remember(fatigue){fatigue.overall()}
    var error by remember {mutableStateOf<String?>(null)};var generation by remember {mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){
        if(assetFailure || error!=null){
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Box(Modifier.fillMaxWidth()){HomeHeading(today);HomeTrainingStatus(trainingScore,Modifier.align(Alignment.TopEnd))}
                Text("人体暂不可用",color=Muted)
                TextButton({error=null;generation++}){Text("重试")}
                TextButton(onList){Text("肌群列表")}
                if(annotations)MuscleId.entries.forEach {m->TextButton({onPick(m,"双侧")}){Text(BodyAnnotations.title(m))}}
                if(selected!=null)MuscleIntroduction(j,selected,onClose)else TodayTraining(j,now,onRecord)
            }
        }else{
            Box(Modifier.fillMaxWidth().height(heroHeight+68.dp)){
                val imageModifier=Modifier.fillMaxSize().padding(top=52.dp,bottom=52.dp)
                key(generation){AndroidView(factory={ctx->BodyIllustrationView(ctx,onPick,{error=it},assetFailure)},onRelease={it.release()},update={it.update(currentView,MuscleRenderState(MuscleDisplayMode.TRAINING_STATE,visual,selected,selectedSide))},modifier=imageModifier)}
                if(annotations)BodyAnnotationLayer(currentView,selected,onPick,imageModifier,rightInset=64.dp)
                HomeHeading(today)
                HomeTrainingStatus(trainingScore,Modifier.align(Alignment.TopEnd))
                BodyTools(
                    viewName=viewName,
                    annotations=annotations,
                    onView=onView,
                    onList=onList,
                    modifier=Modifier.align(Alignment.BottomEnd).padding(bottom=2.dp),
                )
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(selected!=null)MuscleIntroduction(j,selected,onClose)else TodayTraining(j,now,onRecord)
            }
        }
    }
}
@Composable private fun HomeTrainingStatus(score:Int,modifier:Modifier=Modifier){
    Column(modifier.padding(top=8.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text("训练状态",color=Muted,fontSize=13.sp)
        Text("$score/10",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium)
    }
}
@Composable private fun HomeHeading(today:LocalDate){
    Column{Text("身体",fontSize=28.sp,fontWeight=FontWeight.Bold);Text(today.format(DateTimeFormatter.ofPattern("M月d日  E",Locale.CHINA)),color=Muted,fontSize=12.sp,modifier=Modifier.heightIn(min=36.dp).wrapContentHeight())}
}
@Composable private fun TodayTraining(j:Journal,now:Long,open:(String)->Unit){
    val nowMinute=now/60_000L
    val today=remember(nowMinute){LocalDate.now()}
    val records=remember(j,today,nowMinute){ExerciseProgress.onDates(j,today,today,maxOf(now,System.currentTimeMillis()))}
    HorizontalDivider(Modifier.padding(top=8.dp,bottom=12.dp),color=Outline);Text("训练",fontSize=20.sp,fontWeight=FontWeight.Bold)
    if(records.isEmpty())Text("暂无训练记录",color=Muted,fontSize=14.sp,modifier=Modifier.padding(vertical=16.dp))
    records.forEach {record->
        val exercise=j.exercise(record.exerciseId)
        TrainingCard{TrainingCardRow(exercise.name,exercise,click={open(record.sessionId)},trailing={Text("已完成",color=Muted,fontSize=12.sp)})}
    }
    Spacer(Modifier.height(12.dp))
}

@Composable private fun BodyTools(viewName:String,annotations:Boolean,onView:(String)->Unit,onList:()->Unit,modifier:Modifier=Modifier){
    Column(
        modifier=modifier,
        horizontalAlignment=Alignment.End,
        verticalArrangement=Arrangement.spacedBy(8.dp),
    ){
        BodyTool("正面",Icons.Outlined.Man,viewName=="FRONT"){onView("FRONT")}
        BodyTool("背面",Icons.Outlined.Man,viewName=="BACK"){onView("BACK")}
        BodyTool("肌群",Icons.AutoMirrored.Outlined.FormatListBulleted,annotations,onList)
    }
}
@Composable private fun BodyTool(label:String,icon:ImageVector,selected:Boolean,onClick:()->Unit){
    Box(
        Modifier.size(56.dp).clip(CircleShape)
            .clickable(role=Role.Button,onClick=onClick)
            .semantics(mergeDescendants=true){
                contentDescription=label
                this.selected=selected
            },
        contentAlignment=Alignment.Center,
    ){
        Box(
            Modifier.size(44.dp)
                .background(if(selected)Accent.copy(alpha=.18f) else Color(0xFF141414),CircleShape)
                .border(1.dp,if(selected)Accent.copy(alpha=.78f) else Outline,CircleShape),
            contentAlignment=Alignment.Center,
        ){
            Icon(icon,null,tint=if(selected)Color.White else Color(0xFFB8B8B8),modifier=Modifier.size(28.dp))
        }
    }
}

/** Icon-only bottom bar: three tabs share one pill, the personal entry sits separately on the right. */
@Composable internal fun BottomNav(selected:String,home:()->Unit,courses:()->Unit,history:()->Unit,me:()->Unit){
    Row(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Surface(Modifier.weight(1f).height(56.dp),shape=RoundedCornerShape(50),color=Color(0xFF1B1B1B)){
            Row(Modifier.padding(4.dp),verticalAlignment=Alignment.CenterVertically){
                listOf(Triple("首页",Icons.Outlined.Home,home),Triple("课程",Icons.Outlined.FitnessCenter,courses),Triple("记录",Icons.Outlined.Description,history)).forEach {(label,icon,action)->
                    NavTab(label,icon,selected==label,Modifier.weight(1f).fillMaxHeight(),action)
                }
            }
        }
        Box(Modifier.size(56.dp).border(1.dp,Outline,CircleShape),contentAlignment=Alignment.Center){NavTab("个人",Icons.Outlined.Person,selected=="个人",Modifier.size(48.dp),me)}
    }}
@Composable private fun NavTab(label:String,icon:ImageVector,on:Boolean,modifier:Modifier,onClick:()->Unit){
    Column(modifier.clip(RoundedCornerShape(50)).background(if(on)Accent.copy(alpha=.16f) else Color.Transparent,RoundedCornerShape(50))
        .selectable(on,interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Tab){onClick()},
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Icon(icon,label,Modifier.size(30.dp).then(if(label=="课程")Modifier.rotate(-45f)else Modifier),tint=if(on)Color.White else Color(0xFF888888))
    }}
/** Compact training entry: it no longer occupies the whole dock. */
@Composable internal fun TrainingPill(active:Boolean,enabled:Boolean,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,bottom=6.dp),horizontalArrangement=Arrangement.End){
        Button(onClick,enabled=enabled,shape=RoundedCornerShape(40.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),
            colors=ButtonDefaults.buttonColors(containerColor=if(active)Color(0xFFEF3539) else Color(0xFFF5F5F5),contentColor=if(active)Color.White else Ink),
            modifier=Modifier.heightIn(min=48.dp)){
            if(active){Icon(Icons.Outlined.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp))}
            Text(if(active)"继续记录" else "开始训练",fontSize=15.sp,fontWeight=FontWeight.Bold)
        }
    }}

@Composable internal fun AnatomyThumbnail(muscle:MuscleId,modifier:Modifier){
    val context=LocalContext.current
    val bitmap=remember(muscle){
        val view=BodyRegions.viewFor(muscle);val rect=BodyRegions.crop(muscle)
        val sample=if(maxOf(rect.width(),rect.height())<=512)1 else 2
        runCatching {context.assets.open("body-art/${if(view==BodyView.FRONT)"front" else "back"}.png").use {stream->val decoder=android.graphics.BitmapRegionDecoder.newInstance(stream,false)?:error("图片不可读取");try {decoder.decodeRegion(rect,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample})}finally {decoder.recycle()}}}.getOrNull()
    }
    if(bitmap!=null)Image(bitmap.asImageBitmap(),null,modifier,contentScale=ContentScale.Crop)
    else Box(modifier.background(Panel))
    DisposableEffect(bitmap){onDispose {bitmap?.recycle()}}
}

@Composable internal fun ExerciseThumbnail(exercise:Exercise,modifier:Modifier){ExerciseLineIcon(exercise,modifier)}

@Composable internal fun MuscleIntroduction(j:Journal,muscle:MuscleId,close:()->Unit){
    HorizontalDivider(color=Outline)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(BodyAnnotations.title(muscle),fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        IconButton(close){Icon(Icons.Outlined.Close,"关闭肌群介绍",tint=Muted)}
    }
    Text(MuscleGuide.introduction(muscle),fontSize=14.sp,color=Color.White)
    Text("相关动作",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
    val exercises=j.exercises().filter {muscle in it.primary}.take(3)
    Text(exercises.joinToString(" · "){it.name},fontSize=13.sp,color=Color.White)
    Spacer(Modifier.height(16.dp))
}
