package com.teamwolf.acuariowallpaper.core

interface GLRenderer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onUpdate(deltaTime: Float)
    fun onDrawFrame()
    fun onTouchEvent(x: Float, y: Float) {}
    fun onOffsetsChanged(xOffset: Float, yOffset: Float) {}
    fun onSensorValuesChanged(tiltX: Float, tiltY: Float) {}
    /** Called when the host detects a deliberate device shake - see AcuarioWallpaperService's
     * onSensorChanged for the detection logic. Default no-op so renderers that don't react to
     * shakes (or aren't wired to a shake-capable host) don't need to implement it. */
    fun triggerBubbleStorm() {}
}
