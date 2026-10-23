package com.musclejournal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.musclejournal.app.domain.*
import java.time.LocalDate
import kotlin.math.ceil

@Composable internal fun TrainingHistoryChart(records:List<HistoryAction>,period:HistoryPeriod,from:LocalDate,to:LocalDate){
    val daily=TrainingHistory.days(records,from,to)
    val dateOnly=period==HistoryPeriod.DAY && records.any {it.hour==null}
    val counts=if(dateOnly)listOf(records.size)else if(period==HistoryPeriod.DAY)(0..23).map {hour->records.count {it.hour==hour}}else daily.map {it.count}
    var selected by rememberSaveable(period,from.toString(),to.toString()){mutableIntStateOf(-1)}
    val peak=counts.maxOrNull()?:0
    val step=ceil(peak.coerceAtLeast(3)/3.0).toInt()
    val top=step*3
    fun tick(index:Int):String=when(period){
        HistoryPeriod.DAY->if(dateOnly)"当天"else if(index in listOf(0,6,12,18,23))index.toString()else ""
        HistoryPeriod.WEEK->"一二三四五六日"[index].toString()
        HistoryPeriod.MONTH->daily[index].date.dayOfMonth.let {if(it==1 || index==daily.lastIndex || (it%5==0 && index<=daily.lastIndex-3))it.toString()else ""}
    }
    fun label(index:Int):String=when(period){
        HistoryPeriod.DAY->if(dateOnly)"当天"else "${index.toString().padStart(2,'0')}:00–${index.toString().padStart(2,'0')}:59"
        HistoryPeriod.WEEK->"周"+"一二三四五六日"[index]
        HistoryPeriod.MONTH->"${daily[index].date.dayOfMonth}日"
    }
    Surface(color=Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("动作记录",color=Muted,fontSize=13.sp,modifier=Modifier.weight(1f))
                Text(records.size.toString(),fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                Text(" 个动作",color=Muted,fontSize=12.sp)
            }
            Row(Modifier.fillMaxWidth()){
                Box(Modifier.width(22.dp).height(132.dp)){
                    listOf(top,step*2,step,0).forEachIndexed {index,value->
                        Box(Modifier.offset(y=132.dp*index/3-10.dp).width(22.dp).height(20.dp),contentAlignment=Alignment.CenterStart){Text(value.toString(),fontSize=10.sp,color=Muted)}
                    }
                }
                Column(Modifier.weight(1f)){
                    Box(Modifier.fillMaxWidth().height(132.dp)){
                        Canvas(Modifier.matchParentSize()){
                            repeat(4){i->val y=size.height*i/3f;drawLine(Outline.copy(alpha=.55f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())}
                        }
                        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.Bottom){
                            counts.forEachIndexed {index,count->
                                Box(Modifier.weight(1f).fillMaxHeight().semantics {contentDescription="${label(index)}，${count}个动作"}.clickable {selected=index}.padding(horizontal=if(period==HistoryPeriod.WEEK)8.dp else 2.dp),contentAlignment=Alignment.BottomCenter){
                                    if(count>0)Box(Modifier.widthIn(max=32.dp).fillMaxWidth().height(132.dp*(count.toFloat()/top)).background(if(selected==index)Color.White else Accent,RoundedCornerShape(topStart=3.dp,topEnd=3.dp)))
                                }
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top=6.dp).height(18.dp)){
                        counts.indices.filter {tick(it).isNotEmpty()}.forEach {index->
                            Text(tick(index),modifier=Modifier.offset(x=maxWidth*(index+.5f)/counts.size-12.dp).width(24.dp),fontSize=10.sp,color=if(selected==index)Color.White else Muted,maxLines=1,textAlign=TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
