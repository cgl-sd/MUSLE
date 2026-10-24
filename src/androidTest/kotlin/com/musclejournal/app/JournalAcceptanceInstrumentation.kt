package com.musclejournal.app

import android.app.Instrumentation
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.musclejournal.app.data.JournalCodec
import com.musclejournal.app.data.JournalRepository
import com.musclejournal.app.domain.*
import com.musclejournal.app.update.*
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Device-side persistence/codec checks. Runs in a uniquely named test DB, never the production DB. */
class JournalAcceptanceInstrumentation:Instrumentation(){
    private var regionsOnly=false
    private var launcherOnly=false
    private var updateNetworkOnly=false
    override fun onCreate(arguments:Bundle?){
        launcherOnly=arguments?.getString("launcher_icon")=="true"
        regionsOnly=arguments?.getString("body_regions")=="true"
        updateNetworkOnly=arguments?.getString("update_network")=="true"
        super.onCreate(arguments);start()
    }
    override fun onStart(){
        if(launcherOnly){runLauncherIconChecks(this);return}
        if(regionsOnly){runBodyRegionChecks(this);return}
        if(updateNetworkOnly){
            try {
                val source=GithubReleaseClient()
                val release=kotlinx.coroutines.runBlocking {source.latest()}
                val installed=UpdateVersion.parse(BuildConfig.VERSION_NAME)
                    ?: error("Installed test APK version is not major.minor.patch")
                check(release!=null&&release.version>=installed&&release.tag=="v${release.version}")
                val digest=kotlinx.coroutines.runBlocking {source.checksum(release)}
                check(digest.matches(UpdatePolicy.digestPattern))
                finish(RESULT_OK,Bundle().apply {
                    putString("stream","PASS live release metadata and checksum\n1 checks passed\n")
                    putInt("passed",1)
                })
            } catch(error:Throwable) {
                finish(RESULT_CANCELED,Bundle().apply {putString("stream","FAIL ${error.stackTraceToString()}\n")})
            }
            return
        }
        val lines=mutableListOf<String>();var passed=0
        fun verify(name:String,block:()->Unit){block();passed++;lines.add("PASS $name")}
        val name="acceptance-${System.nanoTime()}.db"
        var repo:JournalRepository?=null
        try{
            val at=Instant.parse("2026-10-17T10:00:00Z").toEpochMilli()
            val s=Session(id="test-session",title="验收",startedAt=at,exerciseIds=listOf("plank"))
            val row=SetEntry(id="test-set",sessionId=s.id,exerciseId="plank",completedAt=at+1000,seconds=30)
            val fixture=Journal(profile=Profile(adult="已满18岁",days=listOf(1,3,5)),sessions=listOf(s),sets=listOf(row))
            verify("codec roundtrip including nullable fields and integer days"){val d=JournalCodec.decode(JournalCodec.encode(fixture));check(d==fixture)}
            verify("date-only backfill survives codec round trip without changing save timestamp"){
                val date=java.time.LocalDate.parse("2026-10-16")
                val record=BackfillRecord.create(Journal(),date,"",listOf("plank"),at,java.time.ZoneId.of("Asia/Shanghai"))
                val saved=Journal(sessions=listOf(record));val decoded=JournalCodec.decode(JournalCodec.encode(saved))
                check(decoded==saved && decoded.sessions.single().date()==date && decoded.sessions.single().startedAt==at)
            }
            verify("action side and exercise side requirement survive codec round trip"){
                val sideSession=Session(id="side-session",title="单侧",startedAt=at,endedAt=at,status=SessionStatus.COMPLETED,exerciseIds=listOf("one_arm_row"),completedActions=listOf(CompletedAction("one_arm_row",at,"右侧")))
                val sideJournal=Journal(sessions=listOf(sideSession))
                val decoded=JournalCodec.decode(JournalCodec.encode(sideJournal))
                check(decoded==sideJournal)
                check(decoded.exercise("one_arm_row").requiresSide)
            }
            verify("payloads without recordedDate keep original session date and fields"){
                val payload=org.json.JSONObject(JournalCodec.encode(fixture))
                payload.getJSONArray("sessions").getJSONObject(0).remove("recordedDate")
                check(JournalCodec.decode(payload.toString())==fixture)
            }

            verify("new age and height profile fields round trip"){
                val updated=fixture.copy(profile=fixture.profile.copy(ageBand="30–34岁",heightCm=178.5,experience="3–5年"))
                check(JournalCodec.decode(JournalCodec.encode(updated))==updated)
            }
            verify("existing profiles without optional age and height still decode"){
                val old=org.json.JSONObject(JournalCodec.encode(fixture));old.getJSONObject("profile").remove("ageBand");old.getJSONObject("profile").remove("heightCm")
                check(JournalCodec.decode(old.toString())==fixture)
            }
            verify("invalid height and inconsistent adult classification are rejected"){
                val bad=org.json.JSONObject(JournalCodec.encode(fixture));bad.getJSONObject("profile").put("heightCm",999)
                check(runCatching {JournalCodec.decode(bad.toString())}.isFailure)
                bad.getJSONObject("profile").put("heightCm",178).put("ageBand","未满18岁")
                check(runCatching {JournalCodec.decode(bad.toString())}.isFailure)
            }
            verify("fractional repetitions are not silently rounded on decode"){val malformed=org.json.JSONObject(JournalCodec.encode(fixture));malformed.getJSONArray("sets").getJSONObject(0).put("seconds",1.5);check(runCatching {JournalCodec.decode(malformed.toString())}.isFailure)}
            verify("unknown aggregate schema rejected"){check(runCatching {JournalCodec.decode("{\"schema\":9}")}.isFailure)}
            // A payload as written by schema 1 (no muscle/source keys, no baselines): must migrate, not fail.
            val legacyPayload="""
                {"schema":1,
                 "profile":{"adult":"已满18岁","experience":"不确定","frequency":0,"returning":false,"mass":null,"measuredOn":null,"direction":"规律训练","days":[1,3,5],"minutes":45,"equipment":"","restrictions":"","onboarded":true},
                 "settings":{"unit":"kg","rest":90,"weeks":4,"minimum":3,"hours":48,"motion":true},
                 "exercises":[],"courses":[],"occurrences":[],
                 "sessions":[{"id":"legacy-session","title":"旧记录","start":$at,"zone":"Asia/Shanghai","end":${at+60000},"status":"COMPLETED","snapshot":null,"exercises":["plank"],"occurrence":null,"pause":null,"pausedMs":0,"note":"","modifiedAt":$at}],
                 "sets":[{"id":"legacy-set","session":"legacy-session","exercise":"plank","at":${at+1000},"kg":null,"reps":null,"seconds":30,"warmup":false,"scale":"RIR","effort":null,"side":"双侧","notes":"","modifiedAt":$at}],
                 "checkIns":[],"goals":[],"achievements":[],"restUntil":null,"deletedIds":[]}
            """.trimIndent()
            verify("schema 1 payload migrates to the current schema without losing records"){
                val migrated=JournalCodec.decode(legacyPayload)
                check(migrated.schema==JOURNAL_SCHEMA && migrated.sessions.single().id=="legacy-session" && migrated.sets.single().seconds==30)
            }
            verify("legacy payload re-encodes as the current schema"){check(org.json.JSONObject(JournalCodec.upgrade(legacyPayload)).getInt("schema")==JOURNAL_SCHEMA)}
            verify("manual baseline is preferred over derived history and round trips"){
                val benchRow=SetEntry(id="baseline-set",sessionId=s.id,exerciseId="barbell_bench",completedAt=at+2000,loadKg=40.0,reps=10)
                val typed=fixture.copy(sets=listOf(benchRow),manualBaselines=listOf(ManualBaseline("barbell_bench",32.0,10,"健身房实测","2026-01-01")))
                val ratio=Metrics.baseline(typed,benchRow).ratio
                check(ratio!=null && ratio>1.24 && ratio<1.26)
                check(JournalCodec.decode(JournalCodec.encode(typed)).manualBaselines.single().exerciseId=="barbell_bench")
            }
            verify("muscle goal counts direct working sets inside its period and awards once"){
                val goal=Goal(id="goal-muscle",title="胸肌组数",start="2026-10-12",end="2026-10-18",metric="muscle",target=1.0,muscleId=MuscleId.CHEST)
                val benchRow=SetEntry(id="goal-set",sessionId=s.id,exerciseId="barbell_bench",completedAt=at+3000,loadKg=40.0,reps=10)
                val journal=fixture.copy(sessions=listOf(s.copy(status=SessionStatus.COMPLETED,endedAt=at+60000)),sets=listOf(benchRow),goals=listOf(goal))
                check(Metrics.goalProgress(journal,goal)==1.0)
                check(Metrics.reconcile(journal,at+60000).achievements.size==1)
            }
            verify("malformed aggregate payload is rejected"){check(runCatching {JournalCodec.decode("{}")}.isFailure)}
            repo=JournalRepository(targetContext,name)
            val deadline=System.currentTimeMillis()+10000
            while(!repo.ready && System.currentTimeMillis()<deadline)Thread.sleep(20)
            check(repo.ready && repo.error==null)
            val done=CountDownLatch(1);repo.change(after={done.countDown()}){fixture};check(done.await(10,TimeUnit.SECONDS));check(repo.error==null)
            verify("SQLite committed all training state"){check(repo.state.sets.single()==row)}
            verify("unilateral action side commits through repository") {
                val sideName="side-action-${System.nanoTime()}.db"
                val sideRepo=JournalRepository(targetContext,sideName)
                val sideReady=System.currentTimeMillis()+10000
                while(!sideRepo.ready&&System.currentTimeMillis()<sideReady)Thread.sleep(20)
                check(sideRepo.ready&&sideRepo.error==null)
                val sideDone=CountDownLatch(1)
                sideRepo.change(after={sideDone.countDown()}) {
                    TrainingRecord.saveSelections(it,listOf(TrainingRecord.Selection("one_arm_row","左侧")),at)
                }
                check(sideDone.await(10,TimeUnit.SECONDS)&&sideRepo.error==null)
                check(sideRepo.state.sessions.single().completedActions!!.single().side=="左侧")
                sideRepo.close()
            }
            val second=JournalRepository(targetContext,name)
            val until=System.currentTimeMillis()+10000;while(!second.ready && System.currentTimeMillis()<until)Thread.sleep(20)
            verify("fresh repository reloads saved data"){check(second.ready && second.error==null && second.state.sets.single()==row)}
            second.close()
            val queuedName="queued-writes-${System.nanoTime()}.db"
            val queuedRepo=JournalRepository(targetContext,queuedName)
            val queuedReady=System.currentTimeMillis()+10000;while(!queuedRepo.ready&&System.currentTimeMillis()<queuedReady)Thread.sleep(20)
            val queuedCallbacks=CountDownLatch(2)
            queuedRepo.change(after={queuedCallbacks.countDown()}){it.copy(profile=it.profile.copy(heightCm=176.0))}
            queuedRepo.change(after={queuedCallbacks.countDown()}){it.copy(profile=it.profile.copy(direction="queued-write"))}
            check(queuedCallbacks.await(10,TimeUnit.SECONDS));check(queuedRepo.error==null)
            verify("rapid writes queue in order instead of being silently dropped"){
                check(queuedRepo.state.profile.heightCm==176.0&&queuedRepo.state.profile.direction=="queued-write")
            }
            verify("queued latest snapshot remains serializable"){
                check(JournalCodec.decode(JournalCodec.encode(queuedRepo.state)).profile.heightCm==176.0)
            }
            val oneShot=CountDownLatch(1)
            val firstAccepted=queuedRepo.changeOnce("same-save",after={oneShot.countDown()}){it.copy(profile=it.profile.copy(direction="one-shot"))}
            val secondAccepted=queuedRepo.changeOnce("same-save"){it.copy(profile=it.profile.copy(direction="duplicate"))}
            check(firstAccepted && !secondAccepted && oneShot.await(10,TimeUnit.SECONDS))
            verify("same one-shot operation is accepted only once"){check(queuedRepo.state.profile.direction=="one-shot")}
            queuedRepo.close()
            val before=repo.state
            val invalid=CountDownLatch(1)
            val stopObserve=repo.observe {if(!repo.busy && repo.error!=null)invalid.countDown()}
            repo.change {throw IllegalArgumentException("injected write failure")};check(invalid.await(10,TimeUnit.SECONDS));stopObserve()
            verify("failed transaction leaves previous facts unchanged"){check(repo.state==before)}
            val corruptName="unreadable-${System.nanoTime()}.db"
            val corruptFile=targetContext.getDatabasePath(corruptName);corruptFile.parentFile!!.mkdirs();val original="invalid sqlite fixture".toByteArray();corruptFile.writeBytes(original)
            val corruptRepo=JournalRepository(targetContext,corruptName)
            val corruptDeadline=System.currentTimeMillis()+10000;while(!corruptRepo.ready && System.currentTimeMillis()<corruptDeadline)Thread.sleep(20)
            verify("corrupt DB is not silently deleted and reseeded"){check(corruptRepo.ready && corruptRepo.error!=null);check(corruptFile.exists() && corruptFile.readBytes().contentEquals(original))}
            val blockedWrite=CountDownLatch(1)
            val stopCorrupt=corruptRepo.observe {if(!corruptRepo.busy&&corruptRepo.error!=null)blockedWrite.countDown()}
            corruptRepo.change {it.copy(profile=it.profile.copy(direction="must-not-write"))}
            check(blockedWrite.await(10,TimeUnit.SECONDS));stopCorrupt()
            verify("unreadable DB rejects writes and preserves the original file"){check(corruptFile.exists() && corruptFile.readBytes().contentEquals(original))}
            corruptRepo.close()
            verify("database upgrade from v1 keeps records and moves the payload to the current schema"){
                val legacyName="legacy-${System.nanoTime()}.db"
                val legacyFile=targetContext.getDatabasePath(legacyName);legacyFile.parentFile!!.mkdirs();if(legacyFile.exists())legacyFile.delete()
                val raw=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(legacyFile,null)
                raw.execSQL("CREATE TABLE journal_state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)")
                raw.execSQL("INSERT INTO journal_state (id,payload) VALUES (1,?)",arrayOf(legacyPayload))
                raw.version=1;raw.close()
                val upgraded=JournalRepository(targetContext,legacyName)
                val upgradeDeadline=System.currentTimeMillis()+10000
                while(!upgraded.ready && System.currentTimeMillis()<upgradeDeadline)Thread.sleep(20)
                check(upgraded.ready && upgraded.error==null)
                check(upgraded.state.sessions.single().id=="legacy-session" && upgraded.state.sets.single().seconds==30)
                // Must stay in step with JournalRepository's payload table version.
                check(android.database.sqlite.SQLiteDatabase.openDatabase(legacyFile.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { it.version==4 })
                upgraded.close()
            }
            verify("commemoration exports actual PNG without profile fields"){val memory=Achievement("a","测试目标",at,"g",1);val bytes=java.io.ByteArrayOutputStream();com.musclejournal.app.data.Commemoration.write(memory,null,bytes);val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size());check(bitmap.width==1080 && bitmap.height==1740);bitmap.recycle()}
            verify("aggregate payload roundtrip preserves courses schedules feedback goals and memories"){
                val course=Course(id="course",name="课程",exercises=listOf(CourseExercise("plank",listOf(TargetSet(seconds=45,restSeconds=60)))))
                val occurrence=Occurrence(id="occ",courseId=course.id,date="2026-10-18",seriesId="series",override=course)
                val goal=Goal(id="goal",title="本周一次",start="2026-10-12",end="2026-10-18",target=1.0)
                val full=Metrics.reconcile(fixture.copy(courses=listOf(course),occurrences=listOf(occurrence),sessions=listOf(s.copy(status=SessionStatus.COMPLETED,endedAt=at+60000,snapshot=course,occurrenceId=occurrence.id)),checkIns=listOf(CheckIn(muscle=MuscleId.CHEST,date="2026-10-17",soreness=0)),manualBaselines=listOf(ManualBaseline("plank",20.0,10,"健身房实测","2026-01-01")),goals=listOf(goal,Goal(id="goal-muscle",title="胸肌组数",start="2026-10-12",end="2026-10-18",metric="muscle",target=3.0,muscleId=MuscleId.CHEST))),at+60000)
                check(full.achievements.size==1);check(JournalCodec.decode(JournalCodec.encode(full))==full)
            }
            val faultDirectory=java.io.File(targetContext.filesDir,"journal-fault-${System.nanoTime()}").apply {mkdirs()}
            val faultRepo=JournalRepository(targetContext,java.io.File(faultDirectory,"state.db").absolutePath)
            val faultReady=System.currentTimeMillis()+10000;while(!faultRepo.ready && System.currentTimeMillis()<faultReady)Thread.sleep(20)
            val seeded=CountDownLatch(1);faultRepo.change(after={seeded.countDown()}){fixture};check(seeded.await(10,TimeUnit.SECONDS))
            val oldState=faultRepo.state
            try {
                val injectedDb=android.database.sqlite.SQLiteDatabase.openDatabase(java.io.File(faultDirectory,"state.db").absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
                injectedDb.execSQL("CREATE TRIGGER reject_test_write BEFORE UPDATE ON journal_meta BEGIN SELECT RAISE(ABORT, 'injected SQLite write failure'); END");injectedDb.close()
                val failed=CountDownLatch(1);val unobserve=faultRepo.observe {if(!faultRepo.busy && faultRepo.error!=null)failed.countDown()}
                faultRepo.change {it.copy(profile=it.profile.copy(direction="not committed"))}
                check(failed.await(10,TimeUnit.SECONDS));unobserve()
                verify("real SQLite write rejection retains previous published facts"){check(faultRepo.state==oldState)}
            } finally {faultRepo.close()}
            verify("update transfer only inspects and cancels its own download") {
                val manager=targetContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val transfer=UpdateTransfer(targetContext)
                val version=UpdateVersion(0,3,0)
                val apkName="MuscleJournal-$version.apk"
                val base="https://github.com/$UPDATE_REPOSITORY/releases/download/v$version/"
                val release=UpdateRelease(
                    version,
                    "v$version",
                    ReleaseAsset(apkName,base+apkName,1),
                    ReleaseAsset("$apkName.sha256","$base$apkName.sha256",64),
                )
                fun pending()=PendingUpdate(
                    release,
                    "0".repeat(64),
                    "MuscleJournal-update-${UUID.randomUUID()}.apk",
                    0,
                )
                fun enqueueOffline(value:PendingUpdate):Long=manager.enqueue(
                    DownloadManager.Request(Uri.parse(release.apk.url))
                        .setTitle("MuscleJournal update record regression")
                        .setDescription(value.fileName)
                        .setDestinationUri(Uri.fromFile(transfer.file(value.fileName)))
                        .setAllowedNetworkTypes(0),
                )
                val first=pending();val second=pending();val firstId=enqueueOffline(first);var secondId=0L
                try {
                    secondId=enqueueOffline(second)
                    val saved=first.copy(downloadId=firstId)
                    check(transfer.inspect(saved) is TransferSnapshot.Progress)
                    check(transfer.recoverId(first)==firstId)
                    check(transfer.inspect(first.copy(downloadId=secondId))==TransferSnapshot.Missing)
                    transfer.remove(saved)
                    check(transfer.inspect(saved)==TransferSnapshot.Missing)
                    check(transfer.inspect(second.copy(downloadId=secondId)) is TransferSnapshot.Progress)
                } finally {
                    manager.remove(firstId)
                    if(secondId!=0L)manager.remove(secondId)
                    transfer.file(first.fileName).delete()
                    transfer.file(second.fileName).delete()
                }
            }
            runJournalStorageChecks(targetContext,::verify)
            val result=Bundle().apply {putString("stream",lines.joinToString("\n")+"\n$passed checks passed\n");putInt("passed",passed)}
            finish(RESULT_OK,result)
        }catch(e:Throwable){finish(RESULT_CANCELED,Bundle().apply {putString("stream",lines.joinToString("\n")+"\nFAIL ${e.stackTraceToString()}\n")})}
        finally{repo?.close()}
    }
    companion object {const val RESULT_OK=-1;const val RESULT_CANCELED=0}
}
