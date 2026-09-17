#version 300 es
precision highp float;

in vec2 vUV;
in vec2 vLocalPos;

uniform float uDayNight;
uniform highp float uTime;
uniform float uAspect;
uniform vec3 uBaseColor;
uniform vec3 uHighlightColor;
uniform vec3 uShadowColor;
uniform vec3 uDeepColor;

out vec4 fragColor;

// Bounded Hoskins 2D-to-1D hash - completely free of float16/mediump overflow and NaN artifacts
highp float hash(highp vec2 p) {
    highp vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    highp float safeTime = mod(uTime, 62.831853);

    // Quad coordinates: p.x in [-1, 1], p.y in [-1, 1] (-1 is bottom, +1 is top of quad)
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Aspect-ratio-scaled coordinates for uniform ripple frequencies
    vec2 uvScale = vec2(vUV.x * uAspect * 6.0, (1.0 - vUV.y) * 2.5);

    // 1. Dual-layer dune silhouettes
    // Scaled horizontal coordinate for aspect-ratio invariant dune shapes
    float duneX = vUV.x * max(uAspect, 0.5) * 3.5;

    // Back ridge (distant deeper seabed): base height raised and dune troughs bounded
    float duneBack = 0.38 + sin(duneX * 2.2 + 1.2) * 0.06 + sin(duneX * 4.8) * 0.03 + cos(duneX * 0.95) * 0.04;
    // Foreground dune (main seabed profile): base height elevated to embed flora roots seamlessly
    float duneFront = 0.22 + sin(duneX * 3.3 + 0.4) * 0.05 + sin(duneX * 6.5 + 2.0) * 0.025 + cos(duneX * 1.3) * 0.04;

    // Standard-compliant smoothstep where edge0 < edge1 strictly holds across all GPUs
    float alphaBack = 1.0 - smoothstep(duneBack - 0.012, duneBack + 0.015, p.y);
    float alphaFront = 1.0 - smoothstep(duneFront - 0.012, duneFront + 0.015, p.y);

    float totalAlpha = max(alphaBack, alphaFront);
    if (totalAlpha <= 0.001) {
        discard;
    }

    // 2. Fine mineral sand grains
    float grain = (hash(floor(vUV * vec2(320.0 * uAspect, 160.0))) - 0.5) * 0.05;
    float grainFine = (hash(floor(vUV * vec2(640.0 * uAspect, 320.0))) - 0.5) * 0.025;

    // 3. Current wave ripples on sand floor
    float rippleAngle = uvScale.x * 3.5 + uvScale.y * 1.2 + sin(uvScale.x * 1.5) * 0.6;
    float ripple = sin(rippleAngle * 6.2831853);
    float rippleShade = ripple * 0.035;

    // 4. Foreground dune coloring: smooth transition across dune slope, preserving ambient seabed brightness
    float depthInDune = clamp((duneFront - p.y) * 0.55, 0.0, 1.0);
    vec3 foreColor = mix(uHighlightColor, uBaseColor, smoothstep(0.0, 0.40, depthInDune));
    // Soft shadow blending that never crushes the sand into dark mud or black
    foreColor = mix(foreColor, uShadowColor, smoothstep(0.35, 1.0, depthInDune) * 0.25);
    foreColor += rippleShade * (1.0 - depthInDune * 0.35);
    foreColor += grain + grainFine;

    // 5. Back ridge coloring: shifted gently toward water ambient for atmospheric depth
    float depthInBack = clamp((duneBack - p.y) * 0.55, 0.0, 1.0);
    vec3 backColor = mix(uBaseColor * 0.90, uDeepColor, 0.20);
    backColor = mix(backColor, uShadowColor, smoothstep(0.35, 1.0, depthInBack) * 0.25);
    backColor += grain * 0.5;

    // Blend foreground dune over back ridge
    vec3 finalColor = mix(backColor, foreColor, alphaFront);

    // 6. Sunlight caustics (active during day, fade out at dusk/night)
    if (uDayNight > 0.02) {
        vec2 cUV = uvScale * 1.8 + vec2(safeTime * 0.06, safeTime * 0.03);
        float c1 = sin(cUV.x * 5.0 + sin(cUV.y * 4.0 + safeTime * 0.8));
        float c2 = cos(cUV.y * 6.0 + sin(cUV.x * 5.0 + safeTime * 0.6));
        float cVal = clamp(c1 * c2 + 0.3, 0.0, 1.0);
        float caustic = pow(cVal, 2.5) * 0.26 * uDayNight;
        finalColor += vec3(caustic * 0.90, caustic * 0.95, caustic * 1.0);
    }

    // 7. Night bioluminescent / mineral sparkles
    float nightFactor = 1.0 - uDayNight;
    if (nightFactor > 0.05) {
        vec2 spUV = floor(vUV * vec2(160.0 * uAspect, 80.0));
        float spHash = hash(spUV);
        if (spHash > 0.965) {
            float sparklePhase = sin(safeTime * 2.5 + spHash * 62.83) * 0.5 + 0.5;
            float sparkleIntensity = pow(sparklePhase, 3.0) * (spHash - 0.965) * 26.0;
            vec3 sparkleColor = mix(vec3(0.35, 0.85, 0.95), vec3(0.55, 0.95, 0.65), fract(spHash * 19.0));
            finalColor += sparkleColor * sparkleIntensity * nightFactor;
        }
    }

    // Enforce floor ambient floor to guarantee floor is never black
    finalColor = max(finalColor, uShadowColor * 0.50);
    fragColor = vec4(clamp(finalColor, 0.0, 1.0), totalAlpha);
}
