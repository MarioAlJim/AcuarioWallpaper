#version 300 es
precision mediump float;

in vec2 vUV;

uniform highp float uTime;
uniform float uDayNight;       // 0.0 = night, 1.0 = day
uniform float uCrawlPhase;     // Drives rhythmic ambulacral crawl flex
uniform float uTouchReact;      // 0.0 = calm, 1.0 = startled/flexing touch response
uniform float uShinyType;       // 0 = normal, 1 = shiny gold, 2 = shiny diamond

uniform vec3 uBodyColor;
uniform vec3 uDetailColor;
uniform vec3 uSuctionColor;
uniform vec3 uGlowColor;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TWO_PI = 6.28318530718;
const float ANGLE_STEP = TWO_PI / 5.0; // 72 degrees per arm
const float HALF_STEP = ANGLE_STEP * 0.5; // 36 degrees

// 2D Hash function
highp float hash21(highp vec2 p) {
    highp vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Polynomial smooth minimum for organic blending of arms and central disc
float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

void main() {
    // Quad coordinates: centered at (0, 0), ranging [-1.0, 1.0]
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    float r = length(p);
    float a = atan(p.y, p.x); // Angle in [-PI, PI]

    // 5-fold polar symmetry fold
    // Shift angle so arm 0 points straight along +X
    float armId = floor((a + HALF_STEP) / ANGLE_STEP);
    float fa = a - armId * ANGLE_STEP; // in [-HALF_STEP, HALF_STEP]

    // Folded arm coordinate in local arm space
    vec2 pFold = vec2(cos(fa) * r, sin(fa) * r);

    // Subtle natural organic arm flex and startled wiggling
    float armFlexWave = sin(uCrawlPhase + armId * 1.2566) * 0.045 * smoothstep(0.12, 0.80, pFold.x);
    float touchWiggle = sin(uTime * 14.0 + armId * 1.8) * uTouchReact * 0.075 * smoothstep(0.10, 0.85, pFold.x);
    pFold.y -= (armFlexWave + touchWiggle);

    // Arm tapering profile:
    // Base width at disc ~ 0.26, tapers smoothly down to 0.048 near tip at x = 0.80
    float armLength = 0.78;
    float tArm = clamp(pFold.x / armLength, 0.0, 1.0);
    float halfWidth = mix(0.24, 0.046, smoothstep(0.0, 1.0, tArm));

    // Distance to tapered arm shape
    float dArmX = pFold.x - armLength;
    float dArmY = abs(pFold.y) - halfWidth;
    float dArm = max(pFold.x * -1.0, max(dArmX, dArmY));

    // Rounded tip cap
    float dTip = length(pFold - vec2(armLength, 0.0)) - 0.046;
    dArm = min(dArm, dTip);

    // Central circular body disc
    float dDisc = r - 0.23;

    // Smooth fusion of disc and 5 arms to form curved interradial webs
    float dStar = smin(dDisc, dArm, 0.12);

    // Soft antialiased body alpha
    float bodyAlpha = smoothstep(0.015, -0.015, dStar);
    if (bodyAlpha <= 0.001) {
        discard;
    }

    // --- GLASS SUCTION AND VENTRAL/DORSAL DETAILS ---
    // 1. Ambulacral radial groove running along arm centerline
    float grooveMask = smoothstep(0.022, 0.002, abs(pFold.y)) * smoothstep(0.08, 0.22, pFold.x) * smoothstep(armLength, armLength - 0.08, pFold.x);

    // 2. Pairs of suction tube feet pressed against the glass along each arm
    float suctionPads = 0.0;
    for (float sx = 0.18; sx <= 0.72; sx += 0.09) {
        float padYOffset = halfWidth * 0.48;
        vec2 padPos1 = vec2(sx, padYOffset);
        vec2 padPos2 = vec2(sx, -padYOffset);
        float dPad1 = length(pFold - padPos1) - 0.025;
        float dPad2 = length(pFold - padPos2) - 0.025;
        float aPad1 = smoothstep(0.008, -0.008, dPad1);
        float aPad2 = smoothstep(0.008, -0.008, dPad2);
        // Suction rim highlight
        float rim1 = smoothstep(0.025, 0.015, length(pFold - padPos1)) - smoothstep(0.015, 0.005, length(pFold - padPos1));
        float rim2 = smoothstep(0.025, 0.015, length(pFold - padPos2)) - smoothstep(0.015, 0.005, length(pFold - padPos2));
        suctionPads = max(suctionPads, max(aPad1, aPad2) * 0.7 + max(rim1, rim2) * 0.5);
    }

    // 3. Central mouth / disc ring visible against glass
    float dMouth = abs(r - 0.065) - 0.012;
    float mouthRing = smoothstep(0.008, -0.008, dMouth);

    // 4. Dermal spines / nodules along arm ridges
    float spineDots = 0.0;
    for (float sp = 0.16; sp <= 0.74; sp += 0.095) {
        float dSpine = length(pFold - vec2(sp, 0.0)) - 0.018;
        spineDots = max(spineDots, smoothstep(0.008, -0.008, dSpine));
    }
    // Pentagonal ring of spines on central disc
    float dPentSpine = length(vec2(pFold.x - 0.14, pFold.y)) - 0.020;
    float pentSpines = smoothstep(0.008, -0.008, dPentSpine);
    float tubercles = max(spineDots, pentSpines);

    // Dermal granular speckle texture across skin
    float grain = (hash21(floor(pFold * 85.0)) - 0.5) * 0.10;

    // --- COLOR COMPOSITING ---
    vec3 col = clamp(uBodyColor + grain, 0.0, 1.0);

    // Add darker groove depth
    col = mix(col, col * 0.65, grooveMask * 0.75);

    // Add suction cups against glass (translucent cream/pale contact pads)
    col = mix(col, uSuctionColor, suctionPads * 0.85);
    col = mix(col, col * 0.70, mouthRing * 0.80);

    // Add prominent tubercles / spines
    col = mix(col, uDetailColor, tubercles * 0.90);

    // Glass contact edge highlight (specular seal against front glass)
    float glassContactEdge = smoothstep(-0.06, -0.01, dStar) * smoothstep(0.005, -0.005, dStar);
    col += vec3(0.20, 0.25, 0.30) * glassContactEdge * 0.45;

    // Ambient day/night shading:
    // Starfish on the front glass receives good light, but adjusts subtly to night
    float ambient = 0.55 + 0.45 * uDayNight;
    col *= ambient;

    // Bioluminescent glow at night / darkness
    float bioPulse = 0.75 + 0.25 * sin(uTime * 3.0 + armId * 1.2566);
    float glowMap = max(tubercles * 0.85, suctionPads * 0.60) * bioPulse;
    // Arm tips glow brightly in darkness
    float tipGlow = smoothstep(0.70, 0.80, pFold.x) * bioPulse;
    glowMap = max(glowMap, tipGlow);
    vec3 nightGlow = uGlowColor * glowMap * (1.0 - uDayNight * 0.65);
    col += nightGlow;

    // Touch reaction shimmer pulse
    if (uTouchReact > 0.01) {
        float touchWave = sin(r * 18.0 - uTime * 12.0) * 0.5 + 0.5;
        col += uGlowColor * (touchWave * uTouchReact * 0.40);
    }

    // Shiny gold or diamond sparkles
    if (uShinyType > 0.0) {
        highp vec2 sparkUV = p * 30.0;
        float sparkHash = hash21(floor(sparkUV));
        float sparkle = pow(max(0.0, sin(uTime * 4.0 + sparkHash * 62.8)), 4.0);
        vec3 sparkColor = (uShinyType > 1.5) ? vec3(0.75, 0.95, 1.0) : vec3(1.0, 0.88, 0.35);
        col += sparkColor * (sparkle * 0.75);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), bodyAlpha);
}
