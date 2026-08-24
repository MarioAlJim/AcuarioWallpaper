#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;

out vec4 fragColor;

// Soft "inside an ellipse" mask, 1.0 well inside `radii`, fading to 0.0 just past it.
// Not an exact distance field, but the edge softness stays visually consistent enough
// across the small shapes composed below (shell, head, flippers, tail, eye).
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

void main() {
    // Center the quad's UV into a [-1, 1] local space. The sprite is authored facing right;
    // AcuarioRenderer flips the whole quad horizontally (negative X scale) to face left.
    vec2 p = (vUV - 0.5) * 2.0;

    // Front flippers stroke together in a wide sweep; back flippers trail with a smaller,
    // phase-delayed motion, like a real sea turtle "flying" through the water.
    float frontFlap = sin(uSwimPhase) * 0.14;
    float backFlap = sin(uSwimPhase - 1.0) * 0.08;

    float aShell = ellipseAlpha(p, vec2(0.0, 0.0), vec2(0.60, 0.40), 0.025);
    float aHead = ellipseAlpha(p, vec2(0.75, 0.05), vec2(0.22, 0.22), 0.02);
    float aTail = ellipseAlpha(p, vec2(-0.72, 0.0), vec2(0.14, 0.08), 0.02);
    float aFlipperTopFront = ellipseAlpha(p, vec2(0.10, 0.46 + frontFlap), vec2(0.34, 0.14), 0.02);
    float aFlipperBotFront = ellipseAlpha(p, vec2(0.10, -0.46 - frontFlap), vec2(0.34, 0.14), 0.02);
    float aFlipperTopBack = ellipseAlpha(p, vec2(-0.38, 0.34 + backFlap), vec2(0.24, 0.11), 0.02);
    float aFlipperBotBack = ellipseAlpha(p, vec2(-0.38, -0.34 - backFlap), vec2(0.24, 0.11), 0.02);
    float aEye = ellipseAlpha(p, vec2(0.82, 0.10), vec2(0.035, 0.035), 0.01);

    float flippersAlpha = max(max(aFlipperTopFront, aFlipperBotFront), max(aFlipperTopBack, aFlipperBotBack));
    float bodyAlpha = max(max(aShell, aHead), max(aTail, flippersAlpha));
    if (bodyAlpha <= 0.0) {
        discard;
    }

    vec3 flipperColor = vec3(0.20, 0.42, 0.22);
    vec3 headColor = vec3(0.24, 0.48, 0.26);
    vec3 shellColor = vec3(0.16, 0.38, 0.20);
    vec3 spotColor = vec3(0.09, 0.24, 0.13);
    vec3 eyeColor = vec3(0.02, 0.02, 0.02);

    vec3 color = flipperColor;
    color = mix(color, headColor, aHead);
    color = mix(color, shellColor, aShell);

    // A loose speckle pattern on the shell only, breaking up the flat fill a little.
    float speckle = step(0.45, sin(p.x * 9.0 + 1.7) * sin(p.y * 9.0 + 0.6) * 0.5 + 0.5);
    color = mix(color, spotColor, speckle * aShell * 0.6);

    color = mix(color, eyeColor, aEye);

    float alpha = max(bodyAlpha, aEye);
    fragColor = vec4(color, alpha);
}
