package com.teamwolf.acuariowallpaper.acuario

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import com.teamwolf.acuariowallpaper.core.ConfigProvider
import com.teamwolf.acuariowallpaper.core.GLRenderUtils.createProgram
import com.teamwolf.acuariowallpaper.core.GLRenderUtils.readAssetFile
import com.teamwolf.acuariowallpaper.core.GLRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.random.Random

/**
 * First real Acuario effect layer: a procedural underwater background (vertical gradient +
 * animated god rays + caustics, both selectable between the "Acuario" and "Mar abierto"
 * themes via [ConfigProvider.getAcuarioTheme]), a field of bubbles rising from the bottom of
 * the screen, and a single turtle ([Turtle]) wandering around the tank with animated flippers.
 *
 * More fish, plants, sand, etc. are deliberately not here yet - this establishes the
 * rendering pipeline (shader compilation, instanced-quad particles, single transformed-quad
 * creatures) that those will build on, following the same shape as StormRenderer/SunnyRenderer
 * in the "wallpaper" reference project.
 */
class AcuarioRenderer(
    private val context: Context,
    private val configProvider: ConfigProvider
) : GLRenderer {

    // Background (fullscreen gradient + god rays + caustics)
    private var backgroundProgram = 0
    private var bgTimeHandle = 0
    private var bgThemeHandle = 0
    private var bgAspectHandle = 0
    private lateinit var fullscreenQuadBuffer: FloatBuffer

    // Bubbles (instanced quads, same attribute layout as the "wallpaper" reference project's
    // particle.vert/frag)
    private var bubbleProgram = 0
    private var bubbleProjMatrixHandle = 0
    private lateinit var unitQuadBuffer: FloatBuffer
    private lateinit var bubbleInstanceBuffer: FloatBuffer
    private val instanceFloatsPerEntry = 7 // x, y, scale, r, g, b, opacity

    private val maxBubbles = 28
    private val bubbles = mutableListOf<Bubble>()

    // Turtle (single, non-instanced quad transformed via MVP - same shape as moon.vert in the
    // "wallpaper" reference project)
    private var turtleProgram = 0
    private var turtleMVPHandle = 0
    private var turtleSwimPhaseHandle = 0
    private val turtle = Turtle()
    private val kTurtleScale = 0.28f

    private var aspectRatio = 1f
    private var time = 0f
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private class Bubble(
        var x: Float,
        var y: Float,
        val baseX: Float,
        val size: Float,
        val speed: Float,
        val wobbleAmplitude: Float,
        val wobbleSpeed: Float,
        val wobbleSeed: Float,
        val alpha: Float
    )

    override fun onSurfaceCreated() {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        try {
            val vert = readAssetFile(context, "shaders/acuario_background.vert")
            val frag = readAssetFile(context, "shaders/acuario_background.frag")
            backgroundProgram = createProgram(vert, frag)
            bgTimeHandle = GLES30.glGetUniformLocation(backgroundProgram, "uTime")
            bgThemeHandle = GLES30.glGetUniformLocation(backgroundProgram, "uTheme")
            bgAspectHandle = GLES30.glGetUniformLocation(backgroundProgram, "uAspectRatio")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/bubble.vert")
            val frag = readAssetFile(context, "shaders/bubble.frag")
            bubbleProgram = createProgram(vert, frag)
            bubbleProjMatrixHandle = GLES30.glGetUniformLocation(bubbleProgram, "uProjectionMatrix")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/turtle.vert")
            val frag = readAssetFile(context, "shaders/turtle.frag")
            turtleProgram = createProgram(vert, frag)
            turtleMVPHandle = GLES30.glGetUniformLocation(turtleProgram, "uMVPMatrix")
            turtleSwimPhaseHandle = GLES30.glGetUniformLocation(turtleProgram, "uSwimPhase")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fullscreenCoords = floatArrayOf(
            -1f, 1f,
            -1f, -1f,
            1f, 1f,
            1f, -1f
        )
        fullscreenQuadBuffer = ByteBuffer.allocateDirect(fullscreenCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(fullscreenCoords)
                position(0)
            }

        val bubbleQuadCoords = floatArrayOf(
            -0.5f, 0.5f, 0f, 0f,
            -0.5f, -0.5f, 0f, 1f,
            0.5f, 0.5f, 1f, 0f,
            0.5f, -0.5f, 1f, 1f
        )
        unitQuadBuffer = ByteBuffer.allocateDirect(bubbleQuadCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(bubbleQuadCoords)
                position(0)
            }

        bubbleInstanceBuffer = ByteBuffer.allocateDirect(maxBubbles * instanceFloatsPerEntry * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        bubbles.clear()
        repeat(maxBubbles) { bubbles.add(createRandomBubble(spawnAnywhere = true)) }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
        Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)
    }

    override fun onUpdate(deltaTime: Float) {
        time += deltaTime
        turtle.update(deltaTime, aspectRatio)
        for (bubble in bubbles) {
            bubble.y += deltaTime * bubble.speed
            bubble.x = bubble.baseX + kotlin.math.sin(time * bubble.wobbleSpeed + bubble.wobbleSeed) * bubble.wobbleAmplitude
        }
        // Recycle bubbles that drifted past the top edge back to the bottom, instead of
        // reallocating the whole list every frame.
        for (i in bubbles.indices) {
            if (bubbles[i].y > 1.2f) {
                bubbles[i] = createRandomBubble(spawnAnywhere = false)
            }
        }
    }

    override fun onDrawFrame() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawBackground()
        drawTurtle()
        drawBubbles()
    }

    private fun drawTurtle() {
        if (turtleProgram == 0) return
        GLES30.glUseProgram(turtleProgram)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, turtle.x, turtle.y, 0f)
        // facingScale() smoothly passes through 0 as the turtle's heading crosses vertical,
        // so the left/right mirror reads as turning in depth instead of an instant flip.
        Matrix.scaleM(modelMatrix, 0, kTurtleScale * turtle.facingScale(), kTurtleScale, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(turtleMVPHandle, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(turtleSwimPhaseHandle, turtle.swimPhase)

        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun drawBackground() {
        if (backgroundProgram == 0) return
        GLES30.glUseProgram(backgroundProgram)
        GLES30.glUniform1f(bgTimeHandle, time)
        GLES30.glUniform1i(bgThemeHandle, configProvider.getAcuarioTheme())
        GLES30.glUniform1f(bgAspectHandle, aspectRatio)

        fullscreenQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, fullscreenQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
    }

    private fun drawBubbles() {
        if (bubbles.isEmpty() || bubbleProgram == 0) return
        GLES30.glUseProgram(bubbleProgram)
        GLES30.glUniformMatrix4fv(bubbleProjMatrixHandle, 1, false, projectionMatrix, 0)

        bubbleInstanceBuffer.clear()
        for (bubble in bubbles) {
            bubbleInstanceBuffer.put(bubble.x)
            bubbleInstanceBuffer.put(bubble.y)
            bubbleInstanceBuffer.put(bubble.size)
            bubbleInstanceBuffer.put(0.85f) // r
            bubbleInstanceBuffer.put(0.95f) // g
            bubbleInstanceBuffer.put(1.0f)  // b
            bubbleInstanceBuffer.put(bubble.alpha)
        }
        bubbleInstanceBuffer.position(0)

        // Shared quad geometry (locations 0/1, divisor 0 -> same 4 vertices for every instance)
        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        val stride = instanceFloatsPerEntry * 4
        bubbleInstanceBuffer.position(0)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, bubbleInstanceBuffer)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribDivisor(2, 1)

        bubbleInstanceBuffer.position(2)
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, bubbleInstanceBuffer)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribDivisor(3, 1)

        bubbleInstanceBuffer.position(3)
        GLES30.glVertexAttribPointer(4, 3, GLES30.GL_FLOAT, false, stride, bubbleInstanceBuffer)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribDivisor(4, 1)

        bubbleInstanceBuffer.position(6)
        GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, stride, bubbleInstanceBuffer)
        GLES30.glEnableVertexAttribArray(5)
        GLES30.glVertexAttribDivisor(5, 1)

        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, bubbles.size)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(4)
        GLES30.glDisableVertexAttribArray(5)
        GLES30.glVertexAttribDivisor(2, 0)
        GLES30.glVertexAttribDivisor(3, 0)
        GLES30.glVertexAttribDivisor(4, 0)
        GLES30.glVertexAttribDivisor(5, 0)
    }

    /**
     * [spawnAnywhere] places the bubble at a random height (used only to seed the initial
     * field so the first frame isn't empty); recycled bubbles always restart below the
     * bottom edge.
     */
    private fun createRandomBubble(spawnAnywhere: Boolean): Bubble {
        val baseX = Random.nextFloat() * (aspectRatio * 2f) - aspectRatio
        val startY = if (spawnAnywhere) {
            Random.nextFloat() * 2.4f - 1.2f
        } else {
            -1.2f - Random.nextFloat() * 0.3f
        }
        return Bubble(
            x = baseX,
            y = startY,
            baseX = baseX,
            size = 0.02f + Random.nextFloat() * 0.045f,
            speed = 0.12f + Random.nextFloat() * 0.22f,
            wobbleAmplitude = 0.01f + Random.nextFloat() * 0.02f,
            wobbleSpeed = 0.8f + Random.nextFloat() * 1.4f,
            wobbleSeed = Random.nextFloat() * 6.2832f,
            alpha = 0.35f + Random.nextFloat() * 0.45f
        )
    }
}
