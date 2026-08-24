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
 * themes via [ConfigProvider.getAcuarioTheme]), a field of ambient bubbles (count via
 * [ConfigProvider.getBubbleCount]) rising from the bottom of the screen, and 0-5 turtles
 * ([Turtle], count via [ConfigProvider.getTurtleCount]) wandering around the tank with
 * animated flippers, each in a randomly-assigned [TurtlePalette]. Every so often (30-120s)
 * each turtle surfaces to breathe and releases a one-off burst of larger "exhale" bubbles
 * ([Turtle.consumeExhaleEvent]) on the way back down, and on every flap cycle it leaves a
 * small propulsion-bubble trail behind its front flippers ([Turtle.consumePowerStrokeEvent]).
 *
 * More fish, plants, sand, etc. are deliberately not here yet - this establishes the
 * rendering pipeline (shader compilation, instanced-quad particles, single transformed-quad
 * creatures) that those will build on, following the same shape as StormRenderer/SunnyRenderer
 * in the "wallpaper" reference project. Every new effect should ship with its own
 * [ConfigProvider] knob (count/density and/or an enable toggle), the same way bubbles/turtles/
 * theme already do - not as a hardcoded constant only.
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

    // Ambient bubble count is configurable (ConfigProvider.getBubbleCount()); these bounds are
    // just the allocation cap for bubbleInstanceBuffer and a defensive clamp - keep them in
    // sync with AcuarioConfigStore.setBubbleCount()'s coerceIn range.
    private val kMinAmbientBubbles = 4
    private val kMaxAmbientBubbles = 45
    private val bubbles = mutableListOf<Bubble>()

    // "Burst" bubbles: one-off bubbles tied to a specific moment rather than the ambient water
    // - a turtle's "exhale" releasing diving back down after a surface breath (see
    // Turtle.consumeExhaleEvent) and its "propulsion" trail behind the front flippers on each
    // downstroke (see Turtle.consumePowerStrokeEvent). Unlike the ambient `bubbles` above,
    // these are NOT recycled forever - once one drifts off the top of the screen it's simply
    // removed, since each is a momentary event, not a permanent part of the water's atmosphere.
    // kMaxBurstBubbles is a safety cap on the shared instance buffer below, not a tuning knob -
    // sized generously since up to kMaxTurtles turtles can each be adding a small propulsion
    // pair roughly once per stroke cycle (every ~2s), on top of occasional exhale bursts.
    private val kExhaleBubblesPerBreath = 2
    private val kPropulsionBubblesPerStroke = 2
    private val kMaxBurstBubbles = 40
    private val burstBubbles = mutableListOf<Bubble>()

    // Turtles (each a non-instanced quad transformed via its own MVP - same shape as moon.vert
    // in the "wallpaper" reference project; at most kMaxTurtles of them, so one draw call per
    // turtle is simpler than instancing and still cheap)
    private var turtleProgram = 0
    private var turtleMVPHandle = 0
    private var turtleSwimPhaseHandle = 0
    private var turtleShellColorHandle = 0
    private var turtleHeadColorHandle = 0
    private var turtleFlipperColorHandle = 0
    private var turtleSpotColorHandle = 0
    private val turtles = mutableListOf<Turtle>()
    private val kTurtleScale = 0.28f
    private val kMaxTurtles = 5

    // Depth-of-field illusion on an otherwise flat 2D scene: a turtle at Turtle.depth == 1
    // (deep in the tank) is drawn at kMinScaleAtDepth of its normal size and its palette is
    // tinted toward the current theme's deep-water color by up to kMaxDepthTint - see
    // drawTurtles(). Reused per-turtle so tinting 5 turtles/frame doesn't allocate.
    private val kMinScaleAtDepth = 0.45f
    private val kMaxDepthTint = 0.75f
    private val scratchShellColor = FloatArray(3)
    private val scratchHeadColor = FloatArray(3)
    private val scratchFlipperColor = FloatArray(3)
    private val scratchSpotColor = FloatArray(3)

    // Mirrors acuario_background.frag's deepColor per theme, so a receding turtle tints
    // toward the same color the background already fades to at depth.
    private val kDeepColorAcuario = floatArrayOf(0.012f, 0.095f, 0.130f)
    private val kDeepColorMarAbierto = floatArrayOf(0.010f, 0.045f, 0.130f)

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
            turtleShellColorHandle = GLES30.glGetUniformLocation(turtleProgram, "uShellColor")
            turtleHeadColorHandle = GLES30.glGetUniformLocation(turtleProgram, "uHeadColor")
            turtleFlipperColorHandle = GLES30.glGetUniformLocation(turtleProgram, "uFlipperColor")
            turtleSpotColorHandle = GLES30.glGetUniformLocation(turtleProgram, "uSpotColor")
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

        bubbleInstanceBuffer = ByteBuffer.allocateDirect((kMaxAmbientBubbles + kMaxBurstBubbles) * instanceFloatsPerEntry * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        bubbles.clear()
        syncBubbleCount(spawnNewOnesAnywhere = true)
        burstBubbles.clear()

        turtles.clear()
        syncTurtleCount()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
        Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)
    }

    override fun onUpdate(deltaTime: Float) {
        time += deltaTime
        syncTurtleCount()
        syncBubbleCount()
        for (t in turtles) {
            t.update(deltaTime, aspectRatio)
            if (t.consumeExhaleEvent()) {
                spawnExhaleBubbles(t.x, t.y)
            }
            if (t.consumePowerStrokeEvent()) {
                spawnPropulsionBubbles(t)
            }
        }
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

        // Burst bubbles (exhale + propulsion) animate the same way, but are one-off - once one
        // drifts off the top it's removed instead of recycled like the ambient water is.
        val burstIterator = burstBubbles.iterator()
        while (burstIterator.hasNext()) {
            val bubble = burstIterator.next()
            bubble.y += deltaTime * bubble.speed
            bubble.x = bubble.baseX + kotlin.math.sin(time * bubble.wobbleSpeed + bubble.wobbleSeed) * bubble.wobbleAmplitude
            if (bubble.y > 1.2f) {
                burstIterator.remove()
            }
        }
    }

    /** [kExhaleBubblesPerBreath] larger, more opaque bubbles released at ([originX], [originY]). */
    private fun spawnExhaleBubbles(originX: Float, originY: Float) {
        repeat(kExhaleBubblesPerBreath) {
            if (burstBubbles.size >= kMaxBurstBubbles) return
            val jitteredX = originX + (Random.nextFloat() - 0.5f) * 0.08f
            burstBubbles.add(
                Bubble(
                    x = jitteredX,
                    y = originY,
                    baseX = jitteredX,
                    size = 0.07f + Random.nextFloat() * 0.04f,
                    speed = 0.22f + Random.nextFloat() * 0.15f,
                    wobbleAmplitude = 0.01f + Random.nextFloat() * 0.015f,
                    wobbleSpeed = 0.8f + Random.nextFloat() * 1.4f,
                    wobbleSeed = Random.nextFloat() * 6.2832f,
                    alpha = 0.6f + Random.nextFloat() * 0.25f
                )
            )
        }
    }

    /**
     * [kPropulsionBubblesPerStroke] small, subtle bubbles released right at [t]'s front-top and
     * front-bottom flippers - the "push" of a downstroke - reinforcing the effort of swimming.
     */
    private fun spawnPropulsionBubbles(t: Turtle) {
        val (topX, topY) = t.frontFlipperWorldPosition(top = true, turtleScale = kTurtleScale)
        val (botX, botY) = t.frontFlipperWorldPosition(top = false, turtleScale = kTurtleScale)
        spawnPropulsionBubbleAt(topX, topY)
        spawnPropulsionBubbleAt(botX, botY)
    }

    private fun spawnPropulsionBubbleAt(originX: Float, originY: Float) {
        if (burstBubbles.size >= kMaxBurstBubbles) return
        burstBubbles.add(
            Bubble(
                x = originX,
                y = originY,
                baseX = originX,
                size = 0.012f + Random.nextFloat() * 0.015f,
                speed = 0.18f + Random.nextFloat() * 0.12f,
                wobbleAmplitude = 0.006f + Random.nextFloat() * 0.01f,
                wobbleSpeed = 1.2f + Random.nextFloat() * 1.6f,
                wobbleSeed = Random.nextFloat() * 6.2832f,
                alpha = 0.30f + Random.nextFloat() * 0.25f
            )
        )
    }

    override fun onDrawFrame() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawBackground()
        drawTurtles()
        drawBubbles()
    }

    /**
     * Grows/shrinks [turtles] to match [ConfigProvider.getTurtleCount] (clamped to
     * [kMaxTurtles]). Only the size delta is touched - existing turtles keep their position/
     * palette/pitch when the count changes, new ones are freshly created, and a decrease just
     * drops turtles off the end.
     */
    private fun syncTurtleCount() {
        val desired = configProvider.getTurtleCount().coerceIn(0, kMaxTurtles)
        when {
            desired > turtles.size -> repeat(desired - turtles.size) { turtles.add(Turtle()) }
            desired < turtles.size -> while (turtles.size > desired) turtles.removeAt(turtles.size - 1)
        }
    }

    /**
     * Grows/shrinks [bubbles] to match [ConfigProvider.getBubbleCount] (clamped to
     * [kMinAmbientBubbles]/[kMaxAmbientBubbles]). Only the size delta is touched - a decrease
     * just drops bubbles off the end; an increase adds new ones from below like any recycled
     * bubble ([spawnNewOnesAnywhere] is only used once, to seed the initial field in
     * onSurfaceCreated so the very first frame isn't empty).
     */
    private fun syncBubbleCount(spawnNewOnesAnywhere: Boolean = false) {
        val desired = configProvider.getBubbleCount().coerceIn(kMinAmbientBubbles, kMaxAmbientBubbles)
        when {
            desired > bubbles.size -> repeat(desired - bubbles.size) {
                bubbles.add(createRandomBubble(spawnAnywhere = spawnNewOnesAnywhere))
            }
            desired < bubbles.size -> while (bubbles.size > desired) bubbles.removeAt(bubbles.size - 1)
        }
    }

    private fun drawTurtles() {
        if (turtleProgram == 0 || turtles.isEmpty()) return
        GLES30.glUseProgram(turtleProgram)

        // Farthest first, so a turtle nearer the glass correctly draws on top of one that
        // overlaps it deeper in the tank (there's no depth buffer test here - just simple
        // back-to-front painter's-algorithm ordering by Turtle.depth).
        turtles.sortByDescending { it.depth }

        val deepColor = if (configProvider.getAcuarioTheme() == 0) kDeepColorAcuario else kDeepColorMarAbierto

        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        for (t in turtles) {
            // Depth illusion: shrink and tint toward the water's deep color as the turtle
            // recedes (Turtle.depth -> 1), so it reads as farther away/underwater-hazier
            // instead of just smaller.
            val depthScale = kTurtleScale * (1f - (1f - kMinScaleAtDepth) * t.depth)
            val tintAmount = t.depth * kMaxDepthTint

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, t.x, t.y, 0f)
            // Order matters here: Android's Matrix helpers post-multiply, so the LAST call
            // below is the FIRST one actually applied to each vertex. We want, in per-vertex
            // apply order: (1) uniform base scale, (2) pitch rotation (in the sprite's
            // canonical always-facing-right frame, so +pitch always lifts the head), (3) the
            // left/right mirror (only flips X, so it can't undo the vertical lift added by
            // pitch), (4) the translate to world position - hence the calls are written in the
            // reverse of that.
            Matrix.scaleM(modelMatrix, 0, t.facingScale(), 1f, 1f)
            Matrix.rotateM(modelMatrix, 0, t.pitchDegrees, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(turtleMVPHandle, 1, false, mvpMatrix, 0)
            GLES30.glUniform1f(turtleSwimPhaseHandle, t.swimPhase)

            val palette = t.palette
            mixColorInto(scratchShellColor, palette.shellColor, deepColor, tintAmount)
            mixColorInto(scratchHeadColor, palette.headColor, deepColor, tintAmount)
            mixColorInto(scratchFlipperColor, palette.flipperColor, deepColor, tintAmount)
            mixColorInto(scratchSpotColor, palette.spotColor, deepColor, tintAmount)
            GLES30.glUniform3fv(turtleShellColorHandle, 1, scratchShellColor, 0)
            GLES30.glUniform3fv(turtleHeadColorHandle, 1, scratchHeadColor, 0)
            GLES30.glUniform3fv(turtleFlipperColorHandle, 1, scratchFlipperColor, 0)
            GLES30.glUniform3fv(turtleSpotColorHandle, 1, scratchSpotColor, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    /** Writes `mix(from, toward, amount)` component-wise into [out] (avoids a per-call allocation). */
    private fun mixColorInto(out: FloatArray, from: FloatArray, toward: FloatArray, amount: Float) {
        for (i in 0..2) {
            out[i] = from[i] + (toward[i] - from[i]) * amount
        }
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
        val totalBubbles = bubbles.size + burstBubbles.size
        if (totalBubbles == 0 || bubbleProgram == 0) return
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
        for (bubble in burstBubbles) {
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

        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, totalBubbles)

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
