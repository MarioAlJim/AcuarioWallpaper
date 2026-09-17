#version 300 es
precision mediump float;

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

// 2D Hash helper for granular sand texture and nocturnal sparkles
float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 43.12);
    return fract(p.x * p.y);
}

void main() {
    highp float safeTime = mod(uTime, 62.831853);

    // Quad coordinates: p.x in [-1, 1], p.y in [-1, 1] (-1 is bottom, +1 is top of quad)
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Aspect-ratio-scaled coordinates for uniform ripple frequencies
    vec2 uvScale = vec2(vUV.x * uAspect * 6.0, (1.0 - vUV.y) * 2.5);

    // 1. Dual-layer dune silhouettes
    // Back ridge (distant deeper seabed)
    float duneBack = 0.36 + sin(vUV.x * 7.5 + 1.2) * 0.08 + sin(vUV.x * 16.0) * 0.035 + cos(vUV.x * 3.2) * 0.05;
    // Foreground dune (main seabed profile)
    float duneFront = 0.18 + sin(vUV.x * 11.5 + 0.4) * 0.07 + sin(vUV.x * 22.0 + 2.0) * 0.03 + cos(vUV.x * 4.5) * 0.06;

    float alphaBack = smoothstep(duneBack + 0.015, duneBack - 0.010, p.y);
    float alphaFront = smoothstep(duneFront + 0.015, duneFront - 0.010, p.y);

    if (max(alphaBack, alphaFront) <= 0.001) {
        discard;
    }

    // 2. Fine mineral sand grains
    float grain = (hash(floor(vUV * vec2(420.0 * uAspect, 200.0))) - 0.5) * 0.14;
    float grainFine = (hash(floor(vUV * vec2(840.0 * uAspect, 400.0))) - 0.5) * 0.07;

    // 3. Current wave ripples on sand floor
    float rippleAngle = uvScale.x * 3.5 + uvScale.y * 1.2 + sin(uvScale.x * 1.5) * 0.6;
    float ripple = sin(rippleAngle * 6.2831853);
    float rippleShade = ripple * 0.09;

    // 4. Foreground dune coloring: gradient from illuminated crest to settled depths
    float depthInDune = clamp((duneFront - p.y) / 0.85, 0.0, 1.0);
    vec3 foreColor = mix(uHighlightColor, uBaseColor, smoothstep(0.0, 0.35, depthInDune));
    foreColor = mix(foreColor, uShadowColor, smoothstep(0.35, 1.0, depthInDune));
    foreColor += rippleShade * (1.0 - depthInDune * 0.45);
    foreColor += grain + grainFine;

    // 5. Back ridge coloring: shifted toward deep water ambient for atmospheric perspective
    float depthInBack = clamp((duneBack - p.y) / 0.85, 0.0, 1.0);
    vec3 backColor = mix(uBaseColor * 0.82, uDeepColor, 0.40);
    backColor = mix(backColor, uShadowColor * 0.75, smoothstep(0.3, 1.0, depthInBack));
    backColor += grain * 0.5;

    // Blend foreground dune over back ridge
    vec3 finalColor = mix(backColor, foreColor, alphaFront);
    float totalAlpha = max(alphaBack, alphaFront);

    // 6. Sunlight caustics (active during day, fade out at dusk/night)
    if (uDayNight > 0.02) {
        vec2 cUV = uvScale * 1.8 + vec2(safeTime * 0.06, safeTime * 0.03);
        float c1 = sin(cUV.x * 5.0 + sin(cUV.y * 4.0 + safeTime * 0.8));
        float c2 = cos(cUV.y * 6.0 + sin(cUV.x * 5.0 + safeTime * 0.6));
        float caustic = pow(clamp(c1 * c2 + 0.3, 0.0, 1.0), 2.5) * 0.26 * uDayNight;
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

    fragColor = vec4(finalColor, totalAlpha);
}
