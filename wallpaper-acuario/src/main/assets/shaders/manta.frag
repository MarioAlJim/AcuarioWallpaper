#version 300 es
precision mediump float;

in vec2 vUV;

uniform float uSwimPhase;
uniform vec3 uBodyColor;
uniform vec3 uWingColor;
uniform vec3 uTailColor;
uniform vec3 uMarkingColor;

out vec4 fragColor;

// Soft "inside an ellipse" mask - see fish.frag/turtle.frag for the identical helper.
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Rim-light contribution confined to the upper arc - see fish.frag/turtle.frag.
float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
}

void main() {
    // Center UV to [-1, 1] space. Manta faces right (nose toward +x, tail whip toward -x), same
    // convention as fish/turtle. The shared quad's V axis runs top(0)->bottom(1) on screen,
    // opposite of this shader's local +y-is-up convention, so the vertical component is flipped
    // here - see fish.frag's identical comment.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Wing undulation: rather than a fish's sharp tail wag, a manta ripples its whole wing
    // front-to-back while gliding. flapWave's phase depends on p.x so the ripple visibly travels
    // from nose to tail, and multiplying by p.y (not a fixed amplitude) means the displacement
    // grows with distance from the spine - the spine barely moves while the wingtips sweep
    // through the widest excursion, same as a real ray's undulating pectoral fin.
    //
    // kWingFlapAmplitude and the wings' own y-radius below are chosen together: this warp
    // rescales p.y by a factor of (1 +/- kWingFlapAmplitude), so at full spread (factor
    // 1 - kWingFlapAmplitude) the wingtip's *true* silhouette edge would sit at
    // wingRadiusY / (1 - kWingFlapAmplitude) in unwarped space. If that exceeds 1.0 - the quad's
    // own hard edge - the tip gets sliced off flat by the geometry boundary instead of tapering
    // naturally, which is exactly what a too-large radius/amplitude combination looked like
    // before this comment (wings visibly clipped by a flat top/bottom edge mid-flap). Keeping
    // wingRadiusY / (1 - kWingFlapAmplitude) comfortably under 1.0 leaves room for the whole
    // flap cycle to render without ever touching the quad's edge.
    const float kWingFlapAmplitude = 0.16;
    const float kWingRadiusY = 0.80; // 0.80 / (1 - 0.16) = 0.952 - safely inside the quad's [-1, 1]
    float flapWave = sin(uSwimPhase - p.x * 1.8);
    vec2 pWarped = vec2(p.x, p.y + flapWave * p.y * kWingFlapAmplitude);

    // Body: a wide diamond-ish wingspan (much wider in y than long in x) plus a small pointed
    // nose bump at the front. Approximated with ellipses, same trick as the rest of the cast.
    float aWings = ellipseAlpha(pWarped, vec2(0.02, 0.0), vec2(0.44, kWingRadiusY), 0.035);
    float aNose = ellipseAlpha(pWarped, vec2(0.42, 0.0), vec2(0.16, 0.14), 0.03);
    // Long, thin whip tail trailing behind.
    float aTail = ellipseAlpha(pWarped, vec2(-0.74, 0.0), vec2(0.32, 0.018), 0.015);

    float aBody = max(aWings, aNose);

    // A pair of small eyes flanking the nose, on the top and bottom wingtip-ward sides (this is
    // drawn top-down/ventral rather than in profile, so the eyes sit left/right of the head -
    // i.e. at +-y here - rather than one eye on a visible flank).
    float aEyeTop = ellipseAlpha(pWarped, vec2(0.30, 0.12), vec2(0.028, 0.028), 0.012);
    float aEyeBot = ellipseAlpha(pWarped, vec2(0.30, -0.12), vec2(0.028, 0.028), 0.012);
    float aEye = max(aEyeTop, aEyeBot);

    float fishAlpha = max(max(aBody, aTail), aEye);
    if (fishAlpha <= 0.0) {
        discard;
    }

    // Color mixing pipeline
    vec3 color = uWingColor;
    color = mix(color, uBodyColor, aBody);
    color = mix(color, uTailColor, aTail);

    // Pale shoulder chevron patches near the head (reef/oceanic manta rays' signature marking),
    // plus a few scattered spots further back across the wings - works as a stand-in for the
    // spotted eagle ray's rings too, and reads as subtle mottling on the plainer species.
    float shoulderTop = ellipseAlpha(pWarped, vec2(0.12, 0.32), vec2(0.18, 0.22), 0.05);
    float shoulderBot = ellipseAlpha(pWarped, vec2(0.12, -0.32), vec2(0.18, 0.22), 0.05);
    float spot1 = ellipseAlpha(pWarped, vec2(-0.18, 0.55), vec2(0.06, 0.06), 0.02);
    float spot2 = ellipseAlpha(pWarped, vec2(-0.10, -0.60), vec2(0.05, 0.05), 0.02);
    float spot3 = ellipseAlpha(pWarped, vec2(-0.30, 0.30), vec2(0.045, 0.045), 0.02);
    float markingsAlpha = max(max(shoulderTop, shoulderBot), max(max(spot1, spot2), spot3)) * aWings;
    color = mix(color, uMarkingColor, markingsAlpha);

    // Eye colors (simple dark dot, no separate pupil - mantas' eyes read small at this scale).
    vec3 eyeColor = vec3(0.03, 0.03, 0.03);
    color = mix(color, eyeColor, aEye);

    // Soft rim lighting along the upper edges, separating the silhouette from the background.
    float wingsRim = rimLight(pWarped, vec2(0.02, 0.0), vec2(0.44, kWingRadiusY), aWings);
    float tailRim = rimLight(pWarped, vec2(-0.74, 0.0), vec2(0.32, 0.018), aTail);
    float rimAmount = max(wingsRim, tailRim) * (1.0 - aEye);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.92);
    const float kRimIntensity = 0.40;
    color += kRimColor * rimAmount * kRimIntensity;

    fragColor = vec4(color, fishAlpha);
}
