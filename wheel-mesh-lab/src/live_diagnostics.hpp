#pragma once

#include <cstdint>
#include <filesystem>
#include <memory>
#include <string>

struct GLFWwindow;

namespace wheel_lab::diagnostics {

// Reports the GLFW window-system backend without linking against an optional
// native-access symbol. This works with Ubuntu's separate GLFW 3.3 X11 and
// Wayland libraries and prefers glfwGetPlatform when GLFW 3.4+ provides it.
const char* glfwRuntimeBackendName();

enum class GpuTimerStatus {
    NotRequested,
    Pending,
    Ok,
    RingFull,
    Disjoint,
    InvalidTimestamps,
    PendingAtShutdown,
};

const char* gpuTimerStatusName(GpuTimerStatus status);

struct GpuTimingResult {
    GpuTimerStatus status = GpuTimerStatus::NotRequested;
    std::uint64_t startTimestampNanoseconds = 0U;
    std::uint64_t endTimestampNanoseconds = 0U;
    double setupMilliseconds = -1.0;
    double sceneMilliseconds = -1.0;
    double bloomMilliseconds = -1.0;
    double frameMilliseconds = -1.0;
    int disjointEpoch = 0;
    int queryLatencyFrames = -1;
};

// Records timestamp boundaries into a preallocated asynchronous query ring.
// Query results are never waited for or read before GL reports availability.
class GpuFrameTimer {
public:
    explicit GpuFrameTimer(bool requested);
    ~GpuFrameTimer();

    GpuFrameTimer(const GpuFrameTimer&) = delete;
    GpuFrameTimer& operator=(const GpuFrameTimer&) = delete;

    void beginFrame(std::uint64_t frame);
    void markSceneStart();
    void markBloomStart();
    void endFrame();
    void finalize();

    bool requested() const;
    bool supported() const;
    const GpuTimingResult& result(std::uint64_t frame) const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

struct ScanoutObservation {
    bool valid = false;
    std::uint32_t counter = 0U;
    double queryMilliseconds = 0.0;
};

// On an X11 GLFW runtime, uses a separate XCB connection for X Present events,
// so GLFW cannot consume the diagnostic events from its own Xlib queue.
// GLX_SGI_video_sync is sampled independently as a physical display-pipe
// retrace counter. On non-X11 runtimes both probes remain unavailable; the
// renderer and GPU timing diagnostics continue to work without fabricating an
// X11 result.
class PresentationTracker {
public:
    PresentationTracker(
            GLFWwindow* window,
            bool requested,
            std::string diagnosticRunId);
    ~PresentationTracker();

    PresentationTracker(const PresentationTracker&) = delete;
    PresentationTracker& operator=(const PresentationTracker&) = delete;

    ScanoutObservation sampleScanoutCounter();
    void noteSwapSubmission(std::uint64_t frame, double localSubmissionMilliseconds);
    void pollEvents(double localArrivalMilliseconds);
    void finalize(const std::filesystem::path& eventTracePath);

    bool requested() const;
    bool scanoutClockSupported() const;
    bool xPresentSupported() const;
    const char* scanoutSourceName() const;
    const char* completionSourceName() const;
    std::size_t completionEventCount() const;
    bool exactMappingProven() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

std::uint32_t wrappedScanoutDelta(
        std::uint32_t before,
        std::uint32_t after);

}  // namespace wheel_lab::diagnostics
