#version 300 es
precision highp float;
uniform sampler2D uEmissionTex;
uniform float uIntensity;
in vec2 vUV;
out vec4 fragColor;
void main(){
  fragColor = texture(uEmissionTex, vUV) * uIntensity;
}
