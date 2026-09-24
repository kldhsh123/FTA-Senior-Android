package com.fta.senior.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.fta.senior.core.Action
import com.fta.senior.data.ButtonShape
import com.fta.senior.data.OverlaySettings

/** The same view is used in the settings preview and the actual overlay. */
class ActionButtonView(context: Context, val action: Action, private val settings: OverlaySettings) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        contentDescription = action.title
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        val rect = RectF(1f, 1f, width - 1f, height - 1f)
        val radius = when (settings.shape) {
            ButtonShape.CIRCLE, ButtonShape.PILL -> height / 2f
            ButtonShape.ROUNDED -> height * .22f
        }
        paint.style = Paint.Style.FILL
        paint.color = settings.backgroundColor
        if (isPressed) paint.alpha = 190
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = settings.foregroundColor
        paint.strokeWidth = height * .047f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        val unit = height * .4f
        val left = if (settings.showLabels) height * .24f else (width - unit) / 2f
        val top = (height - unit) / 2f
        if (action == Action.STOP_FOREGROUND) {
            canvas.drawArc(RectF(left, top, left + unit, top + unit), -55f, 290f, false, paint)
            canvas.drawLine(left + unit / 2, top - unit * .08f, left + unit / 2, top + unit * .48f, paint)
        } else {
            canvas.drawLine(left + unit * .6f, top, left + unit * .3f, top + unit * .5f, paint)
            val broom = Path().apply {
                moveTo(left + unit * .3f, top + unit * .4f)
                lineTo(left + unit * .85f, top + unit * .7f)
                lineTo(left + unit * .55f, top + unit)
                lineTo(left, top + unit * .7f)
                close()
            }
            canvas.drawPath(broom, paint)
            canvas.drawLine(left + unit * .9f, top + unit, left + unit * 1.05f, top + unit, paint)
        }
        if (settings.showLabels) {
            paint.style = Paint.Style.FILL
            paint.textSize = height * .24f
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            canvas.drawText(if (action == Action.STOP_FOREGROUND) "强停" else "清理", height * .85f,
                height / 2f - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }

    override fun setPressed(pressed: Boolean) { super.setPressed(pressed); invalidate() }
    override fun performClick(): Boolean { super.performClick(); return true }

    companion object {
        fun widthDp(settings: OverlaySettings): Int = when {
            settings.showLabels -> (settings.size * 1.75f).toInt()
            settings.shape == ButtonShape.PILL -> (settings.size * 1.4f).toInt()
            else -> settings.size
        }
    }
}
