package com.musclejournal.app

import android.app.Instrumentation
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Bundle
import java.io.File
import kotlin.math.hypot

/** Draws the actual packaged launcher resource, never a replacement mockup. */
fun runLauncherIconChecks(runner:Instrumentation){
    val lines=mutableListOf<String>()
    try {
        val ctx=runner.targetContext
        val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeResource(ctx.resources,R.drawable.launcher_art,options)
        check(options.outWidth>=1024 && options.outHeight>=1024)
        lines+="PASS high resolution source ${options.outWidth}x${options.outHeight}"
        val folder=File(ctx.cacheDir,"launcher-icon-v2").apply {mkdirs()}
        val drawable=ctx.getDrawable(R.mipmap.ic_launcher)!!
        check(drawable is AdaptiveIconDrawable)
        for(size in listOf(48,192,512)){
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            drawable.setBounds(0,0,size,size);drawable.draw(Canvas(bitmap))
            var red=0;var outerRed=0;var cream=0
            for(y in 0 until size)for(x in 0 until size){
                val c=bitmap.getPixel(x,y)
                if(Color.alpha(c)<240)continue
                if(Color.red(c)>150 && Color.red(c)>Color.green(c)*1.5 && Color.red(c)>Color.blue(c)*1.4){
                    red++
                    if(hypot((x+.5-size/2.0)/size,(y+.5-size/2.0)/size)>.43)outerRed++
                }
                if(Color.red(c)>205 && Color.green(c)>190 && Color.blue(c)>175)cream++
            }
            check(red>size*size/40){"Dumbbell missing at $size"}
            check(outerRed==0){"Red rim or clipped dumbbell at $size: $outerRed"}
            check(cream>size*size/3){"Ivory surface missing at $size"}
            File(folder,"launcher-$size.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            lines+="PASS actual adaptive launcher $size pixels: red=$red outerRed=$outerRed ivory=$cream"
        }
        runner.finish(-1,Bundle().apply {putString("stream",lines.joinToString("\n")+"\n")})
    }catch(t:Throwable){runner.finish(0,Bundle().apply {putString("stream",lines.joinToString("\n")+"\nFAIL ${t.stackTraceToString()}")})}
}
