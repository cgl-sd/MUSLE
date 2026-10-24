package com.musclejournal.app.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.concurrent.ConcurrentHashMap

/** App-lifetime cache for the two full-size body illustrations. */
internal object BodyArt {
    private val cache = ConcurrentHashMap<BodyView, Bitmap>()

    fun get(context: Context, view: BodyView): Bitmap {
        cache[view]?.takeUnless(Bitmap::isRecycled)?.let {return it}
        return synchronized(this) {
            cache[view]?.takeUnless(Bitmap::isRecycled) ?: decode(context, view).also {cache[view] = it}
        }
    }

    fun preload(context: Context) {
        BodyView.entries.forEach {runCatching {get(context.applicationContext,it)}}
    }

    private fun decode(context: Context, view: BodyView): Bitmap =
        context.assets.open("body-art/${if(view==BodyView.FRONT)"front" else "back"}.png").use {
            BitmapFactory.decodeStream(it) ?: error("图片不可读取")
        }
}
