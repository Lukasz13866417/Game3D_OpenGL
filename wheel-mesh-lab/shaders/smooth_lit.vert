#version 300 es
uniform mat4 uMVPMatrix;
uniform mat4 uModelMatrix;
in vec3 vPosition;
in vec3 aNormal;
out vec3 vWorldPos;
out vec3 vWorldNormal;
void main(){
  vec4 wp = uModelMatrix * vec4(vPosition, 1.0);
  vWorldPos = wp.xyz;
  vWorldNormal = mat3(uModelMatrix) * aNormal;
  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
}
