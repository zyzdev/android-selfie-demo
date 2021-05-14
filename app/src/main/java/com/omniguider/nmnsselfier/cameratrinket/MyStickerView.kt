package com.omniguider.nmnsselfier.cameratrinket

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.omniguider.nmnsselfier.R
import kotlin.math.atan2
import kotlin.math.sqrt

class MyStickerView : FrameLayout {

    companion object {
        private const val TAG = "StickerView"
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
    private val hideFunButtonHandler by lazy{
        Handler(Looper.getMainLooper())
    }
    private var scaleDiff = 0f
    private var mode = NONE
    private var oldDist = 1f
    private var d = 0f
    private var dx = 0f
    private var dy = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var angle = 0f

    private val hideFunRunnable = Runnable {
        if (mode == NONE) hideFunButton()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
        initView()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        initView()
    }

    constructor(context: Context?) : super(context!!) {
        initView()
    }

    private fun initView() {
        inflate(context, R.layout.sticker_layout, this)
        //disable function button by default
        val delete = findViewById<ImageView>(R.id.delete)
        delete.isEnabled = false
    }

    fun setImageResource(@DrawableRes drawableRes: Int) {
        findViewById<ImageView>(R.id.trinket).setImageResource(drawableRes)
        findViewById<ImageView>(R.id.trinket).apply {
            setOnTouchListener(stickerViewOnTouchListener)
        }
    }

    fun setDeleteOnClick(listener: OnClickListener) {
        findViewById<ImageView>(R.id.delete).setOnClickListener(listener)
    }

    @SuppressLint("ClickableViewAccessibility")
    val stickerViewOnTouchListener  = OnTouchListener { v, event ->
        val view = v as ImageView
        (view.drawable as BitmapDrawable).setAntiAlias(true)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mode = DRAG
                lastX = event.rawX
                lastY = event.rawY
                cancelDelayToHideFunButton()
                showFunButton()
                Log.d(TAG, "ACTION_DOWN")
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    mode = ZOOM
                }
                d = rotation(event)
                cancelDelayToHideFunButton()
                showFunButton()
                Log.d(TAG, "ACTION_POINTER_DOWN")
            }
            MotionEvent.ACTION_UP -> {
                mode = NONE
                lastX = event.rawX
                lastY = event.rawY
                startDelayToHideFunButton()
                Log.d(TAG, "ACTION_UP")
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                lastX = event.rawX
                lastY = event.rawY
                startDelayToHideFunButton()
                Log.d(TAG, "ACTION_POINTER_UP")
            }
            MotionEvent.ACTION_MOVE -> {
                //Log.d(TAG, "mode:$mode")
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
        true
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun rotation(event: MotionEvent): Float {
        val deltaX = (event.getX(0) - event.getX(1)).toDouble()
        val deltaY = (event.getY(0) - event.getY(1)).toDouble()
        val radians = atan2(deltaY, deltaX)
        return Math.toDegrees(radians).toFloat()
    }

    private fun showFunButton() {
        val delete = findViewById<ImageView>(R.id.delete)
        delete.animate().apply {
            alpha(1f)
            interpolator = FastOutSlowInInterpolator()
            withStartAction {
                delete.isEnabled = true
                delete.visibility = View.VISIBLE
            }
            start()
        }
    }

    private fun hideFunButton() {
        val delete = findViewById<ImageView>(R.id.delete)
        delete.animate().apply {
            alpha(0f)
            interpolator = FastOutSlowInInterpolator()
            withEndAction {
                delete.visibility = View.GONE
            }
            delete.isEnabled = false
            start()
        }
    }
    private fun startDelayToHideFunButton() {
        cancelDelayToHideFunButton()
        hideFunButtonHandler.postDelayed(hideFunRunnable, 3000)
    }
    private fun cancelDelayToHideFunButton() = hideFunButtonHandler.removeCallbacks(hideFunRunnable)
}