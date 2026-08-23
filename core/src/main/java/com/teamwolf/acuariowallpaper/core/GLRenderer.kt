package com.teamwolf.acuariowallpaper.core

interface GLRenderer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onUpdate(deltaTime: Float)
    fun onDrawFrame()
    fun onTouchEvent(x: Float, y: Float) {}
    fun onOffsetsChanged(xOffset: Float, yOffset: Float) {}
    fun onSensorValuesChanged(tiltX: Float, tiltY: Float) {}
}
