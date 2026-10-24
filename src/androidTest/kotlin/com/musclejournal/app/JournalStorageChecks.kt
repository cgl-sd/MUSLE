package com.musclejournal.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.musclejournal.app.data.JournalCodec
import com.musclejournal.app.data.JournalStore
import com.musclejournal.app.domain.*
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** All fixtures live in an explicit instrumentation-only directory, never journal.db. */
internal fun runJournalStorageChecks(context:Context,verify:(String,()->Unit)->Unit){
    val directory=File(context.cacheDir,"storage-checks-${System.nanoTime()}").apply {mkdirs()}
    val now=Instant.parse("2026-10-24T08:00:00Z").toEpochMilli()
    val day=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    fun file(name:String)=File(directory,"$name.db")
    fun raw(file:File)=SQLiteDatabase.openOrCreateDatabase(file,null)
    val planned=TrainingSchedule.save(Journal(profile=Profile(onboardingDone=true,heightCm=176.0)),day,listOf("plank","bodyweight_squat"),now=now-1000)
    val finished=TrainingRecord.save(planned,listOf("plank","bodyweight_squat"),now)

    verify("completed schedule links and status survive codec and record storage reload"){
        check(JournalCodec.decode(JournalCodec.encode(finished))==finished)
        val path=file("schedule")
        JournalStore(context,path.absolutePath).use {store->val old=store.load();store.save(old,finished)}
        JournalStore(context,path.absolutePath).use {store->
            val read=store.load();check(read==finished)
            check(TodayTrainingPlan.scheduled(read,day)==null)
            val after=Actions.deleteSession(read,read.sessions.single().id,now+1000)
            store.save(read,after);check(store.load()==after)
            check(after.occurrences.all {it.status==OccurrenceStatus.PLANNED})
        }
    }
    verify("payloads before completion links default safely to empty links"){
        val payload=org.json.JSONObject(JournalCodec.encode(finished))
        payload.getJSONArray("sessions").getJSONObject(0).remove("completedOccurrences")
        val old=JournalCodec.decode(payload.toString())
        check(old.sessions.single().completedOccurrenceIds.isEmpty())
        check(old.sessions.single().completedActions==finished.sessions.single().completedActions)
        payload.getJSONArray("sessions").getJSONObject(0).put("completedOccurrences","not-an-array")
        check(runCatching {JournalCodec.decode(payload.toString())}.isFailure)
    }
    verify("v2 aggregate migration retains all legacy facts and removes duplicate aggregate storage"){
        val path=file("legacy-v2")
        val course=Course(id="course",name="old course",exercises=listOf(CourseExercise("plank",listOf(TargetSet(seconds=45)))))
        val goal=Goal(id="goal",title="old goal",start=day.toString(),end=day.toString())
        val session=finished.sessions.single().copy(snapshot=course,recordedDate=day.minusDays(1).toString(),note="旧字段保留")
        val legacy=finished.copy(courses=listOf(course),sessions=listOf(session),
            sets=listOf(SetEntry(id="old-set",sessionId=session.id,exerciseId="plank",completedAt=now,seconds=45)),
            checkIns=listOf(CheckIn(id="old-check",muscle=MuscleId.CHEST,date=day.toString(),soreness=2)),
            manualBaselines=listOf(ManualBaseline("plank",20.0,10,"历史",day.toString())),
            goals=listOf(goal),achievements=listOf(Achievement("old-award","历史奖项",now,goal.id,goal.version)),
            customExercises=listOf(Exercise("custom","旧动作",RecordType.REPS,"自重","次数",listOf(MuscleId.ABS),reviewed=false)),
            restUntil=now+1000,deletedIds=setOf("historical-deletion"))
        legacy.validate()
        raw(path).use {db->
            db.execSQL("CREATE TABLE journal_state (id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")
            db.execSQL("INSERT INTO journal_state VALUES (1,?)",arrayOf(JournalCodec.encode(legacy)))
            db.version=2
        }
        JournalStore(context,path.absolutePath).use {check(it.load()==legacy)}
        raw(path).use {db->
            check(db.version==4)
            db.rawQuery("SELECT name FROM sqlite_master WHERE name='journal_state'",null).use {check(!it.moveToFirst())}
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='journal_records_kind_position_idx'",null).use {check(it.moveToFirst())}
        }
    }
    verify("legacy database without its singleton state row is rejected instead of migrated as empty"){
        val path=file("missing-legacy-row")
        raw(path).use {db->
            db.execSQL("CREATE TABLE journal_state (id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")
            db.version=2
        }
        JournalStore(context,path.absolutePath).use {check(runCatching {it.load()}.isFailure)}
        raw(path).use {db->
            check(db.version==2)
            db.rawQuery("SELECT COUNT(*) FROM journal_state",null).use {check(it.moveToFirst()&&it.getInt(0)==0)}
        }
    }
    verify("v3 record database upgrades to indexed storage without losing records"){
        val path=file("v3-index")
        JournalStore(context,path.absolutePath).use {store->
            store.load();store.save(Journal(),finished)
        }
        raw(path).use {db->
            db.execSQL("DROP INDEX journal_records_kind_position_idx")
            db.version=3
        }
        JournalStore(context,path.absolutePath).use {check(it.load()==finished)}
        raw(path).use {db->
            check(db.version==4)
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='journal_records_kind_position_idx'",null).use {check(it.moveToFirst())}
        }
    }
    verify("failed migration leaves original v2 table payload and version intact"){
        val path=file("bad-migration")
        val original="{invalid payload}"
        raw(path).use {db->
            db.execSQL("CREATE TABLE journal_state (id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")
            db.execSQL("INSERT INTO journal_state VALUES (1,?)",arrayOf(original));db.version=2
        }
        JournalStore(context,path.absolutePath).use {check(runCatching {it.load()}.isFailure)}
        raw(path).use {db->
            check(db.version==2)
            db.rawQuery("SELECT payload FROM journal_state",null).use {check(it.moveToFirst()&&it.getString(0)==original)}
        }
    }
    verify("editing a profile writes no training rows and appending only inserts the new session"){
        val path=file("delta")
        JournalStore(context,path.absolutePath).use {store->
            store.load();store.save(Journal(),finished)
            raw(path).use {db->
                db.execSQL("CREATE TABLE test_writes (kind TEXT, record_key TEXT)")
                db.execSQL("CREATE TRIGGER observe_update AFTER UPDATE ON journal_records BEGIN INSERT INTO test_writes VALUES (NEW.kind,NEW.record_key); END")
                db.execSQL("CREATE TRIGGER observe_insert AFTER INSERT ON journal_records BEGIN INSERT INTO test_writes VALUES (NEW.kind,NEW.record_key); END")
            }
            val next=finished.copy(profile=finished.profile.copy(heightCm=177.0))
            store.save(finished,next)
            raw(path).use {db->db.rawQuery("SELECT COUNT(*) FROM test_writes",null).use {check(it.moveToFirst()&&it.getInt(0)==0)}}
            val added=TrainingRecord.save(next,listOf("pushup"),now+2000)
            store.save(next,added)
            raw(path).use {db->db.rawQuery("SELECT kind,COUNT(*) FROM test_writes GROUP BY kind",null).use {
                check(it.moveToFirst()&&it.getString(0)=="sessions"&&it.getInt(1)==1&&!it.moveToNext())
            }}
            check(store.load()==added)
        }
    }
    verify("mid-transaction SQLite rejection rolls back both schedule changes and saved actions"){
        val path=file("rollback")
        JournalStore(context,path.absolutePath).use {store->
            store.load();store.save(Journal(),planned)
            raw(path).use {db->db.execSQL("CREATE TRIGGER reject_new_session BEFORE INSERT ON journal_records WHEN NEW.kind='sessions' BEGIN SELECT RAISE(ABORT,'injected after schedule update'); END")}
            check(runCatching {store.save(planned,finished)}.isFailure)
            check(store.load()==planned)
            raw(path).use {it.execSQL("DROP TRIGGER reject_new_session")}
            store.save(planned,finished);check(store.load()==finished)
        }
    }
    verify("two repositories cannot overwrite a newer committed revision with stale data"){
        val path=file("concurrency")
        JournalStore(context,path.absolutePath).use {first->JournalStore(context,path.absolutePath).use {second->
            val old=first.load();check(second.load()==old)
            first.save(old,finished)
            check(runCatching {second.save(old,old.copy(profile=old.profile.copy(heightCm=180.0)))}.isFailure)
            check(second.load()==finished)
        }}
    }
    verify("damaged compressed rows are rejected instead of silently replaced"){
        val path=file("bad-row")
        JournalStore(context,path.absolutePath).use {store->store.load();store.save(Journal(),finished)}
        raw(path).use {it.execSQL("UPDATE journal_records SET payload=X'019999' WHERE kind='sessions'")}
        JournalStore(context,path.absolutePath).use {check(runCatching {it.load()}.isFailure)}
    }
    verify("compressed record storage uses less disk than a representative aggregate database"){
        val sessions=(0 until 1500).map {index->
            val at=now-(1500-index)*86_400_000L
            finished.sessions.single().copy(id="bench-$index",startedAt=at,endedAt=at,
                snapshot=Course(id="snapshot-$index",name="训练记录",exercises=listOf(CourseExercise("plank"),CourseExercise("bodyweight_squat"))),
                completedActions=listOf(CompletedAction("plank",at),CompletedAction("bodyweight_squat",at)),
                completedOccurrenceIds=emptyList(),note="正常训练记录",modifiedAt=at)
        }
        val bulk=Journal(sessions=sessions)
        val encoded=JournalCodec.encode(bulk)
        val oldPath=file("benchmark-old");val newPath=file("benchmark-new")
        raw(oldPath).use {db->
            db.execSQL("CREATE TABLE journal_state (id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")
            db.execSQL("INSERT INTO journal_state VALUES (1,?)",arrayOf(encoded))
        }
        val start=System.nanoTime()
        JournalStore(context,newPath.absolutePath).use {store->store.load();store.save(Journal(),bulk);check(store.load()==bulk)}
        val elapsed=(System.nanoTime()-start)/1_000_000
        check(newPath.length()<oldPath.length())
        File(directory,"benchmark.json").writeText(org.json.JSONObject()
            .put("sessions",1500).put("legacy_database_bytes",oldPath.length())
            .put("record_database_bytes",newPath.length()).put("write_and_reload_ms",elapsed).toString())
        android.util.Log.i("JournalStorageBenchmark","old=${oldPath.length()} new=${newPath.length()} sessions=1500 write_and_reload_ms=$elapsed")
    }
    verify("record storage reloads histories beyond the former aggregate 8MB cap"){
        val path=file("large-history")
        val sessions=(0 until 500).map {index->finished.sessions.single().copy(id="large-$index",note="history ".repeat(2200),completedOccurrenceIds=emptyList())}
        val large=Journal(sessions=sessions)
        check(JournalCodec.encode(large).length>8_000_000)
        JournalStore(context,path.absolutePath).use {store->store.load();store.save(Journal(),large)}
        JournalStore(context,path.absolutePath).use {check(it.load()==large)}
    }
}
