#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwayPhase;
uniform vec3 uBladeColor;
uniform vec3 uTipColor;
uniform vec3 uBaseColor;
uniform vec3 uHighlightColor;

out vec4 fragColor;

// Alpha mask for one bending blade, anchored at (bladeX, -1) - the bottom edge of the quad, i.e.
// the tank floor - growing straight up to tipY. `t` (0 at the anchor, 1 at the tip) drives both
// the taper (wide base, narrow tip) and the sideways bend: multiplying by t*t means the base
// barely moves while the tip sweeps the widest arc, the same trick fish.frag uses for its tail
// wag, just applied along the whole blade's height instead of just its rear third.
float bladeAlpha(vec2 p, float bladeX, float tipY, float phaseOffset, float baseHalfWidth, float tipHalfWidth) {
    float span = tipY + 1.0;
    float t = clamp((p.y + 1.0) / span, 0.0, 1.0);
    float sway = sin(uSwayPhase + phaseOffset) * 0.22 * t * t;
    float localX = p.x - (bladeX + sway);
    float halfWidth = mix(baseHalfWidth, tipHalfWidth, t);
    float softness = 0.02;
    float xMask = smoothstep(halfWidth, halfWidth - softness, abs(localX));
    float yMask = smoothstep(-1.0 - softness, -1.0 + softness, p.y) * smoothstep(tipY + softness, tipY - softness, p.y);
    return xMask * yMask;
}

void main() {
    // Center UV to [-1, 1] space. y = -1 is the tank floor (anchor), y = +1 the top of the
    // clump's bounding box. Same V-axis flip as fish.frag/manta.frag - see their identical
    // comment for why: the shared quad's V runs top(0)->bottom(1) on screen, opposite of this
    // shader's +y-is-up convention.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Three blades of varying height/width/phase make up one clump, so it reads as a small
    // patch of kelp rather than a single flat frond.
    float aBlade1 = bladeAlpha(p, -0.50, 0.85, 0.0, 0.17, 0.03);
    float aBlade2 = bladeAlpha(p, 0.05, 1.00, 2.1, 0.15, 0.025);
    float aBlade3 = bladeAlpha(p, 0.55, 0.72, 4.2, 0.16, 0.03);
    float alpha = max(max(aBlade1, aBlade2), aBlade3);
    if (alpha <= 0.0) {
        discard;
    }

    // Vertical color gradient: dark at the anchored base, main blade color through the middle,
    // lightening toward the tip - approximated on the raw p.y rather than per-blade since the
    // three blades' heights are close enough that one shared gradient reads fine.
    float vertT = clamp((p.y + 1.0) / 1.85, 0.0, 1.0);
    vec3 color = mix(uBaseColor, uBladeColor, smoothstep(0.0, 0.35, vertT));
    color = mix(color, uTipColor, smoothstep(0.55, 0.90, vertT));

    // A thin sheen along each blade's silhouette edge, same "peaks at the edge" trick as the
    // rimLight() helper elsewhere, just simplified (no directional bias - kelp sways every way).
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    color += uHighlightColor * pow(edgeBand, 1.6) * 0.35;

    fragColor = vec4(color, alpha);
}
