#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform float uGillPhase;
uniform float uLegPhase;

uniform vec3 uBodyColor;
uniform vec3 uGillColor;
uniform vec3 uBellyColor;
uniform vec3 uSpotColor;

uniform float uDayNight;
uniform vec3 uGlowColor;
uniform float uShinyType;
uniform highp float uTime;

out vec4 fragColor;

// Soft ellipse alpha
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Hinged limb segment extending from pivot
float hingedLimb(vec2 p, vec2 pivot, float restAngle, float hingeAngle, float limbLength, float halfWidth, float softness) {
    float totalAngle = restAngle + hingeAngle;
    vec2 d = p - pivot;
    float c = cos(totalAngle);
    float s = sin(totalAngle);
    vec2 local = vec2(d.x * c + d.y * s, -d.x * s + d.y * c);
    vec2 q = (local - vec2(limbLength * 0.5, 0.0)) / vec2(limbLength * 0.5, halfWidth);
    float dist = length(q) - 1.0;
    float w = softness / min(limbLength * 0.5, halfWidth);
    return smoothstep(w, -w, dist);
}

// Feathery external gill branch with frills
float featheryGill(vec2 p, vec2 pivot, float restAngle, float flutterAngle, float gillLength, float thickness) {
    float totalAngle = restAngle + flutterAngle;
    vec2 d = p - pivot;
    float c = cos(totalAngle);
    float s = sin(totalAngle);
    vec2 local = vec2(d.x * c + d.y * s, -d.x * s + d.y * c);
    
    // Main stem branch
    vec2 qStem = (local - vec2(gillLength * 0.5, 0.0)) / vec2(gillLength * 0.5, thickness);
    float stemDist = length(qStem) - 1.0;
    float stemAlpha = smoothstep(0.08, -0.08, stemDist);

    // Feathery side frills along the branch stem
    float frillPattern = sin(local.x * 60.0) * 0.5 + 0.5;
    float frillWidth = thickness * (1.5 + 1.2 * frillPattern);
    vec2 qFrill = (local - vec2(gillLength * 0.5, 0.0)) / vec2(gillLength * 0.48, frillWidth);
    float frillDist = length(qFrill) - 1.0;
    float frillAlpha = smoothstep(0.12, -0.12, frillDist) * 0.85;

    return max(stemAlpha, frillAlpha);
}

void main() {
    highp float safeTime = mod(uTime, 62.831853);

    // Transform UV to [-1, 1] space. Axolotl faces right (+x).
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Undulating body wave from head (p.x > 0.2) to extended tail tip (p.x < -0.8)
    float wave = sin(uSwimPhase - p.x * 4.0) * 0.13 * smoothstep(0.2, -0.82, p.x);
    vec2 pWarped = vec2(p.x, p.y + wave);

    // --- Anatomical Masks ---
    // 1. Head (cute wide rounded head)
    float aHead = ellipseAlpha(pWarped, vec2(0.30, 0.0), vec2(0.18, 0.16), 0.025);

    // 2. Trunk / Main Body
    float aBody = ellipseAlpha(pWarped, vec2(-0.02, 0.0), vec2(0.38, 0.14), 0.025);

    // 3. Elongated muscular tail stem (central fleshy core of the tail)
    float tailLen = 0.76;
    float tailStartX = -0.05;
    float tX = (pWarped.x - tailStartX) / (-tailLen);
    float tXClamped = clamp(tX, 0.0, 1.0);
    float stemHalfHeight = 0.11 * (1.0 - tXClamped * 0.82);
    float aTailStem = smoothstep(0.0, 0.05, tX) * smoothstep(1.0, 0.92, tX) * smoothstep(stemHalfHeight, stemHalfHeight - 0.02, abs(pWarped.y));

    // 4. Paddle Tail Fin membrane (broad translucent swimming fold running along the back/tail)
    float finStartX = 0.05;
    float finLen = tailLen + (finStartX - tailStartX);
    float tFin = (pWarped.x - finStartX) / (-finLen);
    float tFinClamped = clamp(tFin, 0.0, 1.0);
    float finHeight = 0.04 + 0.17 * sin(tFinClamped * 3.14159);
    float aTailFin = smoothstep(0.0, 0.05, tFin) * smoothstep(1.0, 0.90, tFin) * smoothstep(finHeight, finHeight - 0.02, abs(pWarped.y));

    // 5. Feathery External Gills (3 top, 3 bottom on head/neck at x = 0.18)
    vec2 neckPivot = vec2(0.18, 0.0);
    float flutter1 = sin(uGillPhase) * 0.10;
    float flutter2 = sin(uGillPhase + 1.0) * 0.12;
    float flutter3 = sin(uGillPhase + 2.0) * 0.11;

    // Top 3 gills
    float gTop1 = featheryGill(pWarped, neckPivot + vec2(0.04, 0.06), 0.55, flutter1, 0.22, 0.032);
    float gTop2 = featheryGill(pWarped, neckPivot + vec2(0.0, 0.08), 1.00, flutter2, 0.25, 0.035);
    float gTop3 = featheryGill(pWarped, neckPivot + vec2(-0.04, 0.07), 1.45, flutter3, 0.21, 0.030);

    // Bottom 3 gills
    float gBot1 = featheryGill(pWarped, neckPivot + vec2(0.04, -0.06), -0.55, -flutter1, 0.22, 0.032);
    float gBot2 = featheryGill(pWarped, neckPivot + vec2(0.0, -0.08), -1.00, -flutter2, 0.25, 0.035);
    float gBot3 = featheryGill(pWarped, neckPivot + vec2(-0.04, -0.07), -1.45, -flutter3, 0.21, 0.030);

    float aGills = max(max(max(gTop1, gTop2), gTop3), max(max(gBot1, gBot2), gBot3));

    // 6. 4 Small Limbs / Legs
    float legSwingFront = sin(uLegPhase) * 0.35;
    float legSwingBack = cos(uLegPhase) * 0.35;

    float aLegFrontTop = hingedLimb(pWarped, vec2(0.12, 0.10), 1.6, legSwingFront, 0.12, 0.035, 0.02);
    float aLegFrontBot = hingedLimb(pWarped, vec2(0.12, -0.10), -1.6, -legSwingFront, 0.12, 0.035, 0.02);
    float aLegBackTop  = hingedLimb(pWarped, vec2(-0.18, 0.10), 1.8, legSwingBack, 0.13, 0.038, 0.02);
    float aLegBackBot  = hingedLimb(pWarped, vec2(-0.18, -0.10), -1.8, -legSwingBack, 0.13, 0.038, 0.02);

    float aLegs = max(max(aLegFrontTop, aLegFrontBot), max(aLegBackTop, aLegBackBot));

    // 7. Facial Features (Cute Eye, Pupil, Shine, Mouth, Blush)
    vec2 eyeCenter = vec2(0.36, 0.07);
    float aEye = ellipseAlpha(pWarped, eyeCenter, vec2(0.035, 0.035), 0.015);
    float aPupil = ellipseAlpha(pWarped, eyeCenter + vec2(0.005, 0.0), vec2(0.020, 0.020), 0.01);
    float aEyeShine = ellipseAlpha(pWarped, eyeCenter + vec2(0.012, 0.012), vec2(0.008, 0.008), 0.008);

    // Cute smiling mouth arc
    vec2 mP = (pWarped - vec2(0.42, -0.03)) * vec2(15.0, 25.0);
    float aMouth = smoothstep(0.08, 0.02, abs(mP.y + mP.x * mP.x - 0.2)) * step(mP.x, 0.8) * step(-0.8, mP.x);

    // Cute blush cheek
    float aBlush = ellipseAlpha(pWarped, vec2(0.33, -0.04), vec2(0.045, 0.03), 0.03) * 0.45;

    // Combine overall alpha with realistic tail translucency
    float stemOpacity = mix(0.95, 0.65, tXClamped);
    float solidCore = max(max(aHead, aBody), aLegs);
    solidCore = max(solidCore, aTailStem * stemOpacity);

    // Delicate translucent tail fin membrane (alpha ~ 0.45 - 0.68)
    float finEdgeDist = abs(pWarped.y) / max(0.001, finHeight);
    float finAlpha = aTailFin * mix(0.68, 0.45, finEdgeDist);

    float coreBodyAlpha = max(solidCore, aTailStem);
    float creatureAlpha = max(max(solidCore, aGills), finAlpha);

    // --- Shiny Rare Aura ---
    float haloAlpha = 0.0;
    if (uShinyType > 0.5) {
        vec2 qHalo = (pWarped - vec2(-0.04, 0.0)) / vec2(0.58, 0.23);
        float dEdge = length(qHalo) - 1.0;
        float isDiamond = step(1.5, uShinyType);
        float haloSigma = mix(0.24, 0.15, isDiamond);

        float breathe = mix(0.80 + 0.20 * sin(safeTime * 2.0), pow(0.5 + 0.5 * sin(safeTime * 4.0), 2.0), isDiamond);
        float halo = exp(-(dEdge * dEdge) / (haloSigma * haloSigma));
        halo *= mix(0.12, 1.0, smoothstep(-0.06, 0.02, dEdge));
        haloAlpha = halo * breathe;
    }

    if (creatureAlpha <= 0.0 && haloAlpha <= 0.004) {
        discard;
    }

    // --- Shading & Texturing ---
    // Wavy ripple texture pattern across body (undulating waves instead of grid squares)
    float wavePattern = sin(pWarped.x * 20.0 + sin(pWarped.y * 14.0) * 1.6);
    float spotPattern = smoothstep(0.15, 0.80, wavePattern * 0.5 + 0.5) * 0.35;

    // Gradient shading: lighter belly at bottom, soft rim on top
    float verticalGrad = smoothstep(-0.15, 0.15, pWarped.y);
    vec3 baseColor = mix(uBellyColor, uBodyColor, verticalGrad);
    baseColor = mix(baseColor, uSpotColor, spotPattern * step(0.01, coreBodyAlpha));

    // Add blush to cheek
    baseColor = mix(baseColor, vec3(1.0, 0.4, 0.5), aBlush);

    // Color assembly
    vec3 finalColor = baseColor;

    // Blend translucent tail fin
    if (aTailFin > 0.0) {
        vec3 finCol = mix(uBodyColor, uGillColor, 0.3);
        finalColor = mix(finalColor, finCol, aTailFin * 0.75);
    }

    // Blend feathery gills with vivid gill color
    if (aGills > 0.0) {
        finalColor = mix(finalColor, uGillColor, aGills);
    }

    // Render eye
    if (aEye > 0.0) {
        finalColor = mix(finalColor, vec3(0.05, 0.05, 0.08), aEye);
        finalColor = mix(finalColor, vec3(0.01, 0.01, 0.01), aPupil);
        finalColor = mix(finalColor, vec3(1.0, 1.0, 1.0), aEyeShine);
    }

    // Render mouth
    if (aMouth > 0.0) {
        finalColor = mix(finalColor, vec3(0.3, 0.1, 0.15), aMouth);
    }

    // --- Night Bioluminescence ---
    float nightGlow = (1.0 - uDayNight);
    float glowPulse = 0.85 + 0.15 * sin(safeTime * 1.6);
    vec3 glowEmissive = uGlowColor * nightGlow * glowPulse;

    // Gills and tail fin fringe glow prominently in the dark
    float glowMask = max(aGills * 0.9, aTailFin * 0.6);
    finalColor += glowEmissive * glowMask;

    // --- Shiny Sheen Finish ---
    if (uShinyType > 0.5) {
        bool isDiamond = (uShinyType > 1.5);
        vec3 auraColor = isDiamond ? vec3(0.40, 0.92, 1.0) : vec3(1.0, 0.85, 0.20);
        
        // Specular sheen sweep across body
        float sheen = pow(max(0.0, sin(pWarped.x * 3.0 + safeTime * 2.5)), 4.0) * 0.4;
        finalColor += auraColor * sheen;

        if (haloAlpha > 0.0) {
            finalColor = mix(finalColor, auraColor, haloAlpha * 0.6);
        }
    }

    float finalAlpha = max(creatureAlpha, haloAlpha);
    fragColor = vec4(finalColor, finalAlpha);
}
