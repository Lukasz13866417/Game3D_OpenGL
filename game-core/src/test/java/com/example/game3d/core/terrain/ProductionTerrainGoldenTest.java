package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.Portal;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Exact production characterization captured before replacing the terrain producer. */
public final class ProductionTerrainGoldenTest {
    private static final String FIXTURE =
            "/com/example/game3d/core/terrain/production-terrain-manifest-v1.txt";

    @Test
    public void productionRecipesMatchCheckedInSemanticManifest() throws Exception {
        InputStream stream = ProductionTerrainGoldenTest.class.getResourceAsStream(FIXTURE);
        assertNotNull("Missing fixture " + FIXTURE, stream);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        int checked = 0;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                Map<String, String> expected = fields(trimmed);
                TerrainSnapshot snapshot = generate(expected.get("case"));
                assertManifest(expected, snapshot);
                checked++;
            }
        } finally {
            reader.close();
        }
        assertEquals(9, checked);
    }

    private static TerrainSnapshot generate(String caseName) {
        StreamingTerrainGenerator generator = new StreamingTerrainGenerator(
                3.2, 1.4, new Vec3(0.0, -3.5, -0.5));
        if ("intro".equals(caseName)) {
            generator.enqueueIntroSegments();
        } else if (caseName != null && caseName.startsWith("level-")) {
            generator.enqueueGameplayLevel(Integer.parseInt(caseName.substring(6)));
        } else {
            throw new IllegalArgumentException("Unknown manifest case " + caseName);
        }
        generator.generateChunks(-1);
        TerrainSnapshot result = generator.snapshot();
        generator.close();
        return result;
    }

    private static void assertManifest(
            Map<String, String> expected, TerrainSnapshot snapshot) {
        int solid = 0;
        int connected = 0;
        int normal = 0;
        int ramp = 0;
        int launch = 0;
        int spikes = 0;
        int potions = 0;
        int portals = 0;
        StringBuilder portalManifest = new StringBuilder();
        for (TerrainSegment segment : snapshot.segments) {
            if (segment.solid) solid++;
            if (segment.connectedToPrevious) connected++;
            if (segment.surface.kind == SurfaceProperties.Kind.NORMAL) normal++;
            if (segment.surface.kind == SurfaceProperties.Kind.BOOST_RAMP) ramp++;
            if (segment.surface.kind == SurfaceProperties.Kind.BOOST_RAMP_LAUNCH) launch++;
            for (Addon addon : segment.addons) {
                if (addon.kind == Addon.Kind.DEATH_SPIKE) {
                    spikes++;
                } else if (addon.kind == Addon.Kind.AIR_JUMP_POTION) {
                    potions++;
                } else if (addon.kind == Addon.Kind.PORTAL) {
                    portals++;
                    Portal portal = (Portal) addon;
                    if (portalManifest.length() > 0) portalManifest.append(',');
                    portalManifest.append(portal.id()).append('@')
                            .append(portal.ownerSegmentId()).append(':')
                            .append(portal.pairId).append(':').append(portal.role);
                }
            }
        }

        String label = expected.get("case");
        assertInt(label, expected, "segments", snapshot.segments.size());
        assertInt(label, expected, "solid", solid);
        assertInt(label, expected, "connected", connected);
        assertInt(label, expected, "normal", normal);
        assertInt(label, expected, "ramp", ramp);
        assertInt(label, expected, "launch", launch);
        assertInt(label, expected, "spike", spikes);
        assertInt(label, expected, "potion", potions);
        assertInt(label, expected, "portal", portals);
        assertEquals(label + " high-watermark", expected.get("high"),
                Long.toString(snapshot.addonIdHighWatermark));
        assertEquals(label + " snapshot digest", expected.get("snapshot"),
                Long.toUnsignedString(snapshot.deterministicDigest));
        assertEquals(label + " geometry digest", expected.get("geometry"),
                Long.toUnsignedString(geometryDigest(snapshot)));
        assertEquals(label + " addon digest", expected.get("addons"),
                Long.toUnsignedString(addonDigest(snapshot)));
        assertVec(label + " first near-left", expected.get("firstNearLeft"),
                snapshot.segments.get(0).nearLeft);
        assertVec(label + " last far-right", expected.get("lastFarRight"),
                snapshot.segments.get(snapshot.segments.size() - 1).farRight);
        assertEquals(label + " portal ordering",
                "-".equals(expected.get("portals")) ? "" : expected.get("portals"),
                portalManifest.toString());
    }

    private static long geometryDigest(TerrainSnapshot snapshot) {
        long hash = 0xcbf29ce484222325L;
        for (TerrainSegment segment : snapshot.segments) {
            hash = mix(hash, segment.id);
            hash = mixVec(hash, segment.nearLeft);
            hash = mixVec(hash, segment.nearRight);
            hash = mixVec(hash, segment.farLeft);
            hash = mixVec(hash, segment.farRight);
            hash = mix(hash, segment.solid ? 1L : 0L);
            hash = mix(hash, segment.connectedToPrevious ? 1L : 0L);
            hash = mix(hash, segment.surface.deterministicFingerprint());
            hash = mixAppearance(hash, segment.nearLeftAppearance);
            hash = mixAppearance(hash, segment.nearRightAppearance);
            hash = mixAppearance(hash, segment.farLeftAppearance);
            hash = mixAppearance(hash, segment.farRightAppearance);
        }
        return hash;
    }

    private static long addonDigest(TerrainSnapshot snapshot) {
        long hash = 0xcbf29ce484222325L;
        for (TerrainSegment segment : snapshot.segments) {
            for (Addon addon : segment.addons) {
                hash = mix(hash, addon.deterministicDigest());
            }
        }
        return hash;
    }

    private static Map<String, String> fields(String line) {
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        for (String field : line.split("\\s+")) {
            int separator = field.indexOf('=');
            if (separator <= 0 || separator == field.length() - 1) {
                throw new IllegalStateException("Invalid manifest field " + field);
            }
            result.put(field.substring(0, separator), field.substring(separator + 1));
        }
        return result;
    }

    private static void assertInt(
            String label, Map<String, String> expected, String field, int actual) {
        assertEquals(label + " " + field, Integer.parseInt(expected.get(field)), actual);
    }

    private static void assertVec(String label, String encoded, Vec3 actual) {
        if (encoded == null || !encoded.startsWith("(") || !encoded.endsWith(")")) {
            throw new IllegalStateException("Invalid vector in manifest: " + encoded);
        }
        String[] values = encoded.substring(1, encoded.length() - 1).split(",");
        if (values.length != 3) {
            throw new IllegalStateException("Invalid vector in manifest: " + encoded);
        }
        assertEquals(label + " x", Double.parseDouble(values[0]), actual.x, 1.0e-6);
        assertEquals(label + " y", Double.parseDouble(values[1]), actual.y, 1.0e-6);
        assertEquals(label + " z", Double.parseDouble(values[2]), actual.z, 1.0e-6);
    }

    private static long mixAppearance(long hash, TerrainVertexAppearance value) {
        hash = mix(hash, Float.floatToIntBits(value.alpha));
        return mix(hash, Float.floatToIntBits(value.brightness));
    }

    private static long mixVec(long hash, Vec3 value) {
        hash = mix(hash, Double.doubleToLongBits(value.x));
        hash = mix(hash, Double.doubleToLongBits(value.y));
        return mix(hash, Double.doubleToLongBits(value.z));
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}
