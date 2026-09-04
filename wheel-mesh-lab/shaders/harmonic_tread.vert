#version 300 es
precision highp float;

uniform mat4 uMVPMatrix;
in vec3 vPosition;
out vec3 vLocalPosition;

void main() {
  vLocalPosition = vPosition;
  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
}
