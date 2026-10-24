package com.musclejournal.app

import android.app.Instrumentation
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/** Test-process accessibility driver. Does not restart or write the target application's database. */
class UiInputInstrumentation:Instrumentation(){
    private var options=Bundle()
    override fun onCreate(arguments:Bundle?){options=arguments?:Bundle();super.onCreate(arguments);start()}
    override fun onStart(){
        try{
            val nodes=mutableListOf<AccessibilityNodeInfo>()
            fun visit(n:AccessibilityNodeInfo?){if(n==null)return;if(n.className=="android.widget.EditText")nodes+=n;for(i in 0 until n.childCount)visit(n.getChild(i))}
            visit(uiAutomation.rootInActiveWindow)
            val index=options.getString("index")?.toInt()?:0
            check(index in nodes.indices){"Text field $index unavailable (${nodes.size} fields)"}
            val args=Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,options.getString("value")?:"")}
            check(nodes[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){"Text field rejected ACTION_SET_TEXT"}
            finish(0,Bundle().apply {putString("result","PASS")})
        }catch(e:Exception){finish(1,Bundle().apply {putString("error",e.toString())})}
    }
}
