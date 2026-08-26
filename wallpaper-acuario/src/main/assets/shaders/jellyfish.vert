#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aUV;

uniform mat4 uMVPMatrix;

out vec2 vUV;

void main() {
    gl_Position = uMVPMatrix * vec4(aPosition, 0.0, 1.0);
    vUV = aUV;
}
