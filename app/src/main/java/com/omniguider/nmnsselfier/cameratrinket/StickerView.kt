package com.omniguider.nmnsselfier.cameratrinket

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

class StickerView : AppCompatImageView, OnTouchListener {
    var scaleDiff = 0f
    private var mode = NONE
    private var oldDist = 1f
    private var d = 0f
    var dx = 0f
    var dy = 0f
    var lastX = 0f
    var lastY = 0f
    var angle = 0f

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init(context: Context) {
        setOnTouchListener(this@StickerView)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val view = v as ImageView
        (view.drawable as BitmapDrawable).setAntiAlias(true)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mode = DRAG
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    mode = ZOOM
                }
                d = rotation(event)
            }
            MotionEvent.ACTION_UP -> {
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                Log.d(TAG, "mode:$mode")
                if (mode == DRAG) {
                    dx = event.rawX - lastX
                    dy = event.rawY - lastY
                    view.animate().xBy(dx).setDuration(0).setInterpolator(LinearInterpolator())
                        .start()
                    view.animate().yBy(dy).setDuration(0).setInterpolator(LinearInterpolator())
                        .start()
                } else if (mode == ZOOM) {
                    if (event.pointerCount == 2) {
                        val newRot = rotation(event)
                        angle = newRot - d
                        lastX = event.rawX
                        lastY = event.rawY
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            val scale = newDist / oldDist * view.scaleX
                            if (scale > 0.6) {
                                scaleDiff = scale
                                view.scaleX = scale
                                view.scaleY = scale
                            }
                        }
                        view.animate().rotationBy(angle).setDuration(0)
                            .setInterpolator(LinearInterpolator()).start()
                    }
                }
                lastX = event.rawX
                lastY = event.rawY
            }
        }
        return true
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun rotation(event: MotionEvent): Float {
        val delta_x = (event.getX(0) - event.getX(1)).toDouble()
        val delta_y = (event.getY(0) - event.getY(1)).toDouble()
        val radians = Math.atan2(delta_y, delta_x)
        return Math.toDegrees(radians).toFloat()
    }

    companion object {
        private const val TAG = "StickerView"
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}