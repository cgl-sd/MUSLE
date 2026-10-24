package com.musclejournal.app

import android.app.Instrumentation
import android.os.Bundle
import android.graphics.*
import android.view.MotionEvent
import androidx.core.graphics.PathParser
import com.musclejournal.app.domain.MuscleId
import com.musclejournal.app.rendering.*
import java.io.File

/** Geometry/pixel/input checks against explicitly reviewed landmarks, without opening any database. */
fun runBodyRegionChecks(runner:Instrumentation){
    val lines=mutableListOf<String>();var count=0
    fun verify(name:String,block:()->Unit){try{block()}catch(t:Throwable){throw IllegalStateException(name,t)};count++;lines+="PASS $name"}
    val folder=File(runner.targetContext.cacheDir,"body-regions-v2").apply {mkdirs()}
    try{
        val front=listOf(
            Triple(MuscleId.CHEST,410,380),Triple(MuscleId.SHOULDERS,295,365),Triple(MuscleId.BICEPS,295,500),Triple(MuscleId.FOREARMS,220,630),
            Triple(MuscleId.ABS,475,485),Triple(MuscleId.ABS,475,535),Triple(MuscleId.ABS,475,585),Triple(MuscleId.ABS,480,665),Triple(MuscleId.OBLIQUES,402,602),
            Triple(MuscleId.QUADRICEPS,346,965),Triple(MuscleId.QUADRICEPS,390,950),Triple(MuscleId.QUADRICEPS,430,1025),Triple(MuscleId.CALVES,412,1225))
        val back=listOf(
            Triple(MuscleId.TRAPEZIUS,480,320),Triple(MuscleId.SHOULDERS,300,350),Triple(MuscleId.TRICEPS,300,465),Triple(MuscleId.FOREARMS,214,650),
            Triple(MuscleId.LATS,405,490),Triple(MuscleId.LOWER_BACK,475,620),Triple(MuscleId.HAMSTRINGS,345,955),Triple(MuscleId.HAMSTRINGS,395,965),Triple(MuscleId.HAMSTRINGS,441,1000),
            Triple(MuscleId.CALVES,350,1190),Triple(MuscleId.CALVES,412,1225),Triple(MuscleId.CALVES,335,1310))
        val blankFront=listOf(512 to 380,512 to 535,512 to 622,385 to 1095,639 to 1095,360 to 1400,664 to 1400,512 to 800,190 to 880,834 to 880,512 to 130,410 to 480,350 to 1230,100 to 500)
        val blankBack=listOf(512 to 400,512 to 600,400 to 380,250 to 580,385 to 1090,371 to 1350,512 to 800,350 to 1490,420 to 770,100 to 500)
        for(view in BodyView.entries){
            val outlines=BodyRegions.outlines(view)
            val regions=outlines.map {o->Region().apply {setPath(PathParser.createPathFromPathData(o.data)!!,Region(0,0,1024,1536))}}
            for(label in BodyAnnotations.labels(view)){
                verify("$view annotation ${label.muscle} endpoint matches visible geometry"){
                    val hits=outlines.indices.filter {regions[it].contains(label.x.toInt(),label.y.toInt())}
                    if(label.covered){check(label.muscle==MuscleId.GLUTES && hits.isEmpty())}
                    else check(hits.size==1 && outlines[hits.single()].muscle==label.muscle){"${label.muscle}: $hits"}
                }
            }
            verify("$view has 16 independently authored side regions"){check(outlines.size==16 && outlines.map {it.muscle to it.side}.distinct().size==16)}
            verify("$view distinct muscle-side areas do not overlap"){
                for(i in regions.indices)for(k in 0 until i)check(Region(regions[i]).apply {op(regions[k],Region.Op.INTERSECT)}.isEmpty){"${outlines[i].muscle}/${outlines[i].side} overlaps ${outlines[k].muscle}/${outlines[k].side}"}
            }
            val landmarks=if(view==BodyView.FRONT)front else back
            val blanks=if(view==BodyView.FRONT)blankFront else blankBack
            for((muscle,x,y) in landmarks)for(opposite in listOf(false,true)){
                val px=if(opposite)1024-x else x;val side=if((view==BodyView.FRONT) xor opposite)"右侧"else "左侧"
                verify("$view $muscle $side visible belly at $px,$y"){
                    val hits=outlines.indices.filter {regions[it].contains(px,y)};check(hits.size==1 && outlines[hits.single()].muscle==muscle && outlines[hits.single()].side==side)
                }
            }
            for((x,y) in blanks)verify("$view joint tendon clothes or unclassified surface excluded $x,$y"){check(regions.none {it.contains(x,y)})}
            var mainFailure:Throwable?=null
            runner.runOnMainSync {
                try {
                var hit:Pair<MuscleId,String>?=null
                val body=BodyIllustrationView(runner.targetContext,{m,s->hit=m to s},{error(it)})
                try {
                    for((width,height) in listOf(512 to 768,720 to 1080,900 to 900)){
                        body.layout(0,0,width,height);body.update(view,MuscleRenderState())
                        val reference=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);body.draw(Canvas(reference))
                        val neutral=outlines.map {it.muscle}.distinct().associateWith {MuscleVisualState(normalizedScore=0f)}
                        body.update(view,MuscleRenderState(neutral))
                        val empty=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);body.draw(Canvas(empty))
                        verify("$view $width/$height no training leaves original pixels unchanged"){check(empty.sameAs(reference))}
                        empty.recycle()
                        val trained=outlines.map {it.muscle}.distinct().associateWith {MuscleVisualState(normalizedScore=.6f)}
                        body.update(view,MuscleRenderState(trained))
                        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);body.draw(Canvas(bitmap))
                        val scale=minOf(width/1024f,height/1536f);val ox=(width-1024*scale)/2;val oy=(height-1536*scale)/2
                        fun touch(x:Int,y:Int){hit=null;val px=ox+x*scale;val py=oy+y*scale;val now=android.os.SystemClock.uptimeMillis();val a=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,px,py,0);val b=MotionEvent.obtain(now,now+1,MotionEvent.ACTION_UP,px,py,0);body.onTouchEvent(a);body.onTouchEvent(b);a.recycle();b.recycle()}
                        for((muscle,x,y) in landmarks)for(opposite in listOf(false,true)){
                            val px=if(opposite)1024-x else x;val side=if((view==BodyView.FRONT) xor opposite)"右侧"else "左侧"
                            verify("$view $width/$height touch $muscle $side"){touch(px,y);check(hit==muscle to side){"expected $muscle/$side actual $hit"};val cx=(ox+px*scale).toInt();val cy=(oy+y*scale).toInt();val tint=bitmap.getPixel(cx,cy);check(tint!=reference.getPixel(cx,cy) && Color.red(tint)>Color.green(tint)){"Belly pixel missing red tint"}}
                        }
                        for((x,y) in blanks)verify("$view $width/$height excluded input $x,$y"){touch(x,y);check(hit==null);val cx=(ox+x*scale).toInt();val cy=(oy+y*scale).toInt();check(bitmap.getPixel(cx,cy)==reference.getPixel(cx,cy)){"Tint leaked into excluded surface"}}
                        if(width==720)File(folder,"${view.name.lowercase()}-native.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                        bitmap.recycle();reference.recycle()
                    }
                    body.layout(0,0,1024,1536)
                    val (sampleMuscle,sx,sy)=landmarks.first()
                    fun render(score:Float,selected:MuscleId?=null):Bitmap {
                        body.update(view,MuscleRenderState(mapOf(sampleMuscle to MuscleVisualState(normalizedScore=score,normalizedLeftScore=0f)),selected))
                        return Bitmap.createBitmap(1024,1536,Bitmap.Config.ARGB_8888).also {body.draw(Canvas(it))}
                    }
                    val base=render(0f);val low=render(.2f);val high=render(.8f);val selected=render(.8f,sampleMuscle)
                    val rx=if(view==BodyView.FRONT)sx else 1024-sx
                    val lx=1024-rx
                    verify("$view tint grows with fatigue and respects side"){
                        check(base.getPixel(rx,sy)!=low.getPixel(rx,sy))
                        check(Color.green(high.getPixel(rx,sy))<Color.green(low.getPixel(rx,sy)))
                        check(high.getPixel(lx,sy)==base.getPixel(lx,sy))
                    }
                    verify("$view selecting changes outline only, never belly score"){
                        check(selected.getPixel(rx,sy)==high.getPixel(rx,sy))
                        check(!selected.sameAs(high))
                    }
                    listOf(base,low,high,selected).forEach {it.recycle()}
                    for(muscle in outlines.map {it.muscle}.distinct()){
                        body.update(view,MuscleRenderState(mapOf(muscle to MuscleVisualState(normalizedScore=1f)),muscle,"双侧"))
                        val bitmap=Bitmap.createBitmap(1024,1536,Bitmap.Config.ARGB_8888);body.draw(Canvas(bitmap));File(folder,"${view.name.lowercase()}-${muscle.stableId}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                    }
                }finally{body.release()}
                }catch(t:Throwable){mainFailure=t}
            }
            mainFailure?.let {throw it}
        }
        runner.finish(-1,Bundle().apply {putString("stream",lines.joinToString("\n")+"\n$count checks passed\n");putInt("passed",count)})
    }catch(e:Throwable){runner.finish(0,Bundle().apply {putString("stream",lines.joinToString("\n")+"\nFAIL ${e.stackTraceToString()}\n")})}
}
