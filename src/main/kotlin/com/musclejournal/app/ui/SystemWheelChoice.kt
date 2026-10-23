package com.musclejournal.app.ui

import android.app.AlertDialog
import android.view.ContextThemeWrapper
import android.widget.NumberPicker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*

/** Android's own wheel picker: committing a selection requires the confirmation button. */
@Composable internal fun SystemWheelChoice(label:String,value:String,options:List<String>,change:(String)->Unit){
    val context=LocalContext.current
    val dirty=LocalFormDirty.current
    val currentChange by rememberUpdatedState(change)
    var dialog by remember {mutableStateOf<AlertDialog?>(null)}
    DisposableEffect(Unit){onDispose {dialog?.dismiss()}}
    InlineFieldRow(label,click={
        val values=if(value in options)options else listOf(value)+options
        val themed=ContextThemeWrapper(context,com.musclejournal.app.R.style.Theme_MuscleJournal_Wheel)
        val picker=NumberPicker(themed).apply {
            minValue=0;maxValue=values.lastIndex;displayedValues=values.toTypedArray()
            this.value=values.indexOf(value).coerceAtLeast(0);wrapSelectorWheel=false
            descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS
            contentDescription=label
        }
        dialog=AlertDialog.Builder(themed).setTitle(label).setView(picker)
            .setNegativeButton("取消",null).setPositiveButton("确定"){_,_->dirty();currentChange(values[picker.value])}.create().also {it.show()}
    }){
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Text(value,color=Color.White,fontSize=16.sp,modifier=Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(18.dp),tint=Muted)
        }
    }
}
