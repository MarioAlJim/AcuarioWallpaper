#version 300 es
precision mediump float;

in vec2 vUV;

uniform highp float uTime;
uniform float uLidOpen;      // 0.0 = fully closed, 1.0 = fully open
uniform float uGlowIntensity;// 0.0 = dark inside, 1.0 = radiant gold glow
uniform float uDayNight;     // 0.0 = night, 1.0 = day
uniform float uBeaconGlow;   // 0.0 = normal, >0.0 = special ready-to-restore pulsing beacon

uniform vec3 uWoodColor;
uniform vec3 uMetalColor;
uniform vec3 uGoldColor;
uniform vec3 uGemColor;

out vec4 fragColor;

// 2D Hash function
highp float hash21(highp vec2 p) {
    highp vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Rounded box SDF
float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    // Quad coordinates: p.x in [-1, 1], p.y in [-1, 1] (-1 is bottom, +1 is top of quad)
    vec2 p = vec2((vUV.x - 0.5) * 2.0, (0.5 - vUV.y) * 2.0);

    // --- CHEST BASE (Main Box Body) ---
    // Positioned in lower half: center at (0.0, -0.32), size (0.52, 0.28)
    vec2 boxCenter = vec2(0.0, -0.32);
    vec2 boxHalfSize = vec2(0.52, 0.28);
    float dBox = sdRoundedBox(p - boxCenter, boxHalfSize, 0.04);
    float aBox = smoothstep(0.01, -0.01, dBox);

    // Wood plank texture on box
    float woodGrain = sin((p.y - boxCenter.y) * 45.0) * 0.08 + (hash21(floor(vUV * vec2(120.0, 60.0))) - 0.5) * 0.06;
    vec3 baseWood = clamp(uWoodColor + woodGrain, 0.0, 1.0);

    // Metallic reinforcement bands (Left, Right, Middle)
    float bandWidth = 0.065;
    float bandL = smoothstep(bandWidth, bandWidth - 0.015, abs(p.x - (-0.38)));
    float bandR = smoothstep(bandWidth, bandWidth - 0.015, abs(p.x - 0.38));
    float bandM = smoothstep(bandWidth, bandWidth - 0.015, abs(p.x - 0.0));
    float metalBands = max(max(bandL, bandR), bandM) * aBox;

    // Metal lock plate in center
    float dLock = sdRoundedBox(p - (boxCenter + vec2(0.0, 0.06)), vec2(0.08, 0.08), 0.02);
    float aLock = smoothstep(0.01, -0.01, dLock);
    // Keyhole inside lock plate
    float dKeyhole = min(length(p - (boxCenter + vec2(0.0, 0.08))) - 0.02,
                         sdRoundedBox(p - (boxCenter + vec2(0.0, 0.04)), vec2(0.008, 0.03), 0.002));
    float aKeyhole = smoothstep(0.008, -0.008, dKeyhole) * aLock;

    // --- CHEST LID (Hinged Top) ---
    // Pivot line at top of box body: y = -0.04
    // Opening shifts/rotates the lid upwards
    float lidLift = uLidOpen * 0.38;
    float lidAngle = uLidOpen * 0.85; // Angle in radians
    
    // Transform coordinates for the opening lid
    vec2 lidP = p - vec2(0.0, -0.04 + lidLift);
    // Apply slight perspective tilt when opening
    lidP.y /= (1.0 + uLidOpen * 0.25);
    
    // Arched Lid shape
    vec2 lidCenter = vec2(0.0, 0.16);
    vec2 lidHalfSize = vec2(0.53, 0.18);
    float dLid = sdRoundedBox(lidP - lidCenter, lidHalfSize, 0.08);
    float aLid = smoothstep(0.01, -0.01, dLid);

    // Wood & metal texture for lid
    float lidWoodGrain = sin(lidP.x * 30.0) * 0.07 + (hash21(floor(lidP * 40.0)) - 0.5) * 0.05;
    vec3 lidWood = clamp(uWoodColor * 1.1 + lidWoodGrain, 0.0, 1.0);
    float lidMetalBands = max(max(smoothstep(bandWidth, bandWidth - 0.015, abs(lidP.x - (-0.38))),
                                  smoothstep(bandWidth, bandWidth - 0.015, abs(lidP.x - 0.38))),
                              smoothstep(bandWidth, bandWidth - 0.015, abs(lidP.x - 0.0))) * aLid;

    // --- TREASURE INSIDE & GOLDEN GLOW ---
    // Interior opening region between box top and lifted lid
    float interiorY = p.y - (-0.04);
    float interiorMask = smoothstep(-0.04, 0.02, p.y) * smoothstep(lidLift + 0.05, lidLift - 0.05, p.y) * smoothstep(0.48, 0.40, abs(p.x));
    interiorMask *= smoothstep(0.05, 0.30, uLidOpen);

    // Gold coins & sparkling jewels pattern inside
    highp vec2 coinUV = p * 45.0;
    float coinHash = hash21(floor(coinUV));
    float coinSparkle = sin(uTime * 5.0 + coinHash * 62.8) * 0.5 + 0.5;
    vec3 treasureContent = mix(uGoldColor, uGemColor, step(0.82, coinHash));
    treasureContent *= (0.7 + 0.6 * pow(coinSparkle, 2.0));

    // Radiant volumetric light beam escaping upward from open chest
    float beamX = smoothstep(0.60, 0.0, abs(p.x));
    float beamY = smoothstep(-0.10, 0.85, p.y);
    float beamGlow = beamX * beamY * uGlowIntensity * uLidOpen * (0.8 + 0.2 * sin(uTime * 3.5));
    vec3 beamColor = mix(uGoldColor, vec3(1.0, 0.95, 0.70), 0.5);

    // Special beacon aura when chest is ready to restore depleted fauna
    float beaconPulse = 0.5 + 0.5 * sin(uTime * 4.5);
    float dChest = min(dBox, dLid);
    float beaconAura = smoothstep(0.35, -0.05, dChest) * uBeaconGlow * (0.55 + 0.45 * beaconPulse);
    vec3 auraColor = mix(vec3(1.0, 0.85, 0.25), vec3(1.0, 0.98, 0.70), beaconPulse);

    // --- COMPOSITING ---
    float totalAlpha = max(max(aBox, aLid), max(beamGlow * 0.8, beaconAura * 0.75));
    if (totalAlpha <= 0.001) {
        discard;
    }

    // Base body color assembly
    vec3 bodyCol = baseWood;
    bodyCol = mix(bodyCol, uMetalColor, metalBands);
    bodyCol = mix(bodyCol, uMetalColor * 1.2, aLock);
    bodyCol = mix(bodyCol, vec3(0.05), aKeyhole);

    // Lid color assembly
    vec3 lidCol = lidWood;
    lidCol = mix(lidCol, uMetalColor, lidMetalBands);

    // Combine layers: Body -> Interior Treasure -> Lid on top -> Light Beam + Beacon Glow
    vec3 finalColor = bodyCol;
    finalColor = mix(finalColor, treasureContent, interiorMask);
    finalColor = mix(finalColor, lidCol, aLid);
    finalColor += beamColor * beamGlow * 0.65;
    finalColor += auraColor * beaconAura * 0.85;

    // Golden shimmer over the chest when ready
    if (aBox > 0.0 || aLid > 0.0) {
        finalColor += uGoldColor * (uBeaconGlow * beaconPulse * 0.40);
    }

    // Ambient lighting & underwater shading
    float ambient = 0.45 + 0.55 * uDayNight;
    finalColor *= ambient;

    // Subtle top rim highlight
    float rim = smoothstep(0.0, 0.08, p.y - (-0.04 + lidLift + 0.32)) * aLid * 0.30;
    finalColor += vec3(0.9, 0.95, 1.0) * rim;

    fragColor = vec4(clamp(finalColor, 0.0, 1.0), totalAlpha);
}
