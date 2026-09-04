#version 300 es
uniform mat4 uMVPMatrix;
uniform mat4 uModelMatrix;
uniform vec4 vColor;
uniform vec3 uLightPos;
uniform vec3 uLightColor;
uniform vec3 uCameraPos;
uniform float uAmbient;
uniform float uDiffuse;
uniform float uSpecular;
uniform float uShininess;
in vec3 vPosition;
in vec3 aNormal;
out vec4 vLitColor;
void main(){
  vec4 wp = uModelMatrix * vec4(vPosition, 1.0);
  vec3 worldPos = wp.xyz;
  vec3 worldNormal = normalize(mat3(uModelMatrix) * aNormal);
  vec3 L = normalize(uLightPos - worldPos);
  vec3 V = normalize(uCameraPos - worldPos);
  vec3 H = normalize(L + V);
  float NdotL = max(dot(worldNormal, L), 0.0);
  float NdotH = max(dot(worldNormal, H), 0.0);
  float spec = pow(NdotH, uShininess);
  vec3 color = vColor.rgb * (uAmbient + uDiffuse * NdotL) + uLightColor * uSpecular * spec;
  vLitColor = vec4(color, vColor.a);
  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
}
