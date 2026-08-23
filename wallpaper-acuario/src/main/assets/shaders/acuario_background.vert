#version 300 es
// Fullscreen quad already spans clip space (-1..1), so no MVP matrix is needed - just
// forward the position and derive a 0..1 UV from it for the fragment shader.
layout(location = 0) in vec2 aPosition;

out vec2 vUv;

void main() {
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
