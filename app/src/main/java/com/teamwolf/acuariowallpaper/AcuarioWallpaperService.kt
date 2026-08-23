package com.teamwolf.acuariowallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.teamwolf.acuariowallpaper.core.ConfigManager
import com.teamwolf.acuariowallpaper.core.GLRenderThread
import com.teamwolf.acuariowallpaper.core.GLRenderer

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
            val targetTiltX = (ax / 9.8f).coerceIn(-1f, 1f)
            val targetTiltY = ((ay - 4.9f) / 4.9f).coerceIn(-1f, 1f)

            val alpha = 0.1f
            smoothedTiltX += alpha * (targetTiltX - smoothedTiltX)
            smoothedTiltY += alpha * (targetTiltY - smoothedTiltY)

            renderThread?.queueSensorValues(smoothedTiltX, smoothedTiltY)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
