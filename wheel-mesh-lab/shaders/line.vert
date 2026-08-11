#version 300 es
uniform mat4 uMVPMatrix;
in vec3 aPosition;
in vec3 aColor;
out vec3 vColor;
void main(){
  vColor = aColor;
  gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
}
