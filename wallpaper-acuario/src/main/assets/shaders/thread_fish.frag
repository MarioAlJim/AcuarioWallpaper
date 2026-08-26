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

    // Thread-fin fish shape (Moorish Idol style)
    float aBody = ellipseAlpha(pWarped, vec2(0.04, 0.0), vec2(0.35, 0.26), 0.025);
    float aDorsalFin = ellipseAlpha(pWarped, vec2(-0.18, 0.48), vec2(0.06, 0.46), 0.025);
    float aVentralFin = ellipseAlpha(pWarped, vec2(-0.10, -0.28), vec2(0.12, 0.18), 0.025);
    
    float tailLen = 0.18;
    float tX = (pWarped.x - (-0.28)) / (-tailLen);
    float yNorm = pWarped.y / (0.04 + 0.15 * clamp(tX, 0.0, 1.0));
    float tXLimit = 0.7 + 0.3 * (yNorm * yNorm);
    float xBounds = smoothstep(0.0, 0.05, tX) * smoothstep(tXLimit, tXLimit - 0.05, tX);
    float halfHeight = 0.04 + 0.15 * clamp(tX, 0.0, 1.0);
    float yBounds = smoothstep(halfHeight, halfHeight - 0.025, abs(pWarped.y));
    float aTailFin = xBounds * yBounds;
    
    float aEye = ellipseAlpha(pWarped, vec2(0.22, 0.07), vec2(0.036, 0.036), 0.015);
    float aPupil = ellipseAlpha(pWarped, vec2(0.23, 0.07), vec2(0.018, 0.018), 0.01);

    float finsAlpha = max(max(aTailFin, aDorsalFin), aVentralFin);
    float fishAlpha = max(max(aBody, finsAlpha), aEye);
    if (fishAlpha <= 0.0) {
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

    float bodyRim = rimLight(pWarped, vec2(0.04, 0.0), vec2(0.35, 0.26), aBody);
    float dorsalRim = rimLight(pWarped, vec2(-0.18, 0.48), vec2(0.06, 0.46), aDorsalFin);
    float tailRim = rimLight(pWarped, vec2(-0.38, 0.0), vec2(0.12, 0.20), aTailFin);
    float rimAmount = max(max(bodyRim, dorsalRim), tailRim) * (1.0 - aEye);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.90);
    const float kRimIntensity = 0.50;
    color += kRimColor * rimAmount * kRimIntensity;

    float finalAlpha = max(fishAlpha, aPupil);
    fragColor = vec4(color, finalAlpha);
}
