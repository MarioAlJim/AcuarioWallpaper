#version 300 es
precision mediump float;

in vec2 vUv; // 0..1, y=0 at the bottom of the screen, y=1 at the top

uniform highp float uTime;
uniform int uTheme; // 0 = Acuario (contained, warmer light), 1 = Mar abierto (deeper, colder light)
uniform float uAspectRatio;

out vec4 fragColor;

// Deterministic pseudo-random 2D hash, used to place one "feature point" per grid cell for the
// Voronoi caustic network below. Same technique/constants as turtle.frag's own hash2() (reused
// here unchanged, just duplicated - this project's shaders are standalone files, no #include).
vec2 hash2(vec2 cell) {
    vec2 h = vec2(dot(cell, vec2(127.1, 311.7)), dot(cell, vec2(269.5, 183.3)));
    return fract(sin(h) * 43758.5453123);
}

// Cellular/Voronoi lookup at `p`: returns (F1, F2), the distances to the nearest and
// second-nearest feature points. (Local variable named `dist`, not `length`, so it doesn't
// shadow the builtin length() call right below it - the same footgun already documented next to
// turtle.frag's hingedLimbAlpha().)
vec2 voronoiCaustic(vec2 p) {
    vec2 ip = floor(p);
    vec2 fp = fract(p);

    float f1 = 8.0;
    float f2 = 8.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 point = hash2(ip + neighbor);
            float dist = length(neighbor + point - fp);
            if (dist < f1) {
                f2 = f1;
                f1 = dist;
            } else if (dist < f2) {
                f2 = dist;
            }
        }
    }

    return vec2(f1, f2);
}

void main() {
    float y = vUv.y;

    vec3 deepColor;
    vec3 shallowColor;
    float causticStrength;
    float rayStrength;

    if (uTheme == 0) {
        // Acuario: warmer greenish teal, light kept fairly contained (as if bounded by glass
        // and an artificial lamp above rather than open sky).
        deepColor = vec3(0.012, 0.095, 0.130);
        shallowColor = vec3(0.10, 0.44, 0.40);
        causticStrength = 0.06;
        rayStrength = 0.10;
    } else {
        // Mar abierto: colder, deeper blue with stronger sunlight shafts filtering from the
        // surface, reading as more open/infinite depth.
        deepColor = vec3(0.010, 0.045, 0.130);
        shallowColor = vec3(0.05, 0.34, 0.55);
        causticStrength = 0.10;
        rayStrength = 0.22;
    }

    // Base vertical gradient: deep/dark at the bottom, brighter near the "surface" at top.
    vec3 color = mix(deepColor, shallowColor, pow(y, 1.4));

    // God rays: soft slanted light shafts fanning down from the surface, strongest near the
    // top and fading out with depth.
    float raysMask = pow(y, 2.2);
    float ray = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float slant = 0.35 + fi * 0.15;
        float freq = 6.0 + fi * 2.3;
        float speed = 0.05 + fi * 0.02;
        // highp: derived directly from uTime, which grows unboundedly for as long as the
        // wallpaper runs. uTime itself is already highp, but that alone doesn't protect this -
        // assigning a highp-derived expression into an (implicitly mediump, per this file's
        // default precision) local truncates it right there. Without highp here, this
        // rediscovers the exact same mediump-precision bug already fixed for Turtle.kt's
        // swimPhase/turtle.frag's uSwimPhase, just one level removed behind an
        // already-correct-looking `uniform highp float uTime`.
        highp float phase = vUv.x * uAspectRatio * freq + y * slant * freq - uTime * speed * freq;
        ray += sin(phase) * 0.5 + 0.5;
    }
    ray /= 3.0;
    color += shallowColor * ray * raysMask * rayStrength;

    // Caustics: a cellular (Voronoi) light-net, the classic bright, curved, moving mesh seen on
    // a pool or reef floor. Real caustics are the SEAMS between neighboring focused-light cells,
    // not the cell interiors, so we look at F2-F1 (distance to the 2nd-nearest feature point
    // minus distance to the nearest) and light up where that gap is small - the same edge/groove
    // trick turtle.frag uses for scute boundaries, but here it drives the whole pattern instead
    // of just a fake bevel.
    //
    // The feature-point grid itself never moves - what animates is a small sinusoidal warp
    // applied to the sample position before the lookup, so the cell walls ripple and flow
    // organically over time instead of sliding past as a rigid grid (an actually-translating
    // Voronoi grid would read as tiles scrolling by, not water).
    vec2 p = vec2(vUv.x * uAspectRatio, y) * 6.0;
    // highp for the same reason as `phase` above: derived directly from the ever-growing
    // uTime, and every sin()/cos() call below (both the warp and the re-added sine caustic
    // further down) needs an accurate `t` to stay smooth no matter how long the wallpaper has
    // been running. `p`/`warp`/`cp` don't need highp themselves - they're bounded by screen
    // coordinates and sin() outputs respectively, never growing over time - only `t` (and
    // `phase` above) are direct multiples of the unbounded uTime.
    highp float t = uTime * 0.35;
    vec2 warp = vec2(
        sin(p.y * 1.3 + t * 2.0),
        sin(p.x * 1.1 - t * 1.6)
    ) * 0.3;
    vec2 cp = p + warp;

    // Chromatic aberration: real underwater caustics separate slightly by wavelength right at
    // their sharp edges. With no texture to sample here, we fake it by re-running the same
    // cellular lookup at a small position offset for red/blue (green stays on the un-shifted
    // sample) - since the pattern is ~0 everywhere except right at a light-net seam, this only
    // produces a visible red/blue fringe exactly where the pattern itself has contrast, never as
    // a flat screen-wide tint.
    //
    // The R/B *deviation* from the green (centered) sample is boosted by kAberrationBoost
    // independently of causticStrength/kAberrationOffset below, so the fringe stays clearly
    // visible even though causticStrength dims the whole caustic layer - without this, turning
    // the mesh down would also (wrongly) fade the color separation to invisible right along with it.
    const vec2 kAberrationOffset = vec2(0.05, 0.02);
    const float kAberrationBoost = 1.6;
    vec2 cellG = voronoiCaustic(cp);
    vec2 cellR = voronoiCaustic(cp + kAberrationOffset);
    vec2 cellB = voronoiCaustic(cp - kAberrationOffset);
    float caustic = pow(1.0 - smoothstep(0.0, 0.2, cellG.y - cellG.x), 1.5);
    float causticR = pow(1.0 - smoothstep(0.0, 0.2, cellR.y - cellR.x), 1.5);
    float causticB = pow(1.0 - smoothstep(0.0, 0.2, cellB.y - cellB.x), 1.5);
    // Clamped to >= 0: near a sharp seam corner the boosted deviation could in principle push a
    // channel negative, which would subtract from `color` instead of tinting it - a dark halo
    // where a bright fringe was intended.
    vec3 causticRGB = vec3(
        max(caustic + (causticR - caustic) * kAberrationBoost, 0.0),
        caustic,
        max(caustic + (causticB - caustic) * kAberrationBoost, 0.0)
    );

    // Layered back in alongside the Voronoi net (not replacing it): the original two-sine
    // interference ripple this project used before switching to Voronoi. Real caustics are
    // rarely just one clean pattern - overlapping wave interference at a different scale on top
    // of the light-net seams reads as richer/more turbulent water. Reuses the same `p`/`t` as
    // the Voronoi lookup above, just processed differently, so both patterns share one
    // coordinate/time frame instead of drifting relative to each other. Weighted at 0.7 (not
    // 1.0) so it reads as a secondary texture riding on top of the net, not a second
    // equally-dominant pattern competing with it.
    float c1 = sin(p.x * 1.7 + t + sin(p.y * 2.3 - t * 0.7));
    float c2 = sin(p.y * 1.9 - t * 1.3 + sin(p.x * 2.1 + t * 0.5));
    float sineCaustic = smoothstep(0.55, 1.0, c1 * c2) * 0.7;

    vec3 combinedCaustic = causticRGB + vec3(sineCaustic);
    color += shallowColor * combinedCaustic * causticStrength * (0.4 + raysMask * 0.8);

    fragColor = vec4(color, 1.0);
}
