package com.daniell.controller

import android.content.Context
import android.graphics.Canvas
import android.view.View

/**
 * Placeholder interno para manter a posição dos analógicos disponível
 * para uma futura representação gráfica.
 */
class StickView(context: Context) : View(context) {
    var position: Pair<Float, Float> = 0f to 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
