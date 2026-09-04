#version 300 es
precision highp float;
uniform sampler2D uSceneTex;
uniform vec2 uSceneTexelStep;
uniform float uThreshold;
in vec2 vUV;
out vec4 fragColor;
vec3 extractBright(vec3 c){
  float br = max(max(c.r, c.g), c.b);
  float k = max((br - uThreshold) / max(1e-4, (1.0 - uThreshold)), 0.0);
  return c * k;
}
void main(){
  vec2 d = uSceneTexelStep;
  vec3 bloom = extractBright(texture(uSceneTex, vUV + vec2(-d.x, -d.y)).rgb);
  bloom += extractBright(texture(uSceneTex, vUV + vec2( d.x, -d.y)).rgb);
  bloom += extractBright(texture(uSceneTex, vUV + vec2(-d.x,  d.y)).rgb);
  bloom += extractBright(texture(uSceneTex, vUV + vec2( d.x,  d.y)).rgb);
  fragColor = vec4(bloom * 0.25, 1.0);
}
