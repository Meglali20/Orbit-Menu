#version 300 es
precision highp float;
precision highp int;

uniform mat4 uWorldMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform vec4 uRotationAxisVelocity;
uniform sampler2D uGlowMap;
uniform int uItemCount;

in vec3 aModelPosition;
in vec3 aModelNormal;
in vec2 aModelUvs;
in mat4 aInstanceMatrix;

out vec2 vUvs;
out float vAlpha;
flat out int vInstanceId;
out float vGlowFactor;
out vec3 vGlowColor;

#define PI 3.141593

void main() {
    // glow factor for this instance (1.0 = glow enabled, 0.0 = disabled)
    int glowMapIndex = gl_InstanceID % uItemCount;
    vec4 glowData = texelFetch(uGlowMap, ivec2(glowMapIndex, 0), 0);
    float glowEnabled = glowData.r;
    vec3 glowColor = glowData.gba;

    vec4 worldPosition = uWorldMatrix * aInstanceMatrix * vec4(aModelPosition, 1.);

    // center of the disc in world space
    vec3 centerPos = (uWorldMatrix * aInstanceMatrix * vec4(0., 0., 0., 1.)).xyz;
    float radius = length(centerPos.xyz);

    // calculate distance from center for glow effect
    float distFromCenter = length(aModelPosition.xy);

    // skip the center vertex of the disc geometry
    if (gl_VertexID > 0) {
        // stretch the disc according to the axis and velocity of the rotation
        vec3 rotationAxis = uRotationAxisVelocity.xyz;
        float rotationVelocity = min(.15, uRotationAxisVelocity.w * 15.);
        // the stretch direction is orthogonal to the rotation axis and the position
        vec3 stretchDir = normalize(cross(centerPos, rotationAxis));
        vec3 relativeVertexPos = normalize(worldPosition.xyz - centerPos);
        float strength = dot(stretchDir, relativeVertexPos);
        float invAbsStrength = min(0., abs(strength) - 1.);
        strength = rotationVelocity * sign(strength) * abs(invAbsStrength * invAbsStrength * invAbsStrength + 1.);
        // apply the stretch distortion
        worldPosition.xyz += stretchDir * strength;
    }

    worldPosition.xyz = radius * normalize(worldPosition.xyz);

    gl_Position = uProjectionMatrix * uViewMatrix * worldPosition;

    vAlpha = smoothstep(0.5, 1., normalize(worldPosition.xyz).z) * .9 + .1;
    vUvs = aModelUvs;
    vInstanceId = gl_InstanceID;

    // glow factor passed based on distance from edge and whether glow is enabled
    float edgeFactor = smoothstep(0.85, 1.0, distFromCenter);
    vGlowFactor = glowEnabled * edgeFactor;
    vGlowColor = glowColor;
}