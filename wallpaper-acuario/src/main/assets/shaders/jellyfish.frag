#version 300 es
precision mediump float;

in vec2 vUV;

// Drives both the bell's rhythmic contraction/expansion and the trailing tentacle ripple - see
// Jellyfish.kt's pulsePhase, which also uses it to fire the matching upward thrust.
uniform float uPulsePhase;
uniform vec3 uBellColor;
uniform vec3 uMarginColor;
uniform vec3 uTentacleColor;
// Radial canal/rosette marking on the bell - see the pattern block in main() and
// JellyfishPalette.patternColor.
uniform vec3 uPatternColor;
uniform float uDayNight;
// Per-palette bioluminescent tint (see JellyfishPalette.glowColor) - see fish.frag's identical
// comment.
uniform vec3 uGlowColor;
// 0.0 = normal, 1.0 = "shiny gold", 2.0 = "shiny diamond" (see Jellyfish.kt's shinyType) - drives
// the premium halo/sheen finish below.
uniform float uShinyType;
uniform float uTime;
uniform float uElectricIntensity;

out vec4 fragColor;

// Soft "inside an ellipse" mask - see fish.frag for the identical helper.
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

void main() {
    // Center UV to [-1, 1] space, +y up (flipped from the quad's screen-space V, same convention
    // as every other creature shader). A jellyfish has no real "facing" direction - the bell is
    // radially symmetric enough that Jellyfish.kt skips heading/mirror entirely - so unlike
    // fish/manta/seahorse there's no left/right asymmetry to preserve here.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Bell pulse: contracts taller/narrower on the power stroke, then relaxes back to a wider,
    // flatter dome - the same rhythm that drives the upward thrust in Jellyfish.kt. Squash/
    // stretch is inverse on the two axes so the bell reads as pushing water down rather than
    // just uniformly scaling.
    float pulse = sin(uPulsePhase);
    float bellScaleX = 1.0 - pulse * 0.10;
    float bellScaleY = 1.0 + pulse * 0.14;

    // Breathing size envelope, layered on top of the squash/stretch above: the whole bell also
    // swells a little right as a pulse is about to fire, snaps into a slightly smaller contracted
    // size during the power stroke itself, then relaxes back to its normal size by the time the
    // stroke ends and holds there through the rest of the recovery half - until it starts
    // swelling again as the next pulse approaches. cyclePos is a 0..1 sawtooth over one full
    // pulse period, shifted so cyclePos == 0 lines up with the start of the power stroke (the
    // same instant Jellyfish.kt's "contracting" flag flips true, i.e. where sin(uPulsePhase)
    // bottoms out - see its comment).
    const float kTwoPi = 6.28318530718;
    const float kHalfPi = 1.5707963;
    float cyclePos = fract((uPulsePhase + kHalfPi) / kTwoPi);
    const float kExpandAmount = 0.10;
    const float kContractAmount = 0.08;
    // k1 carries the envelope from +expand (cyclePos 0) down to -contract (cyclePos 0.22, roughly
    // the middle of the power stroke); k2 carries it back up from -contract to normal (0) by
    // cyclePos 0.5 (the end of the stroke); k3 ramps it from normal back up to +expand across the
    // last sliver of the cycle (0.85-1.0), in anticipation of the next stroke. Continuous and
    // branchless by construction: at cyclePos == 1.0 this evaluates to the same +kExpandAmount as
    // cyclePos == 0.0, so it wraps with no discontinuity.
    float k1 = smoothstep(0.0, 0.22, cyclePos);
    float k2 = smoothstep(0.22, 0.5, cyclePos);
    float k3 = smoothstep(0.85, 1.0, cyclePos);
    float sizeEnvelope = kExpandAmount - (kExpandAmount + kContractAmount) * k1 + kContractAmount * k2 + kExpandAmount * k3;
    float sizeScale = 1.0 + sizeEnvelope;

    // NOTE: sizeScale (like bellScaleX/Y above) only reshapes pBell, which feeds aBell/aMargin/
    // pattern below - the tentacle loop further down still attaches at a fixed y (bellBottomY,
    // built from the raw kBellCenter/kBellRadii constants) that doesn't track either effect. This
    // currently stays visually attached to the rim because sizeEnvelope == 0 exactly at
    // cyclePos == 0.5, which is also bellScaleY's own peak (both derive from uPulsePhase via the
    // same kHalfPi shift) - so the two effects don't compound at bellScaleY's extreme. Retuning
    // kExpandAmount/kContractAmount/the 0.22 breakpoint (or bellScaleX/Y's own 0.10/0.14) should
    // re-check that this still holds, or thread sizeScale/bellScaleY into bellBottomY/tipY below
    // so the two can't drift apart structurally.
    vec2 pBell = vec2(p.x / (bellScaleX * sizeScale), (p.y - 0.15) / (bellScaleY * sizeScale) + 0.15);

    const vec2 kBellCenter = vec2(0.0, 0.30);
    const vec2 kBellRadii = vec2(0.48, 0.38);
    float aBell = ellipseAlpha(pBell, kBellCenter, kBellRadii, 0.03);
    // Cut the flat underside of the dome away (a real bell's rim, not a full ellipse) by fading
    // out anything below the bell's own center.
    float domeMask = smoothstep(kBellCenter.y - 0.30, kBellCenter.y + 0.05, pBell.y);
    aBell *= domeMask;

    // Margin: a thin fringe tracing the bell's lower rim, where the bioluminescent glow
    // concentrates on a real jelly.
    float aMargin = ellipseAlpha(pBell, kBellCenter, kBellRadii, 0.03)
        * (1.0 - ellipseAlpha(pBell, kBellCenter, kBellRadii * 0.90, 0.03))
        * domeMask;

    // Radial canal spokes + a central four-lobed rosette (the classic moon-jelly "horseshoe"
    // gonad marking) - both read in pBell space so the pattern pulses with the bell's own
    // squash/stretch instead of floating independently on top of it.
    const float kNumSpokes = 10.0;
    vec2 pRosette = pBell - kBellCenter;
    float bellRadiusNorm = length(pRosette / kBellRadii);
    float angle01 = atan(pRosette.y, pRosette.x) / kTwoPi + 0.5;
    float spokeDist = abs(fract(angle01 * kNumSpokes + 0.5) - 0.5);
    float spokeLine = smoothstep(0.10, 0.0, spokeDist);
    // Fades in just past the center (so it doesn't clash with the rosette) and mostly fades back
    // out before the margin - the two bands do soften into each other a little just inside the
    // rim, but aMargin is mixed in after the pattern below, so the crisp margin ring always wins
    // there regardless.
    float radialFade = smoothstep(0.18, 0.38, bellRadiusNorm) * smoothstep(1.05, 0.78, bellRadiusNorm);
    float spokesAlpha = spokeLine * radialFade * aBell;

    float lobeN = ellipseAlpha(pBell, kBellCenter + vec2(0.0, 0.10), vec2(0.06, 0.10), 0.02);
    float lobeS = ellipseAlpha(pBell, kBellCenter + vec2(0.0, -0.10), vec2(0.06, 0.10), 0.02);
    float lobeE = ellipseAlpha(pBell, kBellCenter + vec2(0.10, 0.0), vec2(0.10, 0.06), 0.02);
    float lobeW = ellipseAlpha(pBell, kBellCenter + vec2(-0.10, 0.0), vec2(0.10, 0.06), 0.02);
    float rosetteAlpha = max(max(lobeN, lobeS), max(lobeE, lobeW)) * aBell;

    float patternAlpha = max(spokesAlpha, rosetteAlpha);

    // Trailing tentacles: a handful of thin ribbons hanging below the bell. Each one bends along
    // its own length rather than swinging as a single rigid pendulum: the horizontal offset is a
    // traveling wave in p.y (the same coordinate-warp trick fish.frag/manta.frag use for their
    // tail/wing undulation), so the bend visibly ripples from base to tip instead of the whole
    // strand just tilting. Two sine terms at different frequency/speed - a slow one tied to the
    // bell's own pulse rhythm plus a faster secondary ripple - layer into a less metronomic,
    // more organic sway. The sway amplitude still ramps from ~0 at the bell (tReach == 0) to full
    // strength at the tip (tReach == 1), like Manta's tail-sway pendulum.
    float aTentacles = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        // Spaced a bit wider than the old rigid-pendulum version (was 0.16 apart) so the larger
        // sway amplitude below has room to move without adjacent tentacles crossing too far past
        // each other's resting position.
        float baseX = -0.36 + fi * 0.18;
        float tipY = -0.85 - 0.06 * sin(fi * 1.7);
        float bellBottomY = kBellCenter.y - kBellRadii.y * 0.55;
        float tReach = clamp((bellBottomY - p.y) / (bellBottomY - tipY), 0.0, 1.0);
        float travel = sin(uPulsePhase * 0.8 - fi * 1.1 - p.y * 2.6) * 0.13
            + sin(uPulsePhase * 1.9 - fi * 0.6 - p.y * 5.2 + 1.7) * 0.045;
        float sway = travel * tReach;
        vec2 pTentacle = vec2(p.x - baseX - sway, p.y);
        float widthTaper = mix(0.028, 0.010, tReach);
        float aOne = ellipseAlpha(pTentacle, vec2(0.0, mix(bellBottomY, tipY, 0.5)), vec2(widthTaper, (bellBottomY - tipY) * 0.5), 0.012);
        aTentacles = max(aTentacles, aOne);
    }

    float jellyAlpha = max(max(aBell, aMargin), aTentacles);

    // Shiny halo - see fish.frag's identical block for the full explanation. Anchored to the bell
    // itself via the already-named kBellCenter/kBellRadii consts (no literal copy needed here).
    float haloAlpha = 0.0;
    if (uShinyType > 0.5) {
        vec2 qHalo = (pBell - kBellCenter) / kBellRadii;
        float dEdge = length(qHalo) - 1.0;

        float isDiamond = step(1.5, uShinyType);
        float haloSigma = mix(0.24, 0.15, isDiamond);

        float breatheGold = 0.80 + 0.20 * sin(uPulsePhase);
        float breatheDiamond = pow(0.5 + 0.5 * sin(uPulsePhase * 3.0), 2.0);
        float breathe = mix(breatheGold, breatheDiamond, isDiamond);

        float halo = exp(-(dEdge * dEdge) / (haloSigma * haloSigma));
        halo *= mix(0.12, 1.0, smoothstep(-0.06, 0.02, dEdge));
        haloAlpha = halo * breathe;
    }

    if (jellyAlpha <= 0.0 && haloAlpha <= 0.004) {
        discard;
    }

    // Color mixing pipeline
    vec3 color = uBellColor;
    color = mix(color, uPatternColor, patternAlpha);
    color = mix(color, uMarginColor, aMargin);
    color = mix(color, uTentacleColor, aTentacles);

    // Electrification effect when touched:
    float electricGlow = 0.0;
    if (uElectricIntensity > 0.0) {
        // High frequency flicker (crackling look)
        float flicker = 0.6 + 0.4 * sin(uTime * 80.0);
        
        // Wavy vertical ray 1 down the middle
        float w1 = sin(p.y * 25.0 + uTime * 35.0) * 0.15 * cos(p.y * 12.0 - uTime * 20.0);
        float d1 = abs(p.x - w1);
        float s1 = smoothstep(0.02, 0.0, d1);
        
        // Ray 2 (warping on the right side)
        float w2 = sin(p.y * 40.0 - uTime * 45.0) * 0.10 * sin(p.y * 15.0 + uTime * 25.0) + 0.25 * sin(uTime * 5.0);
        float d2 = abs(p.x - w2);
        float s2 = smoothstep(0.015, 0.0, d2);

        // Ray 3 (warping on the left side)
        float w3 = cos(p.y * 35.0 + uTime * 50.0) * 0.10 * cos(p.y * 18.0 - uTime * 30.0) - 0.25 * sin(uTime * 6.0);
        float d3 = abs(p.x - w3);
        float s3 = smoothstep(0.015, 0.0, d3);

        // Combine all crackling rays/sparks
        float sparks = max(max(s1, s2), s3);
        // Only render sparks on the jellyfish body & tentacles
        sparks *= jellyAlpha;

        // Bright electric cyan/white spark color
        vec3 electricColor = vec3(0.4, 0.8, 1.0) * sparks * 2.5 * flicker * uElectricIntensity;
        color += electricColor;

        // Electric blue background body glow
        electricGlow = jellyAlpha * 0.6 * flicker * uElectricIntensity;
        color += vec3(0.0, 0.55, 1.0) * electricGlow;
    }

    // Bioluminescent glow along the margin, radial pattern and tentacles at night - see
    // fish.frag's identical fix for why this is ramped through
    // smoothstep(0.0, kNightGlowEdge, uDayNight) rather than a plain (1.0 - uDayNight).
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowAlpha = max(max(aMargin, aTentacles * 0.7), patternAlpha * 0.6);
    color += uGlowColor * glowAlpha * nightGlow * 1.3;

    // Shiny sheen - see fish.frag's identical block for the full explanation. Masked by aBell
    // (not jellyAlpha) so the sweep/glints stay on the bell's own gelatinous surface rather than
    // painting a flat diagonal band across the thin trailing tentacles, which wouldn't read well.
    if (uShinyType > 0.5) {
        float isDiamond = step(1.5, uShinyType);

        // Uses pBell (not the raw p) so the sweep bends/breathes with the bell's own pulse
        // squash-stretch instead of reading as a flat overlay independent of its animation.
        const vec2 kSweepDir = vec2(0.8, 0.6);
        float sweepAxis = dot(pBell, kSweepDir);
        const float kTwoPiSheen = 6.28318530718;
        float sweepSpeedMul = mix(1.0, 2.0, isDiamond);
        float sweepT = fract(uPulsePhase * sweepSpeedMul / kTwoPiSheen);
        float sweepCenter = mix(-1.6, 1.6, sweepT);
        float sweepDist = sweepAxis - sweepCenter;
        float sweepWidth = mix(0.55, 0.22, isDiamond);
        float sweepCore = exp(-(sweepDist * sweepDist) / (sweepWidth * sweepWidth));
        float sweepBand = pow(sweepCore, mix(1.0, 2.2, isDiamond));

        const vec3 kGoldSheen = vec3(1.00, 0.88, 0.58);
        const vec3 kDiamondSheen = vec3(0.55, 0.85, 1.00);
        vec3 sweepColor = mix(kGoldSheen, kDiamondSheen, isDiamond);
        float sweepIntensity = mix(0.55, 0.40, isDiamond);

        color = mix(color, sweepColor, sweepBand * sweepIntensity * aBell);

        // Facet-glint hash deliberately stays on the raw, un-warped `p` (not pBell) so the glint
        // positions stay fixed on the bell's surface instead of swimming with the pulse warp -
        // only their twinkle timing should animate, not their layout.
        highp vec2 cell = floor(p * 9.0);
        highp vec2 cellFrac = fract(p * 9.0);
        highp vec3 hash3 = fract(sin(vec3(
            cell.x * 127.1 + cell.y * 311.7,
            cell.x * 269.5 + cell.y * 183.3,
            cell.x * 419.2 + cell.y * 371.9)) * 43758.5453);
        vec2 glintPos = hash3.xy;
        float glintDist = length(cellFrac - glintPos) * 3.0;
        float glintMask = smoothstep(1.0, 0.0, glintDist);
        float freqInt = floor(hash3.z * 8.0) + 5.0;
        float twinkle = pow(max(0.0, sin(uPulsePhase * freqInt + hash3.z * 6.283)), 10.0);
        float facetHue = fract(hash3.x * 7.13 + hash3.y * 13.71 + hash3.z * 3.29);
        vec3 facetColor = 0.5 + 0.5 * cos(6.28318 * (facetHue + vec3(0.0, 0.33, 0.67)));
        float facetStrength = glintMask * twinkle * isDiamond * aBell;

        color = mix(color, facetColor, facetStrength);
    }

    // Translucency: a jellyfish's bell and tentacles are gelatinous and semi-transparent, unlike
    // every other creature's opaque silhouette - the background shows faintly through, more so
    // toward the tentacle tips than the denser bell.
    float bellOpacity = 0.72;
    float tentacleOpacity = mix(0.55, 0.30, clamp((p.y - (-0.85)) / 1.0, 0.0, 1.0));
    float finalAlpha = max(max(aBell, aMargin) * bellOpacity, aTentacles * tentacleOpacity);
    finalAlpha = max(finalAlpha, electricGlow * 0.8);

    // Shiny halo compositing - see fish.frag's identical block for the full explanation.
    if (uShinyType > 0.5) {
        const vec3 kGoldHalo = vec3(1.00, 0.80, 0.35);
        const vec3 kDiamondHalo = vec3(0.45, 0.80, 1.00);
        vec3 haloColor = mix(kGoldHalo, kDiamondHalo, step(1.5, uShinyType));

        float outside = 1.0 - jellyAlpha;
        color = mix(color, haloColor, haloAlpha * outside);
        color += haloColor * haloAlpha * 0.65;
        finalAlpha = max(finalAlpha, haloAlpha * outside * 0.65);
    }

    fragColor = vec4(color, finalAlpha);
}
