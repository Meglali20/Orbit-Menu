#version 300 es

in vec3 aPosition;
in vec2 aUvs;

out vec2 vUv;

void main() {
    vUv = aUvs;

    gl_Position = vec4(aPosition, 1.0);
}