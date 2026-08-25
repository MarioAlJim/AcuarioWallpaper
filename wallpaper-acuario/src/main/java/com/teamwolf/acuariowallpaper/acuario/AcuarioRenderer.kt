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
 * Also 0-8 fish ([Fish], count via [ConfigProvider.getFishCount]) and 0-4 manta rays ([Manta],
 * count via [ConfigProvider.getMantaCount]) - mantas are bigger, slower gliders drawn first/
 * farthest-back among the creatures, each in a randomly-assigned [MantaPalette]. Finally, 0-24
 * anchored plants ([Kelp]/[Anemone], total density via [ConfigProvider.getPlantDensity], split
 * ~65/35 between the two) grow from the tank floor and sway in place rather than wandering -
 * drawn before every creature, right on top of the background.
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
    private var activeBurstCount = 0

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

    // Fish (drawn in back-to-front order by depth, single draw call per fish)
    private var fishProgram = 0
    private var fishMVPHandle = 0
    private var fishSwimPhaseHandle = 0
    private var fishBodyColorHandle = 0
    private var fishFinColorHandle = 0
    private var fishTailColorHandle = 0
    private var fishStripeColorHandle = 0
    private val fishes = mutableListOf<Fish>()
    private val kFishScale = 0.18f
    private val kMaxFish = 8

    // Manta rays (same single-transformed-quad shape as fish/turtles, but bigger and drawn
    // first/farthest-back among the creatures - they're meant to read as big, majestic
    // background gliders)
    private var mantaProgram = 0
    private var mantaMVPHandle = 0
    private var mantaSwimPhaseHandle = 0
    private var mantaBodyColorHandle = 0
    private var mantaWingColorHandle = 0
    private var mantaTailColorHandle = 0
    private var mantaMarkingColorHandle = 0
    private val mantas = mutableListOf<Manta>()
    private val kMantaScale = 0.34f
    private val kMaxMantas = 4

    // Vegetation (kelp + anemones): unlike the wandering creatures above, these are anchored to
    // the tank floor and never move - see layoutPlants()'s comment for why the whole layout is
    // rebuilt from scratch on any density/aspect-ratio change instead of incrementally adjusted.
    private var kelpProgram = 0
    private var kelpMVPHandle = 0
    private var kelpSwayPhaseHandle = 0
    private var kelpBladeColorHandle = 0
    private var kelpTipColorHandle = 0
    private var kelpBaseColorHandle = 0
    private var kelpHighlightColorHandle = 0
    private val kelps = mutableListOf<Kelp>()
    private val kKelpWidth = 0.32f
    private val kKelpBaseHeight = 0.55f

    private var anemoneProgram = 0
    private var anemoneMVPHandle = 0
    private var anemoneSwayPhaseHandle = 0
    private var anemoneFootColorHandle = 0
    private var anemoneTentacleColorHandle = 0
    private var anemoneTipColorHandle = 0
    private var anemoneHighlightColorHandle = 0
    private val anemones = mutableListOf<Anemone>()
    private val kAnemoneSize = 0.30f

    // Fraction of the total plant density budget spent on kelp clumps vs anemones.
    private val kKelpShareOfDensity = 0.65f
    private val kMaxPlantDensity = 24
    // The floor plants are anchored to - low enough that a full-height kelp clump's tip stays
    // comfortably inside the tank rather than poking past the turtles' own roaming floor.
    private val kPlantFloorY = -1.0f
    // Sentinel (an impossible real density) forcing layoutPlants() to run once on the very first
    // syncPlants() call, regardless of what ConfigProvider.getPlantDensity() returns.
    private var currentPlantDensity = -1
    private var plantLayoutAspectRatio = -1f

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
    private val scratchBodyColor = FloatArray(3)
    private val scratchFinColor = FloatArray(3)
    private val scratchTailColor = FloatArray(3)
    private val scratchStripeColor = FloatArray(3)
    private val scratchMantaBodyColor = FloatArray(3)
    private val scratchMantaWingColor = FloatArray(3)
    private val scratchMantaTailColor = FloatArray(3)
    private val scratchMantaMarkingColor = FloatArray(3)
    private val scratchKelpBladeColor = FloatArray(3)
    private val scratchKelpTipColor = FloatArray(3)
    private val scratchKelpBaseColor = FloatArray(3)
    private val scratchKelpHighlightColor = FloatArray(3)
    private val scratchAnemoneFootColor = FloatArray(3)
    private val scratchAnemoneTentacleColor = FloatArray(3)
    private val scratchAnemoneTipColor = FloatArray(3)
    private val scratchAnemoneHighlightColor = FloatArray(3)

    // Mirrors acuario_background.frag's deepColor per theme, so a receding turtle tints
    // toward the same color the background already fades to at depth.
    private val kDeepColorTurquesa = floatArrayOf(0.012f, 0.095f, 0.130f)
    private val kDeepColorAzulProfundo = floatArrayOf(0.010f, 0.045f, 0.130f)
    private val kDeepColorAtardecer = floatArrayOf(0.08f, 0.04f, 0.15f)
    private val kDeepColorAbisal = floatArrayOf(0.01f, 0.01f, 0.04f)
    private val kDeepColorArrecife = floatArrayOf(0.02f, 0.08f, 0.18f)

    private fun getDeepColorForTheme(theme: Int): FloatArray {
        return when (theme) {
            0 -> kDeepColorTurquesa
            1 -> kDeepColorAzulProfundo
            2 -> kDeepColorAtardecer
            3 -> kDeepColorAbisal
            else -> kDeepColorArrecife
        }
    }

    private var aspectRatio = 1f
    private var time = 0f
    private val kTwoPi = (Math.PI * 2.0).toFloat()
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private class Bubble(
        var x: Float = 0f,
        var y: Float = 0f,
        var baseX: Float = 0f,
        var size: Float = 0f,
        var speed: Float = 0f,
        var wobbleAmplitude: Float = 0f,
        var wobbleSpeed: Float = 0f,
        var wobbleSeed: Float = 0f,
        var alpha: Float = 0f
    ) {
        fun reset(
            startX: Float,
            startY: Float,
            size: Float,
            speed: Float,
            wobbleAmplitude: Float,
            wobbleSpeed: Float,
            wobbleSeed: Float,
            alpha: Float
        ) {
            this.x = startX
            this.y = startY
            this.baseX = startX
            this.size = size
            this.speed = speed
            this.wobbleAmplitude = wobbleAmplitude
            this.wobbleSpeed = wobbleSpeed
            this.wobbleSeed = wobbleSeed
            this.alpha = alpha
        }

        fun resetRandom(spawnAnywhere: Boolean, aspectRatio: Float) {
            val rx = Random.nextFloat() * (aspectRatio * 2f) - aspectRatio
            val ry = if (spawnAnywhere) {
                Random.nextFloat() * 2.4f - 1.2f
            } else {
                -1.2f - Random.nextFloat() * 0.3f
            }
            reset(
                startX = rx,
                startY = ry,
                size = 0.02f + Random.nextFloat() * 0.045f,
                speed = 0.12f + Random.nextFloat() * 0.22f,
                wobbleAmplitude = 0.01f + Random.nextFloat() * 0.02f,
                wobbleSpeed = 0.8f + Random.nextFloat() * 1.4f,
                wobbleSeed = Random.nextFloat() * 6.2832f,
                alpha = 0.35f + Random.nextFloat() * 0.45f
            )
        }
    }

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

        try {
            val vert = readAssetFile(context, "shaders/fish.vert")
            val frag = readAssetFile(context, "shaders/fish.frag")
            fishProgram = createProgram(vert, frag)
            fishMVPHandle = GLES30.glGetUniformLocation(fishProgram, "uMVPMatrix")
            fishSwimPhaseHandle = GLES30.glGetUniformLocation(fishProgram, "uSwimPhase")
            fishBodyColorHandle = GLES30.glGetUniformLocation(fishProgram, "uBodyColor")
            fishFinColorHandle = GLES30.glGetUniformLocation(fishProgram, "uFinColor")
            fishTailColorHandle = GLES30.glGetUniformLocation(fishProgram, "uTailColor")
            fishStripeColorHandle = GLES30.glGetUniformLocation(fishProgram, "uStripeColor")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/manta.vert")
            val frag = readAssetFile(context, "shaders/manta.frag")
            mantaProgram = createProgram(vert, frag)
            mantaMVPHandle = GLES30.glGetUniformLocation(mantaProgram, "uMVPMatrix")
            mantaSwimPhaseHandle = GLES30.glGetUniformLocation(mantaProgram, "uSwimPhase")
            mantaBodyColorHandle = GLES30.glGetUniformLocation(mantaProgram, "uBodyColor")
            mantaWingColorHandle = GLES30.glGetUniformLocation(mantaProgram, "uWingColor")
            mantaTailColorHandle = GLES30.glGetUniformLocation(mantaProgram, "uTailColor")
            mantaMarkingColorHandle = GLES30.glGetUniformLocation(mantaProgram, "uMarkingColor")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/kelp.vert")
            val frag = readAssetFile(context, "shaders/kelp.frag")
            kelpProgram = createProgram(vert, frag)
            kelpMVPHandle = GLES30.glGetUniformLocation(kelpProgram, "uMVPMatrix")
            kelpSwayPhaseHandle = GLES30.glGetUniformLocation(kelpProgram, "uSwayPhase")
            kelpBladeColorHandle = GLES30.glGetUniformLocation(kelpProgram, "uBladeColor")
            kelpTipColorHandle = GLES30.glGetUniformLocation(kelpProgram, "uTipColor")
            kelpBaseColorHandle = GLES30.glGetUniformLocation(kelpProgram, "uBaseColor")
            kelpHighlightColorHandle = GLES30.glGetUniformLocation(kelpProgram, "uHighlightColor")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/anemone.vert")
            val frag = readAssetFile(context, "shaders/anemone.frag")
            anemoneProgram = createProgram(vert, frag)
            anemoneMVPHandle = GLES30.glGetUniformLocation(anemoneProgram, "uMVPMatrix")
            anemoneSwayPhaseHandle = GLES30.glGetUniformLocation(anemoneProgram, "uSwayPhase")
            anemoneFootColorHandle = GLES30.glGetUniformLocation(anemoneProgram, "uFootColor")
            anemoneTentacleColorHandle = GLES30.glGetUniformLocation(anemoneProgram, "uTentacleColor")
            anemoneTipColorHandle = GLES30.glGetUniformLocation(anemoneProgram, "uTipColor")
            anemoneHighlightColorHandle = GLES30.glGetUniformLocation(anemoneProgram, "uHighlightColor")
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
        repeat(kMaxBurstBubbles) {
            burstBubbles.add(Bubble())
        }
        activeBurstCount = 0

        turtles.clear()
        syncTurtleCount()

        fishes.clear()
        syncFishCount()

        mantas.clear()
        syncMantaCount()

        kelps.clear()
        anemones.clear()
        currentPlantDensity = -1
        plantLayoutAspectRatio = -1f
        syncPlants()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
        Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)
    }

    override fun onUpdate(deltaTime: Float) {
        time += deltaTime
        syncTurtleCount()
        syncFishCount()
        syncMantaCount()
        syncPlants()
        syncBubbleCount()
        for (i in fishes.indices) {
            fishes[i].update(deltaTime, aspectRatio)
        }
        for (i in mantas.indices) {
            mantas[i].update(deltaTime, aspectRatio)
        }
        for (i in kelps.indices) {
            kelps[i].update(deltaTime)
        }
        for (i in anemones.indices) {
            anemones[i].update(deltaTime)
        }
        for (i in turtles.indices) {
            val t = turtles[i]
            t.update(deltaTime, aspectRatio)
            if (t.consumeExhaleEvent()) {
                spawnExhaleBubbles(t.x, t.y)
            }
            if (t.consumePowerStrokeEvent()) {
                spawnPropulsionBubbles(t)
            }
        }
        for (i in bubbles.indices) {
            val bubble = bubbles[i]
            bubble.y += deltaTime * bubble.speed
            bubble.x = bubble.baseX + kotlin.math.sin(time * bubble.wobbleSpeed + bubble.wobbleSeed) * bubble.wobbleAmplitude
            if (bubble.y > 1.2f) {
                bubble.resetRandom(spawnAnywhere = false, aspectRatio)
            }
        }

        // Burst bubbles (exhale + propulsion) animate the same way, but are one-off - once one
        // drifts off the top it's removed instead of recycled like the ambient water is.
        var i = 0
        while (i < activeBurstCount) {
            val bubble = burstBubbles[i]
            bubble.y += deltaTime * bubble.speed
            bubble.x = bubble.baseX + kotlin.math.sin(time * bubble.wobbleSpeed + bubble.wobbleSeed) * bubble.wobbleAmplitude
            if (bubble.y > 1.2f) {
                if (i < activeBurstCount - 1) {
                    val lastActive = burstBubbles[activeBurstCount - 1]
                    burstBubbles[activeBurstCount - 1] = bubble
                    burstBubbles[i] = lastActive
                }
                activeBurstCount--
            } else {
                i++
            }
        }
    }

    /** [kExhaleBubblesPerBreath] larger, more opaque bubbles released at ([originX], [originY]). */
    private fun spawnExhaleBubbles(originX: Float, originY: Float) {
        repeat(kExhaleBubblesPerBreath) {
            if (activeBurstCount >= kMaxBurstBubbles) return
            val jitteredX = originX + (Random.nextFloat() - 0.5f) * 0.08f
            burstBubbles[activeBurstCount].reset(
                startX = jitteredX,
                startY = originY,
                size = 0.07f + Random.nextFloat() * 0.04f,
                speed = 0.22f + Random.nextFloat() * 0.15f,
                wobbleAmplitude = 0.01f + Random.nextFloat() * 0.015f,
                wobbleSpeed = 0.8f + Random.nextFloat() * 1.4f,
                wobbleSeed = Random.nextFloat() * 6.2832f,
                alpha = 0.6f + Random.nextFloat() * 0.25f
            )
            activeBurstCount++
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
        if (activeBurstCount >= kMaxBurstBubbles) return
        burstBubbles[activeBurstCount].reset(
            startX = originX,
            startY = originY,
            size = 0.012f + Random.nextFloat() * 0.015f,
            speed = 0.18f + Random.nextFloat() * 0.12f,
            wobbleAmplitude = 0.006f + Random.nextFloat() * 0.01f,
            wobbleSpeed = 1.2f + Random.nextFloat() * 1.6f,
            wobbleSeed = Random.nextFloat() * 6.2832f,
            alpha = 0.30f + Random.nextFloat() * 0.25f
        )
        activeBurstCount++
    }

    override fun onDrawFrame() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawBackground()
        drawPlants()
        drawMantas()
        drawFish()
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

    private fun syncFishCount() {
        val desired = configProvider.getFishCount().coerceIn(0, kMaxFish)
        when {
            desired > fishes.size -> repeat(desired - fishes.size) { fishes.add(Fish()) }
            desired < fishes.size -> while (fishes.size > desired) fishes.removeAt(fishes.size - 1)
        }
    }

    private fun drawFish() {
        if (fishProgram == 0 || fishes.isEmpty()) return
        GLES30.glUseProgram(fishProgram)

        // Farthest first, manual insertion sort to avoid allocation
        for (i in 1 until fishes.size) {
            val key = fishes[i]
            var j = i - 1
            while (j >= 0 && fishes[j].depth < key.depth) {
                fishes[j + 1] = fishes[j]
                j--
            }
            fishes[j + 1] = key
        }

        val deepColor = getDeepColorForTheme(configProvider.getAcuarioTheme())

        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        for (i in fishes.indices) {
            val f = fishes[i]
            // Depth-of-field illusion
            val depthScale = kFishScale * (1f - (1f - kMinScaleAtDepth) * f.depth)
            val tintAmount = f.depth * kMaxDepthTint

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, f.x, f.y, 0f)
            Matrix.scaleM(modelMatrix, 0, f.facingScale(), 1f, 1f)
            Matrix.rotateM(modelMatrix, 0, f.pitchDegrees, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(fishMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(fishSwimPhaseHandle, f.swimPhase % kTwoPi)

            val palette = f.palette
            mixColorInto(scratchBodyColor, palette.bodyColor, deepColor, tintAmount)
            mixColorInto(scratchFinColor, palette.finColor, deepColor, tintAmount)
            mixColorInto(scratchTailColor, palette.tailColor, deepColor, tintAmount)
            mixColorInto(scratchStripeColor, palette.stripeColor, deepColor, tintAmount)

            GLES30.glUniform3fv(fishBodyColorHandle, 1, scratchBodyColor, 0)
            GLES30.glUniform3fv(fishFinColorHandle, 1, scratchFinColor, 0)
            GLES30.glUniform3fv(fishTailColorHandle, 1, scratchTailColor, 0)
            GLES30.glUniform3fv(fishStripeColorHandle, 1, scratchStripeColor, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun syncMantaCount() {
        val desired = configProvider.getMantaCount().coerceIn(0, kMaxMantas)
        when {
            desired > mantas.size -> repeat(desired - mantas.size) { mantas.add(Manta()) }
            desired < mantas.size -> while (mantas.size > desired) mantas.removeAt(mantas.size - 1)
        }
    }

    private fun drawMantas() {
        if (mantaProgram == 0 || mantas.isEmpty()) return
        GLES30.glUseProgram(mantaProgram)

        // Farthest first, manual insertion sort to avoid allocation
        for (i in 1 until mantas.size) {
            val key = mantas[i]
            var j = i - 1
            while (j >= 0 && mantas[j].depth < key.depth) {
                mantas[j + 1] = mantas[j]
                j--
            }
            mantas[j + 1] = key
        }

        val deepColor = getDeepColorForTheme(configProvider.getAcuarioTheme())

        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        for (i in mantas.indices) {
            val m = mantas[i]
            // Depth-of-field illusion
            val depthScale = kMantaScale * (1f - (1f - kMinScaleAtDepth) * m.depth)
            val tintAmount = m.depth * kMaxDepthTint

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, m.x, m.y, 0f)
            Matrix.scaleM(modelMatrix, 0, m.facingScale(), 1f, 1f)
            Matrix.rotateM(modelMatrix, 0, m.pitchDegrees, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(mantaMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(mantaSwimPhaseHandle, m.swimPhase % kTwoPi)

            val palette = m.palette
            mixColorInto(scratchMantaBodyColor, palette.bodyColor, deepColor, tintAmount)
            mixColorInto(scratchMantaWingColor, palette.wingColor, deepColor, tintAmount)
            mixColorInto(scratchMantaTailColor, palette.tailColor, deepColor, tintAmount)
            mixColorInto(scratchMantaMarkingColor, palette.markingColor, deepColor, tintAmount)

            GLES30.glUniform3fv(mantaBodyColorHandle, 1, scratchMantaBodyColor, 0)
            GLES30.glUniform3fv(mantaWingColorHandle, 1, scratchMantaWingColor, 0)
            GLES30.glUniform3fv(mantaTailColorHandle, 1, scratchMantaTailColor, 0)
            GLES30.glUniform3fv(mantaMarkingColorHandle, 1, scratchMantaMarkingColor, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    /**
     * Rebuilds [kelps]/[anemones] from scratch whenever [ConfigProvider.getPlantDensity] or
     * [aspectRatio] changes (tracked via [currentPlantDensity]/[plantLayoutAspectRatio]) - unlike
     * the wandering creatures' sync*Count() functions, which only touch the size delta because
     * existing ones keep wandering from wherever they already are, a plant's position IS its
     * whole state: there's nothing worth preserving across a resize, and a full relayout is the
     * simplest way to keep them evenly spread across the (possibly now different) tank width.
     */
    private fun syncPlants() {
        val desired = configProvider.getPlantDensity().coerceIn(0, kMaxPlantDensity)
        if (desired == currentPlantDensity && aspectRatio == plantLayoutAspectRatio) return

        val kelpCount = (desired * kKelpShareOfDensity).toInt()
        val anemoneCount = desired - kelpCount

        kelps.clear()
        repeat(kelpCount) {
            val kelp = Kelp()
            kelp.placeAt(
                x = Random.nextFloat() * (aspectRatio * 1.8f) - aspectRatio * 0.9f,
                depth = Random.nextFloat(),
                heightScale = 0.7f + Random.nextFloat() * 0.6f
            )
            kelps.add(kelp)
        }

        anemones.clear()
        repeat(anemoneCount) {
            val anemone = Anemone()
            anemone.placeAt(
                x = Random.nextFloat() * (aspectRatio * 1.8f) - aspectRatio * 0.9f,
                depth = Random.nextFloat(),
                scale = 0.65f + Random.nextFloat() * 0.5f
            )
            anemones.add(anemone)
        }

        currentPlantDensity = desired
        plantLayoutAspectRatio = aspectRatio
    }

    private fun drawPlants() {
        drawKelp()
        drawAnemones()
    }

    private fun drawKelp() {
        if (kelpProgram == 0 || kelps.isEmpty()) return
        GLES30.glUseProgram(kelpProgram)

        // Farthest first, same back-to-front painter's-algorithm ordering the creatures use.
        for (i in 1 until kelps.size) {
            val key = kelps[i]
            var j = i - 1
            while (j >= 0 && kelps[j].depth < key.depth) {
                kelps[j + 1] = kelps[j]
                j--
            }
            kelps[j + 1] = key
        }

        val deepColor = getDeepColorForTheme(configProvider.getAcuarioTheme())

        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        for (i in kelps.indices) {
            val k = kelps[i]
            val depthScale = 1f - (1f - kMinScaleAtDepth) * k.depth
            val tintAmount = k.depth * kMaxDepthTint
            val width = kKelpWidth * depthScale
            val height = kKelpBaseHeight * k.heightScale * depthScale

            Matrix.setIdentityM(modelMatrix, 0)
            // Anchored at the floor: translate to the clump's vertical center (half its own
            // height above kPlantFloorY) rather than to kPlantFloorY itself, so the *local* quad
            // bottom edge (y = -1 in kelp.frag) lands exactly on the floor instead of the quad's
            // center sitting there.
            Matrix.translateM(modelMatrix, 0, k.x, kPlantFloorY + height * 0.5f, 0f)
            Matrix.scaleM(modelMatrix, 0, width, height, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(kelpMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(kelpSwayPhaseHandle, k.swayPhase % kTwoPi)

            val palette = k.palette
            mixColorInto(scratchKelpBladeColor, palette.bladeColor, deepColor, tintAmount)
            mixColorInto(scratchKelpTipColor, palette.tipColor, deepColor, tintAmount)
            mixColorInto(scratchKelpBaseColor, palette.baseColor, deepColor, tintAmount)
            mixColorInto(scratchKelpHighlightColor, palette.highlightColor, deepColor, tintAmount)

            GLES30.glUniform3fv(kelpBladeColorHandle, 1, scratchKelpBladeColor, 0)
            GLES30.glUniform3fv(kelpTipColorHandle, 1, scratchKelpTipColor, 0)
            GLES30.glUniform3fv(kelpBaseColorHandle, 1, scratchKelpBaseColor, 0)
            GLES30.glUniform3fv(kelpHighlightColorHandle, 1, scratchKelpHighlightColor, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun drawAnemones() {
        if (anemoneProgram == 0 || anemones.isEmpty()) return
        GLES30.glUseProgram(anemoneProgram)

        for (i in 1 until anemones.size) {
            val key = anemones[i]
            var j = i - 1
            while (j >= 0 && anemones[j].depth < key.depth) {
                anemones[j + 1] = anemones[j]
                j--
            }
            anemones[j + 1] = key
        }

        val deepColor = getDeepColorForTheme(configProvider.getAcuarioTheme())

        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        for (i in anemones.indices) {
            val a = anemones[i]
            val depthScale = 1f - (1f - kMinScaleAtDepth) * a.depth
            val tintAmount = a.depth * kMaxDepthTint
            val size = kAnemoneSize * a.scale * depthScale

            Matrix.setIdentityM(modelMatrix, 0)
            // Same floor-anchoring as kelp: the local quad's bottom edge (y = -1 in
            // anemone.frag, where the foot/tentacle bases are anchored) lands on kPlantFloorY.
            Matrix.translateM(modelMatrix, 0, a.x, kPlantFloorY + size * 0.5f, 0f)
            Matrix.scaleM(modelMatrix, 0, size, size, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(anemoneMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(anemoneSwayPhaseHandle, a.swayPhase % kTwoPi)

            val palette = a.palette
            mixColorInto(scratchAnemoneFootColor, palette.footColor, deepColor, tintAmount)
            mixColorInto(scratchAnemoneTentacleColor, palette.tentacleColor, deepColor, tintAmount)
            mixColorInto(scratchAnemoneTipColor, palette.tipColor, deepColor, tintAmount)
            mixColorInto(scratchAnemoneHighlightColor, palette.highlightColor, deepColor, tintAmount)

            GLES30.glUniform3fv(anemoneFootColorHandle, 1, scratchAnemoneFootColor, 0)
            GLES30.glUniform3fv(anemoneTentacleColorHandle, 1, scratchAnemoneTentacleColor, 0)
            GLES30.glUniform3fv(anemoneTipColorHandle, 1, scratchAnemoneTipColor, 0)
            GLES30.glUniform3fv(anemoneHighlightColorHandle, 1, scratchAnemoneHighlightColor, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
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
                bubbles.add(Bubble().apply { resetRandom(spawnNewOnesAnywhere, aspectRatio) })
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
        // Manual insertion sort to avoid any list/comparator allocations per frame.
        for (i in 1 until turtles.size) {
            val key = turtles[i]
            var j = i - 1
            while (j >= 0 && turtles[j].depth < key.depth) {
                turtles[j + 1] = turtles[j]
                j--
            }
            turtles[j + 1] = key
        }

        val deepColor = getDeepColorForTheme(configProvider.getAcuarioTheme())

        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        for (i in turtles.indices) {
            val t = turtles[i]
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
            // Wrapped to [0, 2*PI) before upload: turtle.frag is `precision mediump float`, and
            // t.swimPhase itself grows forever (never resets) for as long as the wallpaper runs.
            // Past a few hours of continuous uptime it's large enough that mediump - roughly a
            // 10-bit mantissa, so its representable step size scales with magnitude - can no
            // longer resolve a single frame's small increment, so consecutive frames round to
            // the *same* value and the animation visibly stalls/steps instead of flowing - worse
            // for the back flippers specifically since their whole motion range is much smaller
            // than the front flippers', so the same absolute rounding error eats a bigger share
            // of it. sin()/cos() only ever need the phase mod 2*PI anyway, so wrapping here (in
            // full 32-bit float, on the CPU) costs nothing and keeps the uploaded value small
            // enough for mediump to represent precisely no matter how long the wallpaper's been
            // running.
            GLES30.glUniform1f(turtleSwimPhaseHandle, t.swimPhase % kTwoPi)

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
        val totalBubbles = bubbles.size + activeBurstCount
        if (totalBubbles == 0 || bubbleProgram == 0) return
        GLES30.glUseProgram(bubbleProgram)
        GLES30.glUniformMatrix4fv(bubbleProjMatrixHandle, 1, false, projectionMatrix, 0)

        bubbleInstanceBuffer.clear()
        for (i in bubbles.indices) {
            val bubble = bubbles[i]
            bubbleInstanceBuffer.put(bubble.x)
            bubbleInstanceBuffer.put(bubble.y)
            bubbleInstanceBuffer.put(bubble.size)
            bubbleInstanceBuffer.put(0.85f) // r
            bubbleInstanceBuffer.put(0.95f) // g
            bubbleInstanceBuffer.put(1.0f)  // b
            bubbleInstanceBuffer.put(bubble.alpha)
        }
        for (i in 0 until activeBurstCount) {
            val bubble = burstBubbles[i]
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
}
