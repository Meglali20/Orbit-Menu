#version 300 es
precision mediump float;
in vec2 vUv;

uniform sampler2D uBackgroundTex;
uniform float uAlpha;

out vec4 fragColor;

void main() {
    vec2 flippedUv = vec2(vUv.x, 1.0 - vUv.y);

    vec4 texColor = texture(uBackgroundTex, flippedUv);
    fragColor = vec4(texColor.rgb, texColor.a * uAlpha);
}