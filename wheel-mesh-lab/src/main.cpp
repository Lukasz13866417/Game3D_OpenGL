#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl31.h>

#include "live_diagnostics.hpp"
#include "wheel_models.hpp"

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <limits>
#include <numeric>
#include <optional>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include <glm/geometric.hpp>
#include <glm/gtc/constants.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/type_ptr.hpp>
#include <glm/mat4x4.hpp>
#include <glm/vec2.hpp>
#include <glm/vec3.hpp>

namespace fs = std::filesystem;
using wheel_lab::CpuMesh;
using wheel_lab::Material;
using wheel_lab::WheelModel;
using wheel_lab::diagnostics::GpuFrameTimer;
using wheel_lab::diagnostics::GpuTimingResult;
using wheel_lab::diagnostics::PresentationTracker;
using wheel_lab::diagnostics::ScanoutObservation;

namespace {

constexpr float kGameVerticalFovDegrees = 36.86989765F;
constexpr float kBloomThreshold = 0.64F;
constexpr float kBloomIntensity = 0.95F;
constexpr int kBloomIterations = 2;
constexpr float kBloomDownsample = 0.25F;
constexpr float kBloomTexelStepScale = 0.5F;
constexpr float kCollisionRadius = 0.5F;
constexpr float kGameplayCylinderRadius = 0.22806F;
constexpr float kGameplayCylinderHalfLength = 0.063F;
constexpr float kGameToLabScale = kCollisionRadius / kGameplayCylinderRadius;
constexpr float kCollisionHalfWidth = kGameplayCylinderHalfLength * kGameToLabScale;
constexpr float kGameplayCameraBack = 3.8F * kGameToLabScale;
constexpr float kGameplayCameraAbove = 0.75F * kGameToLabScale;
constexpr float kGameplayNearClip = 3.0F * kGameToLabScale;
constexpr float kGameplayFarClip = 160.0F * kGameToLabScale;
constexpr float kGameplayAddonLightBack = 8.5F * kGameToLabScale;
constexpr float kGameplayAddonLightAbove = 0.5F * kGameToLabScale;
constexpr glm::vec3 kBackground(0.018F, 0.022F, 0.030F);
constexpr float kDefaultSpinRps = 5.0F;
constexpr int kReferenceTemporalSamples = 64;
constexpr int kMaxTemporalSamples = 12;
constexpr float kDefaultPresentedFramesPerSecond = 120.0F;
constexpr float kTemporalShutterFrameFraction = 0.75F;
constexpr float kMaxTemporalExposureSeconds = 1.0F / 30.0F;
constexpr float kTargetSampleSpacingPixels = 0.75F;
constexpr float kLodHysteresisDegreesPerFrame = 0.25F;
constexpr float kTemporalBlendStartPixels = 0.5F;
constexpr float kTemporalBlendFullPixels = 2.5F;
constexpr float kTemporalActivationEpsilon = 1.0e-4F;
constexpr float kNeonDarkChannel = 0.04F;
constexpr float kNeonBrightChannel = 0.98F;
constexpr float kNeonSaturationGain = 2.2F;
constexpr float kMintGroovePitchRadians = glm::two_pi<float>()
        / static_cast<float>(wheel_lab::kMintChevronCount);
constexpr float kBandTransitionStartDegrees = 8.0F;
constexpr float kBandTransitionEndDegrees = 12.0F;
// The production representation begins losing repeated-detail contrast before
// two adjacent presentations can differ by half a groove period. At and above
// that ambiguity boundary, a phase-independent emissive tread band carries the
// exact integrated groove energy instead of trying to infer an apparent phase.
constexpr float kAliasSafeBandStartGrooveCyclesPerFrame = 0.35F;
constexpr float kAliasSafeBandEndGrooveCyclesPerFrame = 0.50F;
constexpr float kAliasSafeBandStartDegrees =
        kAliasSafeBandStartGrooveCyclesPerFrame * 360.0F
        / static_cast<float>(wheel_lab::kMintChevronCount);
constexpr float kAliasSafeBandEndDegrees =
        kAliasSafeBandEndGrooveCyclesPerFrame * 360.0F
        / static_cast<float>(wheel_lab::kMintChevronCount);
constexpr float kPositiveHarmonicRolloff = 18.0F;
constexpr int kMaxAliasSafePhysicalSamples = kMaxTemporalSamples - 1;
constexpr float kDefaultMaxRollStepDegrees = 1.5F;
// Interactive split integration must never turn a missed presentation into an
// unbounded burst of draw calls. Offline captures retain the larger reference
// budget so the existing box-oracle comparison remains available.
constexpr int kMaxInteractiveFrameSplitSamples = 64;
constexpr int kMaxOfflineFrameSplitSamples = 128;
constexpr double kInteractiveSplitCountHysteresisParts = 0.20;

enum class TemporalMode {
    Sharp,
    Reference,
    Adaptive,
    AdaptiveRaw,
    BandLimited,
    FrameSplit,
    FrameSplitRaw,
    AliasSafe,
};

enum class TemporalSource {
    SharpMesh,
    HarmonicShell,
    PhysicalSamples,
    MotionBand,
    PhysicalPlusBand,
    Empty,
};

bool usesAliasSafeBand(const TemporalMode mode) {
    return mode == TemporalMode::Adaptive
            || mode == TemporalMode::FrameSplit
            || mode == TemporalMode::AliasSafe;
}

bool usesHarmonicTreadShell(const TemporalMode mode) {
    return mode == TemporalMode::Adaptive
            || mode == TemporalMode::FrameSplit;
}

bool isFrameSplitMode(const TemporalMode mode) {
    return mode == TemporalMode::FrameSplit
            || mode == TemporalMode::FrameSplitRaw;
}

const char* temporalModeName(const TemporalMode mode) {
    switch (mode) {
        case TemporalMode::Sharp: return "sharp";
        case TemporalMode::Reference: return "reference";
        case TemporalMode::Adaptive: return "adaptive";
        case TemporalMode::AdaptiveRaw: return "adaptive-raw";
        case TemporalMode::BandLimited: return "band";
        case TemporalMode::FrameSplit: return "split";
        case TemporalMode::FrameSplitRaw: return "split-raw";
        case TemporalMode::AliasSafe: return "alias-safe";
    }
    return "unknown";
}

const char* temporalSourceName(const TemporalSource source) {
    switch (source) {
        case TemporalSource::SharpMesh: return "sharp_mesh";
        case TemporalSource::HarmonicShell: return "harmonic_shell";
        case TemporalSource::PhysicalSamples: return "physical_samples";
        case TemporalSource::MotionBand: return "motion_band";
        case TemporalSource::PhysicalPlusBand: return "physical_plus_band";
        case TemporalSource::Empty: return "empty";
    }
    return "unknown";
}

const char* temporalExposureProfileName(const TemporalMode mode) {
    switch (mode) {
        case TemporalMode::Adaptive: return "analytic_centered_hann";
        case TemporalMode::FrameSplit: return "analytic_trailing_box";
        case TemporalMode::FrameSplitRaw: return "trailing_box_midpoints";
        default: return "centered_hann_samples";
    }
}

bool isGeneratedSequenceFrame(const fs::path& path) {
    const std::string name = path.filename().string();
    constexpr std::size_t prefixLength = 6U;
    constexpr std::size_t digitCount = 5U;
    constexpr std::size_t suffixLength = 4U;
    if (name.size() != prefixLength + digitCount + suffixLength
            || name.compare(0U, prefixLength, "frame-") != 0
            || name.compare(prefixLength + digitCount, suffixLength, ".ppm") != 0) {
        return false;
    }
    return std::all_of(
            name.begin() + static_cast<std::ptrdiff_t>(prefixLength),
            name.begin() + static_cast<std::ptrdiff_t>(prefixLength + digitCount),
            [](const char value) { return value >= '0' && value <= '9'; });
}

bool isGeneratedReplaySource(const fs::path& path) {
    const std::string name = path.filename().string();
    constexpr std::size_t prefixLength = 7U;
    constexpr std::size_t digitCount = 5U;
    constexpr std::size_t suffixLength = 4U;
    if (name.size() != prefixLength + digitCount + suffixLength
            || name.compare(0U, prefixLength, "source-") != 0
            || name.compare(prefixLength + digitCount, suffixLength, ".ppm") != 0) {
        return false;
    }
    return std::all_of(
            name.begin() + static_cast<std::ptrdiff_t>(prefixLength),
            name.begin() + static_cast<std::ptrdiff_t>(prefixLength + digitCount),
            [](const char value) { return value >= '0' && value <= '9'; });
}

// frame-%05d.ppm is the renderer and QA-tool interchange format.  Keep every
// generated index at exactly five digits so cleanup and contiguous-sequence
// validation cannot disagree about which files belong to a run.
constexpr int kMaxSequenceFrames = 100000;

TemporalMode parseTemporalMode(const std::string& value) {
    if (value == "sharp") return TemporalMode::Sharp;
    if (value == "reference") return TemporalMode::Reference;
    if (value == "adaptive") return TemporalMode::Adaptive;
    if (value == "adaptive-raw") return TemporalMode::AdaptiveRaw;
    if (value == "band" || value == "band-limited") return TemporalMode::BandLimited;
    if (value == "split" || value == "frame-split") return TemporalMode::FrameSplit;
    if (value == "split-raw" || value == "frame-split-raw") {
        return TemporalMode::FrameSplitRaw;
    }
    if (value == "alias-safe" || value == "production") return TemporalMode::AliasSafe;
    throw std::invalid_argument(
            "unknown temporal mode: " + value
            + " (expected sharp, reference, adaptive, adaptive-raw, band, "
                    "split, split-raw, or alias-safe)");
}

float smoothStep(const float edge0, const float edge1, const float value) {
    const float t = std::clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
    return t * t * (3.0F - 2.0F * t);
}

float sincPi(const float value) {
    if (std::abs(value) <= 1.0e-6F) {
        return 1.0F;
    }
    const float argument = glm::pi<float>() * value;
    return std::sin(argument) / argument;
}

float centeredHannTransfer(const float cyclesDuringExposure) {
    return sincPi(cyclesDuringExposure)
            + 0.5F * (sincPi(cyclesDuringExposure - 1.0F)
                    + sincPi(cyclesDuringExposure + 1.0F));
}

float positiveHarmonicFilterGain(
        const int harmonic,
        const float cyclesPerSample) {
    const float cycles = std::abs(cyclesPerSample);
    const float carrier = 1.0F - smoothStep(
            kAliasSafeBandStartGrooveCyclesPerFrame,
            kAliasSafeBandEndGrooveCyclesPerFrame,
            cycles);
    const float frequency = static_cast<float>(harmonic) * cycles;
    return carrier * std::exp(
            -kPositiveHarmonicRolloff * frequency * frequency);
}

float emissionBrightPassFactor() {
    return std::max(
            (kNeonBrightChannel - kBloomThreshold)
                    / std::max(1.0e-4F, 1.0F - kBloomThreshold),
            0.0F);
}

float temporalBloomResidual(
        const float decodedPremultipliedExposure,
        const float ordinaryBrightPass,
        const float correctionBlend) {
    return std::clamp(correctionBlend, 0.0F, 1.0F) * std::max(
            decodedPremultipliedExposure * emissionBrightPassFactor()
                    - ordinaryBrightPass,
            0.0F);
}

struct TemporalPlan {
    TemporalMode mode = TemporalMode::Sharp;
    int sampleCount = 1;
    float centerPhaseRadians = 0.0F;
    float angularVelocityRadiansPerSecond = 0.0F;
    float presentedRollDeltaRadians = 0.0F;
    float presentationIntervalSeconds = 0.0F;
    float maxRollStepRadians = glm::radians(kDefaultMaxRollStepDegrees);
    float effectiveExposureSeconds = 0.0F;
    float degreesPerFrame = 0.0F;
    float projectedSweepPixels = 0.0F;
    float grooveCyclesPerFrame = 0.0F;
    float rawBandBlend = 0.0F;
    float bandBlend = 0.0F;
    float grooveContrast = 1.0F;
    float motionBandEnergyWeight = 0.0F;
    float temporalBlend = 0.0F;
    float bloomCorrectionBlend = 0.0F;
    float coreIntensity = 0.0F;
    bool lodHeldByHysteresis = false;
    std::uint64_t requestedSampleCount = 1U;
    bool sampleCapApplied = false;
    bool splitBudgetHeldByHysteresis = false;
    int splitSampleBudget = kMaxOfflineFrameSplitSamples;
    std::vector<float> angleOffsetsRadians{0.0F};
    std::vector<float> sampleWeights{1.0F};

    void resetPreservingStorage() {
        mode = TemporalMode::Sharp;
        sampleCount = 1;
        centerPhaseRadians = 0.0F;
        angularVelocityRadiansPerSecond = 0.0F;
        presentedRollDeltaRadians = 0.0F;
        presentationIntervalSeconds = 0.0F;
        maxRollStepRadians = glm::radians(kDefaultMaxRollStepDegrees);
        effectiveExposureSeconds = 0.0F;
        degreesPerFrame = 0.0F;
        projectedSweepPixels = 0.0F;
        grooveCyclesPerFrame = 0.0F;
        rawBandBlend = 0.0F;
        bandBlend = 0.0F;
        grooveContrast = 1.0F;
        motionBandEnergyWeight = 0.0F;
        temporalBlend = 0.0F;
        bloomCorrectionBlend = 0.0F;
        coreIntensity = 0.0F;
        lodHeldByHysteresis = false;
        requestedSampleCount = 1U;
        sampleCapApplied = false;
        splitBudgetHeldByHysteresis = false;
        splitSampleBudget = kMaxOfflineFrameSplitSamples;
        angleOffsetsRadians.assign(1U, 0.0F);
        sampleWeights.assign(1U, 1.0F);
    }

    float samplePhase(const int sampleIndex) const {
        return centerPhaseRadians
                + angleOffsetsRadians[static_cast<std::size_t>(sampleIndex)];
    }

    float weight(const int sampleIndex) const {
        return sampleWeights[static_cast<std::size_t>(sampleIndex)];
    }

    int emissionDrawCount() const {
        if (usesHarmonicTreadShell(mode)) {
            return 1;
        }
        if (!usesAliasSafeBand(mode)) {
            return sampleCount;
        }
        return (grooveContrast > kTemporalActivationEpsilon ? sampleCount : 0)
                + (motionBandEnergyWeight > kTemporalActivationEpsilon ? 1 : 0);
    }
};

class TemporalPlanner {
public:
    const TemporalPlan& plan(
            const TemporalMode mode,
            const float centerPhaseRadians,
            const float spinRps,
            const float presentedFramesPerSecond,
            const float projectedRadiusPixels) {
        return plan(
                mode,
                centerPhaseRadians,
                spinRps,
                presentedFramesPerSecond,
                projectedRadiusPixels,
                spinRps * glm::two_pi<float>() / presentedFramesPerSecond,
                glm::radians(kDefaultMaxRollStepDegrees));
    }

    const TemporalPlan& plan(
            const TemporalMode mode,
            const float centerPhaseRadians,
            const float spinRps,
            const float presentedFramesPerSecond,
            const float projectedRadiusPixels,
            const float presentedRollDeltaRadians,
            const float maxRollStepRadians,
            const int splitSampleBudget = kMaxOfflineFrameSplitSamples,
            const bool stabilizeInteractiveSplitBudget = false) {
        if (!std::isfinite(presentedFramesPerSecond)
                || !(presentedFramesPerSecond > 0.0F)) {
            throw std::invalid_argument(
                    "temporal presentation cadence must be finite and positive");
        }
        if (splitSampleBudget < 1) {
            throw std::invalid_argument(
                    "temporal split sample budget must be positive");
        }
        if (!std::isfinite(presentedRollDeltaRadians)) {
            throw std::invalid_argument(
                    "presented roll delta must be finite");
        }
        // Retain the largest sample buffers reached by this planner. The old
        // aggregate assignment destroyed and reallocated both vectors every
        // frame, contaminating high-refresh timing diagnostics.
        plan_.resetPreservingStorage();
        plan_.mode = mode;
        plan_.centerPhaseRadians = centerPhaseRadians;
        // The actual change between presented poses is authoritative. The RPS
        // argument remains in this overload for source compatibility and for
        // the convenience overload above, which synthesizes this same delta.
        (void) spinRps;
        plan_.angularVelocityRadiansPerSecond =
                presentedRollDeltaRadians * presentedFramesPerSecond;
        plan_.presentedRollDeltaRadians = presentedRollDeltaRadians;
        plan_.presentationIntervalSeconds = 1.0F / presentedFramesPerSecond;
        plan_.maxRollStepRadians = maxRollStepRadians;
        plan_.splitSampleBudget = splitSampleBudget;
        if (mode == TemporalMode::FrameSplit) {
            populateHarmonicFrameSplitPlan(
                    presentedFramesPerSecond,
                    projectedRadiusPixels);
            finalizeHarmonicShellPlan();
            return plan_;
        }
        if (mode == TemporalMode::FrameSplitRaw) {
            populateFrameSplitSamples(
                    presentedFramesPerSecond,
                    projectedRadiusPixels,
                    stabilizeInteractiveSplitBudget,
                    false);
            return plan_;
        }
        if (mode == TemporalMode::Sharp
                || (presentedRollDeltaRadians == 0.0F
                        && !usesHarmonicTreadShell(mode))) {
            return plan_;
        }

        const float frameSeconds = 1.0F / presentedFramesPerSecond;
        plan_.effectiveExposureSeconds = std::min(
                frameSeconds * kTemporalShutterFrameFraction,
                kMaxTemporalExposureSeconds);
        const float absoluteAngularVelocity = std::abs(
                plan_.angularVelocityRadiansPerSecond);
        plan_.degreesPerFrame = glm::degrees(
                std::abs(plan_.presentedRollDeltaRadians));
        plan_.grooveCyclesPerFrame = std::abs(plan_.presentedRollDeltaRadians)
                / kMintGroovePitchRadians;
        plan_.projectedSweepPixels = absoluteAngularVelocity
                * plan_.effectiveExposureSeconds * projectedRadiusPixels;
        // Android ramps only the residual bloom policy with projected motion.
        // Deliberately exclude bandBlend: representation changes (and the
        // legacy band's LOD history) must not pump bloom.
        plan_.bloomCorrectionBlend = smoothStep(
                kTemporalBlendStartPixels,
                kTemporalBlendFullPixels,
                plan_.projectedSweepPixels);

        if (mode == TemporalMode::BandLimited || usesAliasSafeBand(mode)) {
            const float transitionStart = usesAliasSafeBand(mode)
                    ? kAliasSafeBandStartDegrees
                    : kBandTransitionStartDegrees;
            const float transitionEnd = usesAliasSafeBand(mode)
                    ? kAliasSafeBandEndDegrees
                    : kBandTransitionEndDegrees;
            plan_.rawBandBlend = smoothStep(
                    transitionStart,
                    transitionEnd,
                    plan_.degreesPerFrame);
            if (usesAliasSafeBand(mode)) {
                // A deadband is useful when it chooses between discrete mesh
                // LODs, but it is wrong for a continuous presentation blend:
                // holding this value creates visible brightness/contrast
                // plateaus followed by a jump while speed is changing. The
                // alias-safe modes therefore use the stateless response.
                plan_.bandBlend = plan_.rawBandBlend;
                plan_.grooveContrast = 1.0F - plan_.bandBlend;
                plan_.motionBandEnergyWeight =
                        wheel_lab::kMintMotionBandCanonicalDutyCycle
                        * plan_.bandBlend;
            } else {
                // Retain the original experiment's LOD hysteresis only in the
                // explicitly legacy band-limited mode.
                plan_.bandBlend = stabilizedBandBlend(
                        plan_.degreesPerFrame,
                        plan_.rawBandBlend,
                        transitionStart,
                        transitionEnd,
                        plan_.lodHeldByHysteresis);
            }
        }

        if (mode == TemporalMode::Reference) {
            plan_.sampleCount = kReferenceTemporalSamples;
            // The high-sample oracle uses the same centered 0.75-frame Hann
            // exposure, but deliberately does not apply production band LOD.
            plan_.temporalBlend = 1.0F;
        } else {
            if (!std::isfinite(plan_.projectedSweepPixels)
                    || plan_.projectedSweepPixels
                            >= kTargetSampleSpacingPixels
                                    * static_cast<float>(kMaxTemporalSamples - 1)) {
                plan_.sampleCount = kMaxTemporalSamples;
            } else {
                const float intervals = std::ceil(
                        plan_.projectedSweepPixels / kTargetSampleSpacingPixels);
                plan_.sampleCount = std::clamp(
                        1 + static_cast<int>(intervals), 1, kMaxTemporalSamples);
            }
            if (mode == TemporalMode::BandLimited) {
                plan_.sampleCount = std::max(
                        plan_.sampleCount,
                        1 + static_cast<int>(std::ceil(
                                static_cast<float>(kMaxTemporalSamples - 1)
                                * plan_.bandBlend)));
            } else if (usesAliasSafeBand(mode)) {
                // Reserve the phase-independent band's fixed work slot even
                // just below its transition. Otherwise the first nonzero band
                // frame could change 12 physical samples into 11 and create a
                // separate sampling discontinuity.
                plan_.sampleCount = std::min(
                        plan_.sampleCount, kMaxAliasSafePhysicalSamples);
            }
            plan_.temporalBlend = std::max(
                    plan_.bloomCorrectionBlend, plan_.bandBlend);
        }

        if (plan_.temporalBlend > kTemporalActivationEpsilon) {
            // Android switches to one complete normalized exposure here. The
            // blend controls activation only, not core opacity or bloom energy.
            plan_.coreIntensity = 1.0F;
        }
        if (usesHarmonicTreadShell(mode)) {
            finalizeHarmonicShellPlan();
            return plan_;
        }
        populateSamples();
        return plan_;
    }

    void resetLodHistory() {
        lodInitialized_ = false;
        acceptedLodDegreesPerFrame_ = 0.0F;
        stabilizedBandBlend_ = 0.0F;
        splitBudgetInitialized_ = false;
        acceptedSplitSampleCount_ = 1;
    }

private:
    TemporalPlan plan_;
    bool lodInitialized_ = false;
    float acceptedLodDegreesPerFrame_ = 0.0F;
    float stabilizedBandBlend_ = 0.0F;
    bool splitBudgetInitialized_ = false;
    int acceptedSplitSampleCount_ = 1;

    void populateHarmonicFrameSplitPlan(
            const float presentedFramesPerSecond,
            const float projectedRadiusPixels) {
        const float absoluteDelta = std::abs(plan_.presentedRollDeltaRadians);
        plan_.degreesPerFrame = glm::degrees(absoluteDelta);
        plan_.grooveCyclesPerFrame = absoluteDelta / kMintGroovePitchRadians;
        plan_.projectedSweepPixels = absoluteDelta * projectedRadiusPixels;
        plan_.effectiveExposureSeconds = 1.0F / presentedFramesPerSecond;
        plan_.rawBandBlend = smoothStep(
                kAliasSafeBandStartDegrees,
                kAliasSafeBandEndDegrees,
                plan_.degreesPerFrame);
        plan_.bandBlend = plan_.rawBandBlend;
        plan_.grooveContrast = 1.0F - plan_.bandBlend;
        plan_.motionBandEnergyWeight =
                wheel_lab::kMintMotionBandCanonicalDutyCycle
                * plan_.bandBlend;
        plan_.bloomCorrectionBlend = smoothStep(
                kTemporalBlendStartPixels,
                kTemporalBlendFullPixels,
                plan_.projectedSweepPixels);

        // Clean split is an analytic shader integral. Keep every lattice-only
        // diagnostic at its neutral value so title/manifest output cannot
        // imply work that was neither planned nor rendered.
        plan_.requestedSampleCount = 1U;
        plan_.sampleCapApplied = false;
        plan_.splitBudgetHeldByHysteresis = false;
    }

    void finalizeHarmonicShellPlan() {
        // Clean adaptive/split modes evaluate the complete periodic glow in a
        // single shell shader. There is no quadrature lattice whose integer
        // count can pop while presentation timing jitters.
        plan_.sampleCount = 1;
        plan_.angleOffsetsRadians.assign(1U, 0.0F);
        plan_.sampleWeights.assign(1U, 1.0F);
        plan_.temporalBlend = 1.0F;
        plan_.coreIntensity = 1.0F;
    }

    float stabilizedBandBlend(
            const float degreesPerFrame,
            const float rawBandBlend,
            const float transitionStartDegrees,
            const float transitionEndDegrees,
            bool& held) {
        const bool endpoint = degreesPerFrame <= transitionStartDegrees
                || degreesPerFrame >= transitionEndDegrees;
        if (!lodInitialized_ || endpoint
                || std::abs(degreesPerFrame - acceptedLodDegreesPerFrame_)
                        >= kLodHysteresisDegreesPerFrame) {
            lodInitialized_ = true;
            acceptedLodDegreesPerFrame_ = degreesPerFrame;
            stabilizedBandBlend_ = rawBandBlend;
            held = false;
        } else {
            held = true;
        }
        return stabilizedBandBlend_;
    }

    void populateFrameSplitSamples(
            const float presentedFramesPerSecond,
            const float projectedRadiusPixels,
            const bool stabilizeInteractiveSplitBudget,
            const bool aliasSafeBand) {
        if (!std::isfinite(plan_.presentedRollDeltaRadians)) {
            throw std::invalid_argument("split-mode presented roll delta must be finite");
        }
        if (!std::isfinite(plan_.maxRollStepRadians)
                || !(plan_.maxRollStepRadians > 0.0F)) {
            throw std::invalid_argument("split-mode maximum roll step must be finite and positive");
        }

        const float absoluteDelta = std::abs(plan_.presentedRollDeltaRadians);
        plan_.degreesPerFrame = glm::degrees(absoluteDelta);
        plan_.grooveCyclesPerFrame = absoluteDelta / kMintGroovePitchRadians;
        plan_.projectedSweepPixels = absoluteDelta * projectedRadiusPixels;
        if (aliasSafeBand) {
            plan_.rawBandBlend = smoothStep(
                    kAliasSafeBandStartDegrees,
                    kAliasSafeBandEndDegrees,
                    plan_.degreesPerFrame);
            // This is a continuously weighted presentation filter, not a
            // discrete LOD choice. Quantizing it with the old 0.25-degree
            // deadband makes a speed ramp pulse as each held value catches up.
            plan_.bandBlend = plan_.rawBandBlend;
            plan_.grooveContrast = 1.0F - plan_.bandBlend;
            plan_.motionBandEnergyWeight =
                    wheel_lab::kMintMotionBandCanonicalDutyCycle
                    * plan_.bandBlend;
            plan_.bloomCorrectionBlend = smoothStep(
                    kTemporalBlendStartPixels,
                    kTemporalBlendFullPixels,
                    plan_.projectedSweepPixels);
        }
        const double continuousPartCount =
                static_cast<double>(absoluteDelta)
                        / static_cast<double>(plan_.maxRollStepRadians);
        if (!(continuousPartCount > 1.0)) {
            plan_.requestedSampleCount = 1U;
        } else {
            const double rawRequested = std::ceil(continuousPartCount);
            if (!std::isfinite(rawRequested)
                    || rawRequested
                            >= static_cast<double>(
                                    std::numeric_limits<std::uint64_t>::max())) {
                plan_.requestedSampleCount =
                        std::numeric_limits<std::uint64_t>::max();
            } else {
                plan_.requestedSampleCount = std::max<std::uint64_t>(
                        2U, static_cast<std::uint64_t>(rawRequested));
            }
        }
        const bool renderBand = aliasSafeBand
                && plan_.motionBandEnergyWeight > kTemporalActivationEpsilon;
        const bool renderPhysical = !aliasSafeBand
                || plan_.grooveContrast > kTemporalActivationEpsilon;
        if (!renderPhysical) {
            // At the ambiguity endpoint, calculating or allocating a split
            // lattice has no visual effect. A hitch therefore reduces to one
            // fixed band draw rather than increasing temporal work.
            plan_.sampleCount = 1;
            plan_.sampleCapApplied = plan_.requestedSampleCount
                    > static_cast<std::uint64_t>(plan_.splitSampleBudget);
            plan_.effectiveExposureSeconds = 1.0F / presentedFramesPerSecond;
            plan_.temporalBlend = 1.0F;
            plan_.coreIntensity = 1.0F;
            return;
        }

        const int aliasSafeTotalBudget = std::min(
                plan_.splitSampleBudget, kMaxTemporalSamples);
        const int physicalSampleBudget = aliasSafeBand
                ? std::max(1, aliasSafeTotalBudget - 1)
                : plan_.splitSampleBudget;
        plan_.sampleCapApplied = plan_.requestedSampleCount
                > static_cast<std::uint64_t>(physicalSampleBudget);
        const int requestedWithinBudget = static_cast<int>(
                std::min<std::uint64_t>(
                plan_.requestedSampleCount,
                static_cast<std::uint64_t>(physicalSampleBudget)));
        plan_.sampleCount = stabilizeInteractiveSplitBudget
                ? stabilizedSplitSampleCount(
                        continuousPartCount,
                        requestedWithinBudget,
                        plan_.splitBudgetHeldByHysteresis)
                : requestedWithinBudget;
        if (plan_.sampleCount <= 1) {
            // At or below the inclusive threshold, retain the exact current pose. Interactive
            // hysteresis may also hold this state briefly above the boundary to prevent chatter.
            // A partial safe-band transition still needs the normalized current groove plus its
            // one energy-matched band cell, even though the split shutter itself is inactive.
            if (renderBand) {
                plan_.effectiveExposureSeconds = 1.0F / presentedFramesPerSecond;
                plan_.temporalBlend = plan_.bandBlend;
                plan_.coreIntensity = 1.0F;
            }
            return;
        }
        plan_.angleOffsetsRadians.assign(
                static_cast<std::size_t>(plan_.sampleCount), 0.0F);
        plan_.sampleWeights.assign(
                static_cast<std::size_t>(plan_.sampleCount),
                1.0F / static_cast<float>(plan_.sampleCount));

        // A full trailing box exposure: each sample is the midpoint of one
        // equal-angle part between previous=(current-D) and current. Samples
        // never extrapolate beyond the current pose; only roll changes.
        for (int index = 0; index < plan_.sampleCount; ++index) {
            const double unitTime = (static_cast<double>(index) + 0.5)
                    / static_cast<double>(plan_.sampleCount);
            const double offset = (unitTime - 1.0)
                    * static_cast<double>(plan_.presentedRollDeltaRadians);
            plan_.angleOffsetsRadians[static_cast<std::size_t>(index)] =
                    static_cast<float>(offset);
        }
        plan_.sampleWeights.back() += 1.0F
                - std::accumulate(
                        plan_.sampleWeights.begin(),
                        plan_.sampleWeights.end(),
                        0.0F);
        plan_.effectiveExposureSeconds = 1.0F / presentedFramesPerSecond;
        plan_.temporalBlend = 1.0F;
        if (!aliasSafeBand) {
            plan_.bloomCorrectionBlend = 1.0F;
        }
        plan_.coreIntensity = 1.0F;
    }

    int stabilizedSplitSampleCount(
            const double continuousPartCount,
            const int requestedWithinBudget,
            bool& held) {
        if (!splitBudgetInitialized_) {
            splitBudgetInitialized_ = true;
            acceptedSplitSampleCount_ = requestedWithinBudget;
            held = false;
            return acceptedSplitSampleCount_;
        }

        // ceil(r) normally selects N over (N-1, N]. Expand that interval on both sides. This
        // prevents tiny presentation-time variations from rebuilding every midpoint when r is
        // close to an integer, while still responding immediately to meaningful speed changes.
        const double lower = acceptedSplitSampleCount_ - 1.0
                - kInteractiveSplitCountHysteresisParts;
        const double upper = acceptedSplitSampleCount_
                + kInteractiveSplitCountHysteresisParts;
        if (continuousPartCount > lower && continuousPartCount <= upper) {
            held = requestedWithinBudget != acceptedSplitSampleCount_;
            return acceptedSplitSampleCount_;
        }

        acceptedSplitSampleCount_ = requestedWithinBudget;
        held = false;
        return acceptedSplitSampleCount_;
    }

    void populateSamples() {
        plan_.angleOffsetsRadians.assign(
                static_cast<std::size_t>(plan_.sampleCount), 0.0F);
        plan_.sampleWeights.assign(
                static_cast<std::size_t>(plan_.sampleCount), 0.0F);
        if (plan_.sampleCount <= 1) {
            plan_.sampleWeights[0] = 1.0F;
            return;
        }
        float rawWeightSum = 0.0F;
        for (int index = 0; index < plan_.sampleCount; ++index) {
            const float unitTime = (static_cast<float>(index) + 0.5F)
                    / static_cast<float>(plan_.sampleCount);
            const float timeFraction = unitTime - 0.5F;
            const float physicalOffset = plan_.angularVelocityRadiansPerSecond
                    * timeFraction * plan_.effectiveExposureSeconds;
            const float direction = std::copysign(
                    1.0F, plan_.angularVelocityRadiansPerSecond);
            const float bandOffset = direction * timeFraction * kMintGroovePitchRadians;
            plan_.angleOffsetsRadians[static_cast<std::size_t>(index)] =
                    plan_.mode == TemporalMode::BandLimited
                    ? (plan_.bandBlend >= 1.0F
                            ? bandOffset
                            : physicalOffset
                                    + (bandOffset - physicalOffset)
                                            * plan_.bandBlend)
                    : physicalOffset;

            const float hannWeight = 0.5F
                    - 0.5F * std::cos(glm::two_pi<float>() * unitTime);
            plan_.sampleWeights[static_cast<std::size_t>(index)] = hannWeight;
            rawWeightSum += hannWeight;
        }
        float normalizedSum = 0.0F;
        for (int index = 0; index < plan_.sampleCount; ++index) {
            const std::size_t sample = static_cast<std::size_t>(index);
            const float hannWeight = plan_.sampleWeights[sample] / rawWeightSum;
            const float uniformWeight = 1.0F / static_cast<float>(plan_.sampleCount);
            plan_.sampleWeights[sample] = plan_.mode == TemporalMode::BandLimited
                    ? hannWeight + (uniformWeight - hannWeight) * plan_.bandBlend
                    : hannWeight;
            normalizedSum += plan_.sampleWeights[sample];
        }
        plan_.sampleWeights[static_cast<std::size_t>(plan_.sampleCount - 1)]
                += 1.0F - normalizedSum;
    }
};

struct Options {
    bool smokeTest = false;
    bool exportAll = false;
    bool validateOnly = false;
    int selectedModel = 0;
    int width = 1180;
    int height = 820;
    int cameraPreset = 5;
    int mintGlowCount = wheel_lab::kDefaultMintGlowCount;
    bool bloom = true;
    bool autoRoll = false;
    bool fixedSpinPhase = false;
    float spinRps = kDefaultSpinRps;
    float spinPhaseDegrees = 0.0F;
    TemporalMode temporalMode = TemporalMode::AliasSafe;
    float presentedFramesPerSecond = kDefaultPresentedFramesPerSecond;
    float maxRollStepDegrees = kDefaultMaxRollStepDegrees;
    int sequenceFrames = 0;
    std::optional<float> sequenceEndSpinRps;
    bool sequenceFixedPhase = false;
    fs::path sequenceDirectory;
    fs::path frameTimingReplayPath;
    fs::path screenshotPath;
    fs::path frameTimingTracePath;
    fs::path presentationEventsPath;
    fs::path bufferDumpDirectory;
    float diagnosticSeconds = 0.0F;
    int swapInterval = 1;
    bool useEglWindowContext = false;
    bool scheduledPhaseClock = true;
    bool gpuTiming = false;
    bool diagnosticInputLock = false;
    bool fpsExplicit = false;
};

struct InteractiveFrameTimingSample {
    std::uint64_t frame = 0U;
    double loopStartMilliseconds = 0.0;
    double loopDeltaMilliseconds = 0.0;
    double renderMilliseconds = 0.0;
    double setupMilliseconds = 0.0;
    double sceneMilliseconds = 0.0;
    double bloomMilliseconds = 0.0;
    double screenshotMilliseconds = 0.0;
    double swapWaitMilliseconds = 0.0;
    double swapReturnMilliseconds = 0.0;
    double swapIntervalMilliseconds = 0.0;
    double pollMilliseconds = 0.0;
    double phaseDegrees = 0.0;
    double physicalPoseDeltaDegrees = 0.0;
    double filterDeltaDegrees = 0.0;
    double nominalHz = 0.0;
    double spinRps = 0.0;
    double grooveCyclesPerFrame = 0.0;
    double aliasEnvelopeCycles = 0.0;
    double temporalBlend = 0.0;
    double grooveContrast = 0.0;
    double bandBlend = 0.0;
    double motionBandEnergyWeight = 0.0;
    double bloomCorrectionBlend = 0.0;
    double coreIntensity = 0.0;
    int temporalSampleCount = 0;
    int emissionDrawCount = 0;
    int cadenceTitleUpdate = 0;
    int scheduledPhaseClock = 0;
    const char* modelSlug = "";
    TemporalMode requestedTemporalMode = TemporalMode::Sharp;
    TemporalMode effectiveTemporalMode = TemporalMode::Sharp;
    TemporalSource temporalSource = TemporalSource::SharpMesh;
    int temporalGroovesAvailable = 0;
    int mintGlowCount = 0;
    int scanoutValid = 0;
    std::uint32_t scanoutCounterBefore = 0U;
    std::uint32_t scanoutCounterAfter = 0U;
    std::uint32_t scanoutCounterDelta = 0U;
    double scanoutQueryBeforeMilliseconds = 0.0;
    double scanoutQueryAfterMilliseconds = 0.0;
};

std::string eglErrorText(const EGLint error) {
    std::ostringstream message;
    message << "0x" << std::hex << std::uppercase << error;
    return message.str();
}

class HeadlessEglContext {
public:
    HeadlessEglContext(const int width, const int height) {
        display_ = eglGetPlatformDisplay(
                EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, nullptr);
        if (display_ == EGL_NO_DISPLAY) {
            throw std::runtime_error(
                    "could not get a surfaceless EGL display (EGL "
                    + eglErrorText(eglGetError()) + ")");
        }

        EGLint eglMajor = 0;
        EGLint eglMinor = 0;
        if (eglInitialize(display_, &eglMajor, &eglMinor) != EGL_TRUE) {
            const EGLint error = eglGetError();
            eglTerminate(display_);
            display_ = EGL_NO_DISPLAY;
            throw std::runtime_error(
                    "could not initialize the surfaceless EGL display (EGL "
                    + eglErrorText(error) + ")");
        }
        if (eglBindAPI(EGL_OPENGL_ES_API) != EGL_TRUE) {
            const EGLint error = eglGetError();
            destroy();
            throw std::runtime_error(
                    "could not select the OpenGL ES EGL API (EGL "
                    + eglErrorText(error) + ")");
        }

        const EGLint configAttributes[] = {
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_DEPTH_SIZE, 16,
                EGL_NONE};
        EGLConfig config = nullptr;
        EGLint configCount = 0;
        if (eglChooseConfig(
                    display_, configAttributes, &config, 1, &configCount) != EGL_TRUE
                || configCount < 1 || config == nullptr) {
            const EGLint error = eglGetError();
            destroy();
            throw std::runtime_error(
                    "surfaceless EGL has no GLES 3 pbuffer configuration (EGL "
                    + eglErrorText(error) + ")");
        }

        const EGLint surfaceAttributes[] = {
                EGL_WIDTH, std::max(1, width),
                EGL_HEIGHT, std::max(1, height),
                EGL_NONE};
        surface_ = eglCreatePbufferSurface(display_, config, surfaceAttributes);
        if (surface_ == EGL_NO_SURFACE) {
            const EGLint error = eglGetError();
            destroy();
            throw std::runtime_error(
                    "could not create the headless EGL pbuffer (EGL "
                    + eglErrorText(error) + ")");
        }

        const EGLint contextAttributes[] = {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_NONE};
        context_ = eglCreateContext(
                display_, config, EGL_NO_CONTEXT, contextAttributes);
        if (context_ == EGL_NO_CONTEXT) {
            const EGLint error = eglGetError();
            destroy();
            throw std::runtime_error(
                    "could not create a headless OpenGL ES 3 context (EGL "
                    + eglErrorText(error) + ")");
        }
        if (eglMakeCurrent(display_, surface_, surface_, context_) != EGL_TRUE) {
            const EGLint error = eglGetError();
            destroy();
            throw std::runtime_error(
                    "could not make the headless EGL context current (EGL "
                    + eglErrorText(error) + ")");
        }

        GLint glMajor = 0;
        glGetIntegerv(GL_MAJOR_VERSION, &glMajor);
        if (glMajor < 3) {
            destroy();
            throw std::runtime_error(
                    "surfaceless EGL supplied OpenGL ES " + std::to_string(glMajor)
                    + ", but the lab requires OpenGL ES 3");
        }
        std::cout << "Headless context: surfaceless EGL " << eglMajor << '.' << eglMinor
                << " pbuffer " << std::max(1, width) << 'x' << std::max(1, height) << '\n';
    }

    HeadlessEglContext(const HeadlessEglContext&) = delete;
    HeadlessEglContext& operator=(const HeadlessEglContext&) = delete;
    ~HeadlessEglContext() { destroy(); }

private:
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLContext context_ = EGL_NO_CONTEXT;

    void destroy() {
        if (display_ == EGL_NO_DISPLAY) {
            return;
        }
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
            context_ = EGL_NO_CONTEXT;
        }
        if (surface_ != EGL_NO_SURFACE) {
            eglDestroySurface(display_, surface_);
            surface_ = EGL_NO_SURFACE;
        }
        eglTerminate(display_);
        display_ = EGL_NO_DISPLAY;
    }
};

std::string readTextFile(const fs::path& path) {
    std::ifstream input(path);
    if (!input) {
        throw std::runtime_error("could not open shader: " + path.string());
    }
    std::ostringstream contents;
    contents << input.rdbuf();
    return contents.str();
}

GLuint compileShader(const GLenum type, const fs::path& path) {
    const std::string source = readTextFile(path);
    const char* sourcePointer = source.c_str();
    const GLint sourceLength = static_cast<GLint>(source.size());
    const GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &sourcePointer, &sourceLength);
    glCompileShader(shader);
    GLint status = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (status == GL_TRUE) {
        return shader;
    }
    GLint logLength = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLength);
    std::string log(static_cast<std::size_t>(std::max(1, logLength)), '\0');
    glGetShaderInfoLog(shader, logLength, nullptr, log.data());
    glDeleteShader(shader);
    throw std::runtime_error("shader compilation failed for " + path.string() + ":\n" + log);
}

GLuint linkProgram(
        const fs::path& vertexPath,
        const fs::path& fragmentPath,
        const std::vector<std::pair<GLuint, std::string>>& attributes) {
    const GLuint vertex = compileShader(GL_VERTEX_SHADER, vertexPath);
    const GLuint fragment = compileShader(GL_FRAGMENT_SHADER, fragmentPath);
    const GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    for (const auto& [location, name] : attributes) {
        glBindAttribLocation(program, location, name.c_str());
    }
    glLinkProgram(program);
    glDeleteShader(vertex);
    glDeleteShader(fragment);

    GLint status = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &status);
    if (status == GL_TRUE) {
        return program;
    }
    GLint logLength = 0;
    glGetProgramiv(program, GL_INFO_LOG_LENGTH, &logLength);
    std::string log(static_cast<std::size_t>(std::max(1, logLength)), '\0');
    glGetProgramInfoLog(program, logLength, nullptr, log.data());
    glDeleteProgram(program);
    throw std::runtime_error("program link failed (" + vertexPath.string() + ", "
            + fragmentPath.string() + "):\n" + log);
}

GLint uniformLocation(const GLuint program, const char* name) {
    const GLint result = glGetUniformLocation(program, name);
    if (result < 0) {
        throw std::runtime_error(std::string("required shader uniform missing: ") + name);
    }
    return result;
}

void throwOnGlError(const char* context) {
    std::ostringstream errors;
    bool found = false;
    for (GLenum error = glGetError(); error != GL_NO_ERROR; error = glGetError()) {
        found = true;
        errors << " 0x" << std::hex << error;
    }
    if (found) {
        throw std::runtime_error(std::string("OpenGL ES error after ") + context + ":" + errors.str());
    }
}

struct LitProgram {
    GLuint id = 0;
    GLint mvp = -1;
    GLint model = -1;
    GLint color = -1;
    GLint lightPosition = -1;
    GLint lightColor = -1;
    GLint cameraPosition = -1;
    GLint ambient = -1;
    GLint diffuse = -1;
    GLint specular = -1;
    GLint shininess = -1;

    LitProgram() = default;
    LitProgram(const LitProgram&) = delete;
    LitProgram& operator=(const LitProgram&) = delete;
    LitProgram(LitProgram&& other) noexcept { *this = std::move(other); }
    LitProgram& operator=(LitProgram&& other) noexcept {
        if (this != &other) {
            destroy();
            id = std::exchange(other.id, 0U);
            mvp = other.mvp;
            model = other.model;
            color = other.color;
            lightPosition = other.lightPosition;
            lightColor = other.lightColor;
            cameraPosition = other.cameraPosition;
            ambient = other.ambient;
            diffuse = other.diffuse;
            specular = other.specular;
            shininess = other.shininess;
        }
        return *this;
    }
    ~LitProgram() { destroy(); }

    void load(const fs::path& vertex, const fs::path& fragment) {
        destroy();
        id = linkProgram(vertex, fragment, {{0U, "vPosition"}, {1U, "aNormal"}});
        mvp = uniformLocation(id, "uMVPMatrix");
        model = uniformLocation(id, "uModelMatrix");
        color = uniformLocation(id, "vColor");
        lightPosition = uniformLocation(id, "uLightPos");
        lightColor = uniformLocation(id, "uLightColor");
        cameraPosition = uniformLocation(id, "uCameraPos");
        ambient = uniformLocation(id, "uAmbient");
        diffuse = uniformLocation(id, "uDiffuse");
        specular = uniformLocation(id, "uSpecular");
        shininess = uniformLocation(id, "uShininess");
    }

    void destroy() {
        if (id != 0U) {
            glDeleteProgram(id);
            id = 0U;
        }
    }
};

struct LineProgram {
    GLuint id = 0;
    GLint mvp = -1;

    LineProgram() = default;
    LineProgram(const LineProgram&) = delete;
    LineProgram& operator=(const LineProgram&) = delete;
    ~LineProgram() { destroy(); }

    void load(const fs::path& shaderDirectory) {
        destroy();
        id = linkProgram(
                shaderDirectory / "line.vert", shaderDirectory / "line.frag",
                {{0U, "aPosition"}, {1U, "aColor"}});
        mvp = uniformLocation(id, "uMVPMatrix");
    }

    void destroy() {
        if (id != 0U) {
            glDeleteProgram(id);
            id = 0U;
        }
    }
};

struct HarmonicTreadProgram {
    GLuint id = 0;
    GLint mvp = -1;
    GLint color = -1;
    GLint rollPhase = -1;
    GLint aliasCycles = -1;
    GLint exposureCycles = -1;
    GLint trailingBox = -1;

    HarmonicTreadProgram() = default;
    HarmonicTreadProgram(const HarmonicTreadProgram&) = delete;
    HarmonicTreadProgram& operator=(const HarmonicTreadProgram&) = delete;
    ~HarmonicTreadProgram() { destroy(); }

    void load(const fs::path& shaderDirectory) {
        destroy();
        id = linkProgram(
                shaderDirectory / "harmonic_tread.vert",
                shaderDirectory / "harmonic_tread.frag",
                {{0U, "vPosition"}});
        mvp = uniformLocation(id, "uMVPMatrix");
        color = uniformLocation(id, "uColor");
        rollPhase = uniformLocation(id, "uRollPhaseRadians");
        aliasCycles = uniformLocation(id, "uGrooveCyclesPerFrame");
        exposureCycles = uniformLocation(id, "uGrooveCyclesDuringExposure");
        trailingBox = uniformLocation(id, "uTrailingBox");
    }

    void destroy() {
        if (id != 0U) {
            glDeleteProgram(id);
            id = 0U;
        }
    }
};

struct GpuMesh {
    GLuint vao = 0;
    GLuint vertexBuffer = 0;
    GLuint indexBuffer = 0;
    GLsizei indexCount = 0;

    GpuMesh() = default;
    GpuMesh(const GpuMesh&) = delete;
    GpuMesh& operator=(const GpuMesh&) = delete;
    GpuMesh(GpuMesh&& other) noexcept { *this = std::move(other); }
    GpuMesh& operator=(GpuMesh&& other) noexcept {
        if (this != &other) {
            destroy();
            vao = std::exchange(other.vao, 0U);
            vertexBuffer = std::exchange(other.vertexBuffer, 0U);
            indexBuffer = std::exchange(other.indexBuffer, 0U);
            indexCount = std::exchange(other.indexCount, 0);
        }
        return *this;
    }
    ~GpuMesh() { destroy(); }

    void upload(const CpuMesh& mesh) {
        destroy();
        indexCount = static_cast<GLsizei>(mesh.indices.size());
        glGenVertexArrays(1, &vao);
        glGenBuffers(1, &vertexBuffer);
        glGenBuffers(1, &indexBuffer);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(
                GL_ARRAY_BUFFER,
                static_cast<GLsizeiptr>(mesh.vertices.size() * sizeof(wheel_lab::Vertex)),
                mesh.vertices.data(), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(
                GL_ELEMENT_ARRAY_BUFFER,
                static_cast<GLsizeiptr>(mesh.indices.size() * sizeof(std::uint32_t)),
                mesh.indices.data(), GL_STATIC_DRAW);
        glEnableVertexAttribArray(0U);
        glVertexAttribPointer(
                0U, 3, GL_FLOAT, GL_FALSE, static_cast<GLsizei>(sizeof(wheel_lab::Vertex)),
                reinterpret_cast<const void*>(offsetof(wheel_lab::Vertex, position)));
        glEnableVertexAttribArray(1U);
        glVertexAttribPointer(
                1U, 3, GL_FLOAT, GL_FALSE, static_cast<GLsizei>(sizeof(wheel_lab::Vertex)),
                reinterpret_cast<const void*>(offsetof(wheel_lab::Vertex, normal)));
        glBindVertexArray(0U);
    }

    void draw() const {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, nullptr);
    }

    void destroy() {
        if (indexBuffer != 0U) {
            glDeleteBuffers(1, &indexBuffer);
            indexBuffer = 0U;
        }
        if (vertexBuffer != 0U) {
            glDeleteBuffers(1, &vertexBuffer);
            vertexBuffer = 0U;
        }
        if (vao != 0U) {
            glDeleteVertexArrays(1, &vao);
            vao = 0U;
        }
        indexCount = 0;
    }
};

struct LineVertex {
    glm::vec3 position{};
    glm::vec3 color{1.0F};
};

struct LineMesh {
    GLuint vao = 0;
    GLuint buffer = 0;
    GLsizei vertexCount = 0;

    LineMesh() = default;
    LineMesh(const LineMesh&) = delete;
    LineMesh& operator=(const LineMesh&) = delete;
    LineMesh(LineMesh&& other) noexcept { *this = std::move(other); }
    LineMesh& operator=(LineMesh&& other) noexcept {
        if (this != &other) {
            destroy();
            vao = std::exchange(other.vao, 0U);
            buffer = std::exchange(other.buffer, 0U);
            vertexCount = std::exchange(other.vertexCount, 0);
        }
        return *this;
    }
    ~LineMesh() { destroy(); }

    void upload(const std::vector<LineVertex>& vertices) {
        destroy();
        vertexCount = static_cast<GLsizei>(vertices.size());
        glGenVertexArrays(1, &vao);
        glGenBuffers(1, &buffer);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(
                GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(vertices.size() * sizeof(LineVertex)),
                vertices.data(), GL_STATIC_DRAW);
        glEnableVertexAttribArray(0U);
        glVertexAttribPointer(
                0U, 3, GL_FLOAT, GL_FALSE, static_cast<GLsizei>(sizeof(LineVertex)),
                reinterpret_cast<const void*>(offsetof(LineVertex, position)));
        glEnableVertexAttribArray(1U);
        glVertexAttribPointer(
                1U, 3, GL_FLOAT, GL_FALSE, static_cast<GLsizei>(sizeof(LineVertex)),
                reinterpret_cast<const void*>(offsetof(LineVertex, color)));
        glBindVertexArray(0U);
    }

    void draw() const {
        glBindVertexArray(vao);
        glDrawArrays(GL_LINES, 0, vertexCount);
    }

    void destroy() {
        if (buffer != 0U) {
            glDeleteBuffers(1, &buffer);
            buffer = 0U;
        }
        if (vao != 0U) {
            glDeleteVertexArrays(1, &vao);
            vao = 0U;
        }
        vertexCount = 0;
    }
};

struct GpuPart {
    std::string name;
    Material material;
    GpuMesh smooth;
    GpuMesh flat;
};

struct GpuModel {
    std::vector<GpuPart> parts;
    LineMesh wireframe;
    LineMesh smoothNormals;
    LineMesh flatNormals;
    LineMesh collisionAndBounds;
};

void appendLine(
        std::vector<LineVertex>& lines,
        const glm::vec3& from,
        const glm::vec3& to,
        const glm::vec3& color) {
    lines.push_back({from, color});
    lines.push_back({to, color});
}

std::vector<LineVertex> buildWireframe(const WheelModel& model) {
    std::vector<LineVertex> lines;
    for (const auto& part : model.parts) {
        const CpuMesh& mesh = part.smoothMesh;
        std::set<std::pair<std::uint32_t, std::uint32_t>> edges;
        for (std::size_t i = 0; i + 2 < mesh.indices.size(); i += 3) {
            for (int edge = 0; edge < 3; ++edge) {
                std::uint32_t a = mesh.indices[i + static_cast<std::size_t>(edge)];
                std::uint32_t b = mesh.indices[i + static_cast<std::size_t>((edge + 1) % 3)];
                if (a > b) {
                    std::swap(a, b);
                }
                edges.emplace(a, b);
            }
        }
        const glm::vec3 color = part.material.luminous
                ? glm::vec3(part.material.color)
                : glm::vec3(0.42F, 0.48F, 0.56F);
        for (const auto& [a, b] : edges) {
            appendLine(lines, mesh.vertices[a].position, mesh.vertices[b].position, color);
        }
    }
    return lines;
}

std::vector<LineVertex> buildNormals(const WheelModel& model, const bool flat) {
    std::vector<LineVertex> lines;
    const glm::vec3 color(1.0F, 0.72F, 0.12F);
    for (const auto& part : model.parts) {
        const CpuMesh& mesh = flat ? part.flatMesh : part.smoothMesh;
        const std::size_t desiredSamples = flat ? 800U : 500U;
        const std::size_t step = std::max<std::size_t>(1U, mesh.vertices.size() / desiredSamples);
        for (std::size_t i = 0; i < mesh.vertices.size(); i += step) {
            const auto& vertex = mesh.vertices[i];
            appendLine(lines, vertex.position, vertex.position + vertex.normal * 0.045F, color);
        }
    }
    return lines;
}

std::vector<LineVertex> buildCollisionAndBounds(const WheelModel& model) {
    std::vector<LineVertex> lines;
    const glm::vec3 min = model.boundsMin;
    const glm::vec3 max = model.boundsMax;
    const glm::vec3 boxColor(1.0F, 0.62F, 0.12F);
    const std::array<glm::vec3, 8> corners = {{
            {min.x, min.y, min.z}, {max.x, min.y, min.z},
            {max.x, max.y, min.z}, {min.x, max.y, min.z},
            {min.x, min.y, max.z}, {max.x, min.y, max.z},
            {max.x, max.y, max.z}, {min.x, max.y, max.z}}};
    constexpr std::array<std::pair<int, int>, 12> boxEdges = {{
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6},
            {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}}};
    for (const auto& [a, b] : boxEdges) {
        appendLine(lines, corners[static_cast<std::size_t>(a)],
                corners[static_cast<std::size_t>(b)], boxColor);
    }

    const glm::vec3 cylinderColor(0.12F, 0.84F, 1.0F);
    constexpr int segments = 48;
    for (const float x : {-kCollisionHalfWidth, kCollisionHalfWidth}) {
        for (int i = 0; i < segments; ++i) {
            const float a = glm::two_pi<float>() * static_cast<float>(i)
                    / static_cast<float>(segments);
            const float b = glm::two_pi<float>() * static_cast<float>(i + 1)
                    / static_cast<float>(segments);
            appendLine(lines,
                    {x, kCollisionRadius * std::cos(a), kCollisionRadius * std::sin(a)},
                    {x, kCollisionRadius * std::cos(b), kCollisionRadius * std::sin(b)},
                    cylinderColor);
        }
    }
    for (int i = 0; i < segments; i += 6) {
        const float angle = glm::two_pi<float>() * static_cast<float>(i)
                / static_cast<float>(segments);
        appendLine(lines,
                {-kCollisionHalfWidth, kCollisionRadius * std::cos(angle),
                        kCollisionRadius * std::sin(angle)},
                {kCollisionHalfWidth, kCollisionRadius * std::cos(angle),
                        kCollisionRadius * std::sin(angle)}, cylinderColor);
    }
    return lines;
}

std::vector<LineVertex> buildGrid() {
    std::vector<LineVertex> lines;
    constexpr int halfCount = 10;
    constexpr float spacing = 0.25F;
    constexpr float floorY = -0.525F;
    for (int i = -halfCount; i <= halfCount; ++i) {
        const float coordinate = static_cast<float>(i) * spacing;
        const glm::vec3 color = i == 0
                ? glm::vec3(0.25F, 0.30F, 0.38F)
                : glm::vec3(0.095F, 0.11F, 0.14F);
        appendLine(lines, {coordinate, floorY, -2.5F}, {coordinate, floorY, 2.5F}, color);
        appendLine(lines, {-2.5F, floorY, coordinate}, {2.5F, floorY, coordinate}, color);
    }
    appendLine(lines, {0.0F, 0.0F, 0.0F}, {0.75F, 0.0F, 0.0F}, {1.0F, 0.15F, 0.12F});
    appendLine(lines, {0.0F, 0.0F, 0.0F}, {0.0F, 0.75F, 0.0F}, {0.15F, 1.0F, 0.25F});
    appendLine(lines, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.75F}, {0.18F, 0.45F, 1.0F});
    return lines;
}

GpuModel uploadModel(const WheelModel& model) {
    GpuModel result;
    result.parts.reserve(model.parts.size());
    for (const auto& sourcePart : model.parts) {
        GpuPart gpuPart;
        gpuPart.name = sourcePart.name;
        gpuPart.material = sourcePart.material;
        gpuPart.smooth.upload(sourcePart.smoothMesh);
        gpuPart.flat.upload(sourcePart.flatMesh);
        result.parts.push_back(std::move(gpuPart));
    }
    result.wireframe.upload(buildWireframe(model));
    result.smoothNormals.upload(buildNormals(model, false));
    result.flatNormals.upload(buildNormals(model, true));
    result.collisionAndBounds.upload(buildCollisionAndBounds(model));
    return result;
}

struct RenderTarget {
    GLuint framebuffer = 0;
    GLuint texture = 0;
    GLuint depth = 0;
    int width = 0;
    int height = 0;

    RenderTarget() = default;
    RenderTarget(const RenderTarget&) = delete;
    RenderTarget& operator=(const RenderTarget&) = delete;
    ~RenderTarget() { destroy(); }

    void create(
            const int newWidth,
            const int newHeight,
            const bool withDepth,
            const bool halfFloatColor = false) {
        destroy();
        width = std::max(1, newWidth);
        height = std::max(1, newHeight);
        glGenFramebuffers(1, &framebuffer);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glGenTextures(1, &texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                halfFloatColor ? GL_RGBA16F : GL_RGBA,
                width,
                height,
                0,
                GL_RGBA,
                halfFloatColor ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE,
                nullptr);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (withDepth) {
            glGenRenderbuffers(1, &depth);
            glBindRenderbuffer(GL_RENDERBUFFER, depth);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, width, height);
            glFramebufferRenderbuffer(
                    GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
        }
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw std::runtime_error(
                    std::string("OpenGL ES framebuffer is incomplete")
                    + (halfFloatColor
                            ? "; temporal integration requires a renderable RGBA16F target"
                            : ""));
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0U);
        glBindTexture(GL_TEXTURE_2D, 0U);
        glBindRenderbuffer(GL_RENDERBUFFER, 0U);
    }

    void destroy() {
        if (depth != 0U) {
            glDeleteRenderbuffers(1, &depth);
            depth = 0U;
        }
        if (texture != 0U) {
            glDeleteTextures(1, &texture);
            texture = 0U;
        }
        if (framebuffer != 0U) {
            glDeleteFramebuffers(1, &framebuffer);
            framebuffer = 0U;
        }
    }
};

class PixelReadState {
public:
    PixelReadState() {
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &readFramebuffer_);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &drawFramebuffer_);
        glGetIntegerv(GL_READ_BUFFER, &readBuffer_);
        glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, &pixelPackBuffer_);
        glGetIntegerv(GL_PACK_ALIGNMENT, &packAlignment_);
        glGetIntegerv(GL_PACK_ROW_LENGTH, &packRowLength_);
        glGetIntegerv(GL_PACK_SKIP_ROWS, &packSkipRows_);
        glGetIntegerv(GL_PACK_SKIP_PIXELS, &packSkipPixels_);
    }

    PixelReadState(const PixelReadState&) = delete;
    PixelReadState& operator=(const PixelReadState&) = delete;

    ~PixelReadState() {
        glBindBuffer(GL_PIXEL_PACK_BUFFER, static_cast<GLuint>(pixelPackBuffer_));
        glPixelStorei(GL_PACK_ALIGNMENT, packAlignment_);
        glPixelStorei(GL_PACK_ROW_LENGTH, packRowLength_);
        glPixelStorei(GL_PACK_SKIP_ROWS, packSkipRows_);
        glPixelStorei(GL_PACK_SKIP_PIXELS, packSkipPixels_);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, static_cast<GLuint>(readFramebuffer_));
        glReadBuffer(static_cast<GLenum>(readBuffer_));
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, static_cast<GLuint>(drawFramebuffer_));
    }

    void prepare() const {
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0U);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
    }

private:
    GLint readFramebuffer_ = 0;
    GLint drawFramebuffer_ = 0;
    GLint readBuffer_ = GL_BACK;
    GLint pixelPackBuffer_ = 0;
    GLint packAlignment_ = 4;
    GLint packRowLength_ = 0;
    GLint packSkipRows_ = 0;
    GLint packSkipPixels_ = 0;
};

std::size_t rgbaComponentCount(const RenderTarget& target) {
    if (target.width <= 0 || target.height <= 0 || target.framebuffer == 0U) {
        throw std::runtime_error("diagnostic readback target is not initialized");
    }
    const std::size_t width = static_cast<std::size_t>(target.width);
    const std::size_t height = static_cast<std::size_t>(target.height);
    if (height > std::numeric_limits<std::size_t>::max() / width
            || width * height > std::numeric_limits<std::size_t>::max() / 4U) {
        throw std::overflow_error("diagnostic readback size overflows size_t");
    }
    return width * height * 4U;
}

std::vector<std::uint8_t> readTargetRgba8(const RenderTarget& target) {
    std::vector<std::uint8_t> pixels(rgbaComponentCount(target));
    glBindFramebuffer(GL_READ_FRAMEBUFFER, target.framebuffer);
    glReadBuffer(GL_COLOR_ATTACHMENT0);
    glReadPixels(0, 0, target.width, target.height, GL_RGBA,
            GL_UNSIGNED_BYTE, pixels.data());
    throwOnGlError("RGBA8 diagnostic target readback");
    return pixels;
}

std::vector<float> readTargetRgba32f(const RenderTarget& target) {
    std::vector<float> pixels(rgbaComponentCount(target));
    glBindFramebuffer(GL_READ_FRAMEBUFFER, target.framebuffer);
    glReadBuffer(GL_COLOR_ATTACHMENT0);
    glReadPixels(0, 0, target.width, target.height, GL_RGBA,
            GL_FLOAT, pixels.data());
    throwOnGlError("RGBA16F diagnostic target readback as float32");
    return pixels;
}

bool hostIsLittleEndian() {
    const std::uint16_t value = 1U;
    return *reinterpret_cast<const std::uint8_t*>(&value) == 1U;
}

template <typename Value>
void writeRawFile(const fs::path& path, const std::vector<Value>& values) {
    const std::size_t bytes = values.size() * sizeof(Value);
    if (bytes > static_cast<std::size_t>(
                    std::numeric_limits<std::streamsize>::max())) {
        throw std::overflow_error("diagnostic buffer is too large to write: "
                + path.string());
    }
    std::ofstream output(path, std::ios::binary);
    if (!output) {
        throw std::runtime_error("could not create diagnostic buffer: "
                + path.string());
    }
    output.write(reinterpret_cast<const char*>(values.data()),
            static_cast<std::streamsize>(bytes));
    if (!output) {
        throw std::runtime_error("could not write diagnostic buffer: "
                + path.string());
    }
}

template <typename Value>
std::uint64_t fnv1a64(const std::vector<Value>& values) {
    constexpr std::uint64_t offsetBasis = 14695981039346656037ULL;
    constexpr std::uint64_t prime = 1099511628211ULL;
    std::uint64_t result = offsetBasis;
    const auto* bytes = reinterpret_cast<const std::uint8_t*>(values.data());
    const std::size_t byteCount = values.size() * sizeof(Value);
    for (std::size_t index = 0; index < byteCount; ++index) {
        result ^= static_cast<std::uint64_t>(bytes[index]);
        result *= prime;
    }
    return result;
}

std::string hexadecimalHash(const std::uint64_t value) {
    std::ostringstream output;
    output << std::hex << std::setfill('0') << std::setw(16) << value;
    return output.str();
}

std::string jsonString(const std::string& value) {
    std::ostringstream output;
    output << '"';
    for (const unsigned char character : value) {
        switch (character) {
            case '"': output << "\\\""; break;
            case '\\': output << "\\\\"; break;
            case '\b': output << "\\b"; break;
            case '\f': output << "\\f"; break;
            case '\n': output << "\\n"; break;
            case '\r': output << "\\r"; break;
            case '\t': output << "\\t"; break;
            default:
                if (character < 0x20U) {
                    output << "\\u00" << std::hex << std::setfill('0')
                            << std::setw(2) << static_cast<unsigned int>(character)
                            << std::dec;
                } else {
                    output << static_cast<char>(character);
                }
                break;
        }
    }
    output << '"';
    return output.str();
}

struct FullscreenProgram {
    GLuint id = 0;
    GLint textureA = -1;
    GLint textureB = -1;
    GLint scalar = -1;
    GLint scalarB = -1;
    GLint scalarC = -1;
    GLint vector = -1;

    FullscreenProgram() = default;
    FullscreenProgram(const FullscreenProgram&) = delete;
    FullscreenProgram& operator=(const FullscreenProgram&) = delete;
    ~FullscreenProgram() { destroy(); }

    void destroy() {
        if (id != 0U) {
            glDeleteProgram(id);
            id = 0U;
        }
    }
};

struct BloomPipeline {
    int width = 0;
    int height = 0;
    RenderTarget scene;
    RenderTarget bloomA;
    RenderTarget bloomB;
    RenderTarget finalComposite;
    GLuint vao = 0;
    GLuint vertexBuffer = 0;
    GLuint indexBuffer = 0;
    FullscreenProgram prefilter;
    FullscreenProgram directEmission;
    FullscreenProgram bloomResidual;
    FullscreenProgram blur;
    FullscreenProgram composite;

    BloomPipeline() = default;
    BloomPipeline(const BloomPipeline&) = delete;
    BloomPipeline& operator=(const BloomPipeline&) = delete;
    ~BloomPipeline() { destroyQuad(); }

    void initialize(const fs::path& shaderDirectory) {
        prefilter.destroy();
        directEmission.destroy();
        bloomResidual.destroy();
        blur.destroy();
        composite.destroy();
        destroyQuad();
        const fs::path vertex = shaderDirectory / "fullscreen.vert";
        prefilter.id = linkProgram(
                vertex, shaderDirectory / "bloom_prefilter.frag",
                {{0U, "aPosition"}, {1U, "aUV"}});
        prefilter.textureA = uniformLocation(prefilter.id, "uSceneTex");
        prefilter.scalar = uniformLocation(prefilter.id, "uThreshold");
        prefilter.vector = uniformLocation(prefilter.id, "uSceneTexelStep");
        directEmission.id = linkProgram(
                vertex, shaderDirectory / "direct_emission.frag",
                {{0U, "aPosition"}, {1U, "aUV"}});
        directEmission.textureA = uniformLocation(directEmission.id, "uEmissionTex");
        directEmission.scalar = uniformLocation(directEmission.id, "uIntensity");
        bloomResidual.id = linkProgram(
                vertex, shaderDirectory / "temporal_bloom_residual.frag",
                {{0U, "aPosition"}, {1U, "aUV"}});
        bloomResidual.textureA = uniformLocation(
                bloomResidual.id, "uEmissionTex");
        bloomResidual.textureB = uniformLocation(
                bloomResidual.id, "uSceneTex");
        bloomResidual.vector = uniformLocation(
                bloomResidual.id, "uSceneTexelStep");
        bloomResidual.scalar = uniformLocation(
                bloomResidual.id, "uBloomThreshold");
        bloomResidual.scalarB = uniformLocation(
                bloomResidual.id, "uEmissionBrightFactor");
        bloomResidual.scalarC = uniformLocation(
                bloomResidual.id, "uBloomCorrectionBlend");
        blur.id = linkProgram(
                vertex, shaderDirectory / "bloom_blur.frag",
                {{0U, "aPosition"}, {1U, "aUV"}});
        blur.textureA = uniformLocation(blur.id, "uInputTex");
        blur.vector = uniformLocation(blur.id, "uTexelStep");
        composite.id = linkProgram(
                vertex, shaderDirectory / "bloom_composite.frag",
                {{0U, "aPosition"}, {1U, "aUV"}});
        composite.textureA = uniformLocation(composite.id, "uSceneTex");
        composite.textureB = uniformLocation(composite.id, "uBloomTex");
        composite.scalar = uniformLocation(composite.id, "uBloomIntensity");

        constexpr std::array<float, 20> vertices = {
                -1.0F, -1.0F, 0.0F, 0.0F, 0.0F,
                1.0F, -1.0F, 0.0F, 1.0F, 0.0F,
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
                -1.0F, 1.0F, 0.0F, 0.0F, 1.0F};
        constexpr std::array<std::uint16_t, 6> indices = {0U, 1U, 2U, 0U, 2U, 3U};
        glGenVertexArrays(1, &vao);
        glGenBuffers(1, &vertexBuffer);
        glGenBuffers(1, &indexBuffer);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(sizeof(vertices)),
                vertices.data(), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, static_cast<GLsizeiptr>(sizeof(indices)),
                indices.data(), GL_STATIC_DRAW);
        glEnableVertexAttribArray(0U);
        glVertexAttribPointer(0U, 3, GL_FLOAT, GL_FALSE, 5 * static_cast<GLsizei>(sizeof(float)),
                nullptr);
        glEnableVertexAttribArray(1U);
        glVertexAttribPointer(1U, 2, GL_FLOAT, GL_FALSE, 5 * static_cast<GLsizei>(sizeof(float)),
                reinterpret_cast<const void*>(3U * sizeof(float)));
        glBindVertexArray(0U);
    }

    void resize(const int newWidth, const int newHeight) {
        if (newWidth == width && newHeight == height) {
            return;
        }
        width = std::max(1, newWidth);
        height = std::max(1, newHeight);
        scene.create(width, height, true);
        const int bloomWidth = std::max(1, static_cast<int>(std::lround(
                static_cast<float>(width) * kBloomDownsample)));
        const int bloomHeight = std::max(1, static_cast<int>(std::lround(
                static_cast<float>(height) * kBloomDownsample)));
        bloomA.create(bloomWidth, bloomHeight, false);
        bloomB.create(bloomWidth, bloomHeight, false);
        finalComposite.create(width, height, false);
    }

    void beginScene() const {
        glBindFramebuffer(GL_FRAMEBUFFER, scene.framebuffer);
        glViewport(0, 0, scene.width, scene.height);
    }

    void compositeToScreen(
            const bool presentToDefaultFramebuffer,
            const GLuint directEmissionTexture = 0U,
            const float directCoreIntensity = 0.0F,
            const float directBloomCorrectionBlend = 0.0F) const {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(GL_FALSE);
        glDisable(GL_BLEND);
        glBindVertexArray(vao);

        if (directEmissionTexture != 0U && directCoreIntensity > 0.0F) {
            // The temporal target stores premultiplied radiance and weighted
            // alpha coverage. Insert that normalized exposure over the sharp
            // body exactly once, matching Android's GL_ONE /
            // GL_ONE_MINUS_SRC_ALPHA core composite. This deliberately occurs
            // before bright prefiltering so the resolved core participates in
            // ordinary bloom as scene color.
            glBindFramebuffer(GL_FRAMEBUFFER, scene.framebuffer);
            glViewport(0, 0, scene.width, scene.height);
            glEnable(GL_BLEND);
            glBlendEquation(GL_FUNC_ADD);
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glUseProgram(directEmission.id);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, directEmissionTexture);
            glUniform1i(directEmission.textureA, 0);
            glUniform1f(directEmission.scalar, directCoreIntensity);
            drawQuad();
            glDisable(GL_BLEND);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, bloomA.framebuffer);
        glViewport(0, 0, bloomA.width, bloomA.height);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(prefilter.id);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, scene.texture);
        glUniform1i(prefilter.textureA, 0);
        glUniform2f(prefilter.vector,
                1.0F / static_cast<float>(scene.width),
                1.0F / static_cast<float>(scene.height));
        glUniform1f(prefilter.scalar, kBloomThreshold);
        drawQuad();

        if (directEmissionTexture != 0U
                && directBloomCorrectionBlend > 0.0F) {
            // Ordinary scene bloom already contains extractBright(E). Add only
            // the missing per-pixel residual Q-O, where Q is the normalized
            // time-integrated emitter bright pass. Ramp that residual with
            // projected motion only, matching Android and preventing a hard
            // policy jump without coupling bloom to band-LOD hysteresis.
            glEnable(GL_BLEND);
            glBlendEquation(GL_FUNC_ADD);
            glBlendFunc(GL_ONE, GL_ONE);
            glUseProgram(bloomResidual.id);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, directEmissionTexture);
            glUniform1i(bloomResidual.textureA, 0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, scene.texture);
            glUniform1i(bloomResidual.textureB, 1);
            glUniform2f(
                    bloomResidual.vector,
                    1.0F / static_cast<float>(scene.width),
                    1.0F / static_cast<float>(scene.height));
            glUniform1f(bloomResidual.scalar, kBloomThreshold);
            glUniform1f(bloomResidual.scalarB, emissionBrightPassFactor());
            glUniform1f(
                    bloomResidual.scalarC,
                    std::clamp(directBloomCorrectionBlend, 0.0F, 1.0F));
            drawQuad();
            glDisable(GL_BLEND);
            glActiveTexture(GL_TEXTURE0);
        }

        for (int iteration = 0; iteration < kBloomIterations; ++iteration) {
            glBindFramebuffer(GL_FRAMEBUFFER, bloomB.framebuffer);
            glViewport(0, 0, bloomB.width, bloomB.height);
            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(blur.id);
            glBindTexture(GL_TEXTURE_2D, bloomA.texture);
            glUniform1i(blur.textureA, 0);
            glUniform2f(blur.vector,
                    kBloomTexelStepScale / static_cast<float>(bloomA.width), 0.0F);
            drawQuad();

            glBindFramebuffer(GL_FRAMEBUFFER, bloomA.framebuffer);
            glViewport(0, 0, bloomA.width, bloomA.height);
            glClear(GL_COLOR_BUFFER_BIT);
            glBindTexture(GL_TEXTURE_2D, bloomB.texture);
            glUniform2f(blur.vector, 0.0F,
                    kBloomTexelStepScale / static_cast<float>(bloomA.height));
            drawQuad();
        }

        glBindFramebuffer(GL_FRAMEBUFFER, finalComposite.framebuffer);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(composite.id);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, scene.texture);
        glUniform1i(composite.textureA, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, bloomA.texture);
        glUniform1i(composite.textureB, 1);
        glUniform1f(composite.scalar, kBloomIntensity);
        drawQuad();

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0U);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0U);
        glBindVertexArray(0U);

        // The final texture is the canonical capture source. Interactive mode
        // also presents it; a surfaceless smoke run deliberately avoids any
        // dependency on the pbuffer's default framebuffer.
        if (presentToDefaultFramebuffer) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, finalComposite.framebuffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0U);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL_COLOR_BUFFER_BIT, GL_NEAREST);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0U);
        glDepthMask(GL_TRUE);
        glEnable(GL_DEPTH_TEST);
    }

    void drawQuad() const {
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, nullptr);
    }

    void destroyQuad() {
        if (indexBuffer != 0U) {
            glDeleteBuffers(1, &indexBuffer);
            indexBuffer = 0U;
        }
        if (vertexBuffer != 0U) {
            glDeleteBuffers(1, &vertexBuffer);
            vertexBuffer = 0U;
        }
        if (vao != 0U) {
            glDeleteVertexArrays(1, &vao);
            vao = 0U;
        }
    }
};

// Integrates only the rotating groove emission. Each temporal sample receives
// its own freshly-cleared depth buffer. Production modes populate it with the
// current-pose carcass used by Android; the reference oracle uses the sampled
// carcass pose. The samples are then averaged before bloom is evaluated. Internal
// additive blending performs only the normalized weighted exposure sum; the
// resulting premultiplied RGB/coverage is source-over composited onto the body.
struct TemporalEmissionPipeline {
    int width = 0;
    int height = 0;
    RenderTarget sample;
    RenderTarget accumulation;
    GLuint vao = 0;
    GLuint vertexBuffer = 0;
    GLuint indexBuffer = 0;
    FullscreenProgram accumulate;

    TemporalEmissionPipeline() = default;
    TemporalEmissionPipeline(const TemporalEmissionPipeline&) = delete;
    TemporalEmissionPipeline& operator=(const TemporalEmissionPipeline&) = delete;
    ~TemporalEmissionPipeline() { destroy(); }

    void initialize(const fs::path& shaderDirectory) {
        destroy();
        accumulate.id = linkProgram(
                shaderDirectory / "fullscreen.vert",
                shaderDirectory / "temporal_accumulate.frag",
                {{0U, "aPosition"}, {1U, "aUV"}});
        accumulate.textureA = uniformLocation(accumulate.id, "uSampleTex");
        accumulate.scalar = uniformLocation(accumulate.id, "uSampleWeight");

        constexpr std::array<float, 20> vertices = {
                -1.0F, -1.0F, 0.0F, 0.0F, 0.0F,
                1.0F, -1.0F, 0.0F, 1.0F, 0.0F,
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
                -1.0F, 1.0F, 0.0F, 0.0F, 1.0F};
        constexpr std::array<std::uint16_t, 6> indices = {0U, 1U, 2U, 0U, 2U, 3U};
        glGenVertexArrays(1, &vao);
        glGenBuffers(1, &vertexBuffer);
        glGenBuffers(1, &indexBuffer);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(sizeof(vertices)),
                vertices.data(), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, static_cast<GLsizeiptr>(sizeof(indices)),
                indices.data(), GL_STATIC_DRAW);
        glEnableVertexAttribArray(0U);
        glVertexAttribPointer(0U, 3, GL_FLOAT, GL_FALSE,
                5 * static_cast<GLsizei>(sizeof(float)), nullptr);
        glEnableVertexAttribArray(1U);
        glVertexAttribPointer(1U, 2, GL_FLOAT, GL_FALSE,
                5 * static_cast<GLsizei>(sizeof(float)),
                reinterpret_cast<const void*>(3U * sizeof(float)));
        glBindVertexArray(0U);
    }

    void resize(const int newWidth, const int newHeight) {
        if (newWidth == width && newHeight == height) {
            return;
        }
        width = std::max(1, newWidth);
        height = std::max(1, newHeight);
        sample.create(width, height, true, true);
        accumulation.create(width, height, false, true);
    }

    void clearAccumulation() const {
        glBindFramebuffer(GL_FRAMEBUFFER, accumulation.framebuffer);
        glViewport(0, 0, width, height);
        glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    void beginSample() const {
        glBindFramebuffer(GL_FRAMEBUFFER, sample.framebuffer);
        glViewport(0, 0, width, height);
        glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    void accumulateSample(const float weight) const {
        drawTexture(
                accumulation.framebuffer,
                sample.texture,
                width,
                height,
                weight,
                BlendOperation::Additive);
    }

    void compositeOverTarget(
            const GLuint targetFramebuffer,
            const int targetWidth,
            const int targetHeight,
            const float intensity) const {
        drawTexture(
                targetFramebuffer,
                accumulation.texture,
                targetWidth,
                targetHeight,
                intensity,
                BlendOperation::PremultipliedSourceOver);
    }

    void destroy() {
        accumulate.destroy();
        if (indexBuffer != 0U) {
            glDeleteBuffers(1, &indexBuffer);
            indexBuffer = 0U;
        }
        if (vertexBuffer != 0U) {
            glDeleteBuffers(1, &vertexBuffer);
            vertexBuffer = 0U;
        }
        if (vao != 0U) {
            glDeleteVertexArrays(1, &vao);
            vao = 0U;
        }
    }

private:
    enum class BlendOperation {
        Additive,
        PremultipliedSourceOver,
    };

    void drawTexture(
            const GLuint targetFramebuffer,
            const GLuint sourceTexture,
            const int targetWidth,
            const int targetHeight,
            const float weight,
            const BlendOperation blendOperation) const {
        glBindFramebuffer(GL_FRAMEBUFFER, targetFramebuffer);
        glViewport(0, 0, targetWidth, targetHeight);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(GL_FALSE);
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFunc(
                GL_ONE,
                blendOperation == BlendOperation::PremultipliedSourceOver
                        ? GL_ONE_MINUS_SRC_ALPHA
                        : GL_ONE);
        glUseProgram(accumulate.id);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        glUniform1i(accumulate.textureA, 0);
        glUniform1f(accumulate.scalar, weight);
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, nullptr);
        glBindVertexArray(0U);
        glBindTexture(GL_TEXTURE_2D, 0U);
        glDisable(GL_BLEND);
        glDepthMask(GL_TRUE);
        glEnable(GL_DEPTH_TEST);
    }
};

struct Camera {
    float yaw = 0.62F;
    float pitch = 0.22F;
    float distance = 2.75F;
    float nearClip = 0.03F;
    float farClip = 100.0F;
    glm::vec3 target{0.0F, 0.03F, 0.0F};
    bool orthographic = false;

    glm::vec3 position() const {
        const float horizontal = std::cos(pitch) * distance;
        return target + glm::vec3(
                std::sin(yaw) * horizontal,
                std::sin(pitch) * distance,
                std::cos(yaw) * horizontal);
    }

    glm::mat4 view() const {
        return glm::lookAt(position(), target, {0.0F, 1.0F, 0.0F});
    }

    glm::mat4 projection(const float aspect) const {
        if (orthographic) {
            const float halfHeight = distance * 0.34F;
            return glm::ortho(-halfHeight * aspect, halfHeight * aspect,
                    -halfHeight, halfHeight, 0.01F, 100.0F);
        }
        return glm::perspective(
                glm::radians(kGameVerticalFovDegrees), aspect, nearClip, farClip);
    }
};

class Application {
public:
    struct RenderTimingBreakdown {
        double setupMilliseconds = 0.0;
        double sceneMilliseconds = 0.0;
        double bloomMilliseconds = 0.0;
    };

    Application(
            GLFWwindow* appWindow,
            std::vector<WheelModel> cpuModels,
            const int initialModel,
            const Options& options,
            const int initialFramebufferWidth,
            const int initialFramebufferHeight)
        : window_(appWindow),
          gpuTimer_(options.gpuTiming),
          models_(std::move(cpuModels)),
          selectedModel_(initialModel),
          framebufferWidth_(std::max(1, initialFramebufferWidth)),
          framebufferHeight_(std::max(1, initialFramebufferHeight)),
          bloomEnabled_(options.bloom),
          autoRoll_(options.autoRoll || !options.frameTimingReplayPath.empty()),
          fixedSpinPhase_(options.fixedSpinPhase),
          diagnosticInputLock_(options.diagnosticInputLock),
          spinRps_(options.spinRps),
          temporalMode_(options.temporalMode),
          mintGlowCount_(options.mintGlowCount),
          presentedFramesPerSecond_(options.presentedFramesPerSecond),
          nominalPresentedFramesPerSecond_(options.presentedFramesPerSecond),
          nominalCadenceLocked_(options.fpsExplicit),
          presentationCadenceDivisor_(std::max(1, options.swapInterval)),
          scheduledPhaseClock_(options.scheduledPhaseClock),
          timingReplay_(!options.frameTimingReplayPath.empty()),
          maxRollStepRadians_(glm::radians(options.maxRollStepDegrees)),
          modelRoll_(glm::radians(options.spinPhaseDegrees)),
          unwrappedModelRoll_(glm::radians(
                  static_cast<double>(options.spinPhaseDegrees))),
          forceConfiguredPresentedDelta_(
                  options.smokeTest || options.sequenceFixedPhase),
          lastPlanningIntervalSeconds_(
                  1.0 / static_cast<double>(options.presentedFramesPerSecond)),
          scheduledPresentationIntervalSeconds_(
                  1.0 / static_cast<double>(options.presentedFramesPerSecond)) {
        loadAllShaders();
        grid_.upload(buildGrid());
        gpuModels_.reserve(models_.size());
        for (const auto& model : models_) {
            gpuModels_.push_back(uploadModel(model));
        }
        if (window_ != nullptr) {
            glfwSetWindowUserPointer(window_, this);
            glfwSetFramebufferSizeCallback(window_, framebufferSizeCallback);
            glfwSetWindowPosCallback(window_, windowPositionCallback);
            glfwSetWindowSizeCallback(window_, windowSizeCallback);
            glfwSetCursorPosCallback(window_, cursorPositionCallback);
            glfwSetMouseButtonCallback(window_, mouseButtonCallback);
            glfwSetScrollCallback(window_, scrollCallback);
            glfwSetKeyCallback(window_, keyCallback);
            refreshNominalPresentationCadence();
        }
        updateTitle();
    }

    void render(const double deltaSeconds) {
        gpuTimer_.beginFrame(gpuFrameIndex_++);
        const auto timingStart = std::chrono::steady_clock::now();
        const bool liveInteractive = window_ != nullptr;
        const bool timedPresentation = liveInteractive || timingReplay_;
        const double configuredInterval =
                1.0 / static_cast<double>(presentedFramesPerSecond_);
        const double measuredInteractiveInterval = timedPresentation
                ? sanitizeInteractiveInterval(deltaSeconds, configuredInterval)
                : configuredInterval;
        const double presentationInterval = timedPresentation
                ? phaseClockInterval(
                        measuredInteractiveInterval,
                        1.0 / static_cast<double>(
                                nominalPresentedFramesPerSecond_))
                : configuredInterval;
        double presentedRollDeltaRadians = 0.0;
        if (autoRoll_ && !fixedSpinPhase_) {
            presentedRollDeltaRadians = presentationInterval
                    * static_cast<double>(spinRps_)
                    * glm::two_pi<double>();
            unwrappedModelRoll_ += presentedRollDeltaRadians;
            modelRoll_ = static_cast<float>(std::remainder(
                    unwrappedModelRoll_, glm::two_pi<double>()));
        } else if (forceConfiguredPresentedDelta_ && fixedSpinPhase_) {
            // Exact-pose smoke/sweep captures intentionally freeze phase while
            // still asking how this frame's configured motion would present.
            presentedRollDeltaRadians = static_cast<double>(spinRps_)
                    * glm::two_pi<double>() * configuredInterval;
        } else if (externalPhaseUpdatePending_) {
            presentedRollDeltaRadians = pendingPresentedRollDeltaRadians_;
        }
        externalPhaseUpdatePending_ = false;
        pendingPresentedRollDeltaRadians_ = 0.0;
        lastPhysicalPresentedRollDeltaRadians_ = presentedRollDeltaRadians;
        lastPlanningIntervalSeconds_ = presentationInterval;
        lastCadenceLabelChanged_ = liveInteractive
                && updateInteractiveCadenceLabel(measuredInteractiveInterval);
        // framebufferWidth_/framebufferHeight_ are initialized at window
        // creation and maintained by framebufferSizeCallback. Querying them
        // synchronously here adds an avoidable X11 round trip and can absorb
        // unrelated window-server stalls into renderer setup timing.
        framebufferWidth_ = std::max(1, framebufferWidth_);
        framebufferHeight_ = std::max(1, framebufferHeight_);
        bloom_.resize(framebufferWidth_, framebufferHeight_);
        const double presentedSpinRps = presentedRollDeltaRadians
                / presentationInterval / glm::two_pi<double>();
        lastTemporalGroovesAvailable_ = hasTemporalGroovePart();
        TemporalMode plannedTemporalMode = lastTemporalGroovesAvailable_
                ? temporalMode_
                : TemporalMode::Sharp;
        if (usesAliasSafeBand(plannedTemporalMode)
                && !hasMotionBandPart()) {
            // Older/isolated meshes can still use a truthful physical shutter,
            // but must never fade toward a representation they do not contain.
            plannedTemporalMode = plannedTemporalMode == TemporalMode::FrameSplit
                    ? TemporalMode::FrameSplitRaw
                    : TemporalMode::AdaptiveRaw;
        }
        const bool harmonicTread = usesHarmonicTreadShell(
                plannedTemporalMode);
        const double nominalFilterInterval = timedPresentation
                ? 1.0 / static_cast<double>(
                        nominalPresentedFramesPerSecond_)
                : configuredInterval;
        float planningFramesPerSecond = static_cast<float>(
                1.0 / presentationInterval);
        float planningRollDelta = static_cast<float>(
                presentedRollDeltaRadians);
        if (harmonicTread) {
            // Center phase follows the selected live/replay phase clock, while
            // the shutter and harmonic cutoff use stable nominal cadence.
            // Otherwise ordinary swap jitter changes the representation even
            // when physical RPS is perfectly constant.
            const double nominalRollDelta = presentedSpinRps
                    * glm::two_pi<double>() * nominalFilterInterval;
            const float targetCycles = static_cast<float>(
                    std::abs(nominalRollDelta)
                    / static_cast<double>(kMintGroovePitchRadians));
            // Representation is a property of physical speed and nominal
            // display cadence. Letting a single delayed CPU/swap callback
            // drive this value erased the grooves and then faded them back for
            // ~50 ms, visually amplifying every unrelated scheduling hitch.
            cleanAliasEnvelopeCycles_ = targetCycles;
            double directionSource = nominalRollDelta;
            if (directionSource == 0.0) {
                directionSource = presentedRollDeltaRadians;
            }
            planningRollDelta = std::copysign(
                    cleanAliasEnvelopeCycles_
                            * kMintGroovePitchRadians,
                    static_cast<float>(
                            directionSource == 0.0
                                    ? 1.0
                                    : directionSource));
            planningFramesPerSecond = static_cast<float>(
                    1.0 / nominalFilterInterval);
        } else {
            cleanAliasEnvelopeCycles_ = 0.0F;
        }
        lastTemporalPlan_ = temporalPlanner_.plan(
                plannedTemporalMode,
                modelRoll_,
                static_cast<float>(presentedSpinRps),
                planningFramesPerSecond,
                projectedRadiusPixels(),
                planningRollDelta,
                maxRollStepRadians_,
                timedPresentation
                        ? kMaxInteractiveFrameSplitSamples
                        : kMaxOfflineFrameSplitSamples,
                timedPresentation);
        if (lastTemporalPlan_.sampleCount != titleSampleCount_
                || lastCadenceLabelChanged_) {
            titleSampleCount_ = lastTemporalPlan_.sampleCount;
            updateTitle();
        }
        const bool temporalFrame = lastTemporalPlan_.temporalBlend
                > kTemporalActivationEpsilon;
        if (temporalFrame) {
            temporal_.resize(framebufferWidth_, framebufferHeight_);
        }
        gpuTimer_.markSceneStart();
        const auto sceneStart = std::chrono::steady_clock::now();

        if (temporalFrame) {
            renderTemporalFrame(lastTemporalPlan_);
        } else {
            bindFinalSceneTarget();
            clearScene(kBackground, 1.0F);
            if (usesAliasSafeBand(lastTemporalPlan_.mode)) {
                drawAliasSafeSceneAtPhase(modelRoll_, 1.0F);
            } else {
                drawSceneAtPhase(modelRoll_, 1.0F);
            }
        }
        gpuTimer_.markBloomStart();
        const auto bloomStart = std::chrono::steady_clock::now();

        if (bloomEnabled_) {
            bloom_.compositeToScreen(
                    window_ != nullptr,
                    temporalFrame ? temporal_.accumulation.texture : 0U,
                    temporalFrame ? lastTemporalPlan_.coreIntensity : 0.0F,
                    temporalFrame
                            ? lastTemporalPlan_.bloomCorrectionBlend
                            : 0.0F);
        }
        gpuTimer_.endFrame();
        const auto timingEnd = std::chrono::steady_clock::now();
        const auto milliseconds = [](const auto duration) {
            return std::chrono::duration<double, std::milli>(duration).count();
        };
        lastRenderTiming_.setupMilliseconds = milliseconds(
                sceneStart - timingStart);
        lastRenderTiming_.sceneMilliseconds = milliseconds(
                bloomStart - sceneStart);
        lastRenderTiming_.bloomMilliseconds = milliseconds(
                timingEnd - bloomStart);
        glBindVertexArray(0U);
        glUseProgram(0U);
    }

    void setSpinPhaseRadians(const double phaseRadians) {
        pendingPresentedRollDeltaRadians_ = phaseRadians - unwrappedModelRoll_;
        externalPhaseUpdatePending_ = true;
        unwrappedModelRoll_ = phaseRadians;
        modelRoll_ = static_cast<float>(std::remainder(
                phaseRadians, glm::two_pi<double>()));
    }

    void setSpinRps(const float spinRps) {
        spinRps_ = spinRps;
    }

    const TemporalPlan& lastTemporalPlan() const { return lastTemporalPlan_; }
    float spinRps() const { return spinRps_; }
    float nominalPresentedFramesPerSecond() const {
        return nominalPresentedFramesPerSecond_;
    }
    float cleanAliasEnvelopeCycles() const {
        return cleanAliasEnvelopeCycles_;
    }
    bool lastCadenceLabelChanged() const {
        return lastCadenceLabelChanged_;
    }
    const RenderTimingBreakdown& lastRenderTiming() const {
        return lastRenderTiming_;
    }
    void finalizeGpuTimings() { gpuTimer_.finalize(); }
    const GpuTimingResult& gpuTiming(const std::uint64_t frame) const {
        return gpuTimer_.result(frame);
    }
    double lastPhysicalPresentedRollDeltaRadians() const {
        return lastPhysicalPresentedRollDeltaRadians_;
    }
    const std::string& selectedModelSlug() const {
        return models_.at(static_cast<std::size_t>(selectedModel_)).slug;
    }
    const std::string& selectedModelName() const {
        return models_.at(static_cast<std::size_t>(selectedModel_)).name;
    }
    std::string selectedModelOptionName() const {
        if (selectedModelSlug() == "mint-wheel") return "mint";
        if (selectedModelSlug() == "violet-wheel") return "violet";
        return selectedModelSlug();
    }
    TemporalMode requestedTemporalMode() const { return temporalMode_; }
    bool lastTemporalGroovesAvailable() const {
        return lastTemporalGroovesAvailable_;
    }
    int mintGlowCount() const { return mintGlowCount_; }
    TemporalSource lastTemporalSource() const {
        if (lastTemporalPlan_.temporalBlend <= kTemporalActivationEpsilon) {
            return TemporalSource::SharpMesh;
        }
        if (usesHarmonicTreadShell(lastTemporalPlan_.mode)) {
            return TemporalSource::HarmonicShell;
        }
        const bool aliasSafe = usesAliasSafeBand(lastTemporalPlan_.mode);
        const float physicalWeight = aliasSafe
                ? lastTemporalPlan_.grooveContrast
                : 1.0F;
        const bool physical = lastTemporalPlan_.sampleCount > 0
                && physicalWeight > kTemporalActivationEpsilon;
        const bool band = aliasSafe
                && lastTemporalPlan_.motionBandEnergyWeight
                        > kTemporalActivationEpsilon;
        if (physical && band) return TemporalSource::PhysicalPlusBand;
        if (physical) return TemporalSource::PhysicalSamples;
        if (band) return TemporalSource::MotionBand;
        return TemporalSource::Empty;
    }

    void capture(const fs::path& path, const bool announce = true) const {
        if (!path.parent_path().empty()) {
            fs::create_directories(path.parent_path());
        }
        std::vector<std::uint8_t> pixels(
                static_cast<std::size_t>(framebufferWidth_ * framebufferHeight_ * 4));
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        if (bloomEnabled_) {
            glBindFramebuffer(GL_FRAMEBUFFER, bloom_.finalComposite.framebuffer);
        }
        glReadPixels(0, 0, framebufferWidth_, framebufferHeight_, GL_RGBA,
                GL_UNSIGNED_BYTE, pixels.data());
        glBindFramebuffer(GL_FRAMEBUFFER, 0U);
        std::ofstream output(path, std::ios::binary);
        if (!output) {
            throw std::runtime_error("could not write screenshot: " + path.string());
        }
        output << "P6\n" << framebufferWidth_ << ' ' << framebufferHeight_ << "\n255\n";
        std::vector<std::uint8_t> rgbRow(static_cast<std::size_t>(framebufferWidth_ * 3));
        for (int y = framebufferHeight_ - 1; y >= 0; --y) {
            const std::size_t sourceRow = static_cast<std::size_t>(y * framebufferWidth_ * 4);
            for (int x = 0; x < framebufferWidth_; ++x) {
                const std::size_t source = sourceRow + static_cast<std::size_t>(x * 4);
                const std::size_t destination = static_cast<std::size_t>(x * 3);
                rgbRow[destination] = pixels[source];
                rgbRow[destination + 1U] = pixels[source + 1U];
                rgbRow[destination + 2U] = pixels[source + 2U];
            }
            output.write(reinterpret_cast<const char*>(rgbRow.data()),
                    static_cast<std::streamsize>(rgbRow.size()));
        }
        std::size_t visiblePixels = 0;
        const glm::ivec3 backgroundBytes(
                static_cast<int>(kBackground.r * 255.0F),
                static_cast<int>(kBackground.g * 255.0F),
                static_cast<int>(kBackground.b * 255.0F));
        for (std::size_t i = 0; i + 3 < pixels.size(); i += 4) {
            const int delta = std::abs(static_cast<int>(pixels[i]) - backgroundBytes.r)
                    + std::abs(static_cast<int>(pixels[i + 1]) - backgroundBytes.g)
                    + std::abs(static_cast<int>(pixels[i + 2]) - backgroundBytes.b);
            if (delta > 24) {
                ++visiblePixels;
            }
        }
        if (announce) {
            std::cout << "Screenshot: " << path << " (" << visiblePixels
                    << " non-background pixels)\n";
        }
        const std::size_t pixelCount = pixels.size() / 4U;
        // The gameplay-distance preset intentionally occupies about 0.6% of a
        // 1180x820 capture. Grid and analysis overlays are disabled in smoke
        // mode, so a 0.25% floor still proves that the solid model rendered.
        if (visiblePixels < pixelCount / 400U) {
            throw std::runtime_error("smoke render contains too few visible pixels");
        }
    }

    void beginDiagnosticCapture(
            const fs::path& directory,
            const std::size_t expectedFrameCount) {
        if (directory.empty()) {
            throw std::invalid_argument("diagnostic buffer directory is empty");
        }
        if (!hostIsLittleEndian()) {
            throw std::runtime_error(
                    "diagnostic raw buffers currently require a little-endian host");
        }
        if (fs::exists(directory)) {
            if (!fs::is_directory(directory)) {
                throw std::invalid_argument(
                        "diagnostic buffer destination is not a directory: "
                        + directory.string());
            }
            if (!fs::is_empty(directory)) {
                throw std::invalid_argument(
                        "diagnostic buffer directory must be empty: "
                        + directory.string());
            }
        } else {
            fs::create_directories(directory);
        }
        diagnosticCaptureDirectory_ = directory;
        diagnosticExpectedFrameCount_ = expectedFrameCount;
        diagnosticFrames_.open(directory / "frames.tsv", std::ios::trunc);
        if (!diagnosticFrames_) {
            throw std::runtime_error(
                    "could not create diagnostic frames manifest in "
                    + directory.string());
        }
        diagnosticFrames_
                << "frame\tmodel_slug\tmint_glow_count"
                << "\trequested_temporal_mode\teffective_temporal_mode"
                << "\ttemporal_source\ttemporal_grooves_available"
                << "\ttemporal_active\temission_available\tphase_degrees"
                << "\tphysical_pose_delta_degrees\tfilter_delta_degrees"
                << "\tnominal_hz\tspin_rps\tgroove_cycles_per_frame"
                << "\tgroove_contrast\tband_blend"
                << "\tmotion_band_energy_weight\tcore_intensity"
                << "\tbloom_correction_blend"
                << "\ttemporal_sample_count\tfinal_hash\tscene_hash"
                << "\tbloom_hash\temission_hash\n";
        diagnosticDescriptorWritten_ = false;
    }

    void captureDiagnosticBundle(const std::uint64_t frameIndex) {
        if (diagnosticCaptureDirectory_.empty() || !diagnosticFrames_) {
            throw std::logic_error(
                    "beginDiagnosticCapture must precede diagnostic readback");
        }
        if (!bloomEnabled_) {
            throw std::logic_error(
                    "diagnostic buffers require bloom's canonical offscreen targets");
        }

        const bool temporalActive = lastTemporalPlan_.temporalBlend
                > kTemporalActivationEpsilon;
        std::ostringstream frameName;
        frameName << "frame-" << std::setw(5) << std::setfill('0') << frameIndex;
        const fs::path frameDirectory =
                diagnosticCaptureDirectory_ / frameName.str();
        if (fs::exists(frameDirectory) && !fs::is_empty(frameDirectory)) {
            throw std::runtime_error(
                    "diagnostic frame directory already contains data: "
                    + frameDirectory.string());
        }
        fs::create_directories(frameDirectory);

        std::vector<std::uint8_t> finalPixels;
        std::vector<std::uint8_t> scenePixels;
        std::vector<std::uint8_t> bloomPixels;
        std::optional<std::vector<float>> emissionPixels;
        {
            throwOnGlError("before diagnostic buffer readback");
            PixelReadState readState;
            readState.prepare();
            finalPixels = readTargetRgba8(bloom_.finalComposite);
            scenePixels = readTargetRgba8(bloom_.scene);
            bloomPixels = readTargetRgba8(bloom_.bloomA);
            if (temporalActive) {
                emissionPixels = readTargetRgba32f(temporal_.accumulation);
            }
        }
        throwOnGlError("after restoring diagnostic readback state");

        const fs::path finalPath = frameDirectory / "final.rgba8";
        const fs::path scenePath = frameDirectory / "scene.rgba8";
        const fs::path bloomPath = frameDirectory / "bloom.rgba8";
        const fs::path emissionPath = frameDirectory / "emission.rgba32f";
        writeRawFile(finalPath, finalPixels);
        writeRawFile(scenePath, scenePixels);
        writeRawFile(bloomPath, bloomPixels);
        if (emissionPixels.has_value()) {
            writeRawFile(emissionPath, *emissionPixels);
        }

        const std::string finalHash = hexadecimalHash(fnv1a64(finalPixels));
        const std::string sceneHash = hexadecimalHash(fnv1a64(scenePixels));
        const std::string bloomHash = hexadecimalHash(fnv1a64(bloomPixels));
        const std::string emissionHash = emissionPixels.has_value()
                ? hexadecimalHash(fnv1a64(*emissionPixels))
                : std::string();
        const TemporalSource source = lastTemporalSource();

        if (!diagnosticDescriptorWritten_) {
            std::ofstream descriptor(
                    diagnosticCaptureDirectory_ / "capture.json",
                    std::ios::trunc);
            if (!descriptor) {
                throw std::runtime_error(
                        "could not create diagnostic capture descriptor");
            }
            descriptor << std::setprecision(12)
                    << "{\n"
                    << "  \"schema\": \"wheel-render-truth-v1\",\n"
                    << "  \"schema_version\": 1,\n"
                    << "  \"layout\": \"frame-directories-v1\",\n"
                    << "  \"frame_count\": "
                    << diagnosticExpectedFrameCount_ << ",\n"
                    << "  \"width\": " << framebufferWidth_ << ",\n"
                    << "  \"height\": " << framebufferHeight_ << ",\n"
                    << "  \"requested_model\": "
                    << jsonString(selectedModelOptionName()) << ",\n"
                    << "  \"effective_model\": "
                    << jsonString(selectedModelOptionName()) << ",\n"
                    << "  \"model_slug\": " << jsonString(selectedModelSlug()) << ",\n"
                    << "  \"model_name\": " << jsonString(selectedModelName()) << ",\n"
                    << "  \"mint_glow_count\": " << mintGlowCount_ << ",\n"
                    << "  \"requested_temporal_mode\": "
                    << jsonString(temporalModeName(temporalMode_)) << ",\n"
                    << "  \"effective_temporal_mode\": "
                    << jsonString(temporalModeName(lastTemporalPlan_.mode)) << ",\n"
                    << "  \"origin\": \"bottom-left\",\n"
                    << "  \"byte_order\": \"little-endian\",\n"
                    << "  \"channels\": \"RGBA\",\n"
                    << "  \"stages\": {\n"
                    << "    \"final\": {\"dtype\": \"uint8\", \"width\": "
                    << bloom_.finalComposite.width << ", \"height\": "
                    << bloom_.finalComposite.height
                    << ", \"file_pattern\": \"frame-%05d/final.rgba8\"},\n"
                    << "    \"scene\": {\"dtype\": \"uint8\", \"width\": "
                    << bloom_.scene.width << ", \"height\": "
                    << bloom_.scene.height
                    << ", \"file_pattern\": \"frame-%05d/scene.rgba8\"},\n"
                    << "    \"bloom\": {\"dtype\": \"uint8\", \"width\": "
                    << bloom_.bloomA.width << ", \"height\": "
                    << bloom_.bloomA.height
                    << ", \"file_pattern\": \"frame-%05d/bloom.rgba8\"},\n"
                    << "    \"emission\": {\"dtype\": \"float32\", \"width\": "
                    << framebufferWidth_ << ", \"height\": "
                    << framebufferHeight_
                    << ", \"file_pattern\": \"frame-%05d/emission.rgba32f\""
                    << ", \"meaning\": \"premultiplied temporally integrated groove RGB and weighted alpha coverage; absent on sharp frames\"}\n"
                    << "  },\n"
                    << "  \"timing_warning\": \"Synchronous GPU readback; this capture is not a live cadence measurement.\"\n"
                    << "}\n";
            diagnosticDescriptorWritten_ = true;
        }

        std::ofstream metadata(frameDirectory / "frame.json", std::ios::trunc);
        if (!metadata) {
            throw std::runtime_error("could not create diagnostic frame metadata");
        }
        metadata << std::setprecision(12)
                << "{\n"
                << "  \"schema_version\": 1,\n"
                << "  \"frame\": " << frameIndex << ",\n"
                << "  \"model_slug\": " << jsonString(selectedModelSlug()) << ",\n"
                << "  \"model_name\": " << jsonString(selectedModelName()) << ",\n"
                << "  \"requested_model\": "
                << jsonString(selectedModelOptionName()) << ",\n"
                << "  \"effective_model\": "
                << jsonString(selectedModelOptionName()) << ",\n"
                << "  \"mint_glow_count\": " << mintGlowCount_ << ",\n"
                << "  \"requested_temporal_mode\": "
                << jsonString(temporalModeName(temporalMode_)) << ",\n"
                << "  \"effective_temporal_mode\": "
                << jsonString(temporalModeName(lastTemporalPlan_.mode)) << ",\n"
                << "  \"temporal_source\": "
                << jsonString(temporalSourceName(source)) << ",\n"
                << "  \"temporal_grooves_available\": "
                << (lastTemporalGroovesAvailable_ ? "true" : "false") << ",\n"
                << "  \"temporal_active\": "
                << (temporalActive ? "true" : "false") << ",\n"
                << "  \"emission_available\": "
                << (emissionPixels.has_value() ? "true" : "false") << ",\n"
                << "  \"phase_degrees\": "
                << glm::degrees(lastTemporalPlan_.centerPhaseRadians) << ",\n"
                << "  \"physical_pose_delta_degrees\": "
                << glm::degrees(lastPhysicalPresentedRollDeltaRadians_) << ",\n"
                << "  \"filter_delta_degrees\": "
                << glm::degrees(lastTemporalPlan_.presentedRollDeltaRadians) << ",\n"
                << "  \"nominal_hz\": "
                << nominalPresentedFramesPerSecond_ << ",\n"
                << "  \"spin_rps\": " << spinRps_ << ",\n"
                << "  \"groove_cycles_per_frame\": "
                << lastTemporalPlan_.grooveCyclesPerFrame << ",\n"
                << "  \"groove_contrast\": "
                << lastTemporalPlan_.grooveContrast << ",\n"
                << "  \"motion_band_energy_weight\": "
                << lastTemporalPlan_.motionBandEnergyWeight << ",\n"
                << "  \"core_intensity\": "
                << lastTemporalPlan_.coreIntensity << ",\n"
                << "  \"bloom_correction_blend\": "
                << lastTemporalPlan_.bloomCorrectionBlend << ",\n"
                << "  \"sample_phases_degrees\": [";
        for (int index = 0; index < lastTemporalPlan_.sampleCount; ++index) {
            if (index > 0) metadata << ", ";
            metadata << glm::degrees(lastTemporalPlan_.samplePhase(index));
        }
        metadata << "],\n  \"sample_weights\": [";
        for (int index = 0; index < lastTemporalPlan_.sampleCount; ++index) {
            if (index > 0) metadata << ", ";
            metadata << lastTemporalPlan_.weight(index);
        }
        metadata << "],\n"
                << "  \"buffers\": {\n"
                << "    \"final\": {\"file\": \"final.rgba8\", \"hash_fnv1a64\": \""
                << finalHash << "\"},\n"
                << "    \"scene\": {\"file\": \"scene.rgba8\", \"hash_fnv1a64\": \""
                << sceneHash << "\"},\n"
                << "    \"bloom\": {\"file\": \"bloom.rgba8\", \"hash_fnv1a64\": \""
                << bloomHash << "\"},\n"
                << "    \"emission\": ";
        if (emissionPixels.has_value()) {
            metadata << "{\"file\": \"emission.rgba32f\", \"hash_fnv1a64\": \""
                    << emissionHash << "\"}\n";
        } else {
            metadata << "null\n";
        }
        metadata << "  }\n}\n";

        diagnosticFrames_ << std::setprecision(12)
                << frameIndex << '\t' << selectedModelSlug() << '\t'
                << mintGlowCount_ << '\t' << temporalModeName(temporalMode_) << '\t'
                << temporalModeName(lastTemporalPlan_.mode) << '\t'
                << temporalSourceName(source) << '\t'
                << (lastTemporalGroovesAvailable_ ? 1 : 0) << '\t'
                << (temporalActive ? 1 : 0) << '\t'
                << (emissionPixels.has_value() ? 1 : 0) << '\t'
                << glm::degrees(lastTemporalPlan_.centerPhaseRadians) << '\t'
                << glm::degrees(lastPhysicalPresentedRollDeltaRadians_) << '\t'
                << glm::degrees(lastTemporalPlan_.presentedRollDeltaRadians) << '\t'
                << nominalPresentedFramesPerSecond_ << '\t' << spinRps_ << '\t'
                << lastTemporalPlan_.grooveCyclesPerFrame << '\t'
                << lastTemporalPlan_.grooveContrast << '\t'
                << lastTemporalPlan_.bandBlend << '\t'
                << lastTemporalPlan_.motionBandEnergyWeight << '\t'
                << lastTemporalPlan_.coreIntensity << '\t'
                << lastTemporalPlan_.bloomCorrectionBlend << '\t'
                << lastTemporalPlan_.sampleCount << '\t'
                << finalHash << '\t' << sceneHash << '\t' << bloomHash << '\t'
                << emissionHash << '\n';
        diagnosticFrames_.flush();
        if (!metadata || !diagnosticFrames_) {
            throw std::runtime_error("could not finish diagnostic buffer metadata");
        }
    }

    void capturePendingScreenshot() {
        if (pendingScreenshot_.has_value()) {
            capture(*pendingScreenshot_);
            pendingScreenshot_.reset();
        }
    }

    void setSelectedModel(const int index) {
        if (index >= 0 && index < static_cast<int>(models_.size())) {
            selectedModel_ = index;
            isolatedPart_ = -1;
            cleanAliasEnvelopeCycles_ = 0.0F;
            temporalPlanner_.resetLodHistory();
            updateTitle();
        }
    }

    void setCameraPreset(const int preset) { selectPreset(preset); }

    void prepareSmokeTest() {
        // Make the pixel assertion prove that the selected solid mesh rendered;
        // grid/axes alone must never be enough to pass a GPU smoke test.
        showGrid_ = false;
        showWireframe_ = false;
        showNormals_ = false;
        showCollision_ = false;
    }

private:
    GLFWwindow* window_ = nullptr;
    GpuFrameTimer gpuTimer_;
    std::vector<WheelModel> models_;
    std::vector<GpuModel> gpuModels_;
    LitProgram flatProgram_;
    LitProgram smoothProgram_;
    LineProgram lineProgram_;
    HarmonicTreadProgram harmonicTreadProgram_;
    BloomPipeline bloom_;
    TemporalEmissionPipeline temporal_;
    TemporalPlanner temporalPlanner_;
    LineMesh grid_;
    Camera camera_;
    int selectedModel_ = 0;
    int isolatedPart_ = -1;
    int framebufferWidth_ = 1;
    int framebufferHeight_ = 1;
    bool flatShading_ = true;
    bool bloomEnabled_ = true;
    bool showWireframe_ = false;
    bool showNormals_ = false;
    bool showCollision_ = false;
    bool showGrid_ = true;
    bool autoRoll_ = false;
    bool fixedSpinPhase_ = false;
    bool diagnosticInputLock_ = false;
    bool leftMouseDown_ = false;
    bool middleMouseDown_ = false;
    bool rightMouseDown_ = false;
    double previousMouseX_ = 0.0;
    double previousMouseY_ = 0.0;
    float spinRps_ = kDefaultSpinRps;
    TemporalMode temporalMode_ = TemporalMode::Sharp;
    int mintGlowCount_ = wheel_lab::kDefaultMintGlowCount;
    float presentedFramesPerSecond_ = kDefaultPresentedFramesPerSecond;
    float nominalPresentedFramesPerSecond_ = kDefaultPresentedFramesPerSecond;
    bool nominalCadenceLocked_ = false;
    int presentationCadenceDivisor_ = 1;
    bool scheduledPhaseClock_ = true;
    bool timingReplay_ = false;
    float maxRollStepRadians_ = glm::radians(kDefaultMaxRollStepDegrees);
    float modelYaw_ = 0.0F;
    float modelTilt_ = 0.0F;
    float modelRoll_ = 0.0F;
    double unwrappedModelRoll_ = 0.0;
    double pendingPresentedRollDeltaRadians_ = 0.0;
    double lastPhysicalPresentedRollDeltaRadians_ = 0.0;
    bool externalPhaseUpdatePending_ = false;
    bool forceConfiguredPresentedDelta_ = false;
    double lastPlanningIntervalSeconds_ =
            1.0 / static_cast<double>(kDefaultPresentedFramesPerSecond);
    double scheduledPresentationIntervalSeconds_ =
            1.0 / static_cast<double>(kDefaultPresentedFramesPerSecond);
    double smoothedInteractiveIntervalSeconds_ = 0.0;
    double cadenceLabelUpdateElapsedSeconds_ = 0.0;
    float cleanAliasEnvelopeCycles_ = 0.0F;
    int displayedInteractiveCadenceHz_ = 0;
    bool lastCadenceLabelChanged_ = false;
    bool lastTemporalGroovesAvailable_ = false;
    TemporalPlan lastTemporalPlan_;
    RenderTimingBreakdown lastRenderTiming_;
    std::uint64_t gpuFrameIndex_ = 0U;
    int titleSampleCount_ = -1;
    std::optional<fs::path> pendingScreenshot_;
    fs::path diagnosticCaptureDirectory_;
    std::ofstream diagnosticFrames_;
    bool diagnosticDescriptorWritten_ = false;
    std::size_t diagnosticExpectedFrameCount_ = 0U;

    void loadAllShaders() {
        const fs::path shaders(WHEEL_LAB_SHADER_DIR);
        flatProgram_.load(shaders / "flat_lit.vert", shaders / "flat_lit.frag");
        smoothProgram_.load(shaders / "smooth_lit.vert", shaders / "smooth_lit.frag");
        lineProgram_.load(shaders);
        harmonicTreadProgram_.load(shaders);
        bloom_.initialize(shaders);
        temporal_.initialize(shaders);
    }

    void configureLitProgram(
            const LitProgram& program,
            const glm::mat4& modelMatrix,
            const glm::mat4& mvp) const {
        glUseProgram(program.id);
        glUniformMatrix4fv(program.mvp, 1, GL_FALSE, glm::value_ptr(mvp));
        glUniformMatrix4fv(program.model, 1, GL_FALSE, glm::value_ptr(modelMatrix));
        const glm::vec3 cameraPosition = camera_.position();
        // Same addon-light placement as gameplay, normalized to a radius-0.5 wheel.
        const glm::vec3 lightPosition(
                0.0F, kGameplayAddonLightAbove, kGameplayAddonLightBack);
        glUniform3fv(program.lightPosition, 1, glm::value_ptr(lightPosition));
        glUniform3f(program.lightColor, 1.0F, 1.0F, 1.0F);
        glUniform3fv(program.cameraPosition, 1, glm::value_ptr(cameraPosition));
    }

    static bool isTemporalGroovePart(const GpuPart& part) {
        // The first name is the production material contract. The part-name
        // fallback keeps temporal captures useful while inspecting an older
        // unsplit procedural model in a dirty worktree.
        return part.material.name == "mint_groove_emissive"
                || part.name == "glowing chevron grooves";
    }

    static bool isMotionBandPart(const GpuPart& part) {
        return part.material.name == wheel_lab::kMintMotionBandMaterialName
                || part.name == "phase-independent tread motion band";
    }

    static bool isMintEmissionPart(const GpuPart& part) {
        return isTemporalGroovePart(part)
                || isMotionBandPart(part)
                || part.material.name == "mint_side_emissive";
    }

    static glm::vec4 gameplayNeonColor(const glm::vec4& themeColor) {
        const float maximum = std::max({themeColor.r, themeColor.g, themeColor.b});
        glm::vec4 result(1.0F);
        if (maximum <= 1.0e-6F) {
            result.r = kNeonBrightChannel;
            result.g = kNeonBrightChannel;
            result.b = kNeonBrightChannel;
        } else {
            const float brightnessScale = kNeonBrightChannel / maximum;
            for (int channel = 0; channel < 3; ++channel) {
                const float brightened = themeColor[static_cast<std::size_t>(channel)]
                        * brightnessScale;
                result[static_cast<std::size_t>(channel)] = std::clamp(
                        kNeonBrightChannel
                                - (kNeonBrightChannel - brightened)
                                        * kNeonSaturationGain,
                        kNeonDarkChannel,
                        kNeonBrightChannel);
            }
        }
        result.a = themeColor.a;
        return result;
    }

    bool hasTemporalGroovePart() const {
        const GpuModel& gpuModel = gpuModels_.at(static_cast<std::size_t>(selectedModel_));
        for (std::size_t partIndex = 0; partIndex < gpuModel.parts.size(); ++partIndex) {
            if (isolatedPart_ >= 0 && static_cast<int>(partIndex) != isolatedPart_) {
                continue;
            }
            if (isTemporalGroovePart(gpuModel.parts[partIndex])) {
                return true;
            }
        }
        return false;
    }

    bool hasMotionBandPart() const {
        const GpuModel& gpuModel = gpuModels_.at(
                static_cast<std::size_t>(selectedModel_));
        for (std::size_t partIndex = 0; partIndex < gpuModel.parts.size(); ++partIndex) {
            if (isolatedPart_ >= 0 && static_cast<int>(partIndex) != isolatedPart_) {
                continue;
            }
            if (isMotionBandPart(gpuModel.parts[partIndex])) {
                return true;
            }
        }
        return false;
    }

    void drawSolid(
            const glm::mat4& modelMatrix,
            const glm::mat4& mvp,
            const bool includeTemporalGrooves,
            const bool temporalGroovesOnly = false,
            const float temporalGrooveScale = 1.0F,
            const bool includeMotionBand = false,
            const bool motionBandOnly = false) const {
        const LitProgram& program = flatShading_ ? flatProgram_ : smoothProgram_;
        const GpuModel& gpuModel = gpuModels_.at(static_cast<std::size_t>(selectedModel_));
        configureLitProgram(program, modelMatrix, mvp);

        for (std::size_t partIndex = 0; partIndex < gpuModel.parts.size(); ++partIndex) {
            if (isolatedPart_ >= 0 && static_cast<int>(partIndex) != isolatedPart_) {
                continue;
            }
            const GpuPart& part = gpuModel.parts[partIndex];
            const bool temporalGroove = isTemporalGroovePart(part);
            const bool motionBand = isMotionBandPart(part);
            if ((motionBandOnly && !motionBand)
                    || (temporalGroovesOnly && !temporalGroove)
                    || (!includeTemporalGrooves && temporalGroove)
                    || (!includeMotionBand && motionBand)) {
                continue;
            }
            const Material& material = part.material;
            glm::vec4 drawColor = isMintEmissionPart(part)
                    ? gameplayNeonColor(material.color)
                    : material.color;
            if (temporalGroove) {
                drawColor.r *= temporalGrooveScale;
                drawColor.g *= temporalGrooveScale;
                drawColor.b *= temporalGrooveScale;
            }
            glUniform4fv(program.color, 1, glm::value_ptr(drawColor));
            glUniform1f(program.ambient, material.ambient);
            glUniform1f(program.diffuse, material.diffuse);
            glUniform1f(program.specular, material.specular);
            glUniform1f(program.shininess, std::max(1.0F, material.shininess));
            (flatShading_ ? part.flat : part.smooth).draw();
        }
    }

    void drawHarmonicTreadShell(
            const TemporalPlan& plan,
            const glm::mat4& mvp) const {
        const GpuModel& gpuModel = gpuModels_.at(
                static_cast<std::size_t>(selectedModel_));
        glUseProgram(harmonicTreadProgram_.id);
        glUniformMatrix4fv(
                harmonicTreadProgram_.mvp,
                1,
                GL_FALSE,
                glm::value_ptr(mvp));
        glUniform1f(
                harmonicTreadProgram_.rollPhase,
                plan.centerPhaseRadians);
        glUniform1f(
                harmonicTreadProgram_.aliasCycles,
                plan.grooveCyclesPerFrame);
        const float signedExposureCycles =
                plan.angularVelocityRadiansPerSecond
                * plan.effectiveExposureSeconds
                / kMintGroovePitchRadians;
        glUniform1f(
                harmonicTreadProgram_.exposureCycles,
                signedExposureCycles);
        glUniform1i(
                harmonicTreadProgram_.trailingBox,
                plan.mode == TemporalMode::FrameSplit ? 1 : 0);

        for (std::size_t partIndex = 0; partIndex < gpuModel.parts.size(); ++partIndex) {
            if (isolatedPart_ >= 0
                    && static_cast<int>(partIndex) != isolatedPart_) {
                continue;
            }
            const GpuPart& part = gpuModel.parts[partIndex];
            if (!isMotionBandPart(part)) {
                continue;
            }
            const glm::vec4 drawColor = gameplayNeonColor(part.material.color);
            glUniform4fv(
                    harmonicTreadProgram_.color,
                    1,
                    glm::value_ptr(drawColor));
            // The analytic emitter does not use lighting normals. The smooth
            // shell avoids the flat mesh's duplicated vertices and is the
            // cheapest exact surface on which to evaluate the periodic mask.
            part.smooth.draw();
        }
    }

    glm::mat4 modelMatrixAtPhase(const float phaseRadians) const {
        return glm::rotate(glm::mat4(1.0F), modelYaw_, {0.0F, 1.0F, 0.0F})
                * glm::rotate(glm::mat4(1.0F), modelTilt_, {0.0F, 0.0F, 1.0F})
                * glm::rotate(glm::mat4(1.0F), phaseRadians, {1.0F, 0.0F, 0.0F});
    }

    glm::mat4 viewProjection() const {
        const float aspect = static_cast<float>(framebufferWidth_)
                / static_cast<float>(framebufferHeight_);
        return camera_.projection(aspect) * camera_.view();
    }

    void bindFinalSceneTarget() const {
        if (bloomEnabled_) {
            bloom_.beginScene();
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0U);
            glViewport(0, 0, framebufferWidth_, framebufferHeight_);
        }
    }

    static void clearScene(const glm::vec3& color, const float alpha) {
        glClearColor(color.r, color.g, color.b, alpha);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(GL_TRUE);
        glDepthFunc(GL_LESS);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);  // Matches current gameplay state.
    }

    void drawSceneAtPhase(
            const float phaseRadians,
            const float temporalGrooveScale) const {
        const glm::mat4 vp = viewProjection();
        const glm::mat4 modelMatrix = modelMatrixAtPhase(phaseRadians);
        const glm::mat4 mvp = vp * modelMatrix;
        if (showGrid_) {
            drawLines(grid_, vp);
        }
        drawSolid(
                modelMatrix,
                mvp,
                temporalGrooveScale > 1.0e-4F,
                false,
                temporalGrooveScale);
        const GpuModel& gpuModel = gpuModels_.at(static_cast<std::size_t>(selectedModel_));
        if (showWireframe_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(gpuModel.wireframe, mvp);
        }
        if (showNormals_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(flatShading_ ? gpuModel.flatNormals : gpuModel.smoothNormals, mvp);
        }
        if (showCollision_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(gpuModel.collisionAndBounds, mvp);
        }
        glDepthFunc(GL_LESS);
    }

    void drawAliasSafeSceneAtPhase(
            const float groovePhaseRadians,
            const float temporalGrooveScale) const {
        const glm::mat4 vp = viewProjection();
        const glm::mat4 stableBodyModelMatrix = modelMatrixAtPhase(0.0F);
        const glm::mat4 stableBodyMvp = vp * stableBodyModelMatrix;
        if (showGrid_) {
            drawLines(grid_, vp);
        }

        // Carcass, hub and side rings are surfaces of revolution. Keeping
        // those parts in a canonical roll pose removes polygon/facet cadence
        // from the signal without changing their ideal physical appearance.
        // Only the genuinely roll-sensitive grooves retain physical phase.
        drawSolid(stableBodyModelMatrix, stableBodyMvp, false);
        if (temporalGrooveScale > kTemporalActivationEpsilon) {
            const glm::mat4 grooveModelMatrix = modelMatrixAtPhase(
                    groovePhaseRadians);
            const glm::mat4 grooveMvp = vp * grooveModelMatrix;
            drawSolid(
                    grooveModelMatrix,
                    grooveMvp,
                    true,
                    true,
                    temporalGrooveScale);
        }

        // Inspection overlays intentionally follow physical phase. They are
        // diagnostic geometry, not part of the alias-safe gameplay result.
        const glm::mat4 physicalModelMatrix = modelMatrixAtPhase(
                groovePhaseRadians);
        const glm::mat4 physicalMvp = vp * physicalModelMatrix;
        const GpuModel& gpuModel = gpuModels_.at(
                static_cast<std::size_t>(selectedModel_));
        if (showWireframe_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(gpuModel.wireframe, physicalMvp);
        }
        if (showNormals_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(
                    flatShading_ ? gpuModel.flatNormals : gpuModel.smoothNormals,
                    physicalMvp);
        }
        if (showCollision_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(gpuModel.collisionAndBounds, physicalMvp);
        }
        glDepthFunc(GL_LESS);
    }

    void renderHarmonicTemporalFrame(const TemporalPlan& plan) {
        bindFinalSceneTarget();
        clearScene(kBackground, 1.0F);
        drawAliasSafeSceneAtPhase(plan.centerPhaseRadians, 0.0F);

        temporal_.clearAccumulation();
        temporal_.beginSample();
        glEnable(GL_DEPTH_TEST);
        glDepthMask(GL_TRUE);
        glDepthFunc(GL_LESS);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);

        const glm::mat4 stableModelMatrix = modelMatrixAtPhase(0.0F);
        const glm::mat4 stableMvp = viewProjection() * stableModelMatrix;
        glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE);
        drawSolid(stableModelMatrix, stableMvp, false);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glDepthFunc(GL_LEQUAL);
        drawHarmonicTreadShell(plan, stableMvp);
        glDepthFunc(GL_LESS);
        temporal_.accumulateSample(1.0F);

        if (!bloomEnabled_) {
            temporal_.compositeOverTarget(
                    0U,
                    framebufferWidth_,
                    framebufferHeight_,
                    plan.coreIntensity);
        }
        glClearColor(kBackground.r, kBackground.g, kBackground.b, 1.0F);
    }

    void renderTemporalFrame(const TemporalPlan& plan) {
        if (usesHarmonicTreadShell(plan.mode)) {
            renderHarmonicTemporalFrame(plan);
            return;
        }
        // Render the non-groove wheel once. Legacy experiments keep current
        // roll; alias-safe mode pins ideal surfaces of revolution and side
        // lights to their equivalent canonical pose. The groove emissive is
        // replaced, rather than double-counted, by the exposure integral.
        bindFinalSceneTarget();
        clearScene(kBackground, 1.0F);
        // Once exposure is active, the sharp moving groove is absent. Side
        // emission is a different material and remains sharp throughout.
        const bool aliasSafe = usesAliasSafeBand(plan.mode);
        if (aliasSafe) {
            drawAliasSafeSceneAtPhase(plan.centerPhaseRadians, 0.0F);
        } else {
            drawSceneAtPhase(plan.centerPhaseRadians, 0.0F);
        }

        temporal_.clearAccumulation();
        const glm::mat4 vp = viewProjection();
        const float physicalWeightScale = aliasSafe
                ? plan.grooveContrast
                : 1.0F;
        if (physicalWeightScale > kTemporalActivationEpsilon) {
            for (int sampleIndex = 0; sampleIndex < plan.sampleCount; ++sampleIndex) {
                temporal_.beginSample();
                glEnable(GL_DEPTH_TEST);
                glDepthMask(GL_TRUE);
                glDepthFunc(GL_LESS);
                glDisable(GL_BLEND);
                glDisable(GL_CULL_FACE);
                const float samplePhase = plan.samplePhase(sampleIndex);
                const glm::mat4 grooveModelMatrix = modelMatrixAtPhase(samplePhase);
                const glm::mat4 grooveMvp = vp * grooveModelMatrix;

                // Legacy production samples use current-pose body depth. The
                // oracle samples physical body depth; alias-safe samples use
                // the canonical symmetric-body pose.
                const float depthBodyPhase = plan.mode == TemporalMode::Reference
                        ? samplePhase
                        : (aliasSafe ? 0.0F : plan.centerPhaseRadians);
                const glm::mat4 bodyModelMatrix = modelMatrixAtPhase(depthBodyPhase);
                const glm::mat4 bodyMvp = vp * bodyModelMatrix;

                // Only the opaque wheel body populates depth; the groove draw
                // supplies both its nearest surface and alpha coverage.
                glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE);
                drawSolid(bodyModelMatrix, bodyMvp, false);
                glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
                glDepthFunc(GL_LEQUAL);
                drawSolid(grooveModelMatrix, grooveMvp, true, true, 1.0F);
                glDepthFunc(GL_LESS);
                temporal_.accumulateSample(
                        plan.weight(sampleIndex) * physicalWeightScale);
            }
        }

        if (aliasSafe
                && plan.motionBandEnergyWeight > kTemporalActivationEpsilon) {
            // This is one phase-independent draw regardless of speed or hitch
            // length. Weighting the full-neon shell by authored groove duty
            // cycle preserves premultiplied RGB and alpha energy through the
            // physical-groove -> continuous-band cross-fade.
            temporal_.beginSample();
            glEnable(GL_DEPTH_TEST);
            glDepthMask(GL_TRUE);
            glDepthFunc(GL_LESS);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            const glm::mat4 stableModelMatrix = modelMatrixAtPhase(0.0F);
            const glm::mat4 stableMvp = vp * stableModelMatrix;
            glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE);
            drawSolid(stableModelMatrix, stableMvp, false);
            glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
            glDepthFunc(GL_LEQUAL);
            drawSolid(
                    stableModelMatrix,
                    stableMvp,
                    false,
                    false,
                    1.0F,
                    true,
                    true);
            glDepthFunc(GL_LESS);
            temporal_.accumulateSample(plan.motionBandEnergyWeight);
        }

        // With bloom enabled, BloomPipeline source-over composites this
        // premultiplied exposure into the scene before bright prefiltering and
        // then adds only the per-pixel bright-pass residual. Without bloom,
        // source-over composite it into the default target here.
        if (!bloomEnabled_) {
            temporal_.compositeOverTarget(
                    0U,
                    framebufferWidth_,
                    framebufferHeight_,
                    plan.coreIntensity);
        }
        glClearColor(kBackground.r, kBackground.g, kBackground.b, 1.0F);
    }

    float projectedRadiusPixels() const {
        const WheelModel& model = models_.at(static_cast<std::size_t>(selectedModel_));
        const float radius = std::max({
                std::abs(model.boundsMin.y), std::abs(model.boundsMax.y),
                std::abs(model.boundsMin.z), std::abs(model.boundsMax.z)});
        // Android plans in the quarter-resolution temporal/bloom target, not
        // full-resolution scene pixels. Keeping this metric identical is what
        // makes the 0.75-pixel adaptive interval target comparable.
        const float targetHeightPixels = static_cast<float>(
                std::max(1, bloom_.bloomA.height));
        if (camera_.orthographic) {
            const float halfHeight = camera_.distance * 0.34F;
            return radius * targetHeightPixels / (2.0F * halfHeight);
        }
        const float focalPixels = targetHeightPixels
                / (2.0F * std::tan(glm::radians(kGameVerticalFovDegrees) * 0.5F));
        return radius * focalPixels / std::max(0.01F, camera_.distance);
    }

    void drawLines(const LineMesh& mesh, const glm::mat4& mvp) const {
        glUseProgram(lineProgram_.id);
        glUniformMatrix4fv(lineProgram_.mvp, 1, GL_FALSE, glm::value_ptr(mvp));
        mesh.draw();
    }

    void resetView() {
        camera_ = Camera{};
        modelYaw_ = 0.0F;
        modelTilt_ = 0.0F;
        modelRoll_ = 0.0F;
        unwrappedModelRoll_ = 0.0;
        pendingPresentedRollDeltaRadians_ = 0.0;
        externalPhaseUpdatePending_ = false;
        cleanAliasEnvelopeCycles_ = 0.0F;
        temporalPlanner_.resetLodHistory();
        updateTitle();
    }

    void refreshNominalPresentationCadence() {
        if (nominalCadenceLocked_) {
            nominalPresentedFramesPerSecond_ = presentedFramesPerSecond_;
            scheduledPresentationIntervalSeconds_ =
                    1.0 / static_cast<double>(presentedFramesPerSecond_);
            return;
        }
        if (window_ == nullptr) {
            nominalPresentedFramesPerSecond_ = presentedFramesPerSecond_;
            return;
        }

        GLFWmonitor* selectedMonitor = glfwGetWindowMonitor(window_);
        if (selectedMonitor == nullptr) {
            int windowX = 0;
            int windowY = 0;
            int windowWidth = 0;
            int windowHeight = 0;
            glfwGetWindowPos(window_, &windowX, &windowY);
            glfwGetWindowSize(window_, &windowWidth, &windowHeight);

            int monitorCount = 0;
            GLFWmonitor** monitors = glfwGetMonitors(&monitorCount);
            std::int64_t largestOverlap = 0;
            for (int index = 0; index < monitorCount; ++index) {
                GLFWmonitor* monitor = monitors[index];
                int monitorX = 0;
                int monitorY = 0;
                glfwGetMonitorPos(monitor, &monitorX, &monitorY);
                const GLFWvidmode* mode = glfwGetVideoMode(monitor);
                if (mode == nullptr) {
                    continue;
                }
                const std::int64_t overlapWidth = std::max<std::int64_t>(
                        0,
                        std::min<std::int64_t>(
                                static_cast<std::int64_t>(windowX)
                                        + windowWidth,
                                static_cast<std::int64_t>(monitorX)
                                        + mode->width)
                                - std::max<std::int64_t>(windowX, monitorX));
                const std::int64_t overlapHeight = std::max<std::int64_t>(
                        0,
                        std::min<std::int64_t>(
                                static_cast<std::int64_t>(windowY)
                                        + windowHeight,
                                static_cast<std::int64_t>(monitorY)
                                        + mode->height)
                                - std::max<std::int64_t>(windowY, monitorY));
                const std::int64_t overlap = overlapWidth * overlapHeight;
                if (overlap > largestOverlap) {
                    largestOverlap = overlap;
                    selectedMonitor = monitor;
                }
            }
        }
        if (selectedMonitor == nullptr) {
            selectedMonitor = glfwGetPrimaryMonitor();
        }

        float nextCadence = presentedFramesPerSecond_;
        if (selectedMonitor != nullptr) {
            const GLFWvidmode* mode = glfwGetVideoMode(selectedMonitor);
            if (mode != nullptr && mode->refreshRate > 0) {
                nextCadence = static_cast<float>(mode->refreshRate)
                        / static_cast<float>(presentationCadenceDivisor_);
            }
        }
        if (!std::isfinite(nextCadence) || !(nextCadence > 0.0F)) {
            nextCadence = kDefaultPresentedFramesPerSecond;
        }
        if (nominalPresentedFramesPerSecond_ != nextCadence) {
            nominalPresentedFramesPerSecond_ = nextCadence;
            scheduledPresentationIntervalSeconds_ =
                    1.0 / static_cast<double>(nextCadence);
            cleanAliasEnvelopeCycles_ = 0.0F;
            updateTitle();
        }
    }

    static double sanitizeInteractiveInterval(
            const double intervalSeconds,
            const double fallbackSeconds) {
        if (!std::isfinite(intervalSeconds) || !(intervalSeconds > 0.0)) {
            return fallbackSeconds;
        }
        // This cap is a presentation safety boundary, not an FPS target. It prevents a debugger
        // pause from advancing phase by minutes while still exposing ordinary missed refreshes.
        return std::min(intervalSeconds, 0.1);
    }

    double phaseClockInterval(
            const double measuredIntervalSeconds,
            const double nominalIntervalSeconds) {
        if (!scheduledPhaseClock_) {
            return measuredIntervalSeconds;
        }
        if (!(scheduledPresentationIntervalSeconds_ > 0.0)
                || !std::isfinite(scheduledPresentationIntervalSeconds_)) {
            scheduledPresentationIntervalSeconds_ = nominalIntervalSeconds;
        }
        // Estimate sustained delivered cadence over a two-second time
        // constant. One delayed swap contributes to the long-term average but
        // can no longer become one enormous pose step on the following frame.
        if (measuredIntervalSeconds >= nominalIntervalSeconds * 0.25
                && measuredIntervalSeconds <= 0.1) {
            constexpr double trackingSeconds = 2.0;
            const double gain = 1.0 - std::exp(
                    -nominalIntervalSeconds / trackingSeconds);
            scheduledPresentationIntervalSeconds_ += gain
                    * (measuredIntervalSeconds
                            - scheduledPresentationIntervalSeconds_);
        }
        return std::clamp(
                scheduledPresentationIntervalSeconds_,
                nominalIntervalSeconds * 0.75,
                nominalIntervalSeconds * 1.5);
    }

    bool updateInteractiveCadenceLabel(const double intervalSeconds) {
        if (window_ == nullptr || !std::isfinite(intervalSeconds)
                || intervalSeconds < 0.001 || intervalSeconds > 0.1) {
            return false;
        }
        if (!(smoothedInteractiveIntervalSeconds_ > 0.0)) {
            smoothedInteractiveIntervalSeconds_ = intervalSeconds;
        } else {
            constexpr double labelSmoothing = 0.08;
            smoothedInteractiveIntervalSeconds_ += labelSmoothing
                    * (intervalSeconds - smoothedInteractiveIntervalSeconds_);
        }
        // Updating an X11 title queues traffic to the window server. It is only
        // a human diagnostic, so keep that traffic out of the high-rate render
        // path. This removes an avoidable perturbation; it is not evidence that
        // title updates are the source of every swap-return stall.
        cadenceLabelUpdateElapsedSeconds_ += intervalSeconds;
        constexpr double minimumTitleUpdateIntervalSeconds = 0.5;
        if (cadenceLabelUpdateElapsedSeconds_
                < minimumTitleUpdateIntervalSeconds) {
            return false;
        }
        cadenceLabelUpdateElapsedSeconds_ = std::fmod(
                cadenceLabelUpdateElapsedSeconds_,
                minimumTitleUpdateIntervalSeconds);
        const int nextCadence = static_cast<int>(std::lround(
                1.0 / smoothedInteractiveIntervalSeconds_));
        if (nextCadence == displayedInteractiveCadenceHz_) {
            return false;
        }
        displayedInteractiveCadenceHz_ = nextCadence;
        return true;
    }

    void selectPreset(const int preset) {
        camera_.target = {0.0F, 0.03F, 0.0F};
        camera_.distance = 2.75F;
        camera_.nearClip = 0.03F;
        camera_.farClip = 100.0F;
        if (preset == 3) {  // side/profile: look down +X axle
            camera_.yaw = glm::half_pi<float>();
            camera_.pitch = 0.0F;
        } else if (preset == 4) {  // tread/end: look down +Z
            camera_.yaw = 0.0F;
            camera_.pitch = 0.0F;
        } else if (preset == 5) {  // analysis three-quarter
            camera_.yaw = 0.62F;
            camera_.pitch = 0.22F;
        } else if (preset == 6) {  // normalized gameplay camera distance
            camera_.yaw = 0.0F;
            camera_.pitch = 0.0F;
            camera_.target = {0.0F, kGameplayCameraAbove, 0.0F};
            camera_.distance = kGameplayCameraBack;
            camera_.nearClip = kGameplayNearClip;
            camera_.farClip = kGameplayFarClip;
        }
        updateTitle();
    }

    void cycleIsolatedPart() {
        const int partCount = static_cast<int>(models_[static_cast<std::size_t>(selectedModel_)].parts.size());
        ++isolatedPart_;
        if (isolatedPart_ >= partCount) {
            isolatedPart_ = -1;
        }
        cleanAliasEnvelopeCycles_ = 0.0F;
        temporalPlanner_.resetLodHistory();
        updateTitle();
    }

    void cycleTemporalMode() {
        switch (temporalMode_) {
            case TemporalMode::Sharp: temporalMode_ = TemporalMode::Reference; break;
            case TemporalMode::Reference: temporalMode_ = TemporalMode::Adaptive; break;
            case TemporalMode::Adaptive: temporalMode_ = TemporalMode::FrameSplit; break;
            case TemporalMode::FrameSplit: temporalMode_ = TemporalMode::BandLimited; break;
            case TemporalMode::BandLimited: temporalMode_ = TemporalMode::Sharp; break;
            case TemporalMode::AdaptiveRaw: temporalMode_ = TemporalMode::Adaptive; break;
            case TemporalMode::FrameSplitRaw: temporalMode_ = TemporalMode::FrameSplit; break;
            case TemporalMode::AliasSafe: temporalMode_ = TemporalMode::Sharp; break;
        }
        cleanAliasEnvelopeCycles_ = 0.0F;
        temporalPlanner_.resetLodHistory();
        updateTitle();
    }

    void exportSelected() const {
        const WheelModel& model = models_[static_cast<std::size_t>(selectedModel_)];
        const fs::path path = fs::path(WHEEL_LAB_ROOT) / "exports" / (model.slug + ".obj");
        wheel_lab::exportObj(model, path);
        std::cout << "Exported " << path << " and sibling MTL\n";
    }

    void queueInteractiveScreenshot() {
        const WheelModel& model = models_[static_cast<std::size_t>(selectedModel_)];
        const auto now = std::chrono::system_clock::now().time_since_epoch();
        const auto stamp = std::chrono::duration_cast<std::chrono::seconds>(now).count();
        pendingScreenshot_ = fs::path(WHEEL_LAB_ROOT) / "screenshots"
                / (model.slug + "-" + std::to_string(stamp) + ".ppm");
    }

    void updateTitle() const {
        if (models_.empty() || window_ == nullptr) {
            return;
        }
        const WheelModel& model = models_[static_cast<std::size_t>(selectedModel_)];
        std::ostringstream title;
        title << "Wheel Mesh Lab | " << model.name
                << " | " << (flatShading_ ? "game flat" : "game smooth")
                << " | bloom " << (bloomEnabled_ ? "on" : "off")
                << " | spin " << spinRps_ << " rps"
                << (autoRoll_ ? " moving" : (fixedSpinPhase_ ? " fixed-motion pose" : " paused"))
                << " | temporal " << temporalModeName(temporalMode_)
                << ':' << lastTemporalPlan_.sampleCount
                << (isFrameSplitMode(temporalMode_)
                        ? " @1.00f trailing/"
                        : " @0.75f centered/")
                << (window_ == nullptr
                        ? std::to_string(static_cast<int>(std::lround(
                                presentedFramesPerSecond_))) + "fps configured"
                        : (displayedInteractiveCadenceHz_ > 0
                                ? "~" + std::to_string(
                                        displayedInteractiveCadenceHz_)
                                        + "fps actual"
                                : "actual cadence pending"))
                << ((window_ != nullptr
                                && usesHarmonicTreadShell(
                                        lastTemporalPlan_.mode))
                        ? " filter@"
                                + std::to_string(static_cast<int>(std::lround(
                                        nominalPresentedFramesPerSecond_)))
                                + "Hz nominal"
                        : "")
                << " band=" << std::fixed << std::setprecision(2)
                << lastTemporalPlan_.bandBlend
                << " groove=" << lastTemporalPlan_.grooveContrast
                << " bandEnergy=" << lastTemporalPlan_.motionBandEnergyWeight;
        if (temporalMode_ == TemporalMode::FrameSplitRaw) {
            title << " splitMax=" << std::setprecision(1)
                    << glm::degrees(maxRollStepRadians_) << "deg"
                    << " splitBudget=" << lastTemporalPlan_.splitSampleBudget
                    << (lastTemporalPlan_.splitBudgetHeldByHysteresis ? " HOLD" : "")
                    << (lastTemporalPlan_.sampleCapApplied ? " CAP" : "");
        } else if (temporalMode_ == TemporalMode::FrameSplit) {
            title << " analyticBox";
        }
        title << " | part ";
        if (isolatedPart_ < 0) {
            title << "all";
        } else {
            title << model.parts[static_cast<std::size_t>(isolatedPart_)].name;
        }
        glfwSetWindowTitle(window_, title.str().c_str());
    }

    void onCursorPosition(const double x, const double y) {
        if (diagnosticInputLock_) {
            previousMouseX_ = x;
            previousMouseY_ = y;
            return;
        }
        const float dx = static_cast<float>(x - previousMouseX_);
        const float dy = static_cast<float>(y - previousMouseY_);
        previousMouseX_ = x;
        previousMouseY_ = y;

        const bool shift = glfwGetKey(window_, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window_, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
        if (leftMouseDown_ && !shift) {
            camera_.yaw -= dx * 0.006F;
            camera_.pitch = std::clamp(camera_.pitch + dy * 0.006F, -1.45F, 1.45F);
        } else if (middleMouseDown_ || (leftMouseDown_ && shift)) {
            const glm::vec3 eye = camera_.position();
            const glm::vec3 forward = glm::normalize(camera_.target - eye);
            const glm::vec3 right = glm::normalize(glm::cross(forward, {0.0F, 1.0F, 0.0F}));
            const glm::vec3 up = glm::normalize(glm::cross(right, forward));
            camera_.target += (-right * dx + up * dy) * (camera_.distance * 0.0014F);
        }
        if (rightMouseDown_) {
            modelYaw_ += dx * 0.006F;
            modelTilt_ += dy * 0.006F;
        }
    }

    void onMouseButton(const int button, const int action) {
        if (diagnosticInputLock_) {
            leftMouseDown_ = false;
            middleMouseDown_ = false;
            rightMouseDown_ = false;
            return;
        }
        const bool pressed = action == GLFW_PRESS;
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            leftMouseDown_ = pressed;
        } else if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
            middleMouseDown_ = pressed;
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            rightMouseDown_ = pressed;
        }
        glfwGetCursorPos(window_, &previousMouseX_, &previousMouseY_);
    }

    void onScroll(const double offset) {
        if (diagnosticInputLock_) {
            return;
        }
        camera_.distance = std::clamp(
                camera_.distance * std::exp(static_cast<float>(-offset) * 0.12F), 0.7F, 20.0F);
    }

    void onKey(const int key, const int action) {
        if (action != GLFW_PRESS) {
            return;
        }
        if (diagnosticInputLock_) {
            if (key == GLFW_KEY_ESCAPE) {
                glfwSetWindowShouldClose(window_, GLFW_TRUE);
            }
            return;
        }
        try {
            switch (key) {
                case GLFW_KEY_ESCAPE: glfwSetWindowShouldClose(window_, GLFW_TRUE); break;
                case GLFW_KEY_1: setSelectedModel(0); break;
                case GLFW_KEY_2: setSelectedModel(1); break;
                case GLFW_KEY_3: selectPreset(3); break;
                case GLFW_KEY_4: selectPreset(4); break;
                case GLFW_KEY_5: selectPreset(5); break;
                case GLFW_KEY_6: selectPreset(6); break;
                case GLFW_KEY_F: flatShading_ = !flatShading_; updateTitle(); break;
                case GLFW_KEY_L: bloomEnabled_ = !bloomEnabled_; updateTitle(); break;
                case GLFW_KEY_W: showWireframe_ = !showWireframe_; break;
                case GLFW_KEY_N: showNormals_ = !showNormals_; break;
                case GLFW_KEY_C: showCollision_ = !showCollision_; break;
                case GLFW_KEY_G: showGrid_ = !showGrid_; break;
                case GLFW_KEY_O: camera_.orthographic = !camera_.orthographic; updateTitle(); break;
                case GLFW_KEY_I: cycleIsolatedPart(); break;
                case GLFW_KEY_T: cycleTemporalMode(); break;
                case GLFW_KEY_LEFT_BRACKET:
                    spinRps_ -= 0.5F;
                    updateTitle();
                    break;
                case GLFW_KEY_RIGHT_BRACKET:
                    spinRps_ += 0.5F;
                    updateTitle();
                    break;
                case GLFW_KEY_SPACE:
                    if (fixedSpinPhase_) {
                        fixedSpinPhase_ = false;
                        autoRoll_ = true;
                    } else {
                        autoRoll_ = !autoRoll_;
                    }
                    updateTitle();
                    break;
                case GLFW_KEY_R: resetView(); break;
                case GLFW_KEY_E: exportSelected(); break;
                case GLFW_KEY_P: queueInteractiveScreenshot(); break;
                case GLFW_KEY_H: loadAllShaders(); std::cout << "Reloaded shaders\n"; break;
                default: break;
            }
        } catch (const std::exception& error) {
            std::cerr << "Action failed: " << error.what() << '\n';
        }
    }

    static Application* from(GLFWwindow* window) {
        return static_cast<Application*>(glfwGetWindowUserPointer(window));
    }
    static void framebufferSizeCallback(
            GLFWwindow* window,
            const int width,
            const int height) {
        auto* application = static_cast<Application*>(
                glfwGetWindowUserPointer(window));
        if (application != nullptr) {
            application->framebufferWidth_ = std::max(1, width);
            application->framebufferHeight_ = std::max(1, height);
        }
    }
    static void windowPositionCallback(GLFWwindow* window, int, int) {
        from(window)->refreshNominalPresentationCadence();
    }
    static void windowSizeCallback(GLFWwindow* window, int, int) {
        from(window)->refreshNominalPresentationCadence();
    }
    static void cursorPositionCallback(GLFWwindow* window, const double x, const double y) {
        from(window)->onCursorPosition(x, y);
    }
    static void mouseButtonCallback(GLFWwindow* window, const int button, const int action, int) {
        from(window)->onMouseButton(button, action);
    }
    static void scrollCallback(GLFWwindow* window, double, const double yOffset) {
        from(window)->onScroll(yOffset);
    }
    static void keyCallback(GLFWwindow* window, const int key, int, const int action, int) {
        from(window)->onKey(key, action);
    }
};

void glfwErrorCallback(int, const char* description) {
    std::cerr << "GLFW: " << description << '\n';
}

int parseIntegerOption(
        const std::string& value,
        const std::string& optionName
) {
    try {
        std::size_t parsedCharacters = 0;
        const int result = std::stoi(value, &parsedCharacters);
        if (parsedCharacters != value.size()) {
            throw std::invalid_argument("trailing characters");
        }
        return result;
    } catch (const std::exception&) {
        throw std::invalid_argument(
                optionName + " requires a whole number");
    }
}

float parseFloatOption(
        const std::string& value,
        const std::string& optionName) {
    try {
        std::size_t parsedCharacters = 0;
        const float result = std::stof(value, &parsedCharacters);
        if (parsedCharacters != value.size() || !std::isfinite(result)) {
            throw std::invalid_argument("invalid floating-point value");
        }
        return result;
    } catch (const std::exception&) {
        throw std::invalid_argument(
                optionName + " requires a finite number");
    }
}

Options parseOptions(const int argc, char** argv) {
    Options options;
    for (int i = 1; i < argc; ++i) {
        const std::string argument(argv[i]);
        if (argument == "--smoke-test") {
            options.smokeTest = true;
        } else if (argument == "--export-all") {
            options.exportAll = true;
        } else if (argument == "--validate-only") {
            options.validateOnly = true;
        } else if (argument == "--model=mint"
                || (argument == "--model" && i + 1 < argc
                        && std::string(argv[i + 1]) == "mint")) {
            options.selectedModel = 0;
            if (argument == "--model") {
                ++i;
            }
        } else if (argument == "--model=violet"
                || (argument == "--model" && i + 1 < argc
                        && std::string(argv[i + 1]) == "violet")) {
            options.selectedModel = 1;
            if (argument == "--model") {
                ++i;
            }
        } else if (argument == "--screenshot" && i + 1 < argc) {
            options.screenshotPath = argv[++i];
        } else if (argument.rfind("--buffer-dump-dir=", 0) == 0) {
            options.bufferDumpDirectory = argument.substr(
                    std::string("--buffer-dump-dir=").size());
        } else if (argument == "--buffer-dump-dir" && i + 1 < argc) {
            options.bufferDumpDirectory = argv[++i];
        } else if (argument.rfind("--frame-timing-trace=", 0) == 0) {
            options.frameTimingTracePath = argument.substr(
                    std::string("--frame-timing-trace=").size());
        } else if (argument == "--frame-timing-trace" && i + 1 < argc) {
            options.frameTimingTracePath = argv[++i];
        } else if (argument.rfind("--presentation-events=", 0) == 0) {
            options.presentationEventsPath = argument.substr(
                    std::string("--presentation-events=").size());
        } else if (argument == "--presentation-events" && i + 1 < argc) {
            options.presentationEventsPath = argv[++i];
        } else if (argument == "--gpu-timing") {
            options.gpuTiming = true;
        } else if (argument == "--diagnostic-input-lock") {
            options.diagnosticInputLock = true;
        } else if (argument.rfind("--frame-timing-replay=", 0) == 0) {
            options.frameTimingReplayPath = argument.substr(
                    std::string("--frame-timing-replay=").size());
        } else if (argument == "--frame-timing-replay" && i + 1 < argc) {
            options.frameTimingReplayPath = argv[++i];
        } else if (argument.rfind("--diagnostic-seconds=", 0) == 0) {
            options.diagnosticSeconds = parseFloatOption(
                    argument.substr(std::string("--diagnostic-seconds=").size()),
                    "--diagnostic-seconds");
        } else if (argument == "--diagnostic-seconds" && i + 1 < argc) {
            options.diagnosticSeconds = parseFloatOption(
                    argv[++i], "--diagnostic-seconds");
        } else if (argument.rfind("--swap-interval=", 0) == 0) {
            options.swapInterval = parseIntegerOption(
                    argument.substr(std::string("--swap-interval=").size()),
                    "--swap-interval");
        } else if (argument == "--swap-interval" && i + 1 < argc) {
            options.swapInterval = parseIntegerOption(
                    argv[++i], "--swap-interval");
        } else if (argument == "--egl-window-context") {
            options.useEglWindowContext = true;
        } else if (argument.rfind("--phase-clock=", 0) == 0
                || (argument == "--phase-clock" && i + 1 < argc)) {
            const std::string value = argument == "--phase-clock"
                    ? std::string(argv[++i])
                    : argument.substr(std::string("--phase-clock=").size());
            if (value == "scheduled") {
                options.scheduledPhaseClock = true;
            } else if (value == "previous-delta") {
                options.scheduledPhaseClock = false;
            } else {
                throw std::invalid_argument(
                        "--phase-clock must be scheduled or previous-delta");
            }
        } else if (argument.rfind("--mint-glow-count=", 0) == 0) {
            options.mintGlowCount = parseIntegerOption(
                    argument.substr(std::string("--mint-glow-count=").size()),
                    "--mint-glow-count");
        } else if (argument == "--mint-glow-count" && i + 1 < argc) {
            options.mintGlowCount = parseIntegerOption(
                    argv[++i], "--mint-glow-count");
        } else if (argument == "--bloom") {
            options.bloom = true;
        } else if (argument == "--no-bloom") {
            options.bloom = false;
        } else if (argument == "--auto-roll") {
            options.autoRoll = true;
            options.fixedSpinPhase = false;
        } else if (argument.rfind("--spin-rps=", 0) == 0) {
            options.spinRps = parseFloatOption(
                    argument.substr(std::string("--spin-rps=").size()),
                    "--spin-rps");
        } else if (argument == "--spin-rps" && i + 1 < argc) {
            options.spinRps = parseFloatOption(argv[++i], "--spin-rps");
        } else if (argument.rfind("--spin-phase-degrees=", 0) == 0) {
            options.spinPhaseDegrees = parseFloatOption(
                    argument.substr(std::string("--spin-phase-degrees=").size()),
                    "--spin-phase-degrees");
            options.fixedSpinPhase = true;
            options.autoRoll = false;
        } else if (argument == "--spin-phase-degrees" && i + 1 < argc) {
            options.spinPhaseDegrees = parseFloatOption(
                    argv[++i], "--spin-phase-degrees");
            options.fixedSpinPhase = true;
            options.autoRoll = false;
        } else if (argument.rfind("--temporal-mode=", 0) == 0) {
            options.temporalMode = parseTemporalMode(
                    argument.substr(std::string("--temporal-mode=").size()));
        } else if (argument == "--temporal-mode" && i + 1 < argc) {
            options.temporalMode = parseTemporalMode(argv[++i]);
        } else if (argument.rfind("--max-roll-step-deg=", 0) == 0) {
            options.maxRollStepDegrees = parseFloatOption(
                    argument.substr(std::string("--max-roll-step-deg=").size()),
                    "--max-roll-step-deg");
        } else if (argument == "--max-roll-step-deg" && i + 1 < argc) {
            options.maxRollStepDegrees = parseFloatOption(
                    argv[++i], "--max-roll-step-deg");
        } else if (argument.rfind("--fps=", 0) == 0) {
            options.presentedFramesPerSecond = parseFloatOption(
                    argument.substr(std::string("--fps=").size()), "--fps");
            options.fpsExplicit = true;
        } else if (argument == "--fps" && i + 1 < argc) {
            options.presentedFramesPerSecond = parseFloatOption(argv[++i], "--fps");
            options.fpsExplicit = true;
        } else if (argument.rfind("--sequence-frames=", 0) == 0) {
            options.sequenceFrames = parseIntegerOption(
                    argument.substr(std::string("--sequence-frames=").size()),
                    "--sequence-frames");
        } else if (argument == "--sequence-frames" && i + 1 < argc) {
            options.sequenceFrames = parseIntegerOption(argv[++i], "--sequence-frames");
        } else if (argument.rfind("--sequence-end-spin-rps=", 0) == 0) {
            options.sequenceEndSpinRps = parseFloatOption(
                    argument.substr(std::string("--sequence-end-spin-rps=").size()),
                    "--sequence-end-spin-rps");
        } else if (argument == "--sequence-end-spin-rps" && i + 1 < argc) {
            options.sequenceEndSpinRps = parseFloatOption(
                    argv[++i], "--sequence-end-spin-rps");
        } else if (argument == "--sequence-fixed-phase") {
            options.sequenceFixedPhase = true;
        } else if (argument == "--sequence-dir" && i + 1 < argc) {
            options.sequenceDirectory = argv[++i];
        } else if (argument.rfind("--width=", 0) == 0) {
            options.width = parseIntegerOption(
                    argument.substr(std::string("--width=").size()), "--width");
        } else if (argument == "--width" && i + 1 < argc) {
            options.width = parseIntegerOption(argv[++i], "--width");
        } else if (argument.rfind("--height=", 0) == 0) {
            options.height = parseIntegerOption(
                    argument.substr(std::string("--height=").size()), "--height");
        } else if (argument == "--height" && i + 1 < argc) {
            options.height = parseIntegerOption(argv[++i], "--height");
        } else if (argument == "--preset" && i + 1 < argc) {
            const std::string preset(argv[++i]);
            if (preset == "side") options.cameraPreset = 3;
            else if (preset == "tread") options.cameraPreset = 4;
            else if (preset == "three-quarter") options.cameraPreset = 5;
            else if (preset == "gameplay") options.cameraPreset = 6;
            else throw std::invalid_argument("unknown camera preset: " + preset);
        } else if (argument == "--help" || argument == "-h") {
            std::cout
                    << "wheel_mesh_lab [--model mint|violet] "
                    << "[--mint-glow-count 1..18] "
                    << "[--bloom|--no-bloom] "
                    << "[--auto-roll] "
                    << "[--spin-rps RPS] [--spin-phase-degrees DEGREES] "
                    << "[--temporal-mode sharp|reference|adaptive|adaptive-raw|band|"
                            "split|split-raw|alias-safe] "
                    << "[--max-roll-step-deg DEGREES] "
                    << "[--fps FPS] "
                    << "[--frame-timing-trace FILE.tsv --diagnostic-seconds SECONDS] "
                    << "[--gpu-timing --presentation-events FILE.tsv] "
                    << "[--diagnostic-input-lock] "
                    << "[--frame-timing-replay FILE.tsv --sequence-dir DIR] "
                    << "[--swap-interval 0..4] "
                    << "[--egl-window-context] "
                    << "[--phase-clock scheduled|previous-delta] "
                    << "[--sequence-dir DIR --sequence-frames COUNT<=100000] "
                    << "[--sequence-end-spin-rps RPS --sequence-fixed-phase] "
                    << "[--buffer-dump-dir DIR] "
                    << "[--width PIXELS --height PIXELS] "
                    << "[--preset side|tread|three-quarter|gameplay] [--smoke-test] "
                    << "[--screenshot FILE.ppm] [--export-all] [--validate-only]\n";
            std::exit(0);
        } else {
            throw std::invalid_argument("unknown/incomplete option: " + argument);
        }
    }
    if (options.width < 1 || options.height < 1
            || options.width > 8192 || options.height > 8192) {
        throw std::invalid_argument("--width and --height must be in 1..8192");
    }
    if (!(options.presentedFramesPerSecond > 0.0F)
            || options.presentedFramesPerSecond > 1000.0F) {
        throw std::invalid_argument("--fps must be greater than 0 and at most 1000");
    }
    if (!(options.maxRollStepDegrees > 0.0F)) {
        throw std::invalid_argument("--max-roll-step-deg must be greater than 0");
    }
    if (options.diagnosticSeconds < 0.0F) {
        throw std::invalid_argument("--diagnostic-seconds cannot be negative");
    }
    if (options.swapInterval < 0 || options.swapInterval > 4) {
        throw std::invalid_argument("--swap-interval must be in 0..4");
    }
    if (options.sequenceFrames < 0) {
        throw std::invalid_argument("--sequence-frames cannot be negative");
    }
    if (options.sequenceFrames > kMaxSequenceFrames) {
        throw std::invalid_argument(
                "--sequence-frames cannot exceed 100000 (frame-00000..frame-99999)");
    }
    const bool timingReplay = !options.frameTimingReplayPath.empty();
    if (!options.sequenceDirectory.empty() && options.sequenceFrames == 0
            && !timingReplay) {
        options.sequenceFrames = 120;
    }
    if (options.sequenceFrames > 0 && options.sequenceDirectory.empty()) {
        throw std::invalid_argument("--sequence-frames requires --sequence-dir DIR");
    }
    if ((options.sequenceEndSpinRps.has_value() || options.sequenceFixedPhase)
            && options.sequenceFrames == 0) {
        throw std::invalid_argument(
                "sequence sweep options require --sequence-frames and --sequence-dir");
    }
    if (timingReplay && options.sequenceDirectory.empty()) {
        throw std::invalid_argument(
                "--frame-timing-replay requires --sequence-dir DIR");
    }
    if (timingReplay && (options.sequenceFrames > 0
            || options.sequenceEndSpinRps.has_value()
            || options.sequenceFixedPhase || options.smokeTest)) {
        throw std::invalid_argument(
                "timing replay cannot be combined with ordinary sequence/smoke options");
    }
    if ((!options.frameTimingTracePath.empty() || options.diagnosticSeconds > 0.0F)
            && (options.smokeTest || options.sequenceFrames > 0
                    || timingReplay || options.validateOnly || options.exportAll)) {
        throw std::invalid_argument(
                "live timing diagnostics cannot be combined with headless/export modes");
    }
    if (options.gpuTiming && options.frameTimingTracePath.empty()) {
        throw std::invalid_argument(
                "--gpu-timing requires --frame-timing-trace FILE.tsv");
    }
    if (options.diagnosticInputLock
            && options.frameTimingTracePath.empty()) {
        throw std::invalid_argument(
                "--diagnostic-input-lock requires --frame-timing-trace FILE.tsv");
    }
    if (!options.presentationEventsPath.empty()
            && options.frameTimingTracePath.empty()) {
        throw std::invalid_argument(
                "--presentation-events requires --frame-timing-trace FILE.tsv");
    }
    if (!options.presentationEventsPath.empty()
            && options.useEglWindowContext) {
        throw std::invalid_argument(
                "--presentation-events currently requires the native X11/GLX context");
    }
    if (timingReplay && (options.validateOnly || options.exportAll)) {
        throw std::invalid_argument(
                "timing replay cannot be combined with validate/export modes");
    }
    if (!options.bufferDumpDirectory.empty()) {
        if (!options.bloom) {
            throw std::invalid_argument(
                    "--buffer-dump-dir requires --bloom so every canonical "
                    "offscreen render target exists");
        }
        if (!(options.smokeTest || options.sequenceFrames > 0 || timingReplay)) {
            throw std::invalid_argument(
                    "--buffer-dump-dir is headless-only and requires --smoke-test, "
                    "--sequence-dir, or --frame-timing-replay");
        }
        if (options.validateOnly || options.exportAll) {
            throw std::invalid_argument(
                    "--buffer-dump-dir cannot be combined with validate/export modes");
        }
    }
    return options;
}

struct FrameTimingReplayRow {
    std::uint64_t frame = 0U;
    double loopDeltaMilliseconds = 0.0;
    double swapIntervalMilliseconds = 0.0;
    double swapReturnMilliseconds = 0.0;
    double nominalHz = 0.0;
};

struct FrameTimingReplay {
    std::vector<FrameTimingReplayRow> rows;
    double nominalHz = 0.0;
};

std::vector<std::string> splitTabSeparated(const std::string& line) {
    std::vector<std::string> cells;
    std::size_t start = 0U;
    while (true) {
        const std::size_t separator = line.find('\t', start);
        cells.push_back(line.substr(start, separator - start));
        if (separator == std::string::npos) {
            break;
        }
        start = separator + 1U;
    }
    return cells;
}

double parseReplayFiniteNumber(
        const std::string& text,
        const std::string& field,
        const std::size_t lineNumber) {
    try {
        std::size_t parsed = 0U;
        const double value = std::stod(text, &parsed);
        if (parsed != text.size() || !std::isfinite(value)) {
            throw std::invalid_argument("not finite");
        }
        return value;
    } catch (const std::exception&) {
        throw std::invalid_argument(
                "timing replay line " + std::to_string(lineNumber)
                + " has invalid " + field);
    }
}

FrameTimingReplay loadFrameTimingReplay(const fs::path& path) {
    std::ifstream source(path);
    if (!source) {
        throw std::runtime_error(
                "could not open frame timing replay: " + path.string());
    }
    std::string headerLine;
    if (!std::getline(source, headerLine)) {
        throw std::invalid_argument("frame timing replay is empty");
    }
    if (!headerLine.empty() && headerLine.back() == '\r') {
        headerLine.pop_back();
    }
    const std::vector<std::string> header = splitTabSeparated(headerLine);
    const std::set<std::string> uniqueHeader(header.begin(), header.end());
    if (uniqueHeader.size() != header.size()) {
        throw std::invalid_argument(
                "frame timing replay contains duplicate column names");
    }
    const auto column = [&header](const std::string& name) {
        const auto found = std::find(header.begin(), header.end(), name);
        if (found == header.end()) {
            throw std::invalid_argument(
                    "frame timing replay is missing column " + name);
        }
        return static_cast<std::size_t>(std::distance(header.begin(), found));
    };
    const std::size_t frameColumn = column("frame");
    const std::size_t loopDeltaColumn = column("loop_delta_ms");
    const std::size_t swapIntervalColumn = column("swap_interval_ms");
    const std::size_t nominalHzColumn = column("nominal_hz");
    const auto swapReturnIterator = std::find(
            header.begin(), header.end(), "swap_return_ms");
    const std::optional<std::size_t> swapReturnColumn =
            swapReturnIterator == header.end()
            ? std::nullopt
            : std::optional<std::size_t>(static_cast<std::size_t>(
                    std::distance(header.begin(), swapReturnIterator)));

    FrameTimingReplay replay;
    std::string line;
    std::size_t lineNumber = 1U;
    double synthesizedSwapReturn = 0.0;
    while (std::getline(source, line)) {
        ++lineNumber;
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (line.empty()) {
            throw std::invalid_argument(
                    "timing replay contains an empty row at line "
                    + std::to_string(lineNumber));
        }
        const std::vector<std::string> cells = splitTabSeparated(line);
        if (cells.size() != header.size()) {
            throw std::invalid_argument(
                    "timing replay line " + std::to_string(lineNumber)
                    + " has the wrong number of columns");
        }
        const double frameValue = parseReplayFiniteNumber(
                cells[frameColumn], "frame", lineNumber);
        if (frameValue < 0.0 || frameValue != std::floor(frameValue)
                || frameValue > static_cast<double>(kMaxSequenceFrames)) {
            throw std::invalid_argument(
                    "timing replay frame must be a non-negative integer");
        }
        FrameTimingReplayRow row;
        row.frame = static_cast<std::uint64_t>(frameValue);
        row.loopDeltaMilliseconds = parseReplayFiniteNumber(
                cells[loopDeltaColumn], "loop_delta_ms", lineNumber);
        row.swapIntervalMilliseconds = parseReplayFiniteNumber(
                cells[swapIntervalColumn], "swap_interval_ms", lineNumber);
        row.nominalHz = parseReplayFiniteNumber(
                cells[nominalHzColumn], "nominal_hz", lineNumber);
        if (row.loopDeltaMilliseconds < 0.0
                || row.swapIntervalMilliseconds < 0.0
                || !(row.nominalHz > 0.0) || row.nominalHz > 1000.0) {
            throw std::invalid_argument(
                    "timing replay intervals must be non-negative and nominal_hz "
                    "must be in (0, 1000]");
        }
        synthesizedSwapReturn += row.swapIntervalMilliseconds;
        row.swapReturnMilliseconds = swapReturnColumn.has_value()
                ? parseReplayFiniteNumber(
                        cells[*swapReturnColumn], "swap_return_ms", lineNumber)
                : synthesizedSwapReturn;
        if (row.frame != replay.rows.size()) {
            throw std::invalid_argument(
                    "timing replay frame numbers must start at zero and be consecutive");
        }
        if (!replay.rows.empty()) {
            const FrameTimingReplayRow& previous = replay.rows.back();
            const double observedInterval =
                    row.swapReturnMilliseconds - previous.swapReturnMilliseconds;
            const double tolerance = std::max(
                    0.01, std::abs(observedInterval) * 1.0e-5);
            if (observedInterval < 0.0
                    || std::abs(observedInterval
                            - row.swapIntervalMilliseconds) > tolerance) {
                throw std::invalid_argument(
                        "timing replay swap_return_ms disagrees with "
                        "swap_interval_ms at line " + std::to_string(lineNumber));
            }
        }
        if (replay.rows.empty()) {
            replay.nominalHz = row.nominalHz;
        } else if (std::abs(row.nominalHz - replay.nominalHz)
                > std::max(1.0, replay.nominalHz) * 1.0e-6) {
            throw std::invalid_argument(
                    "timing replay nominal_hz must remain constant");
        }
        replay.rows.push_back(row);
        if (replay.rows.size() > static_cast<std::size_t>(kMaxSequenceFrames)) {
            throw std::invalid_argument(
                    "timing replay cannot exceed 100000 submissions");
        }
    }
    if (replay.rows.size() < 3U) {
        throw std::invalid_argument(
                "timing replay requires at least three submissions");
    }
    return replay;
}

void printControls() {
    std::cout
            << "\nControls\n"
            << "  left drag       orbit camera\n"
            << "  shift+left/mid  pan camera\n"
            << "  right drag      rotate model\n"
            << "  wheel           zoom\n"
            << "  1 / 2           mint / violet model\n"
            << "  3 / 4 / 5 / 6   side / tread / 3-quarter / gameplay camera\n"
            << "  F               exact game flat/smooth shader\n"
            << "  L               gameplay bloom\n"
            << "  W / N           wireframe / normals\n"
            << "  C / G           collision+AABB / grid+axes\n"
            << "  O / I           ortho camera / isolate next submesh\n"
            << "  Space           auto-roll at selected rps\n"
            << "  T               sharp / reference / safe adaptive / safe split / legacy band\n"
            << "  [ / ]           decrease / increase spin by 0.5 rps\n"
            << "  E / P           export OBJ+MTL / save PPM screenshot\n"
            << "  H / R           hot-reload shaders / reset view\n\n";
}

void validateTemporalPlanner() {
    if (std::abs(centeredHannTransfer(0.0F) - 1.0F) > 1.0e-6F
            || positiveHarmonicFilterGain(1, 0.0F) != 1.0F
            || positiveHarmonicFilterGain(1, 0.50F) != 0.0F) {
        throw std::runtime_error(
                "harmonic cutoff did not remove presentation-ambiguous detail");
    }
    const float resolvableFundamental = positiveHarmonicFilterGain(
            1, 0.425F);
    if (!(resolvableFundamental > 0.0F)
            || !(resolvableFundamental < 1.0F)) {
        throw std::runtime_error(
                "harmonic cutoff lost its continuous pre-Nyquist transition");
    }

    TemporalPlanner planner;
    const TemporalPlan sharp = planner.plan(
            TemporalMode::Sharp, 0.25F, 50.0F, 120.0F, 250.0F);
    if (sharp.sampleCount != 1 || sharp.samplePhase(0) != 0.25F) {
        throw std::runtime_error("sharp temporal plan changed the presentation phase");
    }

    const TemporalPlan reference = planner.plan(
            TemporalMode::Reference, 1.0F, 5.0F, 120.0F, 250.0F);
    float referenceWeightSum = 0.0F;
    for (int sample = 0; sample < reference.sampleCount; ++sample) {
        referenceWeightSum += reference.weight(sample);
    }
    if (reference.sampleCount != kReferenceTemporalSamples
            || !(reference.samplePhase(0) < reference.samplePhase(63))
            || std::abs(referenceWeightSum - 1.0F) > 1.0e-6F
            || !(reference.weight(0) < reference.weight(32))
            || reference.coreIntensity != 1.0F
            || reference.bloomCorrectionBlend != 1.0F
            || std::abs(reference.effectiveExposureSeconds - 0.75F / 120.0F)
                    > 1.0e-7F) {
        throw std::runtime_error(
                "reference plan is not a normalized centered 64-sample Hann exposure");
    }

    const TemporalPlan reverse = planner.plan(
            TemporalMode::Reference, 1.0F, -5.0F, 120.0F, 250.0F);
    if (!(reverse.samplePhase(0) > reverse.samplePhase(63))) {
        throw std::runtime_error("reverse spin lost its temporal direction");
    }
    const TemporalPlan hitch = planner.plan(
            TemporalMode::Reference, 0.0F, 1.0F, 1.0F, 100.0F);
    if (std::abs(hitch.effectiveExposureSeconds - kMaxTemporalExposureSeconds)
            > 1.0e-7F) {
        throw std::runtime_error("temporal exposure exceeded the 1/30-second hitch cap");
    }

    const float tenRadiansPerSecondRps = 10.0F / glm::two_pi<float>();
    const TemporalPlan halfPixel = planner.plan(
            TemporalMode::AdaptiveRaw,
            0.0F,
            tenRadiansPerSecondRps,
            100.0F,
            20.0F / 3.0F);
    const TemporalPlan onePixel = planner.plan(
            TemporalMode::AdaptiveRaw,
            0.0F,
            tenRadiansPerSecondRps,
            100.0F,
            40.0F / 3.0F);
    const TemporalPlan capped = planner.plan(
            TemporalMode::AdaptiveRaw, 0.0F, 100.0F, 60.0F, 400.0F);
    if (halfPixel.sampleCount != 2 || onePixel.sampleCount != 3
            || capped.sampleCount != kMaxTemporalSamples) {
        throw std::runtime_error("0.75-pixel adaptive sample selection changed");
    }
    if (halfPixel.coreIntensity != 0.0F
            || halfPixel.bloomCorrectionBlend != 0.0F
            || onePixel.coreIntensity != 1.0F
            || std::abs(onePixel.bloomCorrectionBlend - 0.15625F) > 1.0e-6F) {
        throw std::runtime_error(
                "projected-motion activation/correction ramp changed");
    }

    planner.resetLodHistory();
    const TemporalPlan band = planner.plan(
            TemporalMode::BandLimited, 0.0F, 5.0F, 120.0F, 250.0F);
    float bandWeightSum = 0.0F;
    for (int sample = 0; sample < band.sampleCount; ++sample) {
        bandWeightSum += band.weight(sample);
        if (std::abs(band.weight(sample) - 1.0F / 12.0F) > 1.0e-6F) {
            throw std::runtime_error("band-limit weights are not uniform");
        }
    }
    const float coveredPitch = band.angleOffsetsRadians[11]
            - band.angleOffsetsRadians[0];
    if (band.bandBlend < 0.999F || band.sampleCount != kMaxTemporalSamples
            || std::abs(bandWeightSum - 1.0F) > 1.0e-6F
            || std::abs(coveredPitch - kMintGroovePitchRadians * 11.0F / 12.0F)
                    > 1.0e-6F) {
        throw std::runtime_error("high-speed mode is not a phase-invariant one-pitch average");
    }

    planner.resetLodHistory();
    const TemporalPlan first = planner.plan(
            TemporalMode::BandLimited, 0.0F, 3.0F, 120.0F, 100.0F);
    const TemporalPlan held = planner.plan(
            TemporalMode::BandLimited, 0.0F, 9.1F / 3.0F, 120.0F, 100.0F);
    if (!held.lodHeldByHysteresis || held.bandBlend != first.bandBlend) {
        throw std::runtime_error("0.25-degree LOD hysteresis did not hold speed jitter");
    }

    planner.resetLodHistory();
    const float tenDegreesPerFrameRps = 10.0F * 120.0F / 360.0F;
    const TemporalPlan bandOnly = planner.plan(
            TemporalMode::BandLimited,
            0.0F,
            tenDegreesPerFrameRps,
            120.0F,
            1.0F);
    if (!(bandOnly.bandBlend > 0.0F)
            || !(bandOnly.temporalBlend > kTemporalActivationEpsilon)
            || bandOnly.bloomCorrectionBlend != 0.0F) {
        throw std::runtime_error(
                "band LOD/hysteresis leaked into the projected-only bloom correction");
    }

    const TemporalPlan stationary = planner.plan(
            TemporalMode::BandLimited, 4.0F, 0.0F, 120.0F, 250.0F);
    if (stationary.sampleCount != 1 || stationary.coreIntensity != 0.0F
            || stationary.bloomCorrectionBlend != 0.0F) {
        throw std::runtime_error("stationary wheel unexpectedly received temporal blur");
    }

    planner.resetLodHistory();
    const float aliasLowDelta = 0.34F * kMintGroovePitchRadians;
    const TemporalPlan aliasLow = planner.plan(
            TemporalMode::AliasSafe,
            0.7F,
            aliasLowDelta * 120.0F / glm::two_pi<float>(),
            120.0F,
            250.0F,
            aliasLowDelta,
            glm::radians(kDefaultMaxRollStepDegrees));
    if (aliasLow.bandBlend != 0.0F || aliasLow.grooveContrast != 1.0F
            || aliasLow.motionBandEnergyWeight != 0.0F) {
        throw std::runtime_error(
                "alias-safe mode replaced physical grooves before its pre-Nyquist boundary");
    }

    planner.resetLodHistory();
    const float aliasMidDelta = 0.425F * kMintGroovePitchRadians;
    const TemporalPlan aliasMid = planner.plan(
            TemporalMode::AliasSafe,
            0.7F,
            aliasMidDelta * 120.0F / glm::two_pi<float>(),
            120.0F,
            250.0F,
            aliasMidDelta,
            glm::radians(kDefaultMaxRollStepDegrees));
    const float integratedAliasMidDuty =
            wheel_lab::kMintMotionBandCanonicalDutyCycle
                    * aliasMid.grooveContrast
            + aliasMid.motionBandEnergyWeight;
    if (std::abs(aliasMid.bandBlend - 0.5F) > 1.0e-5F
            || std::abs(aliasMid.grooveContrast - 0.5F) > 1.0e-5F
            || std::abs(
                    integratedAliasMidDuty
                            - wheel_lab::kMintMotionBandCanonicalDutyCycle)
                    > 1.0e-6F
            || aliasMid.sampleCount > kMaxAliasSafePhysicalSamples) {
        throw std::runtime_error(
                "alias-safe cross-fade changed groove energy or exceeded its work budget");
    }

    planner.resetLodHistory();
    const TemporalPlan cleanAdaptiveFirst = planner.plan(
            TemporalMode::Adaptive,
            0.7F,
            0.0F,
            120.0F,
            250.0F,
            0.45F * kMintGroovePitchRadians,
            glm::radians(kDefaultMaxRollStepDegrees));
    const float cleanAdaptiveFirstBlend = cleanAdaptiveFirst.bandBlend;
    const TemporalPlan cleanAdaptiveChanged = planner.plan(
            TemporalMode::Adaptive,
            0.7F,
            0.0F,
            120.0F,
            250.0F,
            0.455F * kMintGroovePitchRadians,
            glm::radians(kDefaultMaxRollStepDegrees));
    if (cleanAdaptiveChanged.lodHeldByHysteresis
            || cleanAdaptiveChanged.bandBlend
                    != cleanAdaptiveChanged.rawBandBlend
            || !(cleanAdaptiveChanged.bandBlend > cleanAdaptiveFirstBlend)
            || cleanAdaptiveChanged.sampleCount != 1
            || cleanAdaptiveChanged.emissionDrawCount() != 1
            || cleanAdaptiveChanged.temporalBlend != 1.0F) {
        throw std::runtime_error(
                "clean adaptive mode lost its continuous one-pass filter");
    }

    planner.resetLodHistory();
    const TemporalPlan cleanSplitFirst = planner.plan(
            TemporalMode::FrameSplit,
            0.7F,
            0.0F,
            120.0F,
            250.0F,
            0.45F * kMintGroovePitchRadians,
            glm::radians(kDefaultMaxRollStepDegrees));
    const float cleanSplitFirstBlend = cleanSplitFirst.bandBlend;
    const TemporalPlan cleanSplitChanged = planner.plan(
            TemporalMode::FrameSplit,
            0.7F,
            0.0F,
            120.0F,
            250.0F,
            0.455F * kMintGroovePitchRadians,
            glm::radians(kDefaultMaxRollStepDegrees));
    if (cleanSplitChanged.lodHeldByHysteresis
            || cleanSplitChanged.bandBlend != cleanSplitChanged.rawBandBlend
            || !(cleanSplitChanged.bandBlend > cleanSplitFirstBlend)
            || cleanSplitChanged.sampleCount != 1
            || cleanSplitChanged.emissionDrawCount() != 1
            || cleanSplitChanged.temporalBlend != 1.0F) {
        throw std::runtime_error(
                "clean split mode lost its continuous one-pass filter");
    }

    planner.resetLodHistory();
    const float aliasFullDelta = 0.5F * kMintGroovePitchRadians;
    const TemporalPlan aliasFull = planner.plan(
            TemporalMode::AliasSafe,
            12345.0F,
            aliasFullDelta * 120.0F / glm::two_pi<float>(),
            120.0F,
            250.0F,
            aliasFullDelta,
            glm::radians(kDefaultMaxRollStepDegrees));
    if (aliasFull.bandBlend != 1.0F || aliasFull.grooveContrast != 0.0F
            || std::abs(
                    aliasFull.motionBandEnergyWeight
                            - wheel_lab::kMintMotionBandCanonicalDutyCycle)
                    > 1.0e-6F
            || aliasFull.emissionDrawCount() != 1) {
        throw std::runtime_error(
                "alias-safe high-speed mode is not a one-draw phase-independent band");
    }

    planner.resetLodHistory();
    const TemporalPlan aliasHitch = planner.plan(
            TemporalMode::AliasSafe,
            0.0F,
            100000.0F,
            10.0F,
            250.0F,
            glm::radians(20000.0F),
            glm::radians(kDefaultMaxRollStepDegrees),
            kMaxInteractiveFrameSplitSamples,
            true);
    if (aliasHitch.emissionDrawCount() != 1
            || aliasHitch.bandBlend != 1.0F) {
        throw std::runtime_error(
                "an alias-safe hitch increased temporal rendering work");
    }

    planner.resetLodHistory();
    const TemporalPlan adaptiveFull = planner.plan(
            TemporalMode::Adaptive,
            -9876.0F,
            0.0F,
            120.0F,
            250.0F,
            aliasFullDelta,
            glm::radians(kDefaultMaxRollStepDegrees));
    if (adaptiveFull.bandBlend != 1.0F
            || adaptiveFull.emissionDrawCount() != 1
            || adaptiveFull.sampleCount != 1) {
        throw std::runtime_error(
                "user-facing adaptive mode did not use one harmonic draw");
    }

    planner.resetLodHistory();
    const TemporalPlan safeSplitMid = planner.plan(
            TemporalMode::FrameSplit,
            0.3F,
            0.0F,
            120.0F,
            250.0F,
            aliasMidDelta,
            glm::radians(2.0F));
    const float integratedSafeSplitMidDuty =
            wheel_lab::kMintMotionBandCanonicalDutyCycle
                    * safeSplitMid.grooveContrast
            + safeSplitMid.motionBandEnergyWeight;
    if (std::abs(safeSplitMid.bandBlend - 0.5F) > 1.0e-5F
            || std::abs(
                    integratedSafeSplitMidDuty
                            - wheel_lab::kMintMotionBandCanonicalDutyCycle)
                    > 1.0e-6F
            || safeSplitMid.emissionDrawCount() != 1
            || safeSplitMid.sampleCount != 1
            || safeSplitMid.temporalBlend != 1.0F
            || safeSplitMid.coreIntensity != 1.0F) {
        throw std::runtime_error(
                "harmonic split transition changed energy or one-pass work");
    }

    planner.resetLodHistory();
    const TemporalPlan safeSplitFull = planner.plan(
            TemporalMode::FrameSplit,
            0.3F,
            0.0F,
            120.0F,
            250.0F,
            aliasFullDelta,
            glm::radians(2.0F));
    if (safeSplitFull.bandBlend != 1.0F
            || safeSplitFull.emissionDrawCount() != 1
            || safeSplitFull.sampleCount != 1) {
        throw std::runtime_error(
                "user-facing split mode did not converge to one stable band draw");
    }

    planner.resetLodHistory();
    const TemporalPlan safeSplitHitch = planner.plan(
            TemporalMode::FrameSplit,
            0.0F,
            0.0F,
            10.0F,
            250.0F,
            glm::radians(20000.0F),
            glm::radians(0.001F),
            kMaxInteractiveFrameSplitSamples,
            true);
    if (safeSplitHitch.emissionDrawCount() != 1
            || safeSplitHitch.bandBlend != 1.0F) {
        throw std::runtime_error(
                "safe split hitch increased work instead of selecting the band");
    }

    planner.resetLodHistory();
    const TemporalPlan rawAdaptive = planner.plan(
            TemporalMode::AdaptiveRaw,
            0.0F,
            0.0F,
            120.0F,
            250.0F,
            aliasFullDelta,
            glm::radians(kDefaultMaxRollStepDegrees));
    const TemporalPlan rawSplit = planner.plan(
            TemporalMode::FrameSplitRaw,
            0.0F,
            0.0F,
            120.0F,
            250.0F,
            aliasFullDelta,
            glm::radians(2.0F));
    if (rawAdaptive.bandBlend != 0.0F || rawSplit.bandBlend != 0.0F
            || rawAdaptive.motionBandEnergyWeight != 0.0F
            || rawSplit.motionBandEnergyWeight != 0.0F) {
        throw std::runtime_error(
                "raw diagnostic modes unexpectedly enabled the stable band");
    }

    const float twoDegrees = glm::radians(2.0F);
    const TemporalPlan splitAtThreshold = planner.plan(
            TemporalMode::FrameSplitRaw,
            1.0F,
            100.0F,
            120.0F,
            250.0F,
            twoDegrees,
            twoDegrees);
    if (splitAtThreshold.sampleCount != 1
            || splitAtThreshold.samplePhase(0) != 1.0F
            || splitAtThreshold.temporalBlend != 0.0F
            || splitAtThreshold.coreIntensity != 0.0F) {
        throw std::runtime_error(
                "split mode blurred or half-frame-lagged at its inclusive threshold");
    }

    const float fiveDegrees = glm::radians(5.0F);
    const TemporalPlan split = planner.plan(
            TemporalMode::FrameSplitRaw,
            1.0F,
            100.0F,
            120.0F,
            250.0F,
            fiveDegrees,
            twoDegrees);
    float splitWeightSum = 0.0F;
    for (int sample = 0; sample < split.sampleCount; ++sample) {
        splitWeightSum += split.weight(sample);
        if (std::abs(split.weight(sample) - 1.0F / 3.0F) > 1.0e-6F) {
            throw std::runtime_error("split-mode box weights are not uniform");
        }
    }
    const float expectedFirstOffset = -fiveDegrees * 5.0F / 6.0F;
    const float expectedLastOffset = -fiveDegrees * 1.0F / 6.0F;
    if (split.sampleCount != 3 || split.requestedSampleCount != 3U
            || split.sampleCapApplied
            || std::abs(splitWeightSum - 1.0F) > 1.0e-6F
            || std::abs(split.angleOffsetsRadians.front() - expectedFirstOffset)
                    > 1.0e-6F
            || std::abs(split.angleOffsetsRadians.back() - expectedLastOffset)
                    > 1.0e-6F
            || !(split.samplePhase(0) < split.samplePhase(2))
            || !(split.samplePhase(2) < split.centerPhaseRadians)
            || split.effectiveExposureSeconds != 1.0F / 120.0F
            || split.bloomCorrectionBlend != 1.0F
            || split.coreIntensity != 1.0F) {
        throw std::runtime_error(
                "split mode is not a normalized trailing previous-to-current box exposure");
    }

    const TemporalPlan reverseSplit = planner.plan(
            TemporalMode::FrameSplitRaw,
            1.0F,
            -100.0F,
            120.0F,
            250.0F,
            -fiveDegrees,
            twoDegrees);
    if (reverseSplit.sampleCount != 3
            || !(reverseSplit.samplePhase(0) > reverseSplit.samplePhase(2))
            || !(reverseSplit.samplePhase(2) > reverseSplit.centerPhaseRadians)) {
        throw std::runtime_error(
                "reverse split exposure escaped the past-to-current roll interval");
    }

    const TemporalPlan safetyCappedSplit = planner.plan(
            TemporalMode::FrameSplitRaw,
            0.0F,
            100.0F,
            120.0F,
            250.0F,
            glm::radians(20000.0F),
            glm::radians(0.001F));
    float cappedWeightSum = 0.0F;
    for (int sample = 0; sample < safetyCappedSplit.sampleCount; ++sample) {
        cappedWeightSum += safetyCappedSplit.weight(sample);
    }
    if (!safetyCappedSplit.sampleCapApplied
            || safetyCappedSplit.sampleCount != kMaxOfflineFrameSplitSamples
            || safetyCappedSplit.requestedSampleCount
                    <= static_cast<std::uint64_t>(
                            kMaxOfflineFrameSplitSamples)
            || std::abs(cappedWeightSum - 1.0F) > 1.0e-5F
            || !(safetyCappedSplit.angleOffsetsRadians.front()
                    < safetyCappedSplit.angleOffsetsRadians.back())
            || !(safetyCappedSplit.angleOffsetsRadians.back() < 0.0F)) {
        throw std::runtime_error(
                "split-mode safety cap lost exposure extent, ordering, or normalized energy");
    }

    planner.resetLodHistory();
    const float actual180Delta = glm::radians(44.6F);
    const float actual180Rps = actual180Delta * 180.0F
            / glm::two_pi<float>();
    const TemporalPlan actualCadence = planner.plan(
            TemporalMode::Adaptive,
            0.0F,
            actual180Rps,
            180.0F,
            100.0F,
            actual180Delta,
            glm::radians(kDefaultMaxRollStepDegrees),
            kMaxInteractiveFrameSplitSamples,
            true);
    if (std::abs(actualCadence.presentationIntervalSeconds - 1.0F / 180.0F)
                    > 1.0e-7F
            || std::abs(actualCadence.effectiveExposureSeconds - 0.75F / 180.0F)
                    > 1.0e-7F
            || std::abs(actualCadence.degreesPerFrame - 44.6F) > 1.0e-4F) {
        throw std::runtime_error(
                "interactive temporal plan ignored its actual presentation interval/delta");
    }

    planner.resetLodHistory();
    const float tenDegreeStep = glm::radians(10.0F);
    const TemporalPlan interactiveThree = planner.plan(
            TemporalMode::FrameSplitRaw,
            0.0F,
            0.0F,
            180.0F,
            100.0F,
            glm::radians(29.9F),
            tenDegreeStep,
            kMaxInteractiveFrameSplitSamples,
            true);
    const TemporalPlan interactiveHeld = planner.plan(
            TemporalMode::FrameSplitRaw,
            0.0F,
            0.0F,
            180.0F,
            100.0F,
            glm::radians(19.9F),
            tenDegreeStep,
            kMaxInteractiveFrameSplitSamples,
            true);
    const TemporalPlan interactiveSettled = planner.plan(
            TemporalMode::FrameSplitRaw,
            0.0F,
            0.0F,
            180.0F,
            100.0F,
            glm::radians(17.0F),
            tenDegreeStep,
            kMaxInteractiveFrameSplitSamples,
            true);
    if (interactiveThree.sampleCount != 3
            || interactiveHeld.requestedSampleCount != 2U
            || interactiveHeld.sampleCount != 3
            || !interactiveHeld.splitBudgetHeldByHysteresis
            || interactiveSettled.sampleCount != 2
            || interactiveSettled.splitBudgetHeldByHysteresis) {
        throw std::runtime_error(
                "interactive split sample budget did not hold and settle across jitter");
    }

    planner.resetLodHistory();
    const TemporalPlan interactiveHitch = planner.plan(
            TemporalMode::FrameSplitRaw,
            0.0F,
            0.0F,
            10.0F,
            100.0F,
            glm::radians(20000.0F),
            glm::radians(0.001F),
            kMaxInteractiveFrameSplitSamples,
            true);
    if (!interactiveHitch.sampleCapApplied
            || interactiveHitch.sampleCount != kMaxInteractiveFrameSplitSamples
            || interactiveHitch.splitSampleBudget
                    != kMaxInteractiveFrameSplitSamples) {
        throw std::runtime_error(
                "interactive split hitch escaped its 64-sample work budget");
    }

    constexpr std::array<float, 5> coverages = {0.0F, 0.1F, 0.35F, 0.7F, 1.0F};
    constexpr std::array<float, 4> ordinaryValues = {0.0F, 0.04F, 0.3F, 1.0F};
    for (const float coverage : coverages) {
        const float exposure = coverage * kNeonBrightChannel;
        const float target = exposure * emissionBrightPassFactor();
        for (const float ordinary : ordinaryValues) {
            const float fullResidual = temporalBloomResidual(
                    exposure, ordinary, 1.0F);
            const float halfResidual = temporalBloomResidual(
                    exposure, ordinary, 0.5F);
            const float zeroResidual = temporalBloomResidual(
                    exposure, ordinary, 0.0F);
            if (!std::isfinite(fullResidual) || fullResidual < 0.0F
                    || std::abs(
                            ordinary + fullResidual
                                    - std::max(ordinary, target))
                            > 1.0e-6F) {
                throw std::runtime_error(
                        "per-pixel bloom residual no longer supplies exactly Q-O");
            }
            if (zeroResidual != 0.0F
                    || std::abs(halfResidual - 0.5F * fullResidual) > 1.0e-6F) {
                throw std::runtime_error(
                        "projected-only bloom correction no longer scales Q-O");
            }
        }
    }
    std::cout << "[ok] temporal planner: analytic per-harmonic Nyquist guards, "
            << "one-pass clean Hann/box shutters, 0.75px raw adaptation, "
            << "projected-only residual continuity, smooth clean transitions, "
            << "legacy-LOD/split hysteresis, energy-matched alias-safe band, "
            << "and bounded raw trailing frame split\n";
}

}  // namespace

int main(int argc, char** argv) {
    try {
        Options options = parseOptions(argc, argv);
        std::optional<FrameTimingReplay> timingReplay;
        if (!options.frameTimingReplayPath.empty()) {
            timingReplay = loadFrameTimingReplay(options.frameTimingReplayPath);
            if (options.fpsExplicit
                    && std::abs(
                            static_cast<double>(options.presentedFramesPerSecond)
                            - timingReplay->nominalHz)
                            > std::max(1.0, timingReplay->nominalHz) * 1.0e-6) {
                throw std::invalid_argument(
                        "--fps must match nominal_hz in --frame-timing-replay");
            }
            options.presentedFramesPerSecond = static_cast<float>(
                    timingReplay->nominalHz);
        }
        std::vector<WheelModel> models;
        models.push_back(wheel_lab::makeMintWheel(
                options.mintGlowCount));
        models.push_back(wheel_lab::makeVioletWheel());
        for (auto& model : models) {
            const wheel_lab::ValidationReport report = wheel_lab::validateModel(model);
            std::cout << (report.valid ? "[ok] " : "[invalid] ") << report.summary << '\n';
            if (!report.valid) {
                return 2;
            }
        }
        validateTemporalPlanner();
        if (options.validateOnly) {
            return 0;
        }
        if (options.exportAll) {
            for (const auto& model : models) {
                const fs::path destination = fs::path(WHEEL_LAB_ROOT) / "exports"
                        / (model.slug + ".obj");
                wheel_lab::exportObj(model, destination);
                std::cout << "Exported " << destination << '\n';
            }
            return 0;
        }

        if (options.smokeTest || options.sequenceFrames > 0
                || timingReplay.has_value()) {
            const fs::path path = options.screenshotPath.empty()
                    ? fs::path(WHEEL_LAB_ROOT) / "build"
                            / (models.at(static_cast<std::size_t>(options.selectedModel)).slug
                                    + "-smoke.ppm")
                    : options.screenshotPath;
            HeadlessEglContext context(options.width, options.height);
            std::cout << "Renderer: " << glGetString(GL_RENDERER) << '\n'
                    << "OpenGL ES: " << glGetString(GL_VERSION) << '\n'
                    << "GLSL: " << glGetString(GL_SHADING_LANGUAGE_VERSION) << '\n';
            {
                Application application(
                        nullptr,
                        std::move(models),
                        options.selectedModel,
                        options,
                        options.width,
                        options.height);
                application.setCameraPreset(options.cameraPreset);
                application.prepareSmokeTest();
                if (!options.bufferDumpDirectory.empty()) {
                    const std::size_t expectedBufferFrames = timingReplay.has_value()
                            ? timingReplay->rows.size()
                            : (options.sequenceFrames > 0
                                    ? static_cast<std::size_t>(options.sequenceFrames)
                                    : 1U);
                    application.beginDiagnosticCapture(
                            options.bufferDumpDirectory,
                            expectedBufferFrames);
                }
                if (timingReplay.has_value()) {
                    fs::create_directories(options.sequenceDirectory);
                    const fs::path sourceDirectory =
                            options.sequenceDirectory / "sources";
                    fs::create_directories(sourceDirectory);
                    for (const fs::directory_entry& entry
                            : fs::directory_iterator(options.sequenceDirectory)) {
                        if (entry.is_regular_file()
                                && isGeneratedSequenceFrame(entry.path())) {
                            fs::remove(entry.path());
                        }
                    }
                    for (const fs::directory_entry& entry
                            : fs::directory_iterator(sourceDirectory)) {
                        if (entry.is_regular_file()
                                && isGeneratedReplaySource(entry.path())) {
                            fs::remove(entry.path());
                        }
                    }
                    fs::remove(options.sequenceDirectory / "manifest.tsv");
                    fs::remove(options.sequenceDirectory / "submissions.tsv");
                    fs::remove(options.sequenceDirectory / "qa-timing.svg");

                    const double nominalIntervalMilliseconds =
                            1000.0 / timingReplay->nominalHz;
                    double cumulativeSlots = 0.0;
                    std::size_t presentationFrameCount = 0U;
                    for (std::size_t index = 1U;
                            index < timingReplay->rows.size(); ++index) {
                        cumulativeSlots += timingReplay->rows[index]
                                .swapIntervalMilliseconds
                                / nominalIntervalMilliseconds;
                        const double boundary = std::floor(cumulativeSlots + 0.5);
                        if (boundary > static_cast<double>(kMaxSequenceFrames)) {
                            throw std::invalid_argument(
                                    "timing replay expands beyond 100000 "
                                    "nominal presentation frames");
                        }
                        presentationFrameCount = static_cast<std::size_t>(
                                std::max(0.0, boundary));
                    }

                    std::ofstream submissions(
                            options.sequenceDirectory / "submissions.tsv");
                    if (!submissions) {
                        throw std::runtime_error(
                                "could not write replay submissions manifest");
                    }
                    submissions
                            << "submission\tinput_frame\tloop_delta_ms"
                            << "\tswap_interval_ms\tphase_degrees"
                            << "\tphysical_pose_delta_degrees"
                            << "\tfilter_delta_degrees\tgroove_contrast"
                            << "\talias_envelope_cycles\ttemporal_blend"
                            << "\tband_blend\tmotion_band_energy_weight"
                            << "\tbloom_correction_blend\tcore_intensity"
                            << "\ttemporal_sample_count\temission_draw_count"
                            << "\tphase_clock\n";
                    submissions << std::setprecision(12);
                    std::vector<double> sourcePhases;
                    sourcePhases.reserve(timingReplay->rows.size());
                    for (std::size_t index = 0U;
                            index < timingReplay->rows.size(); ++index) {
                        const FrameTimingReplayRow& row = timingReplay->rows[index];
                        application.render(row.loopDeltaMilliseconds / 1000.0);
                        throwOnGlError("headless frame timing replay render");
                        if (!options.bufferDumpDirectory.empty()) {
                            application.captureDiagnosticBundle(index);
                        }
                        std::ostringstream sourceName;
                        sourceName << "source-" << std::setw(5)
                                << std::setfill('0') << index << ".ppm";
                        application.capture(
                                sourceDirectory / sourceName.str(), false);
                        const TemporalPlan& plan = application.lastTemporalPlan();
                        const double phaseDegrees = glm::degrees(
                                static_cast<double>(plan.centerPhaseRadians));
                        sourcePhases.push_back(phaseDegrees);
                        submissions << index << '\t' << row.frame << '\t'
                                << row.loopDeltaMilliseconds << '\t'
                                << row.swapIntervalMilliseconds << '\t'
                                << phaseDegrees << '\t'
                                << glm::degrees(
                                        application.lastPhysicalPresentedRollDeltaRadians())
                                << '\t'
                                << glm::degrees(plan.presentedRollDeltaRadians) << '\t'
                                << plan.grooveContrast << '\t'
                                << application.cleanAliasEnvelopeCycles() << '\t'
                                << plan.temporalBlend << '\t'
                                << plan.bandBlend << '\t'
                                << plan.motionBandEnergyWeight << '\t'
                                << plan.bloomCorrectionBlend << '\t'
                                << plan.coreIntensity << '\t'
                                << plan.sampleCount << '\t'
                                << plan.emissionDrawCount() << '\t'
                                << (options.scheduledPhaseClock
                                        ? "scheduled" : "previous-delta")
                                << '\n';
                    }

                    std::ofstream presentation(
                            options.sequenceDirectory / "manifest.tsv");
                    if (!presentation) {
                        throw std::runtime_error(
                                "could not write replay presentation manifest");
                    }
                    presentation
                            << "frame\tsource_submission\tinput_frame"
                            << "\tphase_degrees\trepeated_source\tsource_gray_code"
                            << "\tfps\trps\ttiming_source\n";
                    presentation << std::setprecision(12);
                    std::vector<std::size_t> presentedSources;
                    std::vector<double> presentedPhases;
                    presentedSources.reserve(presentationFrameCount);
                    presentedPhases.reserve(presentationFrameCount);
                    cumulativeSlots = 0.0;
                    std::size_t outputFrame = 0U;
                    std::optional<std::size_t> previousSource;
                    for (std::size_t index = 1U;
                            index < timingReplay->rows.size(); ++index) {
                        cumulativeSlots += timingReplay->rows[index]
                                .swapIntervalMilliseconds
                                / nominalIntervalMilliseconds;
                        const std::size_t nextBoundary = static_cast<std::size_t>(
                                std::max(0.0, std::floor(cumulativeSlots + 0.5)));
                        const std::size_t sourceIndex = index - 1U;
                        while (outputFrame < nextBoundary) {
                            std::ostringstream sourceName;
                            sourceName << "source-" << std::setw(5)
                                    << std::setfill('0') << sourceIndex << ".ppm";
                            std::ostringstream frameName;
                            frameName << "frame-" << std::setw(5)
                                    << std::setfill('0') << outputFrame << ".ppm";
                            const fs::path sourcePath =
                                    sourceDirectory / sourceName.str();
                            const fs::path framePath =
                                    options.sequenceDirectory / frameName.str();
                            std::error_code linkError;
                            fs::create_hard_link(sourcePath, framePath, linkError);
                            if (linkError) {
                                fs::copy_file(
                                        sourcePath,
                                        framePath,
                                        fs::copy_options::overwrite_existing);
                            }
                            const bool repeated = previousSource.has_value()
                                    && *previousSource == sourceIndex;
                            const std::size_t grayCode =
                                    sourceIndex ^ (sourceIndex >> 1U);
                            presentation << outputFrame << '\t' << sourceIndex << '\t'
                                    << timingReplay->rows[sourceIndex].frame << '\t'
                                    << sourcePhases[sourceIndex] << '\t'
                                    << (repeated ? 1 : 0) << '\t'
                                    << grayCode << '\t'
                                    << timingReplay->nominalHz << '\t'
                                    << application.spinRps() << '\t'
                                    << "swap_return_proxy_replay\n";
                            presentedSources.push_back(sourceIndex);
                            presentedPhases.push_back(sourcePhases[sourceIndex]);
                            previousSource = sourceIndex;
                            ++outputFrame;
                        }
                    }

                    std::ofstream qaSvg(
                            options.sequenceDirectory / "qa-timing.svg");
                    if (!qaSvg) {
                        throw std::runtime_error(
                                "could not write replay QA timing SVG");
                    }
                    constexpr double plotLeft = 64.0;
                    constexpr double plotTop = 62.0;
                    constexpr double plotWidth = 1100.0;
                    constexpr double plotHeight = 150.0;
                    qaSvg << "<svg xmlns=\"http://www.w3.org/2000/svg\" "
                            << "width=\"1200\" height=\"260\" "
                            << "viewBox=\"0 0 1200 260\">\n"
                            << "<rect width=\"1200\" height=\"260\" "
                            << "fill=\"#0d141d\"/>\n"
                            << "<text x=\"32\" y=\"34\" fill=\"#e6edf5\" "
                            << "font-family=\"monospace\" font-size=\"20\">"
                            << "Replay source phase; red = held source</text>\n"
                            << "<rect x=\"" << plotLeft << "\" y=\"" << plotTop
                            << "\" width=\"" << plotWidth << "\" height=\""
                            << plotHeight << "\" fill=\"#101c28\" "
                            << "stroke=\"#30445a\"/>\n";
                    if (!presentedSources.empty()) {
                        for (std::size_t index = 1U;
                                index < presentedSources.size(); ++index) {
                            if (presentedSources[index]
                                    == presentedSources[index - 1U]) {
                                const double x = plotLeft + static_cast<double>(index)
                                        / static_cast<double>(
                                                std::max<std::size_t>(
                                                        1U, presentedSources.size() - 1U))
                                        * plotWidth;
                                qaSvg << "<line x1=\"" << x << "\" y1=\""
                                        << plotTop << "\" x2=\"" << x
                                        << "\" y2=\"" << plotTop + plotHeight
                                        << "\" stroke=\"#ff5d73\" opacity=\"0.45\"/>\n";
                            }
                        }
                        qaSvg << "<polyline fill=\"none\" stroke=\"#72e6c1\" "
                                << "stroke-width=\"1.5\" points=\"";
                        for (std::size_t index = 0U;
                                index < presentedPhases.size(); ++index) {
                            const double x = plotLeft + static_cast<double>(index)
                                    / static_cast<double>(
                                            std::max<std::size_t>(
                                                    1U, presentedPhases.size() - 1U))
                                    * plotWidth;
                            const double wrapped = std::fmod(
                                    std::fmod(presentedPhases[index], 360.0) + 360.0,
                                    360.0);
                            const double y = plotTop + plotHeight
                                    * (1.0 - wrapped / 360.0);
                            qaSvg << x << ',' << y << ' ';
                        }
                        qaSvg << "\"/>\n";
                    }
                    qaSvg << "</svg>\n";
                    std::cout << "Timing replay: " << options.sequenceDirectory
                            << " (" << timingReplay->rows.size()
                            << " submissions -> " << presentationFrameCount
                            << " nominal presentation slots; swap-return timing "
                            << "is a back-pressure proxy)\n";
                } else if (options.sequenceFrames > 0) {
                    fs::create_directories(options.sequenceDirectory);
                    for (const fs::directory_entry& entry
                            : fs::directory_iterator(options.sequenceDirectory)) {
                        if (entry.is_regular_file()
                                && isGeneratedSequenceFrame(entry.path())) {
                            fs::remove(entry.path());
                        }
                    }
                    fs::remove(options.sequenceDirectory / "manifest.tsv");
                    std::ofstream manifest(options.sequenceDirectory / "manifest.tsv");
                    if (!manifest) {
                        throw std::runtime_error(
                                "could not write temporal sequence manifest");
                    }
                    manifest << "frame\tphase_degrees\tsamples\trequested_samples"
                            << "\tsample_cap_applied\tsplit_sample_budget"
                            << "\tsplit_budget_hysteresis_held"
                            << "\tmax_roll_step_degrees"
                            << "\tpresented_roll_delta_degrees\texposure_profile"
                            << "\tpresentation_interval_ms\texposure_ms"
                            << "\tresolved_span_degrees\tdegrees_per_frame"
                            << "\tgroove_cycles_per_frame\tprojected_sweep_pixels"
                            << "\traw_band_blend\tband_blend\tgroove_contrast"
                            << "\tmotion_band_energy_weight\temission_draws"
                            << "\ttemporal_blend"
                            << "\ttemporal_active\tcore_intensity"
                            << "\temission_bright_factor"
                            << "\tbloom_correction_blend\tbloom_correction"
                            << "\thysteresis_held\tmode\tfps\trps\tshutter_frames\n";
                    const double initialPhase = glm::radians(
                            static_cast<double>(options.spinPhaseDegrees));
                    const double phaseStep = static_cast<double>(options.spinRps)
                            * glm::two_pi<double>()
                            / static_cast<double>(options.presentedFramesPerSecond);
                    double sweptPhase = initialPhase;
                    double previousFrameRps = options.spinRps;
                    if (!options.sequenceFixedPhase) {
                        // Prime a deterministic previous presentation so frame zero has the same
                        // exact configured-FPS motion interval as every subsequent frame. Without
                        // this, every temporal mode would emit one artificial sharp first frame.
                        application.setSpinPhaseRadians(initialPhase - phaseStep);
                    }
                    for (int frame = 0; frame < options.sequenceFrames; ++frame) {
                        const float ramp = options.sequenceFrames > 1
                                ? static_cast<float>(frame)
                                        / static_cast<float>(options.sequenceFrames - 1)
                                : 0.0F;
                        const double frameRps = options.sequenceEndSpinRps.has_value()
                                ? static_cast<double>(options.spinRps)
                                        + static_cast<double>(
                                                *options.sequenceEndSpinRps
                                                        - options.spinRps) * ramp
                                : static_cast<double>(options.spinRps);
                        if (frame > 0 && options.sequenceEndSpinRps.has_value()
                                && !options.sequenceFixedPhase) {
                            sweptPhase += 0.5F * (previousFrameRps + frameRps)
                                    * glm::two_pi<double>()
                                    / static_cast<double>(
                                            options.presentedFramesPerSecond);
                        }
                        const double phase = options.sequenceFixedPhase
                                ? initialPhase
                                : (options.sequenceEndSpinRps.has_value()
                                        ? sweptPhase
                                        : initialPhase
                                                + static_cast<double>(frame) * phaseStep);
                        application.setSpinRps(static_cast<float>(frameRps));
                        application.setSpinPhaseRadians(phase);
                        application.render(0.0F);
                        throwOnGlError("headless temporal sequence render");
                        if (!options.bufferDumpDirectory.empty()) {
                            application.captureDiagnosticBundle(
                                    static_cast<std::uint64_t>(frame));
                        }
                        std::ostringstream filename;
                        filename << "frame-" << std::setw(5) << std::setfill('0')
                                << frame << ".ppm";
                        application.capture(options.sequenceDirectory / filename.str());
                        const TemporalPlan& plan = application.lastTemporalPlan();
                        const float resolvedSpan = usesHarmonicTreadShell(plan.mode)
                                ? plan.angularVelocityRadiansPerSecond
                                        * plan.effectiveExposureSeconds
                                : (plan.sampleCount > 1
                                        ? plan.angleOffsetsRadians[
                                                static_cast<std::size_t>(
                                                        plan.sampleCount - 1)]
                                                - plan.angleOffsetsRadians[0]
                                        : 0.0F);
                        manifest << frame << '\t'
                                << std::setprecision(12) << glm::degrees(phase) << '\t'
                                << plan.sampleCount << '\t'
                                << plan.requestedSampleCount << '\t'
                                << (plan.sampleCapApplied ? 1 : 0) << '\t'
                                << plan.splitSampleBudget << '\t'
                                << (plan.splitBudgetHeldByHysteresis ? 1 : 0) << '\t'
                                << glm::degrees(plan.maxRollStepRadians) << '\t'
                                << glm::degrees(plan.presentedRollDeltaRadians) << '\t'
                                << temporalExposureProfileName(plan.mode) << '\t'
                                << plan.presentationIntervalSeconds * 1000.0F << '\t'
                                << plan.effectiveExposureSeconds * 1000.0F << '\t'
                                << glm::degrees(resolvedSpan) << '\t'
                                << plan.degreesPerFrame << '\t'
                                << plan.grooveCyclesPerFrame << '\t'
                                << plan.projectedSweepPixels << '\t'
                                << plan.rawBandBlend << '\t'
                                << plan.bandBlend << '\t'
                                << plan.grooveContrast << '\t'
                                << plan.motionBandEnergyWeight << '\t'
                                << plan.emissionDrawCount() << '\t'
                                << plan.temporalBlend << '\t'
                                << (plan.temporalBlend
                                        > kTemporalActivationEpsilon ? 1 : 0) << '\t'
                                << plan.coreIntensity << '\t'
                                << emissionBrightPassFactor() << '\t'
                                << plan.bloomCorrectionBlend << '\t'
                                << (plan.mode == TemporalMode::FrameSplitRaw
                                        ? "full_per_pixel_residual"
                                        : "projected_blended_per_pixel_residual") << '\t'
                                << (plan.lodHeldByHysteresis ? 1 : 0) << '\t'
                                << temporalModeName(plan.mode) << '\t'
                                << options.presentedFramesPerSecond << '\t'
                                << frameRps << '\t'
                                << plan.effectiveExposureSeconds
                                        * options.presentedFramesPerSecond << '\n';
                        previousFrameRps = frameRps;
                    }
                    std::cout << "Sequence: " << options.sequenceDirectory << " ("
                            << options.sequenceFrames << " deterministic frames at "
                            << options.presentedFramesPerSecond << " fps)\n";
                } else {
                    // Fixed warm-up steps keep legacy auto-roll smoke captures
                    // byte-stable instead of depending on build-machine timing.
                    const double smokeDeltaSeconds =
                            1.0 / options.presentedFramesPerSecond;
                    for (int frame = 0; frame < 3; ++frame) {
                        application.render(smokeDeltaSeconds);
                        throwOnGlError("headless smoke render");
                    }
                    if (!options.bufferDumpDirectory.empty()) {
                        application.captureDiagnosticBundle(0U);
                    }
                    application.capture(path);
                    throwOnGlError("headless smoke screenshot");
                }
            }
            return 0;
        }

        glfwSetErrorCallback(glfwErrorCallback);
        if (glfwInit() != GLFW_TRUE) {
            throw std::runtime_error("GLFW initialization failed");
        }
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_ES_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        if (options.useEglWindowContext) {
            glfwWindowHint(
                    GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        }
        glfwWindowHint(GLFW_SAMPLES, 0);  // Gameplay's bloom scene target is non-MSAA.
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        GLFWwindow* window = glfwCreateWindow(
                options.width, options.height, "Wheel Mesh Lab", nullptr, nullptr);
        if (window == nullptr) {
            glfwTerminate();
            throw std::runtime_error("could not create a GLFW OpenGL ES 3.1 window");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(options.swapInterval);
        int initialFramebufferWidth = options.width;
        int initialFramebufferHeight = options.height;
        // One startup query is required for HiDPI correctness. Subsequent
        // changes arrive through framebufferSizeCallback without X11 polling.
        glfwGetFramebufferSize(
                window, &initialFramebufferWidth, &initialFramebufferHeight);
        std::cout << "Renderer: " << glGetString(GL_RENDERER) << '\n'
                << "OpenGL ES: " << glGetString(GL_VERSION) << '\n'
                << "GLSL: " << glGetString(GL_SHADING_LANGUAGE_VERSION) << '\n'
                << "Window backend: "
                << wheel_lab::diagnostics::glfwRuntimeBackendName() << '\n';
        printControls();

        {
            Application application(
                    window,
                    std::move(models),
                    options.selectedModel,
                    options,
                    initialFramebufferWidth,
                    initialFramebufferHeight);
            application.setCameraPreset(options.cameraPreset);
            using SteadyClock = std::chrono::steady_clock;
            const auto runStamp = std::chrono::duration_cast<
                    std::chrono::nanoseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
            const std::string diagnosticRunId =
                    "wheel-" + std::to_string(runStamp);
            PresentationTracker presentationTracker(
                    window,
                    !options.presentationEventsPath.empty(),
                    diagnosticRunId);
            if (!options.presentationEventsPath.empty()) {
                std::cout
                        << "Physical scanout clock: "
                        << presentationTracker.scanoutSourceName() << '\n'
                        << "Frame completion feedback: "
                        << presentationTracker.completionSourceName()
                        << " (mapping is accepted only after full-run validation)\n";
            }
            const auto diagnosticStart = SteadyClock::now();
            auto previousLoopStart = diagnosticStart;
            auto previousSwapReturn = diagnosticStart;
            std::uint64_t frameIndex = 0U;
            std::vector<InteractiveFrameTimingSample> frameTimingSamples;
            const bool collectFrameTiming =
                    !options.frameTimingTracePath.empty();
            if (collectFrameTiming) {
                const double requestedCapacity = options.diagnosticSeconds > 0.0F
                        ? std::ceil(
                                static_cast<double>(options.diagnosticSeconds)
                                * 12000.0)
                        : 4096.0;
                frameTimingSamples.reserve(static_cast<std::size_t>(std::clamp(
                        requestedCapacity, 4096.0, 1000000.0)));
                std::cout << "Frame timing trace: "
                        << options.frameTimingTracePath << '\n';
            }
            const auto milliseconds = [](const auto duration) {
                return std::chrono::duration<double, std::milli>(
                        duration).count();
            };
            while (glfwWindowShouldClose(window) == GLFW_FALSE) {
                const auto loopStart = SteadyClock::now();
                const double delta =
                        std::chrono::duration<double>(
                                loopStart - previousLoopStart).count();
                previousLoopStart = loopStart;
                application.render(std::min(delta, 0.1));
                const auto renderEnd = SteadyClock::now();
                application.capturePendingScreenshot();
                const auto screenshotEnd = SteadyClock::now();
                const ScanoutObservation scanoutBefore =
                        presentationTracker.sampleScanoutCounter();
                const auto swapStart = SteadyClock::now();
                presentationTracker.noteSwapSubmission(
                        frameIndex,
                        milliseconds(swapStart - diagnosticStart));
                glfwSwapBuffers(window);
                const auto swapReturn = SteadyClock::now();
                const ScanoutObservation scanoutAfter =
                        presentationTracker.sampleScanoutCounter();
                glfwPollEvents();
                const auto pollEnd = SteadyClock::now();
                presentationTracker.pollEvents(
                        milliseconds(pollEnd - diagnosticStart));

                if (collectFrameTiming) {
                    const TemporalPlan& plan = application.lastTemporalPlan();
                    const Application::RenderTimingBreakdown& renderTiming =
                            application.lastRenderTiming();
                    frameTimingSamples.push_back(InteractiveFrameTimingSample{
                            frameIndex,
                            milliseconds(loopStart - diagnosticStart),
                            delta * 1000.0,
                            milliseconds(renderEnd - loopStart),
                            renderTiming.setupMilliseconds,
                            renderTiming.sceneMilliseconds,
                            renderTiming.bloomMilliseconds,
                            milliseconds(screenshotEnd - renderEnd),
                            milliseconds(swapReturn - swapStart),
                            milliseconds(swapReturn - diagnosticStart),
                            frameIndex == 0U
                                    ? 0.0
                                    : milliseconds(
                                            swapReturn - previousSwapReturn),
                            milliseconds(pollEnd - swapReturn),
                            glm::degrees(plan.centerPhaseRadians),
                            glm::degrees(
                                    application.lastPhysicalPresentedRollDeltaRadians()),
                            glm::degrees(plan.presentedRollDeltaRadians),
                            application.nominalPresentedFramesPerSecond(),
                            application.spinRps(),
                            plan.grooveCyclesPerFrame,
                            application.cleanAliasEnvelopeCycles(),
                            plan.temporalBlend,
                            plan.grooveContrast,
                            plan.bandBlend,
                            plan.motionBandEnergyWeight,
                            plan.bloomCorrectionBlend,
                            plan.coreIntensity,
                            plan.sampleCount,
                            plan.emissionDrawCount(),
                            application.lastCadenceLabelChanged() ? 1 : 0,
                            options.scheduledPhaseClock ? 1 : 0,
                            application.selectedModelSlug().c_str(),
                            application.requestedTemporalMode(),
                            plan.mode,
                            application.lastTemporalSource(),
                            application.lastTemporalGroovesAvailable() ? 1 : 0,
                            application.mintGlowCount(),
                            scanoutBefore.valid && scanoutAfter.valid ? 1 : 0,
                            scanoutBefore.counter,
                            scanoutAfter.counter,
                            scanoutBefore.valid && scanoutAfter.valid
                                    ? wheel_lab::diagnostics::wrappedScanoutDelta(
                                            scanoutBefore.counter,
                                            scanoutAfter.counter)
                                    : 0U,
                            scanoutBefore.queryMilliseconds,
                            scanoutAfter.queryMilliseconds});
                }
                previousSwapReturn = swapReturn;
                ++frameIndex;

                if (options.diagnosticSeconds > 0.0F
                        && std::chrono::duration<double>(
                                swapReturn - diagnosticStart).count()
                                >= options.diagnosticSeconds) {
                    glfwSetWindowShouldClose(window, GLFW_TRUE);
                }
            }
            if (collectFrameTiming) {
                application.finalizeGpuTimings();
                if (!options.presentationEventsPath.empty()) {
                    presentationTracker.finalize(options.presentationEventsPath);
                }
                if (!options.frameTimingTracePath.parent_path().empty()) {
                    fs::create_directories(
                            options.frameTimingTracePath.parent_path());
                }
                std::ofstream frameTimingTrace(options.frameTimingTracePath);
                if (!frameTimingTrace) {
                    throw std::runtime_error(
                            "could not write frame timing trace: "
                            + options.frameTimingTracePath.string());
                }
                frameTimingTrace
                        << "frame\tloop_start_ms\tloop_delta_ms\trender_ms"
                        << "\tsetup_ms\tscene_ms\tbloom_ms"
                        << "\tscreenshot_ms\tswap_wait_ms\tswap_return_ms"
                        << "\tswap_interval_ms\tpoll_ms\tphase_degrees"
                        << "\tphysical_pose_delta_degrees"
                        << "\tfilter_delta_degrees\tnominal_hz\tspin_rps"
                        << "\tgroove_cycles_per_frame\talias_envelope_cycles"
                        << "\ttemporal_blend\tgroove_contrast"
                        << "\tband_blend\tmotion_band_energy_weight"
                        << "\tbloom_correction_blend\tcore_intensity"
                        << "\ttemporal_sample_count\temission_draw_count"
                        << "\tcadence_title_update\tscheduled_phase_clock"
                        << "\tmodel_slug\trequested_temporal_mode"
                        << "\teffective_temporal_mode\ttemporal_source"
                        << "\ttemporal_grooves_available\tmint_glow_count"
                        << "\tdiagnostic_run_id\tgpu_timer_status"
                        << "\tgpu_disjoint_epoch\tgpu_query_latency_frames"
                        << "\tgpu_start_timestamp_ns\tgpu_end_timestamp_ns"
                        << "\tgpu_setup_ms\tgpu_scene_ms\tgpu_bloom_ms"
                        << "\tgpu_frame_ms\tscanout_source\tscanout_valid"
                        << "\tscanout_counter_before\tscanout_counter_after"
                        << "\tscanout_counter_delta"
                        << "\tscanout_query_before_ms\tscanout_query_after_ms"
                        << "\tpresentation_completion_source"
                        << "\tpresentation_completion_events"
                        << "\tpresentation_exact_mapping\n";
                frameTimingTrace << std::setprecision(12);
                for (const InteractiveFrameTimingSample& sample
                        : frameTimingSamples) {
                    const GpuTimingResult& gpu = application.gpuTiming(
                            sample.frame);
                    frameTimingTrace << sample.frame << '\t'
                            << sample.loopStartMilliseconds << '\t'
                            << sample.loopDeltaMilliseconds << '\t'
                            << sample.renderMilliseconds << '\t'
                            << sample.setupMilliseconds << '\t'
                            << sample.sceneMilliseconds << '\t'
                            << sample.bloomMilliseconds << '\t'
                            << sample.screenshotMilliseconds << '\t'
                            << sample.swapWaitMilliseconds << '\t'
                            << sample.swapReturnMilliseconds << '\t'
                            << sample.swapIntervalMilliseconds << '\t'
                            << sample.pollMilliseconds << '\t'
                            << sample.phaseDegrees << '\t'
                            << sample.physicalPoseDeltaDegrees << '\t'
                            << sample.filterDeltaDegrees << '\t'
                            << sample.nominalHz << '\t'
                            << sample.spinRps << '\t'
                            << sample.grooveCyclesPerFrame << '\t'
                            << sample.aliasEnvelopeCycles << '\t'
                            << sample.temporalBlend << '\t'
                            << sample.grooveContrast << '\t'
                            << sample.bandBlend << '\t'
                            << sample.motionBandEnergyWeight << '\t'
                            << sample.bloomCorrectionBlend << '\t'
                            << sample.coreIntensity << '\t'
                            << sample.temporalSampleCount << '\t'
                            << sample.emissionDrawCount << '\t'
                            << sample.cadenceTitleUpdate << '\t'
                            << sample.scheduledPhaseClock << '\t'
                            << sample.modelSlug << '\t'
                            << temporalModeName(sample.requestedTemporalMode) << '\t'
                            << temporalModeName(sample.effectiveTemporalMode) << '\t'
                            << temporalSourceName(sample.temporalSource) << '\t'
                            << sample.temporalGroovesAvailable << '\t'
                            << sample.mintGlowCount << '\t'
                            << diagnosticRunId << '\t'
                            << wheel_lab::diagnostics::gpuTimerStatusName(
                                    gpu.status) << '\t'
                            << gpu.disjointEpoch << '\t'
                            << gpu.queryLatencyFrames << '\t'
                            << gpu.startTimestampNanoseconds << '\t'
                            << gpu.endTimestampNanoseconds << '\t'
                            << gpu.setupMilliseconds << '\t'
                            << gpu.sceneMilliseconds << '\t'
                            << gpu.bloomMilliseconds << '\t'
                            << gpu.frameMilliseconds << '\t'
                            << presentationTracker.scanoutSourceName() << '\t'
                            << sample.scanoutValid << '\t'
                            << sample.scanoutCounterBefore << '\t'
                            << sample.scanoutCounterAfter << '\t'
                            << sample.scanoutCounterDelta << '\t'
                            << sample.scanoutQueryBeforeMilliseconds << '\t'
                            << sample.scanoutQueryAfterMilliseconds << '\t'
                            << presentationTracker.completionSourceName() << '\t'
                            << presentationTracker.completionEventCount() << '\t'
                            << (presentationTracker.exactMappingProven()
                                    ? 1 : 0) << '\n';
                }
                std::cout << "Captured " << frameTimingSamples.size()
                        << " interactive timing samples\n";
            }
        }
        glfwDestroyWindow(window);
        glfwTerminate();
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "wheel-mesh-lab: " << error.what() << '\n';
        return 1;
    }
}
