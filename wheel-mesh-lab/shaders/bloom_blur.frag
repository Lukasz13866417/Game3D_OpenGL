#version 300 es
precision mediump float;
uniform sampler2D uInputTex;
uniform vec2 uTexelStep;
in vec2 vUV;
out vec4 fragColor;
void main(){
  vec3 s = texture(uInputTex, vUV).rgb * 0.227027;
  s += texture(uInputTex, vUV + uTexelStep * 1.384615).rgb * 0.316216;
  s += texture(uInputTex, vUV - uTexelStep * 1.384615).rgb * 0.316216;
  s += texture(uInputTex, vUV + uTexelStep * 3.230769).rgb * 0.070270;
  s += texture(uInputTex, vUV - uTexelStep * 3.230769).rgb * 0.070270;
  fragColor = vec4(s, 1.0);
}
