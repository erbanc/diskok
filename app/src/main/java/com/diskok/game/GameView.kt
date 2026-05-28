package com.diskok.game

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Hosts the game on a dedicated render thread synced to the display via
 * lockCanvas/unlockCanvasAndPost, which keeps motion smooth at the panel's
 * refresh rate (60/90/120 Hz) with a tiny footprint.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private val prefs = context.getSharedPreferences("diskok", Context.MODE_PRIVATE)
    private val engine = Engine(prefs.getInt("level", 0))
    private val vibrator = resolveVibrator(context)

    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        engine.saveLevel = { prefs.edit().putInt("level", it).apply() }
        engine.haptic = ::vibrate
    }

    // MARK: - Surface lifecycle

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "diskok-render").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        synchronized(engine) { engine.configure(w.toFloat(), h.toFloat()) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join() } catch (_: InterruptedException) {}
        thread = null
    }

    // MARK: - Render loop

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000.0).toFloat()
            last = now

            val canvas = holder.lockCanvas() ?: continue
            try {
                synchronized(engine) {
                    engine.update(dt)
                    engine.render(canvas)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    // MARK: - Input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                synchronized(engine) { engine.onPointerDown(x, y) }
            MotionEvent.ACTION_MOVE ->
                synchronized(engine) { engine.onPointerMove(x, y) }
            MotionEvent.ACTION_UP -> {
                synchronized(engine) { engine.onPointerUp(x, y) }
                performClick()
            }
            MotionEvent.ACTION_CANCEL ->
                synchronized(engine) { engine.onPointerUp(x, y) }
            else -> return super.onTouchEvent(event)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // MARK: - Haptics

    private fun vibrate(type: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    1 -> VibrationEffect.createOneShot(16, 150)   // lock
                    2 -> VibrationEffect.createWaveform(
                        longArrayOf(0, 14, 40, 22), intArrayOf(0, 120, 0, 200), -1) // win
                    else -> VibrationEffect.createOneShot(8, 60)  // tick
                }
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(if (type == 0) 8L else 16L)
            }
        } catch (_: Exception) {
            // Haptics are non-essential; never let them crash the game.
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
