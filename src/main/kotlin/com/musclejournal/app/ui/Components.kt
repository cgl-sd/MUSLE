package com.musclejournal.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.musclejournal.app.domain.Exercise
import java.time.*
import java.time.format.DateTimeFormatter

internal val LocalDateSaver=Saver<LocalDate,Long>(save={it.toEpochDay()},restore={LocalDate.ofEpochDay(it)})
internal val LocalPageBack=staticCompositionLocalOf<(() -> Unit)?> {null}
internal val LocalFormDirty=staticCompositionLocalOf<()->Unit> { {} }
@Composable internal fun Heading(title:String,subtitle:String?=null){Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold);if(!subtitle.isNullOrBlank())Text(subtitle,color=Muted,fontSize=13.sp)}
@Composable internal fun Note(text:String){Text(text,color=Muted,fontSize=14.sp)}
@Composable internal fun Action(text:String,enabled:Boolean=true,onClick:()->Unit){Button(onClick,enabled=enabled,shape=CircleShape,contentPadding=PaddingValues(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(text,fontWeight=FontWeight.Bold,fontSize=16.sp)}}
@Composable internal fun Secondary(text:String,enabled:Boolean=true,onClick:()->Unit){OutlinedButton(onClick,enabled=enabled,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Outline),modifier=Modifier.heightIn(min=48.dp)){Text(text,color=Muted)}}
@Composable internal fun TapIcon(icon:ImageVector,label:String,click:()->Unit){IconButton(click,modifier=Modifier.size(48.dp)){Icon(icon,label,tint=Color.White)}}
@Composable internal fun PageTitle(title:String,back:(()->Unit)?=LocalPageBack.current,action:(@Composable ()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)TapIcon(Icons.Outlined.ArrowBack,"返回",back);Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));action?.invoke()}}
@Composable internal fun Input(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false,lines:Int=1,trackChange:Boolean=true,maxLength:Int?=null){val dirty=LocalFormDirty.current;val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current;Column(verticalArrangement=Arrangement.spacedBy(8.dp)){if(label.isNotBlank())Text(label,fontSize=14.sp,color=Muted);OutlinedTextField(value,{if(maxLength==null||it.length<=maxLength){if(trackChange)dirty();onChange(it)}},singleLine=lines==1,minLines=lines,shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Panel,focusedContainerColor=Panel,unfocusedBorderColor=Outline,focusedBorderColor=Accent),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text,imeAction=if(lines==1)ImeAction.Done else ImeAction.Default),keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}),modifier=Modifier.fillMaxWidth())}}
@Composable internal fun Group(title:String,content:@Composable ColumnScope.()->Unit){Surface(color=Panel,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Outline),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){if(title.isNotBlank())Text(title,fontSize=17.sp,fontWeight=FontWeight.Bold);content()}}}
@Composable internal fun DesignRow(title:String,subtitle:String?=null,icon:ImageVector?=null,click:(()->Unit)?=null,leading:(@Composable ()->Unit)?=null,trailing:(@Composable ()->Unit)?=null,modifier:Modifier=Modifier){Row(modifier.fillMaxWidth().then(if(click!=null)Modifier.clickable(onClick=click) else Modifier).heightIn(min=56.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){if(leading!=null)leading() else if(icon!=null)Icon(icon,null,Modifier.size(26.dp),tint=Muted);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,fontSize=16.sp,fontWeight=FontWeight.Medium);if(!subtitle.isNullOrBlank())Text(subtitle,color=Muted,fontSize=13.sp)};if(trailing!=null)trailing() else if(click!=null)Icon(Icons.Outlined.ChevronRight,null,tint=Muted)}}
@Composable internal fun TrainingCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Surface(color=Panel,shape=RoundedCornerShape(16.dp),modifier=modifier.fillMaxWidth()){
        Column(Modifier.padding(horizontal=12.dp),content=content)
    }
}
@Composable internal fun TrainingCardRow(title:String,exercise:Exercise?,subtitle:String?=null,click:(()->Unit)?=null,trailing:(@Composable ()->Unit)?=null){
    DesignRow(title,subtitle,click=click,leading={if(exercise!=null)ExerciseThumbnail(exercise,Modifier.size(64.dp,56.dp))else Spacer(Modifier.size(64.dp,56.dp))},trailing=trailing)
}
@Composable internal fun Art(path:String,modifier:Modifier,description:String?=null,scale:ContentScale=ContentScale.Fit){val context=LocalContext.current;val bmp=remember(path){runCatching {context.assets.open(path).use {android.graphics.BitmapFactory.decodeStream(it,null,android.graphics.BitmapFactory.Options().apply {inSampleSize=2})}}.getOrNull()};if(bmp!=null)Image(bmp.asImageBitmap(),description,modifier,contentScale=scale)}
@Composable internal fun CalendarGrid(value:LocalDate,highlights:Set<LocalDate> = emptySet(),onPick:(LocalDate)->Unit){var month by remember(value){mutableStateOf(value.withDayOfMonth(1))};Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TapIcon(Icons.Outlined.ChevronLeft,"上月"){month=month.minusMonths(1)};Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月")),Modifier.weight(1f),fontWeight=FontWeight.Bold);TapIcon(Icons.Outlined.ChevronRight,"下月"){month=month.plusMonths(1)}};Row{ "一二三四五六日".forEach {Text(it.toString(),Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=Muted,fontSize=12.sp)}};val first=month.minusDays((month.dayOfWeek.value-1).toLong());repeat(if(month.lengthOfMonth()+month.dayOfWeek.value-1>35)6 else 5){r->Row{repeat(7){c->val d=first.plusDays((r*7+c).toLong());Box(Modifier.weight(1f).height(48.dp).padding(2.dp).clip(CircleShape).background(if(d==value||d in highlights)Accent else Color.Transparent).clickable {onPick(d)},contentAlignment=Alignment.Center){Text(d.dayOfMonth.toString(),fontSize=14.sp,color=if(d.month==month.month)Color.White else Color.DarkGray)}}}}}}
@Composable internal fun Editor(title:String,busy:Boolean,onClose:()->Unit,onSave:()->Unit,saveLabel:String="保存",spacing:Dp=16.dp,cancelLabel:String?="取消",content:@Composable ColumnScope.()->Unit){var error by remember {mutableStateOf<String?>(null)};var dirty by remember {mutableStateOf(false)};var discard by remember {mutableStateOf(false)};val leave:()->Unit={if(dirty)discard=true else onClose()};Dialog(onDismissRequest={if(!busy)leave()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){Surface(color=Ink,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()){Column(Modifier.padding(horizontal=20.dp)){PageTitle(title,leave);Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(spacing)){CompositionLocalProvider(LocalFormDirty provides {dirty=true}){content()};error?.let {Text(it,color=Accent)}};Row(Modifier.padding(vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){if(cancelLabel!=null)OutlinedButton(leave,enabled=!busy,shape=CircleShape,modifier=Modifier.weight(.85f).height(52.dp)){Text(cancelLabel,color=Muted)};Button({error=null;runCatching(onSave).onFailure {error=it.message?:"请检查输入"}},enabled=!busy,modifier=Modifier.weight(if(cancelLabel==null)1f else 1.15f).height(52.dp)){Text(if(busy)"保存中…" else saveLabel,fontWeight=FontWeight.Bold)}}}}};if(discard)ConfirmModal("放弃本次修改？","继续编辑","放弃修改",{discard=false},{discard=false;onClose()})}
@Composable internal fun ConfirmModal(title:String,keep:String,accept:String,onKeep:()->Unit,onAccept:()->Unit,context:String?=null){val deleting=title.startsWith("删除");Dialog(onDismissRequest=onKeep){Surface(color=Panel,shape=RoundedCornerShape(24.dp)){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)){if(deleting)Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(36.dp),tint=Accent);Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold);if(!context.isNullOrBlank())Note(context);if(deleting){Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(onKeep,modifier=Modifier.weight(1f).height(48.dp),shape=RoundedCornerShape(14.dp)){Text(keep,color=Muted)};Button(onAccept,modifier=Modifier.weight(1f).height(48.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent,contentColor=Color.White)){Text(accept,fontWeight=FontWeight.Bold)}}}else{Action(keep,onClick=onKeep);TextButton(onAccept){Text(accept,color=Accent)}}}}}}
@Composable internal fun FailureModal(onReturn:()->Unit,onRetry:(()->Unit)?){Dialog(onDismissRequest=onReturn){Surface(color=Panel,shape=RoundedCornerShape(24.dp)){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){Icon(Icons.Outlined.ErrorOutline,null,Modifier.size(42.dp),tint=Accent);Text("未能保存",fontSize=24.sp,fontWeight=FontWeight.Bold);Note("输入已保留");Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(onReturn,modifier=Modifier.weight(1f).height(50.dp),shape=RoundedCornerShape(14.dp)){Text("返回编辑",color=Color.White)};if(onRetry!=null)Button(onRetry,modifier=Modifier.weight(1f).height(50.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent,contentColor=Color.White)){Text("重试",fontWeight=FontWeight.Bold)}}}}}}
internal fun weightText(kg:Double?,unit:String):String=kg?.let {"%.1f".format(java.util.Locale.ROOT,if(unit=="lb")it*2.2046226218 else it).removeSuffix(".0")}?:""

@Composable internal fun CompactInput(label:String,value:String,change:(String)->Unit,numeric:Boolean=true,maxLength:Int?=null){val dirty=LocalFormDirty.current;Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text(label,color=Muted,fontSize=11.sp);BasicTextField(value,{if(maxLength==null||it.length<=maxLength){dirty();change(it)}},singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=14.sp),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).border(1.dp,Outline,RoundedCornerShape(10.dp)).background(Ink,RoundedCornerShape(10.dp)).padding(10.dp))}}
@Composable internal fun InlineFieldRow(label:String,click:(()->Unit)?=null,modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    Row(modifier.fillMaxWidth().heightIn(min=56.dp).then(if(click!=null)Modifier.clickable(onClick=click)else Modifier),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(label,Modifier.weight(1f),fontSize=14.sp)
        Box(Modifier.width(148.dp).heightIn(min=48.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp,Outline,RoundedCornerShape(12.dp)),contentAlignment=Alignment.CenterStart,content=content)
    }
}
@Composable internal fun InlineInput(label:String,value:String,change:(String)->Unit,numeric:Boolean=true){
    val dirty=LocalFormDirty.current
    InlineFieldRow(label){BasicTextField(value,{dirty();change(it)},singleLine=true,textStyle=TextStyle(color=Color.White,fontSize=16.sp,lineHeight=20.sp),cursorBrush=SolidColor(Accent),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text),modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp))}
}
@Composable internal fun NeutralTabs(values:List<String>,selected:String,change:(String)->Unit){Row(Modifier.fillMaxWidth().border(1.dp,Outline,CircleShape).background(Panel,CircleShape).padding(3.dp)){values.forEach {v->Box(Modifier.weight(1f).height(42.dp).clip(CircleShape).background(if(v==selected)Color(0xFF303030)else Color.Transparent).clickable {change(v)},contentAlignment=Alignment.Center){Text(v,color=if(v==selected)Color.White else Muted,fontSize=13.sp,fontWeight=if(v==selected)FontWeight.Bold else FontWeight.Normal)}}}}

@Composable internal fun Choices(values:List<String>,selected:String,onSelect:(String)->Unit){val dirty=LocalFormDirty.current;Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp,Outline,RoundedCornerShape(16.dp)).padding(3.dp)){values.forEach {v->Box(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(13.dp)).background(if(v==selected)Accent.copy(alpha=.22f) else Color.Transparent).then(if(v==selected)Modifier.border(1.dp,Accent,RoundedCornerShape(13.dp)) else Modifier).clickable {dirty();onSelect(v)}.padding(4.dp),contentAlignment=Alignment.Center){Text(v,color=if(v==selected)Color.White else Muted,fontSize=13.sp,fontWeight=if(v==selected)FontWeight.Bold else FontWeight.Normal)}}}}
@Composable internal fun InlineSwitch(label:String,value:Boolean,change:(Boolean)->Unit){
    val dirty=LocalFormDirty.current
    InlineFieldRow(label,modifier=Modifier.toggleable(value,role=Role.Switch,onValueChange={dirty();change(it)})){
        Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center){
            Switch(value,null,colors=SwitchDefaults.colors(checkedTrackColor=Accent,checkedThumbColor=Color.White,uncheckedTrackColor=Outline,uncheckedThumbColor=Color(0xFFD0D0D0),uncheckedBorderColor=Color.Transparent))
        }
    }
}
internal fun loadValue(s:String,unit:String):Double{val n=s.toDoubleOrNull();require(n!=null && n.isFinite() && n in 0.0..5000.0){"请检查数值"};return if(unit=="lb")n/2.2046226218 else n}
