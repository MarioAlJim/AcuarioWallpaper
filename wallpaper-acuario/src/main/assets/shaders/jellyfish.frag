#version 300 es
precision mediump float;

in vec2 vUV;

// Drives both the bell's rhythmic contraction/expansion and the trailing tentacle ripple - see
// Jellyfish.kt's pulsePhase, which also uses it to fire the matching upward thrust.
uniform float uPulsePhase;
uniform vec3 uBellColor;
uniform vec3 uMarginColor;
uniform vec3 uTentacleColor;
uniform float uDayNight;
// Per-palette bioluminescent tint (see JellyfishPalette.glowColor) - see fish.frag's identical
// comment.
uniform vec3 uGlowColor;

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
    vec2 pBell = vec2(p.x / bellScaleX, (p.y - 0.15) / bellScaleY + 0.15);

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

    // Trailing tentacles: a handful of thin ribbons hanging below the bell, each swaying on its
    // own phase offset with the sway amplitude ramping from ~0 at the bell (tReach == 0) to full
    // strength at the tip (tReach == 1), like Manta's tail-sway pendulum.
    float aTentacles = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float baseX = -0.32 + fi * 0.16;
        float tipY = -0.85 - 0.06 * sin(fi * 1.7);
        float bellBottomY = kBellCenter.y - kBellRadii.y * 0.55;
        float tReach = clamp((bellBottomY - p.y) / (bellBottomY - tipY), 0.0, 1.0);
        float sway = sin(uPulsePhase * 0.8 - fi * 1.1) * 0.10 * tReach;
        vec2 pTentacle = vec2(p.x - baseX - sway, p.y);
        float widthTaper = mix(0.028, 0.010, tReach);
        float aOne = ellipseAlpha(pTentacle, vec2(0.0, mix(bellBottomY, tipY, 0.5)), vec2(widthTaper, (bellBottomY - tipY) * 0.5), 0.012);
        aTentacles = max(aTentacles, aOne);
    }

    float jellyAlpha = max(max(aBell, aMargin), aTentacles);
    if (jellyAlpha <= 0.0) {
        discard;
    }

    // Color mixing pipeline
    vec3 color = uBellColor;
    color = mix(color, uMarginColor, aMargin);
    color = mix(color, uTentacleColor, aTentacles);

    // Bioluminescent glow along the margin and tentacles at night - see fish.frag's identical
    // fix for why this is ramped through smoothstep(0.0, kNightGlowEdge, uDayNight) rather than a
    // plain (1.0 - uDayNight).
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowAlpha = max(aMargin, aTentacles * 0.7);
    color += uGlowColor * glowAlpha * nightGlow * 1.3;

    // Translucency: a jellyfish's bell and tentacles are gelatinous and semi-transparent, unlike
    // every other creature's opaque silhouette - the background shows faintly through, more so
    // toward the tentacle tips than the denser bell.
    float bellOpacity = 0.72;
    float tentacleOpacity = mix(0.55, 0.30, clamp((p.y - (-0.85)) / 1.0, 0.0, 1.0));
    float finalAlpha = max(max(aBell, aMargin) * bellOpacity, aTentacles * tentacleOpacity);

    fragColor = vec4(color, finalAlpha);
}
