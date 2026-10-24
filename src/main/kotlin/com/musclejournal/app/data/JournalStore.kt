package com.musclejournal.app.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper
import com.musclejournal.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Record storage v4. The domain aggregate remains an in-memory view, not a rewritten disk blob. */
internal class JournalStore(context:Context,name:String):SQLiteOpenHelper(
    context,name,null,DB_VERSION,
    DatabaseErrorHandler {db->throw SQLiteDatabaseCorruptException("数据库损坏；禁止自动删除原文件：${db.path}")},
) {
    private data class Record(val kind:String,val key:String,val position:Int,val value:Any,val encode:()->String)
    private var loadedRevision:Long?=null

    override fun onCreate(db:SQLiteDatabase) { createRecords(db);createIndexes(db) }

    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        require(newVersion==DB_VERSION){"缺少数据库迁移步骤；保留原文件"}
        if(oldVersion in 1..2){
            // SQLiteOpenHelper wraps the entire upgrade in a transaction, including the old-table removal.
            val legacy=db.rawQuery("SELECT payload FROM journal_state WHERE id=1",null).use {
                check(it.moveToFirst()){"旧数据库状态缺失，禁止迁移为空库"}
                JournalCodec.decodeStored(JSONObject(it.getString(0)))
            }
            createRecords(db)
            writeRecords(db,Journal(),legacy)
            db.execSQL("UPDATE journal_meta SET payload=? WHERE id=1",arrayOf(pack(metadata(legacy))))
            check(readRecords(db).second==legacy){"迁移校验失败；保留原始数据库"}
            db.execSQL("DROP TABLE journal_state")
        }else{
            require(oldVersion==3){"缺少数据库迁移步骤；保留原文件"}
        }
        createIndexes(db)
    }

    override fun onDowngrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) { error("不支持数据库降级；保留原文件") }

    private fun createRecords(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE journal_meta (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL, payload BLOB NOT NULL)")
        db.execSQL("CREATE TABLE journal_records (kind TEXT NOT NULL, record_key TEXT NOT NULL, position INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(kind,record_key)) WITHOUT ROWID")
        db.execSQL("INSERT INTO journal_meta (id,revision,payload) VALUES (1,0,?)",arrayOf(pack(metadata(Journal()))))
    }
    private fun createIndexes(db:SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS journal_records_kind_position_idx ON journal_records(kind,position)")
    }

    fun load():Journal {
        val db=readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val (revision,journal)=readRecords(db)
            db.setTransactionSuccessful()
            loadedRevision=revision
            return journal
        } finally {db.endTransaction()}
    }

    fun save(previous:Journal,next:Journal) {
        val db=writableDatabase
        val expected=checkNotNull(loadedRevision){"数据库尚未读取，禁止覆盖"}
        db.beginTransaction()
        try {
            val revision=db.rawQuery("SELECT revision FROM journal_meta WHERE id=1",null).use {
                check(it.moveToFirst()){"数据库元数据缺失，禁止覆盖"};it.getLong(0)
            }
            check(revision==expected){"数据已被另一实例修改，请重新打开应用后重试"}
            writeRecords(db,previous,next)
            val values=ContentValues().apply {put("revision",expected+1);put("payload",pack(metadata(next)))}
            check(db.update("journal_meta",values,"id=1 AND revision=?",arrayOf(expected.toString()))==1)
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        loadedRevision=expected+1
    }

    private fun writeRecords(db:SQLiteDatabase,previous:Journal,next:Journal) {
        val old=records(previous).associateBy {it.kind to it.key}
        val current=records(next)
        val currentKeys=current.map {it.kind to it.key}.toSet()
        old.keys.filterNot {it in currentKeys}.forEach {(kind,key)->
            db.delete("journal_records","kind=? AND record_key=?",arrayOf(kind,key))
        }
        current.forEach {record->
            val prior=old[record.kind to record.key]
            if(prior==null){
                val values=ContentValues().apply {
                    put("kind",record.kind);put("record_key",record.key);put("position",record.position)
                    put("payload",pack(record.encode()))
                }
                db.insertOrThrow("journal_records",null,values)
            } else if(prior.value!=record.value || prior.position!=record.position){
                val values=ContentValues().apply {
                    if(prior.position!=record.position)put("position",record.position)
                    if(prior.value!=record.value)put("payload",pack(record.encode()))
                }
                check(db.update("journal_records",values,"kind=? AND record_key=?",arrayOf(record.kind,record.key))==1)
            }
        }
    }

    private fun readRecords(db:SQLiteDatabase):Pair<Long,Journal> {
        val (revision,root)=db.rawQuery("SELECT revision,payload FROM journal_meta WHERE id=1",null).use {
            check(it.moveToFirst()){"数据库元数据缺失；禁止重建空库"}
            it.getLong(0) to JSONObject(unpack(it.getBlob(1)))
        }
        val counts=root.getJSONObject("_counts")
        val arrays=KINDS.associateWith {JSONArray()}
        // A single cursor avoids per-record queries; position restores domain order without another index.
        val grouped=KINDS.associateWith {mutableListOf<Pair<Int,JSONObject>>()}
        db.rawQuery("SELECT kind,record_key,position,payload FROM journal_records ORDER BY kind,position",null).use {cursor->
            while(cursor.moveToNext()){
                val kind=cursor.getString(0)
                val rows=checkNotNull(grouped[kind]){"未知存储类型，禁止覆盖"}
                val value=JSONObject(unpack(cursor.getBlob(3)))
                check(key(kind,value)==cursor.getString(1)){"记录标识不匹配，禁止覆盖"}
                rows.add(cursor.getInt(2) to value)
            }
        }
        KINDS.forEach {kind->
            val rows=grouped.getValue(kind)
            check(rows.size==counts.getInt(kind)){"记录数量不匹配，禁止覆盖"}
            rows.forEachIndexed {index,(position,value)->
                check(position==index){"记录顺序损坏，禁止覆盖"};arrays.getValue(kind).put(value)
            }
            root.put(kind,arrays.getValue(kind))
        }
        return revision to JournalCodec.decodeStored(root)
    }

    private fun metadata(j:Journal):String {
        val root=JSONObject(JournalCodec.encode(Journal(profile=j.profile,settings=j.settings,restUntil=j.restUntil,deletedIds=j.deletedIds)))
        val counts=JSONObject()
        listOf("exercises" to j.customExercises.size,"courses" to j.courses.size,
            "occurrences" to j.occurrences.size,"sessions" to j.sessions.size,"sets" to j.sets.size,
            "checkIns" to j.checkIns.size,"goals" to j.goals.size,"baselines" to j.manualBaselines.size,
            "achievements" to j.achievements.size).forEach {(kind,size)->counts.put(kind,size)}
        KINDS.forEach(root::remove)
        return root.put("_counts",counts).toString()
    }

    private fun records(j:Journal):List<Record> = buildList {
        fun <T:Any> append(kind:String,items:List<T>,id:(T)->String,slice:(T)->Journal){
            items.forEachIndexed {index,item->
                add(Record(kind,id(item),index,item){
                    // Reuse the compatibility codec only for changed records, never the full history.
                    JSONObject(JournalCodec.encode(slice(item))).getJSONArray(kind).getJSONObject(0).toString()
                })
            }
        }
        append("exercises",j.customExercises,{it.id}){Journal(customExercises=listOf(it))}
        append("courses",j.courses,{it.id}){Journal(courses=listOf(it))}
        append("occurrences",j.occurrences,{it.id}){Journal(occurrences=listOf(it))}
        append("sessions",j.sessions,{it.id}){Journal(sessions=listOf(it))}
        append("sets",j.sets,{it.id}){Journal(sets=listOf(it))}
        append("checkIns",j.checkIns,{"${it.muscle.name}:${it.date}"}){Journal(checkIns=listOf(it))}
        append("goals",j.goals,{"${it.id}:${it.version}"}){Journal(goals=listOf(it))}
        append("baselines",j.manualBaselines,{it.exerciseId}){Journal(manualBaselines=listOf(it))}
        append("achievements",j.achievements,{it.key}){Journal(achievements=listOf(it))}
    }

    private fun key(kind:String,value:JSONObject):String=when(kind){
        "checkIns"->"${value.getString("muscle")}:${value.getString("date")}"
        "goals"->"${value.getString("id")}:${value.getInt("version")}"
        "baselines"->value.getString("exercise")
        "achievements"->value.getString("key")
        else->value.getString("id")
    }

    companion object {
        private val KINDS=listOf("exercises","courses","occurrences","sessions","sets","checkIns","goals","baselines","achievements")
        private const val DB_VERSION=4
        private const val MAX_RECORD_BYTES=32*1024*1024
        // Keep each stored value below the CursorWindow floor on the supported Android versions.
        private const val MAX_STORED_BYTES=1_000_000
        private fun pack(text:String):ByteArray {
            val plain=text.toByteArray(Charsets.UTF_8)
            require(plain.size<=MAX_RECORD_BYTES){"单条记录过大，未保存"}
            if(plain.size>=256){
                val buffer=ByteArrayOutputStream()
                GZIPOutputStream(buffer).use {it.write(plain)}
                val compressed=buffer.toByteArray()
                if(compressed.size<plain.size){
                    require(compressed.size<MAX_STORED_BYTES){"单条记录过大，未保存"}
                    return byteArrayOf(1)+compressed
                }
            }
            require(plain.size<MAX_STORED_BYTES){"单条记录过大，未保存"}
            return byteArrayOf(0)+plain
        }
        private fun unpack(bytes:ByteArray):String {
            check(bytes.isNotEmpty()){"存储内容为空"}
            val raw=when(bytes[0].toInt()){
                0->bytes.copyOfRange(1,bytes.size).also {check(it.size<=MAX_RECORD_BYTES)}
                1->GZIPInputStream(bytes.inputStream(1,bytes.size-1)).use {input->
                    val buffer=ByteArrayOutputStream();val chunk=ByteArray(8192)
                    while(true){val count=input.read(chunk);if(count<0)break;check(buffer.size()+count<=MAX_RECORD_BYTES){"存储记录解压超限"};buffer.write(chunk,0,count)}
                    buffer.toByteArray()
                }
                else->error("未知存储编码，禁止覆盖")
            }
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw)).toString()
        }
    }
}
