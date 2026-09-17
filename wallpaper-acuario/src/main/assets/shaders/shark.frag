#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uBodyColor;
uniform float uDayNight;
uniform vec3 uGlowColor;
uniform float uBiteProgress; // 0.0 to 1.0 (interaction)
uniform highp float uTime;

out vec4 fragColor;

// Soft "inside an ellipse" mask
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Deterministic pseudo-random 2D hash for Voronoi caustics
vec2 hash2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Cellular/Voronoi lookup for caustics
vec2 voronoiCaustic(vec2 p) {
    vec2 ip = floor(p);
    vec2 fp = fract(p);
    float f1Sq = 64.0;
    float f2Sq = 64.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 point = hash2(ip + neighbor);
            vec2 diff = neighbor + point - fp;
            float distSq = dot(diff, diff);
            if (distSq < f1Sq) {
                f2Sq = f1Sq;
                f1Sq = distSq;
            } else if (distSq < f2Sq) {
                f2Sq = distSq;
            }
        }
    }
    return vec2(sqrt(f1Sq), sqrt(f2Sq));
}

// Rim-light contribution confined to the upper arc
float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
}

void main() {
    // Center UV to [-1, 1] space. Shark faces right (nose toward +x, tail toward -x).
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Organic swimming tail-wag warp - significantly more aggressive when biting (uBiteProgress > 0)
    // Increases both amplitude and frequency-offset for a "thrashing" look.
    float wagAmplitude = 0.12 + 0.14 * uBiteProgress;
    float wagFreq = 2.8 + 1.2 * uBiteProgress;
    float wave = sin(uSwimPhase - p.x * wagFreq) * wagAmplitude * smoothstep(0.4, -0.6, p.x);

    // Head shake: sharks thrash their head side-to-side when biting.
    // This affects the front half (+x) and is synced with a faster harmonic of swimPhase.
    float headShake = sin(uSwimPhase * 2.2) * 0.07 * uBiteProgress * smoothstep(-0.2, 0.6, p.x);

    vec2 pWarped = vec2(p.x, p.y + wave + headShake);

    // 1. Torpedo-shaped body with a tapered, thinner tail peduncle
    float tailTaper = 1.0;
    if (pWarped.x < 0.15) {
        tailTaper = 1.0 + (0.15 - pWarped.x) * 1.6;
    }
    vec2 pBody = vec2(pWarped.x, pWarped.y * tailTaper);
    float aBodyRaw = ellipseAlpha(pBody, vec2(0.05, -0.02), vec2(0.55, 0.15), 0.012);

    // Mouth cut-out shifted closer to the tip/nose
    // Mouth opens wide during bite interaction
    float mouthOpenY = -0.11 - 0.05 * uBiteProgress;
    float mouthRadiusY = 0.020 + 0.065 * uBiteProgress;
    float aMouthCut = ellipseAlpha(pWarped, vec2(0.41, mouthOpenY), vec2(0.072, mouthRadiusY), 0.008) * aBodyRaw;
    float aBody = clamp(aBodyRaw - aMouthCut, 0.0, 1.0);


    // 2. Dorsal fin (tapered to a sharp point, slanted back, pointing up, cut off at the body)
    vec2 pDorsal = pWarped - vec2(-0.05, 0.12);
    float cos25 = 0.906;
    float sin25 = 0.422;
    vec2 pDorsalRot = vec2(pDorsal.x * cos25 + pDorsal.y * sin25, -pDorsal.x * sin25 + pDorsal.y * cos25);
    float dorsalT = clamp(pDorsalRot.y / 0.19, 0.0, 1.0);
    float dorsalTaper = clamp(1.0 - dorsalT, 0.03, 1.0);
    float aDorsalRaw = ellipseAlpha(vec2(pDorsalRot.x / dorsalTaper, pDorsalRot.y), vec2(0.0, 0.0), vec2(0.075, 0.19), 0.004);
    float aDorsal = aDorsalRaw * (1.0 - aBodyRaw);

    // 3. Pectoral fins (foreground and background to show two lateral fins),
    // horizontally reflected so the base attaches behind the gills and sweeps backward/downward.
    float cos35 = 0.819;
    float sin35 = -0.574;

    // Foreground fin (horizontal reflection applied to sweep backward):
    vec2 pPect1 = vec2(-(pWarped.x - 0.14), pWarped.y - (-0.10));
    vec2 pPectRot1 = vec2(pPect1.x * cos35 + pPect1.y * sin35, -pPect1.x * sin35 + pPect1.y * cos35);
    float pect1T = clamp(pPectRot1.x / 0.19, 0.0, 1.0);
    float pect1Curve = 0.03 * pect1T * pect1T; // droops downward toward the tip
    float pect1Taper = clamp(1.0 - pect1T, 0.22, 1.0);
    float aPectoral1 = ellipseAlpha(vec2(pPectRot1.x, (pPectRot1.y - pect1Curve) / pect1Taper), vec2(0.0, 0.0), vec2(0.19, 0.06), 0.003) * step(pWarped.y, 0.0);

    // Background fin (horizontally reflected and offset for 3D depth):
    vec2 pPect2 = vec2(-(pWarped.x - 0.20), pWarped.y - (-0.05));
    vec2 pPectRot2 = vec2(pPect2.x * cos35 + pPect2.y * sin35, -pPect2.x * sin35 + pPect2.y * cos35);
    float pect2T = clamp(pPectRot2.x / 0.155, 0.0, 1.0);
    float pect2Curve = 0.025 * pect2T * pect2T;
    float pect2Taper = clamp(1.0 - pect2T, 0.22, 1.0);
    float aPectoral2 = ellipseAlpha(vec2(pPectRot2.x, (pPectRot2.y - pect2Curve) / pect2Taper), vec2(0.0, 0.0), vec2(0.155, 0.05), 0.003) * step(pWarped.y, 0.0);

    // 4. Ventral/anal fin eliminated completely
    float aVentral = 0.0;

    // 5. Caudal (tail) fin - classic asymmetrical shark tail (taller upper lobe)
    float tailLen = 0.27;
    float tX = (pWarped.x - (-0.45)) / (-tailLen);
    float halfHeight = 0.02 + 0.36 * clamp(tX, 0.0, 1.0);
    float yNorm = pWarped.y / halfHeight;
    float lobeLimit = mix(0.50, 1.0, step(0.0, pWarped.y));
    float tXLimit = lobeLimit * (0.5 + 0.5 * (yNorm * yNorm));
    float xBounds = smoothstep(0.0, 0.06, tX) * smoothstep(tXLimit, tXLimit - 0.04, tX);
    float yBounds = smoothstep(halfHeight, halfHeight - 0.012, abs(pWarped.y));
    float aTailFin = xBounds * yBounds;

    // Combine alpha masks
    float fgFinsAlpha = max(max(max(aTailFin, aDorsal), aPectoral1), aVentral);
    float sharkAlpha = max(max(aBodyRaw, fgFinsAlpha), aPectoral2);

    if (sharkAlpha <= 0.0) {
        discard;
    }

    // 6. Gills (three vertical lines)
    float gill1 = smoothstep(0.006, 0.0, abs(pWarped.x - 0.28)) * smoothstep(0.06, 0.04, abs(pWarped.y + 0.02));
    float gill2 = smoothstep(0.006, 0.0, abs(pWarped.x - 0.24)) * smoothstep(0.055, 0.04, abs(pWarped.y + 0.02));
    float gill3 = smoothstep(0.006, 0.0, abs(pWarped.x - 0.20)) * smoothstep(0.05, 0.04, abs(pWarped.y + 0.02));
    float aGills = max(max(gill1, gill2), gill3) * aBody;

    // 7. Eye (small dark dot)
    float aEye = ellipseAlpha(pWarped, vec2(0.42, 0.02), vec2(0.02, 0.02), 0.005);

    // 8. Countershading: dark back, light belly
    float bellyFactor = smoothstep(0.08, -0.10, pWarped.y);
    vec3 bellyColor = vec3(0.92, 0.92, 0.92);
    vec3 finalBodyColor = mix(uBodyColor, bellyColor, bellyFactor);

    // Color mixing: Layered to support bilateral (two) pectoral fins.
    // 1. Start with base background fin color (dark shadow on the far side)
    vec3 color = mix(uBodyColor * 0.45, finalBodyColor * 0.45, bellyFactor);
    color = mix(color * 0.45, color, aPectoral2);

    // 2. Build the main body color (including countershading, gills, eye, mouth interior, and teeth)
    vec3 bodyCol = mix(uBodyColor, finalBodyColor, aBody);
    bodyCol = mix(bodyCol, finalBodyColor * 0.75, aGills);
    bodyCol = mix(bodyCol, vec3(0.05, 0.05, 0.05), aEye);

    // Mouth throat cavity and razor-sharp serrated teeth (visible whenever bared)
    float inMouthX = smoothstep(0.335, 0.355, pWarped.x) * smoothstep(0.485, 0.465, pWarped.x);
    float aTeeth = 0.0;
    if (inMouthX > 0.0 && aMouthCut > 0.05 && uBiteProgress > 0.02) {
        // Upper jaw teeth: sharp triangles pointing down from upper jaw rim
        float upperToothPattern = fract((pWarped.x - 0.33) * 36.0);
        float upperTriangle = clamp(1.0 - 2.0 * abs(upperToothPattern - 0.5), 0.0, 1.0);
        float upperToothHeight = 0.028 * upperTriangle;
        float upperJawY = -0.082 + (pWarped.x - 0.33) * 0.11;
        if (pWarped.y <= upperJawY && pWarped.y >= upperJawY - upperToothHeight) {
            float tMask = smoothstep(-0.003, 0.002, pWarped.y - (upperJawY - upperToothHeight));
            aTeeth = max(aTeeth, inMouthX * tMask);
        }

        // Lower jaw teeth: sharp triangles pointing up from dropping lower jaw rim
        float lowerToothPattern = fract((pWarped.x - 0.33 + 0.014) * 36.0);
        float lowerTriangle = clamp(1.0 - 2.0 * abs(lowerToothPattern - 0.5), 0.0, 1.0);
        float lowerToothHeight = 0.028 * lowerTriangle;
        float lowerJawY = -0.108 - 0.065 * uBiteProgress + (pWarped.x - 0.33) * 0.11;
        if (pWarped.y >= lowerJawY && pWarped.y <= lowerJawY + lowerToothHeight) {
            float bMask = smoothstep(0.003, -0.002, pWarped.y - (lowerJawY + lowerToothHeight));
            aTeeth = max(aTeeth, inMouthX * bMask);
        }
    }
    aTeeth = clamp(aTeeth * smoothstep(0.05, 0.25, aMouthCut) * smoothstep(0.02, 0.15, uBiteProgress), 0.0, 1.0);

    vec3 throatColor = vec3(0.18, 0.02, 0.03); // deep dark crimson throat
    bodyCol = mix(bodyCol, throatColor, aMouthCut);
    vec3 teethColor = vec3(0.98, 0.98, 0.96); // bright ivory teeth
    bodyCol = mix(bodyCol, teethColor, aTeeth);

    // 3. Blend the entire body (skin + mouth cavity + teeth) on top of the background fin
    color = mix(color, bodyCol, aBodyRaw);

    // Caustics on the shark's skin (back only, similar to the turtle)
    highp float safeTime = mod(uTime, 62.831853);
    highp float t = safeTime * 0.35;
    vec2 cp = pWarped * 5.0 + vec2(sin(pWarped.y * 1.3 + t * 2.0), sin(pWarped.x * 1.1 - t * 1.6)) * 0.3;
    vec2 v = voronoiCaustic(cp);
    float caustic = pow(1.0 - smoothstep(0.0, 0.25, v.y - v.x), 1.5);

    // Only on the back (not the light belly)
    float backMask = smoothstep(-0.15, 0.10, pWarped.y) * aBodyRaw;
    color += vec3(0.35, 0.45, 0.55) * caustic * backMask * uDayNight * 0.7;

    // 4. Blend the foreground fins (dorsal, tail, anal/ventral, and foreground pectoral) on top of the body
    float fgFins = max(max(max(aTailFin, aDorsal), aPectoral1), aVentral);
    color = mix(color, uBodyColor, fgFins);

    // Day-Night shading
    float ambientFactor = 0.35 + 0.65 * uDayNight;
    // Ambient dimming for body and fins, but keep teeth gleaming bright white even at night
    color *= mix(ambientFactor, 1.0, aTeeth);

    // Bioluminescent glow (only on the colored part of the body and fins, not on gills, eye, or mouth interior/teeth)
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowStrength = nightGlow * 1.2;

    float bodyGlow = aBody * (1.0 - bellyFactor) * (1.0 - aGills) * (1.0 - aEye) * (1.0 - aMouthCut);
    float finsGlow = max(max(max(aTailFin, aDorsal), aPectoral1), aPectoral2 * (1.0 - aBodyRaw));
    float glowMask = max(bodyGlow, finsGlow);

    color += uGlowColor * glowMask * glowStrength;

    // Emissive gleaming highlight on teeth whenever bared (visible day and night)
    if (aTeeth > 0.0) {
        vec3 toothGlint = vec3(1.0, 1.0, 0.98);
        color = mix(color, toothGlint, aTeeth * 0.92);
    }

    // Rim lighting (glowing edges)
    float bodyRim = rimLight(pWarped, vec2(0.05, -0.02), vec2(0.55, 0.15), aBody);
    float dorsalRim = rimLight(pWarped, vec2(-0.05, 0.12), vec2(0.12, 0.18), aDorsal);
    float tailRim = rimLight(pWarped, vec2(-0.45, 0.0), vec2(0.1, 0.3), aTailFin);
    float rimAmount = max(max(bodyRim, dorsalRim), tailRim) * (1.0 - aEye);
    const vec3 kRimColor = vec3(0.90, 0.95, 1.00);
    color += kRimColor * rimAmount * 0.40;

    fragColor = vec4(color, sharkAlpha);
}
