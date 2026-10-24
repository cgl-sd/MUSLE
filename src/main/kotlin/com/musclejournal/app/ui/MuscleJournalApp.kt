package com.musclejournal.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import com.musclejournal.app.BuildConfig
import com.musclejournal.app.data.*
import com.musclejournal.app.domain.*
import com.musclejournal.app.rendering.*
import kotlinx.coroutines.delay
import java.time.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun MuscleJournalApp() {
    val context=LocalContext.current
    val preferences=remember {context.getSharedPreferences("journal_mode",0)}
    var testMode by rememberSaveable {mutableStateOf(BuildConfig.TEST_TOOLS && preferences.getBoolean("test_mode",false))}
    var welcomeSeen by remember {mutableStateOf(preferences.getBoolean("welcome_seen",false))}
    val repo=remember(testMode){JournalRepository(context,if(testMode)"journal-test.db" else "journal.db")}
    var revision by remember(repo){mutableIntStateOf(0)}
    DisposableEffect(repo){val remove=repo.observe {revision++};onDispose {remove();repo.close()}}
    val j=revision.let {repo.state};val busy=repo.busy
    var page by rememberSaveable {mutableStateOf("body")};var detailId by rememberSaveable {mutableStateOf<String?>(null)}
    var annotations by rememberSaveable {mutableStateOf(false)}
    var bodyView by rememberSaveable {mutableStateOf(BodyView.FRONT.name)}
    var selectedName by rememberSaveable {mutableStateOf<String?>(null)};val muscle=selectedName?.let {MuscleId.valueOf(it)}
    var side by rememberSaveable {mutableStateOf("双侧")};var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var range by rememberSaveable {mutableStateOf("今天")}
    var form by remember {mutableStateOf<String?>(null)};var editId by remember {mutableStateOf<String?>(null)};var exerciseId by remember {mutableStateOf<String?>(null)}
    var backfillDay by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var courseFocusDate by rememberSaveable {mutableStateOf<String?>(null)}
    var draftData by rememberSaveable {mutableStateOf<String?>(null)}
    var draftSides by rememberSaveable {mutableStateOf(emptyMap<String,String>())}
    var scheduleDraft by rememberSaveable {mutableStateOf<String?>(null)}
    var scheduleDay by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var scheduleEditing by rememberSaveable {mutableStateOf<String?>(null)}
    fun prepareSchedule(day:LocalDate,occurrence:Occurrence?=null){
        scheduleDay=day.toString();scheduleEditing=occurrence?.id
        val source=occurrence?.let {it.override?:j.courses.firstOrNull {c->c.id==it.courseId}}
        scheduleDraft=JournalCodec.encode(Journal(customExercises=j.customExercises,courses=listOf(source?:Course(name="训练安排",exercises=emptyList()))))
        page="scheduleActions"
    }
    var draftDate by rememberSaveable {mutableStateOf<String?>(null)}
    var planReturnPage by rememberSaveable {mutableStateOf("body")}
    var pickerReturnPage by rememberSaveable {mutableStateOf("body")}
    var confirm by remember {mutableStateOf<Pair<String,()->Unit>?>(null)}
    var message by remember {mutableStateOf<String?>(null)}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    var previewMemory by remember {mutableStateOf<Achievement?>(null)}
    var onboardingPreview by remember {mutableStateOf(false)}
    // Test navigation never opens a production database or writes sample records.
    LaunchedEffect(repo.ready){if(repo.ready && BuildConfig.TEST_TOOLS && testMode){
        val intent=(context as? android.app.Activity)?.intent
        val target=intent?.getStringExtra("ui_page")
        if(target in setOf("body","muscles","courses","history","me","memories","settings","about","picker","active","detail","trend"))page=target!!
        detailId=intent?.getStringExtra("ui_record")?:j.sessions.firstOrNull()?.id
        editId=intent?.getStringExtra("ui_edit")
        exerciseId=intent?.getStringExtra("ui_exercise")?:"dumbbell_bench_press"
        val editor=intent?.getStringExtra("ui_form")
        if(target=="picker")detailId=null
        if(editor in setOf("date","profile","course","muscles","schedule","occurrence","backfill","sessionNote","memoryNote"))form=editor
        if(intent?.getBooleanExtra("ui_memory",false)==true)previewMemory=j.achievements.firstOrNull()
        if(intent?.getBooleanExtra("ui_onboarding",false)==true)onboardingPreview=true
    }}
    var previousToday by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    LaunchedEffect(LocalDate.now().toString()){val today=LocalDate.now().toString();if(date==previousToday)date=today;previousToday=today}
    LaunchedEffect(Unit){while(true){delay(60_000);now=System.currentTimeMillis()}}
    var retrySave by remember {mutableStateOf<(() -> Unit)?>(null)}
    LaunchedEffect(form){retrySave=null}
    fun commit(action:(Journal)->Journal){val task={repo.change(action=action)};retrySave=task;task()}
    fun save(action:(Journal)->Journal){val task={repo.change(after={form=null},action=action)};retrySave=task;task()}
    var recordReturnPage by rememberSaveable {mutableStateOf("history")}
    fun openRecord(id:String){recordReturnPage=if(page=="body")"body"else "history";detailId=id;page="detail"}
    fun storePlan(course:Course){draftData=JournalCodec.encode(Journal(customExercises=j.customExercises,courses=listOf(course)));draftDate=LocalDate.now().toString()}
    val currentDraft=remember(draftData){draftData?.let {JournalCodec.decode(it).courses.firstOrNull()}}
    val currentSchedulePlan=remember(scheduleDraft){scheduleDraft?.let {JournalCodec.decode(it).courses.firstOrNull()}}
    fun prepareTraining(origin:String){
        planReturnPage=origin;pickerReturnPage=origin;detailId=null;selectedName=null
        if(draftData==null || draftDate!=LocalDate.now().toString()){
            val active=j.active()
            draftSides=active?.completedActions.orEmpty().mapNotNull {action->action.side?.let {action.exerciseId to it}}.toMap()
            storePlan(if(active!=null)Course(name=active.title,exercises=active.exerciseIds.map {CourseExercise(it)})else TodayTrainingPlan.scheduled(j,LocalDate.now())?:Course(name="今日训练",exercises=emptyList()))
        }
        page="record"
    }
    fun start(course:Course?=null,occurrence:String?=null){
        if(course!=null){storePlan(course);draftSides=emptyMap()}
        prepareTraining(page)
    }
    var exportAchievement by remember {mutableStateOf<Achievement?>(null)}
    val memoryLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->if(uri!=null){val a=exportAchievement;if(a!=null)runCatching {context.contentResolver.openOutputStream(uri)?.use {Commemoration.write(a,j.goals.firstOrNull {g->g.id==a.goalId && g.version==a.goalVersion},it,context.assets,j.goals.firstOrNull {g->g.id==a.goalId && g.version==a.goalVersion}?.let {g->Metrics.goalProgress(j,g)})}?:error("无法写入纪念图")}.onSuccess {message="纪念图已保存"}.onFailure {message="未能保存图片"}}}
    val pageState=rememberSaveableStateHolder()
    val pageScroll=remember(page,detailId){androidx.compose.foundation.ScrollState(0)}
    BackHandler(enabled=page!="body" && form==null && selectedName==null){page=when(page){"scheduleActions"->"courses";"record"->planReturnPage;"detail"->recordReturnPage;"picker"->pickerReturnPage;"trainingPlan"->planReturnPage;"trainingReview"->"active";"trend"->"history";"about"->"settings";"memories","settings"->"me";else->"body"}}
    BackHandler(enabled=page=="body" && annotations && selectedName==null && form==null){annotations=false}
    BackHandler(enabled=selectedName!=null && form==null){selectedName=null}
    if(repo.ready && !j.profile.onboardingDone && !welcomeSeen)Onboarding({welcomeSeen=true;preferences.edit().putBoolean("welcome_seen",true).apply()}){profile->
        repo.change(after={welcomeSeen=true;preferences.edit().putBoolean("welcome_seen",true).apply()}){it.copy(profile=profile)}
    }
    if(onboardingPreview)Onboarding({onboardingPreview=false}){_->onboardingPreview=false}
    // Motion off removes the tap ripple and the celebration animation; the content itself is unchanged.
    CompositionLocalProvider(LocalRippleConfiguration provides if(j.settings.motion) LocalRippleConfiguration.current else null){
    Scaffold(containerColor=Ink,bottomBar={if(form==null && page in setOf("body","courses","history","me"))Column {
        val dockPage=when(page){"body"->"首页";"courses"->"课程";"history"->"记录";else->"个人"}
        val training=j.active()!=null || currentDraft?.exercises?.isNotEmpty()==true
        if(page=="body")TrainingPill(training,repo.ready&&!busy,{prepareTraining("body")})
        BottomNav(dockPage,{selectedName=null;page="body"},{selectedName=null;page="courses"},{selectedName=null;page="history"},{page="me"})
    }}){padding->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)){
        val homeHeroHeight=(maxHeight*0.77f-68.dp).coerceAtLeast(200.dp)
        Column(Modifier.fillMaxSize()){
            if(!repo.ready || busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Accent)
            // The body screen keeps a fixed toolbar, a fixed figure area and its own scrollable info section (§4.1).
            if(page=="body")BodyOverview(j,now,bodyView,homeHeroHeight,muscle,side,annotations,BuildConfig.TEST_TOOLS && testMode && preferences.getBoolean("model_failure",false),{v->bodyView=v;if(BodyAnnotations.labels(BodyView.valueOf(v)).none {it.muscle==muscle})selectedName=null},{m,s->annotations=true;selectedName=m.name;side=s},{annotations=!annotations;if(!annotations)selectedName=null},{selectedName=null},{openRecord(it)})
            else Column(Modifier.weight(1f).then(if(page !in listOf("scheduleActions","record","picker","active","trainingPlan","trainingReview","courses","history"))Modifier.verticalScroll(pageScroll)else Modifier).padding(horizontal=if(page in listOf("scheduleActions","record","picker","active","trainingPlan","trainingReview"))16.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(if(page=="me")0.dp else 8.dp)){
                CompositionLocalProvider(LocalPageBack provides if(page in listOf("courses","history","me"))null else ({page=when(page){"scheduleActions"->"courses";"record"->planReturnPage;"detail"->recordReturnPage;"picker"->pickerReturnPage;"trainingPlan"->planReturnPage;"trainingReview"->"active";"trend"->"history";"about"->"settings";"memories","settings"->"me";else->"body"}})){
                pageState.SaveableStateProvider(page){
                when(page){
                    "scheduleActions"->{
                        val plan=currentSchedulePlan?:Course(name="训练安排",exercises=emptyList())
                        val saveKey=remember(page,scheduleDraft){"schedule-save:${newId()}"}
                        TrainingRecordPage(j,plan.exercises.map {it.exerciseId},busy,{ids->
                            val previous=plan.exercises.associateBy {it.exerciseId}
                            scheduleDraft=JournalCodec.encode(Journal(customExercises=j.customExercises,courses=listOf(plan.copy(exercises=ids.map {previous[it]?:CourseExercise(it)}))))
                        },{repo.changeOnce(saveKey,after={scheduleDraft=null;scheduleEditing=null;page="courses";android.widget.Toast.makeText(context,"已添加训练",android.widget.Toast.LENGTH_SHORT).show()}){
                            TrainingSchedule.save(it,LocalDate.parse(scheduleDay),plan.exercises.map {it.exerciseId},scheduleEditing)
                        }},title="添加训练",saveLabel="确认添加",enforceSides=false)
                    }
                    "record","picker","active","trainingPlan","trainingReview"->{
                        val plan=currentDraft?:j.active()?.let {a->Course(name=a.title,exercises=a.exerciseIds.map {CourseExercise(it)})}?:TodayTrainingPlan.scheduled(j,LocalDate.now())?:Course(name="今日训练",exercises=emptyList())
                        val saveKey=remember(page,draftData){"record-save:${newId()}"}
                        TrainingRecordPage(j,plan.exercises.map {it.exerciseId},busy,{ids->
                            val byId=plan.exercises.associateBy {it.exerciseId}
                            storePlan(plan.copy(exercises=ids.map {byId[it]?:CourseExercise(it)}))
                            draftSides=draftSides.filterKeys {it in ids}
                        },
                            selectedSides=draftSides,
                            onSideChange={id,side->draftSides=if(side==null)draftSides-id else draftSides+(id to side)},
                            save={
                                repo.changeOnce(saveKey,after={draftData=null;draftDate=null;draftSides=emptyMap();page="body";android.widget.Toast.makeText(context,"训练记录已保存",android.widget.Toast.LENGTH_SHORT).show()}){
                                    TrainingRecord.saveSelections(it,plan.exercises.map {row->TrainingRecord.Selection(row.exerciseId,draftSides[row.exerciseId])})
                                }
                            },
                        )
                    }
                    "courses"->CoursesScreen(j,busy,{c->start(c)},{c->editId=c?.id;form="course"},{c->commit {it.copy(courses=it.courses+c.copy(id=newId(),name=c.name+" 副本",archived=false))}},{c->commit {it.copy(courses=it.courses.map {v->if(v.id==c.id)v.copy(archived=!v.archived) else v})}},{c->editId=c.id;form="course"},{o->val c=o.override?:j.courses.firstOrNull {it.id==o.courseId};if(c!=null)start(c,o.id)},{o,status->commit {if(status==OccurrenceStatus.CANCELLED)TrainingSchedule.cancel(it,o.id)else it.copy(occurrences=it.occurrences.map {v->if(v.id==o.id)v.copy(status=status) else v})}},{o->prepareSchedule(LocalDate.parse(o.date),o)},{day->prepareSchedule(day)},courseFocusDate,{courseFocusDate=null})
                    "history"->HistoryScreen(j,{openRecord(it)},{day->backfillDay=day.toString();form="backfill"})
                    "detail"->{val session=j.sessions.firstOrNull {it.id==detailId};if(session==null)Note("记录已不存在")else SessionDetail(j,session,{id->exerciseId=id;page="trend"},{start(session.snapshot?:Course(name=session.title,exercises=session.exerciseIds.map {CourseExercise(it)}))},{confirm="删除这场训练？" to {repo.change(after={page="history"}){Actions.deleteSession(it,session.id)}}},{form="sessionNote"})}
                    "trend"->TrendScreen(j,exerciseId)
                    "muscles"->MusclesPage(j,date,range){m->annotations=true;selectedName=m.name;side="双侧";bodyView=BodyRegions.viewFor(m).name;page="body"}
                    "me"->PersonalHub(j,{target->page=target},{form="profile"})
                    "settings"->SettingsPage {page=it}
                    "about"->AboutPage()
                    "memories"->MemoriesPage(j)
                }
                }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        }
    }
    val session=j.sessions.firstOrNull {it.id==detailId}
    when(form){
        "date"->{var picked by remember {mutableStateOf(LocalDate.parse(date))};Editor("选择日期",false,{form=null},{date=picked.toString();form=null},"查看日期"){CalendarGrid(picked){picked=it};Row{TextButton({picked=LocalDate.now()}){Text("今天")};TextButton({picked=picked.minusDays(1)}){Text("前一天")};TextButton({picked=picked.plusDays(1)}){Text("后一天")}}}}
        "profile"->ProfileEditor(j,busy,{form=null}){p->val task={repo.change(after={form=null}){it.copy(profile=p)}};retrySave=task;task()}
        "course"->{
            val preset=editId?.let {id->CoursePresets.all.firstOrNull {"preset:${it.id}"==id}?.course()}
            val initial=editId?.let {id->j.courses.firstOrNull {it.id==id}}?:preset
            CourseEditor(j,initial,startAsCopy=preset!=null,busy=busy,close={form=null}){c,date->
                val task={repo.change(after={form=null;courseFocusDate=date.toString();android.widget.Toast.makeText(context,"已安排到 ${date.monthValue}月${date.dayOfMonth}日",android.widget.Toast.LENGTH_SHORT).show()}){CoursePlanning.save(it,c,date)}}
                retrySave=task
                task()
            }
        }
        "muscles"->Editor("肌群列表",busy,{form=null},{form=null},"关闭"){var query by remember {mutableStateOf("")};Input("搜索肌群",query,{query=it},trackChange=false);MuscleId.entries.filter {it.displayName.contains(query)}.forEach {m->DesignRow(m.displayName,click={annotations=true;selectedName=m.name;side="双侧";bodyView=BodyRegions.viewFor(m).name;page="body";form=null},leading={AnatomyThumbnail(m,Modifier.size(76.dp,64.dp))});HorizontalDivider(color=Outline)}}
        "schedule"->ScheduleEditor(j,editId,busy,{form=null}){c,d,days,end->save {Actions.schedule(it,c,d,days,end)}}
        "occurrence"->j.occurrences.firstOrNull {it.id==editId}?.let {o->OccurrenceEditor(j,o,busy,{form=null}){updated->save {it.copy(occurrences=it.occurrences.map {v->if(v.id==o.id)updated else v})}}}
        "backfill"->BackfillEditor(j,LocalDate.parse(backfillDay),busy,{form=null}){s->val task={repo.change(after={form=null;page="history";android.widget.Toast.makeText(context,"训练记录已保存",android.widget.Toast.LENGTH_SHORT).show()}){it.copy(sessions=it.sessions+s)}};retrySave=task;task()}
        "sessionNote"->if(session!=null){var note by remember {mutableStateOf(session.note)};Editor("训练备注",busy,{form=null},{require(note.length<=MAX_SESSION_NOTE_LENGTH){"备注过长"};save {it.copy(sessions=it.sessions.map {s->if(s.id==session.id)s.copy(note=note) else s})}},"保存备注"){Note(session.title+" · "+session.date());Input("",note,{note=it},lines=14,maxLength=MAX_SESSION_NOTE_LENGTH);Text("${note.length}/${MAX_SESSION_NOTE_LENGTH}",color=Muted)}}
        "memoryNote"->j.achievements.firstOrNull {it.key==editId}?.let {memory->var note by remember {mutableStateOf(memory.note)};Editor("纪念备注",busy,{form=null},{save {it.copy(achievements=it.achievements.map {x->if(x.key==memory.key)x.copy(note=note) else x})}}){Input("备注",note,{note=it},lines=3)}}
    }
    previewMemory?.let {a->MemoryPreview(j,a,{previewMemory=null}){exportAchievement=a;memoryLauncher.launch("muscle-journal-memory-${a.awardedAt}.png")}}
    confirm?.let {(text,action)->ConfirmModal(text,"保留",if(text.startsWith("删除"))"删除"else "确认",{confirm=null},{action();confirm=null},if(text=="删除这场训练？")session?.let {"${it.title} · ${it.date()}"}else null)}
    message?.let {AlertDialog(onDismissRequest={message=null},containerColor=Panel,title={Text(it)},confirmButton={TextButton({message=null}){Text("知道了")}})}
    repo.error?.let {FailureModal({repo.clearError()},if(form!=null && retrySave!=null)({repo.clearError();retrySave?.invoke()})else null)}

    }
}
