#version 300 es
precision mediump float;

in vec3 aPosition;
in vec2 aUvs;

uniform mat4 uWorldMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;

out vec2 vUvs;

void main() {
    vUvs = aUvs;
    gl_Position = uProjectionMatrix * uViewMatrix * uWorldMatrix * vec4(aPosition, 1.0);
}