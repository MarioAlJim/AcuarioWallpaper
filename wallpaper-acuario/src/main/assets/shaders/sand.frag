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
uniform int uIsForeground; // 0 = main seabed (background layer), 1 = foreground rim covering bottom 10% of flora
uniform float uBioluminescence; // 1.0 = enabled, 0.0 = disabled

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

    // Scaled horizontal coordinate for aspect-ratio invariant dune shapes
    float duneX = vUV.x * max(uAspect, 0.5) * 3.5;

    // Fine mineral sand grains
    float grain = (hash(floor(vUV * vec2(320.0 * uAspect, 160.0))) - 0.5) * 0.05;
    float grainFine = (hash(floor(vUV * vec2(640.0 * uAspect, 320.0))) - 0.5) * 0.025;

    // Current wave ripples on sand floor
    float rippleAngle = uvScale.x * 3.5 + uvScale.y * 1.2 + sin(uvScale.x * 1.5) * 0.6;
    float ripple = sin(rippleAngle * 6.2831853);
    float rippleShade = ripple * 0.035;

    float totalAlpha;
    vec3 finalColor;

    if (uIsForeground == 1) {
        // Foreground sand: strictly generates ~10% over the flora anchor line (kPlantFloorY = -0.80)
        // while the remaining ~90% extends downward to anchor the seabed to the bottom of the screen.
        float duneCrest = 0.87 + sin(duneX * 3.3 + 0.4) * 0.035 + cos(duneX * 1.3) * 0.025;
        float alphaFront = 1.0 - smoothstep(duneCrest - 0.012, duneCrest + 0.015, p.y);
        totalAlpha = alphaFront;

        if (totalAlpha <= 0.001) {
            discard;
        }

        float depthInDune = clamp((duneCrest - p.y) / 1.8, 0.0, 1.0);
        vec3 foreColor = mix(uHighlightColor, uBaseColor, smoothstep(0.0, 0.35, depthInDune));
        foreColor = mix(foreColor, uShadowColor, smoothstep(0.35, 1.0, depthInDune) * 0.25);
        foreColor += rippleShade * (1.0 - depthInDune * 0.35);
        foreColor += grain + grainFine;
        finalColor = foreColor;
    } else {
        // Main seabed (background layer, drawn behind flora and rocks):
        // Dual-layer dune silhouettes rising behind the plants for atmospheric perspective.
        float duneBack = 0.38 + sin(duneX * 2.2 + 1.2) * 0.06 + sin(duneX * 4.8) * 0.03 + cos(duneX * 0.95) * 0.04;
        float duneFront = 0.22 + sin(duneX * 3.3 + 0.4) * 0.05 + sin(duneX * 6.5 + 2.0) * 0.025 + cos(duneX * 1.3) * 0.04;

        float alphaBack = 1.0 - smoothstep(duneBack - 0.012, duneBack + 0.015, p.y);
        float alphaFront = 1.0 - smoothstep(duneFront - 0.012, duneFront + 0.015, p.y);
        totalAlpha = max(alphaBack, alphaFront);

        if (totalAlpha <= 0.001) {
            discard;
        }

        float depthInDune = clamp((duneFront - p.y) * 0.55, 0.0, 1.0);
        vec3 foreColor = mix(uHighlightColor, uBaseColor, smoothstep(0.0, 0.40, depthInDune));
        foreColor = mix(foreColor, uShadowColor, smoothstep(0.35, 1.0, depthInDune) * 0.25);
        foreColor += rippleShade * (1.0 - depthInDune * 0.35);
        foreColor += grain + grainFine;

        float depthInBack = clamp((duneBack - p.y) * 0.55, 0.0, 1.0);
        vec3 backColor = mix(uBaseColor * 0.90, uDeepColor, 0.20);
        backColor = mix(backColor, uShadowColor, smoothstep(0.35, 1.0, depthInBack) * 0.25);
        backColor += grain * 0.5;

        finalColor = mix(backColor, foreColor, alphaFront);
    }

    // Sunlight caustics (active during day, fade out at dusk/night)
    if (uDayNight > 0.02) {
        vec2 cUV = uvScale * 1.8 + vec2(safeTime * 0.06, safeTime * 0.03);
        float c1 = sin(cUV.x * 5.0 + sin(cUV.y * 4.0 + safeTime * 0.8));
        float c2 = cos(cUV.y * 6.0 + sin(cUV.x * 5.0 + safeTime * 0.6));
        float cVal = clamp(c1 * c2 + 0.3, 0.0, 1.0);
        float caustic = pow(cVal, 2.5) * 0.26 * uDayNight;
        finalColor += vec3(caustic * 0.90, caustic * 0.95, caustic * 1.0);
    }

    // Night bioluminescent / mineral sparkles (circular point-like glow, avoiding flat rectangular cells)
    float nightFactor = (1.0 - uDayNight) * uBioluminescence;
    if (nightFactor > 0.05) {
        float quadWidth = (uIsForeground == 1) ? (uAspect * 4.5) : (uAspect * 4.2);
        float quadHeight = (uIsForeground == 1) ? 0.33 : 0.95;
        vec2 spCoord = vec2(vUV.x * quadWidth, vUV.y * quadHeight) * 35.0;
        vec2 spUV = floor(spCoord);
        vec2 spFrac = fract(spCoord);
        float spHash = hash(spUV);
        if (spHash > 0.965) {
            // Pseudo-random sub-cell center offset kept within [0.25, 0.75]
            vec2 center = vec2(
                fract(spHash * 31.41),
                fract(spHash * 73.17)
            ) * 0.5 + 0.25;

            float dist = length(spFrac - center);
            float radius = 0.22;
            float spot = smoothstep(radius, 0.0, dist);
            float core = smoothstep(radius * 0.45, 0.0, dist);
            float spotMask = spot * spot + core * 0.8;

            float sparklePhase = sin(safeTime * 2.5 + spHash * 62.83) * 0.5 + 0.5;
            float sparkleIntensity = pow(sparklePhase, 3.0) * (spHash - 0.965) * 40.0 * spotMask;
            vec3 sparkleColor = mix(vec3(0.35, 0.85, 0.95), vec3(0.55, 0.95, 0.65), fract(spHash * 19.0));
            finalColor += sparkleColor * sparkleIntensity * nightFactor;
        }
    }

    // Enforce floor ambient floor to guarantee floor is never black
    finalColor = max(finalColor, uShadowColor * 0.50);
    fragColor = vec4(clamp(finalColor, 0.0, 1.0), totalAlpha);
}
