package com.teamwolf.acuariowallpaper

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.teamwolf.acuariowallpaper.core.ConfigProvider
import com.teamwolf.acuariowallpaper.core.GLRenderThread

/**
 * Runs the real AcuarioRenderer inside a SurfaceView on the settings screen, using the same
 * [GLRenderThread] the wallpaper engine uses (ported from InfiniteRetro's LivePreviewController).
 * The renderer reads [ConfigProvider] every frame and ConfigManager invalidates its cache on every
 * SharedPreferences change, so settings edits show up immediately. Pauses with the screen's
 * lifecycle so it never renders in the background.
 *
 * Touch: a tap is forwarded as a wallpaper touch, a horizontal drag pans the scene like swiping
 * between home-screen pages does on the real wallpaper.
 */
class LivePreviewController(
    private val surfaceView: SurfaceView,
    configProvider: ConfigProvider,
    lifecycleOwner: LifecycleOwner,
) : SurfaceHolder.Callback, DefaultLifecycleObserver {

    private val renderer = AppWallpaperRendererFactory(configProvider).createRenderer(surfaceView.context)
    private var renderThread: GLRenderThread? = null
    private var resumed = false

    private var dragStartX = 0f
    private var dragStartOffset = 0.5f
    private var xOffset = 0.5f

    init {
        surfaceView.holder.addCallback(this)
        lifecycleOwner.lifecycle.addObserver(this)
        attachTouch()
    }

    /** Shake gesture stand-in: lets the user see the "Bubble Pop" effect without shaking the phone. */
    fun triggerBubbleStorm() {
        renderThread?.queueBubbleStorm()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch() {
        surfaceView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Keep the surrounding NestedScrollView from stealing a horizontal pan.
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    dragStartX = event.x
                    dragStartOffset = xOffset
                    renderThread?.queueTouch(event.x, event.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    val w = view.width.toFloat()
                    if (w > 0f) {
                        xOffset = (dragStartOffset - (event.x - dragStartX) / w).coerceIn(0f, 1f)
                        renderThread?.queueOffsets(xOffset, 0.5f)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        stopThread()
        renderThread = GLRenderThread(holder, renderer).apply {
            setVisible(resumed)
            val frame = holder.surfaceFrame
            if (frame.width() > 0 && frame.height() > 0) onSurfaceChanged(frame.width(), frame.height())
            queueOffsets(xOffset, 0.5f)
            start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderThread?.onSurfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = stopThread()

    override fun onResume(owner: LifecycleOwner) {
        resumed = true
        renderThread?.setVisible(true)
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed = false
        renderThread?.setVisible(false)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        surfaceView.holder.removeCallback(this)
        stopThread()
    }

    private fun stopThread() {
        renderThread?.apply {
            shutdown()
            try {
                join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        renderThread = null
    }
}
