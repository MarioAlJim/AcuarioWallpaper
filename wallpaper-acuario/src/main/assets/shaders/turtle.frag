#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uShellColor;
uniform vec3 uHeadColor;
uniform vec3 uFlipperColor;
uniform vec3 uSpotColor;
uniform float uDayNight;
// 0 = normal swimming pose, 1 = fully withdrawn into the shell (head/eye/flippers tucked away)
// - see Turtle.kt's "Startle response" doc and AcuarioRenderer's drawTurtles(), which feeds it
// straight from Turtle.retraction.
uniform float uRetraction;
// Per-palette bioluminescent tint (see TurtlePalette.glowColor) - each species glows its own
// color instead of every turtle sharing one fixed neon hue.
uniform vec3 uGlowColor;
// 0.0 = normal, 1.0 = "shiny gold", 2.0 = "shiny diamond" (see Turtle.kt's shinyType) - drives
// the premium halo/sheen finish below.
uniform float uShinyType;

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

// Ellipse-shaped "limb" (a capsule-like segment) hinged at `pivot`: at hinge angle `hingeAngle`
// (radians, added to the local rest direction `restAngle`), the limb extends `limbLength`
// outward from the pivot with half-width `halfWidth`, rotating like a real hinged joint - a
// shoulder swinging a flipper through an arc - instead of the whole shape just translating
// up/down. The pivot itself sits exactly on the limb's near edge, so it starts right at the
// joint and sweeps outward from there. (Parameter named `limbLength`, not `length`, so it
// doesn't shadow the builtin length() used below.)
float hingedLimbAlpha(vec2 p, vec2 pivot, float restAngle, float hingeAngle, float limbLength, float halfWidth, float softness) {
    float totalAngle = restAngle + hingeAngle;
    vec2 d = p - pivot;
    float c = cos(totalAngle);
    float s = sin(totalAngle);
    // Rotate d by -totalAngle (the inverse of the limb's own rotation) so the limb lies along
    // local +x in this frame regardless of its current hinge angle.
    vec2 local = vec2(d.x * c + d.y * s, -d.x * s + d.y * c);
    vec2 q = (local - vec2(limbLength * 0.5, 0.0)) / vec2(limbLength * 0.5, halfWidth);
    float dist = length(q) - 1.0;
    float w = softness / min(limbLength * 0.5, halfWidth);
    return smoothstep(w, -w, dist);
}

// Deterministic pseudo-random 2D hash, used below to place one "feature point" per grid cell
// for the Voronoi scute pattern (same cell coordinate always yields the same point/shade, so
// the plate layout doesn't swim as the turtle moves). Fast, sine-free 2D hash based on Dave Hoskins.
//
// highp throughout (parameter, return type, and every local) despite this file's default
// `precision mediump float` - GLSL ES only guarantees mediump as AT LEAST ~16-bit half-float,
// and plenty of real mobile GPUs implement it as exactly that. This hash's fract()-then-
// (+33.33)-then-fract() chain needs many bits of mantissa to keep nearby inputs from collapsing
// to the same output; at 16-bit half-float precision it visibly does exactly that, and the
// intended irregular Voronoi rhombi degrade into a coarse, regular-looking grid on whichever
// device's GPU actually implements mediump that narrowly (this is the same root cause as the
// mediump-precision bug already fixed for swimPhase/uSwimPhase elsewhere - too little mantissa
// for what's being computed - just showing up as a spatial hash artifact here instead of a
// stalled time-based animation).
highp vec2 hash2(highp vec2 p) {
    highp vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Cellular/Voronoi lookup at `p`: returns (F1, F2, cellShade, cellSpot) where F1/F2 are the
// distances to the nearest and second-nearest feature points, and cellShade/cellSpot are two
// independent, stable pseudo-random values in [0, 1) tied to the WINNING cell. Optimized to
// use squared distances in the inner loop to save 9 square root operations, only taking the sqrt
// at the very end. highp for the same reason as hash2() above - it calls hash2() in its inner
// loop and needs to preserve that same precision through the distance comparisons.
highp vec4 voronoi(highp vec2 p) {
    highp vec2 ip = floor(p);
    highp vec2 fp = fract(p);

    highp float f1Sq = 64.0;
    highp float f2Sq = 64.0;
    highp vec2 f1Cell = ip;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            highp vec2 neighbor = vec2(float(x), float(y));
            highp vec2 point = hash2(ip + neighbor);
            highp vec2 diff = neighbor + point - fp;
            highp float distSq = dot(diff, diff);
            if (distSq < f1Sq) {
                f2Sq = f1Sq;
                f1Sq = distSq;
                f1Cell = ip + neighbor;
            } else if (distSq < f2Sq) {
                f2Sq = distSq;
            }
        }
    }

    highp vec2 cellRandom = hash2(f1Cell);
    return vec4(sqrt(f1Sq), sqrt(f2Sq), cellRandom.x, cellRandom.y);
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

    // Front flippers hinge (rotate) around a shoulder pivot sitting right above the shell's own
    // surface, starting CLOSED - folded flush against the body, pointing back toward the TAIL
    // (not the head) - and swinging open up to 90% of a full 90-degree spread before returning
    // to closed. Both flippers open in mirror-symmetric sync (top swings up, bottom swings down
    // by the same amount) - "front flippers stroke together in a wide sweep" - driven by
    // sin(uSwimPhase) mapped to a 0..1 easing so the fastest mid-swing point (where openness is
    // CLOSING at peak speed - the actual power stroke) lands at swimPhase == PI, the exact
    // instant Turtle.kt's consumePowerStrokeEvent() fires. Back flippers keep their smaller,
    // phase-delayed translation, like a real sea turtle "flying" through the water.
    float frontOpenness = (sin(uSwimPhase) * 0.5 + 0.5) * 0.9; // 0 (closed) .. 0.9 (90% open)
    const float kPi = 3.14159265;
    const float kFrontRestAngle = kPi; // 180 deg: closed pose points toward the tail (-x)
    const float kFrontMaxOpenAngle = 1.5708; // 90 deg: "fully open" reference, capped at 90% of it
    // Pivot sits above the shell's own highest point (0.40 at x=0) rather than right at its
    // surface: since "closed" now points tail-ward, the limb sweeps back across the shell's
    // tallest region on its way there, so it needs the extra clearance to stay outside the
    // shell for its whole length (a pivot merely flush with the surface, as when "closed" only
    // ever pointed forward toward the head, would dip back inside it here).
    const vec2 kFrontTopPivot = vec2(0.15, 0.46);
    const vec2 kFrontBotPivot = vec2(0.15, -0.46);
    const float kFrontLimbLength = 0.506; // 0.46 + 10%
    const float kFrontHalfWidth = 0.15;
    float frontOpenAngle = frontOpenness * kFrontMaxOpenAngle;

    float backFlap = sin(uSwimPhase - 1.0) * 0.08;

    // Startle retraction: pulls the pivot/center of every "limb" part inward until its whole
    // footprint sits under the shell's own silhouette, so the shell (drawn last, over these)
    // fully hides it - no extra masking needed, just moving the shapes underneath it. The front
    // flippers also stop opening (frontOpenAngle -> 0, folded flush at kFrontRestAngle) instead
    // of continuing to flap while shrinking into place.
    vec2 frontTopPivot = mix(kFrontTopPivot, vec2(0.05, 0.20), uRetraction);
    vec2 frontBotPivot = mix(kFrontBotPivot, vec2(0.05, -0.20), uRetraction);
    float frontLimbLength = mix(kFrontLimbLength, 0.20, uRetraction);
    float frontHalfWidth = mix(kFrontHalfWidth, 0.08, uRetraction);
    frontOpenAngle *= (1.0 - uRetraction);
    vec2 headCenter = mix(vec2(0.75, 0.05), vec2(0.30, 0.03), uRetraction);
    vec2 headRadii = mix(vec2(0.22, 0.22), vec2(0.12, 0.12), uRetraction);
    vec2 backTopCenter = mix(vec2(-0.38, 0.34 + backFlap), vec2(-0.15, 0.15), uRetraction);
    vec2 backBotCenter = mix(vec2(-0.38, -0.34 - backFlap), vec2(-0.15, -0.15), uRetraction);
    vec2 backRadii = mix(vec2(0.24, 0.11), vec2(0.10, 0.05), uRetraction);
    vec2 eyeCenter = vec2(0.82, 0.10) + (headCenter - vec2(0.75, 0.05));

    float aShell = ellipseAlpha(p, vec2(0.0, 0.0), vec2(0.60, 0.40), 0.025);
    float aHead = ellipseAlpha(p, headCenter, headRadii, 0.02);
    float aTail = ellipseAlpha(p, vec2(-0.72, 0.0), vec2(0.14, 0.08), 0.02);
    // Both start at kFrontRestAngle (tail-ward); only the hinge/open angle's sign differs so the
    // top opens upward and the bottom opens downward from that same closed pose.
    float aFlipperTopFront = hingedLimbAlpha(p, frontTopPivot, kFrontRestAngle, -frontOpenAngle, frontLimbLength, frontHalfWidth, 0.02);
    float aFlipperBotFront = hingedLimbAlpha(p, frontBotPivot, kFrontRestAngle, frontOpenAngle, frontLimbLength, frontHalfWidth, 0.02);
    float aFlipperTopBack = ellipseAlpha(p, backTopCenter, backRadii, 0.02);
    float aFlipperBotBack = ellipseAlpha(p, backBotCenter, backRadii, 0.02);
    // Fades out on top of moving inward with the head, so no sliver of it can poke past the
    // shell's soft edge right at full retraction.
    float aEye = ellipseAlpha(p, eyeCenter, vec2(0.035, 0.035), 0.01) * (1.0 - smoothstep(0.7, 0.95, uRetraction));

    float flippersAlpha = max(max(aFlipperTopFront, aFlipperBotFront), max(aFlipperTopBack, aFlipperBotBack));
    float bodyAlpha = max(max(aShell, aHead), max(aTail, flippersAlpha));

    // Shiny halo - see fish.frag's identical block for the full explanation. Anchored to aShell
    // (this file's dominant silhouette part) via the same (p, center, radii) triple its own
    // ellipseAlpha call above already uses.
    float haloAlpha = 0.0;
    if (uShinyType > 0.5) {
        vec2 qHalo = (p - vec2(0.0, 0.0)) / vec2(0.60, 0.40);
        float dEdge = length(qHalo) - 1.0;

        float isDiamond = step(1.5, uShinyType);
        float haloSigma = mix(0.24, 0.15, isDiamond);

        float breatheGold = 0.80 + 0.20 * sin(uSwimPhase);
        float breatheDiamond = pow(0.5 + 0.5 * sin(uSwimPhase * 3.0), 2.0);
        float breathe = mix(breatheGold, breatheDiamond, isDiamond);

        float halo = exp(-(dEdge * dEdge) / (haloSigma * haloSigma));
        halo *= mix(0.12, 1.0, smoothstep(-0.06, 0.02, dEdge));
        haloAlpha = halo * breathe;
    }

    if (bodyAlpha <= 0.0 && haloAlpha <= 0.004) {
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
    
    // Bioluminescent glow on shell grooves at night. Ramped through
    // smoothstep(0.0, kNightGlowEdge, uDayNight) rather than a plain (1.0 - uDayNight) - see
    // fish.frag's identical fix for why: the raw linear version made the glow already partway
    // visible as soon as the sun started dipping, well before the background actually looked
    // dark. This keeps it at 0 through day/dusk/dawn and only fades it in once it's genuinely
    // dark, in sync with the background.
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float shellGlowStrength = nightGlow * 1.25;
    shellColor += uGlowColor * groove * shellGlowStrength;

    // The shell is drawn in front of the flippers/head (not the other way around) - it's the
    // carapace, limbs tuck under its edge, not over it. The front-flipper hinge above places its
    // pivot clear of the shell's boundary specifically so this ordering doesn't clip a chunk out
    // of it on every stroke the way the old translate-based motion did; the back flippers still
    // overlap the shell's edge somewhat through their cycle (unchanged from before this hinge
    // pass - only the front flippers were in scope here).
    color = mix(color, shellColor, aShell);
    color = mix(color, eyeColor, aEye);

    // Shiny sheen - see fish.frag's identical block for the full explanation.
    if (uShinyType > 0.5) {
        float isDiamond = step(1.5, uShinyType);
        float eyeMask = 1.0 - aEye;

        const vec2 kSweepDir = vec2(0.8, 0.6);
        float sweepAxis = dot(p, kSweepDir);
        const float kTwoPiSheen = 6.28318530718;
        float sweepSpeedMul = mix(1.0, 2.0, isDiamond);
        float sweepT = fract(uSwimPhase * sweepSpeedMul / kTwoPiSheen);
        float sweepCenter = mix(-1.6, 1.6, sweepT);
        float sweepDist = sweepAxis - sweepCenter;
        float sweepWidth = mix(0.55, 0.22, isDiamond);
        float sweepCore = exp(-(sweepDist * sweepDist) / (sweepWidth * sweepWidth));
        float sweepBand = pow(sweepCore, mix(1.0, 2.2, isDiamond));

        const vec3 kGoldSheen = vec3(1.00, 0.88, 0.58);
        const vec3 kDiamondSheen = vec3(0.55, 0.85, 1.00);
        vec3 sweepColor = mix(kGoldSheen, kDiamondSheen, isDiamond);
        float sweepIntensity = mix(0.55, 0.40, isDiamond);

        color = mix(color, sweepColor, sweepBand * sweepIntensity * bodyAlpha * eyeMask);

        // Named sheenCell/sheenCellFrac (not cell/cellFrac) - this file already has an outer
        // `vec4 cell = voronoi(...)` for the shell scute pattern; reusing that name here would
        // shadow it (legal but confusing) rather than colliding, so this avoids the ambiguity.
        highp vec2 sheenCell = floor(p * 9.0);
        highp vec2 sheenCellFrac = fract(p * 9.0);
        highp vec3 hash3 = fract(sin(vec3(
            sheenCell.x * 127.1 + sheenCell.y * 311.7,
            sheenCell.x * 269.5 + sheenCell.y * 183.3,
            sheenCell.x * 419.2 + sheenCell.y * 371.9)) * 43758.5453);
        vec2 glintPos = hash3.xy;
        float glintDist = length(sheenCellFrac - glintPos) * 3.0;
        float glintMask = smoothstep(1.0, 0.0, glintDist);
        float freqInt = floor(hash3.z * 8.0) + 5.0;
        float twinkle = pow(max(0.0, sin(uSwimPhase * freqInt + hash3.z * 6.283)), 10.0);
        float facetHue = fract(hash3.x * 7.13 + hash3.y * 13.71 + hash3.z * 3.29);
        vec3 facetColor = 0.5 + 0.5 * cos(6.28318 * (facetHue + vec3(0.0, 0.33, 0.67)));
        float facetStrength = glintMask * twinkle * isDiamond * bodyAlpha * eyeMask;

        color = mix(color, facetColor, facetStrength);
    }

    // Rim lighting on the head and flippers only (per the design ask - the shell already gets
    // its own "domed plate" shading above): a fine sunlit highlight along their upper edges,
    // helping the silhouette separate from a dark background. Masked by (1 - aShell) since the
    // shell is drawn last and can still cover part of the head/flippers where they tuck under
    // its edge - without that mask this would incorrectly glow through the shell there.
    float headRim = rimLight(p, headCenter, headRadii, aHead);
    float frontTopTotalAngle = kFrontRestAngle - frontOpenAngle;
    float frontBotTotalAngle = kFrontRestAngle + frontOpenAngle;
    vec2 frontTopCenter = frontTopPivot + vec2(cos(frontTopTotalAngle), sin(frontTopTotalAngle)) * (frontLimbLength * 0.5);
    vec2 frontBotCenter = frontBotPivot + vec2(cos(frontBotTotalAngle), sin(frontBotTotalAngle)) * (frontLimbLength * 0.5);
    float flipperTopFrontRim = rimLight(p, frontTopCenter, vec2(frontLimbLength * 0.5, frontHalfWidth), aFlipperTopFront);
    float flipperBotFrontRim = rimLight(p, frontBotCenter, vec2(frontLimbLength * 0.5, frontHalfWidth), aFlipperBotFront);
    float flipperTopBackRim = rimLight(p, backTopCenter, backRadii, aFlipperTopBack);
    float flipperBotBackRim = rimLight(p, backBotCenter, backRadii, aFlipperBotBack);
    float flipperRim = max(max(flipperTopFrontRim, flipperBotFrontRim), max(flipperTopBackRim, flipperBotBackRim));
    float rimAmount = max(headRim, flipperRim) * (1.0 - aShell);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.90);
    const float kRimIntensity = 0.55;
    color += kRimColor * rimAmount * kRimIntensity;

    float alpha = max(bodyAlpha, aEye);

    // Shiny halo compositing - see fish.frag's identical block for the full explanation.
    if (uShinyType > 0.5) {
        const vec3 kGoldHalo = vec3(1.00, 0.80, 0.35);
        const vec3 kDiamondHalo = vec3(0.45, 0.80, 1.00);
        vec3 haloColor = mix(kGoldHalo, kDiamondHalo, step(1.5, uShinyType));

        float outside = 1.0 - bodyAlpha;
        color = mix(color, haloColor, haloAlpha * outside);
        color += haloColor * haloAlpha * 0.65;
        alpha = max(alpha, haloAlpha * outside * 0.65);
    }

    fragColor = vec4(color, alpha);
}
