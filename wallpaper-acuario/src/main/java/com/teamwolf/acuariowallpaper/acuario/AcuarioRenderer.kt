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
import kotlin.math.exp
import kotlin.random.Random

/**
 * First real Acuario effect layer: a procedural underwater background (vertical gradient +
 * animated god rays + caustics, both selectable between the "Acuario" and "Mar abierto"
 * themes via [ConfigProvider.getAcuarioTheme]), a field of ambient bubbles (count via
 * [ConfigProvider.getBubbleCount]) rising from the bottom of the screen, and 0-5 turtles
 * ([Turtle], count via [ConfigProvider.getTurtleCount]) wandering around the tank with
 * animated flippers, each in a randomly-assigned [TurtlePalette]. Every so often (30-120s)
 * each turtle surfaces to breathe and releases a one-off burst of larger "exhale" bubbles
 * ([Turtle.consumeExhaleEvent]) on the way back down, and occasionally (~30% of flap cycles,
 * 1-2 bubbles - see kPropulsionBubbleChance) leaves a small propulsion puff behind a front
 * flipper ([Turtle.consumePowerStrokeEvent]).
 * Also 0-8 fish ([Fish], count via [ConfigProvider.getFishCount]) and 0-4 manta rays ([Manta],
 * count via [ConfigProvider.getMantaCount]) - mantas are bigger, slower gliders drawn first/
 * farthest-back among the creatures, each in a randomly-assigned [MantaPalette]. And 0-6 jellyfish
 * ([Jellyfish], count via [ConfigProvider.getJellyfishCount]) that pulse their bell and otherwise
 * drift on the current rather than steering like every other creature here. Finally, 0-24
 * anchored plants ([Kelp]/[Anemone]/[SeaGrass]/[Coral], total density via
 * [ConfigProvider.getPlantDensity], split 35/20/30/15 between them) grow from the tank floor and
 * sway in place rather than wandering - drawn before every creature, right on top of the
 * background. Coral barely sways at all (real coral is far stiffer than an anemone), sea grass
 * is short and sways quickly, kelp is tall and sways slowly.
 *
 * Parallax: swiping between home screens (see [onOffsetsChanged]) pans every wandering
 * creature/plant/bubble sideways by [foregroundParallax], while acuario_background.frag's own
 * distant rock/kelp/animal silhouette layer shifts by only a small fraction of that same swipe -
 * see the fields' own doc comment. The mismatch in how far each layer moves for the same swipe
 * is what sells the tank as far deeper than the single flat plane it actually is.
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
    private var bgParallaxHandle = 0
    private var bgDayNightHandle = 0
    private var bgCustomShallowHandle = 0
    private var bgCustomDeepHandle = 0
    private var bgStormIntensityHandle = 0
    private var dayNight = 1.0f
    private lateinit var fullscreenQuadBuffer: FloatBuffer

    // "Storm at the Surface": how visually agitated acuario_background.frag currently reads
    // (0 = calm, 1 = full storm) - driven by the exact same triggerBubbleStorm() the bubble burst
    // already uses (see its own cooldown/dedup gating in kBubbleStormCooldownSeconds's doc), NOT
    // tied to how long the storm's 100 bubbles themselves take to rise off-screen - this visual
    // flourish is meant to read as one short, punchy squall, not linger for as long as the
    // bubbles are still clearing. stormVisualIntensity is an attack/sustain/release envelope over
    // stormTimeRemaining (same shape as jellyfish.frag's sizeEnvelope k1/k2/k3), computed once per
    // frame in onUpdate() and uploaded to the background shader in drawBackground().
    private val kStormVisualDuration = 6f
    private val kStormFadeInTime = 0.35f
    private val kStormFadeOutTime = 2.0f
    private var stormTimeRemaining = 0f
    private var stormVisualIntensity = 0f

    // Parallax (depth-of-tank illusion driven by home-screen swiping): [offsetX] is the eased,
    // per-frame home-screen scroll position (0..1, 0.5 = centered/no swipe), smoothed from
    // [targetOffsetX] - the raw value the launcher reports via onOffsetsChanged() - so an
    // instantaneous jump (e.g. tapping an app icon snaps the position immediately, rather than a
    // drag) glides briefly instead of snapping the whole scene sideways. [parallaxRaw] (that
    // eased value re-centered to [-0.5, 0.5]) is uploaded to acuario_background.frag, which
    // shifts its own distant rock/kelp/animal silhouette layer by only a small fraction of it -
    // see that shader's comment. [foregroundParallax] is the much larger world-space shift
    // applied to every wandering creature/plant/bubble's own x position at draw time (not their
    // logical x - this is purely a render-time camera pan, so it can never affect movement/
    // wander-bounds logic), so the foreground visibly shifts while the background barely moves -
    // the core parallax cue that the tank is far deeper than the single flat plane it actually is.
    private var offsetX = 0.5f
    private var targetOffsetX = 0.5f
    private var parallaxRaw = 0f
    private var foregroundParallax = 0f
    private val kForegroundParallaxRange = 0.10f
    // Chosen so offsetX is ~95% of the way to a new swipe position in about 0.3s
    // (1 - e^(-kParallaxEaseRate*0.3) ~= 0.95).
    private val kParallaxEaseRate = 10f

    private fun getParallaxX(depth: Float): Float {
        val rangeX = aspectRatio * (0.45f - 0.22f * depth)
        return parallaxRaw * rangeX
    }

    private fun getParallaxY(depth: Float): Float = 0f

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
    // kMaxBurstBubbles is a safety cap on the shared instance buffer below - sized to comfortably
    // fit one full kBubbleStormCount storm (see triggerBubbleStorm()) PLUS some headroom for
    // ordinary exhale/propulsion bursts that might land mid-storm, not just those two on their
    // own (which alone would fit in far fewer).
    private val kExhaleBubblesPerBreath = 2
    // Chance, per power stroke, that a propulsion bubble is spawned at all - firing on every
    // single stroke across every turtle read as constant bubble clutter saturating the screen,
    // so this makes the trail an occasional flourish (1-2 bubbles, see spawnPropulsionBubbles())
    // instead of a guaranteed one every ~2s per turtle.
    private val kPropulsionBubbleChance = 0.30f
    private val kMaxBurstBubbles = 130
    private val burstBubbles = mutableListOf<Bubble>()
    private var activeBurstCount = 0

    // "Bubble Pop" shake gesture (see GLRenderer.triggerBubbleStorm's doc and
    // AcuarioWallpaperService.detectShake()): a one-off burst of kBubbleStormCount bubbles from
    // across the tank floor. Gated so a flurry of shakes can't re-trigger it while the previous
    // storm is still on screen or saturate the burst pool - a new storm needs BOTH
    // kBubbleStormCooldownSeconds to have passed AND every bubble from the last storm to have
    // already risen off-screen (activeStormBubbleCount back to 0), whichever takes longer.
    private val kBubbleStormCount = 100
    private val kBubbleStormCooldownSeconds = 10f
    private var stormCooldownRemaining = 0f
    private var activeStormBubbleCount = 0

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
    private var turtleDayNightHandle = 0
    private var turtleRetractionHandle = 0
    private var turtleGlowColorHandle = 0
    private var turtleShinyTypeHandle = 0
    private val turtles = mutableListOf<Turtle>()
    private val kTurtleScale = 0.308f
    private val kMaxTurtles = 5

    // Fish (drawn in back-to-front order by depth, single draw call per fish)
    private val fishPrograms = IntArray(4)
    private val fishMVPHandles = IntArray(4)
    private val fishSwimPhaseHandles = IntArray(4)
    private val fishBodyColorHandles = IntArray(4)
    private val fishFinColorHandles = IntArray(4)
    private val fishTailColorHandles = IntArray(4)
    private val fishStripeColorHandles = IntArray(4)
    private val fishDayNightHandles = IntArray(4)
    private val fishGlowColorHandles = IntArray(4)
    private val fishShinyTypeHandles = IntArray(4)
    private val fishes = mutableListOf<Fish>()
    private val kFishScale = 0.198f
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
    private var mantaDayNightHandle = 0
    private var mantaGlowColorHandle = 0
    private var mantaShinyTypeHandle = 0
    private val mantas = mutableListOf<Manta>()
    private val kMantaScale = 0.374f
    private val kMaxMantas = 4


    // Jellyfish (translucent bell + trailing tentacles - see Jellyfish.kt for how their
    // current-driven drift differs from every other creature here)
    private var jellyfishProgram = 0
    private var jellyfishMVPHandle = 0
    private var jellyfishPulsePhaseHandle = 0
    private var jellyfishBellColorHandle = 0
    private var jellyfishMarginColorHandle = 0
    private var jellyfishTentacleColorHandle = 0
    private var jellyfishPatternColorHandle = 0
    private var jellyfishDayNightHandle = 0
    private var jellyfishGlowColorHandle = 0
    private var jellyfishShinyTypeHandle = 0
    private var jellyfishTimeHandle = 0
    private var jellyfishElectricIntensityHandle = 0
    private val jellyfishes = mutableListOf<Jellyfish>()
    private val kJellyfishScale = 0.286f
    private val kMaxJellyfish = 6

    // Axolotls (crawling/swimming bottom-dwellers with feathery gills)
    private var axolotlProgram = 0
    private var axolotlMVPHandle = 0
    private var axolotlSwimPhaseHandle = 0
    private var axolotlGillPhaseHandle = 0
    private var axolotlLegPhaseHandle = 0
    private var axolotlBodyColorHandle = 0
    private var axolotlGillColorHandle = 0
    private var axolotlBellyColorHandle = 0
    private var axolotlSpotColorHandle = 0
    private var axolotlDayNightHandle = 0
    private var axolotlGlowColorHandle = 0
    private var axolotlShinyTypeHandle = 0
    private var axolotlTimeHandle = 0
    private val axolotls = mutableListOf<Axolotl>()
    private val kAxolotlScale = 0.3432f
    private val kMaxAxolotls = 4
    private val kSharkScale = 1.21f

    private var lastConfiguredFishCount = -1
    private var lastConfiguredTurtleCount = -1
    private var lastConfiguredMantaCount = -1
    private var lastConfiguredAxolotlCount = -1
    private var lastRealisticMode = false

    // Sand seabed (drawn behind rocks and flora along the aquarium floor)
    private var sandProgram = 0
    private var sandMVPHandle = 0
    private var sandDayNightHandle = 0
    private var sandTimeHandle = 0
    private var sandAspectHandle = 0
    private var sandBaseColorHandle = 0
    private var sandHighlightColorHandle = 0
    private var sandShadowColorHandle = 0
    private var sandDeepColorHandle = 0
    private var sandIsForegroundHandle = 0
    private var sandBioluminescenceHandle = 0

    private val kZeroGlow = floatArrayOf(0f, 0f, 0f)

    private val kSandBaseColor = floatArrayOf(0.78f, 0.70f, 0.54f)
    private val kSandHighlightColor = floatArrayOf(0.90f, 0.84f, 0.70f)
    private val kSandShadowColor = floatArrayOf(0.62f, 0.54f, 0.42f)

    private val scratchSandBaseColor = FloatArray(3)
    private val scratchSandHighlightColor = FloatArray(3)
    private val scratchSandShadowColor = FloatArray(3)

    // Rocks (substrate boulders along the tank floor bed)
    private var rockProgram = 0
    private var rockMVPHandle = 0
    private var rockShapeTypeHandle = 0
    private var rockBaseColorHandle = 0
    private var rockHighlightColorHandle = 0
    private var rockShadowColorHandle = 0
    private var rockSpotColorHandle = 0
    private var rockDayNightHandle = 0
    private var rockGlowColorHandle = 0
    private var rockTimeHandle = 0
    private var rockAspectHandle = 0
    private var rockBioluminescenceHandle = 0
    private val rocks = mutableListOf<Rock>()
    private val kRockFloorY = -0.84f

    // Submarine (rendered as a single quad with its own vertex and fragment shader, traveling in the far background)
    private var submarineProgram = 0
    private var submarineMVPHandle = 0
    private var submarineSwimPhaseHandle = 0
    private var submarineBodyColorHandle = 0
    private var submarineAccentColorHandle = 0
    private var submarineWindowColorHandle = 0
    private val submarine = Submarine()
    private val kSubmarineScale = 0.28f

    // Sunken Treasure Chest
    private var treasureChestProgram = 0
    private var treasureChestMVPHandle = 0
    private var treasureChestTimeHandle = 0
    private var treasureChestLidOpenHandle = 0
    private var treasureChestGlowHandle = 0
    private var treasureChestDayNightHandle = 0
    private var treasureChestWoodColorHandle = 0
    private var treasureChestMetalColorHandle = 0
    private var treasureChestGoldColorHandle = 0
    private var treasureChestGemColorHandle = 0
    private var treasureChestBeaconGlowHandle = 0
    private val treasureChest = TreasureChest()
    private val kTreasureChestScale = 0.35f

    // Starfish on the front screen glass
    private var starfishProgram = 0
    private var starfishMVPHandle = 0
    private var starfishTimeHandle = 0
    private var starfishDayNightHandle = 0
    private var starfishCrawlPhaseHandle = 0
    private var starfishTouchReactHandle = 0
    private var starfishShinyTypeHandle = 0
    private var starfishBodyColorHandle = 0
    private var starfishDetailColorHandle = 0
    private var starfishSuctionColorHandle = 0
    private var starfishGlowColorHandle = 0
    private val starfishes = mutableListOf<Starfish>()
    private val kMaxStarfish = 3
    private val kStarfishScale = 0.22f

    // Per-configProvider.getSubmarineColor() palettes - hoisted to top-level constants so
    // drawSubmarine() doesn't allocate 3 fresh FloatArrays every single frame the submarine is
    // on screen.
    private val kSubmarineBodyColorAmarillo = floatArrayOf(0.95f, 0.80f, 0.10f)
    private val kSubmarineAccentColorAmarillo = floatArrayOf(0.85f, 0.15f, 0.10f)
    private val kSubmarineWindowColorAmarillo = floatArrayOf(0.98f, 0.92f, 0.40f)
    private val kSubmarineBodyColorRojo = floatArrayOf(0.85f, 0.15f, 0.10f)
    private val kSubmarineAccentColorRojo = floatArrayOf(0.95f, 0.80f, 0.10f)
    private val kSubmarineWindowColorRojo = floatArrayOf(0.98f, 0.92f, 0.40f)
    private val kSubmarineBodyColorAzul = floatArrayOf(0.15f, 0.50f, 0.85f)
    private val kSubmarineAccentColorAzul = floatArrayOf(0.95f, 0.80f, 0.10f)
    private val kSubmarineWindowColorAzul = floatArrayOf(0.50f, 0.90f, 0.98f)
    private val kSubmarineBodyColorVerde = floatArrayOf(0.18f, 0.80f, 0.44f)
    private val kSubmarineAccentColorVerde = floatArrayOf(0.90f, 0.30f, 0.20f)
    private val kSubmarineWindowColorVerde = floatArrayOf(0.98f, 0.92f, 0.40f)
    private val kSubmarineBodyColorRosa = floatArrayOf(0.91f, 0.12f, 0.39f)
    private val kSubmarineAccentColorRosa = floatArrayOf(0.15f, 0.80f, 0.85f)
    private val kSubmarineWindowColorRosa = floatArrayOf(0.98f, 0.98f, 0.98f)
    private val kSubmarineBodyColorNaranja = floatArrayOf(0.90f, 0.49f, 0.13f)
    private val kSubmarineAccentColorNaranja = floatArrayOf(0.15f, 0.25f, 0.35f)
    private val kSubmarineWindowColorNaranja = floatArrayOf(0.98f, 0.92f, 0.40f)

    // Food (Feeding Time)
    class FoodParticle(var x: Float, var y: Float) {
        var active: Boolean = false
    }
    private var foodProgram = 0
    private var foodMVPHandle = 0
    private val foodParticles = ArrayList<FoodParticle>(4).apply {
        repeat(4) { add(FoodParticle(0f, 0f)) }
    }
    private val kFoodScale = 0.05f
    private val foodSpeed = 0.22f
    private val kFeedingCooldownSeconds = 5f
    private var feedingCooldownRemaining = 0f

    // Shark
    private var sharkProgram = 0
    private var sharkMVPHandle = 0
    private var sharkSwimPhaseHandle = 0
    private var sharkBodyColorHandle = 0
    private var sharkDayNightHandle = 0
    private var sharkGlowColorHandle = 0
    private var sharkBiteHandle = 0
    private var sharkTimeHandle = 0
    private val shark = Shark()

    // Per-colorMode shark palettes (0: Gray, 1: Blue, 2: White/Albino, 3: Golden) - hoisted to
    // top-level constants so drawShark() doesn't allocate 2 fresh FloatArrays every single frame
    // the shark is active.
    private val kSharkColorGray = floatArrayOf(0.45f, 0.48f, 0.52f)
    private val kSharkColorBlue = floatArrayOf(0.22f, 0.35f, 0.55f)
    private val kSharkColorWhite = floatArrayOf(0.92f, 0.88f, 0.88f)
    private val kSharkColorGolden = floatArrayOf(0.90f, 0.72f, 0.25f)
    private val kSharkGlowGray = floatArrayOf(0.0f, 0.75f, 0.6f)
    private val kSharkGlowBlue = floatArrayOf(0.0f, 0.45f, 1.0f)
    private val kSharkGlowWhite = floatArrayOf(0.0f, 0.9f, 0.8f)
    private val kSharkGlowGolden = floatArrayOf(1.0f, 0.55f, 0.0f)



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
    private var kelpDayNightHandle = 0
    private var kelpGlowColorHandle = 0
    private var kelpTimeHandle = 0
    private val kelps = mutableListOf<Kelp>()
    // +25% over the original 0.32/0.55 - vegetation reads too small/sparse at the original size.
    private val kKelpWidth = 0.40f
    private val kKelpBaseHeight = 0.6875f

    private var anemoneProgram = 0
    private var anemoneMVPHandle = 0
    private var anemoneSwayPhaseHandle = 0
    private var anemoneFootColorHandle = 0
    private var anemoneTentacleColorHandle = 0
    private var anemoneTipColorHandle = 0
    private var anemoneHighlightColorHandle = 0
    private var anemoneDayNightHandle = 0
    private var anemoneGlowColorHandle = 0
    private var anemoneTimeHandle = 0
    private val anemones = mutableListOf<Anemone>()
    // +25% over the original 0.30.
    private val kAnemoneSize = 0.375f

    private var seaGrassProgram = 0
    private var seaGrassMVPHandle = 0
    private var seaGrassSwayPhaseHandle = 0
    private var seaGrassBladeColorHandle = 0
    private var seaGrassTipColorHandle = 0
    private var seaGrassBaseColorHandle = 0
    private var seaGrassHighlightColorHandle = 0
    private var seaGrassDayNightHandle = 0
    private var seaGrassGlowColorHandle = 0
    private var seaGrassTimeHandle = 0
    private val seaGrasses = mutableListOf<SeaGrass>()
    // +25% over the original 0.20/0.22.
    private val kSeaGrassWidth = 0.25f
    private val kSeaGrassBaseHeight = 0.275f

    private var coralProgram = 0
    private var coralMVPHandle = 0
    private var coralSwayPhaseHandle = 0
    private var coralBaseColorHandle = 0
    private var coralBranchColorHandle = 0
    private var coralPolypColorHandle = 0
    private var coralHighlightColorHandle = 0
    private var coralDayNightHandle = 0
    private var coralGlowColorHandle = 0
    private var coralTimeHandle = 0
    private val corals = mutableListOf<Coral>()
    // +25% over the original 0.34.
    private val kCoralSize = 0.425f

    // Fraction of the total plant density budget spent on each vegetation type - must sum to 1.
    private val kKelpShareOfDensity = 0.35f
    private val kAnemoneShareOfDensity = 0.20f
    private val kSeaGrassShareOfDensity = 0.30f
    // Coral gets whatever's left after the three shares above, so rounding always adds up to
    // the full requested density instead of possibly dropping a plant to rounding error.
    private val kMaxPlantDensity = 28
    // The floor plants are anchored to - raised to -0.80f so plants sit gracefully on top of
    // the sandy seabed floor (sand.frag) with their bases embedded into the dunes rather than
    // floating in open water.
    private val kPlantFloorY = -0.80f
    // 3 stepped tiers for flora and seabed depth:
    // Tier 0 (Fondo): depth >= kTier0DepthThreshold (0.65f)
    // Tier 1 (Medio): kTier1DepthThreshold <= depth < kTier0DepthThreshold
    // Tier 2 (Frente): depth < kTier1DepthThreshold (0.35f)
    private val kTier0DepthThreshold = 0.65f
    private val kTier1DepthThreshold = 0.35f
    // Sentinel (an impossible real density) forcing layoutPlants() to run once on the very first
    // syncPlants() call, regardless of what ConfigProvider.getPlantDensity() returns.
    private var currentPlantDensity = -1
    private var plantLayoutAspectRatio = -1f
    // Each list is sorted farthest-first (descending depth) once in syncPlants() so:
    // [0, tier1Index) is Tier 0 (Fondo), [tier1Index, tier2Index) is Tier 1 (Medio),
    // and [tier2Index, size) is Tier 2 (Frente). Zero-allocation indexing during draw.
    private var kelpTier1Index = 0
    private var kelpTier2Index = 0
    private var anemoneTier1Index = 0
    private var anemoneTier2Index = 0
    private var seaGrassTier1Index = 0
    private var seaGrassTier2Index = 0
    private var coralTier1Index = 0
    private var coralTier2Index = 0

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
    // Reused by spawnPropulsionBubbles() to receive Turtle.frontFlipperWorldPosition()'s [x, y]
    // output without allocating a Pair<Float, Float> (which boxes both values) on every call.
    private val scratchFlipperPosition = FloatArray(2)
    private val scratchBodyColor = FloatArray(3)
    private val scratchFinColor = FloatArray(3)
    private val scratchTailColor = FloatArray(3)
    private val scratchStripeColor = FloatArray(3)
    private val scratchMantaBodyColor = FloatArray(3)
    private val scratchMantaWingColor = FloatArray(3)
    private val scratchMantaTailColor = FloatArray(3)
    private val scratchMantaMarkingColor = FloatArray(3)
    private val scratchSharkColor = FloatArray(3)
    private val scratchJellyfishBellColor = FloatArray(3)
    private val scratchJellyfishMarginColor = FloatArray(3)
    private val scratchJellyfishTentacleColor = FloatArray(3)
    private val scratchJellyfishPatternColor = FloatArray(3)
    private val scratchKelpBladeColor = FloatArray(3)
    private val scratchKelpTipColor = FloatArray(3)
    private val scratchKelpBaseColor = FloatArray(3)
    private val scratchKelpHighlightColor = FloatArray(3)
    private val scratchAnemoneFootColor = FloatArray(3)
    private val scratchAnemoneTentacleColor = FloatArray(3)
    private val scratchAnemoneTipColor = FloatArray(3)
    private val scratchAnemoneHighlightColor = FloatArray(3)
    private val scratchSeaGrassBladeColor = FloatArray(3)
    private val scratchSeaGrassTipColor = FloatArray(3)
    private val scratchSeaGrassBaseColor = FloatArray(3)
    private val scratchSeaGrassHighlightColor = FloatArray(3)
    private val scratchCoralBaseColor = FloatArray(3)
    private val scratchCoralBranchColor = FloatArray(3)
    private val scratchCoralPolypColor = FloatArray(3)
    private val scratchCoralHighlightColor = FloatArray(3)

    private val scratchSubmarineBodyColor = FloatArray(3)
    private val scratchSubmarineAccentColor = FloatArray(3)
    private val scratchSubmarineWindowColor = FloatArray(3)

    private val scratchAxolotlBodyColor = FloatArray(3)
    private val scratchAxolotlGillColor = FloatArray(3)
    private val scratchAxolotlBellyColor = FloatArray(3)
    private val scratchAxolotlSpotColor = FloatArray(3)

    private val scratchStarfishBodyColor = FloatArray(3)
    private val scratchStarfishDetailColor = FloatArray(3)
    private val scratchStarfishSuctionColor = FloatArray(3)

    private val scratchRockBaseColor = FloatArray(3)
    private val scratchRockHighlightColor = FloatArray(3)
    private val scratchRockShadowColor = FloatArray(3)
    private val scratchRockSpotColor = FloatArray(3)

    private val scratchWoodColor = FloatArray(3)
    private val scratchMetalColor = FloatArray(3)

    // Written once per frame by getDeepColorForTheme() and then night-tinted in place by
    // onDrawFrame() - reused instead of allocating a fresh FloatArray (via .clone()) every single
    // frame, same rationale as every other scratch* buffer above.
    private val scratchDeepColor = FloatArray(3)

    // Mirrors acuario_background.frag's deepColor per theme, so a receding turtle tints
    // toward the same color the background already fades to at depth.
    private val kDeepColorTurquesa = floatArrayOf(0.012f, 0.095f, 0.130f)
    private val kDeepColorAzulProfundo = floatArrayOf(0.010f, 0.045f, 0.130f)
    private val kDeepColorAtardecer = floatArrayOf(0.08f, 0.04f, 0.15f)
    private val kDeepColorAbisal = floatArrayOf(0.01f, 0.01f, 0.04f)
    private val kDeepColorArrecife = floatArrayOf(0.02f, 0.08f, 0.18f)

    /** Writes the deep-water base color for [theme] into [out] (avoids a per-frame allocation). */
    private fun getDeepColorForTheme(theme: Int, out: FloatArray) {
        val source = when (theme) {
            0 -> kDeepColorTurquesa
            1 -> kDeepColorAzulProfundo
            2 -> kDeepColorAtardecer
            3 -> kDeepColorAbisal
            4 -> kDeepColorArrecife
            else -> null
        }
        if (source != null) {
            out[0] = source[0]
            out[1] = source[1]
            out[2] = source[2]
        } else {
            val customColor = configProvider.getCustomDeepColor()
            out[0] = ((customColor shr 16) and 0xFF) / 255f
            out[1] = ((customColor shr 8) and 0xFF) / 255f
            out[2] = (customColor and 0xFF) / 255f
        }
    }

    private var aspectRatio = 1f
    private var screenWidth = 0
    private var screenHeight = 0
    private var time = 0f
    private val kTwoPi = (Math.PI * 2.0).toFloat()
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Realistic Mode Chest Regeneration State
    private var isRegeneratingFauna = false
    private var faunaRegenTimer = 0f
    private val kFaunaRegenInterval = 1.6f

    private fun spawnNextRegenAnimal(): Boolean {
        val desiredFish = configProvider.getFishCount().coerceIn(0, kMaxFish)
        val desiredTurtles = configProvider.getTurtleCount().coerceIn(0, kMaxTurtles)
        val desiredMantas = configProvider.getMantaCount().coerceIn(0, kMaxMantas)
        val desiredAxolotls = configProvider.getAxolotlCount().coerceIn(0, kMaxAxolotls)
        val desiredJellyfish = configProvider.getJellyfishCount().coerceIn(0, kMaxJellyfish)

        // Alternate organic arrivals one by one from screen edges:
        synchronized(fishes) {
            if (fishes.size < desiredFish) {
                fishes.add(Fish().apply { spawnFromEdge(aspectRatio) })
                return true
            }
        }
        synchronized(turtles) {
            if (turtles.size < desiredTurtles) {
                turtles.add(Turtle().apply { spawnFromEdge(aspectRatio) })
                return true
            }
        }
        synchronized(mantas) {
            if (mantas.size < desiredMantas) {
                mantas.add(Manta().apply { spawnFromEdge(aspectRatio) })
                return true
            }
        }
        synchronized(axolotls) {
            if (axolotls.size < desiredAxolotls) {
                axolotls.add(Axolotl().apply { spawnFromEdge(aspectRatio) })
                return true
            }
        }
        synchronized(jellyfishes) {
            if (jellyfishes.size < desiredJellyfish) {
                jellyfishes.add(Jellyfish())
                return true
            }
        }
        return false // Fully restored
    }

    private fun isFaunaWaitingForChest(): Boolean {
        if (!configProvider.getRealisticModeEnabled() || isRegeneratingFauna) return false
        return fishes.isEmpty() && turtles.isEmpty() && mantas.isEmpty() && axolotls.isEmpty() && jellyfishes.isEmpty()
    }

    private class Bubble(
        var x: Float = 0f,
        var y: Float = 0f,
        var baseX: Float = 0f,
        var size: Float = 0f,
        var speed: Float = 0f,
        var wobbleAmplitude: Float = 0f,
        var wobbleSpeed: Float = 0f,
        var wobbleSeed: Float = 0f,
        var alpha: Float = 0f,
        var r: Float = 0.85f,
        var g: Float = 0.95f,
        var b: Float = 1.0f,
        // True only for bubbles spawned by triggerBubbleStorm() - lets the burst-removal loop in
        // onUpdate() track activeStormBubbleCount separately from ordinary exhale/propulsion
        // bursts sharing the same burstBubbles pool, so the storm cooldown can tell exactly when
        // every one of ITS bubbles (not just any burst bubble) has risen off-screen.
        var isStorm: Boolean = false
    ) {
        fun reset(
            startX: Float,
            startY: Float,
            size: Float,
            speed: Float,
            wobbleAmplitude: Float,
            wobbleSpeed: Float,
            wobbleSeed: Float,
            alpha: Float,
            r: Float = 0.85f,
            g: Float = 0.95f,
            b: Float = 1.0f
        ): Bubble {
            this.x = startX
            this.y = startY
            this.baseX = startX
            this.size = size
            this.speed = speed
            this.wobbleAmplitude = wobbleAmplitude
            this.wobbleSpeed = wobbleSpeed
            this.wobbleSeed = wobbleSeed
            this.alpha = alpha
            this.r = r
            this.g = g
            this.b = b
            // Always cleared here (not left to the caller) so a pooled instance previously used
            // for a storm bubble can't stay mismarked once it's recycled for an unrelated
            // exhale/propulsion burst - triggerBubbleStorm() sets it back to true right after
            // calling this, for the instances it actually spawns.
            this.isStorm = false
            return this
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
            bgParallaxHandle = GLES30.glGetUniformLocation(backgroundProgram, "uParallaxOffset")
            bgDayNightHandle = GLES30.glGetUniformLocation(backgroundProgram, "uDayNight")
            bgCustomShallowHandle = GLES30.glGetUniformLocation(backgroundProgram, "uCustomShallowColor")
            bgCustomDeepHandle = GLES30.glGetUniformLocation(backgroundProgram, "uCustomDeepColor")
            bgStormIntensityHandle = GLES30.glGetUniformLocation(backgroundProgram, "uStormIntensity")
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
            turtleDayNightHandle = GLES30.glGetUniformLocation(turtleProgram, "uDayNight")
            turtleRetractionHandle = GLES30.glGetUniformLocation(turtleProgram, "uRetraction")
            turtleGlowColorHandle = GLES30.glGetUniformLocation(turtleProgram, "uGlowColor")
            turtleShinyTypeHandle = GLES30.glGetUniformLocation(turtleProgram, "uShinyType")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fishShaders = listOf(
            "shaders/fish.frag",
            "shaders/slender_fish.frag",
            "shaders/deep_fish.frag",
            "shaders/thread_fish.frag"
        )
        for (i in 0 until 4) {
            try {
                val vert = readAssetFile(context, "shaders/fish.vert")
                val frag = readAssetFile(context, fishShaders[i])
                val prog = createProgram(vert, frag)
                fishPrograms[i] = prog
                fishMVPHandles[i] = GLES30.glGetUniformLocation(prog, "uMVPMatrix")
                fishSwimPhaseHandles[i] = GLES30.glGetUniformLocation(prog, "uSwimPhase")
                fishBodyColorHandles[i] = GLES30.glGetUniformLocation(prog, "uBodyColor")
                fishFinColorHandles[i] = GLES30.glGetUniformLocation(prog, "uFinColor")
                fishTailColorHandles[i] = GLES30.glGetUniformLocation(prog, "uTailColor")
                fishStripeColorHandles[i] = GLES30.glGetUniformLocation(prog, "uStripeColor")
                fishDayNightHandles[i] = GLES30.glGetUniformLocation(prog, "uDayNight")
                fishGlowColorHandles[i] = GLES30.glGetUniformLocation(prog, "uGlowColor")
                fishShinyTypeHandles[i] = GLES30.glGetUniformLocation(prog, "uShinyType")
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
            mantaDayNightHandle = GLES30.glGetUniformLocation(mantaProgram, "uDayNight")
            mantaGlowColorHandle = GLES30.glGetUniformLocation(mantaProgram, "uGlowColor")
            mantaShinyTypeHandle = GLES30.glGetUniformLocation(mantaProgram, "uShinyType")
        } catch (e: Exception) {
            e.printStackTrace()
        }


        try {
            val vert = readAssetFile(context, "shaders/jellyfish.vert")
            val frag = readAssetFile(context, "shaders/jellyfish.frag")
            jellyfishProgram = createProgram(vert, frag)
            jellyfishMVPHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uMVPMatrix")
            jellyfishPulsePhaseHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uPulsePhase")
            jellyfishBellColorHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uBellColor")
            jellyfishMarginColorHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uMarginColor")
            jellyfishTentacleColorHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uTentacleColor")
            jellyfishPatternColorHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uPatternColor")
            jellyfishDayNightHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uDayNight")
            jellyfishGlowColorHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uGlowColor")
            jellyfishShinyTypeHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uShinyType")
            jellyfishTimeHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uTime")
            jellyfishElectricIntensityHandle = GLES30.glGetUniformLocation(jellyfishProgram, "uElectricIntensity")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/axolotl.vert")
            val frag = readAssetFile(context, "shaders/axolotl.frag")
            axolotlProgram = createProgram(vert, frag)
            axolotlMVPHandle = GLES30.glGetUniformLocation(axolotlProgram, "uMVPMatrix")
            axolotlSwimPhaseHandle = GLES30.glGetUniformLocation(axolotlProgram, "uSwimPhase")
            axolotlGillPhaseHandle = GLES30.glGetUniformLocation(axolotlProgram, "uGillPhase")
            axolotlLegPhaseHandle = GLES30.glGetUniformLocation(axolotlProgram, "uLegPhase")
            axolotlBodyColorHandle = GLES30.glGetUniformLocation(axolotlProgram, "uBodyColor")
            axolotlGillColorHandle = GLES30.glGetUniformLocation(axolotlProgram, "uGillColor")
            axolotlBellyColorHandle = GLES30.glGetUniformLocation(axolotlProgram, "uBellyColor")
            axolotlSpotColorHandle = GLES30.glGetUniformLocation(axolotlProgram, "uSpotColor")
            axolotlDayNightHandle = GLES30.glGetUniformLocation(axolotlProgram, "uDayNight")
            axolotlGlowColorHandle = GLES30.glGetUniformLocation(axolotlProgram, "uGlowColor")
            axolotlShinyTypeHandle = GLES30.glGetUniformLocation(axolotlProgram, "uShinyType")
            axolotlTimeHandle = GLES30.glGetUniformLocation(axolotlProgram, "uTime")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/sand.vert")
            val frag = readAssetFile(context, "shaders/sand.frag")
            sandProgram = createProgram(vert, frag)
            sandMVPHandle = GLES30.glGetUniformLocation(sandProgram, "uMVPMatrix")
            sandDayNightHandle = GLES30.glGetUniformLocation(sandProgram, "uDayNight")
            sandTimeHandle = GLES30.glGetUniformLocation(sandProgram, "uTime")
            sandAspectHandle = GLES30.glGetUniformLocation(sandProgram, "uAspect")
            sandBaseColorHandle = GLES30.glGetUniformLocation(sandProgram, "uBaseColor")
            sandHighlightColorHandle = GLES30.glGetUniformLocation(sandProgram, "uHighlightColor")
            sandShadowColorHandle = GLES30.glGetUniformLocation(sandProgram, "uShadowColor")
            sandDeepColorHandle = GLES30.glGetUniformLocation(sandProgram, "uDeepColor")
            sandIsForegroundHandle = GLES30.glGetUniformLocation(sandProgram, "uIsForeground")
            sandBioluminescenceHandle = GLES30.glGetUniformLocation(sandProgram, "uBioluminescence")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/rock.vert")
            val frag = readAssetFile(context, "shaders/rock.frag")
            rockProgram = createProgram(vert, frag)
            rockMVPHandle = GLES30.glGetUniformLocation(rockProgram, "uMVPMatrix")
            rockShapeTypeHandle = GLES30.glGetUniformLocation(rockProgram, "uShapeType")
            rockBaseColorHandle = GLES30.glGetUniformLocation(rockProgram, "uBaseColor")
            rockHighlightColorHandle = GLES30.glGetUniformLocation(rockProgram, "uHighlightColor")
            rockShadowColorHandle = GLES30.glGetUniformLocation(rockProgram, "uShadowColor")
            rockSpotColorHandle = GLES30.glGetUniformLocation(rockProgram, "uSpotColor")
            rockDayNightHandle = GLES30.glGetUniformLocation(rockProgram, "uDayNight")
            rockGlowColorHandle = GLES30.glGetUniformLocation(rockProgram, "uGlowColor")
            rockTimeHandle = GLES30.glGetUniformLocation(rockProgram, "uTime")
            rockAspectHandle = GLES30.glGetUniformLocation(rockProgram, "uAspect")
            rockBioluminescenceHandle = GLES30.glGetUniformLocation(rockProgram, "uBioluminescence")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/fish.vert")
            val frag = readAssetFile(context, "shaders/shark.frag")
            sharkProgram = createProgram(vert, frag)
            sharkMVPHandle = GLES30.glGetUniformLocation(sharkProgram, "uMVPMatrix")
            sharkSwimPhaseHandle = GLES30.glGetUniformLocation(sharkProgram, "uSwimPhase")
            sharkBodyColorHandle = GLES30.glGetUniformLocation(sharkProgram, "uBodyColor")
            sharkDayNightHandle = GLES30.glGetUniformLocation(sharkProgram, "uDayNight")
            sharkGlowColorHandle = GLES30.glGetUniformLocation(sharkProgram, "uGlowColor")
            sharkBiteHandle = GLES30.glGetUniformLocation(sharkProgram, "uBiteProgress")
            sharkTimeHandle = GLES30.glGetUniformLocation(sharkProgram, "uTime")
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
            kelpDayNightHandle = GLES30.glGetUniformLocation(kelpProgram, "uDayNight")
            kelpGlowColorHandle = GLES30.glGetUniformLocation(kelpProgram, "uGlowColor")
            kelpTimeHandle = GLES30.glGetUniformLocation(kelpProgram, "uTime")
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
            anemoneDayNightHandle = GLES30.glGetUniformLocation(anemoneProgram, "uDayNight")
            anemoneGlowColorHandle = GLES30.glGetUniformLocation(anemoneProgram, "uGlowColor")
            anemoneTimeHandle = GLES30.glGetUniformLocation(anemoneProgram, "uTime")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/seagrass.vert")
            val frag = readAssetFile(context, "shaders/seagrass.frag")
            seaGrassProgram = createProgram(vert, frag)
            seaGrassMVPHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uMVPMatrix")
            seaGrassSwayPhaseHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uSwayPhase")
            seaGrassBladeColorHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uBladeColor")
            seaGrassTipColorHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uTipColor")
            seaGrassBaseColorHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uBaseColor")
            seaGrassHighlightColorHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uHighlightColor")
            seaGrassDayNightHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uDayNight")
            seaGrassGlowColorHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uGlowColor")
            seaGrassTimeHandle = GLES30.glGetUniformLocation(seaGrassProgram, "uTime")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/coral.vert")
            val frag = readAssetFile(context, "shaders/coral.frag")
            coralProgram = createProgram(vert, frag)
            coralMVPHandle = GLES30.glGetUniformLocation(coralProgram, "uMVPMatrix")
            coralSwayPhaseHandle = GLES30.glGetUniformLocation(coralProgram, "uSwayPhase")
            coralBaseColorHandle = GLES30.glGetUniformLocation(coralProgram, "uBaseColor")
            coralBranchColorHandle = GLES30.glGetUniformLocation(coralProgram, "uBranchColor")
            coralPolypColorHandle = GLES30.glGetUniformLocation(coralProgram, "uPolypColor")
            coralHighlightColorHandle = GLES30.glGetUniformLocation(coralProgram, "uHighlightColor")
            coralDayNightHandle = GLES30.glGetUniformLocation(coralProgram, "uDayNight")
            coralGlowColorHandle = GLES30.glGetUniformLocation(coralProgram, "uGlowColor")
            coralTimeHandle = GLES30.glGetUniformLocation(coralProgram, "uTime")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/submarine.vert")
            val frag = readAssetFile(context, "shaders/submarine.frag")
            submarineProgram = createProgram(vert, frag)
            submarineMVPHandle = GLES30.glGetUniformLocation(submarineProgram, "uMVPMatrix")
            submarineSwimPhaseHandle = GLES30.glGetUniformLocation(submarineProgram, "uSwimPhase")
            submarineBodyColorHandle = GLES30.glGetUniformLocation(submarineProgram, "uBodyColor")
            submarineAccentColorHandle = GLES30.glGetUniformLocation(submarineProgram, "uAccentColor")
            submarineWindowColorHandle = GLES30.glGetUniformLocation(submarineProgram, "uWindowColor")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/food.vert")
            val frag = readAssetFile(context, "shaders/food.frag")
            foodProgram = createProgram(vert, frag)
            foodMVPHandle = GLES30.glGetUniformLocation(foodProgram, "uMVPMatrix")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/treasure_chest.vert")
            val frag = readAssetFile(context, "shaders/treasure_chest.frag")
            treasureChestProgram = createProgram(vert, frag)
            treasureChestMVPHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uMVPMatrix")
            treasureChestTimeHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uTime")
            treasureChestLidOpenHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uLidOpen")
            treasureChestGlowHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uGlowIntensity")
            treasureChestDayNightHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uDayNight")
            treasureChestWoodColorHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uWoodColor")
            treasureChestMetalColorHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uMetalColor")
            treasureChestGoldColorHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uGoldColor")
            treasureChestGemColorHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uGemColor")
            treasureChestBeaconGlowHandle = GLES30.glGetUniformLocation(treasureChestProgram, "uBeaconGlow")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vert = readAssetFile(context, "shaders/starfish.vert")
            val frag = readAssetFile(context, "shaders/starfish.frag")
            starfishProgram = createProgram(vert, frag)
            starfishMVPHandle = GLES30.glGetUniformLocation(starfishProgram, "uMVPMatrix")
            starfishTimeHandle = GLES30.glGetUniformLocation(starfishProgram, "uTime")
            starfishDayNightHandle = GLES30.glGetUniformLocation(starfishProgram, "uDayNight")
            starfishCrawlPhaseHandle = GLES30.glGetUniformLocation(starfishProgram, "uCrawlPhase")
            starfishTouchReactHandle = GLES30.glGetUniformLocation(starfishProgram, "uTouchReact")
            starfishShinyTypeHandle = GLES30.glGetUniformLocation(starfishProgram, "uShinyType")
            starfishBodyColorHandle = GLES30.glGetUniformLocation(starfishProgram, "uBodyColor")
            starfishDetailColorHandle = GLES30.glGetUniformLocation(starfishProgram, "uDetailColor")
            starfishSuctionColorHandle = GLES30.glGetUniformLocation(starfishProgram, "uSuctionColor")
            starfishGlowColorHandle = GLES30.glGetUniformLocation(starfishProgram, "uGlowColor")
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

        synchronized(turtles) {
            turtles.clear()
            syncTurtleCount()
        }

        fishes.clear()
        syncFishCount()

        mantas.clear()
        syncMantaCount()


        jellyfishes.clear()
        syncJellyfishCount()

        synchronized(axolotls) {
            axolotls.clear()
            syncAxolotlCount()
        }

        synchronized(starfishes) {
            starfishes.clear()
            syncStarfishCount()
        }

        synchronized(rocks) {
            syncRocks()
        }

        kelps.clear()
        anemones.clear()
        seaGrasses.clear()
        corals.clear()
        currentPlantDensity = -1
        plantLayoutAspectRatio = -1f
        syncPlants()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
        screenWidth = width
        screenHeight = height
        Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)

        synchronized(starfishes) {
            for (s in starfishes) {
                s.ensureWithinBounds(aspectRatio)
            }
        }
    }

    override fun onOffsetsChanged(xOffset: Float, yOffset: Float) {
        targetOffsetX = xOffset
    }

    override fun onSensorValuesChanged(tiltX: Float, tiltY: Float) {}

    /**
     * Hit-tests a raw screen-pixel tap against the turtles: converts it into the same [-aspectRatio,
     * aspectRatio] x [-1, 1] world space used everywhere else (matching the ortho projection set up
     * in [onSurfaceChanged]), then checks it against each turtle's on-screen circle (its draw
     * position +- its current [kTurtleScale]-derived radius, i.e. how big/close it looks right now -
     * see drawTurtles()'s depthScale comment). Iterates back-to-front (turtles is left sorted
     * nearest-last by the previous drawTurtles() call - see its own comment) so an overlapping pair
     * resolves to whichever one is actually drawn on top, like a real tap would.
     */
    override fun onTouchEvent(x: Float, y: Float) {
        if (screenWidth <= 0 || screenHeight <= 0) return
        val worldX = (x / screenWidth * 2f - 1f) * aspectRatio
        val worldY = 1f - (y / screenHeight * 2f)

        class TouchTarget(
            val type: Int, // 0 = Turtle, 1 = Manta, 2 = Jellyfish, 3 = Shark
            val obj: Any,
            val depth: Float,
            val x: Float,
            val y: Float,
            val rx: Float,
            val ry: Float
        )

        val targets = ArrayList<TouchTarget>()

        if (shark.active) {
            val depthScale = kSharkScale * (1f - (1f - kMinScaleAtDepth) * shark.depth)
            targets.add(TouchTarget(
                type = 3,
                obj = shark,
                depth = shark.depth,
                x = shark.x + getParallaxX(shark.depth),
                y = shark.y + getParallaxY(shark.depth),
                rx = depthScale * 0.9f,
                ry = depthScale * 0.5f
            ))
        }

        synchronized(turtles) {
            for (t in turtles) {
                val depthScale = kTurtleScale * (1f - (1f - kMinScaleAtDepth) * t.depth)
                targets.add(TouchTarget(
                    type = 0,
                    obj = t,
                    depth = t.depth,
                    x = t.x + getParallaxX(t.depth),
                    y = t.y + getParallaxY(t.depth),
                    rx = depthScale * 0.9f,
                    ry = depthScale * 0.6f
                ))
            }
        }

        synchronized(mantas) {
            for (m in mantas) {
                val depthScale = kMantaScale * (1f - (1f - kMinScaleAtDepth) * m.depth)
                targets.add(TouchTarget(
                    type = 1,
                    obj = m,
                    depth = m.depth,
                    x = m.x + m.loopOffsetX + getParallaxX(m.depth),
                    y = m.y + m.loopOffsetY + getParallaxY(m.depth),
                    rx = depthScale * 1.3f,
                    ry = depthScale * 0.5f
                ))
            }
        }

        synchronized(jellyfishes) {
            for (jf in jellyfishes) {
                val depthScale = kJellyfishScale * (1f - (1f - kMinScaleAtDepth) * jf.depth)
                targets.add(TouchTarget(
                    type = 2,
                    obj = jf,
                    depth = jf.depth,
                    x = jf.x + getParallaxX(jf.depth),
                    y = jf.y + getParallaxY(jf.depth),
                    rx = depthScale * 0.7f,
                    ry = depthScale * 1.1f
                ))
            }
        }

        synchronized(axolotls) {
            for (ax in axolotls) {
                val depthScale = kAxolotlScale * (1f - (1f - kMinScaleAtDepth) * ax.depth)
                targets.add(TouchTarget(
                    type = 4,
                    obj = ax,
                    depth = ax.depth,
                    x = ax.x + getParallaxX(ax.depth),
                    y = ax.y + getParallaxY(ax.depth),
                    rx = depthScale * 1.1f,
                    ry = depthScale * 0.6f
                ))
            }
        }

        synchronized(starfishes) {
            for (s in starfishes) {
                targets.add(TouchTarget(
                    type = 6,
                    obj = s,
                    depth = -0.05f, // Adhered to front glass: highest priority
                    x = s.x + getParallaxX(0f),
                    y = s.y,
                    rx = kStarfishScale * 0.70f,
                    ry = kStarfishScale * 0.70f
                ))
            }
        }

        val chestScale = kTreasureChestScale * (1f - (1f - kMinScaleAtDepth) * treasureChest.depth)
        targets.add(TouchTarget(
            type = 5,
            obj = treasureChest,
            depth = treasureChest.depth,
            x = (treasureChest.x * aspectRatio) + getParallaxX(treasureChest.depth),
            y = treasureChest.y + getParallaxY(treasureChest.depth),
            rx = chestScale * 1.30f,
            ry = chestScale * 0.95f
        ))

        // Sort by depth ascending so the closest creatures (lowest depth value) are checked first
        targets.sortBy { it.depth }

        var animalTouched = false
        for (target in targets) {
            val dx = worldX - target.x
            val dy = worldY - target.y
            val rx = target.rx
            val ry = target.ry
            if ((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1.0f) {
                when (target.type) {
                    0 -> { // Turtle
                        (target.obj as Turtle).touch(worldX, worldY)
                    }
                    1 -> { // Manta
                        (target.obj as Manta).triggerLoop()
                    }
                    2 -> { // Jellyfish
                        val jf = target.obj as Jellyfish
                        jf.triggerElectricity()
                        for (k in 0 until 12) {
                            spawnSparkleAt(jf.x, jf.y, 2)
                        }
                    }
                    3 -> { // Shark
                        if (configProvider.getRealisticModeEnabled()) {
                            huntNearestPrey()
                            if (!shark.isHunting) {
                                (target.obj as Shark).touch()
                            }
                        } else {
                            (target.obj as Shark).touch()
                        }
                    }
                    4 -> { // Axolotl
                        val ax = target.obj as Axolotl
                        synchronized(rocks) {
                            ax.touch(worldX, worldY, rocks)
                        }
                    }
                    5 -> { // TreasureChest
                        val chest = target.obj as TreasureChest
                        chest.touch()
                        val totalFauna = synchronized(fishes) { fishes.size } +
                                synchronized(turtles) { turtles.size } +
                                synchronized(mantas) { mantas.size } +
                                synchronized(axolotls) { axolotls.size } +
                                synchronized(jellyfishes) { jellyfishes.size }
                        if (configProvider.getRealisticModeEnabled() && totalFauna == 0 && !isRegeneratingFauna) {
                            isRegeneratingFauna = true
                            faunaRegenTimer = 0.4f // First animal enters quickly
                            repeat(6) {
                                spawnChestBubbles(chest.x * aspectRatio, chest.y + 0.06f, isGolden = true)
                            }
                            for (k in 0 until 18) {
                                spawnSparkleAt(chest.x * aspectRatio + (Random.nextFloat() - 0.5f) * 0.15f, chest.y + 0.05f + Random.nextFloat() * 0.12f, 1)
                            }
                        }
                    }
                    6 -> { // Starfish on glass
                        val s = target.obj as Starfish
                        s.touch(worldX, worldY, aspectRatio)
                        spawnChestBubbles(s.x, s.y, isGolden = (s.shinyType == 1))
                        for (k in 0 until 8) {
                            spawnSparkleAt(s.x, s.y, if (s.shinyType > 0) s.shinyType else 1)
                        }
                    }
                }
                animalTouched = true
                break // Only trigger the single topmost touched animal
            }
        }

        // Spawning food with 5-second cooldown - only when no animal was touched
        if (!animalTouched && feedingCooldownRemaining <= 0f) {
            feedingCooldownRemaining = kFeedingCooldownSeconds

            val realistic = configProvider.getRealisticModeEnabled()
            if (realistic) {
                synchronized(fishes) {
                    for (f in fishes) {
                        if (f.isStarving) f.feed()
                    }
                }
                synchronized(turtles) {
                    for (t in turtles) {
                        if (t.isStarving) t.feed()
                    }
                }
                synchronized(mantas) {
                    for (m in mantas) {
                        if (m.isStarving) m.feed()
                    }
                }
                synchronized(axolotls) {
                    for (ax in axolotls) {
                        if (ax.isStarving) ax.feed()
                    }
                }

                // If any population is below configured count, attract new animal from screen edge
                val desiredFish = configProvider.getFishCount().coerceIn(0, kMaxFish)
                synchronized(fishes) {
                    if (fishes.size < desiredFish) {
                        fishes.add(Fish().apply { spawnFromEdge(aspectRatio) })
                    }
                }
                val desiredTurtles = configProvider.getTurtleCount().coerceIn(0, kMaxTurtles)
                synchronized(turtles) {
                    if (turtles.size < desiredTurtles) {
                        turtles.add(Turtle().apply { spawnFromEdge(aspectRatio) })
                    }
                }
                val desiredMantas = configProvider.getMantaCount().coerceIn(0, kMaxMantas)
                synchronized(mantas) {
                    if (mantas.size < desiredMantas) {
                        mantas.add(Manta().apply { spawnFromEdge(aspectRatio) })
                    }
                }
                val desiredAxolotls = configProvider.getAxolotlCount().coerceIn(0, kMaxAxolotls)
                synchronized(axolotls) {
                    if (axolotls.size < desiredAxolotls) {
                        axolotls.add(Axolotl().apply { spawnFromEdge(aspectRatio) })
                    }
                }
            }

            // Clean up any previously targeted fish, just in case
            for (f in fishes) {
                if (f.isTargetingFood) {
                    f.stopTargetingFood(aspectRatio)
                }
            }

            // Setup 4 food particles with slight offsets
            for (i in 0 until 4) {
                val p = foodParticles[i]
                p.x = worldX - getParallaxX(0.2f) + (Random.nextFloat() - 0.5f) * 0.20f
                p.y = 1.1f + i * 0.12f
                p.active = true
            }

            // Find closest active food particle for each fish and set target
            for (i in fishes.indices) {
                val f = fishes[i]
                if (!f.isSpinning) {
                    var closestParticle: FoodParticle? = null
                    var minDistSq = Float.MAX_VALUE
                    for (j in 0 until foodParticles.size) {
                        val p = foodParticles[j]
                        if (p.active) {
                            val dx = p.x - f.x
                            val dy = p.y - f.y
                            val distSq = dx * dx + dy * dy
                            if (distSq < minDistSq) {
                                minDistSq = distSq
                                closestParticle = p
                             }
                        }
                    }

                    if (closestParticle != null) {
                        f.isTargetingFood = true
                        f.setTarget(closestParticle.x, closestParticle.y)
                    }
                }
            }
        }
    }

    private fun huntNearestPrey() {
        if (!shark.active || shark.isHunting) return

        var nearestPrey: Any? = null
        var nearestDistSq = Float.MAX_VALUE
        var targetX = 0f
        var targetY = 0f
        var targetDepth = 0.5f

        synchronized(fishes) {
            for (f in fishes) {
                val dx = f.x - shark.x
                val dy = f.y - shark.y
                val d2 = dx * dx + dy * dy
                if (d2 < nearestDistSq) {
                    nearestDistSq = d2
                    nearestPrey = f
                    targetX = f.x
                    targetY = f.y
                    targetDepth = f.depth
                }
            }
        }

        synchronized(turtles) {
            for (t in turtles) {
                val dx = t.x - shark.x
                val dy = t.y - shark.y
                val d2 = dx * dx + dy * dy
                if (d2 < nearestDistSq) {
                    nearestDistSq = d2
                    nearestPrey = t
                    targetX = t.x
                    targetY = t.y
                    targetDepth = t.depth
                }
            }
        }

        synchronized(mantas) {
            for (m in mantas) {
                val dx = m.x - shark.x
                val dy = m.y - shark.y
                val d2 = dx * dx + dy * dy
                if (d2 < nearestDistSq) {
                    nearestDistSq = d2
                    nearestPrey = m
                    targetX = m.x
                    targetY = m.y
                    targetDepth = m.depth
                }
            }
        }

        synchronized(axolotls) {
            for (ax in axolotls) {
                val dx = ax.x - shark.x
                val dy = ax.y - shark.y
                val d2 = dx * dx + dy * dy
                if (d2 < nearestDistSq) {
                    nearestDistSq = d2
                    nearestPrey = ax
                    targetX = ax.x
                    targetY = ax.y
                    targetDepth = ax.depth
                }
            }
        }

        synchronized(jellyfishes) {
            for (jf in jellyfishes) {
                val dx = jf.x - shark.x
                val dy = jf.y - shark.y
                val d2 = dx * dx + dy * dy
                if (d2 < nearestDistSq) {
                    nearestDistSq = d2
                    nearestPrey = jf
                    targetX = jf.x
                    targetY = jf.y
                    targetDepth = jf.depth
                }
            }
        }

        if (nearestPrey != null) {
            shark.startHunt(targetX, targetY, targetDepth, nearestPrey)
        }
    }

    override fun onUpdate(deltaTime: Float) {
        time += deltaTime
        if (time > 10000f) {
            time %= 6283.1853f
        }

        if (stormCooldownRemaining > 0f) {
            stormCooldownRemaining -= deltaTime
        }

        // Attack/sustain/release envelope for the background's storm visuals - see
        // stormVisualIntensity's own doc for why this runs on its own timer instead of tracking
        // the storm bubbles themselves.
        if (stormTimeRemaining > 0f) {
            stormTimeRemaining -= deltaTime
            val elapsed = kStormVisualDuration - stormTimeRemaining
            stormVisualIntensity = when {
                elapsed < kStormFadeInTime -> elapsed / kStormFadeInTime
                stormTimeRemaining < kStormFadeOutTime -> stormTimeRemaining / kStormFadeOutTime
                else -> 1f
            }.coerceIn(0f, 1f)
        } else {
            stormVisualIntensity = 0f
        }

        if (feedingCooldownRemaining > 0f) {
            feedingCooldownRemaining -= deltaTime
        }

        var activeParticlesCount = 0
        for (i in 0 until foodParticles.size) {
            val p = foodParticles[i]
            if (p.active) {
                p.y -= deltaTime * foodSpeed
                if (p.y < -1.1f) {
                    p.active = false
                } else {
                    activeParticlesCount++
                }
            }
        }

        if (activeParticlesCount > 0) {
            for (i in fishes.indices) {
                val f = fishes[i]
                if (!f.isSpinning) {
                    var closestParticle: FoodParticle? = null
                    var minDistSq = Float.MAX_VALUE
                    for (j in 0 until foodParticles.size) {
                        val p = foodParticles[j]
                        if (p.active) {
                            val dx = p.x - f.x
                            val dy = p.y - f.y
                            val distSq = dx * dx + dy * dy
                            if (distSq < minDistSq) {
                                minDistSq = distSq
                                closestParticle = p
                            }
                        }
                    }

                    if (closestParticle != null) {
                        if (minDistSq < 0.0064f) { // 0.08f squared = 0.0064f
                            closestParticle.active = false
                            f.feed()
                            f.startSpin()
                            f.stopTargetingFood(aspectRatio)
                        } else {
                            f.isTargetingFood = true
                            f.setTarget(closestParticle.x, closestParticle.y)
                        }
                    } else {
                        if (f.isTargetingFood) {
                            f.stopTargetingFood(aspectRatio)
                        }
                    }
                }
            }

            // Also allow turtles, mantas, and axolotls to eat sinking food particles
            synchronized(turtles) {
                for (t in turtles) {
                    for (p in foodParticles) {
                        if (p.active) {
                            val dx = p.x - t.x
                            val dy = p.y - t.y
                            if (dx * dx + dy * dy < 0.04f) {
                                p.active = false
                                t.feed()
                                spawnSparkleAt(t.x, t.y, 0)
                            }
                        }
                    }
                }
            }
            synchronized(mantas) {
                for (m in mantas) {
                    for (p in foodParticles) {
                        if (p.active) {
                            val dx = p.x - m.x
                            val dy = p.y - m.y
                            if (dx * dx + dy * dy < 0.05f) {
                                p.active = false
                                m.feed()
                                spawnSparkleAt(m.x, m.y, 0)
                            }
                        }
                    }
                }
            }
            synchronized(axolotls) {
                for (ax in axolotls) {
                    for (p in foodParticles) {
                        if (p.active) {
                            val dx = p.x - ax.x
                            val dy = p.y - ax.y
                            if (dx * dx + dy * dy < 0.04f) {
                                p.active = false
                                ax.feed()
                                spawnSparkleAt(ax.x, ax.y, 0)
                            }
                        }
                    }
                }
            }
        } else {
            for (i in fishes.indices) {
                val f = fishes[i]
                if (f.isTargetingFood) {
                    f.stopTargetingFood(aspectRatio)
                }
            }
        }


        val cycleDuration = configProvider.getDayNightCycleDuration()
        if (cycleDuration == -1) {
            val now = System.currentTimeMillis()
            val localMillis = now + java.util.TimeZone.getDefault().getOffset(now)
            val millisInDay = (localMillis % 86400000L + 86400000L) % 86400000L
            val fractionOfDay = millisInDay.toFloat() / 86400000f
            dayNight = (0.5 + 0.5 * kotlin.math.cos(2.0 * Math.PI * (fractionOfDay.toDouble() - 0.5))).toFloat()
        } else {
            val omega = (2.0 * Math.PI / cycleDuration.toDouble()).toFloat()
            dayNight = 0.5f + 0.5f * kotlin.math.sin(time * omega)
        }

        val offsetLerp = 1f - exp(-kParallaxEaseRate * deltaTime)
        offsetX += (targetOffsetX - offsetX) * offsetLerp
        parallaxRaw = offsetX - 0.5f
        foregroundParallax = parallaxRaw * kForegroundParallaxRange


        val realistic = configProvider.getRealisticModeEnabled()
        val totalFaunaCount = fishes.size + turtles.size + mantas.size + axolotls.size + jellyfishes.size
        val canRestoreFauna = realistic && totalFaunaCount == 0

        // Special beacon glow when chest can be tapped to restore depleted fauna
        treasureChest.beaconGlow = if (canRestoreFauna) 1.0f else 0.0f

        // Gradual restoration tick
        if (isRegeneratingFauna) {
            if (Random.nextFloat() < 0.35f) {
                spawnChestBubbles(treasureChest.x * aspectRatio, treasureChest.y + 0.05f, isGolden = true)
            }
            faunaRegenTimer += deltaTime
            if (faunaRegenTimer >= kFaunaRegenInterval) {
                faunaRegenTimer = 0f
                val spawned = spawnNextRegenAnimal()
                if (!spawned) {
                    isRegeneratingFauna = false
                }
            }
        }

        syncTurtleCount()
        syncFishCount()
        syncMantaCount()
        syncJellyfishCount()
        syncAxolotlCount()
        syncStarfishCount()
        lastRealisticMode = realistic
        syncPlants()
        syncBubbleCount()
        submarine.update(deltaTime, aspectRatio)

        shark.presenceMode = configProvider.getSharkPresence()
        shark.colorMode = configProvider.getSharkColor()
        shark.update(deltaTime, aspectRatio)

        if (realistic && shark.active && shark.isHunting && shark.huntTargetObject != null) {
            val prey = shark.huntTargetObject
            var preyStillExists = false
            var px = 0f
            var py = 0f
            var pDepth = 0.5f

            when (prey) {
                is Fish -> synchronized(fishes) {
                    if (fishes.contains(prey)) {
                        preyStillExists = true
                        px = prey.x
                        py = prey.y
                        pDepth = prey.depth
                    }
                }
                is Turtle -> synchronized(turtles) {
                    if (turtles.contains(prey)) {
                        preyStillExists = true
                        px = prey.x
                        py = prey.y
                        pDepth = prey.depth
                    }
                }
                is Manta -> synchronized(mantas) {
                    if (mantas.contains(prey)) {
                        preyStillExists = true
                        px = prey.x
                        py = prey.y
                        pDepth = prey.depth
                    }
                }
                is Axolotl -> synchronized(axolotls) {
                    if (axolotls.contains(prey)) {
                        preyStillExists = true
                        px = prey.x
                        py = prey.y
                        pDepth = prey.depth
                    }
                }
                is Jellyfish -> synchronized(jellyfishes) {
                    if (jellyfishes.contains(prey)) {
                        preyStillExists = true
                        px = prey.x
                        py = prey.y
                        pDepth = prey.depth
                    }
                }
            }

            if (!preyStillExists) {
                shark.finishHunt(false)
            } else {
                shark.updateHuntTarget(px, py, pDepth)
                val depthScale = kSharkScale * (1f - (1f - kMinScaleAtDepth) * shark.depth)
                val (headX, headY) = shark.getHeadWorldPosition(depthScale)
                val hdx = headX - px
                val hdy = headY - py
                val headDistSq = hdx * hdx + hdy * hdy
                val bdx = shark.x - px
                val bdy = shark.y - py
                val bodyDistSq = bdx * bdx + bdy * bdy

                // Head reaches over the prey (or body overlaps) before swallowing
                if (headDistSq < 0.038f || bodyDistSq < 0.035f) {
                    when (prey) {
                        is Fish -> synchronized(fishes) {
                            FaunaVariationManager.recordDepartedFish(prey.palette)
                            fishes.remove(prey)
                        }
                        is Turtle -> synchronized(turtles) {
                            FaunaVariationManager.recordDepartedTurtle(prey.palette)
                            turtles.remove(prey)
                        }
                        is Manta -> synchronized(mantas) {
                            FaunaVariationManager.recordDepartedManta(prey.palette)
                            mantas.remove(prey)
                        }
                        is Axolotl -> synchronized(axolotls) {
                            FaunaVariationManager.recordDepartedAxolotl(prey.palette)
                            axolotls.remove(prey)
                        }
                        is Jellyfish -> synchronized(jellyfishes) {
                            FaunaVariationManager.recordDepartedJellyfish(prey.palette)
                            jellyfishes.remove(prey)
                        }
                    }
                    spawnExhaleBubbles(headX, headY)
                    for (k in 0 until 12) {
                        spawnSparkleAt(headX, headY, 1)
                    }
                    shark.finishHunt(true)
                }
            }
        }

        synchronized(fishes) {
            for (i in fishes.indices) {
                val f = fishes[i]
                f.update(deltaTime, aspectRatio, realistic)
                if (f.shinyType > 0 && Random.nextFloat() < 0.02f) {
                    spawnSparkleAt(f.x, f.y, f.shinyType)
                }
            }
        }
        synchronized(mantas) {
            for (i in mantas.indices) {
                val m = mantas[i]
                m.update(deltaTime, aspectRatio, realistic)
                if (m.shinyType > 0 && Random.nextFloat() < 0.03f) {
                    spawnSparkleAt(m.x, m.y, m.shinyType)
                }
            }
        }
        synchronized(jellyfishes) {
            for (i in jellyfishes.indices) {
                val j = jellyfishes[i]
                j.update(deltaTime, aspectRatio)
                if (j.shinyType > 0 && Random.nextFloat() < 0.02f) {
                    spawnSparkleAt(j.x, j.y, j.shinyType)
                }
            }
        }
        synchronized(axolotls) {
            for (i in axolotls.indices) {
                val ax = axolotls[i]
                ax.update(deltaTime, aspectRatio, realistic)
                if (ax.shinyType > 0 && Random.nextFloat() < 0.02f) {
                    spawnSparkleAt(ax.x, ax.y, ax.shinyType)
                }
            }
        }
        synchronized(starfishes) {
            for (i in starfishes.indices) {
                val s = starfishes[i]
                s.update(deltaTime, aspectRatio)
                if (s.consumeBubblePuffEvent()) {
                    spawnChestBubbles(s.x, s.y, isGolden = (s.shinyType == 1))
                }
                if (s.shinyType > 0 && Random.nextFloat() < 0.02f) {
                    spawnSparkleAt(s.x, s.y, s.shinyType)
                }
            }
        }
        for (i in kelps.indices) {
            kelps[i].update(deltaTime)
        }
        for (i in anemones.indices) {
            anemones[i].update(deltaTime)
        }
        for (i in seaGrasses.indices) {
            seaGrasses[i].update(deltaTime)
        }
        for (i in corals.indices) {
            corals[i].update(deltaTime)
        }
        treasureChest.update(deltaTime, aspectRatio)
        if (treasureChest.shouldSpawnBubbles) {
            treasureChest.shouldSpawnBubbles = false
            spawnChestBubbles(treasureChest.x * aspectRatio, treasureChest.y + 0.05f, isGolden = isRegeneratingFauna)
        }
        synchronized(turtles) {
            for (i in turtles.indices) {
                val t = turtles[i]
                t.update(deltaTime, aspectRatio, realistic)
                if (t.consumeExhaleEvent()) {
                    spawnExhaleBubbles(t.x, t.y)
                }
                // consumePowerStrokeEvent() must run every frame regardless of the chance roll - it
                // consumes the pending flag - so it stays the left operand of this short-circuiting
                // &&, evaluated exactly once either way.
                if (t.consumePowerStrokeEvent() && Random.nextFloat() < kPropulsionBubbleChance) {
                    spawnPropulsionBubbles(t)
                }
                if (t.shinyType > 0 && Random.nextFloat() < 0.025f) {
                    spawnSparkleAt(t.x, t.y, t.shinyType)
                }
            }
        }

        // Remove starving animals that swam off-screen
        if (realistic) {
            synchronized(fishes) {
                val it = fishes.iterator()
                while (it.hasNext()) {
                    val f = it.next()
                    if (f.isStarving && f.isOutOfBounds(aspectRatio)) {
                        FaunaVariationManager.recordDepartedFish(f.palette)
                        it.remove()
                    }
                }
            }
            synchronized(mantas) {
                val it = mantas.iterator()
                while (it.hasNext()) {
                    val m = it.next()
                    if (m.isStarving && m.isOutOfBounds(aspectRatio)) {
                        FaunaVariationManager.recordDepartedManta(m.palette)
                        it.remove()
                    }
                }
            }
            synchronized(turtles) {
                val it = turtles.iterator()
                while (it.hasNext()) {
                    val t = it.next()
                    if (t.isStarving && t.isOutOfBounds(aspectRatio)) {
                        FaunaVariationManager.recordDepartedTurtle(t.palette)
                        it.remove()
                    }
                }
            }
            synchronized(axolotls) {
                val it = axolotls.iterator()
                while (it.hasNext()) {
                    val ax = it.next()
                    if (ax.isStarving && ax.isOutOfBounds(aspectRatio)) {
                        FaunaVariationManager.recordDepartedAxolotl(ax.palette)
                        it.remove()
                    }
                }
            }

            val remainingFauna = fishes.size + turtles.size + mantas.size + axolotls.size + jellyfishes.size
            val hasFauna = remainingFauna > 0
            shark.canSpawn = hasFauna
            if (!hasFauna) {
                if (shark.active) {
                    shark.retire()
                } else {
                    shark.spawnTimer = 0f
                }
            }
        } else {
            shark.canSpawn = true
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
                if (bubble.isStorm) activeStormBubbleCount--
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

    /**
     * "Bubble Pop": a one-off storm of [kBubbleStormCount] bubbles spawned across the whole tank
     * floor at once, called (via GLRenderThread's queue) when AcuarioWallpaperService detects a
     * deliberate shake. Gated by [stormCooldownRemaining]/[activeStormBubbleCount] - see their
     * shared doc - so it's a no-op while either the cooldown timer is still running or the
     * previous storm's bubbles haven't all risen off-screen yet, instead of stacking storms and
     * saturating the screen. Reuses the same one-off [burstBubbles] pool as exhale/propulsion
     * bubbles (tagged [Bubble.isStorm] so onUpdate()'s removal loop can track them separately),
     * so these drain away on their own the same way any other burst does - no separate cleanup
     * needed here. Also kicks off [stormTimeRemaining] - see its doc - so
     * acuario_background.frag briefly shows a choppier, cooler-tinted surface alongside the
     * bubbles.
     */
    override fun triggerBubbleStorm() {
        if (stormCooldownRemaining > 0f || activeStormBubbleCount > 0) return

        stormTimeRemaining = kStormVisualDuration

        for (n in 0 until kBubbleStormCount) {
            if (activeBurstCount >= kMaxBurstBubbles) break
            burstBubbles[activeBurstCount].apply {
                reset(
                    startX = Random.nextFloat() * (aspectRatio * 2f) - aspectRatio,
                    // Stagger start Y deep below screen so bubbles rise in a smooth continuous wave without a bottom flash
                    startY = -1.25f - Random.nextFloat() * 1.6f,
                    // Bigger and faster than ambient/propulsion bubbles - a churning storm, not
                    // the water's usual gentle trickle.
                    size = 0.025f + Random.nextFloat() * 0.045f,
                    speed = 0.30f + Random.nextFloat() * 0.35f,
                    wobbleAmplitude = 0.02f + Random.nextFloat() * 0.03f,
                    wobbleSpeed = 1.0f + Random.nextFloat() * 2.0f,
                    wobbleSeed = Random.nextFloat() * 6.2832f,
                    alpha = 0.25f + Random.nextFloat() * 0.25f
                )
                isStorm = true
            }
            activeBurstCount++
            activeStormBubbleCount++
        }

        stormCooldownRemaining = kBubbleStormCooldownSeconds
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
     * 1-2 small, subtle bubbles released right at [t]'s front flippers - the "push" of a
     * downstroke - reinforcing the effort of swimming. Only called on the ~30% of strokes that
     * pass [kPropulsionBubbleChance]'s roll, and even then a coin flip decides whether both
     * flippers get one or just a single (randomly chosen) one does, so a full pair is the
     * exception rather than the rule.
     */
    private fun spawnPropulsionBubbles(t: Turtle) {
        val top = Random.nextBoolean()
        t.frontFlipperWorldPosition(top = top, turtleScale = kTurtleScale, out = scratchFlipperPosition)
        spawnPropulsionBubbleAt(scratchFlipperPosition[0], scratchFlipperPosition[1])
        if (Random.nextBoolean()) {
            t.frontFlipperWorldPosition(top = !top, turtleScale = kTurtleScale, out = scratchFlipperPosition)
            spawnPropulsionBubbleAt(scratchFlipperPosition[0], scratchFlipperPosition[1])
        }
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

    private fun spawnSparkleAt(originX: Float, originY: Float, shinyType: Int) {
        if (!configProvider.getBioluminescenceEnabled() || activeBurstCount >= kMaxBurstBubbles) return
        val bubble = burstBubbles[activeBurstCount]
        bubble.reset(
            startX = originX + (Random.nextFloat() - 0.5f) * 0.12f,
            startY = originY + (Random.nextFloat() - 0.5f) * 0.08f,
            size = 0.006f + Random.nextFloat() * 0.010f,
            speed = 0.05f + Random.nextFloat() * 0.08f,
            wobbleAmplitude = 0.003f + Random.nextFloat() * 0.005f,
            wobbleSpeed = 2.0f + Random.nextFloat() * 3.0f,
            wobbleSeed = Random.nextFloat() * 6.2832f,
            alpha = 0.85f + Random.nextFloat() * 0.15f
        )
        if (shinyType == 1) { // Gold
            bubble.r = 1.0f
            bubble.g = 0.85f + Random.nextFloat() * 0.1f
            bubble.b = 0.1f + Random.nextFloat() * 0.1f
        } else if (shinyType == 2) { // Diamond
            val coin = Random.nextInt(3)
            when (coin) {
                0 -> { // White-cyan
                    bubble.r = 0.85f
                    bubble.g = 0.95f
                    bubble.b = 1.0f
                }
                1 -> { // Soft pink-white
                    bubble.r = 1.0f
                    bubble.g = 0.85f
                    bubble.b = 0.95f
                }
                else -> { // Diamond bright blue
                    bubble.r = 0.6f
                    bubble.g = 0.9f
                    bubble.b = 1.0f
                }
            }
        }
        activeBurstCount++
    }


    override fun onDrawFrame() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Read once and threaded through every draw*() call below as a parameter, instead of
        // each of them separately calling configProvider.getAcuarioTheme()/
        // getDeepColorForTheme() (8 redundant reads of the exact same value every single frame).
        val theme = configProvider.getAcuarioTheme()
        getDeepColorForTheme(theme, scratchDeepColor)
        val deepColor = scratchDeepColor
        // Apply night darkening to deepColor to match the background shader's night blending:
        // In the shader: baseColor * vec3(0.25, 0.30, 0.45)
        val nightR = 0.25f
        val nightG = 0.30f
        val nightB = 0.45f
        deepColor[0] = deepColor[0] * (nightR + (1f - nightR) * dayNight)
        deepColor[1] = deepColor[1] * (nightG + (1f - nightG) * dayNight)
        deepColor[2] = deepColor[2] * (nightB + (1f - nightB) * dayNight)

        drawBackground(theme)

        // Every draw call below this point (both plant-layer passes, every creature, and the
        // bubbles' shared per-instance quad) reads its base quad geometry from the same
        // unitQuadBuffer with the same layout - locations 0/1 are bound here ONCE for the whole
        // frame instead of separately in each draw*() function (as they used to be): vertex
        // attribute bindings are global GL state, not per-shader-program state, so rebinding the
        // exact same buffer/format 10 times per frame (once per creature/plant-layer/bubble
        // call, each immediately un-binding it again at its own end) was pure overhead that
        // never actually needed to change between these calls.
        unitQuadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(0)
        unitQuadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, unitQuadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        // 10-layer depth ordering:
        // 1. Far background: Submarine
        // 2. Sand seabed dunes (Escalón 0 / fondo)
        // 3. Seabed rocks
        // 4. Flora Escalón 0 (Fondo: siluetas lejanas, escala reducida, tinte de agua profunda)
        // 5. Deep swimming fauna (Mantas, Shark, Jellyfish)
        // 6. Flora Escalón 1 (Medio: repisa intermedia)
        // 7. Treasure Chest (descansa en el escalón 1)
        // 8. Mid/front swimming fauna (Fish, Turtles, Axolotls)
        // 9. ForeSand (banco de arena frontal del escalón 2)
        // 10. Flora Escalón 2 (Frente / Primer plano: gran escala, vivos, enmarcando la escena)
        // 11. Food, Starfish (adherida al cristal frontal) & Bubbles
        drawSubmarine(deepColor)
        drawSand(deepColor)
        drawRocks(deepColor)
        drawPlants(tier = 0, deepColor)
        drawMantas(deepColor)
        drawShark(deepColor)
        drawJellyfish(deepColor)
        drawPlants(tier = 1, deepColor)
        drawTreasureChest(deepColor)
        drawFish(deepColor)
        drawTurtles(deepColor)
        drawAxolotls(deepColor)
        drawForeSand(deepColor)
        drawPlants(tier = 2, deepColor)
        drawFood()
        drawStarfish(deepColor)
        drawBubbles()

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun spawnChestBubbles(originX: Float, originY: Float, isGolden: Boolean = false) {
        repeat(if (isGolden) 3 else 2) {
            if (activeBurstCount >= kMaxBurstBubbles) return@repeat
            val jitteredX = originX + (Random.nextFloat() - 0.5f) * 0.09f
            burstBubbles[activeBurstCount].reset(
                startX = jitteredX,
                startY = originY,
                size = if (isGolden) 0.030f + Random.nextFloat() * 0.038f else 0.025f + Random.nextFloat() * 0.035f,
                speed = 0.28f + Random.nextFloat() * 0.18f,
                wobbleAmplitude = 0.012f + Random.nextFloat() * 0.015f,
                wobbleSpeed = 1.2f + Random.nextFloat() * 1.5f,
                wobbleSeed = Random.nextFloat() * 6.2832f,
                alpha = if (isGolden) 0.88f + Random.nextFloat() * 0.12f else 0.70f + Random.nextFloat() * 0.25f,
                r = if (isGolden) 1.0f else 0.85f,
                g = if (isGolden) 0.82f else 0.95f,
                b = if (isGolden) 0.22f else 1.0f
            )
            activeBurstCount++
        }
        if (isGolden && Random.nextFloat() < 0.65f) {
            spawnSparkleAt(originX + (Random.nextFloat() - 0.5f) * 0.14f, originY + Random.nextFloat() * 0.08f, 1)
        }
    }

    private fun drawTreasureChest(deepColor: FloatArray) {
        if (treasureChestProgram == 0) return
        GLES30.glUseProgram(treasureChestProgram)
        GLES30.glUniform1f(treasureChestTimeHandle, time)
        GLES30.glUniform1f(treasureChestLidOpenHandle, treasureChest.lidOpen)
        GLES30.glUniform1f(treasureChestGlowHandle, treasureChest.glowIntensity)
        GLES30.glUniform1f(treasureChestDayNightHandle, dayNight)

        val palette = when (configProvider.getChestColor()) {
            0 -> TreasureChestPalette.CLASSIC_PIRATE
            1 -> TreasureChestPalette.ROYAL_VAULT
            2 -> TreasureChestPalette.ANCIENT_RELIC
            else -> TreasureChestPalette.CLASSIC_PIRATE
        }
        treasureChest.palette = palette

        val ambientFactor = 0.55f + 0.45f * dayNight
        val tintAmount = treasureChest.depth * kMaxDepthTint

        mixColorInto(scratchWoodColor, palette.woodColor, deepColor, tintAmount, ambientFactor)
        mixColorInto(scratchMetalColor, palette.metalColor, deepColor, tintAmount, ambientFactor)

        GLES30.glUniform3fv(treasureChestWoodColorHandle, 1, scratchWoodColor, 0)
        GLES30.glUniform3fv(treasureChestMetalColorHandle, 1, scratchMetalColor, 0)
        GLES30.glUniform3fv(treasureChestGoldColorHandle, 1, palette.goldColor, 0)
        GLES30.glUniform3fv(treasureChestGemColorHandle, 1, palette.gemColor, 0)
        GLES30.glUniform1f(treasureChestBeaconGlowHandle, treasureChest.beaconGlow)

        val depthScale = kTreasureChestScale * (1f - (1f - kMinScaleAtDepth) * treasureChest.depth)
        val chestWorldX = treasureChest.x * aspectRatio

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(
            modelMatrix, 0,
            chestWorldX + getParallaxX(treasureChest.depth),
            treasureChest.y + getParallaxY(treasureChest.depth),
            0f
        )
        Matrix.scaleM(modelMatrix, 0, depthScale * 1.32f, depthScale * 0.95f, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

        GLES30.glUniformMatrix4fv(treasureChestMVPHandle, 1, false, mvpMatrix, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawFood() {
        if (foodProgram == 0) return
        var hasActive = false
        for (i in 0 until foodParticles.size) {
            if (foodParticles[i].active) {
                hasActive = true
                break
            }
        }
        if (!hasActive) return
        GLES30.glUseProgram(foodProgram)

        for (i in 0 until foodParticles.size) {
            val p = foodParticles[i]
            if (p.active) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, p.x + getParallaxX(0.2f), p.y + getParallaxY(0.2f), 0f)
                Matrix.scaleM(modelMatrix, 0, kFoodScale, kFoodScale, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
                GLES30.glUniformMatrix4fv(foodMVPHandle, 1, false, mvpMatrix, 0)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }


    /**
     * Grows/shrinks [turtles] to match [ConfigProvider.getTurtleCount] (clamped to
     * [kMaxTurtles]). Only the size delta is touched - existing turtles keep their position/
     * palette/pitch when the count changes, new ones are freshly created, and a decrease just
     * drops turtles off the end.
     */
    private fun syncTurtleCount() {
        val realistic = configProvider.getRealisticModeEnabled()
        if (isFaunaWaitingForChest()) {
            lastConfiguredTurtleCount = 0
            return
        }
        val desired = configProvider.getTurtleCount().coerceIn(0, kMaxTurtles)
        synchronized(turtles) {
            if (!realistic || !lastRealisticMode) {
                when {
                    desired > turtles.size -> repeat(desired - turtles.size) { turtles.add(Turtle()) }
                    desired < turtles.size -> while (turtles.size > desired) turtles.removeAt(turtles.size - 1)
                }
            } else {
                if (desired < turtles.size) {
                    while (turtles.size > desired) turtles.removeAt(turtles.size - 1)
                } else if (!isRegeneratingFauna && lastConfiguredTurtleCount != -1 && desired > lastConfiguredTurtleCount) {
                    repeat(desired - turtles.size) {
                        turtles.add(Turtle().apply { spawnFromEdge(aspectRatio) })
                    }
                }
            }
        }
        lastConfiguredTurtleCount = desired
    }

    private fun syncFishCount() {
        val realistic = configProvider.getRealisticModeEnabled()
        if (isFaunaWaitingForChest()) {
            lastConfiguredFishCount = 0
            return
        }
        val desired = configProvider.getFishCount().coerceIn(0, kMaxFish)
        synchronized(fishes) {
            if (!realistic || !lastRealisticMode) {
                when {
                    desired > fishes.size -> repeat(desired - fishes.size) { fishes.add(Fish()) }
                    desired < fishes.size -> while (fishes.size > desired) fishes.removeAt(fishes.size - 1)
                }
            } else {
                if (desired < fishes.size) {
                    while (fishes.size > desired) fishes.removeAt(fishes.size - 1)
                } else if (!isRegeneratingFauna && lastConfiguredFishCount != -1 && desired > lastConfiguredFishCount) {
                    repeat(desired - fishes.size) {
                        fishes.add(Fish().apply { spawnFromEdge(aspectRatio) })
                    }
                }
            }
        }
        lastConfiguredFishCount = desired
    }

    private fun drawFish(deepColor: FloatArray) {
        synchronized(fishes) {
            if (fishes.isEmpty()) return

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

            for (i in fishes.indices) {
                val f = fishes[i]
                val type = f.fishType.coerceIn(0, 3)
                val prog = fishPrograms[type]
                if (prog == 0) continue

                GLES30.glUseProgram(prog)
                GLES30.glUniform1f(fishDayNightHandles[type], dayNight)

                // Depth-of-field illusion
                val depthScale = kFishScale * (1f - (1f - kMinScaleAtDepth) * f.depth)
                val tintAmount = f.depth * kMaxDepthTint

                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, f.x + getParallaxX(f.depth), f.y + getParallaxY(f.depth), 0f)
                Matrix.scaleM(modelMatrix, 0, f.facingScale(), 1f, 1f)
                Matrix.rotateM(modelMatrix, 0, f.pitchDegrees + f.spinAngle, 0f, 0f, 1f)
                Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
                GLES30.glUniformMatrix4fv(fishMVPHandles[type], 1, false, mvpMatrix, 0)

                GLES30.glUniform1f(fishSwimPhaseHandles[type], f.swimPhase % kTwoPi)

                val ambientFactor = 0.35f + 0.65f * dayNight
                val palette = f.palette
                mixColorInto(scratchBodyColor, palette.bodyColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchFinColor, palette.finColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchTailColor, palette.tailColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchStripeColor, palette.stripeColor, deepColor, tintAmount, ambientFactor)

                GLES30.glUniform3fv(fishBodyColorHandles[type], 1, scratchBodyColor, 0)
                GLES30.glUniform3fv(fishFinColorHandles[type], 1, scratchFinColor, 0)
                GLES30.glUniform3fv(fishTailColorHandles[type], 1, scratchTailColor, 0)
                GLES30.glUniform3fv(fishStripeColorHandles[type], 1, scratchStripeColor, 0)
                // Not depth-tinted like the colors above - bioluminescence is its own light source,
                // not reflected ambient light, so it doesn't fade toward deepColor with distance.
                val fishGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
                GLES30.glUniform3fv(fishGlowColorHandles[type], 1, fishGlow, 0)
                GLES30.glUniform1f(fishShinyTypeHandles[type], f.shinyType.toFloat())

                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }

    private fun syncMantaCount() {
        val realistic = configProvider.getRealisticModeEnabled()
        if (isFaunaWaitingForChest()) {
            lastConfiguredMantaCount = 0
            return
        }
        val desired = configProvider.getMantaCount().coerceIn(0, kMaxMantas)
        synchronized(mantas) {
            if (!realistic || !lastRealisticMode) {
                when {
                    desired > mantas.size -> repeat(desired - mantas.size) { mantas.add(Manta()) }
                    desired < mantas.size -> while (mantas.size > desired) mantas.removeAt(mantas.size - 1)
                }
            } else {
                if (desired < mantas.size) {
                    while (mantas.size > desired) mantas.removeAt(mantas.size - 1)
                } else if (!isRegeneratingFauna && lastConfiguredMantaCount != -1 && desired > lastConfiguredMantaCount) {
                    repeat(desired - mantas.size) {
                        mantas.add(Manta().apply { spawnFromEdge(aspectRatio) })
                    }
                }
            }
        }
        lastConfiguredMantaCount = desired
    }

    private fun drawMantas(deepColor: FloatArray) {
        if (mantaProgram == 0) return
        synchronized(mantas) {
            if (mantas.isEmpty()) return
            GLES30.glUseProgram(mantaProgram)
            GLES30.glUniform1f(mantaDayNightHandle, dayNight)

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

            for (i in mantas.indices) {
                val m = mantas[i]
                // Depth-of-field illusion
                val depthScale = kMantaScale * (1f - (1f - kMinScaleAtDepth) * m.depth)
                val tintAmount = m.depth * kMaxDepthTint

                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, m.x + m.loopOffsetX + getParallaxX(m.depth), m.y + m.loopOffsetY + getParallaxY(m.depth), 0f)
                Matrix.scaleM(modelMatrix, 0, m.facingScale(), 1f, 1f)
                Matrix.rotateM(modelMatrix, 0, m.pitchDegrees, 0f, 0f, 1f)
                Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
                GLES30.glUniformMatrix4fv(mantaMVPHandle, 1, false, mvpMatrix, 0)

                GLES30.glUniform1f(mantaSwimPhaseHandle, m.swimPhase % kTwoPi)

                val ambientFactor = 0.35f + 0.65f * dayNight
                val palette = m.palette
                mixColorInto(scratchMantaBodyColor, palette.bodyColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchMantaWingColor, palette.wingColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchMantaTailColor, palette.tailColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchMantaMarkingColor, palette.markingColor, deepColor, tintAmount, ambientFactor)

                GLES30.glUniform3fv(mantaBodyColorHandle, 1, scratchMantaBodyColor, 0)
                GLES30.glUniform3fv(mantaWingColorHandle, 1, scratchMantaWingColor, 0)
                GLES30.glUniform3fv(mantaTailColorHandle, 1, scratchMantaTailColor, 0)
                GLES30.glUniform3fv(mantaMarkingColorHandle, 1, scratchMantaMarkingColor, 0)
                // Not depth-tinted like the colors above - see the identical comment in the fish
                // draw loop.
                val mantaGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
                GLES30.glUniform3fv(mantaGlowColorHandle, 1, mantaGlow, 0)
                GLES30.glUniform1f(mantaShinyTypeHandle, m.shinyType.toFloat())

                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }


    private fun drawShark(deepColor: FloatArray) {
        if (!shark.active || sharkProgram == 0) return
        GLES30.glUseProgram(sharkProgram)
        GLES30.glUniform1f(sharkDayNightHandle, dayNight)

        val depthScale = kSharkScale * (1f - (1f - kMinScaleAtDepth) * shark.depth)
        val tintAmount = shark.depth * kMaxDepthTint

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, shark.x + getParallaxX(shark.depth), shark.y + getParallaxY(shark.depth), 0f)
        Matrix.scaleM(modelMatrix, 0, shark.facingScale(), 1f, 1f)
        Matrix.rotateM(modelMatrix, 0, shark.pitchDegrees, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(sharkMVPHandle, 1, false, mvpMatrix, 0)

        GLES30.glUniform1f(sharkSwimPhaseHandle, shark.swimPhase % kTwoPi)
        GLES30.glUniform1f(sharkBiteHandle, shark.biteProgress)
        GLES30.glUniform1f(sharkTimeHandle, time)

        val ambientFactor = 0.35f + 0.65f * dayNight

        // Color mapping:
        // 0: Gray, 1: Blue, 2: White/Albino, 3: Golden
        val rawColor = when (shark.colorMode) {
            1 -> kSharkColorBlue
            2 -> kSharkColorWhite
            3 -> kSharkColorGolden
            else -> kSharkColorGray
        }

        mixColorInto(scratchSharkColor, rawColor, deepColor, tintAmount, ambientFactor)
        GLES30.glUniform3fv(sharkBodyColorHandle, 1, scratchSharkColor, 0)

        val glowColor = when (shark.colorMode) {
            1 -> kSharkGlowBlue // Electric Blue
            2 -> kSharkGlowWhite // Cyan/Teal
            3 -> kSharkGlowGolden // Golden/Orange
            else -> kSharkGlowGray // Turquoise/Greenish-blue
        }
        val sharkGlow = if (configProvider.getBioluminescenceEnabled()) glowColor else kZeroGlow
        GLES30.glUniform3fv(sharkGlowColorHandle, 1, sharkGlow, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun syncJellyfishCount() {
        val realistic = configProvider.getRealisticModeEnabled()
        if (isFaunaWaitingForChest()) {
            return
        }
        val desired = configProvider.getJellyfishCount().coerceIn(0, kMaxJellyfish)
        synchronized(jellyfishes) {
            if (!realistic || !lastRealisticMode) {
                when {
                    desired > jellyfishes.size -> repeat(desired - jellyfishes.size) { jellyfishes.add(Jellyfish()) }
                    desired < jellyfishes.size -> while (jellyfishes.size > desired) jellyfishes.removeAt(jellyfishes.size - 1)
                }
            } else {
                if (desired < jellyfishes.size) {
                    while (jellyfishes.size > desired) jellyfishes.removeAt(jellyfishes.size - 1)
                }
            }
        }
    }

    private fun drawJellyfish(deepColor: FloatArray) {
        if (jellyfishProgram == 0 || jellyfishes.isEmpty()) return
        GLES30.glUseProgram(jellyfishProgram)
        GLES30.glUniform1f(jellyfishDayNightHandle, dayNight)
        GLES30.glUniform1f(jellyfishTimeHandle, time)

        // Farthest first, manual insertion sort to avoid allocation
        for (i in 1 until jellyfishes.size) {
            val key = jellyfishes[i]
            var j = i - 1
            while (j >= 0 && jellyfishes[j].depth < key.depth) {
                jellyfishes[j + 1] = jellyfishes[j]
                j--
            }
            jellyfishes[j + 1] = key
        }

        for (i in jellyfishes.indices) {
            val jf = jellyfishes[i]
            // Depth-of-field illusion
            val depthScale = kJellyfishScale * (1f - (1f - kMinScaleAtDepth) * jf.depth)
            val tintAmount = jf.depth * kMaxDepthTint

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, jf.x + getParallaxX(jf.depth), jf.y + getParallaxY(jf.depth), 0f)
            Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(jellyfishMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(jellyfishPulsePhaseHandle, jf.pulsePhase % kTwoPi)

            // Jellyfish are self-illuminating translucent creatures - apply light depth tint and high ambient factor
            // so their colors remain vibrant and luminous regardless of depth or time of day.
            val jellyTintAmount = jf.depth * 0.15f
            val jellyAmbientFactor = 0.85f + 0.15f * dayNight
            val palette = jf.palette
            mixColorInto(scratchJellyfishBellColor, palette.bellColor, deepColor, jellyTintAmount, jellyAmbientFactor)
            mixColorInto(scratchJellyfishMarginColor, palette.marginColor, deepColor, jellyTintAmount, jellyAmbientFactor)
            mixColorInto(scratchJellyfishTentacleColor, palette.tentacleColor, deepColor, jellyTintAmount, jellyAmbientFactor)
            mixColorInto(scratchJellyfishPatternColor, palette.patternColor, deepColor, jellyTintAmount, jellyAmbientFactor)

            GLES30.glUniform3fv(jellyfishBellColorHandle, 1, scratchJellyfishBellColor, 0)
            GLES30.glUniform3fv(jellyfishMarginColorHandle, 1, scratchJellyfishMarginColor, 0)
            GLES30.glUniform3fv(jellyfishTentacleColorHandle, 1, scratchJellyfishTentacleColor, 0)
            GLES30.glUniform3fv(jellyfishPatternColorHandle, 1, scratchJellyfishPatternColor, 0)
            // Not depth-tinted like the colors above - see the identical comment in the fish
            // draw loop.
            val jfGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
            GLES30.glUniform3fv(jellyfishGlowColorHandle, 1, jfGlow, 0)
            GLES30.glUniform1f(jellyfishShinyTypeHandle, jf.shinyType.toFloat())

            val intensity = if (jf.electricTimer > 0f) jf.electricTimer / 1.5f else 0f
            GLES30.glUniform1f(jellyfishElectricIntensityHandle, intensity)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    private fun syncAxolotlCount() {
        val realistic = configProvider.getRealisticModeEnabled()
        if (isFaunaWaitingForChest()) {
            lastConfiguredAxolotlCount = 0
            return
        }
        val desired = configProvider.getAxolotlCount().coerceIn(0, kMaxAxolotls)
        synchronized(axolotls) {
            if (!realistic || !lastRealisticMode) {
                when {
                    desired > axolotls.size -> repeat(desired - axolotls.size) { axolotls.add(Axolotl()) }
                    desired < axolotls.size -> while (axolotls.size > desired) axolotls.removeAt(axolotls.size - 1)
                }
            } else {
                if (desired < axolotls.size) {
                    while (axolotls.size > desired) axolotls.removeAt(axolotls.size - 1)
                } else if (!isRegeneratingFauna && lastConfiguredAxolotlCount != -1 && desired > lastConfiguredAxolotlCount) {
                    repeat(desired - axolotls.size) {
                        axolotls.add(Axolotl().apply { spawnFromEdge(aspectRatio) })
                    }
                }
            }
        }
        lastConfiguredAxolotlCount = desired
    }

    private fun drawAxolotls(deepColor: FloatArray) {
        if (axolotlProgram == 0) return
        synchronized(axolotls) {
            if (axolotls.isEmpty()) return
            GLES30.glUseProgram(axolotlProgram)
            GLES30.glUniform1f(axolotlDayNightHandle, dayNight)
            GLES30.glUniform1f(axolotlTimeHandle, time)

            // Farthest first, manual insertion sort to avoid allocation
            for (i in 1 until axolotls.size) {
                val key = axolotls[i]
                var j = i - 1
                while (j >= 0 && axolotls[j].depth < key.depth) {
                    axolotls[j + 1] = axolotls[j]
                    j--
                }
                axolotls[j + 1] = key
            }

            for (i in axolotls.indices) {
                val ax = axolotls[i]
                val depthScale = kAxolotlScale * (1f - (1f - kMinScaleAtDepth) * ax.depth)
                val tintAmount = ax.depth * kMaxDepthTint

                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, ax.x + getParallaxX(ax.depth), ax.y + getParallaxY(ax.depth), 0f)
                Matrix.scaleM(modelMatrix, 0, ax.facingScale(), 1f, 1f)
                Matrix.rotateM(modelMatrix, 0, ax.pitchDegrees, 0f, 0f, 1f)
                Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

                GLES30.glUniformMatrix4fv(axolotlMVPHandle, 1, false, mvpMatrix, 0)
                GLES30.glUniform1f(axolotlSwimPhaseHandle, ax.swimPhase % kTwoPi)
                GLES30.glUniform1f(axolotlGillPhaseHandle, ax.gillPhase % kTwoPi)
                GLES30.glUniform1f(axolotlLegPhaseHandle, ax.legPhase % kTwoPi)

                val ambientFactor = 0.35f + 0.65f * dayNight
                val palette = ax.palette
                mixColorInto(scratchAxolotlBodyColor, palette.bodyColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchAxolotlGillColor, palette.gillColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchAxolotlBellyColor, palette.bellyColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchAxolotlSpotColor, palette.spotColor, deepColor, tintAmount, ambientFactor)

                GLES30.glUniform3fv(axolotlBodyColorHandle, 1, scratchAxolotlBodyColor, 0)
                GLES30.glUniform3fv(axolotlGillColorHandle, 1, scratchAxolotlGillColor, 0)
                GLES30.glUniform3fv(axolotlBellyColorHandle, 1, scratchAxolotlBellyColor, 0)
                GLES30.glUniform3fv(axolotlSpotColorHandle, 1, scratchAxolotlSpotColor, 0)
                val axGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
                GLES30.glUniform3fv(axolotlGlowColorHandle, 1, axGlow, 0)
                GLES30.glUniform1f(axolotlShinyTypeHandle, ax.shinyType.toFloat())

                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }

    private fun syncStarfishCount() {
        val desired = configProvider.getStarfishCount().coerceIn(0, kMaxStarfish)
        synchronized(starfishes) {
            when {
                desired > starfishes.size -> {
                    repeat(desired - starfishes.size) {
                        val s = Starfish()
                        val maxMarginX = (aspectRatio - 0.14f).coerceIn(0.05f, (aspectRatio * 0.75f).coerceAtLeast(0.05f))
                        val rx = when (starfishes.size) {
                            0 -> -maxMarginX * 0.5f
                            1 -> maxMarginX * 0.5f
                            else -> 0f
                        }
                        val ry = when (starfishes.size) {
                            0 -> -0.30f
                            1 -> 0.25f
                            else -> -0.05f
                        }
                        s.setInitialPosition(rx, ry)
                        starfishes.add(s)
                    }
                }
                desired < starfishes.size -> {
                    while (starfishes.size > desired) {
                        starfishes.removeAt(starfishes.size - 1)
                    }
                }
            }
        }
    }

    private fun drawStarfish(deepColor: FloatArray) {
        if (starfishProgram == 0) return
        synchronized(starfishes) {
            if (starfishes.isEmpty()) return
            GLES30.glUseProgram(starfishProgram)
            GLES30.glUniform1f(starfishDayNightHandle, dayNight)
            GLES30.glUniform1f(starfishTimeHandle, time)

            for (i in starfishes.indices) {
                val s = starfishes[i]

                Matrix.setIdentityM(modelMatrix, 0)
                // Positioned on the glass, shifts naturally with front parallax
                Matrix.translateM(modelMatrix, 0, s.x + getParallaxX(0f), s.y, 0f)
                Matrix.rotateM(modelMatrix, 0, s.rotationDegrees, 0f, 0f, 1f)
                Matrix.scaleM(modelMatrix, 0, kStarfishScale, kStarfishScale, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

                GLES30.glUniformMatrix4fv(starfishMVPHandle, 1, false, mvpMatrix, 0)
                GLES30.glUniform1f(starfishCrawlPhaseHandle, s.crawlPhase)
                GLES30.glUniform1f(starfishTouchReactHandle, s.touchReact)
                GLES30.glUniform1f(starfishShinyTypeHandle, s.shinyType.toFloat())

                // Ambient light calculation (depth is 0.0f at the glass, so no water tint, just dayNight ambient)
                val ambientFactor = 0.40f + 0.60f * dayNight
                val palette = s.palette
                mixColorInto(scratchStarfishBodyColor, palette.bodyColor, deepColor, 0f, ambientFactor)
                mixColorInto(scratchStarfishDetailColor, palette.detailColor, deepColor, 0f, ambientFactor)
                mixColorInto(scratchStarfishSuctionColor, palette.suctionColor, deepColor, 0f, ambientFactor)

                GLES30.glUniform3fv(starfishBodyColorHandle, 1, scratchStarfishBodyColor, 0)
                GLES30.glUniform3fv(starfishDetailColorHandle, 1, scratchStarfishDetailColor, 0)
                GLES30.glUniform3fv(starfishSuctionColorHandle, 1, scratchStarfishSuctionColor, 0)

                val sfGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
                GLES30.glUniform3fv(starfishGlowColorHandle, 1, sfGlow, 0)

                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }

    private fun drawSubmarine(deepColor: FloatArray) {
        if (submarineProgram == 0 || !submarine.active) return
        GLES30.glUseProgram(submarineProgram)

        val tintAmount = submarine.depth * kMaxDepthTint
        val depthScale = kSubmarineScale * (1f - (1f - kMinScaleAtDepth) * submarine.depth)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, submarine.x + getParallaxX(submarine.depth), submarine.y + getParallaxY(submarine.depth), 0f)
        Matrix.scaleM(modelMatrix, 0, depthScale, depthScale, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(submarineMVPHandle, 1, false, mvpMatrix, 0)

        GLES30.glUniform1f(submarineSwimPhaseHandle, submarine.propellerPhase % kTwoPi)

        val ambientFactor = 0.35f + 0.65f * dayNight
        val baseBodyColor: FloatArray
        val baseAccentColor: FloatArray
        val baseWindowColor: FloatArray

        when (configProvider.getSubmarineColor()) {
            0 -> { // Amarillo
                baseBodyColor = kSubmarineBodyColorAmarillo
                baseAccentColor = kSubmarineAccentColorAmarillo
                baseWindowColor = kSubmarineWindowColorAmarillo
            }
            1 -> { // Rojo
                baseBodyColor = kSubmarineBodyColorRojo
                baseAccentColor = kSubmarineAccentColorRojo
                baseWindowColor = kSubmarineWindowColorRojo
            }
            2 -> { // Azul
                baseBodyColor = kSubmarineBodyColorAzul
                baseAccentColor = kSubmarineAccentColorAzul
                baseWindowColor = kSubmarineWindowColorAzul
            }
            3 -> { // Verde
                baseBodyColor = kSubmarineBodyColorVerde
                baseAccentColor = kSubmarineAccentColorVerde
                baseWindowColor = kSubmarineWindowColorVerde
            }
            4 -> { // Rosa
                baseBodyColor = kSubmarineBodyColorRosa
                baseAccentColor = kSubmarineAccentColorRosa
                baseWindowColor = kSubmarineWindowColorRosa
            }
            5 -> { // Naranja
                baseBodyColor = kSubmarineBodyColorNaranja
                baseAccentColor = kSubmarineAccentColorNaranja
                baseWindowColor = kSubmarineWindowColorNaranja
            }
            else -> {
                baseBodyColor = kSubmarineBodyColorAmarillo
                baseAccentColor = kSubmarineAccentColorAmarillo
                baseWindowColor = kSubmarineWindowColorAmarillo
            }
        }

        mixColorInto(scratchSubmarineBodyColor, baseBodyColor, deepColor, tintAmount, ambientFactor)
        mixColorInto(scratchSubmarineAccentColor, baseAccentColor, deepColor, tintAmount, ambientFactor)
        // Windows keep glowing bright at night by avoiding dark deep-water tinting when it's dark!
        val windowTint = tintAmount * dayNight
        mixColorInto(scratchSubmarineWindowColor, baseWindowColor, deepColor, windowTint, 1.0f)

        GLES30.glUniform3fv(submarineBodyColorHandle, 1, scratchSubmarineBodyColor, 0)
        GLES30.glUniform3fv(submarineAccentColorHandle, 1, scratchSubmarineAccentColor, 0)
        GLES30.glUniform3fv(submarineWindowColorHandle, 1, scratchSubmarineWindowColor, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /**
     * Rebuilds [kelps]/[anemones]/[seaGrasses]/[corals] from scratch whenever
     * [ConfigProvider.getPlantDensity] or
     * [aspectRatio] changes (tracked via [currentPlantDensity]/[plantLayoutAspectRatio]) - unlike
     * the wandering creatures' sync*Count() functions, which only touch the size delta because
     * existing ones keep wandering from wherever they already are, a plant's position IS its
     * whole state: there's nothing worth preserving across a resize, and a full relayout is the
     * simplest way to keep them evenly spread across the (possibly now different) tank width.
     *
     * Each list is also sorted farthest-first (descending depth) right here, once, and split
     * into a back-layer/front-layer count (see [kelpBackCount] etc.'s doc) - since a plant's
     * depth never changes after this, that ordering/split stays valid for as long as the layout
     * does, so the draw*(backLayer) functions don't need to re-sort or re-filter every frame the
     * way the wandering creatures (whose depth actually drifts over time) still have to.
     */
    private fun syncPlants() {
        val desired = configProvider.getPlantDensity().coerceIn(0, kMaxPlantDensity)
        if (desired == currentPlantDensity && aspectRatio == plantLayoutAspectRatio) return

        val kelpCount = (desired * kKelpShareOfDensity).toInt()
        val anemoneCount = (desired * kAnemoneShareOfDensity).toInt()
        val seaGrassCount = (desired * kSeaGrassShareOfDensity).toInt()
        // Whatever's left after the three shares above, so rounding always adds up to the full
        // requested density instead of possibly dropping a plant to rounding error.
        val coralCount = desired - kelpCount - anemoneCount - seaGrassCount

        var plantIndex = 0

        // 3 stepped tiers (escalones de fondo marino con perspectiva de profundidad pronunciada):
        // Tier 0: Escalón superior / fondo (depth 0.74..0.86, y ≈ -0.60f, scaleMultiplier 0.55..0.70)
        // Tier 1: Escalón intermedio (depth 0.45..0.55, y ≈ -0.75f, nivel del cofre, scaleMultiplier 0.90..1.05)
        // Tier 2: Escalón inferior / frente (depth 0.12..0.22, y ≈ -0.90f, scaleMultiplier 1.35..1.60)
        data class PlantStepSpec(val tier: Int, val y: Float, val depth: Float, val scaleMultiplier: Float)

        fun getPlantStepTier(index: Int): PlantStepSpec {
            val tier = index % 3
            return when (tier) {
                0 -> PlantStepSpec(
                    tier = 0,
                    y = -0.60f + (Random.nextFloat() - 0.5f) * 0.03f,
                    depth = 0.74f + Random.nextFloat() * 0.12f,
                    scaleMultiplier = 0.55f + Random.nextFloat() * 0.15f
                )
                1 -> PlantStepSpec(
                    tier = 1,
                    y = -0.75f + (Random.nextFloat() - 0.5f) * 0.03f,
                    depth = 0.45f + Random.nextFloat() * 0.10f,
                    scaleMultiplier = 0.95f + Random.nextFloat() * 0.15f
                )
                else -> PlantStepSpec(
                    tier = 2,
                    y = -0.90f + (Random.nextFloat() - 0.5f) * 0.02f,
                    depth = 0.12f + Random.nextFloat() * 0.10f,
                    scaleMultiplier = 1.35f + Random.nextFloat() * 0.25f
                )
            }
        }

        fun getTierX(tier: Int): Float {
            var x = Random.nextFloat() * (aspectRatio * 1.8f) - aspectRatio * 0.9f
            // En el escalón intermedio (Tier 1), separar ligeramente del cofre para no taparlo en su misma repisa
            val chestX = treasureChest.x * aspectRatio
            if (tier == 1 && kotlin.math.abs(x - chestX) < 0.20f) {
                x += if (x < chestX) -0.20f else 0.20f
            }
            return x
        }

        kelps.clear()
        repeat(kelpCount) {
            val spec = getPlantStepTier(plantIndex++)
            val kelp = Kelp()
            kelp.placeAt(
                x = getTierX(spec.tier),
                depth = spec.depth,
                heightScale = spec.scaleMultiplier * (0.85f + Random.nextFloat() * 0.30f),
                y = spec.y
            )
            kelps.add(kelp)
        }

        anemones.clear()
        repeat(anemoneCount) {
            val spec = getPlantStepTier(plantIndex++)
            val anemone = Anemone()
            anemone.placeAt(
                x = getTierX(spec.tier),
                depth = spec.depth,
                scale = spec.scaleMultiplier * (0.85f + Random.nextFloat() * 0.30f),
                y = spec.y
            )
            anemones.add(anemone)
        }

        seaGrasses.clear()
        repeat(seaGrassCount) {
            val spec = getPlantStepTier(plantIndex++)
            val seaGrass = SeaGrass()
            seaGrass.placeAt(
                x = getTierX(spec.tier),
                depth = spec.depth,
                heightScale = spec.scaleMultiplier * (0.85f + Random.nextFloat() * 0.30f),
                y = spec.y
            )
            seaGrasses.add(seaGrass)
        }

        corals.clear()
        repeat(coralCount) {
            val spec = getPlantStepTier(plantIndex++)
            val coral = Coral()
            coral.placeAt(
                x = getTierX(spec.tier),
                depth = spec.depth,
                scale = spec.scaleMultiplier * (0.85f + Random.nextFloat() * 0.30f),
                y = spec.y
            )
            corals.add(coral)
        }

        kelps.sortByDescending { it.depth }
        anemones.sortByDescending { it.depth }
        seaGrasses.sortByDescending { it.depth }
        corals.sortByDescending { it.depth }

        kelpTier1Index = kelps.indexOfFirst { it.depth < kTier0DepthThreshold }.let { if (it == -1) kelps.size else it }
        kelpTier2Index = kelps.indexOfFirst { it.depth < kTier1DepthThreshold }.let { if (it == -1) kelps.size else it }

        anemoneTier1Index = anemones.indexOfFirst { it.depth < kTier0DepthThreshold }.let { if (it == -1) anemones.size else it }
        anemoneTier2Index = anemones.indexOfFirst { it.depth < kTier1DepthThreshold }.let { if (it == -1) anemones.size else it }

        seaGrassTier1Index = seaGrasses.indexOfFirst { it.depth < kTier0DepthThreshold }.let { if (it == -1) seaGrasses.size else it }
        seaGrassTier2Index = seaGrasses.indexOfFirst { it.depth < kTier1DepthThreshold }.let { if (it == -1) seaGrasses.size else it }

        coralTier1Index = corals.indexOfFirst { it.depth < kTier0DepthThreshold }.let { if (it == -1) corals.size else it }
        coralTier2Index = corals.indexOfFirst { it.depth < kTier1DepthThreshold }.let { if (it == -1) corals.size else it }

        currentPlantDensity = desired
        plantLayoutAspectRatio = aspectRatio
        syncRocks()
    }

    private fun syncRocks() {
        synchronized(rocks) {
            rocks.clear()
            val rockCount = 18
            val span = aspectRatio * 2.2f
            val step = span / (rockCount - 1)
            val startX = -aspectRatio * 1.1f

            repeat(rockCount) { i ->
                val rock = Rock()
                val xPos = startX + i * step + (Random.nextFloat() * 0.10f - 0.05f)
                val depth = Random.nextFloat()
                val scaleX = 0.52f + Random.nextFloat() * 0.44f
                val scaleY = 0.28f + Random.nextFloat() * 0.22f
                val shapeType = i % 4
                val rotation = (Random.nextFloat() * 0.20f - 0.10f) * 57.2958f
                rock.placeAt(xPos, depth, scaleX, scaleY, shapeType, rotation)
                rocks.add(rock)
            }
            rocks.sortByDescending { it.depth }
        }
    }

    private fun drawSand(deepColor: FloatArray) {
        if (sandProgram == 0) return
        GLES30.glUseProgram(sandProgram)

        val ambientFactor = 0.55f + 0.45f * dayNight
        val tintAmount = 0.14f

        mixColorInto(scratchSandBaseColor, kSandBaseColor, deepColor, tintAmount, ambientFactor)
        mixColorInto(scratchSandHighlightColor, kSandHighlightColor, deepColor, tintAmount, ambientFactor)
        mixColorInto(scratchSandShadowColor, kSandShadowColor, deepColor, tintAmount, ambientFactor)

        GLES30.glUniform1f(sandDayNightHandle, dayNight)
        GLES30.glUniform1f(sandTimeHandle, time)
        GLES30.glUniform1f(sandAspectHandle, aspectRatio)
        GLES30.glUniform3fv(sandBaseColorHandle, 1, scratchSandBaseColor, 0)
        GLES30.glUniform3fv(sandHighlightColorHandle, 1, scratchSandHighlightColor, 0)
        GLES30.glUniform3fv(sandShadowColorHandle, 1, scratchSandShadowColor, 0)
        GLES30.glUniform3fv(sandDeepColorHandle, 1, deepColor, 0)
        GLES30.glUniform1i(sandIsForegroundHandle, 0)
        GLES30.glUniform1f(sandBioluminescenceHandle, if (configProvider.getBioluminescenceEnabled()) 1.0f else 0.0f)

        val sandWidth = aspectRatio * 4.2f
        val sandHeight = 0.95f
        val sandCenterY = -0.72f
        val sandDepth = 0.75f

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(
            modelMatrix, 0,
            getParallaxX(sandDepth),
            sandCenterY + getParallaxY(sandDepth),
            0f
        )
        Matrix.scaleM(modelMatrix, 0, sandWidth, sandHeight, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

        GLES30.glUniformMatrix4fv(sandMVPHandle, 1, false, mvpMatrix, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /**
     * Draws a smaller, foreground layer of seabed sand directly in front of the flora, strictly
     * covering only ~10% above the flora baseline (anchored at [kPlantFloorY] = -0.80f) with the
     * remaining ~90% extending downward to anchor the seabed to the bottom of the screen.
     */
    private fun drawForeSand(deepColor: FloatArray) {
        if (sandProgram == 0) return
        GLES30.glUseProgram(sandProgram)

        val ambientFactor = 0.60f + 0.40f * dayNight
        val tintAmount = 0.06f

        mixColorInto(scratchSandBaseColor, kSandBaseColor, deepColor, tintAmount, ambientFactor)
        mixColorInto(scratchSandHighlightColor, kSandHighlightColor, deepColor, tintAmount, ambientFactor)
        mixColorInto(scratchSandShadowColor, kSandShadowColor, deepColor, tintAmount, ambientFactor)

        GLES30.glUniform1f(sandDayNightHandle, dayNight)
        GLES30.glUniform1f(sandTimeHandle, time)
        GLES30.glUniform1f(sandAspectHandle, aspectRatio)
        GLES30.glUniform3fv(sandBaseColorHandle, 1, scratchSandBaseColor, 0)
        GLES30.glUniform3fv(sandHighlightColorHandle, 1, scratchSandHighlightColor, 0)
        GLES30.glUniform3fv(sandShadowColorHandle, 1, scratchSandShadowColor, 0)
        GLES30.glUniform3fv(sandDeepColorHandle, 1, deepColor, 0)
        GLES30.glUniform1i(sandIsForegroundHandle, 1)
        GLES30.glUniform1f(sandBioluminescenceHandle, if (configProvider.getBioluminescenceEnabled()) 1.0f else 0.0f)

        val sandWidth = aspectRatio * 4.5f
        val sandHeight = 0.28f
        val sandCenterY = -0.96f
        val sandDepth = 0.12f

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(
            modelMatrix, 0,
            getParallaxX(sandDepth),
            sandCenterY + getParallaxY(sandDepth),
            0f
        )
        Matrix.scaleM(modelMatrix, 0, sandWidth, sandHeight, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

        GLES30.glUniformMatrix4fv(sandMVPHandle, 1, false, mvpMatrix, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawRocks(deepColor: FloatArray) {
        if (rockProgram == 0) return
        synchronized(rocks) {
            if (rocks.isEmpty()) return
            GLES30.glUseProgram(rockProgram)
            GLES30.glUniform1f(rockDayNightHandle, dayNight)
            GLES30.glUniform1f(rockTimeHandle, time)
            GLES30.glUniform1f(rockAspectHandle, aspectRatio)
            GLES30.glUniform1f(rockBioluminescenceHandle, if (configProvider.getBioluminescenceEnabled()) 1.0f else 0.0f)

            for (i in rocks.indices) {
                val r = rocks[i]
                val depthScale = 1f - (1f - kMinScaleAtDepth) * r.depth
                val tintAmount = r.depth * kMaxDepthTint

                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(
                    modelMatrix, 0,
                    r.x + getParallaxX(r.depth),
                    kRockFloorY + r.scaleY * 0.5f * depthScale + getParallaxY(r.depth),
                    0f
                )
                Matrix.rotateM(modelMatrix, 0, r.rotationDegrees, 0f, 0f, 1f)
                Matrix.scaleM(modelMatrix, 0, r.scaleX * depthScale, r.scaleY * depthScale, 1f)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

                GLES30.glUniformMatrix4fv(rockMVPHandle, 1, false, mvpMatrix, 0)
                GLES30.glUniform1f(rockShapeTypeHandle, r.shapeType.toFloat())

                val ambientFactor = 0.35f + 0.65f * dayNight
                val palette = r.palette
                mixColorInto(scratchRockBaseColor, palette.baseColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchRockHighlightColor, palette.highlightColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchRockShadowColor, palette.shadowColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchRockSpotColor, palette.spotColor, deepColor, tintAmount, ambientFactor)

                GLES30.glUniform3fv(rockBaseColorHandle, 1, scratchRockBaseColor, 0)
                GLES30.glUniform3fv(rockHighlightColorHandle, 1, scratchRockHighlightColor, 0)
                GLES30.glUniform3fv(rockShadowColorHandle, 1, scratchRockShadowColor, 0)
                GLES30.glUniform3fv(rockSpotColorHandle, 1, scratchRockSpotColor, 0)
                val rockGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
                GLES30.glUniform3fv(rockGlowColorHandle, 1, rockGlow, 0)

                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }

    /**
     * Draws vegetation split into 3 stepped tiers (Escalón 0 = Fondo, Escalón 1 = Medio,
     * Escalón 2 = Frente / Primer plano) around the seabed terraces, treasure chest, and creatures.
     */
    private fun drawPlants(tier: Int, deepColor: FloatArray) {
        drawCorals(tier, deepColor)
        drawKelp(tier, deepColor)
        drawSeaGrass(tier, deepColor)
        drawAnemones(tier, deepColor)
    }

    private fun drawKelp(tier: Int, deepColor: FloatArray) {
        val startIndex = when (tier) {
            0 -> 0
            1 -> kelpTier1Index
            else -> kelpTier2Index
        }
        val endIndex = when (tier) {
            0 -> kelpTier1Index
            1 -> kelpTier2Index
            else -> kelps.size
        }
        if (kelpProgram == 0 || startIndex >= endIndex) return
        GLES30.glUseProgram(kelpProgram)
        GLES30.glUniform1f(kelpDayNightHandle, dayNight)
        GLES30.glUniform1f(kelpTimeHandle, time)

        for (i in startIndex until endIndex) {
            val k = kelps[i]
            val depthScale = 1f - (1f - kMinScaleAtDepth) * k.depth
            val tintAmount = k.depth * kMaxDepthTint
            val width = kKelpWidth * depthScale
            val height = kKelpBaseHeight * k.heightScale * depthScale

            Matrix.setIdentityM(modelMatrix, 0)
            // Anchored at individual plant seabed floor position:
            Matrix.translateM(modelMatrix, 0, k.x + getParallaxX(k.depth), k.y + height * 0.5f + getParallaxY(k.depth), 0f)
            Matrix.scaleM(modelMatrix, 0, width, height, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(kelpMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(kelpSwayPhaseHandle, k.swayPhase % kTwoPi)

            val ambientFactor = 0.35f + 0.65f * dayNight
            val palette = k.palette
            mixColorInto(scratchKelpBladeColor, palette.bladeColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchKelpTipColor, palette.tipColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchKelpBaseColor, palette.baseColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchKelpHighlightColor, palette.highlightColor, deepColor, tintAmount, ambientFactor)

            GLES30.glUniform3fv(kelpBladeColorHandle, 1, scratchKelpBladeColor, 0)
            GLES30.glUniform3fv(kelpTipColorHandle, 1, scratchKelpTipColor, 0)
            GLES30.glUniform3fv(kelpBaseColorHandle, 1, scratchKelpBaseColor, 0)
            GLES30.glUniform3fv(kelpHighlightColorHandle, 1, scratchKelpHighlightColor, 0)
            // Not depth-tinted like the colors above - see the identical comment in the fish
            // draw loop.
            val kelpGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
            GLES30.glUniform3fv(kelpGlowColorHandle, 1, kelpGlow, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    private fun drawAnemones(tier: Int, deepColor: FloatArray) {
        val startIndex = when (tier) {
            0 -> 0
            1 -> anemoneTier1Index
            else -> anemoneTier2Index
        }
        val endIndex = when (tier) {
            0 -> anemoneTier1Index
            1 -> anemoneTier2Index
            else -> anemones.size
        }
        if (anemoneProgram == 0 || startIndex >= endIndex) return
        GLES30.glUseProgram(anemoneProgram)
        GLES30.glUniform1f(anemoneDayNightHandle, dayNight)
        GLES30.glUniform1f(anemoneTimeHandle, time)

        for (i in startIndex until endIndex) {
            val a = anemones[i]
            val depthScale = 1f - (1f - kMinScaleAtDepth) * a.depth
            val tintAmount = a.depth * kMaxDepthTint
            val size = kAnemoneSize * a.scale * depthScale

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, a.x + getParallaxX(a.depth), a.y + size * 0.5f + getParallaxY(a.depth), 0f)
            Matrix.scaleM(modelMatrix, 0, size, size, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(anemoneMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(anemoneSwayPhaseHandle, a.swayPhase % kTwoPi)

            val ambientFactor = 0.35f + 0.65f * dayNight
            val palette = a.palette
            mixColorInto(scratchAnemoneFootColor, palette.footColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchAnemoneTentacleColor, palette.tentacleColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchAnemoneTipColor, palette.tipColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchAnemoneHighlightColor, palette.highlightColor, deepColor, tintAmount, ambientFactor)

            GLES30.glUniform3fv(anemoneFootColorHandle, 1, scratchAnemoneFootColor, 0)
            GLES30.glUniform3fv(anemoneTentacleColorHandle, 1, scratchAnemoneTentacleColor, 0)
            GLES30.glUniform3fv(anemoneTipColorHandle, 1, scratchAnemoneTipColor, 0)
            GLES30.glUniform3fv(anemoneHighlightColorHandle, 1, scratchAnemoneHighlightColor, 0)
            // Not depth-tinted like the colors above - see the identical comment in the fish
            // draw loop.
            val anemoneGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
            GLES30.glUniform3fv(anemoneGlowColorHandle, 1, anemoneGlow, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    private fun drawSeaGrass(tier: Int, deepColor: FloatArray) {
        val startIndex = when (tier) {
            0 -> 0
            1 -> seaGrassTier1Index
            else -> seaGrassTier2Index
        }
        val endIndex = when (tier) {
            0 -> seaGrassTier1Index
            1 -> seaGrassTier2Index
            else -> seaGrasses.size
        }
        if (seaGrassProgram == 0 || startIndex >= endIndex) return
        GLES30.glUseProgram(seaGrassProgram)
        GLES30.glUniform1f(seaGrassDayNightHandle, dayNight)
        GLES30.glUniform1f(seaGrassTimeHandle, time)

        for (i in startIndex until endIndex) {
            val g = seaGrasses[i]
            val depthScale = 1f - (1f - kMinScaleAtDepth) * g.depth
            val tintAmount = g.depth * kMaxDepthTint
            val width = kSeaGrassWidth * depthScale
            val height = kSeaGrassBaseHeight * g.heightScale * depthScale

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, g.x + getParallaxX(g.depth), g.y + height * 0.5f + getParallaxY(g.depth), 0f)
            Matrix.scaleM(modelMatrix, 0, width, height, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(seaGrassMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(seaGrassSwayPhaseHandle, g.swayPhase % kTwoPi)

            val ambientFactor = 0.35f + 0.65f * dayNight
            val palette = g.palette
            mixColorInto(scratchSeaGrassBladeColor, palette.bladeColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchSeaGrassTipColor, palette.tipColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchSeaGrassBaseColor, palette.baseColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchSeaGrassHighlightColor, palette.highlightColor, deepColor, tintAmount, ambientFactor)

            GLES30.glUniform3fv(seaGrassBladeColorHandle, 1, scratchSeaGrassBladeColor, 0)
            GLES30.glUniform3fv(seaGrassTipColorHandle, 1, scratchSeaGrassTipColor, 0)
            GLES30.glUniform3fv(seaGrassBaseColorHandle, 1, scratchSeaGrassBaseColor, 0)
            GLES30.glUniform3fv(seaGrassHighlightColorHandle, 1, scratchSeaGrassHighlightColor, 0)
            // Not depth-tinted like the colors above - see the identical comment in the fish
            // draw loop.
            val seaGrassGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
            GLES30.glUniform3fv(seaGrassGlowColorHandle, 1, seaGrassGlow, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    private fun drawCorals(tier: Int, deepColor: FloatArray) {
        val startIndex = when (tier) {
            0 -> 0
            1 -> coralTier1Index
            else -> coralTier2Index
        }
        val endIndex = when (tier) {
            0 -> coralTier1Index
            1 -> coralTier2Index
            else -> corals.size
        }
        if (coralProgram == 0 || startIndex >= endIndex) return
        GLES30.glUseProgram(coralProgram)
        GLES30.glUniform1f(coralDayNightHandle, dayNight)
        GLES30.glUniform1f(coralTimeHandle, time)

        for (i in startIndex until endIndex) {
            val c = corals[i]
            val depthScale = 1f - (1f - kMinScaleAtDepth) * c.depth
            val tintAmount = c.depth * kMaxDepthTint
            val size = kCoralSize * c.scale * depthScale

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, c.x + getParallaxX(c.depth), c.y + size * 0.5f + getParallaxY(c.depth), 0f)
            Matrix.scaleM(modelMatrix, 0, size, size, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(coralMVPHandle, 1, false, mvpMatrix, 0)

            GLES30.glUniform1f(coralSwayPhaseHandle, c.swayPhase % kTwoPi)

            val ambientFactor = 0.35f + 0.65f * dayNight
            val palette = c.palette
            mixColorInto(scratchCoralBaseColor, palette.baseColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchCoralBranchColor, palette.branchColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchCoralPolypColor, palette.polypColor, deepColor, tintAmount, ambientFactor)
            mixColorInto(scratchCoralHighlightColor, palette.highlightColor, deepColor, tintAmount, ambientFactor)

            GLES30.glUniform3fv(coralBaseColorHandle, 1, scratchCoralBaseColor, 0)
            GLES30.glUniform3fv(coralBranchColorHandle, 1, scratchCoralBranchColor, 0)
            GLES30.glUniform3fv(coralPolypColorHandle, 1, scratchCoralPolypColor, 0)
            GLES30.glUniform3fv(coralHighlightColorHandle, 1, scratchCoralHighlightColor, 0)
            // Not depth-tinted like the colors above - see the identical comment in the fish
            // draw loop.
            val coralGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
            GLES30.glUniform3fv(coralGlowColorHandle, 1, coralGlow, 0)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
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
                bubbles.add(Bubble().apply { resetRandom(spawnNewOnesAnywhere, aspectRatio) })
            }
            desired < bubbles.size -> while (bubbles.size > desired) bubbles.removeAt(bubbles.size - 1)
        }
    }

    private fun drawTurtles(deepColor: FloatArray) {
        if (turtleProgram == 0) return
        synchronized(turtles) {
            if (turtles.isEmpty()) return
            GLES30.glUseProgram(turtleProgram)
            GLES30.glUniform1f(turtleDayNightHandle, dayNight)

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

            for (i in turtles.indices) {
                val t = turtles[i]
                // Depth illusion: shrink and tint toward the water's deep color as the turtle
                // recedes (Turtle.depth -> 1), so it reads as farther away/underwater-hazier
                // instead of just smaller.
                val depthScale = kTurtleScale * (1f - (1f - kMinScaleAtDepth) * t.depth)
                val tintAmount = t.depth * kMaxDepthTint

                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, t.x + getParallaxX(t.depth), t.y + getParallaxY(t.depth), 0f)
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
                GLES30.glUniform1f(turtleRetractionHandle, t.retraction)

                val ambientFactor = 0.35f + 0.65f * dayNight
                val palette = t.palette
                mixColorInto(scratchShellColor, palette.shellColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchHeadColor, palette.headColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchFlipperColor, palette.flipperColor, deepColor, tintAmount, ambientFactor)
                mixColorInto(scratchSpotColor, palette.spotColor, deepColor, tintAmount, ambientFactor)
                GLES30.glUniform3fv(turtleShellColorHandle, 1, scratchShellColor, 0)
                GLES30.glUniform3fv(turtleHeadColorHandle, 1, scratchHeadColor, 0)
                GLES30.glUniform3fv(turtleFlipperColorHandle, 1, scratchFlipperColor, 0)
                GLES30.glUniform3fv(turtleSpotColorHandle, 1, scratchSpotColor, 0)
                // Not depth-tinted like the colors above - see the identical comment in the fish
                // draw loop.
                val turtleGlow = if (configProvider.getBioluminescenceEnabled()) palette.glowColor else kZeroGlow
                GLES30.glUniform3fv(turtleGlowColorHandle, 1, turtleGlow, 0)
                GLES30.glUniform1f(turtleShinyTypeHandle, t.shinyType.toFloat())

                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }

    /** Writes `mix(from * multiplier, toward, amount)` component-wise into [out] (avoids a per-call allocation). */
    private fun mixColorInto(
        out: FloatArray,
        from: FloatArray,
        toward: FloatArray,
        amount: Float,
        multiplier: Float = 1.0f
    ) {
        for (i in 0..2) {
            val baseVal = from[i] * multiplier
            out[i] = baseVal + (toward[i] - baseVal) * amount
        }
    }

    private fun drawBackground(theme: Int) {
        if (backgroundProgram == 0) return
        GLES30.glUseProgram(backgroundProgram)
        GLES30.glUniform1f(bgTimeHandle, time)
        GLES30.glUniform1i(bgThemeHandle, theme)
        GLES30.glUniform1f(bgAspectHandle, aspectRatio)
        GLES30.glUniform1f(bgParallaxHandle, parallaxRaw)

        GLES30.glUniform1f(bgDayNightHandle, dayNight)
        GLES30.glUniform1f(bgStormIntensityHandle, stormVisualIntensity)

        if (theme == 5) {
            val shallow = configProvider.getCustomShallowColor()
            val deep = configProvider.getCustomDeepColor()

            val shallowR = ((shallow shr 16) and 0xFF) / 255f
            val shallowG = ((shallow shr 8) and 0xFF) / 255f
            val shallowB = (shallow and 0xFF) / 255f

            val deepR = ((deep shr 16) and 0xFF) / 255f
            val deepG = ((deep shr 8) and 0xFF) / 255f
            val deepB = (deep and 0xFF) / 255f

            GLES30.glUniform3f(bgCustomShallowHandle, shallowR, shallowG, shallowB)
            GLES30.glUniform3f(bgCustomDeepHandle, deepR, deepG, deepB)
        }

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
            val bubbleDepth = (1.0f - (bubble.size - 0.02f) / 0.045f).coerceIn(0f, 1f)
            bubbleInstanceBuffer.put(bubble.x + getParallaxX(bubbleDepth))
            bubbleInstanceBuffer.put(bubble.y + getParallaxY(bubbleDepth))
            bubbleInstanceBuffer.put(bubble.size)
            bubbleInstanceBuffer.put(bubble.r)
            bubbleInstanceBuffer.put(bubble.g)
            bubbleInstanceBuffer.put(bubble.b)
            bubbleInstanceBuffer.put(bubble.alpha)
        }
        for (i in 0 until activeBurstCount) {
            val bubble = burstBubbles[i]
            val bubbleDepth = (1.0f - (bubble.size - 0.02f) / 0.045f).coerceIn(0f, 1f)
            bubbleInstanceBuffer.put(bubble.x + getParallaxX(bubbleDepth))
            bubbleInstanceBuffer.put(bubble.y + getParallaxY(bubbleDepth))
            bubbleInstanceBuffer.put(bubble.size)
            bubbleInstanceBuffer.put(bubble.r)
            bubbleInstanceBuffer.put(bubble.g)
            bubbleInstanceBuffer.put(bubble.b)
            bubbleInstanceBuffer.put(bubble.alpha)
        }
        bubbleInstanceBuffer.position(0)

        // Shared quad geometry (locations 0/1, divisor 0 -> same 4 vertices for every instance)
        // is already bound for the whole frame by onDrawFrame() - no need to re-set it here.
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
