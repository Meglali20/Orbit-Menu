#version 300 es
precision highp float;
precision highp int;

uniform sampler2D uTex;
uniform int uItemCount;
uniform int uAtlasSize;
uniform float uTime;

out vec4 outColor;

in vec2 vUvs;
in float vAlpha;
flat in int vInstanceId;
in float vGlowFactor;
in vec3 vGlowColor;

#define NOISE_PERIOD 20
#define PI 3.14159265359
#define TAU 6.2831853071

vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x*34.0)+10.0)*x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
vec2 fade(vec2 t) { return t*t*t*(t*(t*6.0-15.0)+10.0); }

float pnoise(vec2 P, vec2 rep) {
    vec4 Pi = floor(P.xyxy) + vec4(0.0, 0.0, 1.0, 1.0);
    vec4 Pf = fract(P.xyxy) - vec4(0.0, 0.0, 1.0, 1.0);
    Pi = mod(Pi, rep.xyxy);
    Pi = mod289(Pi);
    vec4 ix = Pi.xzxz;
    vec4 iy = Pi.yyww;
    vec4 fx = Pf.xzxz;
    vec4 fy = Pf.yyww;

    vec4 i = permute(permute(ix) + iy);

    vec4 gx = fract(i * (1.0 / 41.0)) * 2.0 - 1.0 ;
    vec4 gy = abs(gx) - 0.5 ;
    vec4 tx = floor(gx + 0.5);
    gx = gx - tx;

    vec2 g00 = vec2(gx.x,gy.x);
    vec2 g10 = vec2(gx.y,gy.y);
    vec2 g01 = vec2(gx.z,gy.z);
    vec2 g11 = vec2(gx.w,gy.w);

    vec4 norm = taylorInvSqrt(vec4(dot(g00, g00), dot(g01, g01), dot(g10, g10), dot(g11, g11)));
    g00 *= norm.x;
    g01 *= norm.y;
    g10 *= norm.z;
    g11 *= norm.w;

    float n00 = dot(g00, vec2(fx.x, fy.x));
    float n10 = dot(g10, vec2(fx.y, fy.y));
    float n01 = dot(g01, vec2(fx.z, fy.z));
    float n11 = dot(g11, vec2(fx.w, fy.w));

    vec2 fade_xy = fade(Pf.xy);
    vec2 n_x = mix(vec2(n00, n01), vec2(n10, n11), fade_xy.x);
    float n_xy = mix(n_x.x, n_x.y, fade_xy.y);
    return 2.3 * n_xy;
}

float fbm_periodic(vec2 pos, int octaves, float persistence, vec2 period) {
    float total = 0.0, frequency = 1.0, amplitude = 1.0, maxValue = 0.0;
    for(int i = 0; i < octaves; ++i) {
        total += pnoise(pos * frequency, period) * amplitude;
        maxValue += amplitude;
        amplitude *= persistence;
        frequency *= 2.0;
    }
    return total / maxValue;
}

vec3 plasma(vec2 uv, float r) {
    float len = length(uv);
    float light = 0.1 / abs(len-r) * r;
    if (len < r) light *= len/r * 0.3;
    light = pow(light, 0.7);
    light *= smoothstep(3.5*r, 1.5*r, len);
    return light * vec3(0.9, 0.65, 0.5);
}

vec3 uchimura(vec3 x, float P, float a, float m, float l, float c, float b) {
    float l0 = ((P - m) * l) / a;
    float L0 = m - m / a;
    float L1 = m + (1.0 - m) / a;
    float S0 = m + l0;
    float S1 = m + a * l0;
    float C2 = (a * P) / (P - S1);
    float CP = -C2 / P;

    vec3 w0 = 1.0 - smoothstep(0.0, m, x);
    vec3 w2 = step(m + l0, x);
    vec3 w1 = 1.0 - w0 - w2;

    vec3 T = m * pow(x/m, vec3(c)) + b;
    vec3 S = P - (P - S1) * exp(CP * (x - S0));
    vec3 L = m + a * (x - m);

    return T * w0 + L * w1 + S * w2;
}

vec3 uchimura(vec3 x) {
    return uchimura(x, 1.0, 1.0, 0.22, 0.4, 1.33, 0.0);
}

float glowingRing(float dist, float radius, float width, float intensity) {
    float ringDist = abs(dist - radius);
    float glow = exp(-ringDist * ringDist / (width * width));
    return glow * intensity;
}

float enhancedOuterGlow(float dist, float radius, float width, float intensity) {
    float ringDist = max(0.0, dist - radius);
    float glow = exp(-ringDist * ringDist / (width * width * 4.0));
    return glow * intensity;
}

void main() {
    vec2 centeredUV = vUvs - 0.5;
    float distFromCenter = length(centeredUV);
    float diskRadius = 0.25;

    vec3 glowCol = vec3(0.98, 0.78, 0.42);
    if(length(vGlowColor) >= 0.01) {
        glowCol = vGlowColor;
    }

    float pulse = 0.5 + 0.5 * sin(uTime * 2.0);
    float ringPulse = 0.7 + 0.3 * sin(uTime * 3.0);
    float glowPulse = 0.85 + 0.15 * sin(uTime * 1.5);

    vec3 ringColor = mix(glowCol, vec3(1.0), 0.7 + pulse * 0.3);

    vec3 innerRingColor = mix(ringColor, vec3(1.0), 0.9) * 2.0;

    vec3 brightRingColor = mix(ringColor, vec3(1.0), 0.8) * 3.0;

    // main ring placed at the edge of the disk
    float mainRing = glowingRing(distFromCenter, diskRadius * 0.99, 0.004, 2.5 + ringPulse);

    // inner ring
    float innerRingWidth = 0.0025;
    float innerRingRadius = diskRadius * 1.;
    float innerRing = glowingRing(distFromCenter, innerRingRadius, innerRingWidth, 1.0 + ringPulse * 0.5); // Subtle glow

    // outer ring
    float outerRing = glowingRing(distFromCenter, diskRadius * 1.02, 0.006, 2.0 + ringPulse);
    float outerGlow = enhancedOuterGlow(distFromCenter, diskRadius, 0.015, 1.0 + ringPulse * 0.5);

    if(distFromCenter <= diskRadius) {
        int itemIndex = vInstanceId % uItemCount;
        int cellsPerRow = uAtlasSize;
        int cellX = itemIndex % cellsPerRow;
        int cellY = itemIndex / cellsPerRow;
        vec2 cellSize = vec2(1.0)/vec2(float(cellsPerRow));
        vec2 cellOffset = vec2(float(cellX), float(cellY)) * cellSize;

        ivec2 texSize = textureSize(uTex, 0);
        float imageAspect = float(texSize.x)/float(texSize.y);
        float scale = max(imageAspect, 1.0/imageAspect) * 1.5;

        vec2 st = (1.0 - vUvs - 0.5) * scale + 0.5;
        st = clamp(st, 0.0, 1.0) * cellSize + cellOffset;

        vec4 texColor = texture(uTex, st);
        texColor.a *= vAlpha;

        float innerGlowSpan = diskRadius * (0.05 + 0.04 * pulse); // Slightly thinner
        float innerEdgeGlow = smoothstep(
            diskRadius - innerGlowSpan,
            diskRadius * (1.0 + 0.05 * glowPulse),
            distFromCenter
        );

        vec3 innerGlowTint = mix(
            texColor.rgb,
            ringColor * (1.1 + 0.3 * glowPulse),
            innerEdgeGlow * (0.5 + 0.3 * glowPulse) * vGlowFactor
        );

        vec3 finalColor = innerGlowTint;

        finalColor += innerRingColor * innerRing * vGlowFactor * 0.3;

        outColor = vec4(finalColor, texColor.a);
    } else {
        vec3 outerRingColor = mix(ringColor, vec3(1.0), 0.7) * 3.0;
        //plasma ray effect
        vec2 uv = centeredUV * 5.2;
        float BlackHoleRadius = diskRadius * 5.2;
        float radius = BlackHoleRadius * 1.6;

        float r = length(uv) / radius;
        float phi = atan(uv.y, uv.x);

        float a = fbm_periodic(vec2((phi + PI)/TAU * float(NOISE_PERIOD), uTime * 0.9), 1, 0.5,
                               vec2(float(NOISE_PERIOD), 100.0));
        a = (a + 1.0) * 0.5;
        a *= 0.75;
        a = pow(a, 1.5);

        vec3 col = vec3(1.0, 0.95, 0.8);
        col *= smoothstep(a + 0.81, a, r);
        col *= pow(1.0/pow(r, 1.15) * 0.85, 3.0) * vec3(1.0, 0.85, 0.75);
        col *= smoothstep(1.0, 1.04, length(uv)/BlackHoleRadius);
        // enhanced plasma
        col += plasma(uv, BlackHoleRadius) * 0.05;
        col += outerRingColor * outerRing * vGlowFactor * 0.5;
        col += outerRingColor * outerGlow * vGlowFactor * 0.35;
        col *= glowCol * 1.2;
        col = uchimura(col) * 1.2;
        // enhanced alpha calculation for better glow visibility
        float alpha = length(col) * vGlowFactor * glowPulse * 1.2;
        outColor = vec4(col, alpha);
    }
}