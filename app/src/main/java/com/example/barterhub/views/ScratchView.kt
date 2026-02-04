package com.example.barterhub.views

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.barterhub.R

class ScratchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 🔥 SCRATCH PAINT
    private val scratchPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 80f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val path = Path()
    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null

    var onScratchListener: OnScratchListener? = null
    private var isRevealed = false

    // 🔊 SoundPool for reveal sound
    private val soundPool: SoundPool
    private val revealSoundId: Int

    interface OnScratchListener {
        fun onScratchStarted()
        fun onScratchProgress(progress: Float)
        fun onScratchComplete()
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttrs)
            .build()

        // Sound played when scratch completes
        revealSoundId = soundPool.load(context, R.raw.mystery_sound, 1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetScratch()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        overlayBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.reset()
                path.moveTo(x, y)
                onScratchListener?.onScratchStarted()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                overlayCanvas?.drawPath(path, scratchPaint)
                invalidate()

                val progress = calculateScratchProgress()
                onScratchListener?.onScratchProgress(progress)

                // Play sound only once when scratch is complete
                if (progress >= 0.30f && !isRevealed) {
                    isRevealed = true
                    onScratchListener?.onScratchComplete()
                    playRevealSound()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                path.reset()
            }
        }
        return true
    }

    private fun calculateScratchProgress(): Float {
        val bmp = overlayBitmap ?: return 0f
        var cleared = 0
        var total = 0
        for (x in 0 until bmp.width step 8) {
            for (y in 0 until bmp.height step 8) {
                total++
                if (bmp.getPixel(x, y) == Color.TRANSPARENT) cleared++
            }
        }
        return cleared.toFloat() / total
    }

    fun resetScratch() {
        overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        overlayCanvas = Canvas(overlayBitmap!!)

        // Semi-transparent overlay (70% alpha)
        val overlayPaint = Paint().apply {
            color = Color.parseColor("#FF4B5563") // semi-transparent gray
            style = Paint.Style.FILL
        }
        overlayCanvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Text sa ibabaw
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        overlayCanvas?.drawText("SCRATCH HERE", width / 2f, height / 2f, textPaint)

        isRevealed = false
        path.reset()
        invalidate()
    }

    fun setScratchEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    private fun playRevealSound() {
        soundPool.play(revealSoundId, 1f, 1f, 1, 0, 1f)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundPool.release()
    }
}
