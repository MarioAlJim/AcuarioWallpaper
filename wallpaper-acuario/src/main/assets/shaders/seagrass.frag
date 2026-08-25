#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwayPhase;
uniform vec3 uBladeColor;
uniform vec3 uTipColor;
uniform vec3 uBaseColor;
uniform vec3 uHighlightColor;

out vec4 fragColor;

// Alpha mask for one bending blade - identical technique to kelp.frag's bladeAlpha (anchored at
// (bladeX, -1), the tank floor, bending more toward its own tip than its base), just with much
// shorter/thinner blades and a snappier sway to read as low grass rather than tall kelp.
float bladeAlpha(vec2 p, float bladeX, float tipY, float phaseOffset, float baseHalfWidth, float tipHalfWidth) {
    float span = tipY + 1.0;
    float t = clamp((p.y + 1.0) / span, 0.0, 1.0);
    float sway = sin(uSwayPhase + phaseOffset) * 0.15 * t * t;
    float localX = p.x - (bladeX + sway);
    float halfWidth = mix(baseHalfWidth, tipHalfWidth, t);
    float softness = 0.015;
    float xMask = smoothstep(halfWidth, halfWidth - softness, abs(localX));
    float yMask = smoothstep(-1.0 - softness, -1.0 + softness, p.y) * smoothstep(tipY + softness, tipY - softness, p.y);
    return xMask * yMask;
}

void main() {
    // Center UV to [-1, 1] space. y = -1 is the tank floor (anchor). Same V-axis flip as
    // fish.frag/kelp.frag - see their identical comment.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Five short, thin blades make up one patch - denser and lower than a kelp clump, reading
    // as a carpet of grass rather than individual fronds.
    float aBlade1 = bladeAlpha(p, -0.70, 0.30, 0.0, 0.06, 0.008);
    float aBlade2 = bladeAlpha(p, -0.35, 0.48, 1.7, 0.055, 0.007);
    float aBlade3 = bladeAlpha(p, 0.02, 0.55, 3.1, 0.06, 0.008);
    float aBlade4 = bladeAlpha(p, 0.38, 0.40, 4.6, 0.05, 0.007);
    float aBlade5 = bladeAlpha(p, 0.70, 0.34, 5.9, 0.055, 0.008);
    float alpha = max(max(max(aBlade1, aBlade2), max(aBlade3, aBlade4)), aBlade5);
    if (alpha <= 0.0) {
        discard;
    }

    // Vertical color gradient: dark at the anchored base, lightening toward the tips.
    float vertT = clamp((p.y + 1.0) / 1.0, 0.0, 1.0);
    vec3 color = mix(uBaseColor, uBladeColor, smoothstep(0.0, 0.35, vertT));
    color = mix(color, uTipColor, smoothstep(0.55, 0.95, vertT));

    // A thin sheen along each blade's silhouette edge, same trick as kelp.frag's.
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    color += uHighlightColor * pow(edgeBand, 1.6) * 0.30;

    fragColor = vec4(color, alpha);
}
