#version 300 es
precision mediump float;

in vec2 vUV;

// Drives the tiny pectoral/dorsal fin flutter - advances at a near-constant high frequency
// (see Seahorse.kt's kFinFlutterSpeed), independent of how fast the body is actually drifting,
// since a seahorse's fins beat rapidly just to hold position rather than stroking in proportion
// to travel speed the way a fish's tail does.
uniform float uFinPhase;
uniform vec3 uBodyColor;
uniform vec3 uFinColor;
uniform vec3 uSnoutColor;
uniform vec3 uPatternColor;
uniform float uDayNight;
// Per-palette bioluminescent tint (see SeahorsePalette.glowColor) - see fish.frag's identical
// comment.
uniform vec3 uGlowColor;

out vec4 fragColor;

// Soft "inside an ellipse" mask - see fish.frag for the identical helper.
float ellipseAlpha(vec2 p, vec2 center, vec2 radii, float softness) {
    vec2 q = (p - center) / radii;
    float d = length(q) - 1.0;
    float w = softness / min(radii.x, radii.y);
    return smoothstep(w, -w, d);
}

// Rim-light contribution confined to the upper arc - see fish.frag.
float rimLight(vec2 p, vec2 center, vec2 radii, float alpha) {
    vec2 q = (p - center) / radii;
    float dist = max(length(q), 0.0001);
    float upFacing = max(q.y / dist, 0.0);
    float edgeBand = 4.0 * alpha * (1.0 - alpha);
    return pow(edgeBand, 1.4) * pow(upFacing, 2.0);
}

void main() {
    // Center UV to [-1, 1] space. A seahorse hangs upright rather than swimming head-first, but
    // it still "faces" +x (snout/head toward +x, curled tail toward -x/-y) so the same
    // facingScale() mirror-flip used by every other creature works unmodified. The shared quad's
    // V axis runs top(0)->bottom(1) on screen, opposite of this shader's local +y-is-up
    // convention, so the vertical component is flipped here - see fish.frag's identical comment.
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // Upright body: a slender vertical ellipse, slightly S-curved by leaning the head/neck
    // forward - approximated by shifting the upper portion of the body toward +x.
    float leanShift = 0.10 * smoothstep(-0.1, 0.55, p.y);
    vec2 pBody = vec2(p.x - leanShift, p.y);
    float aBody = ellipseAlpha(pBody, vec2(0.0, 0.05), vec2(0.17, 0.42), 0.03);

    // Head + snout: a small head at the top of the neck with a long thin snout pointing forward
    // and slightly down, the seahorse's signature horse-like profile.
    vec2 headCenter = vec2(0.10, 0.62);
    float aHead = ellipseAlpha(p, headCenter, vec2(0.15, 0.16), 0.025);
    vec2 snoutCenter = vec2(0.34, 0.55);
    float aSnout = ellipseAlpha(p, snoutCenter, vec2(0.17, 0.055), 0.02);

    // Curled tail: a hooked spiral of shrinking segments curving from the base of the body back
    // underneath itself - the seahorse's other signature feature, used to grip kelp fronds.
    float aTail1 = ellipseAlpha(p, vec2(-0.02, -0.36), vec2(0.14, 0.15), 0.025);
    float aTail2 = ellipseAlpha(p, vec2(0.14, -0.53), vec2(0.115, 0.125), 0.02);
    float aTail3 = ellipseAlpha(p, vec2(0.16, -0.71), vec2(0.09, 0.10), 0.018);
    float aTail4 = ellipseAlpha(p, vec2(0.06, -0.84), vec2(0.065, 0.07), 0.015);
    float aTail = max(max(aTail1, aTail2), max(aTail3, aTail4));

    // Dorsal fin: a small fin on the back rippling at high frequency (a seahorse's main means of
    // propulsion, held nearly still relative to how fast it flutters).
    float dorsalFlutter = sin(uFinPhase * 1.15) * 0.05;
    vec2 dorsalCenter = vec2(-0.16 + dorsalFlutter, 0.05);
    float aDorsalFin = ellipseAlpha(p, dorsalCenter, vec2(0.075, 0.30), 0.02);

    // Pectoral fin: a tiny fin just behind the head, vibrating even faster than the dorsal fin.
    float pectoralFlutter = sin(uFinPhase * 1.6 + 1.3) * 0.045;
    vec2 pectoralCenter = vec2(0.08, 0.42 + pectoralFlutter);
    float aPectoralFin = ellipseAlpha(p, pectoralCenter, vec2(0.09, 0.05), 0.015);

    float aEye = ellipseAlpha(p, vec2(0.16, 0.66), vec2(0.028, 0.028), 0.012);
    float aPupil = ellipseAlpha(p, vec2(0.17, 0.66), vec2(0.015, 0.015), 0.008);

    float finsAlpha = max(aDorsalFin, aPectoralFin);
    float bodyGroupAlpha = max(max(aBody, aHead), max(aSnout, aTail));
    float seahorseAlpha = max(max(bodyGroupAlpha, finsAlpha), aEye);
    if (seahorseAlpha <= 0.0) {
        discard;
    }

    // Color mixing pipeline
    vec3 color = uFinColor;
    color = mix(color, uBodyColor, max(aBody, aTail));
    color = mix(color, uBodyColor, aHead);
    color = mix(color, uSnoutColor, aSnout);

    // Bony-plate rings around the body/tail, a real seahorse's segmented armor.
    float ring1 = smoothstep(0.05, 0.0, abs(p.y - 0.28));
    float ring2 = smoothstep(0.05, 0.0, abs(p.y - 0.02));
    float ring3 = smoothstep(0.05, 0.0, abs(p.y + 0.24));
    float ringsAlpha = max(max(ring1, ring2), ring3) * aBody;
    float tailRing1 = smoothstep(0.04, 0.0, abs(p.y + 0.44));
    float tailRing2 = smoothstep(0.04, 0.0, abs(p.y + 0.62));
    float tailRingsAlpha = max(tailRing1, tailRing2) * aTail;
    float patternAlpha = max(ringsAlpha, tailRingsAlpha);
    color = mix(color, uPatternColor, patternAlpha);

    // Bioluminescent glow on the body rings at night - see fish.frag's identical fix for why
    // this is ramped through smoothstep(0.0, kNightGlowEdge, uDayNight) rather than a plain
    // (1.0 - uDayNight).
    const float kNightGlowEdge = 0.25;
    float nightGlow = 1.0 - smoothstep(0.0, kNightGlowEdge, uDayNight);
    float glowStrength = nightGlow * 1.2;
    color += uGlowColor * patternAlpha * glowStrength;

    // Eye colors
    vec3 eyeRingColor = vec3(0.95, 0.95, 0.95);
    vec3 pupilColor = vec3(0.05, 0.05, 0.05);
    color = mix(color, eyeRingColor, aEye);
    color = mix(color, pupilColor, aPupil);

    // Soft rim lighting along the upper edges, separating the silhouette from the background.
    float bodyRim = rimLight(pBody, vec2(0.0, 0.05), vec2(0.17, 0.42), aBody);
    float dorsalRim = rimLight(p, dorsalCenter, vec2(0.075, 0.30), aDorsalFin);
    float rimAmount = max(bodyRim, dorsalRim) * (1.0 - aEye);

    const vec3 kRimColor = vec3(1.0, 0.98, 0.92);
    const float kRimIntensity = 0.40;
    color += kRimColor * rimAmount * kRimIntensity;

    float finalAlpha = max(seahorseAlpha, aPupil);
    fragColor = vec4(color, finalAlpha);
}
