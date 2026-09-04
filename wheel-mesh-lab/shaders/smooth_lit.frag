#version 300 es
precision mediump float;
uniform vec4 vColor;
uniform vec3 uLightPos;
uniform vec3 uLightColor;
uniform vec3 uCameraPos;
uniform float uAmbient;
uniform float uDiffuse;
uniform float uSpecular;
uniform float uShininess;
in vec3 vWorldPos;
in vec3 vWorldNormal;
out vec4 fragColor;
void main(){
  vec3 N = normalize(vWorldNormal);
  vec3 L = normalize(uLightPos - vWorldPos);
  vec3 V = normalize(uCameraPos - vWorldPos);
  vec3 H = normalize(L + V);
  float NdotL = max(dot(N, L), 0.0);
  float NdotH = max(dot(N, H), 0.0);
  float spec = pow(NdotH, uShininess);
  vec3 color = vColor.rgb * (uAmbient + uDiffuse * NdotL) + uLightColor * uSpecular * spec;
  fragColor = vec4(color, vColor.a);
}
