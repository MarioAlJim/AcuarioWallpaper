#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uBodyColor;
uniform vec3 uWingColor;
uniform vec3 uTailColor;
uniform vec3 uMarkingColor;
uniform float uDayNight;
// Per-palette bioluminescent tint (see MantaPalette.glowColor) - each species glows its own
// color instead of every ray sharing one fixed neon hue.
uniform vec3 uGlowColor;
// 0.0 = normal, 1.0 = "shiny gold", 2.0 = "shiny diamond" (see Manta.kt's shinyType) - drives the
// premium halo/sheen finish below.
uniform float uShinyType;

out vec4 fragColor;

// Soft "inside an ellipse" mask - see fish.frag/turtle.frag for the identical helper.
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Rim-light contribution confined to the upper arc - see fish.frag/turtle.frag.
float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
}

void main() {
    // Center UV to [-1, 1] space. Manta faces right (nose toward +x, tail whip toward -x), same
    // convention as fish/turtle. The shared quad's V axis runs top(0)->bottom(1) on screen,
    // opposite of this shader's local +y-is-up convention, so the vertical component is flipped
    // here - see fish.frag's identical comment.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Wing undulation: rather than a fish's sharp tail wag, a manta ripples its whole wing
    // front-to-back while gliding. flapWave's phase depends on p.x so the ripple visibly travels
    // from nose to tail, and multiplying by p.y (not a fixed amplitude) means the displacement
    // grows with distance from the spine - the spine barely moves while the wingtips sweep
    // through the widest excursion, same as a real ray's undulating pectoral fin.
    //
    // kWingFlapAmplitude and the wings' own y-radius below are chosen together: this warp
    // rescales p.y by a factor of (1 +/- kWingFlapAmplitude), so at full spread (factor
    // 1 - kWingFlapAmplitude) the wingtip's *true* silhouette edge would sit at
    // wingRadiusY / (1 - kWingFlapAmplitude) in unwarped space. If that exceeds 1.0 - the quad's
    // own hard edge - the tip gets sliced off flat by the geometry boundary instead of tapering
    // naturally, which is exactly what a too-large radius/amplitude combination looked like
    // before this comment (wings visibly clipped by a flat top/bottom edge mid-flap). Keeping
    // wingRadiusY / (1 - kWingFlapAmplitude) comfortably under 1.0 leaves room for the whole
    // flap cycle to render without ever touching the quad's edge.
    const float kWingFlapAmplitude = 0.16;
    const float kWingRadiusY = 0.80; // 0.80 / (1 - 0.16) = 0.952 - safely inside the quad's [-1, 1]
    float flapWave = sin(uSwimPhase - p.x * 1.8);
    vec2 pWarped = vec2(p.x, p.y + flapWave * p.y * kWingFlapAmplitude);

    // Body: a wide diamond-ish wingspan (much wider in y than long in x) plus a small pointed
    // nose bump at the front. Approximated with ellipses, same trick as the rest of the cast.
    float aWings = ellipseAlpha(pWarped, vec2(0.02, 0.0), vec2(0.44, kWingRadiusY), 0.035);
    float aNose = ellipseAlpha(pWarped, vec2(0.42, 0.0), vec2(0.16, 0.14), 0.03);

    // Tail whip: a soft oval paddle (wider than the old razor-thin sliver) trailing behind,
    // swaying smoothly side-to-side on its own slow cadence - independent from the wing
    // undulation above (a much lower frequency, since a real tail whip is a slower, gentler
    // motion than the wing beat), so it reads as the tail's own loose sway rather than just
    // being dragged along with the wings. tailReach ramps the sway amplitude from ~0 right at
    // the body (tailBaseX, where it attaches) up to full strength at the tip, like a pendulum -
    // the base barely moves while the tip swings the widest arc.
    const float tailBaseX = -0.42;
    float tailReach = clamp((tailBaseX - p.x) / 0.64, 0.0, 1.0);
    float tailSway = sin(uSwimPhase * 0.5) * 0.16 * tailReach;
    vec2 pTail = vec2(p.x, p.y - tailSway);
    float aTail = ellipseAlpha(pTail, vec2(-0.74, 0.0), vec2(0.32, 0.05), 0.02);

    float aBody = max(aWings, aNose);

    // A pair of small eyes flanking the nose, on the top and bottom wingtip-ward sides (this is
    // drawn top-down/ventral rather than in profile, so the eyes sit left/right of the head -
    // i.e. at +-y here - rather than one eye on a visible flank).
    float aEyeTop = ellipseAlpha(pWarped, vec2(0.30, 0.12), vec2(0.028, 0.028), 0.012);
    float aEyeBot = ellipseAlpha(pWarped, vec2(0.30, -0.12), vec2(0.028, 0.028), 0.012);
    float aEye = max(aEyeTop, aEyeBot);

    float fishAlpha = max(max(aBody, aTail), aEye);

    // Shiny halo - see fish.frag's identical block for the full explanation. Anchored to aWings
    // (this file's dominant silhouette part, not the smaller aNose/aTail) via the same
    // (pWarped, center, radii) triple its own ellipseAlpha call above already uses. haloSigma is
    // tighter than fish.frag's default (0.24/0.15): kWingRadiusY already reaches ~0.952 of the
    // shared quad's own +-1.0 edge at full flap (see this file's kWingFlapAmplitude comment), so a
    // wider halo would hard-clip against that edge instead of fading out smoothly.
    float haloAlpha = 0.0;
    if (uShinyType > 0.5) {
        vec2 qHalo = (pWarped - vec2(0.02, 0.0)) / vec2(0.44, kWingRadiusY);
        float dEdge = length(qHalo) - 1.0;

        float isDiamond = step(1.5, uShinyType);
        float haloSigma = mix(0.10, 0.07, isDiamond);

        float breatheGold = 0.80 + 0.20 * sin(uSwimPhase);
        float breatheDiamond = pow(0.5 + 0.5 * sin(uSwimPhase * 3.0), 2.0);
        float breathe = mix(breatheGold, breatheDiamond, isDiamond);

        float halo = exp(-(dEdge * dEdge) / (haloSigma * haloSigma));
        halo *= mix(0.12, 1.0, smoothstep(-0.06, 0.02, dEdge));
        haloAlpha = halo * breathe;
    }

    if (fishAlpha <= 0.0 && haloAlpha <= 0.004) {
        discard;
    }

    // manta.frag doesn't otherwise track a combined "finalAlpha" separate from fishAlpha - the
    // shiny halo compositing below needs one to extend translucently past the true silhouette.
    float finalAlpha = fishAlpha;

    // Color mixing pipeline
    vec3 color = uWingColor;
    color = mix(color, uBodyColor, aBody);
    color = mix(color, uTailColor, aTail);

    // Pale shoulder chevron patches near the head (reef/oceanic manta rays' signature marking),
    // plus a few scattered spots further back across the wings - works as a stand-in for the
    // spotted eagle ray's rings too, and reads as subtle mottling on the plainer species.
    float shoulderTop = ellipseAlpha(pWarped, vec2(0.12, 0.32), vec2(0.18, 0.22), 0.05);
    float shoulderBot = ellipseAlpha(pWarped, vec2(0.12, -0.32), vec2(0.18, 0.22), 0.05);
    float spot1 = ellipseAlpha(pWarped, vec2(-0.18, 0.55), vec2(0.06, 0.06), 0.02);
    float spot2 = ellipseAlpha(pWarped, vec2(-0.10, -0.60), vec2(0.05, 0.05), 0.02);
    float spot3 = ellipseAlpha(pWarped, vec2(-0.30, 0.30), vec2(0.045, 0.045), 0.02);
    float markingsAlpha = max(max(shoulderTop, shoulderBot), max(max(spot1, spot2), spot3)) * aWings;
    color = mix(color, uMarkingColor, markingsAlpha);
    
    // Bioluminescent glow on markings (spots & chevrons) at night. Ramped through
    // smoothstep(0.0, kNightGlowEdge, uDayNight) rather than a plain (1.0 - uDayNight) - see
    // fish.frag's identical fix for why: the raw linear version made the glow already partway
    // visible as soon as the sun started dipping, well before the background actually looked
    // dark. This keeps it at 0 through day/dusk/dawn and only fades it in once it's genuinely
    // dark, in sync with the background.
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowStrength = nightGlow * 1.2;
    color += uGlowColor * markingsAlpha * glowStrength;

    // Eye colors (simple dark dot, no separate pupil - mantas' eyes read small at this scale).
    vec3 eyeColor = vec3(0.03, 0.03, 0.03);
    color = mix(color, eyeColor, aEye);

    // Shiny sheen - see fish.frag's identical block for the full explanation.
    if (uShinyType > 0.5) {
        float isDiamond = step(1.5, uShinyType);
        float eyeMask = 1.0 - aEye;

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

    // Soft rim lighting along the upper edges, separating the silhouette from the background.
    float wingsRim = rimLight(pWarped, vec2(0.02, 0.0), vec2(0.44, kWingRadiusY), aWings);
    float tailRim = rimLight(pTail, vec2(-0.74, 0.0), vec2(0.32, 0.05), aTail);
    float rimAmount = max(wingsRim, tailRim) * (1.0 - aEye);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.92);
    const float kRimIntensity = 0.40;
    color += kRimColor * rimAmount * kRimIntensity;

    // Shiny halo compositing - see fish.frag's identical block for the full explanation.
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
