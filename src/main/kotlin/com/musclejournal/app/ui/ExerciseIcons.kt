package com.musclejournal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.musclejournal.app.domain.Exercise

/** UI symbols for identifying movements; independent of anatomical images and muscle mappings. */
@Composable internal fun ExerciseLineIcon(exercise:Exercise,modifier:Modifier){
    Canvas(modifier.background(Panel,RoundedCornerShape(12.dp)).padding(8.dp).semantics {contentDescription="${exercise.name}动作图标"}){
        val scale=minOf(size.width/100f,size.height/76f)
        withTransform({translate((size.width-100*scale)/2,(size.height-76*scale)/2);scale(scale,scale,Offset.Zero)}){MovementGlyph(this).draw(exercise)}
    }
}
private class MovementGlyph(private val scope:DrawScope){
    private val white=Color(0xFFE0E0E0);private val gray=Color(0xFF808080)
    fun line(x:Float,y:Float,a:Float,b:Float,color:Color=white)=with(scope){drawLine(color,Offset(x,y),Offset(a,b),2.2f,StrokeCap.Round)}
    fun poly(vararg v:Float,color:Color=white){val p=Path();p.moveTo(v[0],v[1]);for(i in 2 until v.size step 2)p.lineTo(v[i],v[i+1]);scope.drawPath(p,color,style=Stroke(2.2f,cap=StrokeCap.Round,join=StrokeJoin.Round))}
    fun circle(x:Float,y:Float,r:Float)=with(scope){drawCircle(white,r,Offset(x,y),style=Stroke(2.2f))}
    private fun arrow(x:Float,y:Float,a:Float,b:Float){line(x,y,a,b,Accent);val dx=a-x;val dy=b-y;val n=kotlin.math.sqrt(dx*dx+dy*dy).coerceAtLeast(1f);val ux=dx/n;val uy=dy/n;poly(a-ux*5-uy*3,b-uy*5+ux*3,a,b,a-ux*5+uy*3,b-uy*5-ux*3,color=Accent)}
    private fun weight(x:Float,y:Float){line(x-7,y,x+7,y);line(x-7,y-5,x-7,y+5);line(x+7,y-5,x+7,y+5)}
    private fun bar(x:Float,y:Float,w:Float=42f){line(x-w/2,y,x+w/2,y);for(side in listOf(-1,1)){line(x+side*w/2,y-8,x+side*w/2,y+8);line(x+side*(w/2-5),y-6,x+side*(w/2-5),y+6)}}
    private fun bench(incline:Boolean=false){poly(21f,if(incline)40f else 49f,55f,51f,75f,51f,color=gray);line(28f,51f,28f,65f,gray);line(68f,51f,68f,65f,gray)}
    private fun standing(){circle(50f,13f,5f);line(50f,19f,50f,43f);poly(50f,43f,39f,66f);poly(50f,43f,61f,66f)}
    private fun machine(){poly(78f,65f,78f,10f,58f,10f,color=gray);line(72f,65f,87f,65f,gray);circle(77f,15f,3f)}
    fun draw(e:Exercise){when(e.id){
        "barbell_bench","incline_press","dumbbell_bench_press"->{val incline=e.id=="incline_press";bench(incline);circle(29f,if(incline)29f else 40f,5f);poly(35f,if(incline)34f else 44f,57f,47f,66f,58f,78f,58f);poly(43f,40f,48f,27f,48f,17f);if(e.id=="dumbbell_bench_press"){weight(46f,15f);weight(67f,19f)}else bar(50f,15f);arrow(84f,39f,84f,19f)}
        "chest_fly"->{standing();poly(26f,60f,26f,22f,35f,22f,color=gray);poly(74f,60f,74f,22f,65f,22f,color=gray);poly(50f,25f,35f,29f,30f,22f);poly(50f,25f,65f,29f,70f,22f);arrow(25f,12f,40f,18f);arrow(75f,12f,60f,18f)}
        "shoulder_press"->{standing();poly(50f,25f,34f,29f,30f,15f);poly(50f,25f,66f,29f,70f,15f);weight(29f,11f);weight(71f,11f);arrow(85f,33f,85f,13f)}
        "lateral_raise"->{standing();poly(50f,25f,29f,29f,18f,32f);poly(50f,25f,71f,29f,82f,32f);weight(17f,33f);weight(83f,33f);arrow(23f,53f,18f,39f);arrow(77f,53f,82f,39f)}
        "rear_fly"->{circle(52f,16f,5f);poly(50f,23f,45f,43f,36f,66f);poly(45f,43f,60f,66f);poly(50f,25f,29f,34f,16f,31f);poly(50f,25f,72f,34f,85f,31f);weight(15f,31f);weight(86f,31f);arrow(21f,54f,16f,39f);arrow(79f,54f,84f,39f)}
        "lat_pulldown"->{machine();circle(43f,30f,5f);poly(43f,36f,43f,53f,58f,53f,61f,67f);line(29f,56f,58f,56f,gray);line(36f,56f,36f,67f,gray);poly(43f,38f,30f,29f,30f,19f);poly(43f,38f,58f,29f,58f,19f);bar(44f,17f,34f);poly(44f,17f,44f,9f,77f,9f,color=gray);arrow(15f,18f,15f,36f)}
        "pullup"->{poly(15f,67f,15f,9f,85f,9f,85f,67f,color=gray);circle(50f,24f,5f);poly(50f,30f,50f,49f,41f,64f);poly(50f,49f,59f,64f);poly(50f,34f,33f,25f,30f,10f);poly(50f,34f,67f,25f,70f,10f);arrow(76f,47f,76f,29f)}
        "cable_row"->{machine();circle(29f,25f,5f);poly(29f,31f,34f,49f,49f,49f,60f,61f);poly(31f,35f,44f,39f,59f,36f);line(59f,36f,78f,36f,gray);line(18f,53f,43f,53f,gray);line(23f,53f,23f,65f,gray);arrow(63f,22f,45f,22f)}
        "one_arm_row"->{bench();circle(37f,24f,5f);poly(42f,29f,61f,36f,65f,51f);poly(61f,36f,77f,63f);line(45f,31f,35f,49f);poly(53f,34f,52f,45f,45f,47f);weight(43f,48f);arrow(19f,47f,19f,30f)}
        "shrug"->{standing();poly(50f,25f,32f,29f,28f,44f);poly(50f,25f,68f,29f,72f,44f);weight(27f,47f);weight(73f,47f);arrow(22f,31f,22f,18f);arrow(78f,31f,78f,18f)}
        "curl","hammer_curl","wrist_curl"->{standing();poly(50f,25f,34f,34f,34f,43f,24f,30f);poly(50f,25f,66f,34f,66f,43f,76f,30f);if(e.id=="hammer_curl"){line(23f,22f,23f,34f);line(18f,22f,28f,22f);line(18f,34f,28f,34f)}else weight(23f,27f);if(e.id=="wrist_curl")arrow(12f,37f,18f,25f)else arrow(15f,48f,15f,33f)}
        "pushdown","overhead_extension"->{machine();standing();if(e.id=="pushdown"){poly(50f,25f,61f,35f,62f,45f);poly(62f,45f,70f,45f);poly(67f,45f,67f,10f,78f,10f,color=gray);arrow(33f,33f,33f,50f)}else{poly(50f,25f,36f,14f,43f,5f);poly(50f,25f,61f,14f,55f,5f);poly(48f,5f,77f,5f,color=gray);arrow(23f,30f,23f,13f)}}
        "squat","bodyweight_squat"->{circle(42f,18f,5f);poly(44f,25f,54f,39f,37f,49f,44f,64f,57f,64f);poly(54f,39f,67f,49f,63f,64f,75f,64f);if(e.id=="squat")bar(43f,28f,54f)else poly(45f,29f,29f,33f,20f,31f);arrow(84f,30f,84f,49f)}
        "leg_press"->{poly(18f,58f,42f,58f,69f,15f,color=gray);poly(18f,38f,25f,53f,42f,53f,color=gray);circle(22f,34f,5f);poly(26f,40f,36f,49f,49f,38f,63f,37f);line(58f,29f,70f,39f,gray);arrow(73f,47f,83f,29f)}
        "leg_extension","leg_curl"->{circle(30f,17f,5f);poly(30f,24f,30f,44f,51f,44f);poly(20f,28f,20f,50f,51f,50f,color=gray);line(24f,50f,24f,66f,gray);if(e.id=="leg_extension"){poly(51f,44f,72f,35f,82f,35f);weight(72f,39f);arrow(65f,62f,78f,48f)}else{poly(51f,44f,48f,63f,37f,63f);weight(48f,62f);arrow(72f,45f,62f,61f)}}
        "romanian_deadlift"->{circle(46f,21f,5f);poly(43f,28f,58f,40f,55f,53f,61f,66f);poly(58f,40f,42f,61f,47f,66f);line(43f,31f,33f,50f);bar(33f,54f,32f);arrow(78f,42f,78f,23f)}
        "hip_thrust"->{bench();circle(25f,35f,5f);poly(31f,40f,53f,39f,68f,52f,68f,66f,78f,66f);bar(54f,36f,35f);arrow(84f,45f,84f,27f)}
        "calf_raise"->{standing();poly(39f,66f,45f,66f,45f,61f);poly(61f,66f,67f,66f,67f,61f);poly(24f,70f,76f,70f,color=gray);weight(28f,40f);weight(72f,40f);poly(50f,25f,31f,30f,28f,37f);poly(50f,25f,69f,30f,72f,37f);arrow(83f,61f,83f,43f)}
        "pushup","plank"->{circle(27f,29f,5f);poly(33f,32f,67f,42f,81f,57f,88f,57f);if(e.id=="pushup"){poly(38f,35f,33f,46f,38f,59f);arrow(17f,45f,17f,29f)}else poly(38f,35f,32f,56f,18f,56f);line(13f,62f,89f,62f,gray)}
        "side_plank"->{circle(25f,25f,5f);poly(30f,31f,55f,41f,83f,57f);poly(35f,33f,29f,57f,16f,57f);poly(43f,36f,51f,20f,55f,7f);line(12f,62f,89f,62f,gray)}
        "crunch"->{circle(35f,35f,5f);poly(41f,39f,54f,51f,70f,51f,64f,32f,79f,51f,88f,51f);poly(44f,42f,31f,47f);line(18f,59f,89f,59f,gray);arrow(19f,38f,25f,25f)}
        "back_extension"->{circle(25f,26f,5f);poly(31f,31f,51f,43f,75f,55f,86f,55f);poly(49f,46f,42f,64f,65f,64f,70f,55f,color=gray);line(37f,35f,38f,45f);arrow(18f,50f,18f,32f)}
        else->{bar(50f,38f,52f);arrow(50f,20f,50f,7f)}
    }}
}
