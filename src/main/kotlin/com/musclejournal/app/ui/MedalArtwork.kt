package com.musclejournal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclejournal.app.domain.MedalState

/** Original vector medal series: family silhouette, symbol and engraved milestone number. */
@Composable internal fun MedalArtwork(medal:MedalState,modifier:Modifier){
    val earned=medal.acquiredAt!=null;val family=medal.definition.family
    val light=if(earned)Color(0xFFF0DDA3)else Color(0xFF777777)
    val edge=if(earned)Color(0xFFAA7D38)else Color(0xFF454545)
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center){
        val iconSize=(maxWidth*.3f).coerceIn(18.dp,44.dp)
        val rankSize=(maxWidth.value*.15f).coerceIn(9f,22f).sp
        val centerOffset=-(maxHeight*.12f)
        Canvas(Modifier.fillMaxSize()){
            val k=minOf(size.width/100f,size.height/116f)
            withTransform({translate((size.width-100*k)/2,(size.height-116*k)/2);scale(k,k,Offset.Zero)}){
                val ribbon=if(earned)Color(0xFFE84949)else Color(0xFF424242)
                val back=if(earned)Color(0xFF9C2828)else Color(0xFF303030)
                val left=Path().apply {moveTo(28f,58f);lineTo(18f,111f);lineTo(35f,102f);lineTo(47f,113f);lineTo(52f,63f);close()}
                val right=Path().apply {moveTo(48f,63f);lineTo(53f,113f);lineTo(65f,102f);lineTo(82f,111f);lineTo(72f,58f);close()}
                drawPath(left,ribbon);drawPath(right,back)
                val outline=Path().apply {when(family){
                    "course"->{moveTo(50f,6f);lineTo(84f,17f);lineTo(81f,52f);quadraticTo(76f,71f,50f,83f);quadraticTo(24f,71f,19f,52f);lineTo(16f,17f);close()}
                    "exercise","equipment"->{moveTo(50f,5f);lineTo(84f,24f);lineTo(84f,63f);lineTo(50f,83f);lineTo(16f,63f);lineTo(16f,24f);close()}
                    else->addOval(androidx.compose.ui.geometry.Rect(12f,7f,88f,83f))
                }}
                drawPath(outline,Brush.linearGradient(listOf(light,edge,light),Offset(15f,10f),Offset(82f,80f)))
                drawPath(outline,light,style=Stroke(1.6f))
                drawCircle(if(earned)Color(0xFF292723)else Color(0xFF252525),28f,Offset(50f,44f))
                drawCircle(edge,28f,Offset(50f,44f),style=Stroke(1.4f))
                if(earned){drawLine(Color.White.copy(alpha=.6f),Offset(24f,20f),Offset(36f,13f),2f,StrokeCap.Round)}
            }
        }
        Column(Modifier.fillMaxWidth().offset(y=centerOffset),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp)){
            val icon=when(family){"training"->Icons.Outlined.FitnessCenter;"course"->Icons.Outlined.MenuBook;"exercise"->Icons.Outlined.Explore;"equipment"->Icons.Outlined.Tune;"week"->Icons.Outlined.DateRange;"month"->Icons.Outlined.History;else->Icons.Outlined.FormatListBulleted}
            Icon(icon,null,Modifier.size(iconSize),tint=if(earned)Color(0xFFFFE6AD)else Color(0xFF777777))
            Text(medal.definition.threshold.toString(),fontSize=rankSize,fontWeight=FontWeight.Bold,color=light)
        }
    }
}
