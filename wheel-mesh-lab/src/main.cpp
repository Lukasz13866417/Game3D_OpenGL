#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include <GLES3/gl31.h>

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

struct Options {
    bool smokeTest = false;
    bool exportAll = false;
    bool validateOnly = false;
    int selectedModel = 1;
    int width = 1180;
    int height = 820;
    int cameraPreset = 5;
    int mintGlowCount = 4;
    fs::path screenshotPath;
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

    void create(const int newWidth, const int newHeight, const bool withDepth) {
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
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA,
                GL_UNSIGNED_BYTE, nullptr);
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
            throw std::runtime_error("OpenGL ES framebuffer is incomplete");
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

struct FullscreenProgram {
    GLuint id = 0;
    GLint textureA = -1;
    GLint textureB = -1;
    GLint scalar = -1;
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
    FullscreenProgram blur;
    FullscreenProgram composite;

    BloomPipeline() = default;
    BloomPipeline(const BloomPipeline&) = delete;
    BloomPipeline& operator=(const BloomPipeline&) = delete;
    ~BloomPipeline() { destroyQuad(); }

    void initialize(const fs::path& shaderDirectory) {
        prefilter.destroy();
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

    void compositeToScreen() const {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(GL_FALSE);
        glDisable(GL_BLEND);
        glBindVertexArray(vao);

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

        // Keep a deterministic offscreen copy for screenshots/smoke tests, then
        // present exactly those pixels to the GLFW default framebuffer.
        glBindFramebuffer(GL_READ_FRAMEBUFFER, finalComposite.framebuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0U);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);
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
    Application(GLFWwindow* appWindow, std::vector<WheelModel> cpuModels, const int initialModel)
        : window_(appWindow), models_(std::move(cpuModels)), selectedModel_(initialModel) {
        loadAllShaders();
        grid_.upload(buildGrid());
        gpuModels_.reserve(models_.size());
        for (const auto& model : models_) {
            gpuModels_.push_back(uploadModel(model));
        }
        glfwSetWindowUserPointer(window_, this);
        glfwSetFramebufferSizeCallback(window_, framebufferSizeCallback);
        glfwSetCursorPosCallback(window_, cursorPositionCallback);
        glfwSetMouseButtonCallback(window_, mouseButtonCallback);
        glfwSetScrollCallback(window_, scrollCallback);
        glfwSetKeyCallback(window_, keyCallback);
        updateTitle();
    }

    void render(const float deltaSeconds) {
        if (autoRoll_) {
            modelRoll_ += deltaSeconds * 1.55F;
        }
        glfwGetFramebufferSize(window_, &framebufferWidth_, &framebufferHeight_);
        framebufferWidth_ = std::max(1, framebufferWidth_);
        framebufferHeight_ = std::max(1, framebufferHeight_);
        bloom_.resize(framebufferWidth_, framebufferHeight_);

        if (bloomEnabled_) {
            bloom_.beginScene();
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0U);
            glViewport(0, 0, framebufferWidth_, framebufferHeight_);
        }
        glClearColor(kBackground.r, kBackground.g, kBackground.b, 1.0F);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDisable(GL_CULL_FACE);  // Matches current gameplay state.

        const float aspect = static_cast<float>(framebufferWidth_)
                / static_cast<float>(framebufferHeight_);
        const glm::mat4 viewProjection = camera_.projection(aspect) * camera_.view();
        const glm::mat4 modelMatrix = glm::rotate(glm::mat4(1.0F), modelYaw_, {0.0F, 1.0F, 0.0F})
                * glm::rotate(glm::mat4(1.0F), modelTilt_, {0.0F, 0.0F, 1.0F})
                * glm::rotate(glm::mat4(1.0F), modelRoll_, {1.0F, 0.0F, 0.0F});
        const glm::mat4 modelViewProjection = viewProjection * modelMatrix;

        if (showGrid_) {
            drawLines(grid_, viewProjection);
        }

        drawSolid(modelMatrix, modelViewProjection);
        const GpuModel& gpuModel = gpuModels_.at(static_cast<std::size_t>(selectedModel_));
        if (showWireframe_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(gpuModel.wireframe, modelViewProjection);
        }
        if (showNormals_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(flatShading_ ? gpuModel.flatNormals : gpuModel.smoothNormals,
                    modelViewProjection);
        }
        if (showCollision_) {
            glDepthFunc(GL_LEQUAL);
            drawLines(gpuModel.collisionAndBounds, modelViewProjection);
        }
        glDepthFunc(GL_LESS);

        if (bloomEnabled_) {
            bloom_.compositeToScreen();
        }
        glBindVertexArray(0U);
        glUseProgram(0U);
    }

    void capture(const fs::path& path) const {
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
        std::cout << "Screenshot: " << path << " (" << visiblePixels
                << " non-background pixels)\n";
        const std::size_t pixelCount = pixels.size() / 4U;
        // The gameplay-distance preset intentionally occupies about 0.6% of a
        // 1180x820 capture. Grid and analysis overlays are disabled in smoke
        // mode, so a 0.25% floor still proves that the solid model rendered.
        if (visiblePixels < pixelCount / 400U) {
            throw std::runtime_error("smoke render contains too few visible pixels");
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
    std::vector<WheelModel> models_;
    std::vector<GpuModel> gpuModels_;
    LitProgram flatProgram_;
    LitProgram smoothProgram_;
    LineProgram lineProgram_;
    BloomPipeline bloom_;
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
    bool leftMouseDown_ = false;
    bool middleMouseDown_ = false;
    bool rightMouseDown_ = false;
    double previousMouseX_ = 0.0;
    double previousMouseY_ = 0.0;
    float modelYaw_ = 0.0F;
    float modelTilt_ = 0.0F;
    float modelRoll_ = 0.0F;
    std::optional<fs::path> pendingScreenshot_;

    void loadAllShaders() {
        const fs::path shaders(WHEEL_LAB_SHADER_DIR);
        flatProgram_.load(shaders / "flat_lit.vert", shaders / "flat_lit.frag");
        smoothProgram_.load(shaders / "smooth_lit.vert", shaders / "smooth_lit.frag");
        lineProgram_.load(shaders);
        bloom_.initialize(shaders);
    }

    void drawSolid(const glm::mat4& modelMatrix, const glm::mat4& mvp) const {
        const LitProgram& program = flatShading_ ? flatProgram_ : smoothProgram_;
        const GpuModel& gpuModel = gpuModels_.at(static_cast<std::size_t>(selectedModel_));
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

        for (std::size_t partIndex = 0; partIndex < gpuModel.parts.size(); ++partIndex) {
            if (isolatedPart_ >= 0 && static_cast<int>(partIndex) != isolatedPart_) {
                continue;
            }
            const GpuPart& part = gpuModel.parts[partIndex];
            const Material& material = part.material;
            glUniform4fv(program.color, 1, glm::value_ptr(material.color));
            glUniform1f(program.ambient, material.ambient);
            glUniform1f(program.diffuse, material.diffuse);
            glUniform1f(program.specular, material.specular);
            glUniform1f(program.shininess, std::max(1.0F, material.shininess));
            (flatShading_ ? part.flat : part.smooth).draw();
        }
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
        updateTitle();
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
        if (models_.empty()) {
            return;
        }
        const WheelModel& model = models_[static_cast<std::size_t>(selectedModel_)];
        std::ostringstream title;
        title << "Wheel Mesh Lab | " << model.name
                << " | " << (flatShading_ ? "game flat" : "game smooth")
                << " | bloom " << (bloomEnabled_ ? "on" : "off")
                << " | part ";
        if (isolatedPart_ < 0) {
            title << "all";
        } else {
            title << model.parts[static_cast<std::size_t>(isolatedPart_)].name;
        }
        glfwSetWindowTitle(window_, title.str().c_str());
    }

    void onCursorPosition(const double x, const double y) {
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
        camera_.distance = std::clamp(
                camera_.distance * std::exp(static_cast<float>(-offset) * 0.12F), 0.7F, 20.0F);
    }

    void onKey(const int key, const int action) {
        if (action != GLFW_PRESS) {
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
                case GLFW_KEY_SPACE: autoRoll_ = !autoRoll_; break;
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
    static void framebufferSizeCallback(GLFWwindow*, int, int) {}
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
        } else if (argument.rfind("--mint-glow-count=", 0) == 0) {
            options.mintGlowCount = parseIntegerOption(
                    argument.substr(std::string("--mint-glow-count=").size()),
                    "--mint-glow-count");
        } else if (argument == "--mint-glow-count" && i + 1 < argc) {
            options.mintGlowCount = parseIntegerOption(
                    argv[++i], "--mint-glow-count");
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
                    << "[--preset side|tread|three-quarter|gameplay] [--smoke-test] "
                    << "[--screenshot FILE.ppm] [--export-all] [--validate-only]\n";
            std::exit(0);
        } else {
            throw std::invalid_argument("unknown/incomplete option: " + argument);
        }
    }
    return options;
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
            << "  Space           auto-roll around +X axle\n"
            << "  E / P           export OBJ+MTL / save PPM screenshot\n"
            << "  H / R           hot-reload shaders / reset view\n\n";
}

}  // namespace

int main(int argc, char** argv) {
    try {
        const Options options = parseOptions(argc, argv);
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

        glfwSetErrorCallback(glfwErrorCallback);
        if (glfwInit() != GLFW_TRUE) {
            throw std::runtime_error("GLFW initialization failed");
        }
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_ES_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_SAMPLES, 0);  // Gameplay's bloom scene target is non-MSAA.
        glfwWindowHint(GLFW_VISIBLE, options.smokeTest ? GLFW_FALSE : GLFW_TRUE);
        GLFWwindow* window = glfwCreateWindow(
                options.width, options.height, "Wheel Mesh Lab", nullptr, nullptr);
        if (window == nullptr) {
            glfwTerminate();
            throw std::runtime_error("could not create a GLFW OpenGL ES 3.1 window");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(options.smokeTest ? 0 : 1);
        std::cout << "Renderer: " << glGetString(GL_RENDERER) << '\n'
                << "OpenGL ES: " << glGetString(GL_VERSION) << '\n'
                << "GLSL: " << glGetString(GL_SHADING_LANGUAGE_VERSION) << '\n';
        printControls();

        {
            Application application(window, std::move(models), options.selectedModel);
            application.setCameraPreset(options.cameraPreset);
            if (options.smokeTest) {
                application.prepareSmokeTest();
            }
            auto previous = std::chrono::steady_clock::now();
            int renderedFrames = 0;
            while (glfwWindowShouldClose(window) == GLFW_FALSE) {
                const auto now = std::chrono::steady_clock::now();
                const float delta = std::chrono::duration<float>(now - previous).count();
                previous = now;
                application.render(std::min(delta, 0.1F));
                if (options.smokeTest) {
                    throwOnGlError("smoke render");
                }
                ++renderedFrames;
                application.capturePendingScreenshot();
                if (options.smokeTest && renderedFrames >= 3) {
                    const WheelModel model = options.selectedModel == 0
                            ? wheel_lab::makeMintWheel(
                                    options.mintGlowCount)
                            : wheel_lab::makeVioletWheel();
                    const fs::path path = options.screenshotPath.empty()
                            ? fs::path(WHEEL_LAB_ROOT) / "build" / (model.slug + "-smoke.ppm")
                            : options.screenshotPath;
                    application.capture(path);
                    throwOnGlError("smoke screenshot");
                    break;
                }
                glfwSwapBuffers(window);
                glfwPollEvents();
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
