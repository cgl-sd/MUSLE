package com.musclejournal.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.musclejournal.app.domain.*
import com.musclejournal.app.rendering.BodyView
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun ExerciseSearch(value:String,onChange:(String)->Unit){
    val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Outline),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.heightIn(min=48.dp).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(Icons.Outlined.Search,null,Modifier.size(20.dp),tint=Muted)
            BasicTextField(value,onChange,singleLine=true,textStyle=TextStyle(color=Color.White,fontSize=14.sp),cursorBrush=SolidColor(Accent),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}),modifier=Modifier.weight(1f),decorationBox={field->Box{if(value.isEmpty())Text("搜索动作",color=Muted,fontSize=14.sp);field()}})
        }
    }
}
internal enum class HistoryPeriod(val label:String) {
    DAY("日"), WEEK("周"), MONTH("月");
    fun bounds(date:LocalDate):ClosedRange<LocalDate> = when(this){
        DAY->date..date
        WEEK->date.with(DayOfWeek.MONDAY).let {it..it.plusDays(6)}
        MONTH->date.withDayOfMonth(1)..date.withDayOfMonth(date.lengthOfMonth())
    }
    fun shift(date:LocalDate,amount:Long):LocalDate = when(this){
        DAY->date.plusDays(amount);WEEK->date.plusWeeks(amount);MONTH->date.plusMonths(amount)
    }
}
@Composable internal fun HistoryScreen(j:Journal,open:(String)->Unit,backfill:(LocalDate)->Unit){
    var date by rememberSaveable(stateSaver=LocalDateSaver){mutableStateOf(LocalDate.now())}
    var periodName by rememberSaveable {mutableStateOf(HistoryPeriod.DAY.name)}
    var choosingDate by remember {mutableStateOf(false)}
    val period=HistoryPeriod.valueOf(periodName);val bounds=period.bounds(date)
    val records=TrainingHistory.records(j,bounds.start,bounds.endInclusive)
    val bySession=records.groupBy {it.sessionId}
    val visible=j.sessions.filter {it.id in bySession}.sortedWith(compareByDescending<Session>{bySession.getValue(it.id).first().date}.thenByDescending {bySession.getValue(it.id).first().completedAt})
    val label=when(period){
        HistoryPeriod.DAY->date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        HistoryPeriod.WEEK->bounds.start.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))+" — "+bounds.endInclusive.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        HistoryPeriod.MONTH->date.format(DateTimeFormatter.ofPattern("yyyy年M月"))
    }
    val scroll=rememberScrollState()
    LaunchedEffect(date,periodName){scroll.scrollTo(0)}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        PageTitle("训练记录",action={TapIcon(Icons.Outlined.Add,"补录训练"){backfill(date)}})
        NeutralTabs(HistoryPeriod.entries.map {it.label},period.label){value->periodName=HistoryPeriod.entries.first {it.label==value}.name;choosingDate=false}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            TapIcon(Icons.Outlined.ChevronLeft,"上一${period.label}"){date=period.shift(date,-1)}
            Box(Modifier.weight(1f).heightIn(min=48.dp).clickable {choosingDate=!choosingDate},contentAlignment=Alignment.Center){Text(label,color=Muted,fontSize=if(period==HistoryPeriod.WEEK)12.sp else 15.sp)}
            TapIcon(Icons.Outlined.ChevronRight,"下一${period.label}"){date=period.shift(date,1)}
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(choosingDate)Group(""){CalendarGrid(date,j.sessions.map {it.date()}.toSet()){date=it;choosingDate=false};TextButton({date=LocalDate.now();choosingDate=false}){Text("今天",color=Accent)}}
            TrainingHistoryChart(records,period,bounds.start,bounds.endInclusive)
            visible.forEach {s->
                val actions=bySession.getValue(s.id)
                Text(actions.first().date.format(DateTimeFormatter.ofPattern("M月d日  E",java.util.Locale.CHINA)),color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=18.dp))
                TrainingCard{TrainingCardRow(s.title,j.exercise(actions.first().exerciseId),"${actions.size} 个动作",click={open(s.id)})}
            }
        }
    }
}
@Composable internal fun SessionDetail(j:Journal,s:Session,trend:(String)->Unit,repeat:()->Unit,deleteSession:()->Unit,note:()->Unit){
    var menu by remember {mutableStateOf(false)};val done=ExerciseProgress.completed(j,s).map {it.exerciseId}.toSet()
    PageTitle(s.title,action={Box{TapIcon(Icons.Outlined.MoreHoriz,"记录菜单"){menu=true};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("编辑备注")},{menu=false;note()});DropdownMenuItem({Text("删除训练",color=Accent)},{menu=false;deleteSession()})}}})
    Note("${s.date()} · ${s.status.label}")
    if(s.note.isNotBlank())Note(s.note)
    (s.exerciseIds+done).distinct().forEach {id->TrainingCard{TrainingCardRow(j.exercise(id).name,j.exercise(id),if(id in done)"已完成"else "未完成",click={trend(id)})}}
    Action("再次训练",onClick=repeat)
}
@Composable internal fun TrendScreen(j:Journal,id:String?){
    val e=j.exercises().firstOrNull {it.id==id}?:return;PageTitle(e.name)
    j.sessions.filter {s->ExerciseProgress.completed(j,s).any {it.exerciseId==e.id}}.sortedWith(compareByDescending<Session>{it.date()}.thenByDescending {it.startedAt}).forEach {s->TrainingCard{TrainingCardRow(s.date().toString(),e,"已完成")}}
}
@Composable internal fun CoursesScreen(j:Journal,busy:Boolean,start:(Course)->Unit,edit:(Course?)->Unit,copy:(Course)->Unit,archive:(Course)->Unit,schedule:(Course)->Unit,startOccurrence:(Occurrence)->Unit,status:(Occurrence,OccurrenceStatus)->Unit,editOccurrence:(Occurrence)->Unit,arrange:(LocalDate)->Unit,focusDate:String?=null,onFocusConsumed:()->Unit={}){
    var tab by rememberSaveable {mutableStateOf("训练安排")}
    var day by rememberSaveable(stateSaver=LocalDateSaver){mutableStateOf(LocalDate.now())}
    var expanded by rememberSaveable {mutableStateOf<String?>(null)}
    val scheduleScroll=rememberScrollState()
    val coursesScroll=rememberScrollState()
    LaunchedEffect(focusDate){
        focusDate?.let {
            day=LocalDate.parse(it)
            tab="训练安排"
            expanded=null
            onFocusConsumed()
        }
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
    PageTitle("课程与安排")
    NeutralTabs(listOf("训练安排","我的课程"),tab){tab=it}
    Box(Modifier.weight(1f).fillMaxWidth()){
    Column(Modifier.fillMaxSize().verticalScroll(if(tab=="训练安排")scheduleScroll else coursesScroll).padding(bottom=84.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    if(tab=="训练安排"){
        val monday=day.with(DayOfWeek.MONDAY)
        val scheduled=j.occurrences.filter {it.status==OccurrenceStatus.PLANNED}
            .mapNotNull {o->(o.override?:j.courses.firstOrNull {it.id==o.courseId})?.takeIf {it.exercises.isNotEmpty()}?.let {o to it}}
        val scheduledDates=scheduled.map {it.first.date}.toSet()
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp).pointerInput(day){detectHorizontalDragGestures {_,amount->if(kotlin.math.abs(amount)>16)day=if(amount>0)day.minusWeeks(1)else day.plusWeeks(1)}},horizontalArrangement=Arrangement.spacedBy(5.dp)){
            repeat(7){n->
                val d=monday.plusDays(n.toLong())
                val restDay=j.occurrences.any {it.date==d.toString() && it.status==OccurrenceStatus.REST}
                val hasSchedule=d.toString() in scheduledDates
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(d==day && !restDay)Accent else if(d==day)Color(0xFF303030) else Panel).clickable {day=d}.semantics {selected=d==day;stateDescription=if(hasSchedule)"有训练安排"else "无训练安排"}){
                    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("周"+"一二三四五六日"[n],fontSize=11.sp,color=if(restDay)Muted else Color.White)
                        Text(d.dayOfMonth.toString(),fontWeight=FontWeight.Bold,color=if(restDay)Muted else Color.White)
                    }
                    if(hasSchedule)Box(Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp).size(4.dp).background(if(d==day)Color.White else Accent,CircleShape))
                }
            }
        }
        scheduled.filter {it.first.date==day.toString()}.forEach {(o,c)->key(o.id){
            val session=j.sessions.firstOrNull {it.occurrenceId==o.id}
            SwipeScheduleRow(o.id,session==null&&!busy,{editOccurrence(o)},{status(o,OccurrenceStatus.CANCELLED)}){
                c.exercises.forEach {ce->
                    val exercise=j.exercise(ce.exerciseId)
                    TrainingCardRow(exercise.name,exercise,trailing={Text(session?.status?.label?:"已安排",color=if(session==null)Muted else Accent,fontSize=12.sp)})
                }
            }
        }}
    }else{
        Text("预设课程",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=4.dp))
        CoursePresets.all.forEach {preset->
            val c=preset.course()
            CourseListCard(j,c,expanded==c.id,false,{expanded=if(expanded==c.id)null else c.id},{edit(c)},{copy(c)})
        }
        val ownCourses=j.courses
        if(ownCourses.isNotEmpty())Text("已保存课程",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=12.dp))
        ownCourses.forEach {c->
            CourseListCard(j,c,expanded==c.id,c.archived,{expanded=if(expanded==c.id)null else c.id},{schedule(c)},{copy(c)},{archive(c)},{start(c)})
        }
    }
    }
    FloatingActionButton(onClick={if(!busy){if(tab=="训练安排")arrange(day)else edit(null)}},modifier=Modifier.align(Alignment.BottomEnd).padding(bottom=16.dp),containerColor=Accent,contentColor=Color.White,shape=CircleShape){Icon(Icons.Outlined.Add,if(tab=="训练安排")"添加训练"else "创建课程")}
    }
    }
}
@Composable private fun CourseListCard(j:Journal,c:Course,expanded:Boolean,archived:Boolean,toggle:()->Unit,arrange:()->Unit,copy:()->Unit,archive:(()->Unit)?=null,start:(()->Unit)?=null){
    TrainingCard{
        TrainingCardRow(
            c.name,
            c.exercises.firstOrNull()?.let {j.exercise(it.exerciseId)},
            "${c.exercises.size} 个动作${if(archived)" · 已归档"else ""}",
            click=toggle,
            trailing={Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,tint=Muted)},
        )
        if(expanded){
            HorizontalDivider(color=Outline)
            c.exercises.forEach {ce->TrainingCardRow(j.exercise(ce.exerciseId).name,j.exercise(ce.exerciseId))}
            HorizontalDivider(color=Outline)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                TextButton(arrange){Text(if(archived)"安排" else "修改并安排",color=Muted)}
                TextButton(copy){Text("复制",color=Muted)}
                archive?.let {TextButton(it){Text(if(archived)"取消归档"else "归档",color=Muted)}}
            }
            if(!archived&&start!=null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){TextButton(start){Text("开始课程",color=Muted)}}
        }
    }
}
@Composable private fun SwipeScheduleRow(id:String,enabled:Boolean,edit:()->Unit,cancel:()->Unit,content:@Composable ColumnScope.()->Unit){
    var offset by remember(id){mutableFloatStateOf(0f)}
    val density=androidx.compose.ui.platform.LocalDensity.current
    val width=with(density){152.dp.toPx()}
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))){
        TrainingCard(modifier=Modifier.graphicsLayer {translationX=offset}
            .pointerInput(enabled,width){if(enabled)detectHorizontalDragGestures(onDragEnd={offset=if(offset < -width/3)-width else 0f},onDragCancel={offset=0f}){event,amount->event.consume();offset=(offset+amount).coerceIn(-width,0f)}}){
            content()
        }
        if(offset <= -width/3)Row(Modifier.align(Alignment.CenterEnd).width(152.dp).height(64.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=edit,enabled=enabled,modifier=Modifier.weight(1f).fillMaxHeight()){Text("修改",color=Color.White)}
            TextButton(onClick=cancel,enabled=enabled,modifier=Modifier.weight(1f).fillMaxHeight()){Text("取消",color=Accent)}
        }
    }
}
@Composable internal fun ScheduleEditor(j:Journal,courseId:String?,busy:Boolean,close:()->Unit,save:(String?,LocalDate,Set<Int>,LocalDate)->Unit){var selected by remember {mutableStateOf(courseId)};var date by remember {mutableStateOf(LocalDate.now())};var end by remember {mutableStateOf(date.plusWeeks(2))};var weekly by remember {mutableStateOf(false)};var days by remember {mutableStateOf(setOf(date.dayOfWeek.value))};var choosing by remember {mutableStateOf(false)}
    Editor(if(courseId==null)"安排休息" else "安排课程",busy,close,{save(selected,date,if(weekly)days else emptySet(),if(weekly)end else date)},"保存安排"){
        if(courseId!=null){DesignRow(j.courses.firstOrNull {it.id==selected}?.name?:"选择课程",icon=Icons.Outlined.FitnessCenter,click={choosing=!choosing});if(choosing)j.courses.filter {!it.archived}.forEach {c->DesignRow(c.name,click={selected=c.id;choosing=false})}}
        DateField("开始日期",date){date=it};Toggle("每周重复",weekly){weekly=it};if(weekly){Weekdays(days){days=it};DateField("结束日期",end){end=it}}
        val count=if(weekly && end>=date)generateSequence(date){it.plusDays(1)}.takeWhile {it<=end && it<=date.plusYears(1)}.count {it.dayOfWeek.value in days} else 1
        DesignRow("安排日期",trailing={Text("$count 个日期",color=Muted)});val dates=if(weekly&&end>=date)generateSequence(date){it.plusDays(1)}.takeWhile {it<=end&&it<=date.plusYears(1)}.filter {it.dayOfWeek.value in days}.toSet()else emptySet();CalendarGrid(date,dates){date=it}
    }
}
@Composable internal fun DateField(label:String,value:LocalDate,change:(LocalDate)->Unit){var show by remember {mutableStateOf(false)};val dirty=LocalFormDirty.current;Column{Text(label,color=Muted,fontSize=14.sp);Surface(color=Panel,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Outline)){Box(Modifier.padding(horizontal=12.dp)){DesignRow(value.toString(),icon=Icons.Outlined.CalendarMonth,click={show=!show})}};if(show)CalendarGrid(value){dirty();change(it);show=false}}}
@Composable internal fun OccurrenceEditor(j:Journal,o:Occurrence,busy:Boolean,close:()->Unit,save:(Occurrence)->Unit){var date by remember {mutableStateOf(LocalDate.parse(o.date))};var course by remember {mutableStateOf(o.override?:j.courses.firstOrNull {it.id==o.courseId})};var query by rememberSaveable {mutableStateOf("")};Editor("修改本次安排",busy,close,{save(o.copy(date=date.toString(),override=course,modifiedAt=System.currentTimeMillis()))},"保存本次"){DateField("训练日期",date){date=it};Text("选择课程");Input("搜索课程",query,{query=it},trackChange=false);j.courses.filter {!it.archived && it.name.contains(query)}.forEach {c->DesignRow(c.name,click={course=c},trailing={if(c.id==course?.id)Icon(Icons.Outlined.CheckCircle,null,tint=Accent)})};course?.exercises?.forEach {ce->DesignRow(j.exercise(ce.exerciseId).name,leading={ExerciseThumbnail(j.exercise(ce.exerciseId),Modifier.size(70.dp,54.dp))},trailing={TapIcon(Icons.Outlined.RemoveCircleOutline,"移除此动作"){course=course?.copy(exercises=course!!.exercises.filterNot {it.exerciseId==ce.exerciseId})}})}}}
@Composable internal fun SettingsEditor(s:Settings,busy:Boolean,close:()->Unit,save:(Settings)->Unit){
    var motion by remember {mutableStateOf(s.motion)}
    Editor("显示设置",busy,close,{save(s.copy(motion=motion))},"保存设置"){Toggle("动画效果",motion){motion=it}}
}
