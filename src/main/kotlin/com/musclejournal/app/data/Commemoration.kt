package com.musclejournal.app.data

import android.content.res.AssetManager
import android.graphics.*
import com.musclejournal.app.domain.Achievement
import com.musclejournal.app.domain.Goal
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The exported card uses the same illustrations, palette and facts as its on-screen preview. */
object Commemoration {
    fun write(a:Achievement,g:Goal?,output:OutputStream,assets:AssetManager?=null,progress:Double?=null){
        val bitmap=Bitmap.createBitmap(1080,1740,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(21,21,21))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            fun image(path:String,target:RectF){if(assets==null)return;val source=assets.open(path).use {BitmapFactory.decodeStream(it)}?:return;try {val scale=minOf(target.width()/source.width,target.height()/source.height);val w=source.width*scale;val h=source.height*scale;val left=target.centerX()-w/2;val top=target.centerY()-h/2;canvas.drawBitmap(source,null,RectF(left,top,left+w,top+h),paint)}finally {source.recycle()}}
            fun text(value:String,y:Float,size:Float,color:Int=Color.WHITE,bold:Boolean=false,center:Boolean=true){paint.color=color;paint.textSize=size;paint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT;canvas.drawText(value,if(center)540f else 72f,y,paint)}
            text("养肌宝",120f,64f,bold=true,center=false)
            image("ui-art/memory-hero.png",RectF(72f,160f,1008f,1240f))
            a.title.chunked(13).take(2).forEachIndexed {i,line->text(line,1330f+i*64,62f,bold=true)}
            val red=Color.rgb(240,82,82);val gray=Color.rgb(176,176,176)
            if(g!=null){val unit=when(g.metric){"sessions"->"场";"muscle"->"组";"load"->"kg";else->"次"};fun number(n:Double)=String.format(Locale.ROOT,"%.1f",n).removeSuffix(".0")
                if(progress!=null)text("${number(progress)} / ${number(g.target)} $unit",1460f,80f,if(a.valid)red else gray,true)
                text("${g.start} — ${g.end}",1550f,38f,gray)
            }
            val date=Instant.ofEpochMilli(a.awardedAt).atZone(ZoneId.of(g?.zone?:ZoneId.systemDefault().id)).toLocalDate()
            text("完成于 $date",1620f,34f,gray)
            check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)){"纪念图写入失败"}
        }finally {bitmap.recycle()}
    }
}
