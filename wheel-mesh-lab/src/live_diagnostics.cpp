#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include <GLES3/gl31.h>
#include <GLES2/gl2ext.h>
#include <xcb/xcb.h>

#include "live_diagnostics.hpp"

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <dlfcn.h>
#include <fstream>
#include <iomanip>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <utility>
#include <vector>

namespace wheel_lab::diagnostics {
namespace {

using SteadyClock = std::chrono::steady_clock;

double milliseconds(const SteadyClock::duration duration) {
    return std::chrono::duration<double, std::milli>(duration).count();
}

bool hasGlExtension(const char* expected) {
    GLint count = 0;
    glGetIntegerv(GL_NUM_EXTENSIONS, &count);
    for (GLint index = 0; index < count; ++index) {
        const auto* value = reinterpret_cast<const char*>(glGetStringi(
                GL_EXTENSIONS, static_cast<GLuint>(index)));
        if (value != nullptr && std::strcmp(value, expected) == 0) {
            return true;
        }
    }
    return false;
}

template <typename Function>
Function loadGlFunction(const char* name) {
    return reinterpret_cast<Function>(glfwGetProcAddress(name));
}

constexpr std::size_t kGpuQueryRingSize = 256U;
constexpr std::size_t kGpuMarkersPerFrame = 4U;

struct GpuQueryFunctions {
    PFNGLGENQUERIESEXTPROC generate = nullptr;
    PFNGLDELETEQUERIESEXTPROC destroy = nullptr;
    PFNGLQUERYCOUNTEREXTPROC timestamp = nullptr;
    PFNGLGETQUERYIVEXTPROC getQuery = nullptr;
    PFNGLGETQUERYOBJECTUIVEXTPROC getObject = nullptr;
    PFNGLGETQUERYOBJECTUI64VEXTPROC getObject64 = nullptr;

    bool complete() const {
        return generate != nullptr && destroy != nullptr
                && timestamp != nullptr && getQuery != nullptr
                && getObject != nullptr && getObject64 != nullptr;
    }
};

struct GpuQuerySlot {
    std::array<GLuint, kGpuMarkersPerFrame> queries{};
    std::uint64_t frame = 0U;
    int disjointEpoch = 0;
    bool pending = false;
};

struct PresentQueryVersionCookie {
    unsigned int sequence = 0U;
};

struct PresentQueryVersionReply {
    std::uint8_t responseType;
    std::uint8_t padding;
    std::uint16_t sequence;
    std::uint32_t length;
    std::uint32_t majorVersion;
    std::uint32_t minorVersion;
};

struct PresentQueryCapabilitiesCookie {
    unsigned int sequence = 0U;
};

struct PresentQueryCapabilitiesReply {
    std::uint8_t responseType;
    std::uint8_t padding;
    std::uint16_t sequence;
    std::uint32_t length;
    std::uint32_t capabilities;
};

struct __attribute__((__packed__)) PresentGenericEvent {
    std::uint8_t responseType;
    std::uint8_t extension;
    std::uint16_t sequence;
    std::uint32_t length;
    std::uint16_t eventType;
    std::uint8_t padding[2];
    std::uint32_t event;
};

struct __attribute__((__packed__)) PresentCompleteNotifyEvent {
    std::uint8_t responseType;
    std::uint8_t extension;
    std::uint16_t sequence;
    std::uint32_t length;
    std::uint16_t eventType;
    std::uint8_t kind;
    std::uint8_t mode;
    std::uint32_t event;
    std::uint32_t window;
    std::uint32_t serial;
    std::uint64_t ust;
    std::uint64_t msc;
    // libxcb appends the reconstructed 32-bit sequence after the 40-byte
    // Present CompleteNotify wire payload.
    std::uint32_t fullSequence;
};

static_assert(sizeof(PresentCompleteNotifyEvent) == 44U,
        "unexpected X Present CompleteNotify wire layout");

constexpr std::uint16_t kPresentCompleteNotify = 1U;
constexpr std::uint8_t kPresentCompleteKindPixmap = 0U;
constexpr std::uint32_t kPresentCompleteNotifyMask = 2U;
constexpr std::uint32_t kPresentCapabilityUst = 1U << 2U;

using PresentQueryVersionProc = PresentQueryVersionCookie (*)(
        xcb_connection_t*, std::uint32_t, std::uint32_t);
using PresentQueryVersionReplyProc = PresentQueryVersionReply* (*)(
        xcb_connection_t*, PresentQueryVersionCookie, xcb_generic_error_t**);
using PresentQueryCapabilitiesProc = PresentQueryCapabilitiesCookie (*)(
        xcb_connection_t*, std::uint32_t);
using PresentQueryCapabilitiesReplyProc = PresentQueryCapabilitiesReply* (*)(
        xcb_connection_t*, PresentQueryCapabilitiesCookie, xcb_generic_error_t**);
using PresentSelectInputCheckedProc = xcb_void_cookie_t (*)(
        xcb_connection_t*, std::uint32_t, xcb_window_t, std::uint32_t);
using PresentExtensionId = xcb_extension_t;
using GetVideoSyncSgiProc = int (*)(unsigned int*);
// Keep the X11 native entry point optional. Ubuntu ships separate X11 and
// Wayland GLFW libraries with the same ABI; directly referencing
// glfwGetX11Window would prevent this binary from loading with the Wayland
// variant before the diagnostic could report that X11 timing is unavailable.
using GetX11WindowProc = unsigned long (*)(GLFWwindow*);

const char* presentModeName(const std::uint8_t mode) {
    switch (mode) {
        case 0U: return "copy";
        case 1U: return "flip";
        case 2U: return "skip";
        case 3U: return "suboptimal_copy";
        default: return "unknown";
    }
}

struct PendingSubmission {
    std::uint64_t frame = 0U;
    double localSubmissionMilliseconds = 0.0;
};

struct CompletionEvent {
    std::uint64_t eventIndex = 0U;
    std::int64_t candidateFrame = -1;
    double localArrivalMilliseconds = 0.0;
    double localSubmissionMilliseconds = -1.0;
    std::uint64_t ust = 0U;
    std::uint64_t msc = 0U;
    std::uint32_t serial = 0U;
    std::uint32_t window = 0U;
    std::uint8_t mode = 0U;
};

std::string jsonSafeTsvCell(const std::string& value) {
    std::string result = value;
    std::replace(result.begin(), result.end(), '\t', ' ');
    std::replace(result.begin(), result.end(), '\n', ' ');
    std::replace(result.begin(), result.end(), '\r', ' ');
    return result;
}

}  // namespace

const char* glfwRuntimeBackendName() {
    using GetPlatformProc = int (*)();
    const auto getPlatform = reinterpret_cast<GetPlatformProc>(
            dlsym(RTLD_DEFAULT, "glfwGetPlatform"));
    if (getPlatform != nullptr) {
        // GLFW 3.4 platform identifiers. Keep the numeric values local so the
        // lab remains source-compatible with Ubuntu's GLFW 3.3 headers.
        switch (getPlatform()) {
            case 0x00060001: return "win32";
            case 0x00060002: return "cocoa";
            case 0x00060003: return "wayland";
            case 0x00060004: return "x11";
            case 0x00060005: return "null";
            default: return "unknown";
        }
    }
    const bool hasWayland = dlsym(
            RTLD_DEFAULT, "glfwGetWaylandDisplay") != nullptr;
    const bool hasX11 = dlsym(RTLD_DEFAULT, "glfwGetX11Display") != nullptr;
    if (hasWayland != hasX11) {
        return hasWayland ? "wayland" : "x11";
    }
    return "unknown";
}

const char* gpuTimerStatusName(const GpuTimerStatus status) {
    switch (status) {
        case GpuTimerStatus::NotRequested: return "not_requested";
        case GpuTimerStatus::Pending: return "pending";
        case GpuTimerStatus::Ok: return "ok";
        case GpuTimerStatus::RingFull: return "ring_full";
        case GpuTimerStatus::Disjoint: return "disjoint";
        case GpuTimerStatus::InvalidTimestamps: return "invalid_timestamps";
        case GpuTimerStatus::PendingAtShutdown: return "pending_at_shutdown";
    }
    return "unknown";
}

struct GpuFrameTimer::Impl {
    bool requested = false;
    bool supported = false;
    bool frameActive = false;
    int disjointEpoch = 0;
    std::size_t activeSlot = 0U;
    std::uint64_t latestFrame = 0U;
    GpuQueryFunctions functions;
    std::array<GpuQuerySlot, kGpuQueryRingSize> slots;
    std::vector<GpuTimingResult> results;

    explicit Impl(const bool shouldRequest) : requested(shouldRequest) {
        if (!requested) {
            return;
        }
        if (!hasGlExtension("GL_EXT_disjoint_timer_query")) {
            throw std::runtime_error(
                    "--gpu-timing requires GL_EXT_disjoint_timer_query");
        }
        functions.generate = loadGlFunction<PFNGLGENQUERIESEXTPROC>(
                "glGenQueriesEXT");
        functions.destroy = loadGlFunction<PFNGLDELETEQUERIESEXTPROC>(
                "glDeleteQueriesEXT");
        functions.timestamp = loadGlFunction<PFNGLQUERYCOUNTEREXTPROC>(
                "glQueryCounterEXT");
        functions.getQuery = loadGlFunction<PFNGLGETQUERYIVEXTPROC>(
                "glGetQueryivEXT");
        functions.getObject = loadGlFunction<PFNGLGETQUERYOBJECTUIVEXTPROC>(
                "glGetQueryObjectuivEXT");
        functions.getObject64 = loadGlFunction<PFNGLGETQUERYOBJECTUI64VEXTPROC>(
                "glGetQueryObjectui64vEXT");
        if (!functions.complete()) {
            throw std::runtime_error(
                    "GL_EXT_disjoint_timer_query is advertised but its entry points are missing");
        }
        GLint counterBits = 0;
        functions.getQuery(
                GL_TIMESTAMP_EXT, GL_QUERY_COUNTER_BITS_EXT, &counterBits);
        if (counterBits < 30) {
            throw std::runtime_error(
                    "GL timestamp query counter has fewer than 30 useful bits");
        }
        for (GpuQuerySlot& slot : slots) {
            functions.generate(
                    static_cast<GLsizei>(slot.queries.size()),
                    slot.queries.data());
        }
        GLint ignoredDisjoint = 0;
        glGetIntegerv(GL_GPU_DISJOINT_EXT, &ignoredDisjoint);
        supported = true;
    }

    ~Impl() {
        if (!supported) {
            return;
        }
        for (GpuQuerySlot& slot : slots) {
            functions.destroy(
                    static_cast<GLsizei>(slot.queries.size()),
                    slot.queries.data());
        }
    }

    void ensureResult(const std::uint64_t frame) {
        if (frame >= results.size()) {
            results.resize(static_cast<std::size_t>(frame + 1U));
        }
    }

    void invalidatePendingForDisjoint() {
        ++disjointEpoch;
        for (GpuQuerySlot& slot : slots) {
            if (!slot.pending) {
                continue;
            }
            ensureResult(slot.frame);
            GpuTimingResult& result = results[static_cast<std::size_t>(slot.frame)];
            result.status = GpuTimerStatus::Disjoint;
            result.disjointEpoch = disjointEpoch;
            slot.pending = false;
        }
    }

    void collectAvailable(const std::uint64_t currentFrame) {
        if (!supported) {
            return;
        }
        GLint disjoint = 0;
        glGetIntegerv(GL_GPU_DISJOINT_EXT, &disjoint);
        if (disjoint != 0) {
            invalidatePendingForDisjoint();
            return;
        }
        for (GpuQuerySlot& slot : slots) {
            if (!slot.pending) {
                continue;
            }
            GLuint available = GL_FALSE;
            functions.getObject(
                    slot.queries.back(),
                    GL_QUERY_RESULT_AVAILABLE_EXT,
                    &available);
            if (available == GL_FALSE) {
                continue;
            }
            std::array<GLuint64, kGpuMarkersPerFrame> values{};
            for (std::size_t marker = 0U; marker < values.size(); ++marker) {
                functions.getObject64(
                        slot.queries[marker],
                        GL_QUERY_RESULT_EXT,
                        &values[marker]);
            }
            ensureResult(slot.frame);
            GpuTimingResult& result = results[static_cast<std::size_t>(slot.frame)];
            result.disjointEpoch = slot.disjointEpoch;
            const std::uint64_t latency = currentFrame >= slot.frame
                    ? currentFrame - slot.frame
                    : 0U;
            result.queryLatencyFrames = static_cast<int>(std::min<std::uint64_t>(
                    latency,
                    static_cast<std::uint64_t>(std::numeric_limits<int>::max())));
            const bool monotonic = values[0] <= values[1]
                    && values[1] <= values[2]
                    && values[2] <= values[3];
            if (!monotonic) {
                result.status = GpuTimerStatus::InvalidTimestamps;
            } else {
                constexpr double nanosecondsToMilliseconds = 1.0e-6;
                result.status = GpuTimerStatus::Ok;
                result.startTimestampNanoseconds = values[0];
                result.endTimestampNanoseconds = values[3];
                result.setupMilliseconds = static_cast<double>(
                        values[1] - values[0]) * nanosecondsToMilliseconds;
                result.sceneMilliseconds = static_cast<double>(
                        values[2] - values[1]) * nanosecondsToMilliseconds;
                result.bloomMilliseconds = static_cast<double>(
                        values[3] - values[2]) * nanosecondsToMilliseconds;
                result.frameMilliseconds = static_cast<double>(
                        values[3] - values[0]) * nanosecondsToMilliseconds;
            }
            slot.pending = false;
        }
    }
};

GpuFrameTimer::GpuFrameTimer(const bool requested)
    : impl_(std::make_unique<Impl>(requested)) {}

GpuFrameTimer::~GpuFrameTimer() = default;

void GpuFrameTimer::beginFrame(const std::uint64_t frame) {
    if (!impl_->supported) {
        return;
    }
    impl_->latestFrame = frame;
    impl_->collectAvailable(frame);
    impl_->ensureResult(frame);
    auto found = std::find_if(
            impl_->slots.begin(), impl_->slots.end(),
            [](const GpuQuerySlot& slot) { return !slot.pending; });
    if (found == impl_->slots.end()) {
        impl_->results[static_cast<std::size_t>(frame)].status =
                GpuTimerStatus::RingFull;
        impl_->frameActive = false;
        return;
    }
    impl_->activeSlot = static_cast<std::size_t>(
            std::distance(impl_->slots.begin(), found));
    found->frame = frame;
    found->disjointEpoch = impl_->disjointEpoch;
    found->pending = true;
    impl_->results[static_cast<std::size_t>(frame)].status =
            GpuTimerStatus::Pending;
    impl_->functions.timestamp(found->queries[0], GL_TIMESTAMP_EXT);
    impl_->frameActive = true;
}

void GpuFrameTimer::markSceneStart() {
    if (impl_->frameActive) {
        impl_->functions.timestamp(
                impl_->slots[impl_->activeSlot].queries[1], GL_TIMESTAMP_EXT);
    }
}

void GpuFrameTimer::markBloomStart() {
    if (impl_->frameActive) {
        impl_->functions.timestamp(
                impl_->slots[impl_->activeSlot].queries[2], GL_TIMESTAMP_EXT);
    }
}

void GpuFrameTimer::endFrame() {
    if (impl_->frameActive) {
        impl_->functions.timestamp(
                impl_->slots[impl_->activeSlot].queries[3], GL_TIMESTAMP_EXT);
        impl_->frameActive = false;
    }
}

void GpuFrameTimer::finalize() {
    if (!impl_->supported) {
        return;
    }
    glFinish();
    impl_->collectAvailable(impl_->latestFrame + 1U);
    for (GpuQuerySlot& slot : impl_->slots) {
        if (!slot.pending) {
            continue;
        }
        impl_->ensureResult(slot.frame);
        impl_->results[static_cast<std::size_t>(slot.frame)].status =
                GpuTimerStatus::PendingAtShutdown;
        slot.pending = false;
    }
}

bool GpuFrameTimer::requested() const { return impl_->requested; }
bool GpuFrameTimer::supported() const { return impl_->supported; }

const GpuTimingResult& GpuFrameTimer::result(const std::uint64_t frame) const {
    static const GpuTimingResult notRequested;
    if (frame >= impl_->results.size()) {
        return notRequested;
    }
    return impl_->results[static_cast<std::size_t>(frame)];
}

struct PresentationTracker::Impl {
    bool requested = false;
    bool scanoutSupported = false;
    bool presentSupported = false;
    bool finalized = false;
    bool exactMapping = false;
    std::string diagnosticRunId;
    GetVideoSyncSgiProc getVideoSync = nullptr;
    void* presentLibrary = nullptr;
    xcb_connection_t* connection = nullptr;
    PresentExtensionId* extensionId = nullptr;
    const xcb_query_extension_reply_t* extensionData = nullptr;
    PresentQueryVersionProc queryVersion = nullptr;
    PresentQueryVersionReplyProc queryVersionReply = nullptr;
    PresentQueryCapabilitiesProc queryCapabilities = nullptr;
    PresentQueryCapabilitiesReplyProc queryCapabilitiesReply = nullptr;
    PresentSelectInputCheckedProc selectInputChecked = nullptr;
    std::uint32_t presentCapabilities = 0U;
    std::uint32_t eventId = 0U;
    std::uint32_t windowId = 0U;
    std::size_t submittedCount = 0U;
    std::deque<PendingSubmission> pendingSubmissions;
    std::vector<CompletionEvent> completions;

    Impl(GLFWwindow* window, const bool shouldRequest, std::string runId)
        : requested(shouldRequest), diagnosticRunId(std::move(runId)) {
        if (!requested) {
            return;
        }

        const auto getX11Window = reinterpret_cast<GetX11WindowProc>(
                dlsym(RTLD_DEFAULT, "glfwGetX11Window"));
        if (getX11Window == nullptr) {
            return;
        }
        windowId = static_cast<std::uint32_t>(getX11Window(window));
        if (windowId == 0U) {
            return;
        }

        getVideoSync = loadGlFunction<GetVideoSyncSgiProc>(
                "glXGetVideoSyncSGI");
        if (getVideoSync != nullptr) {
            unsigned int counter = 0U;
            scanoutSupported = getVideoSync(&counter) == 0;
        }

        presentLibrary = dlopen("libxcb-present.so.0", RTLD_NOW | RTLD_LOCAL);
        if (presentLibrary == nullptr) {
            return;
        }
        queryVersion = reinterpret_cast<PresentQueryVersionProc>(
                dlsym(presentLibrary, "xcb_present_query_version"));
        queryVersionReply = reinterpret_cast<PresentQueryVersionReplyProc>(
                dlsym(presentLibrary, "xcb_present_query_version_reply"));
        queryCapabilities = reinterpret_cast<PresentQueryCapabilitiesProc>(
                dlsym(presentLibrary, "xcb_present_query_capabilities"));
        queryCapabilitiesReply = reinterpret_cast<
                PresentQueryCapabilitiesReplyProc>(dlsym(
                presentLibrary, "xcb_present_query_capabilities_reply"));
        selectInputChecked = reinterpret_cast<PresentSelectInputCheckedProc>(
                dlsym(presentLibrary, "xcb_present_select_input_checked"));
        extensionId = static_cast<PresentExtensionId*>(
                dlsym(presentLibrary, "xcb_present_id"));
        if (queryVersion == nullptr || queryVersionReply == nullptr
                || queryCapabilities == nullptr
                || queryCapabilitiesReply == nullptr
                || selectInputChecked == nullptr || extensionId == nullptr) {
            return;
        }
        int screen = 0;
        connection = xcb_connect(nullptr, &screen);
        if (connection == nullptr || xcb_connection_has_error(connection) != 0) {
            return;
        }
        extensionData = xcb_get_extension_data(connection, extensionId);
        if (extensionData == nullptr || extensionData->present == 0) {
            return;
        }
        xcb_generic_error_t* versionError = nullptr;
        PresentQueryVersionReply* version = queryVersionReply(
                connection, queryVersion(connection, 1U, 3U), &versionError);
        if (versionError != nullptr || version == nullptr) {
            std::free(versionError);
            std::free(version);
            return;
        }
        const bool usableVersion = version->majorVersion >= 1U;
        std::free(version);
        if (!usableVersion) {
            return;
        }
        xcb_generic_error_t* capabilitiesError = nullptr;
        PresentQueryCapabilitiesReply* capabilities = queryCapabilitiesReply(
                connection,
                queryCapabilities(connection, windowId),
                &capabilitiesError);
        if (capabilitiesError == nullptr && capabilities != nullptr) {
            presentCapabilities = capabilities->capabilities;
        }
        std::free(capabilitiesError);
        std::free(capabilities);
        eventId = xcb_generate_id(connection);
        const xcb_void_cookie_t selected = selectInputChecked(
                connection,
                eventId,
                static_cast<xcb_window_t>(windowId),
                kPresentCompleteNotifyMask);
        xcb_generic_error_t* selectionError = xcb_request_check(
                connection, selected);
        if (selectionError != nullptr) {
            std::free(selectionError);
            return;
        }
        xcb_flush(connection);
        presentSupported = true;
    }

    ~Impl() {
        if (connection != nullptr) {
            xcb_disconnect(connection);
        }
        if (presentLibrary != nullptr) {
            dlclose(presentLibrary);
        }
    }

    void poll(const double localArrivalMilliseconds) {
        if (!presentSupported || connection == nullptr) {
            return;
        }
        while (xcb_generic_event_t* raw = xcb_poll_for_event(connection)) {
            const std::uint8_t type = static_cast<std::uint8_t>(
                    raw->response_type & 0x7fU);
            if (type == XCB_GE_GENERIC) {
                const auto* generic = reinterpret_cast<const PresentGenericEvent*>(raw);
                if (generic->extension == extensionData->major_opcode
                        && generic->eventType == kPresentCompleteNotify) {
                    const auto* complete = reinterpret_cast<
                            const PresentCompleteNotifyEvent*>(raw);
                    if (complete->event == eventId
                            && complete->window == windowId
                            && complete->kind == kPresentCompleteKindPixmap) {
                        CompletionEvent event;
                        event.eventIndex = completions.size();
                        event.localArrivalMilliseconds = localArrivalMilliseconds;
                        event.ust = complete->ust;
                        event.msc = complete->msc;
                        event.serial = complete->serial;
                        event.window = complete->window;
                        event.mode = complete->mode;
                        if (!pendingSubmissions.empty()) {
                            event.candidateFrame = static_cast<std::int64_t>(
                                    pendingSubmissions.front().frame);
                            event.localSubmissionMilliseconds =
                                    pendingSubmissions.front()
                                            .localSubmissionMilliseconds;
                            pendingSubmissions.pop_front();
                        }
                        completions.push_back(event);
                    }
                }
            }
            std::free(raw);
        }
    }

    void validateMapping() {
        exactMapping = presentSupported
                && (presentCapabilities & kPresentCapabilityUst) != 0U
                && submittedCount > 0U
                && completions.size() == submittedCount
                && pendingSubmissions.empty();
        std::uint64_t previousMsc = 0U;
        std::uint64_t previousUst = 0U;
        std::int64_t previousFrame = -1;
        for (const CompletionEvent& event : completions) {
            exactMapping = exactMapping
                    && event.candidateFrame == previousFrame + 1
                    && event.mode != 2U
                    && event.ust > 0U
                    && event.msc > 0U
                    && (previousMsc == 0U || event.msc > previousMsc)
                    && (previousUst == 0U || event.ust > previousUst);
            previousFrame = event.candidateFrame;
            previousMsc = event.msc;
            previousUst = event.ust;
        }
    }

    void writeEvents(const std::filesystem::path& path) {
        if (!path.parent_path().empty()) {
            std::filesystem::create_directories(path.parent_path());
        }
        std::ofstream output(path);
        if (!output) {
            throw std::runtime_error(
                    "could not create presentation event trace: "
                    + path.string());
        }
        output
                << "diagnostic_run_id\tevent_index\tsource\tvalidity"
                << "\tmapped_submission_frame\tlocal_submission_ms"
                << "\tlocal_arrival_ms\tust_raw\tust_units\tmsc\tserial"
                << "\tmode\tust_delta_ms\tmsc_delta\twindow\n"
                << std::setprecision(12);
        std::uint64_t previousUst = 0U;
        std::uint64_t previousMsc = 0U;
        for (const CompletionEvent& event : completions) {
            const bool mapped = exactMapping && event.candidateFrame >= 0;
            output << jsonSafeTsvCell(diagnosticRunId) << '\t'
                    << event.eventIndex << "\txpresent_pixmap\t"
                    << (mapped ? "mapped" : "unmatched") << '\t';
            if (mapped) {
                output << event.candidateFrame;
            }
            output << '\t' << event.localSubmissionMilliseconds << '\t'
                    << event.localArrivalMilliseconds << '\t'
                    << event.ust << '\t'
                    << ((presentCapabilities & kPresentCapabilityUst) != 0U
                            ? "microseconds"
                            : "unsupported")
                    << '\t' << event.msc << '\t'
                    << event.serial << '\t' << presentModeName(event.mode) << '\t';
            if (previousUst != 0U && event.ust >= previousUst) {
                output << static_cast<double>(event.ust - previousUst) / 1000.0;
            }
            output << '\t';
            if (previousMsc != 0U && event.msc >= previousMsc) {
                output << event.msc - previousMsc;
            }
            output << '\t' << event.window << '\n';
            previousUst = event.ust;
            previousMsc = event.msc;
        }
        output.flush();
        if (!output) {
            throw std::runtime_error(
                    "could not finish presentation event trace: "
                    + path.string());
        }
    }
};

PresentationTracker::PresentationTracker(
        GLFWwindow* window,
        const bool requested,
        std::string diagnosticRunId)
    : impl_(std::make_unique<Impl>(
            window, requested, std::move(diagnosticRunId))) {}

PresentationTracker::~PresentationTracker() = default;

ScanoutObservation PresentationTracker::sampleScanoutCounter() {
    ScanoutObservation observation;
    if (!impl_->scanoutSupported || impl_->getVideoSync == nullptr) {
        return observation;
    }
    const auto start = SteadyClock::now();
    unsigned int counter = 0U;
    const int status = impl_->getVideoSync(&counter);
    const auto end = SteadyClock::now();
    observation.valid = status == 0;
    observation.counter = counter;
    observation.queryMilliseconds = milliseconds(end - start);
    return observation;
}

void PresentationTracker::noteSwapSubmission(
        const std::uint64_t frame,
        const double localSubmissionMilliseconds) {
    if (!impl_->requested) {
        return;
    }
    ++impl_->submittedCount;
    if (impl_->presentSupported) {
        impl_->pendingSubmissions.push_back(PendingSubmission{
                frame, localSubmissionMilliseconds});
    }
}

void PresentationTracker::pollEvents(const double localArrivalMilliseconds) {
    impl_->poll(localArrivalMilliseconds);
}

void PresentationTracker::finalize(
        const std::filesystem::path& eventTracePath) {
    if (!impl_->requested || impl_->finalized) {
        return;
    }
    impl_->poll(0.0);
    impl_->validateMapping();
    impl_->writeEvents(eventTracePath);
    impl_->finalized = true;
}

bool PresentationTracker::requested() const { return impl_->requested; }
bool PresentationTracker::scanoutClockSupported() const {
    return impl_->scanoutSupported;
}
bool PresentationTracker::xPresentSupported() const {
    return impl_->presentSupported;
}
const char* PresentationTracker::scanoutSourceName() const {
    return impl_->scanoutSupported ? "glx_sgi_video_sync" : "unavailable";
}
const char* PresentationTracker::completionSourceName() const {
    if (!impl_->presentSupported) {
        return "unavailable";
    }
    if (!impl_->finalized) {
        return "xpresent_pixmap_probe";
    }
    if (impl_->completions.empty()) {
        return "xpresent_no_pixmap_events";
    }
    return impl_->exactMapping
            ? "xpresent_pixmap_exact"
            : "xpresent_pixmap_unmapped";
}
std::size_t PresentationTracker::completionEventCount() const {
    return impl_->completions.size();
}
bool PresentationTracker::exactMappingProven() const {
    return impl_->exactMapping;
}

std::uint32_t wrappedScanoutDelta(
        const std::uint32_t before,
        const std::uint32_t after) {
    return after - before;
}

}  // namespace wheel_lab::diagnostics
