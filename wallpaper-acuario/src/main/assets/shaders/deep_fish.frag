#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uBodyColor;
uniform vec3 uFinColor;
uniform vec3 uTailColor;
uniform vec3 uStripeColor;
uniform float uDayNight;
uniform vec3 uGlowColor;
// 0.0 = normal, 1.0 = "shiny gold", 2.0 = "shiny diamond" (see Fish.kt's shinyType) - drives the
// premium halo/sheen finish below.
uniform float uShinyType;

out vec4 fragColor;

float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
}

void main() {
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    float wave = sin(uSwimPhase - p.x * 3.5) * 0.16 * smoothstep(0.2, -0.6, p.x);
    vec2 pWarped = vec2(p.x, p.y + wave);

    // Deep-bodied / round fish shape (Discus, Angelfish)
    float aBody = ellipseAlpha(pWarped, vec2(0.02, 0.0), vec2(0.32, 0.32), 0.025);
    float aDorsalFin = ellipseAlpha(pWarped, vec2(-0.12, 0.38), vec2(0.12, 0.30), 0.025);
    float aVentralFin = ellipseAlpha(pWarped, vec2(-0.15, -0.38), vec2(0.10, 0.30), 0.025);
    
    float tailLen = 0.15;
    float tX = (pWarped.x - (-0.25)) / (-tailLen);
    float yNorm = pWarped.y / (0.03 + 0.12 * clamp(tX, 0.0, 1.0));
    float tXLimit = 0.8 + 0.2 * (yNorm * yNorm);
    float xBounds = smoothstep(0.0, 0.05, tX) * smoothstep(tXLimit, tXLimit - 0.05, tX);
    float halfHeight = 0.03 + 0.12 * clamp(tX, 0.0, 1.0);
    float yBounds = smoothstep(halfHeight, halfHeight - 0.025, abs(pWarped.y));
    float aTailFin = xBounds * yBounds;
    
    float aEye = ellipseAlpha(pWarped, vec2(0.20, 0.08), vec2(0.038, 0.038), 0.015);
    float aPupil = ellipseAlpha(pWarped, vec2(0.21, 0.08), vec2(0.020, 0.020), 0.01);

    float finsAlpha = max(max(aTailFin, aDorsalFin), aVentralFin);
    float fishAlpha = max(max(aBody, finsAlpha), aEye);

    // Shiny halo - see fish.frag's identical block for the full explanation.
    float haloAlpha = 0.0;
    if (uShinyType > 0.5) {
        vec2 qHalo = (pWarped - vec2(0.02, 0.0)) / vec2(0.32, 0.32);
        float dEdge = length(qHalo) - 1.0;

        float isDiamond = step(1.5, uShinyType);
        float haloSigma = mix(0.24, 0.15, isDiamond);

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

    vec3 color = uFinColor;
    color = mix(color, uTailColor, aTailFin);
    color = mix(color, uBodyColor, aBody);

    float stripe1 = smoothstep(0.08, 0.0, abs(pWarped.x - 0.16));
    float stripe2 = smoothstep(0.08, 0.0, abs(pWarped.x + 0.05));
    float stripe3 = smoothstep(0.06, 0.0, abs(pWarped.x + 0.24));
    float stripesAlpha = max(max(stripe1, stripe2), stripe3) * aBody;
    color = mix(color, uStripeColor, stripesAlpha);
    
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowStrength = nightGlow * 1.2;
    color += uGlowColor * stripesAlpha * glowStrength;

    vec3 eyeRingColor = vec3(0.95, 0.95, 0.95);
    vec3 pupilColor = vec3(0.05, 0.05, 0.05);
    color = mix(color, eyeRingColor, aEye);
    color = mix(color, pupilColor, aPupil);

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

    float bodyRim = rimLight(pWarped, vec2(0.02, 0.0), vec2(0.32, 0.32), aBody);
    float dorsalRim = rimLight(pWarped, vec2(-0.12, 0.38), vec2(0.12, 0.30), aDorsalFin);
    float tailRim = rimLight(pWarped, vec2(-0.35, 0.0), vec2(0.10, 0.18), aTailFin);
    float rimAmount = max(max(bodyRim, dorsalRim), tailRim) * (1.0 - aEye);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.90);
    const float kRimIntensity = 0.50;
    color += kRimColor * rimAmount * kRimIntensity;

    float finalAlpha = max(fishAlpha, aPupil);

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
