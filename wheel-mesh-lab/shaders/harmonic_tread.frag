#version 300 es
precision highp float;
precision highp int;

uniform vec4 uColor;
uniform float uRollPhaseRadians;
uniform float uGrooveCyclesPerFrame;
uniform float uGrooveCyclesDuringExposure;
uniform int uTrailingBox;

in vec3 vLocalPosition;
out vec4 fragColor;

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;
const float GROOVE_PITCH = TWO_PI / 18.0;
const float DUTY_CYCLE = 0.26164;
const float ALIAS_START = 0.35;
const float ALIAS_END = 0.50;
const float POSITIVE_FILTER_ROLLOFF = 18.0;
const int HARMONIC_COUNT = 5;
// Convex blend of cos^8(phase/2) and cos^10(phase/2). It is a finite,
// nonnegative periodic groove profile with peak 1 and mean DUTY_CYCLE.
const float PROFILE_COEFFICIENTS[HARMONIC_COUNT] = float[HARMONIC_COUNT](
    0.4257025,
    0.22549142857142856,
    0.07345482142857145,
    0.012868571428571434,
    0.0008426785714285726
);

float sincPi(float value) {
  if (abs(value) <= 1.0e-5) {
    return 1.0;
  }
  float argument = PI * value;
  return sin(argument) / argument;
}

float centeredHannTransfer(float cycles) {
  return sincPi(cycles)
      + 0.5 * (sincPi(cycles - 1.0) + sincPi(cycles + 1.0));
}

float positiveKernelGain(float cyclesPerSample, float harmonic) {
  float cycles = abs(cyclesPerSample);
  float carrier = 1.0 - smoothstep(ALIAS_START, ALIAS_END, cycles);
  float frequency = harmonic * cycles;
  // exp(-a*k^2) is the Fourier response of a wrapped Gaussian (a positive
  // normalized kernel). Mixing it with the phase-independent mean through
  // carrier is also positive, so this filter cannot change DC energy.
  return carrier * exp(-POSITIVE_FILTER_ROLLOFF * frequency * frequency);
}

void main() {
  float aliasCycles = abs(uGrooveCyclesPerFrame);

  // At Nyquist there is deliberately no phase evaluation at all. Besides
  // saving work, this makes the high-speed endpoint exactly phase invariant.
  if (aliasCycles >= ALIAS_END) {
    float coverage = DUTY_CYCLE;
    fragColor = vec4(uColor.rgb * coverage, uColor.a * coverage);
    return;
  }

  float radius = max(length(vLocalPosition.yz), 1.0e-5);
  float theta = atan(vLocalPosition.z, vLocalPosition.y);

  // The authored groove is a V in axial/tangential coordinates: its apex is
  // 0.0625 behind its shoulders and each arm rises 0.125 over a 0.096 span.
  float chevronTangentialCenter = -0.0625
      + 0.125 * abs(vLocalPosition.x) / 0.096;
  float chevronAngularCenter = chevronTangentialCenter / radius;

  float exposureCycles = uGrooveCyclesDuringExposure;
  float rollCenter = uRollPhaseRadians;
  if (uTrailingBox != 0) {
    rollCenter -= 0.5 * exposureCycles * GROOVE_PITCH;
  }
  float phase = 18.0 * (theta - rollCenter - chevronAngularCenter);
  float spatialCyclesPerPixel = abs(fwidth(phase)) / TWO_PI;

  // The finite positive profile is convolved with three normalized positive
  // kernels: the temporal shutter, the temporal anti-alias filter and the
  // derivative-based spatial anti-alias filter. Their DC response is exactly
  // one, so blur cannot pump or dim the groove's mean emission.
  float coverage = DUTY_CYCLE;
  for (int harmonic = 1; harmonic <= HARMONIC_COUNT; ++harmonic) {
    float k = float(harmonic);
    float temporalGain = positiveKernelGain(aliasCycles, k);
    float spatialGain = positiveKernelGain(spatialCyclesPerPixel, k);
    float exposureFrequency = k * abs(exposureCycles);
    float shutterGain = uTrailingBox != 0
        ? sincPi(exposureFrequency)
        : centeredHannTransfer(exposureFrequency);
    coverage += PROFILE_COEFFICIENTS[harmonic - 1]
        * shutterGain * temporalGain * spatialGain
        * cos(k * phase);
  }

  // The convolution above is analytically in [0,1]; clamp only float roundoff.
  coverage = clamp(coverage, 0.0, 1.0);
  fragColor = vec4(uColor.rgb * coverage, uColor.a * coverage);
}
