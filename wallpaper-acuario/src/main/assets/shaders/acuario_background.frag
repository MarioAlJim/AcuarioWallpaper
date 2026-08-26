#version 300 es
precision mediump float;

in vec2 vUv; // 0..1, y=0 at the bottom of the screen, y=1 at the top

uniform highp float uTime;
uniform int uTheme; // 0 = Acuario (contained, warmer light), 1 = Mar abierto (deeper, colder light)
uniform float uAspectRatio;
// This frame's eased home-screen swipe position, re-centered to [-0.5, 0.5] (0 = no swipe) -
// see AcuarioRenderer's parallaxRaw/foregroundParallax field doc.
uniform float uParallaxOffset;
uniform float uDayNight; // 0.0 = full night, 1.0 = full day

out vec4 fragColor;

// Soft, wide-falloff ellipse mask for a distant, blurred silhouette - the same shape as the
// ellipseAlpha() masks the creature shaders use, but with a much larger `softness` so it reads
// as a vague, out-of-focus shape far at the back of the tank rather than a defined object.
float distantBlobAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    // Clamp the softness width so that the blur isn't so wide that the silhouette
    // washes out completely. Sized to guarantee that the center of the blob reaches
    // full opacity (1.0) instead of being stuck in the middle of a too-wide transition.
    w = min(w, 0.80);
    return smoothstep(w, -w, d);
}

// Deterministic pseudo-random 2D hash, used to place one "feature point" per grid cell for the
// Voronoi caustic network below. Fast, sine-free 2D hash based on Dave Hoskins.
//
// highp throughout (parameter, return type, and every local) despite this file's default
// `precision mediump float` - see turtle.frag's identical hash2()/voronoi() for the full
// explanation: GLSL ES only guarantees mediump as AT LEAST ~16-bit half-float, some real mobile
// GPUs implement it as exactly that, and this hash's fract()-then-(+33.33)-then-fract() chain
// needs more mantissa than that to avoid nearby inputs collapsing to the same output - visible
// here as the caustic mesh degrading from an irregular light-net into a coarse, regular-looking
// grid on whichever device's GPU implements mediump that narrowly.
highp vec2 hash2(highp vec2 p) {
    highp vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Cellular/Voronoi lookup at `p`: returns (F1, F2), the distances to the nearest and
// second-nearest feature points. Optimized to use squared distances inside the loop to avoid
// 9 square root operations, only applying sqrt on the final distances. highp for the same
// reason as hash2() above.
highp vec2 voronoiCaustic(highp vec2 p) {
    highp vec2 ip = floor(p);
    highp vec2 fp = fract(p);

    highp float f1Sq = 64.0;
    highp float f2Sq = 64.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            highp vec2 neighbor = vec2(float(x), float(y));
            highp vec2 point = hash2(ip + neighbor);
            highp vec2 diff = neighbor + point - fp;
            highp float distSq = dot(diff, diff);
            if (distSq < f1Sq) {
                f2Sq = f1Sq;
                f1Sq = distSq;
            } else if (distSq < f2Sq) {
                f2Sq = distSq;
            }
        }
    }

    return vec2(sqrt(f1Sq), sqrt(f2Sq));
}

void main() {
    float y = vUv.y;

    vec3 deepColor;
    vec3 shallowColor;
    float causticStrength;
    float rayStrength;

    if (uTheme == 0) {
        // Turquesa: warmer greenish teal, light kept contained (like an artificial lamp)
        deepColor = vec3(0.012, 0.095, 0.130);
        shallowColor = vec3(0.10, 0.44, 0.40);
        causticStrength = 0.06;
        // Bumped further still (was 0.10, then 0.20) - even doubled, the rays stayed too subtle
        // against the old brighter/flatter gradient. Now paired with a properly-sharpened `ray`
        // (see below, the previous "sharpening" via pow() actually dimmed it) and a darker base
        // gradient for the rays to stand out against.
        rayStrength = 0.32;
    } else if (uTheme == 1) {
        // Azul Profundo: colder, deeper blue with stronger sunlight shafts
        deepColor = vec3(0.010, 0.045, 0.130);
        shallowColor = vec3(0.05, 0.34, 0.55);
        causticStrength = 0.10;
        rayStrength = 0.60;
    } else if (uTheme == 2) {
        // Atardecer Violeta: warm pink/orange rays fading to dark violet at depth
        deepColor = vec3(0.08, 0.04, 0.15);
        shallowColor = vec3(0.70, 0.30, 0.40);
        causticStrength = 0.08;
        rayStrength = 0.50;
    } else if (uTheme == 3) {
        // Fosa Abisal: near black abyss, faint dark purple rays at top
        deepColor = vec3(0.01, 0.01, 0.04);
        shallowColor = vec3(0.15, 0.05, 0.25);
        causticStrength = 0.04;
        rayStrength = 0.24;
    } else {
        // Arrecife Coral: bright tropical cyan/turquoise water with high visibility
        deepColor = vec3(0.02, 0.08, 0.18);
        shallowColor = vec3(0.05, 0.65, 0.60);
        causticStrength = 0.09;
        rayStrength = 0.54;
    }

    // Base vertical gradient: deep/dark at the bottom, brighter near the "surface" at top.
    // Exponent raised from the original 1.4 to 2.0 so only the region right near the top
    // actually approaches shallowColor's full brightness - the rest of the tank reads notably
    // darker/moodier, which also gives the god rays below somewhere dark to visibly stand out
    // against instead of blending into an already-bright base.
    vec3 baseColor = mix(deepColor, shallowColor, pow(y, 2.0));
    // Apply day/night cycle: darken the background at night with a colder, deep-blue tint
    vec3 color = mix(baseColor * vec3(0.25, 0.30, 0.45), baseColor, uDayNight);

    // Parallax layer: very blurry, darkened silhouettes of rocks/kelp/a distant animal shape
    // sitting far at the back of the tank, well behind everything else drawn (the creatures,
    // vegetation and bubbles are all rendered afterward, in front of this). uParallaxOffset
    // shifts them by only kDistantParallaxShift of the swipe - a small fraction of how far the
    // foreground creatures/plants/bubbles shift for that same swipe (see AcuarioRenderer's
    // foregroundParallax) - the classic parallax cue that this layer sits much farther away,
    // making the tank read as far bigger than the single flat plane it actually is.
    const float kDistantParallaxShift = 0.05;
    float bx = vUv.x * uAspectRatio - uParallaxOffset * kDistantParallaxShift;
    vec2 bp = vec2(bx, y);

    float rock1 = distantBlobAlpha(bp, vec2(uAspectRatio * 0.15, 0.06), vec2(0.22, 0.10), 0.18);
    float rock2 = distantBlobAlpha(bp, vec2(uAspectRatio * 0.55, 0.03), vec2(0.30, 0.09), 0.20);
    float rock3 = distantBlobAlpha(bp, vec2(uAspectRatio * 0.88, 0.05), vec2(0.18, 0.09), 0.16);
    float kelpBlur1 = distantBlobAlpha(bp, vec2(uAspectRatio * 0.32, 0.22), vec2(0.09, 0.26), 0.14);
    float kelpBlur2 = distantBlobAlpha(bp, vec2(uAspectRatio * 0.74, 0.28), vec2(0.07, 0.30), 0.14);

    // A single vague animal shape drifting slowly and endlessly across the far background,
    // wrapping around once it exits either side - not tied to any real Fish/Turtle/Manta
    // instance, just a hint of distant life rather than a defined creature.
    float animalSpan = uAspectRatio + 0.6;
    float animalX = mod(uTime * 0.015 + 0.2, 1.0) * animalSpan - 0.3;
    float animal = distantBlobAlpha(bp, vec2(animalX, 0.42), vec2(0.15, 0.05), 0.10);

    float distantSilhouette = max(max(max(rock1, rock2), max(rock3, kelpBlur1)), max(kelpBlur2, animal));
    // Darkened rather than colored - a silhouette, not a distinctly-colored object.
    // Boosted the mix strength (was 0.55, now 0.70) and target color darkness (was deepColor * 0.55,
    // now deepColor * 0.35) so the distant shapes read as clearly visible, out-of-focus silhouettes
    // rather than fading to near-invisible under the overlaying god rays and caustics.
    color = mix(color, deepColor * 0.35, distantSilhouette * 0.70);

    // God rays: distinct, soft-edged diagonal light-shaft LINES fanning down from the surface,
    // strongest near the top and fading out with depth.
    //
    // Averaging several sine waves of different frequencies (the previous approach) creates
    // interference - a mottled, low-contrast noise texture, not visible lines, since the peaks
    // of one wave fall in the troughs of another about as often as they reinforce each other.
    // The actual "line" comes from pow(max(0.0, sin(phase)), N): sin's own hump is already a
    // smooth, soft-edged profile, and raising it to a high power squeezes everything except a
    // narrow band right around the peak down toward 0 - carving one thin, blurred bright line
    // per period instead of a wide wash. Two such beam families (different slant/frequency/
    // speed) are combined with max() - not summed/averaged - so they read as two crossing sets
    // of light lines instead of blending back into noise.
    float raysMask = pow(y, 1.4);
    // highp: derived directly from uTime, which grows unboundedly for as long as the wallpaper
    // runs. uTime itself is already highp, but that alone doesn't protect this - assigning a
    // highp-derived expression into an (implicitly mediump, per this file's default precision)
    // local truncates it right there. Without highp here, this rediscovers the exact same
    // mediump-precision bug already fixed for Turtle.kt's swimPhase/turtle.frag's uSwimPhase,
    // just one level removed behind an already-correct-looking `uniform highp float uTime`.
    highp float rayPhase1 = vUv.x * uAspectRatio * 5.0 + y * 2.2 - uTime * 0.07;
    highp float rayPhase2 = vUv.x * uAspectRatio * 8.0 - y * 3.0 - uTime * 0.10;
    float beam1 = pow(max(0.0, sin(rayPhase1)), 6.0);
    float beam2 = pow(max(0.0, sin(rayPhase2)), 10.0);
    float ray = max(beam1, beam2 * 0.7);

    // Occasional sun flashes (only in open-water themes: 1, 2, 4) fanning from the top edge.
    // Multiplying different frequencies creates occasional spikes/pulses, power of 4 sharpens them.
    float flare = 0.0;
    if (uTheme == 1 || uTheme == 2 || uTheme == 4) {
        flare = pow(max(0.0, sin(uTime * 0.13) * sin(uTime * 0.21 + 1.5) * cos(uTime * 0.07)), 4.0);
    }

    // Boost god rays when the sun flares up, scaled by uDayNight (fades out at night)
    float activeRayStrength = rayStrength * (1.0 + flare * 1.5) * uDayNight;
    color += shallowColor * ray * raysMask * activeRayStrength;

    if (uTheme == 1 || uTheme == 2 || uTheme == 4) {
        // Bright warm sun flash concentrated at the top edge (warm pinkish/orange for sunset), scaled by uDayNight
        float flashMask = pow(y, 3.5);
        vec3 flashColor = (uTheme == 2) ? vec3(0.98, 0.70, 0.50) : vec3(0.95, 0.92, 0.82);
        color += flashColor * flare * flashMask * 0.40 * uDayNight;
    }

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
    // cellular lookup at a small position offset - since the pattern is ~0 everywhere except
    // right at a light-net seam, this only produces a visible red/blue fringe exactly where the
    // pattern itself has contrast, never as a flat screen-wide tint.
    //
    // voronoiCaustic() is the single most expensive thing in this shader (a 3x3 = 9-iteration
    // hashed search per call) and this ran it THREE times per fragment - once for green, and
    // once more each for red/blue at their own independent offsets. Real chromatic aberration
    // only needs one separation axis, though, so a single extra sample - mirrored around the
    // green (centered) sample to get red on one side and blue on the other, instead of two
    // independent offset samples - gives visually the same fringe for one less full Voronoi
    // evaluation per pixel (2 total instead of 3, over every pixel of a full-screen background
    // quad, every frame).
    //
    // The deviation from the green sample is boosted by kAberrationBoost independently of
    // causticStrength/kAberrationOffset below, so the fringe stays clearly visible even though
    // causticStrength dims the whole caustic layer - without this, turning the mesh down would
    // also (wrongly) fade the color separation to invisible right along with it.
    const vec2 kAberrationOffset = vec2(0.05, 0.02);
    const float kAberrationBoost = 1.6;
    vec2 cellG = voronoiCaustic(cp);
    vec2 cellShifted = voronoiCaustic(cp + kAberrationOffset);
    float caustic = pow(1.0 - smoothstep(0.0, 0.2, cellG.y - cellG.x), 1.5);
    float causticShifted = pow(1.0 - smoothstep(0.0, 0.2, cellShifted.y - cellShifted.x), 1.5);
    float aberrationDelta = (causticShifted - caustic) * kAberrationBoost;
    // Clamped to >= 0: near a sharp seam corner the boosted deviation could in principle push a
    // channel negative, which would subtract from `color` instead of tinting it - a dark halo
    // where a bright fringe was intended.
    vec3 causticRGB = vec3(
        max(caustic + aberrationDelta, 0.0),
        caustic,
        max(caustic - aberrationDelta, 0.0)
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
    // Boost caustics when the sun flares up, and fade them out completely at night
    float activeCausticStrength = causticStrength * (1.0 + flare * 1.2) * uDayNight;
    color += shallowColor * combinedCaustic * activeCausticStrength * (0.4 + raysMask * 0.8);

    // Dynamic vignette: darkens the screen edges - the bottom corners more than the rest, since
    // real underwater light falls off toward the substrate rather than symmetrically toward
    // every edge - to pull focus toward the center and read as more cinematic. "Dynamic" means
    // it breathes with the god rays instead of sitting at one fixed darkness: reuses raysMask
    // (this pixel's height in the water column) and activeRayStrength (this theme's ray
    // intensity, itself already boosted by the sun flare above) as a proxy for how much surface
    // light is actually reaching here right now - when it's high the vignette relaxes back
    // toward a normal, barely-there brightness; when it's low (dim theme, or just far from the
    // surface) the vignette digs in further. Reusing these existing signals - rather than a
    // fresh uniform - is what makes the vignette genuinely interact with the rays instead of
    // just coexisting with them independently.
    vec2 vc = vUv - 0.5;
    vc.x *= uAspectRatio;
    float vignetteDist = length(vc);
    // Tuned for the portrait-ish aspect ratios this wallpaper actually renders at (see the x/y
    // roaming bounds elsewhere, e.g. Fish.pickNewTarget) - a very wide landscape aspect would
    // push the horizontal extent past this radius sooner than intended.
    float vignetteShape = smoothstep(0.58, 0.16, vignetteDist);
    // Extra darkening concentrated at the bottom corners specifically: strongest where y is
    // small (near the substrate) AND x is far from center (an actual corner, not just the
    // bottom edge's midpoint).
    float bottomCornerBoost = (1.0 - smoothstep(0.0, 0.6, y)) * smoothstep(0.15, 0.55, abs(vUv.x - 0.5));
    vignetteShape *= (1.0 - bottomCornerBoost * 0.35);

    // Replaced the y-dependent raysMask in lightLevel with a global surface light level.
    // This allows the vignette at the bottom of the screen to dynamically relax and brighten
    // when a sun flare occurs, making the entire background respond to the rays.
    float globalLightLevel = clamp(activeRayStrength / 0.65, 0.0, 1.0);
    // Darkened further (was 0.55/0.92) for a moodier overall background, on top of the steeper
    // base gradient above - the corners now dig noticeably darker when little light reaches
    // them, and even the "well lit" floor stays a touch below full brightness.
    const float kVignetteFloorDim = 0.42; // corner brightness multiplier when little/no light reaches here
    const float kVignetteFloorLit = 0.85; // corner brightness multiplier when well lit - restores near-normal
    float vignetteFloor = mix(kVignetteFloorDim, kVignetteFloorLit, globalLightLevel);
    float vignette = mix(vignetteFloor, 1.0, vignetteShape);
    color *= vignette;

    fragColor = vec4(color, 1.0);
}
