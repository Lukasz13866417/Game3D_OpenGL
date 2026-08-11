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

add_executable(wheel_mesh_lab
    src/main.cpp
    src/wheel_models.cpp
)

target_link_libraries(wheel_mesh_lab PRIVATE
    glfw
    glm::glm
    PkgConfig::GLESV2
)

target_compile_definitions(wheel_mesh_lab PRIVATE
    WHEEL_LAB_ROOT="${CMAKE_CURRENT_SOURCE_DIR}"
    WHEEL_LAB_SHADER_DIR="${CMAKE_CURRENT_SOURCE_DIR}/shaders"
)

if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(wheel_mesh_lab PRIVATE
        -Wall -Wextra -Wpedantic -Wconversion -Wshadow
    )
endif()

enable_testing()
add_test(NAME procedural_mesh_validation COMMAND wheel_mesh_lab --validate-only)
add_test(
    NAME gameplay_shader_parity
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/tools/check_shader_parity.py
)
