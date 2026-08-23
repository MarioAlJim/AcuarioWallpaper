#version 300 es
// Instanced quad: shared unit-quad geometry (aPosition/aTexCoord, divisor 0) plus
// per-instance position/scale/color/opacity (divisor 1), so the whole batch of bubbles
// draws in a single glDrawArraysInstanced call.
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in vec2 aInstancePos;
layout(location = 3) in float aInstanceScale;
layout(location = 4) in vec3 aInstanceColor;
layout(location = 5) in float aInstanceOpacity;

uniform mat4 uProjectionMatrix;

out vec2 vTexCoord;
out vec3 vColor;
out float vOpacity;

void main() {
    vTexCoord = aTexCoord;
    vColor = aInstanceColor;
    vOpacity = aInstanceOpacity;
    vec2 worldPos = aInstancePos + aPosition * aInstanceScale;
    gl_Position = uProjectionMatrix * vec4(worldPos, 0.0, 1.0);
}
