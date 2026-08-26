package com.teamwolf.acuariowallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.teamwolf.acuariowallpaper.core.ConfigManager
import com.teamwolf.acuariowallpaper.core.GLRenderThread
import com.teamwolf.acuariowallpaper.core.GLRenderer

// "Bubble Pop" shake gesture tuning - see AcuarioEngine.detectShake()'s doc. Module-level (not a
// companion object) because AcuarioEngine is an `inner class`, which Kotlin doesn't allow to
// declare its own companion object.
// m/s^2, gravity-inclusive - resting/gentle movement stays under ~12-14, an actual shake spikes
// well past 20.
private const val SHAKE_MAGNITUDE_THRESHOLD = 22f
private const val SHAKE_DEBOUNCE_MS = 300L
private const val SHAKE_WINDOW_MS = 2000L
private const val SHAKES_REQUIRED = 3

class AcuarioWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return AcuarioEngine()
    }

    inner class AcuarioEngine : Engine(), SensorEventListener {
        private var renderThread: GLRenderThread? = null
        private lateinit var configManager: ConfigManager
        private lateinit var rendererFactory: AppWallpaperRendererFactory
        private var currentRenderer: GLRenderer? = null
        private var currentHolder: SurfaceHolder? = null
        private var currentWidth = 0
        private var currentHeight = 0

        // Sensor variables for 3D tilt (wire up once the renderer reacts to it).
        private var sensorManager: SensorManager? = null
        private var accelerometer: Sensor? = null
        private var smoothedTiltX = 0f
        private var smoothedTiltY = 0f

        // "Bubble Pop" shake gesture: needs SHAKES_REQUIRED separate acceleration peaks within
        // SHAKE_WINDOW_MS of each other (not just one hard jolt) before it counts as a deliberate
        // shake, so a single bump/pocket knock can't trigger it. Timestamps (elapsedRealtime, ms)
        // of the qualifying peaks seen so far in the current window.
        private val shakeTimestamps = ArrayDeque<Long>()
        private var lastShakeAt = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            configManager = ConfigManager(applicationContext)
            rendererFactory = AppWallpaperRendererFactory(configManager)
            currentRenderer = rendererFactory.createRenderer(applicationContext)

            setTouchEventsEnabled(true)

            sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event != null && event.action == MotionEvent.ACTION_DOWN) {
                renderThread?.queueTouch(event.x, event.y)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xStep: Float,
            yStep: Float,
            xPixels: Int,
            yPixels: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xStep, yStep, xPixels, yPixels)
            renderThread?.queueOffsets(xOffset.coerceIn(0f, 1f), yOffset)
        }

        override fun onDestroy() {
            super.onDestroy()
            sensorManager?.unregisterListener(this)
            stopRenderThread()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            renderThread?.setVisible(visible)

            // Sensor listener was previously never registered at all (only ever unregistered in
            // onDestroy) - onSensorChanged silently never fired, so neither the tilt parallax nor
            // the shake gesture below could ever work. Registered/unregistered alongside
            // visibility (not just once in onCreate) so the sensor stops sampling - and draining
            // battery - while the wallpaper isn't actually shown, same as the render thread itself.
            if (visible) {
                accelerometer?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
            } else {
                sensorManager?.unregisterListener(this)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
            startRenderThread(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            currentWidth = width
            currentHeight = height
            renderThread?.onSurfaceChanged(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentHolder = null
            stopRenderThread()
        }

        private fun startRenderThread(holder: SurfaceHolder) {
            stopRenderThread()
            val renderer = currentRenderer ?: return
            renderThread = GLRenderThread(holder, renderer).apply {
                setVisible(isVisible)
                if (currentWidth > 0 && currentHeight > 0) {
                    onSurfaceChanged(currentWidth, currentHeight)
                }
                start()
            }
        }

        private fun stopRenderThread() {
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

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val targetTiltX = (ax / 9.8f).coerceIn(-1f, 1f)
            val targetTiltY = ((ay - 4.9f) / 4.9f).coerceIn(-1f, 1f)

            val alpha = 0.1f
            smoothedTiltX += alpha * (targetTiltX - smoothedTiltX)
            smoothedTiltY += alpha * (targetTiltY - smoothedTiltY)

            renderThread?.queueSensorValues(smoothedTiltX, smoothedTiltY)

            detectShake(ax, ay, az)
        }

        /**
         * "Bubble Pop": raw (gravity-inclusive) acceleration magnitude spikes well above resting
         * (~9.8 m/s^2) on every real shake - a single sample past [SHAKE_MAGNITUDE_THRESHOLD]
         * counts as one "peak". SHAKE_DEBOUNCE_MS keeps one violent shake from being counted many
         * times over while the sensor stays above threshold for a few consecutive samples;
         * [shakeTimestamps] then only needs [SHAKES_REQUIRED] of those debounced peaks within
         * [SHAKE_WINDOW_MS] of each other to fire - requiring a deliberate few-second shake, not
         * one lucky jolt, before renderer.triggerBubbleStorm() gets called (via GLRenderThread's
         * queue, same as every other cross-thread input here - see queueBubbleStorm()'s doc).
         */
        private fun detectShake(ax: Float, ay: Float, az: Float) {
            val magnitude = kotlin.math.sqrt(ax * ax + ay * ay + az * az)
            if (magnitude < SHAKE_MAGNITUDE_THRESHOLD) return

            val now = SystemClock.elapsedRealtime()
            if (now - lastShakeAt < SHAKE_DEBOUNCE_MS) return
            lastShakeAt = now

            shakeTimestamps.addLast(now)
            while (shakeTimestamps.isNotEmpty() && now - shakeTimestamps.first() > SHAKE_WINDOW_MS) {
                shakeTimestamps.removeFirst()
            }

            if (shakeTimestamps.size >= SHAKES_REQUIRED) {
                shakeTimestamps.clear()
                renderThread?.queueBubbleStorm()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
