#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uBodyColor;
uniform vec3 uFinColor;
uniform vec3 uTailColor;
uniform vec3 uStripeColor;
uniform float uDayNight;
// Per-palette bioluminescent tint (see FishPalette.glowColor) - each species glows its own
// color instead of every fish sharing one fixed neon hue.
uniform vec3 uGlowColor;

out vec4 fragColor;

// Soft "inside an ellipse" mask
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Rim-light contribution Confined to the upper arc
float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
}

void main() {
    // Center UV to [-1, 1] space. Fish faces right.
    // The shared quad's V axis runs top(0)->bottom(1) on screen, opposite of this
    // shader's local +y-is-up convention (dorsal fin at +y, ventral at -y), so the
    // vertical component is flipped here to keep the fish right-side up.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Organic swimming tail-wag: Warp coordinate y-axis based on x-axis and swim phase.
    // Bending is 0 at the head (p.x > 0.2) and reaches maximum at the tail (p.x < -0.6).
    float wave = sin(uSwimPhase - p.x * 3.5) * 0.16 * smoothstep(0.2, -0.6, p.x);
    vec2 pWarped = vec2(p.x, p.y + wave);

    // Alpha masks for different anatomical parts (drawn in warped space for unified wave motion)
    float aBody = ellipseAlpha(pWarped, vec2(0.05, 0.0), vec2(0.45, 0.22), 0.025);

    // Triangular / crescent half-moon tail fin (attachment at x = -0.32, flares to tips extending to x = -0.47)
    float tailLen = 0.15;
    float tX = (pWarped.x - (-0.32)) / (-tailLen);
    float yNorm = pWarped.y / (0.04 + 0.16 * clamp(tX, 0.0, 1.0));
    // tXLimit creates the concave half-moon cutout (from 0.7 at center to 1.0 at tips)
    float tXLimit = 0.7 + 0.3 * (yNorm * yNorm);
    float xBounds = smoothstep(0.0, 0.05, tX) * smoothstep(tXLimit, tXLimit - 0.05, tX);
    float halfHeight = 0.04 + 0.16 * clamp(tX, 0.0, 1.0);
    float yBounds = smoothstep(halfHeight, halfHeight - 0.025, abs(pWarped.y));
    float aTailFin = xBounds * yBounds;
    float aDorsalFin = ellipseAlpha(pWarped, vec2(-0.05, 0.22), vec2(0.24, 0.10), 0.025);
    float aVentralFin = ellipseAlpha(pWarped, vec2(-0.10, -0.22), vec2(0.18, 0.08), 0.025);
    float aEye = ellipseAlpha(pWarped, vec2(0.32, 0.06), vec2(0.038, 0.038), 0.015);
    float aPupil = ellipseAlpha(pWarped, vec2(0.33, 0.06), vec2(0.020, 0.020), 0.01);

    // Check if fragment is within any part of the fish body/fins
    float finsAlpha = max(max(aTailFin, aDorsalFin), aVentralFin);
    float fishAlpha = max(max(aBody, finsAlpha), aEye);
    if (fishAlpha <= 0.0) {
        discard;
    }

    // Color mixing pipeline
    vec3 color = uFinColor;
    
    // Dorsal/ventral fins get fin color, tail fin gets tail color
    color = mix(color, uTailColor, aTailFin);
    
    // Body coloring
    color = mix(color, uBodyColor, aBody);

    // Vertical tropical stripes on the body
    float stripe1 = smoothstep(0.08, 0.0, abs(pWarped.x - 0.16));
    float stripe2 = smoothstep(0.08, 0.0, abs(pWarped.x + 0.05));
    float stripe3 = smoothstep(0.06, 0.0, abs(pWarped.x + 0.24));
    float stripesAlpha = max(max(stripe1, stripe2), stripe3) * aBody;
    color = mix(color, uStripeColor, stripesAlpha);
    
    // Bioluminescent glow on stripes at night. Ramped through smoothstep(0.0, kNightGlowEdge,
    // uDayNight) rather than a plain (1.0 - uDayNight) - the raw linear version made the glow
    // already partway visible as soon as the sun started dipping, well before the background
    // (acuario_background.frag, which itself darkens roughly linearly in uDayNight) actually
    // looked dark. Clamping the ramp to uDayNight's bottom slice instead keeps the glow at 0
    // through day/dusk/dawn and only fades it in once it's genuinely dark, in sync with the
    // background - see the same fix in manta.frag/turtle.frag.
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowStrength = nightGlow * 1.2;
    color += uGlowColor * stripesAlpha * glowStrength;

    // Eye colors
    vec3 eyeRingColor = vec3(0.95, 0.95, 0.95);
    vec3 pupilColor = vec3(0.05, 0.05, 0.05);
    color = mix(color, eyeRingColor, aEye);
    color = mix(color, pupilColor, aPupil);

    // Soft rim lighting along upper edges for separating from the background
    float bodyRim = rimLight(pWarped, vec2(0.05, 0.0), vec2(0.45, 0.22), aBody);
    float dorsalRim = rimLight(pWarped, vec2(-0.05, 0.22), vec2(0.24, 0.10), aDorsalFin);
    float tailRim = rimLight(pWarped, vec2(-0.40, 0.0), vec2(0.12, 0.20), aTailFin);
    float rimAmount = max(max(bodyRim, dorsalRim), tailRim) * (1.0 - aEye);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.90);
    const float kRimIntensity = 0.50;
    color += kRimColor * rimAmount * kRimIntensity;

    float finalAlpha = max(fishAlpha, aPupil);
    fragColor = vec4(color, finalAlpha);
}
