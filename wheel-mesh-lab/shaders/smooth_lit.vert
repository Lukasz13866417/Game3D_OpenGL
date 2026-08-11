#version 300 es
uniform mat4 uMVPMatrix;
uniform mat4 uModelMatrix;
uniform float uSpinAngleStart;
uniform float uSpinAngleStep;
in vec3 vPosition;
in vec3 aNormal;
out vec3 vWorldPos;
out vec3 vWorldNormal;
void main(){
  float spinAngle = uSpinAngleStart + float(gl_InstanceID) * uSpinAngleStep;
  float spinCos = cos(spinAngle);
  float spinSin = sin(spinAngle);
  vec3 localPosition = vec3(vPosition.x, spinCos * vPosition.y - spinSin * vPosition.z, spinSin * vPosition.y + spinCos * vPosition.z);
  vec3 localNormal = vec3(aNormal.x, spinCos * aNormal.y - spinSin * aNormal.z, spinSin * aNormal.y + spinCos * aNormal.z);
  vec4 wp = uModelMatrix * vec4(localPosition, 1.0);
  vWorldPos = wp.xyz;
  vWorldNormal = mat3(uModelMatrix) * localNormal;
  gl_Position = uMVPMatrix * vec4(localPosition, 1.0);
}
