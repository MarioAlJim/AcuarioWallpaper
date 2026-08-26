#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    float dist = distance(vTexCoord, vec2(0.5));
    // Soft glowing circle with a solid-ish core
    float alpha = smoothstep(0.5, 0.0, dist);
    if (alpha <= 0.0) {
        discard;
    }
    // Bright golden/yellow food particle
    fragColor = vec4(1.0, 0.85, 0.3, alpha);
}
