package com.teamwolf.acuariowallpaper.core

import android.view.SurfaceHolder

class GLRenderThread(
    private val surfaceHolder: SurfaceHolder,
    private val renderer: GLRenderer
) : Thread("GLRenderThread") {

    private val eglHelper = EglHelper()
    private val lock = Object()

    @Volatile private var running = true
    @Volatile private var visible = false
    @Volatile private var width = 0
    @Volatile private var height = 0
    @Volatile private var surfaceChanged = false
    @Volatile private var pendingTouch: Pair<Float, Float>? = null
    @Volatile private var xOffset = 0.5f
    @Volatile private var yOffset = 0.5f
    @Volatile private var tiltX = 0f
    @Volatile private var tiltY = 0f
    @Volatile private var pendingBubbleStorm = false

    fun setVisible(visible: Boolean) {
        synchronized(lock) {
            this.visible = visible
            lock.notifyAll()
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        synchronized(lock) {
            this.width = width
            this.height = height
            this.surfaceChanged = true
            lock.notifyAll()
        }
    }

    fun queueTouch(x: Float, y: Float) {
        synchronized(lock) {
            pendingTouch = Pair(x, y)
            lock.notifyAll()
        }
    }

    fun queueOffsets(xOffset: Float, yOffset: Float) {
        synchronized(lock) {
            this.xOffset = xOffset
            this.yOffset = yOffset
            lock.notifyAll()
        }
    }

    fun queueSensorValues(tiltX: Float, tiltY: Float) {
        synchronized(lock) {
            this.tiltX = tiltX
            this.tiltY = tiltY
            lock.notifyAll()
        }
    }

    /** Queues a shake-triggered bubble storm - consumed (and cleared) at most once per frame in
     * [run], same one-shot pattern as [pendingTouch]. Safe to call from any thread (the sensor
     * callback runs on the main thread, not this one). */
    fun queueBubbleStorm() {
        synchronized(lock) {
            pendingBubbleStorm = true
            lock.notifyAll()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
    }

    override fun run() {
        try {
            eglHelper.initEgl()

            synchronized(lock) {
                if (surfaceHolder.surface != null && surfaceHolder.surface.isValid) {
                    eglHelper.createSurface(surfaceHolder)
                }
            }

            renderer.onSurfaceCreated()

            var lastTime = System.nanoTime()
            // Last values actually delivered to the renderer - lets the loop below skip
            // onOffsetsChanged()/onSensorValuesChanged() entirely on the (overwhelmingly common)
            // frames where neither has moved since the last one, instead of calling them
            // unconditionally 60x/sec for as long as the wallpaper is visible.
            var lastDeliveredXOffset = xOffset
            var lastDeliveredYOffset = yOffset
            var lastDeliveredTiltX = tiltX
            var lastDeliveredTiltY = tiltY

            while (running) {
                synchronized(lock) {
                    while (running && (!visible || surfaceHolder.surface == null || !surfaceHolder.surface.isValid)) {
                        eglHelper.destroySurface()

                        try {
                            lock.wait()
                        } catch (e: InterruptedException) {
                            // ignore
                        }

                        if (running && visible && surfaceHolder.surface != null && surfaceHolder.surface.isValid) {
                            eglHelper.createSurface(surfaceHolder)
                            eglHelper.makeCurrent()
                            surfaceChanged = true
                        }
                    }
                }

                if (!running) break

                // Snapshot every per-frame input in a single critical section instead of one
                // synchronized block per field - same data, fewer monitor acquisitions on a
                // loop that runs up to 60x/sec for as long as the wallpaper is visible.
                var localWidth = 0
                var localHeight = 0
                var needsViewportUpdate = false
                var localXOffset: Float
                var localYOffset: Float
                var localTiltX: Float
                var localTiltY: Float
                synchronized(lock) {
                    if (surfaceChanged) {
                        localWidth = width
                        localHeight = height
                        needsViewportUpdate = true
                        surfaceChanged = false
                    }
                    localXOffset = xOffset
                    localYOffset = yOffset
                    localTiltX = tiltX
                    localTiltY = tiltY
                }

                if (needsViewportUpdate) {
                    renderer.onSurfaceChanged(localWidth, localHeight)
                }

                val touch = pendingTouch
                if (touch != null) {
                    pendingTouch = null
                    renderer.onTouchEvent(touch.first, touch.second)
                }

                if (pendingBubbleStorm) {
                    pendingBubbleStorm = false
                    renderer.triggerBubbleStorm()
                }

                if (localXOffset != lastDeliveredXOffset || localYOffset != lastDeliveredYOffset) {
                    renderer.onOffsetsChanged(localXOffset, localYOffset)
                    lastDeliveredXOffset = localXOffset
                    lastDeliveredYOffset = localYOffset
                }
                if (localTiltX != lastDeliveredTiltX || localTiltY != lastDeliveredTiltY) {
                    renderer.onSensorValuesChanged(localTiltX, localTiltY)
                    lastDeliveredTiltX = localTiltX
                    lastDeliveredTiltY = localTiltY
                }

                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime
                val clampedDelta = deltaTime.coerceAtMost(0.1f)

                renderer.onUpdate(clampedDelta)
                renderer.onDrawFrame()

                eglHelper.swapBuffers()

                val elapsedMs = (System.nanoTime() - currentTime) / 1_000_000
                val sleepTime = 16 - elapsedMs
                if (sleepTime > 0) {
                    try {
                        sleep(sleepTime)
                    } catch (e: InterruptedException) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            eglHelper.destroySurface()
            eglHelper.destroyEgl()
        }
    }
}
