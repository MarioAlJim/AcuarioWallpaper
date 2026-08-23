#version 300 es
precision mediump float;

in vec2 vTexCoord;
in vec3 vColor;
in float vOpacity;
out vec4 fragColor;

void main() {
    float dist = distance(vTexCoord, vec2(0.5));

    // A bubble reads better as a thin bright rim plus a mostly-transparent fill than as a
    // plain soft dot (which just looks like a glow particle).
    float rim = smoothstep(0.5, 0.42, dist) - smoothstep(0.42, 0.30, dist);
    float fill = smoothstep(0.5, 0.0, dist) * 0.18;
    float alpha = rim + fill;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(vColor, alpha * vOpacity);
}
