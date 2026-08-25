#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwayPhase;
uniform vec3 uFootColor;
uniform vec3 uTentacleColor;
uniform vec3 uTipColor;
uniform vec3 uHighlightColor;

out vec4 fragColor;

// Soft "inside an ellipse" mask - see fish.frag/turtle.frag for the identical helper.
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Alpha mask for one curving tentacle, rooted at the anchor (0, -1) - the tank floor - and
// reaching out along `angle` (radians from +x) for `length` units. Same bend-grows-with-t^2
// idea as kelp.frag's blades, but curved sideways (perpendicular to the tentacle's own rest
// direction) rather than along a fixed screen axis, so each one waves outward from wherever it
// points instead of just left/right.
float tentacleAlpha(vec2 p, float angle, float length, float phaseOffset, float baseHalfWidth) {
    vec2 base = vec2(0.0, -1.0);
    vec2 dir = vec2(cos(angle), sin(angle));
    vec2 perp = vec2(-dir.y, dir.x);
    vec2 rel = p - base;
    float along = dot(rel, dir);
    float t = clamp(along / length, 0.0, 1.0);
    float bend = sin(uSwayPhase + phaseOffset) * 0.30 * t * t * length;
    float across = dot(rel, perp) - bend;
    float halfWidth = mix(baseHalfWidth, baseHalfWidth * 0.2, t);
    float softness = 0.02;
    float xMask = smoothstep(halfWidth, halfWidth - softness, abs(across));
    float yMask = smoothstep(-softness, softness, along) * smoothstep(length + softness, length - softness, along);
    return xMask * yMask;
}

void main() {
    // Center UV to [-1, 1] space. y = -1 is the tank floor (anchor). Same V-axis flip as
    // fish.frag/kelp.frag - see their identical comment.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // A rounded foot mound at the anchor - its lower half simply falls outside the quad's own
    // [-1, 1] vertical range, so it naturally reads as a mound sitting on the floor rather than
    // a floating ellipse.
    float aFoot = ellipseAlpha(p, vec2(0.0, -1.0), vec2(0.38, 0.24), 0.03);

    // Six tentacles fanning upward and outward from the foot, each with its own angle, length
    // and sway phase offset so they wave independently instead of in lockstep.
    float aT1 = tentacleAlpha(p, 0.55, 1.05, 0.0, 0.10);
    float aT2 = tentacleAlpha(p, 0.95, 1.25, 1.3, 0.11);
    float aT3 = tentacleAlpha(p, 1.30, 1.35, 2.6, 0.12);
    float aT4 = tentacleAlpha(p, 1.65, 1.30, 3.9, 0.115);
    float aT5 = tentacleAlpha(p, 2.05, 1.20, 5.2, 0.105);
    float aT6 = tentacleAlpha(p, 2.45, 1.00, 0.7, 0.095);

    float aTentacles = max(max(max(aT1, aT2), max(aT3, aT4)), max(aT5, aT6));
    float alpha = max(aFoot, aTentacles);
    if (alpha <= 0.0) {
        discard;
    }

    // Vertical color gradient: foot color at the anchor, through the main tentacle color,
    // lightening toward the tips - approximated on the raw p.y, same simplification as
    // kelp.frag's gradient.
    vec3 color = mix(uFootColor, uTentacleColor, smoothstep(-1.0, -0.4, p.y));
    color = mix(color, uTipColor, smoothstep(0.2, 0.9, p.y));

    // Thin sheen along each part's silhouette edge.
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    color += uHighlightColor * pow(edgeBand, 1.6) * 0.35;

    fragColor = vec4(color, alpha);
}
