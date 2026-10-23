package com.musclejournal.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.musclejournal.app.domain.*
import java.time.DayOfWeek
import java.time.LocalDate

@Composable internal fun PersonalHub(j:Journal,go:(String)->Unit,profile:()->Unit){
    val today=LocalDate.now()
    val records=remember(j,today){TrainingHistory.records(j,LocalDate.MIN,today)}
    val days=records.map {it.date}.toSet()
    val weekStart=today.with(DayOfWeek.MONDAY)
    val medals=remember(j){MedalCatalog.states(j)}
    val preview=medals.sortedByDescending {it.acquiredAt?:Long.MIN_VALUE}.take(4)
    var selectedMedal by remember {mutableStateOf<String?>(null)}
    val experience=ProfileOptions.experienceValue(j.profile.experience)
    val caption=listOfNotNull(ProfileOptions.ageLabel(j.profile).takeIf {it!="未填写"},experience.takeIf {it!="不确定"}).joinToString(" · ")
    val measurements=listOfNotNull(j.profile.heightCm?.let {weightText(it,"kg")+"cm"},j.profile.bodyMassKg?.let {weightText(it,"kg")+"kg"}).joinToString(" · ")

    PageTitle("个人")
    Spacer(Modifier.height(4.dp))
    Surface(color=Panel,shape=RoundedCornerShape(16.dp)){
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Art("ui-art/avatar.png",Modifier.size(64.dp).clip(CircleShape),scale=ContentScale.Crop)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("养肌宝",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                if(caption.isNotBlank())Text(caption,fontSize=12.sp,color=Muted)
            }
            TapIcon(Icons.Outlined.Edit,"编辑身体信息",profile)
        }
    }
    Spacer(Modifier.height(12.dp))
    Surface(color=Panel,shape=RoundedCornerShape(16.dp)){
        Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
            PersonalMetric(days.size,"天","训练天数",Modifier.weight(1f))
            VerticalDivider(Modifier.height(32.dp),color=Outline)
            PersonalMetric(records.size,"个","记录动作",Modifier.weight(1f))
            VerticalDivider(Modifier.height(32.dp),color=Outline)
            PersonalMetric(days.count {it>=weekStart},"天","本周训练",Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(12.dp))
    Surface(color=Panel,shape=RoundedCornerShape(16.dp)){
        Box(Modifier.padding(horizontal=12.dp)){DesignRow("身体信息",measurements,icon=Icons.Outlined.PersonOutline,click=profile)}
    }
    Spacer(Modifier.height(12.dp))
    Surface(color=Panel,shape=RoundedCornerShape(16.dp)){
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp)){
            DesignRow("成就",icon=Icons.Outlined.EmojiEvents,click={go("memories")},trailing={
                Text("${medals.count {it.acquiredAt!=null}} / ${medals.size}",color=Muted,fontSize=13.sp)
                Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
            })
            HorizontalDivider(color=Outline.copy(alpha=.5f))
            Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                preview.forEach {medal->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {selectedMedal=medal.definition.id}
                        .semantics {contentDescription="${medal.definition.title}奖牌，${if(medal.acquiredAt!=null)"已获得"else "未获得"}"}
                        .padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                        MedalArtwork(medal,Modifier.size(48.dp,58.dp))
                        Text(medal.definition.condition,fontSize=11.sp,lineHeight=14.sp,textAlign=TextAlign.Center,color=if(medal.acquiredAt!=null)Color.White else Muted)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Surface(color=Panel,shape=RoundedCornerShape(16.dp)){
        Box(Modifier.padding(horizontal=12.dp)){DesignRow("设置","更新 · 显示 · 关于",icon=Icons.Outlined.Settings,click={go("settings")})}
    }
    selectedMedal?.let {id->medals.firstOrNull {it.definition.id==id}?.let {MedalPresentation(j,it){selectedMedal=null}}}
}

@Composable private fun PersonalMetric(value:Int,unit:String,label:String,modifier:Modifier){
    Column(modifier.semantics(mergeDescendants=true){contentDescription="$label，$value$unit"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)){
            Text(value.toString(),fontSize=24.sp,fontWeight=FontWeight.SemiBold)
            Text(unit,fontSize=11.sp,color=Muted,modifier=Modifier.padding(bottom=4.dp))
        }
        Text(label,fontSize=12.sp,color=Muted)
    }
}
