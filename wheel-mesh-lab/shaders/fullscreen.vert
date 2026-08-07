#version 300 es
in vec3 aPosition;
in vec2 aUV;
out vec2 vUV;
void main(){
  vUV = aUV;
  gl_Position = vec4(aPosition, 1.0);
}
