package com.musclejournal.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.musclejournal.app.domain.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/** Serial writes publish only after the record transaction has committed. */
class JournalRepository(context: Context, private val name: String = "journal.db") {
    private val store=JournalStore(context.applicationContext,name)
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val pendingWrites=AtomicInteger(0)
    private val activeOperations=ConcurrentHashMap.newKeySet<String>()
    @Volatile var state=Journal();private set
    val busy:Boolean get()=pendingWrites.get()>0
    @Volatile var error:String?=null;private set
    @Volatile var ready=false;private set
    private var readable=true
    private val listeners=mutableSetOf<()->Unit>()
    init { executor.execute {
        try { state=store.load() }
        catch(e:Exception){readable=false;error="读取失败，原始数据库已保留：${e.message}"}
        ready=true;publish()
    } }
    fun observe(listener:()->Unit):()->Unit { synchronized(listeners){listeners.add(listener)};return {synchronized(listeners){listeners.remove(listener)}} }
    private fun publish() { main.post { synchronized(listeners){listeners.toList()}.forEach {runCatching(it)} } }
    fun clearError(){error=null;publish()}
    fun change(after:()->Unit = {}, action:(Journal)->Journal) { submit(null,after,action) }
    fun changeOnce(operationKey:String,after:()->Unit = {},action:(Journal)->Journal):Boolean {
        require(operationKey.isNotBlank()){"保存操作标识不能为空"}
        return submit(operationKey,after,action)
    }
    private fun submit(operationKey:String?,after:()->Unit,action:(Journal)->Journal):Boolean {
        if(operationKey!=null && !activeOperations.add(operationKey))return false
        pendingWrites.incrementAndGet();error=null;publish()
        try { executor.execute {
            try {
                check(readable){"原始数据库读取失败，禁止覆盖。请保留原始数据库文件后修复。"}
                val next=Metrics.reconcile(action(state),System.currentTimeMillis());next.validate()
                store.save(state,next)
                state=next
                main.post { after() }
            } catch(e:Exception){Log.e("JournalRepository","write failed",e);error=e.message?:"保存失败，请重试"}
            finally {
                operationKey?.let(activeOperations::remove)
                pendingWrites.decrementAndGet()
                publish()
            }
        } } catch(e:RejectedExecutionException) {
            operationKey?.let(activeOperations::remove)
            pendingWrites.decrementAndGet();error="保存失败：数据库已关闭";publish()
            return false
        }
        return true
    }
    fun close(){executor.execute { store.close() };executor.shutdown()}
}
