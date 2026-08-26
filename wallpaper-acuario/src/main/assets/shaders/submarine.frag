#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase; // used for propeller rotation
uniform vec3 uBodyColor;
uniform vec3 uAccentColor;
uniform vec3 uWindowColor;

out vec4 fragColor;

float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

void main() {
    // Center UV to [-1, 1] space. Submarine faces left.
    // The shared quad's V axis runs top(0)->bottom(1) on screen, opposite of this
    // shader's local +y-is-up convention, so the vertical component is flipped here.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Alpha masks for anatomical parts of the submarine
    float aBody = ellipseAlpha(p, vec2(0.0, 0.0), vec2(0.38, 0.14), 0.015);
    float aTower = ellipseAlpha(p, vec2(-0.06, 0.16), vec2(0.08, 0.08), 0.015);
    float aPeriscope = ellipseAlpha(p, vec2(-0.07, 0.27), vec2(0.010, 0.06), 0.008);
    float aTailFin = ellipseAlpha(p, vec2(0.36, 0.0), vec2(0.035, 0.14), 0.015);

    // Spinning propeller
    vec2 pPropeller = p - vec2(0.40, 0.0);
    float cosA = cos(uSwimPhase);
    float sinA = sin(uSwimPhase);
    vec2 pRot = vec2(pPropeller.x * cosA - pPropeller.y * sinA, pPropeller.x * sinA + pPropeller.y * cosA);
    float aBlade = ellipseAlpha(pRot, vec2(0.0, 0.0), vec2(0.015, 0.11), 0.008);
    float aHub = ellipseAlpha(p, vec2(0.40, 0.0), vec2(0.025, 0.025), 0.005);
    float aPropeller = max(aBlade, aHub);

    // Windows (Portholes) on the hull
    float w1 = ellipseAlpha(p, vec2(-0.16, -0.01), vec2(0.035, 0.035), 0.006);
    float w2 = ellipseAlpha(p, vec2(0.0, -0.01), vec2(0.035, 0.035), 0.006);
    float w3 = ellipseAlpha(p, vec2(0.16, -0.01), vec2(0.035, 0.035), 0.006);
    float aWindows = max(max(w1, w2), w3) * aBody; // must be inside the body

    // Combined solid submarine alpha
    float subAlpha = max(max(max(aBody, aTower), max(aPeriscope, aTailFin)), aPropeller);

    if (subAlpha <= 0.0) {
        discard;
    }

    // Color mixing
    vec3 color = uBodyColor;

    // Apply accent color to rudders, periscope, propeller
    float accentMask = max(max(aPeriscope, aTailFin), aPropeller);
    color = mix(color, uAccentColor, accentMask);

    // Apply main body color on top of base elements to ensure hull overlap
    color = mix(color, uBodyColor, aBody);
    color = mix(color, uBodyColor, aTower);

    // Draw glowing windows
    color = mix(color, uWindowColor, aWindows);

    // Simple rim lighting to separate from deep background
    vec2 q = p / vec2(0.38, 0.14);
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * subAlpha * (1.0 - subAlpha);
    float rim = pow(edgeBand, 1.4) * pow(upFacing, 2.0);
    vec3 kRimColor = vec3(1.0, 0.98, 0.90);
    color += kRimColor * rim * 0.40;

    fragColor = vec4(color, subAlpha);
}
