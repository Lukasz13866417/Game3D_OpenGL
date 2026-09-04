cmake_minimum_required(VERSION 3.20)

project(wheel_mesh_lab LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(glfw3 CONFIG REQUIRED)
find_package(glm CONFIG REQUIRED)
find_package(Python3 COMPONENTS Interpreter REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(GLESV2 REQUIRED IMPORTED_TARGET glesv2)
pkg_check_modules(EGL REQUIRED IMPORTED_TARGET egl)
pkg_check_modules(XCB REQUIRED IMPORTED_TARGET xcb)

add_executable(wheel_mesh_lab
    src/main.cpp
    src/live_diagnostics.cpp
    src/wheel_models.cpp
)

# Deliberately contains no wheel/model/shader code.  This clear+swap control
# distinguishes renderer regressions from window-system presentation behavior.
add_executable(wheel_cadence_probe
    src/cadence_probe.cpp
    src/live_diagnostics.cpp
)

target_link_libraries(wheel_mesh_lab PRIVATE
    glfw
    glm::glm
    PkgConfig::GLESV2
    PkgConfig::EGL
    PkgConfig::XCB
    ${CMAKE_DL_LIBS}
)

target_link_libraries(wheel_cadence_probe PRIVATE
    glfw
    PkgConfig::GLESV2
    PkgConfig::XCB
    ${CMAKE_DL_LIBS}
)

target_compile_definitions(wheel_mesh_lab PRIVATE
    WHEEL_LAB_ROOT="${CMAKE_CURRENT_SOURCE_DIR}"
    WHEEL_LAB_SHADER_DIR="${CMAKE_CURRENT_SOURCE_DIR}/shaders"
)

if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(wheel_mesh_lab PRIVATE
        -Wall -Wextra -Wpedantic -Wconversion -Wshadow
    )
    target_compile_options(wheel_cadence_probe PRIVATE
        -Wall -Wextra -Wpedantic -Wconversion -Wshadow
    )
endif()

enable_testing()
add_test(
    NAME cadence_probe_cli_validation
    COMMAND wheel_cadence_probe
        --validate-only
        --duration-seconds 0.1
        --swap-interval 1
        --width 64
        --height 64
        --trace ${CMAKE_CURRENT_BINARY_DIR}/cadence-probe-cli.tsv
)
# A real window-system smoke test is useful on developer machines, but should
# not make headless builders fail merely because they have no X display.
if(DEFINED ENV{DISPLAY} AND NOT "$ENV{DISPLAY}" STREQUAL "")
    add_test(
        NAME cadence_probe_live_smoke
        COMMAND wheel_cadence_probe
            --duration-seconds 0.1
            --swap-interval 1
            --width 64
            --height 64
            --hidden
            --trace ${CMAKE_CURRENT_BINARY_DIR}/cadence-probe-live-smoke.tsv
    )
    set_tests_properties(cadence_probe_live_smoke PROPERTIES TIMEOUT 10)
    add_test(
        NAME live_gpu_presentation_instrumentation_smoke
        COMMAND ${Python3_EXECUTABLE}
            ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_live_instrumentation.py
            --wheel-binary $<TARGET_FILE:wheel_mesh_lab>
            --probe-binary $<TARGET_FILE:wheel_cadence_probe>
            --output-dir ${CMAKE_CURRENT_BINARY_DIR}/live-instrumentation-smoke
    )
    set_tests_properties(
        live_gpu_presentation_instrumentation_smoke
        PROPERTIES TIMEOUT 25 SKIP_RETURN_CODE 77
    )
endif()
add_test(NAME procedural_mesh_validation COMMAND wheel_mesh_lab --validate-only)
add_test(
    NAME headless_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --spin-rps 5
        --spin-phase-degrees 30
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/headless-gles-smoke.ppm
)
add_test(
    NAME temporal_reference_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --temporal-mode reference
        --spin-rps 5
        --spin-phase-degrees 30
        --fps 120
        --width 320
        --height 240
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/temporal-reference-smoke.ppm
)
add_test(
    NAME temporal_band_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --temporal-mode band
        --spin-rps 22.3
        --spin-phase-degrees 30
        --fps 120
        --width 320
        --height 240
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/temporal-band-smoke.ppm
)
add_test(
    NAME temporal_adaptive_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --temporal-mode adaptive
        --spin-rps 22.3
        --spin-phase-degrees 30
        --fps 120
        --width 320
        --height 240
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/temporal-adaptive-smoke.ppm
)
add_test(
    NAME temporal_adaptive_raw_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --temporal-mode adaptive-raw
        --spin-rps 22.3
        --spin-phase-degrees 30
        --fps 120
        --width 320
        --height 240
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/temporal-adaptive-raw-smoke.ppm
)
add_test(
    NAME temporal_split_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --temporal-mode split
        --max-roll-step-deg 1.5
        --spin-rps 22.3
        --spin-phase-degrees 30
        --fps 120
        --width 320
        --height 240
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/temporal-split-smoke.ppm
)
add_test(
    NAME temporal_split_raw_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --temporal-mode split-raw
        --max-roll-step-deg 1.5
        --spin-rps 22.3
        --spin-phase-degrees 30
        --fps 120
        --width 320
        --height 240
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/temporal-split-raw-smoke.ppm
)
add_test(
    NAME temporal_alias_safe_gles_smoke
    COMMAND wheel_mesh_lab
        --smoke-test
        --model mint
        --preset tread
        --temporal-mode alias-safe
        --spin-rps 22.3
        --spin-phase-degrees 30
        --fps 120
        --width 320
        --height 240
        --screenshot ${CMAKE_CURRENT_BINARY_DIR}/temporal-alias-safe-smoke.ppm
)
add_test(
    NAME temporal_clean_mode_phase_invariance
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_alias_safe_phase_invariance.py
        --binary $<TARGET_FILE:wheel_mesh_lab>
)
add_test(
    NAME canonical_mint_export_validation
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_canonical_mint_export.py
)
add_test(
    NAME gameplay_shader_parity
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_shader_parity.py
)
add_test(
    NAME video_cadence_tools
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/tools/test_video_cadence.py
)
add_test(
    NAME frame_timing_fault_injection
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/test_frame_timing_analysis.py
)
add_test(
    NAME frame_timing_replay_fault_injection
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_timing_replay.py
        --binary $<TARGET_FILE:wheel_mesh_lab>
)
add_test(
    NAME perceptual_stutter_fault_injection
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/test_perceptual_stutter.py
)
add_test(
    NAME render_truth_fault_injection
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/test_render_truth_analysis.py
)
add_test(
    NAME render_truth_capture_smoke
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_render_truth_capture.py
        --binary $<TARGET_FILE:wheel_mesh_lab>
)
add_test(
    NAME live_stutter_diagnostic_cli
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/run_live_stutter_diagnostic.py
        --help
)
add_test(
    NAME live_stutter_diagnostic_model_validation
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/test_live_stutter_diagnostic.py
)
add_test(
    NAME direct_sequence_rerun_cleanup
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_sequence_rerun_cleanup.py
        --binary $<TARGET_FILE:wheel_mesh_lab>
)
add_test(
    NAME sequence_frame_limit_rejects_six_digit_indices
    COMMAND wheel_mesh_lab
        --validate-only
        --sequence-dir ${CMAKE_CURRENT_BINARY_DIR}/sequence-limit-check
        --sequence-frames 100001
)
set_tests_properties(
    sequence_frame_limit_rejects_six_digit_indices
    PROPERTIES WILL_FAIL TRUE
)
