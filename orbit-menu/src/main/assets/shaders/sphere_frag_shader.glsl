#version 300 es
precision mediump float;

in vec2 vUvs;

uniform sampler2D uTex;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uTex, vUvs);
    fragColor = texColor;
}