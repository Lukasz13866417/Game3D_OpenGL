#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

#include <glm/vec3.hpp>
#include <glm/vec4.hpp>

namespace wheel_lab {

struct Vertex {
    glm::vec3 position{};
    glm::vec3 normal{};
};

struct CpuMesh {
    std::vector<Vertex> vertices;
    std::vector<std::uint32_t> indices;
};

struct Material {
    std::string name;
    glm::vec4 color{1.0F};
    float ambient = 0.3F;
    float diffuse = 0.6F;
    float specular = 0.04F;
    float shininess = 5.0F;
    bool luminous = false;
};

struct MeshPart {
    std::string name;
    CpuMesh smoothMesh;
    CpuMesh flatMesh;
    Material material;
};

struct WheelModel {
    std::string name;
    std::string slug;
    std::vector<MeshPart> parts;
    glm::vec3 boundsMin{0.0F};
    glm::vec3 boundsMax{0.0F};
};

struct ValidationReport {
    std::size_t vertices = 0;
    std::size_t triangles = 0;
    std::size_t degenerateTriangles = 0;
    glm::vec3 boundsMin{0.0F};
    glm::vec3 boundsMax{0.0F};
    float widthToDiameter = 0.0F;
    bool valid = false;
    std::string summary;
};

WheelModel makeMintWheel(int glowingGrooveCount = 4);
WheelModel makeVioletWheel();
ValidationReport validateModel(WheelModel& model);

// Writes one OBJ with groups/material assignments plus a sibling MTL. The current
// Android loader ignores these groups, but keeping them here makes the required
// future player-material extension explicit.
void exportObj(const WheelModel& model, const std::filesystem::path& objPath);

}  // namespace wheel_lab
