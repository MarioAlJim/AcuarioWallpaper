#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uBodyColor;
uniform vec3 uFinColor;
uniform vec3 uTailColor;
uniform vec3 uStripeColor;
uniform float uDayNight;
// Per-palette bioluminescent tint (see FishPalette.glowColor) - each species glows its own
// color instead of every fish sharing one fixed neon hue.
uniform vec3 uGlowColor;
// 0.0 = normal, 1.0 = "shiny gold", 2.0 = "shiny diamond" (see Fish.kt's shinyType) - drives the
// premium halo/sheen finish below. Independent of uBodyColor/uGlowColor (which already carry the
// gold/diamond palette's own recolor) - this adds a finish no palette recolor alone can produce.
uniform float uShinyType;

out vec4 fragColor;

// Soft "inside an ellipse" mask
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Rim-light contribution Confined to the upper arc
float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
}

void main() {
    // Center UV to [-1, 1] space. Fish faces right.
    // The shared quad's V axis runs top(0)->bottom(1) on screen, opposite of this
    // shader's local +y-is-up convention (dorsal fin at +y, ventral at -y), so the
    // vertical component is flipped here to keep the fish right-side up.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Organic swimming tail-wag: Warp coordinate y-axis based on x-axis and swim phase.
    // Bending is 0 at the head (p.x > 0.2) and reaches maximum at the tail (p.x < -0.6).
    float wave = sin(uSwimPhase - p.x * 3.5) * 0.16 * smoothstep(0.2, -0.6, p.x);
    vec2 pWarped = vec2(p.x, p.y + wave);

    // Alpha masks for different anatomical parts (drawn in warped space for unified wave motion)
    float aBody = ellipseAlpha(pWarped, vec2(0.05, 0.0), vec2(0.45, 0.22), 0.025);

    // Triangular / crescent half-moon tail fin (attachment at x = -0.32, flares to tips extending to x = -0.47)
    float tailLen = 0.15;
    float tX = (pWarped.x - (-0.32)) / (-tailLen);
    float yNorm = pWarped.y / (0.04 + 0.16 * clamp(tX, 0.0, 1.0));
    // tXLimit creates the concave half-moon cutout (from 0.7 at center to 1.0 at tips)
    float tXLimit = 0.7 + 0.3 * (yNorm * yNorm);
    float xBounds = smoothstep(0.0, 0.05, tX) * smoothstep(tXLimit, tXLimit - 0.05, tX);
    float halfHeight = 0.04 + 0.16 * clamp(tX, 0.0, 1.0);
    float yBounds = smoothstep(halfHeight, halfHeight - 0.025, abs(pWarped.y));
    float aTailFin = xBounds * yBounds;
    float aDorsalFin = ellipseAlpha(pWarped, vec2(-0.05, 0.22), vec2(0.24, 0.10), 0.025);
    float aVentralFin = ellipseAlpha(pWarped, vec2(-0.10, -0.22), vec2(0.18, 0.08), 0.025);
    float aEye = ellipseAlpha(pWarped, vec2(0.32, 0.06), vec2(0.038, 0.038), 0.015);
    float aPupil = ellipseAlpha(pWarped, vec2(0.33, 0.06), vec2(0.020, 0.020), 0.01);

    // Check if fragment is within any part of the fish body/fins
    float finsAlpha = max(max(aTailFin, aDorsalFin), aVentralFin);
    float fishAlpha = max(max(aBody, finsAlpha), aEye);

    // Shiny halo: a soft aura glowing OUTSIDE the body's own silhouette (dEdge > 0), gated behind
    // uShinyType so it costs nothing and changes nothing for a normal fish - see the discard guard
    // just below for why. Distinct from the night-only bioluminescence above: this is always
    // visible, day or night, and reads as a rarity aura rather than reflected/emitted light.
    float haloAlpha = 0.0;
    if (uShinyType > 0.5) {
        vec2 qHalo = (pWarped - vec2(0.05, 0.0)) / vec2(0.45, 0.22);
        float dEdge = length(qHalo) - 1.0;

        float isDiamond = step(1.5, uShinyType);
        float haloSigma = mix(0.24, 0.15, isDiamond);

        // Breathing envelope - integer multiples of uSwimPhase (already wrapped to 2*PI before
        // upload), so this stays exactly continuous across every wrap with no new phase state.
        float breatheGold = 0.80 + 0.20 * sin(uSwimPhase);
        float breatheDiamond = pow(0.5 + 0.5 * sin(uSwimPhase * 3.0), 2.0);
        float breathe = mix(breatheGold, breatheDiamond, isDiamond);

        float halo = exp(-(dEdge * dEdge) / (haloSigma * haloSigma));
        halo *= mix(0.12, 1.0, smoothstep(-0.06, 0.02, dEdge));
        haloAlpha = halo * breathe;
    }

    // Provably identical to the original "if (fishAlpha <= 0.0) discard;" when uShinyType == 0.0:
    // haloAlpha is declared 0.0 and only ever written inside the uShinyType > 0.5 branch above, so
    // for a normal fish haloAlpha stays 0.0 <= 0.004 and this reduces to exactly the original test.
    if (fishAlpha <= 0.0 && haloAlpha <= 0.004) {
        discard;
    }

    // Color mixing pipeline
    vec3 color = uFinColor;
    
    // Dorsal/ventral fins get fin color, tail fin gets tail color
    color = mix(color, uTailColor, aTailFin);
    
    // Body coloring
    color = mix(color, uBodyColor, aBody);

    // Vertical tropical stripes on the body
    float stripe1 = smoothstep(0.08, 0.0, abs(pWarped.x - 0.16));
    float stripe2 = smoothstep(0.08, 0.0, abs(pWarped.x + 0.05));
    float stripe3 = smoothstep(0.06, 0.0, abs(pWarped.x + 0.24));
    float stripesAlpha = max(max(stripe1, stripe2), stripe3) * aBody;
    color = mix(color, uStripeColor, stripesAlpha);
    
    // Bioluminescent glow on stripes at night. Ramped through smoothstep(0.0, kNightGlowEdge,
    // uDayNight) rather than a plain (1.0 - uDayNight) - the raw linear version made the glow
    // already partway visible as soon as the sun started dipping, well before the background
    // (acuario_background.frag, which itself darkens roughly linearly in uDayNight) actually
    // looked dark. Clamping the ramp to uDayNight's bottom slice instead keeps the glow at 0
    // through day/dusk/dawn and only fades it in once it's genuinely dark, in sync with the
    // background - see the same fix in manta.frag/turtle.frag.
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowStrength = nightGlow * 1.2;
    color += uGlowColor * stripesAlpha * glowStrength;

    // Eye colors
    vec3 eyeRingColor = vec3(0.95, 0.95, 0.95);
    vec3 pupilColor = vec3(0.05, 0.05, 0.05);
    color = mix(color, eyeRingColor, aEye);
    color = mix(color, pupilColor, aPupil);

    // Shiny sheen: a moving glossy highlight sweeping across the body, plus (diamond only) a
    // field of tiny asynchronously-twinkling facet glints - the "premium finish" a flat palette
    // recolor alone can't produce. Gated behind uShinyType, same as the halo above. Blended via
    // mix() rather than additive color +=, so it approaches its own highlight color but can never
    // clip past it - important on the already-bright SHINY_DIAMOND palette, where an additive
    // white-ish highlight would just saturate to a flat white wash. Also masked by (1.0 - aEye)
    // so the sweep/glints don't periodically wash over the eye right when they're brightest.
    if (uShinyType > 0.5) {
        float isDiamond = step(1.5, uShinyType);
        float eyeMask = 1.0 - aEye;

        // Layer 1: one continuous diagonal gloss sweep, present for both gold and diamond -
        // reads as a curved metallic/gem surface catching a traveling light source. Uses pWarped
        // (not the raw p) so it bends with the fish's own tail-wag undulation.
        const vec2 kSweepDir = vec2(0.8, 0.6);
        float sweepAxis = dot(pWarped, kSweepDir);
        const float kTwoPiSheen = 6.28318530718;
        float sweepSpeedMul = mix(1.0, 2.0, isDiamond);
        float sweepT = fract(uSwimPhase * sweepSpeedMul / kTwoPiSheen);
        float sweepCenter = mix(-1.6, 1.6, sweepT);
        float sweepDist = sweepAxis - sweepCenter;
        float sweepWidth = mix(0.55, 0.22, isDiamond);
        float sweepCore = exp(-(sweepDist * sweepDist) / (sweepWidth * sweepWidth));
        float sweepBand = pow(sweepCore, mix(1.0, 2.2, isDiamond));

        const vec3 kGoldSheen = vec3(1.00, 0.88, 0.58);
        const vec3 kDiamondSheen = vec3(0.55, 0.85, 1.00);
        vec3 sweepColor = mix(kGoldSheen, kDiamondSheen, isDiamond);
        float sweepIntensity = mix(0.55, 0.40, isDiamond);

        color = mix(color, sweepColor, sweepBand * sweepIntensity * fishAlpha * eyeMask);

        // Layer 2 (diamond only): a static lattice of pinpoint glints from a single-cell hash of
        // `p` - O(1) per fragment, no loop. Each flashes its own fully-saturated spectral hue
        // (like light splitting through a cut gem's facets - real "diamond fire") rather than
        // plain white, so it reads as a clear colored flash instead of invisibly blending into
        // the SHINY_DIAMOND palette's own already near-white body. Twinkles at its own integer
        // multiple of uSwimPhase (chosen per-cell at runtime via floor(), still an exact integer,
        // so still exactly continuous across uSwimPhase's own wrap) so the field twinkles
        // asynchronously.
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
        float twinkle = pow(max(0.0, sin(uSwimPhase * freqInt + hash3.z * 6.283)), 10.0);
        float facetHue = fract(hash3.x * 7.13 + hash3.y * 13.71 + hash3.z * 3.29);
        vec3 facetColor = 0.5 + 0.5 * cos(6.28318 * (facetHue + vec3(0.0, 0.33, 0.67)));
        float facetStrength = glintMask * twinkle * isDiamond * fishAlpha * eyeMask;

        color = mix(color, facetColor, facetStrength);
    }

    // Soft rim lighting along upper edges for separating from the background
    float bodyRim = rimLight(pWarped, vec2(0.05, 0.0), vec2(0.45, 0.22), aBody);
    float dorsalRim = rimLight(pWarped, vec2(-0.05, 0.22), vec2(0.24, 0.10), aDorsalFin);
    float tailRim = rimLight(pWarped, vec2(-0.40, 0.0), vec2(0.12, 0.20), aTailFin);
    float rimAmount = max(max(bodyRim, dorsalRim), tailRim) * (1.0 - aEye);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.90);
    const float kRimIntensity = 0.50;
    color += kRimColor * rimAmount * kRimIntensity;

    float finalAlpha = max(fishAlpha, aPupil);

    // Shiny halo compositing: outside the real body the aura's own hue takes over (graded by
    // haloAlpha, never a hard replace), inside it only adds a gentle glow so the body's own
    // shading/stripes/rim-light are never washed out; extends translucently past the true
    // silhouette edge into finalAlpha instead of being clipped by the opaque body alpha.
    if (uShinyType > 0.5) {
        const vec3 kGoldHalo = vec3(1.00, 0.80, 0.35);
        const vec3 kDiamondHalo = vec3(0.45, 0.80, 1.00);
        vec3 haloColor = mix(kGoldHalo, kDiamondHalo, step(1.5, uShinyType));

        float outside = 1.0 - fishAlpha;
        color = mix(color, haloColor, haloAlpha * outside);
        color += haloColor * haloAlpha * 0.65;
        finalAlpha = max(finalAlpha, haloAlpha * outside * 0.65);
    }

    fragColor = vec4(color, finalAlpha);
}
