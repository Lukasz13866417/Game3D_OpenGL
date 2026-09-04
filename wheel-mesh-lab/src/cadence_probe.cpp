#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include <GLES3/gl31.h>

#include "live_diagnostics.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <optional>
#include <stdexcept>
#include <string>
#include <vector>

namespace fs = std::filesystem;

namespace {

using SteadyClock = std::chrono::steady_clock;

struct Options {
    double durationSeconds = 5.0;
    int swapInterval = 1;
    int width = 800;
    int height = 600;
    fs::path tracePath = "cadence-probe.tsv";
    fs::path presentationEventsPath;
    bool visible = true;
    bool useEglContext = false;
    bool fullscreen = false;
    int fullscreenRefreshHz = GLFW_DONT_CARE;
    std::optional<double> nominalHzOverride;
    bool validateOnly = false;
};

struct FrameTiming {
    std::uint64_t frame = 0U;
    double loopStartMilliseconds = 0.0;
    double loopDeltaMilliseconds = 0.0;
    double clearMilliseconds = 0.0;
    double swapWaitMilliseconds = 0.0;
    double swapReturnMilliseconds = 0.0;
    double swapReturnIntervalMilliseconds = 0.0;
    double pollMilliseconds = 0.0;
    double nominalHz = 0.0;
    int scanoutValid = 0;
    std::uint32_t scanoutCounterBefore = 0U;
    std::uint32_t scanoutCounterAfter = 0U;
    std::uint32_t scanoutCounterDelta = 0U;
    double scanoutQueryBeforeMilliseconds = 0.0;
    double scanoutQueryAfterMilliseconds = 0.0;
};

void glfwErrorCallback(int, const char* description) {
    std::cerr << "GLFW: " << description << '\n';
}

double parseFiniteDouble(
        const std::string& value,
        const std::string& optionName) {
    try {
        std::size_t parsedCharacters = 0U;
        const double result = std::stod(value, &parsedCharacters);
        if (parsedCharacters != value.size() || !std::isfinite(result)) {
            throw std::invalid_argument("invalid value");
        }
        return result;
    } catch (const std::exception&) {
        throw std::invalid_argument(optionName + " requires a finite number");
    }
}

int parseInteger(
        const std::string& value,
        const std::string& optionName) {
    try {
        std::size_t parsedCharacters = 0U;
        const int result = std::stoi(value, &parsedCharacters);
        if (parsedCharacters != value.size()) {
            throw std::invalid_argument("trailing characters");
        }
        return result;
    } catch (const std::exception&) {
        throw std::invalid_argument(optionName + " requires a whole number");
    }
}

Options parseOptions(const int argc, char** argv) {
    Options options;
    for (int index = 1; index < argc; ++index) {
        const std::string argument(argv[index]);
        if (argument.rfind("--duration-seconds=", 0U) == 0U) {
            options.durationSeconds = parseFiniteDouble(
                    argument.substr(std::string("--duration-seconds=").size()),
                    "--duration-seconds");
        } else if ((argument == "--duration-seconds" || argument == "--duration")
                && index + 1 < argc) {
            options.durationSeconds = parseFiniteDouble(
                    argv[++index], "--duration-seconds");
        } else if (argument.rfind("--swap-interval=", 0U) == 0U) {
            options.swapInterval = parseInteger(
                    argument.substr(std::string("--swap-interval=").size()),
                    "--swap-interval");
        } else if (argument == "--swap-interval" && index + 1 < argc) {
            options.swapInterval = parseInteger(
                    argv[++index], "--swap-interval");
        } else if (argument.rfind("--width=", 0U) == 0U) {
            options.width = parseInteger(
                    argument.substr(std::string("--width=").size()),
                    "--width");
        } else if (argument == "--width" && index + 1 < argc) {
            options.width = parseInteger(argv[++index], "--width");
        } else if (argument.rfind("--height=", 0U) == 0U) {
            options.height = parseInteger(
                    argument.substr(std::string("--height=").size()),
                    "--height");
        } else if (argument == "--height" && index + 1 < argc) {
            options.height = parseInteger(argv[++index], "--height");
        } else if (argument.rfind("--trace=", 0U) == 0U) {
            options.tracePath = argument.substr(std::string("--trace=").size());
        } else if (argument == "--trace" && index + 1 < argc) {
            options.tracePath = argv[++index];
        } else if (argument.rfind("--presentation-events=", 0U) == 0U) {
            options.presentationEventsPath = argument.substr(
                    std::string("--presentation-events=").size());
        } else if (argument == "--presentation-events"
                && index + 1 < argc) {
            options.presentationEventsPath = argv[++index];
        } else if (argument == "--hidden") {
            options.visible = false;
        } else if (argument == "--egl-window-context") {
            options.useEglContext = true;
        } else if (argument == "--fullscreen") {
            options.fullscreen = true;
        } else if (argument.rfind("--fullscreen-refresh=", 0U) == 0U) {
            options.fullscreenRefreshHz = parseInteger(
                    argument.substr(std::string("--fullscreen-refresh=").size()),
                    "--fullscreen-refresh");
            options.fullscreen = true;
        } else if (argument == "--fullscreen-refresh"
                && index + 1 < argc) {
            options.fullscreenRefreshHz = parseInteger(
                    argv[++index], "--fullscreen-refresh");
            options.fullscreen = true;
        } else if (argument.rfind("--nominal-hz=", 0U) == 0U) {
            options.nominalHzOverride = parseFiniteDouble(
                    argument.substr(std::string("--nominal-hz=").size()),
                    "--nominal-hz");
        } else if (argument == "--nominal-hz" && index + 1 < argc) {
            options.nominalHzOverride = parseFiniteDouble(
                    argv[++index], "--nominal-hz");
        } else if (argument == "--validate-only") {
            options.validateOnly = true;
        } else if (argument == "--help" || argument == "-h") {
            std::cout
                    << "wheel_cadence_probe "
                    << "[--duration-seconds SECONDS] [--swap-interval 0..4] "
                    << "[--width PIXELS --height PIXELS] [--trace FILE.tsv] "
                    << "[--presentation-events FILE.tsv] "
                    << "[--egl-window-context] [--fullscreen] "
                    << "[--fullscreen-refresh HZ] [--nominal-hz HZ] [--hidden] "
                    << "[--validate-only]\n\n"
                    << "The trace is buffered in memory and written only after the "
                    << "measurement; it is not asserted to be an actual display timestamp.\n";
            std::exit(0);
        } else {
            throw std::invalid_argument("unknown/incomplete option: " + argument);
        }
    }

    if (!(options.durationSeconds > 0.0) || options.durationSeconds > 60.0) {
        throw std::invalid_argument(
                "--duration-seconds must be greater than 0 and at most 60");
    }
    if (options.swapInterval < 0 || options.swapInterval > 4) {
        throw std::invalid_argument("--swap-interval must be in 0..4");
    }
    if (options.width < 1 || options.width > 8192
            || options.height < 1 || options.height > 8192) {
        throw std::invalid_argument("--width and --height must be in 1..8192");
    }
    if (options.tracePath.empty()) {
        throw std::invalid_argument("--trace must not be empty");
    }
    if (options.fullscreenRefreshHz != GLFW_DONT_CARE
            && (options.fullscreenRefreshHz < 1
                    || options.fullscreenRefreshHz > 1000)) {
        throw std::invalid_argument(
                "--fullscreen-refresh must be in 1..1000");
    }
    if (options.nominalHzOverride.has_value()
            && (!(*options.nominalHzOverride > 0.0)
                    || *options.nominalHzOverride > 1000.0)) {
        throw std::invalid_argument(
                "--nominal-hz must be greater than 0 and at most 1000");
    }
    if (!options.presentationEventsPath.empty() && options.useEglContext) {
        throw std::invalid_argument(
                "--presentation-events currently requires the native X11/GLX context");
    }
    return options;
}

double milliseconds(const SteadyClock::duration duration) {
    return std::chrono::duration<double, std::milli>(duration).count();
}

double percentile(std::vector<double> values, const double amount) {
    if (values.empty()) {
        return 0.0;
    }
    std::sort(values.begin(), values.end());
    const double position = amount * static_cast<double>(values.size() - 1U);
    const std::size_t lower = static_cast<std::size_t>(std::floor(position));
    const std::size_t upper = static_cast<std::size_t>(std::ceil(position));
    const double fraction = position - static_cast<double>(lower);
    return values[lower] + (values[upper] - values[lower]) * fraction;
}

void writeTrace(
        const fs::path& path,
        const std::vector<FrameTiming>& samples) {
    if (!path.parent_path().empty()) {
        fs::create_directories(path.parent_path());
    }
    std::ofstream output(path);
    if (!output) {
        throw std::runtime_error("could not write cadence trace: " + path.string());
    }
    output
            << "frame\tloop_start_ms\tloop_delta_ms\tclear_ms"
            << "\tswap_wait_ms\tswap_return_ms\tswap_return_interval_ms"
            << "\tpoll_ms\tnominal_hz\tscanout_source\tscanout_valid"
            << "\tscanout_counter_before\tscanout_counter_after"
            << "\tscanout_counter_delta\tscanout_query_before_ms"
            << "\tscanout_query_after_ms\n"
            << std::setprecision(12);
    for (const FrameTiming& sample : samples) {
        output << sample.frame << '\t'
                << sample.loopStartMilliseconds << '\t'
                << sample.loopDeltaMilliseconds << '\t'
                << sample.clearMilliseconds << '\t'
                << sample.swapWaitMilliseconds << '\t'
                << sample.swapReturnMilliseconds << '\t'
                << sample.swapReturnIntervalMilliseconds << '\t'
                << sample.pollMilliseconds << '\t'
                << sample.nominalHz << '\t'
                << (sample.scanoutValid != 0
                        ? "glx_sgi_video_sync"
                        : "unavailable") << '\t'
                << sample.scanoutValid << '\t'
                << sample.scanoutCounterBefore << '\t'
                << sample.scanoutCounterAfter << '\t'
                << sample.scanoutCounterDelta << '\t'
                << sample.scanoutQueryBeforeMilliseconds << '\t'
                << sample.scanoutQueryAfterMilliseconds << '\n';
    }
    output.flush();
    if (!output) {
        throw std::runtime_error("could not finish cadence trace: " + path.string());
    }
}

void printSummary(
        const std::vector<FrameTiming>& samples,
        const double nominalHz,
        const fs::path& tracePath) {
    std::vector<double> intervals;
    intervals.reserve(samples.size());
    for (std::size_t index = 1U; index < samples.size(); ++index) {
        intervals.push_back(samples[index].swapReturnIntervalMilliseconds);
    }
    const double nominalMilliseconds = nominalHz > 0.0
            ? 1000.0 / nominalHz
            : 0.0;
    const std::size_t longIntervals = nominalMilliseconds > 0.0
            ? static_cast<std::size_t>(std::count_if(
                    intervals.begin(),
                    intervals.end(),
                    [nominalMilliseconds](const double interval) {
                        return interval > 1.5 * nominalMilliseconds;
                    }))
            : 0U;
    const double maximum = intervals.empty()
            ? 0.0
            : *std::max_element(intervals.begin(), intervals.end());
    std::cout << std::fixed << std::setprecision(3)
            << "Cadence probe: " << samples.size() << " buffered frames"
            << ", nominal=" << nominalHz << " Hz"
            << ", median return interval=" << percentile(intervals, 0.5) << " ms"
            << ", p99=" << percentile(intervals, 0.99) << " ms"
            << ", max=" << maximum << " ms"
            << ", >1.5 nominal=" << longIntervals << '\n'
            << "Trace: " << tracePath << '\n';
}

}  // namespace

int main(int argc, char** argv) {
    GLFWwindow* window = nullptr;
    try {
        const Options options = parseOptions(argc, argv);
        if (options.validateOnly) {
            std::cout << "[ok] cadence probe options validated\n";
            return 0;
        }

        glfwSetErrorCallback(glfwErrorCallback);
        if (glfwInit() != GLFW_TRUE) {
            throw std::runtime_error("GLFW initialization failed");
        }
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_ES_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_SAMPLES, 0);
        glfwWindowHint(GLFW_VISIBLE, options.visible ? GLFW_TRUE : GLFW_FALSE);
        if (options.useEglContext) {
            glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        }
        if (options.fullscreenRefreshHz != GLFW_DONT_CARE) {
            glfwWindowHint(GLFW_REFRESH_RATE, options.fullscreenRefreshHz);
        }
        GLFWmonitor* fullscreenMonitor = options.fullscreen
                ? glfwGetPrimaryMonitor() : nullptr;
        int windowWidth = options.width;
        int windowHeight = options.height;
        if (fullscreenMonitor != nullptr) {
            const GLFWvidmode* mode = glfwGetVideoMode(fullscreenMonitor);
            if (mode != nullptr) {
                windowWidth = mode->width;
                windowHeight = mode->height;
            }
        }
        window = glfwCreateWindow(
                windowWidth,
                windowHeight,
                "Wheel cadence control",
                fullscreenMonitor,
                nullptr);
        if (window == nullptr) {
            throw std::runtime_error("could not create the cadence-probe window");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(options.swapInterval);

        int framebufferWidth = options.width;
        int framebufferHeight = options.height;
        glfwGetFramebufferSize(window, &framebufferWidth, &framebufferHeight);
        glViewport(0, 0, std::max(1, framebufferWidth), std::max(1, framebufferHeight));
        glClearColor(0.018F, 0.022F, 0.030F, 1.0F);

        double nominalHz = options.nominalHzOverride.value_or(0.0);
        if (!options.nominalHzOverride.has_value()) {
            GLFWmonitor* monitor = glfwGetPrimaryMonitor();
            if (monitor != nullptr) {
                const GLFWvidmode* mode = glfwGetVideoMode(monitor);
                if (mode != nullptr && mode->refreshRate > 0) {
                    nominalHz = static_cast<double>(mode->refreshRate)
                            / static_cast<double>(std::max(1, options.swapInterval));
                }
            }
        }

        std::cout << "Renderer: " << glGetString(GL_RENDERER) << '\n'
                << "OpenGL ES: " << glGetString(GL_VERSION) << '\n'
                << "Context: " << (options.useEglContext ? "EGL" : "GLFW native")
                << ", backend="
                << wheel_lab::diagnostics::glfwRuntimeBackendName()
                << ", swap interval=" << options.swapInterval
                << ", nominal=" << nominalHz << " Hz"
                << ", window=" << windowWidth << 'x' << windowHeight
                << (options.fullscreen ? " fullscreen" : " windowed") << '\n';

        // Reserving for 25,000 submissions/second avoids hot-loop allocations
        // even for the uncapped control on ordinary desktop drivers. The cap
        // bounds a pathological 60-second request to roughly 120 MiB.
        constexpr double expectedMaximumFramesPerSecond = 25000.0;
        constexpr std::size_t maximumBufferedFrames = 2000000U;
        const std::size_t bufferCapacity = std::min(
                maximumBufferedFrames,
                std::max<std::size_t>(
                        4096U,
                        static_cast<std::size_t>(std::ceil(
                                options.durationSeconds
                                * expectedMaximumFramesPerSecond))));
        std::vector<FrameTiming> samples;
        samples.reserve(bufferCapacity);

        const auto runStamp = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count();
        wheel_lab::diagnostics::PresentationTracker presentationTracker(
                window,
                !options.presentationEventsPath.empty(),
                "control-" + std::to_string(runStamp));
        if (!options.presentationEventsPath.empty()) {
            std::cout
                    << "Physical scanout clock: "
                    << presentationTracker.scanoutSourceName() << '\n'
                    << "Frame completion feedback: "
                    << presentationTracker.completionSourceName()
                    << " (mapping is accepted only after full-run validation)\n";
        }

        const auto start = SteadyClock::now();
        auto previousLoopStart = start;
        auto previousSwapReturn = start;
        bool firstSwap = true;
        bool bufferFull = false;
        std::uint64_t frame = 0U;
        while (glfwWindowShouldClose(window) == GLFW_FALSE) {
            const auto loopStart = SteadyClock::now();
            if (std::chrono::duration<double>(loopStart - start).count()
                    >= options.durationSeconds) {
                break;
            }
            glClear(GL_COLOR_BUFFER_BIT);
            const auto clearEnd = SteadyClock::now();
            const wheel_lab::diagnostics::ScanoutObservation scanoutBefore =
                    presentationTracker.sampleScanoutCounter();
            const auto swapStart = SteadyClock::now();
            presentationTracker.noteSwapSubmission(
                    frame, milliseconds(swapStart - start));
            glfwSwapBuffers(window);
            const auto swapReturn = SteadyClock::now();
            const wheel_lab::diagnostics::ScanoutObservation scanoutAfter =
                    presentationTracker.sampleScanoutCounter();
            glfwPollEvents();
            const auto pollEnd = SteadyClock::now();
            presentationTracker.pollEvents(milliseconds(pollEnd - start));

            if (samples.size() < samples.capacity()) {
                samples.push_back(FrameTiming{
                        frame,
                        milliseconds(loopStart - start),
                        milliseconds(loopStart - previousLoopStart),
                        milliseconds(clearEnd - loopStart),
                        milliseconds(swapReturn - swapStart),
                        milliseconds(swapReturn - start),
                        firstSwap
                                ? 0.0
                                : milliseconds(swapReturn - previousSwapReturn),
                        milliseconds(pollEnd - swapReturn),
                        nominalHz,
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
            } else {
                bufferFull = true;
            }
            firstSwap = false;
            previousLoopStart = loopStart;
            previousSwapReturn = swapReturn;
            ++frame;
        }

        const GLenum error = glGetError();
        if (!options.presentationEventsPath.empty()) {
            presentationTracker.finalize(options.presentationEventsPath);
        }
        glfwDestroyWindow(window);
        window = nullptr;
        glfwTerminate();

        writeTrace(options.tracePath, samples);
        printSummary(samples, nominalHz, options.tracePath);
        if (bufferFull) {
            throw std::runtime_error(
                    "cadence trace exceeded its preallocated buffer; "
                    "shorten the run or enable swap interval 1");
        }
        if (error != GL_NO_ERROR) {
            throw std::runtime_error(
                    "OpenGL ES error after cadence run: "
                    + std::to_string(static_cast<unsigned int>(error)));
        }
        return 0;
    } catch (const std::exception& error) {
        if (window != nullptr) {
            glfwDestroyWindow(window);
        }
        glfwTerminate();
        std::cerr << "wheel_cadence_probe: " << error.what() << '\n';
        return 1;
    }
}
