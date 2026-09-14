package com.daniell.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class StickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private var x = 0f
    private var y = 0f

    fun setPosition(x: Float, y: Float) {
        this.x = x.coerceIn(-1f, 1f)
        this.y = y.coerceIn(-1f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) * .36f
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawLine(cx-r, cy, cx+r, cy, ring)
        canvas.drawLine(cx, cy-r, cx, cy+r, ring)
        canvas.drawCircle(cx + x*r, cy + y*r, r*.16f, dot)
        canvas.drawText("ANALÓGICO", cx, height - 8f, text)
    }
}
