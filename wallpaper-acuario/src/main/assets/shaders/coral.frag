#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwayPhase;
uniform vec3 uBaseColor;
uniform vec3 uBranchColor;
uniform vec3 uPolypColor;
uniform vec3 uHighlightColor;

out vec4 fragColor;

// Soft "inside an ellipse" mask - see fish.frag/turtle.frag for the identical helper.
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// A branch's own bend at its tip (t = 1) - see branchAlpha's comment for why this is so much
// smaller than anemone.frag's tentacleAlpha bend: real coral is far stiffer than a soft-bodied
// anemone. Factored out so branchTip() below can place a polyp bulb exactly where the branch's
// own silhouette actually ends up, instead of at its unbent rest position.
float branchTipBend(float angle, float length, float phaseOffset) {
    return sin(uSwayPhase + phaseOffset) * 0.06 * length;
}

vec2 branchTip(vec2 base, float angle, float length, float phaseOffset) {
    vec2 dir = vec2(cos(angle), sin(angle));
    vec2 perp = vec2(-dir.y, dir.x);
    return base + dir * length + perp * branchTipBend(angle, length, phaseOffset);
}

// Alpha mask for one branch, rooted at `base` and reaching out along `angle` for `length` units
// - same rooted/curving shape as anemone.frag's tentacleAlpha, but with a much smaller bend
// amplitude (real coral barely sways - just enough to not read as a static prop) and less taper
// (branches stay fairly thick along their whole length instead of narrowing to a fine point).
float branchAlpha(vec2 p, vec2 base, float angle, float length, float phaseOffset, float baseHalfWidth) {
    vec2 dir = vec2(cos(angle), sin(angle));
    vec2 perp = vec2(-dir.y, dir.x);
    vec2 rel = p - base;
    float along = dot(rel, dir);
    float t = clamp(along / length, 0.0, 1.0);
    float bend = sin(uSwayPhase + phaseOffset) * 0.06 * t * t * length;
    float across = dot(rel, perp) - bend;
    float halfWidth = mix(baseHalfWidth, baseHalfWidth * 0.55, t);
    float softness = 0.02;
    float xMask = smoothstep(halfWidth, halfWidth - softness, abs(across));
    float yMask = smoothstep(-softness, softness, along) * smoothstep(length + softness, length - softness, along);
    return xMask * yMask;
}

void main() {
    // Center UV to [-1, 1] space. y = -1 is the tank floor (anchor). Same V-axis flip as
    // fish.frag/kelp.frag - see their identical comment.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    vec2 base = vec2(0.0, -1.0);

    // A rounded base mound at the anchor, same trick as anemone.frag's foot: its lower half
    // simply falls outside the quad's own vertical range, reading as sitting on the floor.
    float aBase = ellipseAlpha(p, base, vec2(0.30, 0.20), 0.03);

    // Seven branches fanning upward and outward, alternating shorter/longer for an irregular,
    // natural branching silhouette instead of a perfectly even fan.
    float angle1 = 0.50; float len1 = 0.85; float phase1 = 0.0;
    float angle2 = 0.80; float len2 = 1.10; float phase2 = 1.1;
    float angle3 = 1.10; float len3 = 0.90; float phase3 = 2.2;
    float angle4 = 1.5708; float len4 = 1.20; float phase4 = 3.3;
    float angle5 = 2.05; float len5 = 0.95; float phase5 = 4.4;
    float angle6 = 2.35; float len6 = 1.05; float phase6 = 5.5;
    float angle7 = 2.65; float len7 = 0.80; float phase7 = 0.6;

    float aB1 = branchAlpha(p, base, angle1, len1, phase1, 0.075);
    float aB2 = branchAlpha(p, base, angle2, len2, phase2, 0.08);
    float aB3 = branchAlpha(p, base, angle3, len3, phase3, 0.07);
    float aB4 = branchAlpha(p, base, angle4, len4, phase4, 0.085);
    float aB5 = branchAlpha(p, base, angle5, len5, phase5, 0.07);
    float aB6 = branchAlpha(p, base, angle6, len6, phase6, 0.08);
    float aB7 = branchAlpha(p, base, angle7, len7, phase7, 0.075);
    float aBranches = max(max(max(aB1, aB2), max(aB3, aB4)), max(max(aB5, aB6), aB7));

    // A small round polyp bulb at each branch tip, placed exactly where that branch's own bend
    // puts its end this frame.
    float aPolyp1 = ellipseAlpha(p, branchTip(base, angle1, len1, phase1), vec2(0.065, 0.065), 0.015);
    float aPolyp2 = ellipseAlpha(p, branchTip(base, angle2, len2, phase2), vec2(0.07, 0.07), 0.015);
    float aPolyp3 = ellipseAlpha(p, branchTip(base, angle3, len3, phase3), vec2(0.06, 0.06), 0.015);
    float aPolyp4 = ellipseAlpha(p, branchTip(base, angle4, len4, phase4), vec2(0.075, 0.075), 0.015);
    float aPolyp5 = ellipseAlpha(p, branchTip(base, angle5, len5, phase5), vec2(0.06, 0.06), 0.015);
    float aPolyp6 = ellipseAlpha(p, branchTip(base, angle6, len6, phase6), vec2(0.07, 0.07), 0.015);
    float aPolyp7 = ellipseAlpha(p, branchTip(base, angle7, len7, phase7), vec2(0.065, 0.065), 0.015);
    float aPolyps = max(max(max(aPolyp1, aPolyp2), max(aPolyp3, aPolyp4)), max(max(aPolyp5, aPolyp6), aPolyp7));

    float alpha = max(max(aBase, aBranches), aPolyps);
    if (alpha <= 0.0) {
        discard;
    }

    vec3 color = mix(uBaseColor, uBranchColor, smoothstep(-1.0, -0.3, p.y));
    color = mix(color, uPolypColor, aPolyps);

    // Thin sheen along each part's silhouette edge.
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    color += uHighlightColor * pow(edgeBand, 1.6) * 0.30;

    fragColor = vec4(color, alpha);
}
