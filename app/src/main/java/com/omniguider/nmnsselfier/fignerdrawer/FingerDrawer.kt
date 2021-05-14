package com.omniguider.nmnsselfier.fignerdrawer

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.collections.ArrayList


class FingerDrawer : SurfaceView, SurfaceHolder.Callback {
    constructor(context: Context) : super(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    private val dTag = javaClass.simpleName

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    //drawing path
    private var drawPath: Path? = null
    private var pathMap = Collections.synchronizedList(ArrayList<Triple<Int, Float, Path>>()) //paintColor, path width, path
    //initial color
    private var paintColor = -0x10000

    //drawing and canvas paint
    private var drawPaint: Paint? = null

    //canvas
    private var drawCanvas: Canvas? = null

    //canvas bitmap
    private var canvasBitmap: Bitmap? = null

    //eraser mode
    private var erase = false

    //canvas was draw after canvas is clean
    var wasDraw = false

    //able to draw
    var drawable = false

    //path width scale
    private var widthScale = 0.5f

    private var drawThreadHandler: Handler? = null
    private var drawThread: HandlerThread? = null

    /**
     * 绘图方法，在其中完成具体的过程
     */
    private val draw = Runnable {
        if (!drawable) return@Runnable
        // 锁定画布
        val canvas: Canvas = holder.lockCanvas()

        drawPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawPaint(drawPaint!!)
        //drawCanvas?.drawPaint(drawPaint!!)
        drawPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        //canvas.drawPath(drawPath!!, drawPaint!!)
        synchronized(pathMap) {
            for (index in pathMap.indices) {
                val data = pathMap[index]
                val color = data.first
                val w = data.second
                val path = data.third
                drawPaint?.also { it2 ->
                    it2.strokeWidth = w
                    it2.color = color
                    canvas.drawPath(path, it2)
                } ?: Log.d(dTag, "width:${w} does't with path info")
            }
        }
        //drawCanvas?.drawPath(drawPath!!, drawPaint!!)
        //canvas.drawBitmap(canvasBitmap!!, 0f, 0f, drawPaint)
        Log.d(dTag, "draw. ${pathMap.size}")
        holder.unlockCanvasAndPost(canvas)
    }

    init {
        setZOrderOnTop(true)
        //setZOrderMediaOverlay(true)
        setupDrawing()
        setErase(erase)
        holder.addCallback(this)
    }

    private fun startDrawThread() {
        drawable = true
        drawThread = HandlerThread("${javaClass.simpleName}, draw thread").apply {
            start()
            drawThreadHandler = Handler(looper)
        }
    }

    private fun stopDrawThread() {
        drawable = false
        drawThread?.quitSafely()
        drawThread = null
        drawThreadHandler = null
    }

    private fun setupDrawing() {
        drawable = false
        drawPath = Path()
        drawPaint = Paint()
        drawPaint!!.color = paintColor
        drawPaint!!.isAntiAlias = true
        val w = BASE_STROKE_WIDTH + RANGE_STROKE_WIDTH * widthScale
        drawPaint!!.strokeWidth = w
        drawPaint!!.style = Paint.Style.STROKE
        drawPaint!!.strokeJoin = Paint.Join.ROUND
        drawPaint!!.strokeCap = Paint.Cap.ROUND
        holder.lockCanvas()?.let {
            drawPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            it.drawPaint(drawPaint!!)
            drawPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            holder.unlockCanvasAndPost(it)
        }
        putPath(Triple(paintColor, w, drawPath!!))
        drawable = true
    }

    //*************************************** View assigned size  ****************************************************
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvasBitmap?.also {
            drawCanvas = Canvas(it)
        }
    }

    fun setPathColor(color:Int) {
        drawable = false
        drawThreadHandler?.post {
            paintColor = color
            drawPaint!!.color = paintColor
            val w = BASE_STROKE_WIDTH + RANGE_STROKE_WIDTH * widthScale
            if (checkPath(paintColor, w)) {
                drawPath = getPath(paintColor, w)!!.third
            } else {
                drawPath = Path().apply {
                    //set position to last position
                    moveTo(lastTouchX, lastTouchY)
                }
                putPath(Triple(paintColor, w, drawPath!!))
            }
            drawable = true
        }
    }

    fun setPathWidth(scale: Float) {
        drawable = false
        drawThreadHandler?.post{
            widthScale = scale
            val w = BASE_STROKE_WIDTH + RANGE_STROKE_WIDTH * widthScale
            drawPaint!!.strokeWidth = w
            if (checkPath(paintColor, w)) {
                drawPath = getPath(paintColor, w)!!.third
            } else {
                drawPath = Path().apply {
                    //set position to last position
                    moveTo(lastTouchX, lastTouchY)
                }
                putPath(Triple(paintColor, w, drawPath!!))
            }
            drawable = true
        }
    }

    @Synchronized
    private fun checkPath(color: Int, width:Float): Boolean {
        var hasPath = false
        pathMap.forEach {
            if(it.first == color && it.second == width) {
                hasPath = true
                return@forEach
            }
        }
        return hasPath
    }

    @Synchronized
    private fun getPath(color: Int, width:Float): Triple<Int, Float, Path>?{
        var data :Triple<Int, Float, Path>? = null
        pathMap.forEach {
            if(it.first == color && it.second == width) {
                data = it
                return@forEach
            }
        }
        return data
    }

    @Synchronized
    private fun putPath(data :Triple<Int, Float, Path>) {
        pathMap.add(data)
    }

    @Synchronized
    private fun cleanPaths() {
        pathMap.clear()
    }

    fun isDrawable(drawable:Boolean) {
        this.drawable = drawable
    }

    fun setErase(isErase: Boolean) {
        erase = isErase
        drawPaint = Paint()
        if (erase) {
            setupDrawing()
            val srcColor = 0x00000000
            val mode = PorterDuff.Mode.CLEAR
            val porterDuffColorFilter = PorterDuffColorFilter(srcColor, mode)
            drawPaint!!.colorFilter = porterDuffColorFilter
            drawPaint!!.color = srcColor
            drawPaint!!.xfermode = PorterDuffXfermode(mode)
        } else {
            setupDrawing()
        }
    }

    fun onClear() {
        drawable = false
        drawThreadHandler?.post {
            cleanPaths()
            setupDrawing()
            wasDraw = false
            holder.lockCanvas()?.also {
                it.drawColor(0, PorterDuff.Mode.CLEAR)
                Log.d(dTag, "onClear")
                holder.unlockCanvasAndPost(it)
            }
            drawable = true
        }
    }

    fun getViewBitmap(): Bitmap? = canvasBitmap

    //***************************   respond to touch interaction   **************************************************
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable) {
            val touchX = event.x
            val touchY = event.y
            lastTouchX = touchX
            lastTouchY = touchY
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    drawPath!!.moveTo(touchX, touchY)
                }
                //MotionEvent.ACTION_POINTER_DOWN -> drawPath!!.moveTo(touchX, touchY)
                MotionEvent.ACTION_MOVE -> {
                    drawCanvas!!.drawPath(drawPath!!, drawPaint!!)
                    drawPath!!.lineTo(touchX, touchY)
                    wasDraw = true
                }
                MotionEvent.ACTION_UP -> {
                    drawPath!!.lineTo(touchX, touchY)
                    drawCanvas!!.drawPath(drawPath!!, drawPaint!!)
                }
            }
            drawThreadHandler?.post(draw)
        }
        return drawable
    }

    /**
     * 下面是必须要重写的方法
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.setFormat(PixelFormat.TRANSPARENT)
        //一定要在SurfaceView创建之后启动线程
        startDrawThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvasBitmap?.also {
            drawCanvas = Canvas(it)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        //一定要在SurfaceView销毁之前结束线程
        stopDrawThread()
    }

    companion object {
        //stroke width
        const val BASE_STROKE_WIDTH = 5f
        const val RANGE_STROKE_WIDTH = 30f
    }
}