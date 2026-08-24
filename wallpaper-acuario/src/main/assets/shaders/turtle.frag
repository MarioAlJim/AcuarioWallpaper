#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uShellColor;
uniform vec3 uHeadColor;
uniform vec3 uFlipperColor;
uniform vec3 uSpotColor;

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

// Deterministic pseudo-random 2D hash, used below to place one "feature point" per grid cell
// for the Voronoi scute pattern (same cell coordinate always yields the same point/shade, so
// the plate layout doesn't swim as the turtle moves).
vec2 hash2(vec2 cell) {
    vec2 h = vec2(dot(cell, vec2(127.1, 311.7)), dot(cell, vec2(269.5, 183.3)));
    return fract(sin(h) * 43758.5453123);
}

// Cellular/Voronoi lookup at `p`: returns (F1, F2, cellShade, cellSpot) where F1/F2 are the
// distances to the nearest and second-nearest feature points (the classic building block for
// scute/plate patterns - the shell's actual scute layout in real turtles is fairly regular, but
// the ask here was specifically the Voronoi technique), and cellShade/cellSpot are two
// independent, stable pseudo-random values in [0, 1) tied to the WINNING cell (the one F1 is
// measured to) rather than recomputed from floor(p) - a fragment right at a plate boundary can
// be closer to a neighboring cell's feature point than to its own cell's, so re-deriving the
// cell from floor(p) instead of tracking which one actually won would occasionally tag that
// fragment with the wrong plate's random values.
vec4 voronoi(vec2 p) {
    vec2 ip = floor(p);
    vec2 fp = fract(p);

    float f1 = 8.0;
    float f2 = 8.0;
    vec2 f1Cell = ip;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 point = hash2(ip + neighbor);
            float dist = length(neighbor + point - fp);
            if (dist < f1) {
                f2 = f1;
                f1 = dist;
                f1Cell = ip + neighbor;
            } else if (dist < f2) {
                f2 = dist;
            }
        }
    }

    vec2 cellRandom = hash2(f1Cell);
    return vec4(f1, f2, cellRandom.x, cellRandom.y);
}

// Thin rim-light contribution for one ellipse-shaped part, as if lit from directly above.
// `4*alpha*(1-alpha)` peaks right at the shape's own silhouette edge and fades to 0 both
// further inside and fully outside it (same trick as the "wallpaper" reference project's
// background rim lighting), and `upFacing` - the y-component of the outward direction from
// the ellipse's center, zeroed below the horizontal - keeps it confined to the upper arc
// instead of glowing all the way around. No branching, so it's safe to call unconditionally.
float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
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

    // Colors come from the turtle's randomly-assigned TurtlePalette (see AcuarioRenderer),
    // not hardcoded here, so each turtle in the tank can look like a different real species.
    vec3 eyeColor = vec3(0.02, 0.02, 0.02);

    vec3 color = uFlipperColor;
    color = mix(color, uHeadColor, aHead);

    // Shell scute (plate) pattern: a Voronoi cell diagram scaled non-uniformly to roughly match
    // the shell's own 1.5:1 aspect, so the plates read as a handful of roughly even rows/
    // columns across the carapace rather than a smear of dots.
    vec4 cell = voronoi(p * vec2(5.0, 3.3));
    float f1 = cell.x;
    float f2 = cell.y;
    float cellShade = cell.z;
    float cellSpot = cell.w;

    // Relief: each plate gets a soft "domed" highlight toward its own center (small F1) and a
    // dark groove right at the boundary between two plates (small F2 - F1) - a cheap fake-bevel
    // that reads as a raised, rugged scute instead of a flat painted-on pattern.
    float dome = 1.0 - smoothstep(0.0, 0.55, f1);
    float groove = 1.0 - smoothstep(0.0, 0.10, f2 - f1);

    vec3 shellColor = uShellColor * mix(0.90, 1.08, cellShade);
    shellColor *= mix(0.92, 1.05, dome);

    // A minority of plates (rather than every one) carry a duller/spotted tone, like the
    // natural mottling on a real shell.
    float spotted = step(0.75, cellSpot);
    shellColor = mix(shellColor, uSpotColor, spotted * 0.5 * dome);

    shellColor = mix(shellColor, uShellColor * 0.45, groove);

    color = mix(color, shellColor, aShell);
    color = mix(color, eyeColor, aEye);

    // Rim lighting on the head and flippers only (per the design ask - the shell already gets
    // its own "domed plate" shading above): a fine sunlit highlight along their upper edges,
    // helping the silhouette separate from a dark background. Masked by (1 - aShell) since the
    // shell is drawn last and can cover part of the head/front flippers where they tuck under
    // its front edge - without that mask this would incorrectly glow through the shell there.
    float headRim = rimLight(p, vec2(0.75, 0.05), vec2(0.22, 0.22), aHead);
    float flipperTopFrontRim = rimLight(p, vec2(0.10, 0.46 + frontFlap), vec2(0.34, 0.14), aFlipperTopFront);
    float flipperBotFrontRim = rimLight(p, vec2(0.10, -0.46 - frontFlap), vec2(0.34, 0.14), aFlipperBotFront);
    float flipperTopBackRim = rimLight(p, vec2(-0.38, 0.34 + backFlap), vec2(0.24, 0.11), aFlipperTopBack);
    float flipperBotBackRim = rimLight(p, vec2(-0.38, -0.34 - backFlap), vec2(0.24, 0.11), aFlipperBotBack);
    float flipperRim = max(max(flipperTopFrontRim, flipperBotFrontRim), max(flipperTopBackRim, flipperBotBackRim));
    float rimAmount = max(headRim, flipperRim) * (1.0 - aShell);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.90);
    const float kRimIntensity = 0.55;
    color += kRimColor * rimAmount * kRimIntensity;

    float alpha = max(bodyAlpha, aEye);
    fragColor = vec4(color, alpha);
}
