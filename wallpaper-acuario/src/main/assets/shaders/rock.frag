#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uShapeType;
uniform vec3 uBaseColor;
uniform vec3 uHighlightColor;
uniform vec3 uShadowColor;
uniform vec3 uSpotColor;
uniform float uDayNight;
uniform vec3 uGlowColor;
uniform highp float uTime;

out vec4 fragColor;

// 2D Noise helper for natural organic rock contours and mineral textures
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

void main() {
    highp float safeTime = mod(uTime, 62.831853);

    // Map UVs: x in [-1, 1], y in [-1, 1] (y = -1 is base on floor, y = +1 is top of rock quad)
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (vUV.y - 0.5) * 2.0);

    // Surface texture noise for organic rock displacement
    float n = noise(p * 4.5 + vec2(uShapeType * 3.7, uShapeType * 1.9)) * 0.12;

    float dist = 1.0;
    float type = floor(uShapeType + 0.5);

    if (type < 0.5) {
        // Shape 0: Smooth rounded river boulder
        vec2 q = p * vec2(1.0, 1.35) - vec2(0.0, -0.20);
        dist = length(q) - (0.75 + n);
    } else if (type < 1.5) {
        // Shape 1: Flat slate ledge (wide low-profile stone)
        vec2 q = p * vec2(0.85, 1.70) - vec2(0.0, -0.30);
        float dBox = max(abs(q.x) - 0.70, abs(q.y) - 0.45);
        dist = dBox - n;
    } else if (type < 2.5) {
        // Shape 2: Jagged faceted aquarium rock
        vec2 q = p * vec2(1.1, 1.2) - vec2(0.0, -0.15);
        float tri = max(abs(q.x) * 0.85 + q.y * 0.5, -q.y * 0.7);
        dist = tri - (0.65 + n * 1.3);
    } else {
        // Shape 3: Twin-hump substrate rock
        vec2 q1 = p * vec2(1.3, 1.4) - vec2(-0.35, -0.25);
        vec2 q2 = p * vec2(1.4, 1.5) - vec2(0.35, -0.15);
        float d1 = length(q1) - (0.55 + n);
        float d2 = length(q2) - (0.48 + n);
        dist = min(d1, d2);
    }

    // Flat bottom truncation anchor at the seabed floor (p.y = -0.9)
    dist = max(dist, -p.y - 0.95);

    // Alpha with smooth anti-aliased edge
    float alpha = smoothstep(0.02, -0.02, dist);

    if (alpha <= 0.0) {
        discard;
    }

    // --- Shading and Texturing ---
    // Mineral spot/speckle pattern across stone surface
    float spot = noise(p * 18.0 + vec2(1.5, 3.2));
    spot = smoothstep(0.35, 0.75, spot) * 0.40;

    // Top lighting highlight (light coming from surface above)
    float topHighlight = smoothstep(-0.1, 0.7, p.y) * smoothstep(0.9, 0.0, abs(p.x * 0.7));

    // Base ambient shadow (darker near bottom floor crevice)
    float baseShadow = smoothstep(0.2, -0.9, p.y) * 0.50;

    // Horizontal mineral strata / grain lines for stone texture
    float strata = sin(p.y * 24.0 + n * 8.0) * 0.08 + 0.08;

    // Color assembly
    vec3 col = uBaseColor;
    col = mix(col, uShadowColor, baseShadow);
    col = mix(col, uHighlightColor, topHighlight * 0.65);
    col = mix(col, uSpotColor, spot);
    col += uHighlightColor * strata * 0.3;

    // --- Night Bioluminescent Moss Glow ---
    float nightGlow = (1.0 - uDayNight);
    float glowPulse = 0.85 + 0.15 * sin(safeTime * 1.4 + p.x * 3.0);
    // Moss patches grow on top ridges and crevices
    float mossMask = smoothstep(0.0, 0.5, p.y + n) * (0.6 + 0.4 * spot);
    vec3 glowEmissive = uGlowColor * nightGlow * glowPulse * mossMask * 0.75;
    col += glowEmissive;

    fragColor = vec4(col, alpha);
}
