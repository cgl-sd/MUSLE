package com.musclejournal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.musclejournal.app.domain.MuscleId
import com.musclejournal.app.rendering.*
import kotlin.math.min

/** Uses exactly the image fit/centering applied by BodyIllustrationView, without guessed screen coordinates. */
@Composable internal fun BodyAnnotationLayer(view:BodyView,selected:MuscleId?,pick:(MuscleId,String)->Unit,modifier:Modifier=Modifier,rightInset:Dp=0.dp){
    BoxWithConstraints(modifier){
        val density=LocalDensity.current
        val width=with(density){maxWidth.toPx()};val height=with(density){maxHeight.toPx()}
        val rightInsetPx=with(density){rightInset.toPx()}
        val contentRight=width-rightInsetPx
        val scale=min(width/1024f,height/1536f)
        val ox=(width-1024f*scale)/2;val oy=(height-1536f*scale)/2
        val labelWidth=54.dp;val labelHeight=32.dp
        val lw=with(density){labelWidth.toPx()}
        val lh=with(density){labelHeight.toPx()}
        val labels=BodyAnnotations.labels(view)
        Canvas(Modifier.fillMaxSize()){
            labels.forEach {label->
                val active=label.muscle==selected;val color=if(active)Accent else Color(0xFFAAAAAA)
                val start=Offset(ox+label.x*scale,oy+label.y*scale)
                val end=Offset(if(label.left)2.dp.toPx() else contentRight-2.dp.toPx(),oy+label.labelY*scale+lh/2)
                val bend=Offset(if(label.left)lw+4.dp.toPx() else contentRight-lw-4.dp.toPx(),end.y)
                val dash=if(label.covered)PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx()))else null
                drawLine(color,start,bend,0.75.dp.toPx(),pathEffect=dash)
                drawLine(color,bend,end,0.75.dp.toPx(),pathEffect=dash)
                drawCircle(color,2.dp.toPx(),start,style=if(label.covered)androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())else androidx.compose.ui.graphics.drawscope.Fill)
            }
        }
        labels.forEach {label->
            val y=with(density){(oy+label.labelY*scale).toDp()}
            Box(Modifier.offset(x=if(label.left)0.dp else maxWidth-rightInset-labelWidth,y=y).size(labelWidth,labelHeight).clickable {pick(label.muscle,"双侧")},contentAlignment=if(label.left)Alignment.TopStart else Alignment.TopEnd){
                Text(BodyAnnotations.title(label.muscle),fontSize=11.sp,fontWeight=if(label.muscle==selected)FontWeight.Bold else FontWeight.Normal,color=if(label.muscle==selected)Accent else Color.White,maxLines=1,modifier=Modifier.padding(top=1.dp))
            }
        }
    }
}
