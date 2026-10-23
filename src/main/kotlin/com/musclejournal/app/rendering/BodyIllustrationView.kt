package com.musclejournal.app.rendering

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.PathParser
import com.musclejournal.app.domain.MuscleId
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Fixed educational illustration. Regions follow visible muscle-belly boundaries, never physiological weights. */
class BodyIllustrationView(context:Context,private val selected:(MuscleId,String)->Unit,private val failure:(String)->Unit,private val injectFailure:Boolean=false):View(context) {
    private data class Area(val muscle:MuscleId,val side:String,val path:Path,val region:Region)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap:Bitmap?=null;private var view=BodyView.FRONT
    private var areas=emptyList<Area>();private var state=MuscleRenderState(MuscleDisplayMode.SESSION_LOAD)
    private var factor=1f;private var offsetX=0f;private var offsetY=0f
    private var downX=0f;private var downY=0f;private var moved=false
    private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop
    // Runtime silhouette clipping removes the source's surrounding halo; no background is tappable.
    private val frontSilhouette=PathParser.createPathFromPathData("M512,18 C458,17 432,49 431,91 L439,150 L450,177 L447,226 C429,248 394,270 369,284 C313,282 277,306 264,344 C253,373 257,406 251,433 C235,472 229,526 215,562 C195,612 187,678 171,748 L159,792 C149,827 151,867 167,900 L189,932 L220,947 L237,938 L227,915 L204,896 L196,866 L214,876 L233,869 L231,842 L219,804 L210,782 C224,736 245,693 260,653 C275,614 287,580 309,547 C328,516 339,488 344,466 C352,508 365,558 373,603 L382,673 L365,696 L326,872 L333,910 C332,957 335,1004 340,1046 C337,1070 335,1086 336,1104 C326,1151 324,1208 330,1255 L344,1363 L335,1417 L324,1449 C306,1465 286,1484 275,1497 L272,1517 C297,1532 337,1527 369,1516 L391,1500 L395,1476 L382,1437 L382,1397 L399,1325 C411,1275 426,1224 420,1184 L448,1131 L450,1080 C448,1030 468,978 478,934 L490,915 L532,915 L546,934 C556,978 576,1030 574,1080 L576,1131 L604,1184 C598,1224 613,1275 625,1325 L642,1397 L642,1437 L629,1476 L633,1500 L655,1516 C687,1527 727,1532 752,1517 L749,1497 C738,1484 718,1465 700,1449 L689,1417 L680,1363 L694,1255 C700,1208 698,1151 688,1104 C689,1086 687,1070 684,1046 C689,1004 692,957 691,910 L698,872 L659,696 L642,673 L651,603 C659,558 672,508 680,466 C685,488 696,516 715,547 C737,580 749,614 764,653 C779,693 800,736 814,782 L805,804 L793,842 L791,869 L810,876 L828,866 L820,896 L797,915 L787,938 L804,947 L835,932 L857,900 C873,867 875,827 865,792 L853,748 C837,678 829,612 809,562 C795,526 789,472 773,433 C767,406 771,373 760,344 C747,306 711,282 655,284 C630,270 595,248 577,226 L574,177 L585,150 L593,91 C592,49 566,17 512,18 Z")!!
    private val backSilhouette=PathParser.createPathFromPathData("M512,18 C458,17 432,49 431,91 L439,150 L450,177 L447,226 C429,248 394,270 369,284 C313,282 277,306 264,344 C253,373 257,406 251,433 C235,472 229,526 215,562 C195,612 187,678 171,748 L159,792 C149,827 151,867 167,900 L189,932 L220,947 L237,938 L227,915 L204,896 L196,866 L214,876 L233,869 L231,842 L219,804 L210,782 C224,736 245,693 260,653 C275,614 287,580 309,547 C328,516 339,488 344,466 C352,508 365,558 373,603 L382,673 L365,696 L326,872 L333,910 C332,957 335,1004 340,1046 C337,1070 335,1086 336,1104 C318,1151 303,1208 314,1255 C319,1298 330,1340 343,1380 L335,1417 L324,1449 C306,1465 286,1484 275,1497 L272,1517 C297,1532 337,1527 369,1516 L391,1500 L395,1476 L382,1437 L382,1397 L390,1375 C395,1330 411,1300 429,1271 C442,1252 445,1227 436,1200 C428,1174 426,1150 429,1132 L448,1131 L450,1080 C448,1030 468,978 478,934 L490,915 L532,915 L546,934 C556,978 576,1030 574,1080 L576,1131 L595,1132 C598,1150 596,1174 588,1200 C579,1227 582,1252 595,1271 C613,1300 629,1330 634,1375 L642,1397 L642,1437 L629,1476 L633,1500 L655,1516 C687,1527 727,1532 752,1517 L749,1497 C738,1484 718,1465 700,1449 L689,1417 L681,1380 C694,1340 705,1298 710,1255 C721,1208 706,1151 688,1104 C689,1086 687,1070 684,1046 C689,1004 692,957 691,910 L698,872 L659,696 L642,673 L651,603 C659,558 672,508 680,466 C685,488 696,516 715,547 C737,580 749,614 764,653 C779,693 800,736 814,782 L805,804 L793,842 L791,869 L810,876 L828,866 L820,896 L797,915 L787,938 L804,947 L835,932 L857,900 C873,867 875,827 865,792 L853,748 C837,678 829,612 809,562 C795,526 789,472 773,433 C767,406 771,373 760,344 C747,306 711,282 655,284 C630,270 595,248 577,226 L574,177 L585,150 L593,91 C592,49 566,17 512,18 Z")!!
    private val silhouette:Path get()=if(view==BodyView.FRONT)frontSilhouette else backSilhouette
    init {contentDescription="固定角度人体肌群图，点击选择肌群；也可使用肌群列表";isFocusable=true;load()}
    private fun load(){
        bitmap?.recycle();bitmap=null
        try {check(!injectFailure){"测试人体资源不可用"};bitmap=context.assets.open("body-art/${if(view==BodyView.FRONT)"front" else "back"}.png").use {BitmapFactory.decodeStream(it)}?:error("图片不可读取");areas=regions(view)}
        catch(e:Exception){post {failure(e.message?:"人体暂时无法显示")}}
        invalidate()
    }
    fun update(newView:BodyView,newState:MuscleRenderState){if(newView!=view){view=newView;load()};state=newState;invalidate()}
    fun release(){bitmap?.recycle();bitmap=null}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);val image=bitmap?:return
        factor=min(width/1024f,height/1536f);offsetX=(width-1024f*factor)/2;offsetY=(height-1536f*factor)/2
        val saved=canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(factor,factor)
        canvas.clipPath(silhouette)
        val dst=RectF(0f,0f,1024f,1536f);paint.colorFilter=null;paint.alpha=255;paint.style=Paint.Style.FILL
        canvas.drawBitmap(image,null,dst,paint)
        areas.forEach {area->
            val visual=state.muscles[area.muscle]
            val alpha=when(state.mode){
                MuscleDisplayMode.TRAINING_STATE -> {
                    val score=if(area.side=="左侧")visual?.normalizedLeftScore else visual?.normalizedRightScore
                    score?.takeIf {it>0f}?.let {(45+195*it).roundToInt().coerceAtMost(240)}?:0
                }
                MuscleDisplayMode.SESSION_LOAD -> {val count=if(area.side=="左侧")visual?.completedLeftSets?:0 else visual?.completedRightSets?:0;when {count<=0->0;count<=2->140;count<=5->195;else->240}}
                MuscleDisplayMode.PERSONAL_REFERENCE -> {val ratio=if(area.side=="左侧")visual?.personalLeftRatio else visual?.personalRightRatio;when {ratio==null||ratio<0.5f->0;ratio<1f->140;ratio<1.5f->195;else->240}}
                MuscleDisplayMode.RECOVERY_ESTIMATE -> {val score=visual?.normalizedScore;when {score==null->0;score<0.25f->0;score<0.5f->140;score<0.75f->195;else->240}}
            }
            val selectedArea=state.selectedMuscle==area.muscle && (state.selectedSide=="双侧" || state.selectedSide==area.side)
            if(alpha>0){val clip=canvas.save();canvas.clipPath(area.path);paint.colorFilter=PorterDuffColorFilter(if(state.mode==MuscleDisplayMode.TRAINING_STATE)Color.rgb(224,86,89)else Color.rgb(255,65,69),PorterDuff.Mode.MULTIPLY);paint.alpha=alpha;canvas.drawBitmap(image,null,dst,paint);canvas.restoreToCount(clip)}
            if(selectedArea){paint.colorFilter=null;paint.alpha=255;paint.color=if(state.mode==MuscleDisplayMode.TRAINING_STATE)Color.rgb(255,75,79)else Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f/factor;canvas.drawPath(area.path,paint);paint.style=Paint.Style.FILL}
        }
        paint.colorFilter=null;paint.alpha=255;canvas.restoreToCount(saved)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked){MotionEvent.ACTION_DOWN->{downX=event.x;downY=event.y;moved=false};MotionEvent.ACTION_MOVE->if(abs(event.x-downX)+abs(event.y-downY)>touchSlop)moved=true;MotionEvent.ACTION_POINTER_DOWN->moved=true;MotionEvent.ACTION_CANCEL->moved=true
            MotionEvent.ACTION_UP->if(!moved){val x=((event.x-offsetX)/factor).toInt();val y=((event.y-offsetY)/factor).toInt();areas.firstOrNull {it.region.contains(x,y)}?.let {selected(it.muscle,it.side)};performClick()}}
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    private fun regions(v:BodyView):List<Area> = BodyRegions.outlines(v).map {outline->
        val path=PathParser.createPathFromPathData(outline.data)!!
        Area(outline.muscle,outline.side,path,Region().apply {setPath(path,Region(0,0,1024,1536));op(Region().apply {setPath(silhouette,Region(0,0,1024,1536))},Region.Op.INTERSECT)})
    }
}
