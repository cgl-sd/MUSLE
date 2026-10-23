package com.musclejournal.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.musclejournal.app.domain.*
import java.time.Instant
import java.time.ZoneId

@Composable internal fun MemoriesPage(j:Journal){
    var selected by remember {mutableStateOf<String?>(null)}
    val medals=remember(j){MedalCatalog.states(j)}
    PageTitle("成就")
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val columns=maxOf(4,(maxWidth.value/100).toInt())
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            medals.chunked(columns).forEach {row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                row.forEach {medal->val m=medal.definition
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {selected=m.id}.semantics {contentDescription="${m.title}奖牌，${if(medal.acquiredAt!=null)"已获得"else "未获得"}"}.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
                        MedalArtwork(medal,Modifier.size(64.dp,76.dp))
                        Text(m.condition,fontSize=11.sp,lineHeight=14.sp,textAlign=TextAlign.Center,color=if(medal.acquiredAt!=null)Color.White else Muted)
                    }
                }
                repeat(columns-row.size){Spacer(Modifier.weight(1f))}
            }}
        }
    }
    selected?.let {id->medals.firstOrNull {it.definition.id==id}?.let {medal->
        MedalPresentation(j,medal,{selected=null})
    }}
}
@Composable internal fun MedalPresentation(j:Journal,medal:MedalState,close:()->Unit){
    val earned=medal.acquiredAt!=null
    val reveal=remember {Animatable(if(earned && j.settings.motion)0f else 1f)}
    LaunchedEffect(medal.definition.id,j.settings.motion){
        if(earned && j.settings.motion){reveal.snapTo(0f);reveal.animateTo(1f,tween(850,easing=FastOutSlowInEasing))}else reveal.snapTo(1f)
    }
    Dialog(onDismissRequest=close){Surface(color=Panel,shape=RoundedCornerShape(24.dp)){
        Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TapIcon(Icons.Outlined.Close,"关闭奖牌",close)}
            Box(Modifier.size(200.dp),contentAlignment=Alignment.Center){
                if(earned)Box(Modifier.size(190.dp).background(Brush.radialGradient(listOf(Accent.copy(alpha=.18f*reveal.value),Color.Transparent)),CircleShape))
                MedalArtwork(medal,Modifier.size(148.dp,174.dp).graphicsLayer {val p=reveal.value;rotationY=(1-p)*-150f;scaleX=.65f+.35f*p;scaleY=.65f+.35f*p;translationY=(1-p)*-40.dp.toPx();alpha=p;cameraDistance=16*density})
            }
            Text(medal.definition.title,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Text(medal.definition.condition,fontSize=16.sp,textAlign=TextAlign.Center)
            if(earned){
                Text("获取日期",fontSize=12.sp,color=Muted)
                Text(Instant.ofEpochMilli(medal.acquiredAt!!).atZone(ZoneId.systemDefault()).toLocalDate().toString(),fontSize=20.sp,fontWeight=FontWeight.Bold)
            }else Text("未获得",color=Muted,fontSize=14.sp)
            Action("关闭",onClick=close)
        }
    }}
}
