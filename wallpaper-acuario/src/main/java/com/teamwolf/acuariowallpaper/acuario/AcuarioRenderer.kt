package com.teamwolf.acuariowallpaper.acuario

import android.content.Context
import android.opengl.GLES30
import com.teamwolf.acuariowallpaper.core.ConfigProvider
import com.teamwolf.acuariowallpaper.core.GLRenderer

/**
 * Placeholder renderer for the Acuario effect: currently just clears the screen to a
 * flat "water" color every frame, so the wallpaper is installable and visible end to end
 * while the real scene (background, fish, bubbles, plants, light rays, ...) is built out.
 *
 * Follow the pattern of StormRenderer/SunnyRenderer in the "wallpaper" reference project:
 * compile shaders via [com.teamwolf.acuariowallpaper.core.GLRenderUtils] in
 * [onSurfaceCreated], recompute an orthographic projection in [onSurfaceChanged], advance
 * simulation state in [onUpdate], and issue draw calls in [onDrawFrame].
 */
class AcuarioRenderer(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") configProvider: ConfigProvider
) : GLRenderer {

    override fun onSurfaceCreated() {
        GLES30.glClearColor(0.02f, 0.20f, 0.35f, 1.0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onUpdate(deltaTime: Float) {
        // TODO: advance fish/bubble/plant simulation state once designed.
    }

    override fun onDrawFrame() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }
}
