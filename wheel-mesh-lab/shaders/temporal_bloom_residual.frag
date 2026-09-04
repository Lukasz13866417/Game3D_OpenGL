#version 300 es
precision highp float;
uniform sampler2D uEmissionTex;
uniform sampler2D uSceneTex;
uniform vec2 uSceneTexelStep;
uniform float uBloomThreshold;
uniform float uEmissionBrightFactor;
uniform float uBloomCorrectionBlend;
in vec2 vUV;
out vec4 fragColor;
vec3 extractBright(vec3 color){
  float peak = max(max(color.r, color.g), color.b);
  float factor = max((peak - uBloomThreshold)
      / max(1e-4, 1.0 - uBloomThreshold), 0.0);
  return color * factor;
}
void main(){
  vec2 d = uSceneTexelStep;
  vec3 ordinary = extractBright(texture(uSceneTex, vUV + vec2(-d.x, -d.y)).rgb);
  ordinary += extractBright(texture(uSceneTex, vUV + vec2( d.x, -d.y)).rgb);
  ordinary += extractBright(texture(uSceneTex, vUV + vec2(-d.x,  d.y)).rgb);
  ordinary += extractBright(texture(uSceneTex, vUV + vec2( d.x,  d.y)).rgb);
  ordinary *= 0.25;
  vec3 decodedExposure = texture(uEmissionTex, vUV + vec2(-d.x, -d.y)).rgb;
  decodedExposure += texture(uEmissionTex, vUV + vec2( d.x, -d.y)).rgb;
  decodedExposure += texture(uEmissionTex, vUV + vec2(-d.x,  d.y)).rgb;
  decodedExposure += texture(uEmissionTex, vUV + vec2( d.x,  d.y)).rgb;
  decodedExposure *= 0.25;
  vec3 target = decodedExposure * uEmissionBrightFactor;
  vec3 residual = max(target - ordinary, vec3(0.0));
  fragColor = vec4(residual * uBloomCorrectionBlend, 0.0);
}
