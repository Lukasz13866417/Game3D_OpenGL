#include "wheel_models.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <fstream>
#include <iomanip>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <unordered_map>
#include <utility>

#include <glm/geometric.hpp>
#include <glm/gtc/constants.hpp>
#include <glm/vec2.hpp>

namespace wheel_lab {
namespace {

constexpr float kEpsilon = 1.0e-7F;

struct ProfilePoint {
    float x;
    float radius;
};

void appendMesh(CpuMesh& destination, const CpuMesh& source) {
    const auto base = static_cast<std::uint32_t>(destination.vertices.size());
    destination.vertices.insert(
            destination.vertices.end(), source.vertices.begin(), source.vertices.end());
    destination.indices.reserve(destination.indices.size() + source.indices.size());
    for (const auto index : source.indices) {
        destination.indices.push_back(base + index);
    }
}

CpuMesh makeFlatShaded(const CpuMesh& source) {
    CpuMesh result;
    result.vertices.reserve(source.indices.size());
    result.indices.reserve(source.indices.size());
    for (std::size_t i = 0; i + 2 < source.indices.size(); i += 3) {
        const glm::vec3 a = source.vertices.at(source.indices[i]).position;
        const glm::vec3 b = source.vertices.at(source.indices[i + 1]).position;
        const glm::vec3 c = source.vertices.at(source.indices[i + 2]).position;
        glm::vec3 normal = glm::cross(b - a, c - a);
        const float length = glm::length(normal);
        normal = length > kEpsilon ? normal / length : glm::vec3(0.0F, 1.0F, 0.0F);
        for (const glm::vec3 position : {a, b, c}) {
            result.indices.push_back(static_cast<std::uint32_t>(result.vertices.size()));
            result.vertices.push_back({position, normal});
        }
    }
    return result;
}

void recalculateSmoothNormals(CpuMesh& mesh) {
    for (auto& vertex : mesh.vertices) {
        vertex.normal = glm::vec3(0.0F);
    }
    for (std::size_t i = 0; i + 2 < mesh.indices.size(); i += 3) {
        Vertex& a = mesh.vertices.at(mesh.indices[i]);
        Vertex& b = mesh.vertices.at(mesh.indices[i + 1]);
        Vertex& c = mesh.vertices.at(mesh.indices[i + 2]);
        const glm::vec3 face = glm::cross(b.position - a.position, c.position - a.position);
        a.normal += face;
        b.normal += face;
        c.normal += face;
    }
    for (auto& vertex : mesh.vertices) {
        const float length = glm::length(vertex.normal);
        vertex.normal = length > kEpsilon
                ? vertex.normal / length
                : glm::vec3(0.0F, 1.0F, 0.0F);
    }
}

CpuMesh sweepClosedProfile(
        const std::vector<ProfilePoint>& profile,
        const int radialSegments,
        const float thetaOffset = 0.0F) {
    if (profile.size() < 3 || radialSegments < 3) {
        throw std::invalid_argument("closed sweep needs at least three profile/radial samples");
    }

    CpuMesh mesh;
    const int profileCount = static_cast<int>(profile.size());
    mesh.vertices.reserve(static_cast<std::size_t>(radialSegments * profileCount));
    mesh.indices.reserve(static_cast<std::size_t>(radialSegments * profileCount * 6));

    for (int ring = 0; ring < radialSegments; ++ring) {
        const float theta = thetaOffset
                + glm::two_pi<float>() * static_cast<float>(ring)
                        / static_cast<float>(radialSegments);
        const float cosine = std::cos(theta);
        const float sine = std::sin(theta);
        for (int j = 0; j < profileCount; ++j) {
            const ProfilePoint& previous = profile[static_cast<std::size_t>(
                    (j + profileCount - 1) % profileCount)];
            const ProfilePoint& current = profile[static_cast<std::size_t>(j)];
            const ProfilePoint& next = profile[static_cast<std::size_t>((j + 1) % profileCount)];
            const float dx = next.x - previous.x;
            const float dr = next.radius - previous.radius;
            glm::vec3 normal(-dr, dx * cosine, dx * sine);
            const float normalLength = glm::length(normal);
            normal = normalLength > kEpsilon
                    ? normal / normalLength
                    : glm::vec3(0.0F, cosine, sine);
            mesh.vertices.push_back({
                    {current.x, current.radius * cosine, current.radius * sine}, normal});
        }
    }

    for (int ring = 0; ring < radialSegments; ++ring) {
        const int nextRing = (ring + 1) % radialSegments;
        for (int j = 0; j < profileCount; ++j) {
            const int nextJ = (j + 1) % profileCount;
            const auto a = static_cast<std::uint32_t>(ring * profileCount + j);
            const auto b = static_cast<std::uint32_t>(nextRing * profileCount + j);
            const auto c = static_cast<std::uint32_t>(ring * profileCount + nextJ);
            const auto d = static_cast<std::uint32_t>(nextRing * profileCount + nextJ);
            mesh.indices.insert(mesh.indices.end(), {a, b, c, b, d, c});
        }
    }
    return mesh;
}

CpuMesh makeTorus(
        const float centerX,
        const float majorRadius,
        const float radialTubeRadius,
        const float axialTubeRadius,
        const int radialSegments,
        const int tubeSegments) {
    std::vector<ProfilePoint> profile;
    profile.reserve(static_cast<std::size_t>(tubeSegments));
    for (int i = 0; i < tubeSegments; ++i) {
        const float phi = glm::two_pi<float>() * static_cast<float>(i)
                / static_cast<float>(tubeSegments);
        profile.push_back({
                centerX + axialTubeRadius * std::sin(phi),
                majorRadius + radialTubeRadius * std::cos(phi)});
    }
    return sweepClosedProfile(profile, radialSegments);
}

CpuMesh makeMintMotionBand() {
    static_assert(
            kMintMotionBandRadialSegments % kMintChevronCount == 0,
            "motion-band tessellation should align with the authored repeat pitch");
    constexpr float majorRadius = 0.365F;
    constexpr float radialTubeRadius = 0.128F;
    constexpr float axialTubeRadius = 0.126F;
    constexpr int axialSamples = kMintMotionBandAxialSegments + 1;

    // This is intentionally an open, outward-facing tread skin. Closing it at
    // +/-X would create luminous shoulder/side faces which are not part of the
    // rolling groove exposure. The 360-way sweep limits the maximum radial
    // polygon sagitta to <2e-5 authoring units (well below one screen pixel).
    CpuMesh mesh;
    mesh.vertices.reserve(static_cast<std::size_t>(
            kMintMotionBandRadialSegments * axialSamples));
    mesh.indices.reserve(static_cast<std::size_t>(
            kMintMotionBandRadialSegments
                    * kMintMotionBandAxialSegments * 6));

    for (int ring = 0; ring < kMintMotionBandRadialSegments; ++ring) {
        const float theta = glm::two_pi<float>() * static_cast<float>(ring)
                / static_cast<float>(kMintMotionBandRadialSegments);
        const float cosine = std::cos(theta);
        const float sine = std::sin(theta);
        for (int axial = 0; axial < axialSamples; ++axial) {
            const float amount = static_cast<float>(axial)
                    / static_cast<float>(kMintMotionBandAxialSegments);
            const float x = -kMintMotionBandTreadHalfSpan
                    + 2.0F * kMintMotionBandTreadHalfSpan * amount;
            const float normalizedX = std::clamp(
                    x / axialTubeRadius, -0.999F, 0.999F);
            const float radius = majorRadius + radialTubeRadius
                    * std::sqrt(std::max(
                            0.0F, 1.0F - normalizedX * normalizedX));
            const float radialGradient = (radius - majorRadius)
                    / (radialTubeRadius * radialTubeRadius);
            glm::vec3 normal(
                    x / (axialTubeRadius * axialTubeRadius),
                    radialGradient * cosine,
                    radialGradient * sine);
            normal = glm::normalize(normal);
            const glm::vec3 crownPosition(
                    x, radius * cosine, radius * sine);
            mesh.vertices.push_back({
                    crownPosition + normal * kMintMotionBandSurfaceOffset,
                    normal});
        }
    }

    for (int ring = 0; ring < kMintMotionBandRadialSegments; ++ring) {
        const int nextRing = (ring + 1) % kMintMotionBandRadialSegments;
        for (int axial = 0; axial < kMintMotionBandAxialSegments; ++axial) {
            const auto a = static_cast<std::uint32_t>(ring * axialSamples + axial);
            const auto b = static_cast<std::uint32_t>(nextRing * axialSamples + axial);
            const auto c = a + 1U;
            const auto d = b + 1U;
            mesh.indices.insert(mesh.indices.end(), {a, b, c, b, d, c});
        }
    }
    return mesh;
}

CpuMesh makeAnnulus(
        const float x,
        const float innerRadius,
        const float outerRadius,
        const int segments,
        const float normalSign) {
    CpuMesh mesh;
    const glm::vec3 normal(normalSign, 0.0F, 0.0F);
    mesh.vertices.reserve(static_cast<std::size_t>(segments * 2));
    mesh.indices.reserve(static_cast<std::size_t>(segments * 6));
    for (int i = 0; i < segments; ++i) {
        const float theta = glm::two_pi<float>() * static_cast<float>(i)
                / static_cast<float>(segments);
        const float cosine = std::cos(theta);
        const float sine = std::sin(theta);
        mesh.vertices.push_back({{x, innerRadius * cosine, innerRadius * sine}, normal});
        mesh.vertices.push_back({{x, outerRadius * cosine, outerRadius * sine}, normal});
    }
    for (int i = 0; i < segments; ++i) {
        const int next = (i + 1) % segments;
        const auto inner = static_cast<std::uint32_t>(i * 2);
        const auto outer = inner + 1U;
        const auto nextInner = static_cast<std::uint32_t>(next * 2);
        const auto nextOuter = nextInner + 1U;
        if (normalSign > 0.0F) {
            mesh.indices.insert(
                    mesh.indices.end(), {inner, outer, nextInner, outer, nextOuter, nextInner});
        } else {
            mesh.indices.insert(
                    mesh.indices.end(), {inner, nextInner, outer, outer, nextInner, nextOuter});
        }
    }
    return mesh;
}

void appendQuad(
        CpuMesh& mesh,
        const glm::vec3& a,
        const glm::vec3& b,
        const glm::vec3& c,
        const glm::vec3& d,
        const glm::vec3& normal) {
    const auto base = static_cast<std::uint32_t>(mesh.vertices.size());
    mesh.vertices.insert(mesh.vertices.end(), {
            {a, normal}, {b, normal}, {c, normal}, {d, normal}});
    mesh.indices.insert(mesh.indices.end(), {
            base, base + 1U, base + 2U, base, base + 2U, base + 3U});
}

CpuMesh makeChevronStrip(
        const float theta,
        const float halfSpan,
        const float rise,
        const float halfStroke,
        const float tipLength) {
    const glm::vec2 left(-halfSpan, rise * 0.5F);
    const glm::vec2 apex(0.0F, -rise * 0.5F);
    const glm::vec2 right(halfSpan, rise * 0.5F);
    const glm::vec2 leftDirection = glm::normalize(apex - left);
    const glm::vec2 rightDirection = glm::normalize(right - apex);
    const glm::vec2 leftNormal(-leftDirection.y, leftDirection.x);
    const glm::vec2 rightNormal(-rightDirection.y, rightDirection.x);
    const glm::vec2 miter = glm::normalize(leftNormal + rightNormal);
    const float miterScale = halfStroke / glm::dot(miter, rightNormal);

    // One closed, mitred footprint. Both arms share the same inner and outer
    // apex, and the two end-cap vertices make the shoulder tips triangular.
    std::vector<glm::vec2> footprint = {
            left + leftNormal * halfStroke,
            apex + miter * miterScale,
            right + rightNormal * halfStroke,
            right + rightDirection * tipLength,
            right - rightNormal * halfStroke,
            apex - miter * miterScale,
            left - leftNormal * halfStroke,
            left - leftDirection * tipLength};
    constexpr float majorRadius = 0.365F;
    constexpr float radialTubeRadius = 0.128F;
    constexpr float axialTubeRadius = 0.126F;
    constexpr float baseHeight = -0.003F;
    constexpr float topHeight = 0.004F;

    const auto surfaceRadius = [](const float x) {
        const float normalizedX = std::clamp(
                x / axialTubeRadius, -0.999F, 0.999F);
        return majorRadius + radialTubeRadius
                * std::sqrt(std::max(0.0F, 1.0F - normalizedX * normalizedX));
    };
    const auto crownNormal = [&](const glm::vec2& point) {
        const float radius = surfaceRadius(point.x);
        const float pointTheta = theta + point.y / radius;
        const float radialGradient = (radius - majorRadius)
                / (radialTubeRadius * radialTubeRadius);
        glm::vec3 normal(
                point.x / (axialTubeRadius * axialTubeRadius),
                radialGradient * std::cos(pointTheta),
                radialGradient * std::sin(pointTheta));
        return glm::normalize(normal);
    };
    const auto positionAt = [&](const glm::vec2& point, const float height) {
        const float radius = surfaceRadius(point.x);
        const float pointTheta = theta + point.y / radius;
        const glm::vec3 surface(
                point.x,
                radius * std::cos(pointTheta),
                radius * std::sin(pointTheta));
        return surface + crownNormal(point) * height;
    };

    CpuMesh mesh;
    const auto appendSurfaceTriangle = [&] (
            const glm::vec2& pointA,
            const glm::vec2& pointB,
            const glm::vec2& pointC,
            const float height,
            const float normalSign) {
        glm::vec3 positionA = positionAt(pointA, height);
        glm::vec3 positionB = positionAt(pointB, height);
        glm::vec3 positionC = positionAt(pointC, height);
        glm::vec3 normalA = crownNormal(pointA) * normalSign;
        glm::vec3 normalB = crownNormal(pointB) * normalSign;
        glm::vec3 normalC = crownNormal(pointC) * normalSign;
        const glm::vec3 desiredNormal = normalA + normalB + normalC;
        if (glm::dot(glm::cross(positionB - positionA, positionC - positionA),
                    desiredNormal) < 0.0F) {
            std::swap(positionB, positionC);
            std::swap(normalB, normalC);
        }
        const auto base = static_cast<std::uint32_t>(mesh.vertices.size());
        mesh.vertices.insert(mesh.vertices.end(), {
                {positionA, normalA}, {positionB, normalB}, {positionC, normalC}});
        mesh.indices.insert(mesh.indices.end(), {base, base + 1U, base + 2U});
    };
    const auto appendSurfaceQuad = [&] (
            const glm::vec2& pointA,
            const glm::vec2& pointB,
            const glm::vec2& pointC,
            const glm::vec2& pointD,
            const float height,
            const float normalSign) {
        appendSurfaceTriangle(pointA, pointB, pointC, height, normalSign);
        appendSurfaceTriangle(pointA, pointC, pointD, height, normalSign);
    };
    const auto appendRibbon = [&] (
            const glm::vec2& startA,
            const glm::vec2& startB,
            const glm::vec2& endA,
            const glm::vec2& endB) {
        constexpr int lengthSteps = 12;
        constexpr int widthSteps = 3;
        for (int lengthIndex = 0; lengthIndex < lengthSteps; ++lengthIndex) {
            const float length0 = static_cast<float>(lengthIndex)
                    / static_cast<float>(lengthSteps);
            const float length1 = static_cast<float>(lengthIndex + 1)
                    / static_cast<float>(lengthSteps);
            const glm::vec2 edge0A = glm::mix(startA, endA, length0);
            const glm::vec2 edge0B = glm::mix(startB, endB, length0);
            const glm::vec2 edge1A = glm::mix(startA, endA, length1);
            const glm::vec2 edge1B = glm::mix(startB, endB, length1);
            for (int widthIndex = 0; widthIndex < widthSteps; ++widthIndex) {
                const float width0 = static_cast<float>(widthIndex)
                        / static_cast<float>(widthSteps);
                const float width1 = static_cast<float>(widthIndex + 1)
                        / static_cast<float>(widthSteps);
                const glm::vec2 a = glm::mix(edge0A, edge0B, width0);
                const glm::vec2 b = glm::mix(edge1A, edge1B, width0);
                const glm::vec2 c = glm::mix(edge1A, edge1B, width1);
                const glm::vec2 d = glm::mix(edge0A, edge0B, width1);
                appendSurfaceQuad(a, b, c, d, topHeight, 1.0F);
                appendSurfaceQuad(a, d, c, b, baseHeight, -1.0F);
            }
        }
    };

    // Two ruled ribbons share the exact same mitred apex edge. Subdivision is
    // essential: a single large triangle is a chord through the convex tire and
    // gets hidden by the carcass even though its boundary vertices are outside.
    appendRibbon(footprint[0], footprint[6], footprint[1], footprint[5]);
    appendRibbon(footprint[1], footprint[5], footprint[2], footprint[4]);
    appendSurfaceTriangle(footprint[0], footprint[6], footprint[7], topHeight, 1.0F);
    appendSurfaceTriangle(footprint[0], footprint[7], footprint[6], baseHeight, -1.0F);
    appendSurfaceTriangle(footprint[2], footprint[3], footprint[4], topHeight, 1.0F);
    appendSurfaceTriangle(footprint[2], footprint[4], footprint[3], baseHeight, -1.0F);

    float twiceArea = 0.0F;
    for (std::size_t i = 0; i < footprint.size(); ++i) {
        const glm::vec2& current = footprint[i];
        const glm::vec2& next = footprint[(i + 1U) % footprint.size()];
        twiceArea += current.x * next.y - next.x * current.y;
    }
    const float windingSign = twiceArea >= 0.0F ? 1.0F : -1.0F;
    for (std::size_t i = 0; i < footprint.size(); ++i) {
        const glm::vec2 start = footprint[i];
        const glm::vec2 end = footprint[(i + 1U) % footprint.size()];
        const glm::vec2 edge = end - start;
        const int steps = std::max(1, static_cast<int>(
                std::ceil(glm::length(edge) / 0.020F)));
        for (int step = 0; step < steps; ++step) {
            const float amount0 = static_cast<float>(step) / static_cast<float>(steps);
            const float amount1 = static_cast<float>(step + 1) / static_cast<float>(steps);
            const glm::vec2 pointA = glm::mix(start, end, amount0);
            const glm::vec2 pointB = glm::mix(start, end, amount1);
            const glm::vec2 midpoint = (pointA + pointB) * 0.5F;
            glm::vec2 outwardUv(edge.y, -edge.x);
            outwardUv *= windingSign;
            const glm::vec3 midpointWorld = positionAt(midpoint, topHeight);
            const glm::vec3 derivativeX = positionAt(midpoint + glm::vec2(0.001F, 0.0F),
                    topHeight) - midpointWorld;
            const glm::vec3 derivativeY = positionAt(midpoint + glm::vec2(0.0F, 0.001F),
                    topHeight) - midpointWorld;
            const glm::vec3 outwardWorld = derivativeX * outwardUv.x
                    + derivativeY * outwardUv.y;
            const glm::vec3 bottomA = positionAt(pointA, baseHeight);
            const glm::vec3 bottomB = positionAt(pointB, baseHeight);
            const glm::vec3 topB = positionAt(pointB, topHeight);
            const glm::vec3 topA = positionAt(pointA, topHeight);
            glm::vec3 faceNormal = glm::normalize(
                    glm::cross(bottomB - bottomA, topB - bottomA));
            if (glm::dot(faceNormal, outwardWorld) >= 0.0F) {
                appendQuad(mesh, bottomA, bottomB, topB, topA, faceNormal);
            } else {
                appendQuad(mesh, bottomB, bottomA, topA, topB, -faceNormal);
            }
        }
    }
    return mesh;
}

CpuMesh makeSweepSegment(
        const std::vector<ProfilePoint>& profile,
        const float startTheta,
        const float endTheta,
        const int angularSteps) {
    if (profile.size() < 3 || angularSteps < 1) {
        throw std::invalid_argument("sweep segment needs a closed profile and angular step");
    }
    CpuMesh mesh;
    const int profileCount = static_cast<int>(profile.size());
    const int rings = angularSteps + 1;
    mesh.vertices.reserve(static_cast<std::size_t>(rings * profileCount + 2));
    for (int ring = 0; ring < rings; ++ring) {
        const float t = static_cast<float>(ring) / static_cast<float>(angularSteps);
        const float theta = startTheta + (endTheta - startTheta) * t;
        const float cosine = std::cos(theta);
        const float sine = std::sin(theta);
        for (const auto& point : profile) {
            mesh.vertices.push_back({
                    {point.x, point.radius * cosine, point.radius * sine},
                    {0.0F, cosine, sine}});
        }
    }
    for (int ring = 0; ring < angularSteps; ++ring) {
        for (int j = 0; j < profileCount; ++j) {
            const int nextJ = (j + 1) % profileCount;
            const auto a = static_cast<std::uint32_t>(ring * profileCount + j);
            const auto b = static_cast<std::uint32_t>((ring + 1) * profileCount + j);
            const auto c = static_cast<std::uint32_t>(ring * profileCount + nextJ);
            const auto d = static_cast<std::uint32_t>((ring + 1) * profileCount + nextJ);
            mesh.indices.insert(mesh.indices.end(), {a, b, c, b, d, c});
        }
    }

    // Triangle fans cap both angular ends. A center in profile space is enough
    // because all profiles used here are convex armor-pod cross sections.
    ProfilePoint profileCenter{0.0F, 0.0F};
    for (const auto& point : profile) {
        profileCenter.x += point.x;
        profileCenter.radius += point.radius;
    }
    profileCenter.x /= static_cast<float>(profileCount);
    profileCenter.radius /= static_cast<float>(profileCount);
    const auto appendCap = [&](const int ring, const float theta, const bool reverse) {
        const auto centerIndex = static_cast<std::uint32_t>(mesh.vertices.size());
        mesh.vertices.push_back({
                {profileCenter.x, profileCenter.radius * std::cos(theta),
                        profileCenter.radius * std::sin(theta)},
                {0.0F, -std::sin(theta), std::cos(theta)}});
        for (int j = 0; j < profileCount; ++j) {
            const int nextJ = (j + 1) % profileCount;
            const auto a = static_cast<std::uint32_t>(ring * profileCount + j);
            const auto b = static_cast<std::uint32_t>(ring * profileCount + nextJ);
            if (reverse) {
                mesh.indices.insert(mesh.indices.end(), {centerIndex, b, a});
            } else {
                mesh.indices.insert(mesh.indices.end(), {centerIndex, a, b});
            }
        }
    };
    appendCap(0, startTheta, true);
    appendCap(angularSteps, endTheta, false);
    recalculateSmoothNormals(mesh);
    return mesh;
}

Material material(
        std::string name,
        const glm::vec4 color,
        const float ambient,
        const float diffuse,
        const float specular,
        const float shininess,
        const bool luminous = false) {
    return {std::move(name), color, ambient, diffuse, specular, shininess, luminous};
}

MeshPart part(std::string name, CpuMesh smoothMesh, Material partMaterial) {
    MeshPart result;
    result.name = std::move(name);
    result.flatMesh = makeFlatShaded(smoothMesh);
    result.smoothMesh = std::move(smoothMesh);
    result.material = std::move(partMaterial);
    return result;
}

bool validateMintMotionBandPart(const MeshPart& meshPart) {
    constexpr int axialSamples = kMintMotionBandAxialSegments + 1;
    const CpuMesh& mesh = meshPart.smoothMesh;
    const std::size_t expectedVertices = static_cast<std::size_t>(
            kMintMotionBandRadialSegments * axialSamples);
    const std::size_t expectedIndices = static_cast<std::size_t>(
            kMintMotionBandRadialSegments
                    * kMintMotionBandAxialSegments * 6);
    if (meshPart.material.name != kMintMotionBandMaterialName
            || !meshPart.material.luminous
            || mesh.vertices.size() != expectedVertices
            || mesh.indices.size() != expectedIndices) {
        return false;
    }

    float maximumRadius = 0.0F;
    for (int ring = 0; ring < kMintMotionBandRadialSegments; ++ring) {
        for (int axial = 0; axial < axialSamples; ++axial) {
            const Vertex& vertex = mesh.vertices[static_cast<std::size_t>(
                    ring * axialSamples + axial)];
            const Vertex& reference = mesh.vertices[static_cast<std::size_t>(axial)];
            const float radius = std::hypot(vertex.position.y, vertex.position.z);
            const float referenceRadius = std::hypot(
                    reference.position.y, reference.position.z);
            const float normalRadial = std::hypot(vertex.normal.y, vertex.normal.z);
            const float referenceNormalRadial = std::hypot(
                    reference.normal.y, reference.normal.z);
            maximumRadius = std::max(maximumRadius, radius);
            if (std::abs(vertex.position.x - reference.position.x) > 2.0e-6F
                    || std::abs(radius - referenceRadius) > 2.0e-6F
                    || std::abs(vertex.normal.x - reference.normal.x) > 2.0e-6F
                    || std::abs(normalRadial - referenceNormalRadial) > 2.0e-6F
                    || std::abs(glm::length(vertex.normal) - 1.0F) > 2.0e-5F
                    || std::abs(vertex.position.x)
                            > kMintMotionBandTreadHalfSpan
                                    + kMintMotionBandSurfaceOffset + 1.0e-5F) {
                return false;
            }
        }
    }

    const float maximumSagitta = maximumRadius * (
            1.0F - std::cos(
                    glm::pi<float>()
                            / static_cast<float>(kMintMotionBandRadialSegments)));
    return maximumSagitta <= kMintMotionBandMaximumRadialSagitta;
}

std::string safeMaterialName(std::string value) {
    for (char& character : value) {
        if (!(std::isalnum(static_cast<unsigned char>(character)) || character == '_')) {
            character = '_';
        }
    }
    return value;
}

}  // namespace

WheelModel makeMintWheel(const int glowingGrooveCount) {
    constexpr int repeatCount = kMintChevronCount;
    if (glowingGrooveCount < 1 || glowingGrooveCount > repeatCount) {
        throw std::invalid_argument(
                "mint glowing groove count must be between 1 and 18");
    }

    WheelModel model;
    model.name = "Mint ring monowheel ("
            + std::to_string(glowingGrooveCount)
            + (glowingGrooveCount == 1
                    ? " glowing groove)"
                    : " glowing grooves)");
    model.slug = "mint-wheel";

    const Material rubber = material(
            "mint_rubber", {0.125F, 0.132F, 0.138F, 1.0F}, 0.32F, 0.70F, 0.10F, 14.0F);
    const Material tread = material(
            "mint_tread", {0.025F, 0.030F, 0.032F, 1.0F}, 0.36F, 0.40F, 0.03F, 10.0F);
    const Material hub = material(
            "mint_hub", {0.115F, 0.125F, 0.130F, 1.0F}, 0.34F, 0.58F, 0.22F, 34.0F);
    const Material grooveEmission = material(
            "mint_groove_emissive",
            {0.20F, 0.94F, 0.67F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F, true);
    const Material motionBandEmission = material(
            kMintMotionBandMaterialName,
            {0.20F, 0.94F, 0.67F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F, true);
    const Material sideEmission = material(
            "mint_side_emissive",
            {0.20F, 0.94F, 0.67F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F, true);

    // Axle is +X, matching TireTerrainContactSolver and the post-import gameplay model.
    CpuMesh carcass = makeTorus(0.0F, 0.365F, 0.128F, 0.126F, 48, 14);
    model.parts.push_back(part("carcass", std::move(carcass), rubber));

    CpuMesh grooves;
    CpuMesh glowingGroove;
    std::vector<bool> glowingRepeats(
            static_cast<std::size_t>(repeatCount), false);
    const int markerOffset = repeatCount / 4;
    for (int marker = 0; marker < glowingGrooveCount; ++marker) {
        const int repeat = (markerOffset
                + marker * repeatCount / glowingGrooveCount)
                % repeatCount;
        glowingRepeats[static_cast<std::size_t>(repeat)] = true;
    }
    for (int repeat = 0; repeat < repeatCount; ++repeat) {
        const float theta = glm::two_pi<float>() * static_cast<float>(repeat)
                / static_cast<float>(repeatCount);
        CpuMesh chevron = makeChevronStrip(theta, 0.096F, 0.125F, 0.0145F, 0.014F);
        if (glowingRepeats[static_cast<std::size_t>(repeat)]) {
            appendMesh(glowingGroove, chevron);
        } else {
            appendMesh(grooves, chevron);
        }
    }
    if (!grooves.vertices.empty()) {
        model.parts.push_back(part(
                "dark chevron grooves", std::move(grooves), tread));
    }
    model.parts.push_back(part(
            "glowing chevron grooves", std::move(glowingGroove), grooveEmission));

    // Numerical surface integration of one authored chevron top over the
    // corresponding 20-degree crown strip gives the canonical 0.26164 duty
    // cycle exposed in wheel_models.hpp. Keep this material at full neon: the
    // temporal renderer applies dutyCycle*bandBlend to premultiplied RGB and
    // alpha so sparse-groove energy is neither lost nor multiplied.
    model.parts.push_back(part(
            "phase-independent tread motion band",
            makeMintMotionBand(),
            motionBandEmission));

    CpuMesh hubMesh;
    appendMesh(hubMesh, makeTorus(0.0F, 0.265F, 0.035F, 0.105F, 48, 8));
    appendMesh(hubMesh, makeAnnulus(-0.127F, 0.238F, 0.338F, 48, -1.0F));
    appendMesh(hubMesh, makeAnnulus(0.127F, 0.238F, 0.338F, 48, 1.0F));
    model.parts.push_back(part("recessed hub", std::move(hubMesh), hub));

    CpuMesh glowRings;
    // These profiles follow the sidewall crown and extend only outward. A round
    // tube centered outside the tire lets its inner half poke through the tread
    // in front view, which reads as stray green hooks.
    const std::vector<ProfilePoint> rightGlowProfile = {
            {0.125F, 0.400F}, {0.135F, 0.400F}, {0.141F, 0.389F},
            {0.140F, 0.378F}, {0.130F, 0.378F}, {0.128F, 0.389F}};
    const std::vector<ProfilePoint> leftGlowProfile = {
            {-0.125F, 0.400F}, {-0.128F, 0.389F}, {-0.130F, 0.378F},
            {-0.140F, 0.378F}, {-0.141F, 0.389F}, {-0.135F, 0.400F}};
    appendMesh(glowRings, sweepClosedProfile(leftGlowProfile, 48));
    appendMesh(glowRings, sweepClosedProfile(rightGlowProfile, 48));
    model.parts.push_back(part(
            "mint side rings", std::move(glowRings), sideEmission));

    const ValidationReport report = validateModel(model);
    if (!report.valid) {
        throw std::runtime_error("generated invalid mint wheel: " + report.summary);
    }
    return model;
}

WheelModel makeVioletWheel() {
    WheelModel model;
    model.name = "Violet segmented energy wheel";
    model.slug = "violet-wheel";

    const Material armor = material(
            "violet_armor", {0.105F, 0.095F, 0.125F, 1.0F}, 0.32F, 0.70F, 0.07F, 10.0F);
    const Material hub = material(
            "violet_hub", {0.125F, 0.115F, 0.145F, 1.0F}, 0.34F, 0.62F, 0.16F, 24.0F);
    const Material core = material(
            "violet_core", {0.035F, 0.028F, 0.045F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F);
    const Material energy = material(
            "violet_energy", {0.50F, 0.22F, 0.82F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F, true);
    const Material primaryGlow = material(
            "violet_glow_primary",
            {0.50F, 0.22F, 0.82F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F, true);
    const Material secondaryGlow = material(
            "violet_glow_secondary",
            {0.50F, 0.22F, 0.82F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F, true);
    const Material detailGlow = material(
            "violet_glow_detail",
            {0.50F, 0.22F, 0.82F, 1.0F}, 1.0F, 0.0F, 0.0F, 1.0F, true);

    constexpr float axialScale = 0.88F;
    constexpr int podCount = 16;
    const float pitch = glm::two_pi<float>() / static_cast<float>(podCount);
    const float halfAngle = 0.35F * pitch;
    const std::vector<ProfilePoint> energyProfile = {
            {-0.120F * axialScale, 0.330F}, {-0.138F * axialScale, 0.365F},
            {-0.120F * axialScale, 0.455F}, {0.120F * axialScale, 0.455F},
            {0.138F * axialScale, 0.365F}, {0.120F * axialScale, 0.330F}};
    model.parts.push_back(part(
            "dark under-core", sweepClosedProfile(energyProfile, 48), core));

    std::vector<ProfilePoint> glowProfile = energyProfile;
    for (auto& point : glowProfile) {
        point.radius += 0.0015F;
    }
    const float glowHalfAngle = 0.145F * pitch;
    CpuMesh primaryGlowMesh;
    CpuMesh secondaryGlowMesh;
    CpuMesh detailGlowMesh;
    for (int gap = 0; gap < podCount; ++gap) {
        const float center = (static_cast<float>(gap) + 0.5F) * pitch;
        const CpuMesh glowSegment = makeSweepSegment(
                glowProfile,
                center - glowHalfAngle,
                center + glowHalfAngle,
                2);
        if (gap % 4 == 0) {
            appendMesh(primaryGlowMesh, glowSegment);
        } else if (gap % 2 == 0) {
            appendMesh(secondaryGlowMesh, glowSegment);
        } else {
            appendMesh(detailGlowMesh, glowSegment);
        }
    }
    model.parts.push_back(part(
            "four primary glow grooves",
            std::move(primaryGlowMesh),
            primaryGlow));
    model.parts.push_back(part(
            "four secondary glow grooves",
            std::move(secondaryGlowMesh),
            secondaryGlow));
    model.parts.push_back(part(
            "eight detail glow grooves",
            std::move(detailGlowMesh),
            detailGlow));

    const std::vector<ProfilePoint> podProfile = {
            {-0.135F * axialScale, 0.393F}, {-0.150F * axialScale, 0.414F},
            {-0.150F * axialScale, 0.452F}, {-0.122F * axialScale, 0.474F},
            {-0.055F * axialScale, 0.486F}, {0.055F * axialScale, 0.486F},
            {0.122F * axialScale, 0.474F}, {0.150F * axialScale, 0.452F},
            {0.150F * axialScale, 0.414F}, {0.135F * axialScale, 0.393F}};
    CpuMesh pods;
    for (int podIndex = 0; podIndex < podCount; ++podIndex) {
        const float centerTheta = pitch * static_cast<float>(podIndex);
        appendMesh(pods, makeSweepSegment(
                podProfile, centerTheta - halfAngle, centerTheta + halfAngle, 3));
    }
    model.parts.push_back(part("sixteen radial armor pods", std::move(pods), armor));

    const std::vector<ProfilePoint> hubProfile = {
            {-0.100F * axialScale, 0.090F}, {-0.135F * axialScale, 0.090F},
            {-0.140F * axialScale, 0.130F}, {-0.120F * axialScale, 0.160F},
            {-0.140F * axialScale, 0.200F}, {-0.140F * axialScale, 0.285F},
            {-0.125F * axialScale, 0.335F}, {-0.100F * axialScale, 0.375F},
            {0.100F * axialScale, 0.375F}, {0.125F * axialScale, 0.335F},
            {0.140F * axialScale, 0.285F}, {0.140F * axialScale, 0.200F},
            {0.120F * axialScale, 0.160F}, {0.140F * axialScale, 0.130F},
            {0.135F * axialScale, 0.090F}, {0.100F * axialScale, 0.090F}};
    CpuMesh hubMesh = sweepClosedProfile(hubProfile, 48);
    // Opaque sidewall plates hide the under-core from axial views. Separate thin
    // rings keep the violet identity visible on both sides.
    appendMesh(hubMesh, makeAnnulus(-0.132F, 0.335F, 0.472F, 48, -1.0F));
    appendMesh(hubMesh, makeAnnulus(0.132F, 0.335F, 0.472F, 48, 1.0F));
    model.parts.push_back(part("stepped hub and sidewalls", std::move(hubMesh), hub));

    CpuMesh glowRings;
    appendMesh(glowRings, makeTorus(-0.133F, 0.405F, 0.007F, 0.003F, 48, 6));
    appendMesh(glowRings, makeTorus(0.133F, 0.405F, 0.007F, 0.003F, 48, 6));
    model.parts.push_back(part("violet side rings", std::move(glowRings), energy));

    const ValidationReport report = validateModel(model);
    if (!report.valid) {
        throw std::runtime_error("generated invalid violet wheel: " + report.summary);
    }
    return model;
}

ValidationReport validateModel(WheelModel& model) {
    ValidationReport report;
    glm::vec3 minimum(std::numeric_limits<float>::max());
    glm::vec3 maximum(std::numeric_limits<float>::lowest());
    bool finite = true;
    bool indicesValid = true;

    for (const auto& meshPart : model.parts) {
        if (meshPart.material.name == kMintMotionBandMaterialName) {
            ++report.mintMotionBandParts;
            report.mintMotionBandValid = report.mintMotionBandValid
                    && validateMintMotionBandPart(meshPart);
        }
        const CpuMesh& mesh = meshPart.smoothMesh;
        report.vertices += mesh.vertices.size();
        report.triangles += mesh.indices.size() / 3U;
        for (const auto& vertex : mesh.vertices) {
            for (int component = 0; component < 3; ++component) {
                finite = finite && std::isfinite(vertex.position[component])
                        && std::isfinite(vertex.normal[component]);
            }
            minimum = glm::min(minimum, vertex.position);
            maximum = glm::max(maximum, vertex.position);
        }
        for (const auto index : mesh.indices) {
            indicesValid = indicesValid && index < mesh.vertices.size();
        }
        if (!indicesValid) {
            continue;
        }
        for (std::size_t i = 0; i + 2 < mesh.indices.size(); i += 3) {
            const glm::vec3 a = mesh.vertices[mesh.indices[i]].position;
            const glm::vec3 b = mesh.vertices[mesh.indices[i + 1]].position;
            const glm::vec3 c = mesh.vertices[mesh.indices[i + 2]].position;
            if (glm::length(glm::cross(b - a, c - a)) <= kEpsilon) {
                ++report.degenerateTriangles;
            }
        }
    }

    report.boundsMin = minimum;
    report.boundsMax = maximum;
    model.boundsMin = minimum;
    model.boundsMax = maximum;
    const glm::vec3 size = maximum - minimum;
    const float diameter = std::max(size.y, size.z);
    report.widthToDiameter = diameter > kEpsilon ? size.x / diameter : 0.0F;
    if (model.slug == "mint-wheel") {
        report.mintMotionBandValid = report.mintMotionBandValid
                && report.mintMotionBandParts == 1U;
    }
    report.valid = finite && indicesValid && report.vertices > 0 && report.triangles > 0
            && report.degenerateTriangles == 0 && report.widthToDiameter > 0.15F
            && report.widthToDiameter < 0.36F
            && std::abs(size.y - size.z) / std::max(size.y, size.z) < 0.03F
            && report.mintMotionBandValid;

    std::ostringstream summary;
    summary << model.name << ": " << report.vertices << " authored vertices, "
            << report.triangles << " triangles, bounds "
            << std::fixed << std::setprecision(3)
            << size.x << " x " << size.y << " x " << size.z
            << ", width/diameter=" << report.widthToDiameter
            << ", degenerate=" << report.degenerateTriangles;
    if (!finite) {
        summary << ", non-finite data";
    }
    if (!indicesValid) {
        summary << ", invalid indices";
    }
    if (!report.mintMotionBandValid) {
        summary << ", invalid mint motion band (parts="
                << report.mintMotionBandParts << ')';
    }
    report.summary = summary.str();
    return report;
}

void exportObj(const WheelModel& model, const std::filesystem::path& objPath) {
    if (objPath.empty()) {
        throw std::invalid_argument("OBJ export path is empty");
    }
    std::filesystem::create_directories(objPath.parent_path());
    const std::filesystem::path mtlPath = objPath.parent_path() / (objPath.stem().string() + ".mtl");

    std::ofstream obj(objPath);
    std::ofstream mtl(mtlPath);
    if (!obj || !mtl) {
        throw std::runtime_error("could not open OBJ/MTL export destination");
    }
    obj << "# Procedural wheel exported by wheel-mesh-lab\n";
    obj << "# Canonical coordinates: +X axle, +Y up, wheel plane YZ\n";
    obj << "mtllib " << mtlPath.filename().string() << "\n";

    std::uint32_t vertexBase = 1U;
    for (std::size_t partIndex = 0; partIndex < model.parts.size(); ++partIndex) {
        const auto& meshPart = model.parts[partIndex];
        const CpuMesh& mesh = meshPart.smoothMesh;
        const std::string materialName = safeMaterialName(meshPart.material.name);
        obj << "\no " << model.slug << "_" << safeMaterialName(meshPart.name) << "\n";
        obj << "usemtl " << materialName << "\n";
        for (const auto& vertex : mesh.vertices) {
            obj << "v " << vertex.position.x << ' ' << vertex.position.y << ' '
                    << vertex.position.z << "\n";
        }
        for (const auto& vertex : mesh.vertices) {
            obj << "vn " << vertex.normal.x << ' ' << vertex.normal.y << ' '
                    << vertex.normal.z << "\n";
        }
        for (std::size_t i = 0; i + 2 < mesh.indices.size(); i += 3) {
            obj << "f";
            for (int corner = 0; corner < 3; ++corner) {
                const std::uint32_t index = vertexBase + mesh.indices[i + static_cast<std::size_t>(corner)];
                obj << ' ' << index << "//" << index;
            }
            obj << "\n";
        }
        vertexBase += static_cast<std::uint32_t>(mesh.vertices.size());

        const Material& value = meshPart.material;
        if (partIndex > 0) {
            mtl << '\n';
        }
        mtl << "newmtl " << materialName << "\n";
        mtl << "Ka " << value.color.r * value.ambient << ' '
                << value.color.g * value.ambient << ' '
                << value.color.b * value.ambient << "\n";
        mtl << "Kd " << value.color.r << ' ' << value.color.g << ' ' << value.color.b << "\n";
        mtl << "Ks " << value.specular << ' ' << value.specular << ' ' << value.specular << "\n";
        mtl << "Ns " << value.shininess << "\n";
        if (value.luminous) {
            mtl << "Ke " << value.color.r << ' ' << value.color.g << ' ' << value.color.b << "\n";
        }
        mtl << "d " << value.color.a << '\n';
    }
}

}  // namespace wheel_lab
