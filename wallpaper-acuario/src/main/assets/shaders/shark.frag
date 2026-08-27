#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uBodyColor;
uniform float uDayNight;
uniform vec3 uGlowColor;

out vec4 fragColor;

// Soft "inside an ellipse" mask
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
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

    // Organic swimming tail-wag warp
    float wave = sin(uSwimPhase - p.x * 2.8) * 0.12 * smoothstep(0.4, -0.6, p.x);
    vec2 pWarped = vec2(p.x, p.y + wave);

    // 1. Torpedo-shaped body with a tapered, thinner tail peduncle
    float tailTaper = 1.0;
    if (pWarped.x < 0.15) {
        tailTaper = 1.0 + (0.15 - pWarped.x) * 1.6;
    }
    vec2 pBody = vec2(pWarped.x, pWarped.y * tailTaper);
    float aBodyRaw = ellipseAlpha(pBody, vec2(0.05, -0.02), vec2(0.55, 0.15), 0.012);

    // Mouth cut-out shifted closer to the tip/nose
    float aMouthCut = ellipseAlpha(pWarped, vec2(0.41, -0.11), vec2(0.065, 0.022), 0.01) * aBodyRaw;
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
    // tapered to a less pointy, more rounded tip.
    float cos35 = 0.819;
    float sin35 = -0.574;

    // Foreground fin:
    vec2 pPect1 = pWarped - vec2(0.22, -0.11);
    vec2 pPectRot1 = vec2(pPect1.x * cos35 + pPect1.y * sin35, -pPect1.x * sin35 + pPect1.y * cos35);
    float pect1T = clamp(pPectRot1.x / 0.19, 0.0, 1.0);
    float pect1Curve = 0.03 * pect1T * pect1T; // droops downward toward the tip
    float pect1Taper = clamp(1.0 - pect1T, 0.22, 1.0);
    float aPectoral1 = ellipseAlpha(vec2(pPectRot1.x, (pPectRot1.y - pect1Curve) / pect1Taper), vec2(0.0, 0.0), vec2(0.19, 0.06), 0.003) * step(pWarped.y, 0.0);

    // Background fin (shifted slightly left/up and smaller):
    vec2 pPect2 = pWarped - vec2(0.14, -0.06);
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

    // Mouth throat and teeth (shifted closer to the snout tip)
    float inMouthX = smoothstep(0.33, 0.35, pWarped.x) * smoothstep(0.49, 0.47, pWarped.x);
    float aTeeth = 0.0;
    if (inMouthX > 0.0) {
        float toothPattern = fract((pWarped.x - 0.33) * 35.0);
        float triangle = 1.0 - 2.0 * abs(toothPattern - 0.5);
        float toothHeight = 0.02 * triangle;
        
        // Upper jaw teeth hanging down
        float upperJawY = -0.09 + (pWarped.x - 0.33) * 0.12;
        if (pWarped.y < upperJawY && pWarped.y > upperJawY - toothHeight) {
            aTeeth = inMouthX * smoothstep(-0.005, 0.0, pWarped.y - (upperJawY - toothHeight));
        }
        
        // Lower jaw teeth pointing up
        float lowerJawY = -0.12 + (pWarped.x - 0.33) * 0.12;
        if (pWarped.y > lowerJawY && pWarped.y < lowerJawY + toothHeight) {
            aTeeth = max(aTeeth, inMouthX * smoothstep(0.005, 0.0, pWarped.y - (lowerJawY + toothHeight)));
        }
    }
    aTeeth *= aMouthCut;

    vec3 throatColor = vec3(0.3, 0.05, 0.05); // dark blood red
    bodyCol = mix(bodyCol, throatColor, aMouthCut);
    bodyCol = mix(bodyCol, vec3(0.95, 0.95, 0.95), aTeeth);

    // 3. Blend the body on top of the background fin
    color = mix(color, bodyCol, aBody);

    // 4. Blend the foreground fins (dorsal, tail, anal/ventral, and foreground pectoral) on top of the body
    float fgFins = max(max(max(aTailFin, aDorsal), aPectoral1), aVentral);
    color = mix(color, uBodyColor, fgFins);

    // Day-Night shading
    float ambientFactor = 0.35 + 0.65 * uDayNight;
    color *= ambientFactor;

    // Bioluminescent glow (only on the colored part of the body and fins, not on gills, eye, or mouth interior/teeth)
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowStrength = nightGlow * 1.2;

    float bodyGlow = aBody * (1.0 - bellyFactor) * (1.0 - aGills) * (1.0 - aEye) * (1.0 - aMouthCut);
    float finsGlow = max(max(max(aTailFin, aDorsal), aPectoral1), aPectoral2 * (1.0 - aBodyRaw));
    float glowMask = max(bodyGlow, finsGlow);

    color += uGlowColor * glowMask * glowStrength;

    // Rim lighting (glowing edges)
    float bodyRim = rimLight(pWarped, vec2(0.05, -0.02), vec2(0.55, 0.15), aBody);
    float dorsalRim = rimLight(pWarped, vec2(-0.05, 0.12), vec2(0.12, 0.18), aDorsal);
    float tailRim = rimLight(pWarped, vec2(-0.45, 0.0), vec2(0.1, 0.3), aTailFin);
    float rimAmount = max(max(bodyRim, dorsalRim), tailRim) * (1.0 - aEye);
    const vec3 kRimColor = vec3(0.90, 0.95, 1.00);
    color += kRimColor * rimAmount * 0.40;

    fragColor = vec4(color, sharkAlpha);
}
