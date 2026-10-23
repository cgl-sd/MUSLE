package com.musclejournal.app.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import com.musclejournal.app.domain.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** Payload table version. v2 rewrites schema-1 payloads into the current aggregate schema. */
private const val DB_VERSION = 2

/** SQLite commits a versioned aggregate atomically. No business computation lives in the UI. */
class JournalRepository(context: Context, private val name: String = "journal.db") {
    private class Store(context:Context,name:String):SQLiteOpenHelper(context,name,null,DB_VERSION,DatabaseErrorHandler { db -> throw SQLiteDatabaseCorruptException("数据库损坏；禁止自动删除原文件：${db.path}") }) {
        override fun onCreate(db:SQLiteDatabase) { db.execSQL("CREATE TABLE journal_state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)") }
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
            require(newVersion in (oldVersion+1)..DB_VERSION) { "不支持的数据库升级；保留原文件" }
            for (version in (oldVersion+1)..newVersion) when (version) {
                // The single payload row is rewritten through the codec, so older aggregates are migrated
                // rather than dropped; a failure aborts the upgrade and leaves the original file in place.
                2 -> db.rawQuery("SELECT payload FROM journal_state WHERE id=1",null).use { cursor ->
                    if (cursor.moveToFirst()) db.execSQL("UPDATE journal_state SET payload=? WHERE id=1",arrayOf(JournalCodec.upgrade(cursor.getString(0))))
                }
                else -> error("缺少数据库迁移步骤 $version；保留原文件")
            }
        }
        override fun onDowngrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) { error("不支持数据库降级；保留原文件") }
    }
    private val store=Store(context.applicationContext,name)
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val pendingWrites=AtomicInteger(0)
    @Volatile var state=Journal();private set
    val busy:Boolean get()=pendingWrites.get()>0
    @Volatile var error:String?=null;private set
    @Volatile var ready=false;private set
    private var readable=true
    private val listeners=mutableSetOf<()->Unit>()
    init { executor.execute {
        try { store.readableDatabase.query("journal_state",arrayOf("payload"),"id=1",null,null,null,null).use { if(it.moveToFirst())state=JournalCodec.decode(it.getString(0)) } }
        catch(e:Exception){readable=false;error="读取失败，原始数据库已保留：${e.message}"}
        ready=true;publish()
    } }
    fun observe(listener:()->Unit):()->Unit { synchronized(listeners){listeners.add(listener)};return {synchronized(listeners){listeners.remove(listener)}} }
    private fun publish() { main.post { synchronized(listeners){listeners.toList()}.forEach {runCatching(it)} } }
    fun clearError(){error=null;publish()}
    fun change(after:()->Unit = {}, action:(Journal)->Journal) {
        pendingWrites.incrementAndGet();error=null;publish()
        try { executor.execute {
            try {
                check(readable){"原始数据库读取失败，禁止覆盖。请保留原始数据库文件后修复。"}
                val next=Metrics.reconcile(action(state),System.currentTimeMillis());next.validate()
                val encoded=JournalCodec.encode(next)
                val db=store.writableDatabase;db.beginTransaction()
                try { val values=ContentValues().apply { put("id",1);put("payload",encoded) };db.insertWithOnConflict("journal_state",null,values,SQLiteDatabase.CONFLICT_REPLACE).also { check(it!=-1L) };db.setTransactionSuccessful() } finally {db.endTransaction()}
                state=next
                main.post { after() }
            } catch(e:Exception){error=e.message?:"保存失败，请重试"}
            finally {pendingWrites.decrementAndGet();publish()}
        } } catch(e:RejectedExecutionException) {
            pendingWrites.decrementAndGet();error="保存失败：数据库已关闭";publish()
        }
    }
    fun close(){executor.execute { store.close() };executor.shutdown()}
}
