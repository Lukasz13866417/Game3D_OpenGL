#version 300 es
precision mediump float;
uniform sampler2D uSceneTex;
uniform sampler2D uBloomTex;
uniform float uBloomIntensity;
in vec2 vUV;
out vec4 fragColor;
void main(){
  vec3 scene = texture(uSceneTex, vUV).rgb;
  vec3 bloom = texture(uBloomTex, vUV).rgb;
  fragColor = vec4(scene + bloom * uBloomIntensity, 1.0);
}
