#version 300 es
precision mediump float;

in vec2 vUv; // 0..1, y=0 at the bottom of the screen, y=1 at the top

uniform highp float uTime;
uniform int uTheme; // 0 = Acuario (contained, warmer light), 1 = Mar abierto (deeper, colder light)
uniform float uAspectRatio;

out vec4 fragColor;

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
        causticStrength = 0.10;
        rayStrength = 0.10;
    } else {
        // Mar abierto: colder, deeper blue with stronger sunlight shafts filtering from the
        // surface, reading as more open/infinite depth.
        deepColor = vec3(0.010, 0.045, 0.130);
        shallowColor = vec3(0.05, 0.34, 0.55);
        causticStrength = 0.16;
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
        float phase = vUv.x * uAspectRatio * freq + y * slant * freq - uTime * speed * freq;
        ray += sin(phase) * 0.5 + 0.5;
    }
    ray /= 3.0;
    color += shallowColor * ray * raysMask * rayStrength;

    // Caustics: a dancing light-ripple pattern (two interfering sine fields), most visible
    // where the god rays are already bright, as if refracted by the same surface.
    vec2 p = vec2(vUv.x * uAspectRatio, y) * 6.0;
    float t = uTime * 0.35;
    float c1 = sin(p.x * 1.7 + t + sin(p.y * 2.3 - t * 0.7));
    float c2 = sin(p.y * 1.9 - t * 1.3 + sin(p.x * 2.1 + t * 0.5));
    float caustic = smoothstep(0.55, 1.0, c1 * c2);
    color += shallowColor * caustic * causticStrength * (0.4 + raysMask * 0.8);

    fragColor = vec4(color, 1.0);
}
