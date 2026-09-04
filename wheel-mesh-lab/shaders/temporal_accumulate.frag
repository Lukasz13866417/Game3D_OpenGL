#version 300 es
precision highp float;
uniform sampler2D uSampleTex;
uniform float uSampleWeight;
in vec2 vUV;
out vec4 fragColor;
void main(){
  fragColor = texture(uSampleTex, vUV) * uSampleWeight;
}
