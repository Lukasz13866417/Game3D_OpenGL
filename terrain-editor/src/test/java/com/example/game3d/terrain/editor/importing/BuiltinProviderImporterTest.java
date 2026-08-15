package com.example.game3d.terrain.editor.importing;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d.core.terrain.addon.Potion;
import com.example.game3d.terrain.editor.compile.AuthoringDocumentCompiler;
import com.example.game3d.terrain.editor.compile.CompileResult;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinProviderImporterTest {
    @Test void everyBuiltInBecomesValidExplicitEditableGeometry() throws Exception {
        BuiltinProviderImporter importer = new BuiltinProviderImporter();
        AuthoringDocumentCompiler compiler = new AuthoringDocumentCompiler(emptyRepository());
        TerrainValidator validator = new TerrainValidator();
        TerrainJsonCodec codec = new TerrainJsonCodec();

        for (String provider : importer.providerIds()) {
            BuiltinProviderImporter.ImportedProvider imported =
                    importer.materialize(provider, 7L);
            StructureDocument document = imported.structureWithId("test." + provider);
            assertTrue(validator.validate(document).isValid(), provider);
            StructureDocument decoded = codec.decodeStructure(codec.encode(document));
            CompileResult recompiled = compiler.compile(1L, decoded);
            assertTrue(recompiled.successful(), provider + ": " + recompiled.problems());
            assertEquivalent(imported.originalSnapshot().segments,
                    recompiled.snapshot().segments, provider);
            for (int i = 0; i < imported.originalSnapshot().segments.size(); i++) {
                assertEquals(imported.originalSnapshot().segments.get(i).deterministicDigest(),
                        recompiled.snapshot().segments.get(i).deterministicDigest(),
                        provider + " segment digest " + i);
            }
            assertEquals(imported.originalSnapshot().deterministicDigest,
                    recompiled.snapshot().deterministicDigest,
                    provider + " deterministic snapshot digest");
            assertEquals(imported.originalSnapshot().committedThroughSegmentId,
                    recompiled.snapshot().committedThroughSegmentId);
            assertEquals(imported.originalSnapshot().retireBeforeSegmentId,
                    recompiled.snapshot().retireBeforeSegmentId);
            assertEquals(imported.originalSnapshot().addonIdHighWatermark,
                    recompiled.snapshot().addonIdHighWatermark);
            long expectedAddonHighWatermark = imported.originalSnapshot().segments.stream()
                    .flatMap(segment -> segment.addons.stream())
                    .mapToLong(Addon::id).max().orElse(-1L);
            assertEquals(expectedAddonHighWatermark,
                    imported.originalSnapshot().addonIdHighWatermark);
            assertEquals(imported.originalSnapshot().segments
                            .get(imported.originalSnapshot().segments.size() - 1).id,
                    imported.originalSnapshot().committedThroughSegmentId);
        }
    }

    @Test void sourceIdsAndPortalPairingAreDeterministicAndInlineLevelIsEditableJson()
            throws Exception {
        BuiltinProviderImporter importer = new BuiltinProviderImporter();
        TerrainJsonCodec codec = new TerrainJsonCodec();
        BuiltinProviderImporter.ImportedProvider first =
                importer.materialize("stairs_curve_line", 0L);
        BuiltinProviderImporter.ImportedProvider second =
                importer.materialize("stairs_curve_line", 0L);
        assertEquals(codec.encode(first.structure()), codec.encode(second.structure()));

        BuiltinProviderImporter.ImportedProvider portalImport = null;
        for (long ordinal = 4L; ordinal < 100L && portalImport == null; ordinal++) {
            BuiltinProviderImporter.ImportedProvider candidate = importer.materialize(
                    "stairs_curve_line", ordinal);
            if (candidate.structure().addons().stream().anyMatch(addon ->
                    addon.kind().name().startsWith("PORTAL_"))) {
                portalImport = candidate;
            }
        }
        assertTrue(portalImport != null, "expected a deterministic portal variant");
        StructureDocument withPortal = portalImport.structure();
        for (AddonReservation addon : withPortal.addons()) {
            if (!addon.kind().name().startsWith("PORTAL_")) continue;
            AddonReservation counterpart = withPortal.addons().stream()
                    .filter(value -> value.sourceId().equals(addon.pairSourceId()))
                    .findFirst().orElseThrow();
            assertEquals(addon.sourceId(), counterpart.pairSourceId());
        }
        CompileResult portalCompile = new AuthoringDocumentCompiler(emptyRepository())
                .compile(1L, codec.decodeStructure(codec.encode(withPortal)));
        assertTrue(portalCompile.successful(), portalCompile.problems().toString());
        assertEquals(portalImport.originalSnapshot().deterministicDigest,
                portalCompile.snapshot().deterministicDigest,
                "portal transform/style/pair payload must replay exactly");
        assertEquivalent(portalImport.originalSnapshot().segments,
                portalCompile.snapshot().segments, "portal variant");

        LevelDocument level = importer.importInlineLevel(
                "stairs_curve_line", 0L, "imported.level");
        assertEquals(1, level.entries().size());
        assertEquals(LevelEntry.Kind.INLINE_STRUCTURE, level.entries().get(0).kind());
        assertTrue(codec.encode(level).contains("\"inlineStructure\""));
        assertTrue(new TerrainValidator().validate(level).isValid());
    }

    @Test void asymmetricEdgeAlphaIsRejectedInsteadOfSilentlyAveraged() {
        GameplayLevelProvider provider = new GameplayLevelProvider() {
            @Override public String stableId() { return "asymmetric"; }

            @Override public BaseTerrainStructure<?> create(long levelOrdinal) {
                return new AdvancedTerrainStructure(1) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        brush.setCornerAlphas(.4f, .8f);
                        brush.addSegment();
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                    }
                };
            }
        };
        BuiltinProviderImporter importer = new BuiltinProviderImporter(
                new GameplayLevelCatalog(java.util.List.of(provider)),
                TrackProfile.gameplayDefault());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> importer.materialize("asymmetric", 0L));
        assertTrue(error.getMessage().contains("asymmetric edge alpha"));
    }

    @Test void editingReadableAnglesDropsOnlyTheOverriddenResolvedCommand() {
        StructureDocument imported = new BuiltinProviderImporter()
                .materialize("stairs_curve_line", 7L).structure();
        com.example.game3d.terrain.io.model.TileRecord source = imported.tiles().stream()
                .filter(tile -> tile.resolvedTurnDeltaRadians() != null
                        && tile.resolvedAbsoluteSlopeRadians() != null)
                .findFirst().orElseThrow();
        com.example.game3d.terrain.io.model.TileRecord edited = source.withValues(
                source.turnDeltaDegrees() + 1.0, source.absoluteSlopeDegrees(),
                source.liftBefore(), source.alpha(), source.brightness());
        assertEquals(null, edited.resolvedTurnDeltaRadians());
        assertEquals(source.resolvedAbsoluteSlopeRadians(),
                edited.resolvedAbsoluteSlopeRadians());
    }

    private static void assertEquivalent(
            java.util.List<TerrainSegment> expected,
            java.util.List<TerrainSegment> actual,
            String provider) {
        assertEquals(expected.size(), actual.size(), provider);
        for (int i = 0; i < expected.size(); i++) {
            TerrainSegment left = expected.get(i);
            TerrainSegment right = actual.get(i);
            assertEquals(left.solid, right.solid, provider + " tile " + i);
            assertEquals(left.connectedToPrevious, right.connectedToPrevious,
                    provider + " tile " + i);
            assertEquals(left.surface, right.surface, provider + " tile " + i);
            assertAppearance(left.nearLeftAppearance, right.nearLeftAppearance,
                    provider + " tile " + i + " nearLeft appearance");
            assertAppearance(left.nearRightAppearance, right.nearRightAppearance,
                    provider + " tile " + i + " nearRight appearance");
            assertAppearance(left.farLeftAppearance, right.farLeftAppearance,
                    provider + " tile " + i + " farLeft appearance");
            assertAppearance(left.farRightAppearance, right.farRightAppearance,
                    provider + " tile " + i + " farRight appearance");
            assertNear(left.nearLeft, right.nearLeft, provider, i);
            assertNear(left.nearRight, right.nearRight, provider, i);
            assertNear(left.farLeft, right.farLeft, provider, i);
            assertNear(left.farRight, right.farRight, provider, i);
            assertEquals(left.addons.size(), right.addons.size(),
                    provider + " addons at tile " + i);
            for (int a = 0; a < left.addons.size(); a++) {
                Addon expectedAddon = left.addons.get(a);
                Addon actualAddon = right.addons.get(a);
                assertEquals(expectedAddon.kind, actualAddon.kind);
                assertNear(expectedAddon.footprint().nearLeft,
                        actualAddon.footprint().nearLeft, provider, i);
                assertNear(expectedAddon.footprint().farRight,
                        actualAddon.footprint().farRight, provider, i);
                assertVecBits(expectedAddon.footprint().nearLeft,
                        actualAddon.footprint().nearLeft,
                        provider + " addon nearLeft tile " + i);
                assertVecBits(expectedAddon.footprint().nearRight,
                        actualAddon.footprint().nearRight,
                        provider + " addon nearRight tile " + i);
                assertVecBits(expectedAddon.footprint().farLeft,
                        actualAddon.footprint().farLeft,
                        provider + " addon farLeft tile " + i);
                assertVecBits(expectedAddon.footprint().farRight,
                        actualAddon.footprint().farRight,
                        provider + " addon farRight tile " + i);
                assertEquals(expectedAddon.deterministicDigest(),
                        actualAddon.deterministicDigest(),
                        provider + " addon digest tile " + i);
                assertAddonPayload(expectedAddon, actualAddon,
                        provider + " addon payload tile " + i);
            }
        }
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String provider, int tile) {
        assertTrue(expected.subtract(actual).length() < 1.0e-9,
                provider + " tile " + tile + ": " + expected + " != " + actual);
    }

    private static void assertVecBits(Vec3 expected, Vec3 actual, String message) {
        assertEquals(Double.doubleToLongBits(expected.x),
                Double.doubleToLongBits(actual.x), message + " x");
        assertEquals(Double.doubleToLongBits(expected.y),
                Double.doubleToLongBits(actual.y), message + " y");
        assertEquals(Double.doubleToLongBits(expected.z),
                Double.doubleToLongBits(actual.z), message + " z");
    }

    private static void assertAppearance(
            com.example.game3d.core.terrain.TerrainVertexAppearance expected,
            com.example.game3d.core.terrain.TerrainVertexAppearance actual,
            String message) {
        assertEquals(Float.floatToIntBits(expected.alpha),
                Float.floatToIntBits(actual.alpha), message + " alpha");
        assertEquals(Float.floatToIntBits(expected.brightness),
                Float.floatToIntBits(actual.brightness), message + " brightness");
    }

    private static void assertAddonPayload(
            Addon expected, Addon actual, String message) {
        assertEquals(expected.id(), actual.id(), message + " id");
        assertEquals(expected.ownerSegmentId(), actual.ownerSegmentId(), message + " owner");
        assertEquals(expected.kind, actual.kind, message + " kind");
        if (expected instanceof DeathSpike left) {
            DeathSpike right = (DeathSpike) actual;
            assertVecBits(left.apex, right.apex, message + " apex");
            assertVecBits(left.outwardNormal, right.outwardNormal, message + " normal");
            assertVecBits(left.collisionBaseCenter, right.collisionBaseCenter,
                    message + " collision center");
            assertDoubleBits(left.baseOffset, right.baseOffset, message + " base offset");
            assertDoubleBits(left.collisionRadius, right.collisionRadius,
                    message + " collision radius");
            assertDoubleBits(left.collisionHeight, right.collisionHeight,
                    message + " collision height");
        } else if (expected instanceof Potion left) {
            Potion right = (Potion) actual;
            assertVecBits(left.center, right.center, message + " center");
            assertDoubleBits(left.triggerRadius, right.triggerRadius,
                    message + " trigger radius");
            assertEquals(left.visualStyleId, right.visualStyleId, message + " style");
        } else {
            Portal left = (Portal) expected;
            Portal right = (Portal) actual;
            assertEquals(left.pairId, right.pairId, message + " pair");
            assertEquals(left.role, right.role, message + " role");
            assertVecBits(left.center, right.center, message + " center");
            assertVecBits(left.forward, right.forward, message + " forward");
            assertVecBits(left.up, right.up, message + " up");
            assertDoubleBits(left.width, right.width, message + " width");
            assertDoubleBits(left.height, right.height, message + " height");
            assertEquals(left.visualStyleId, right.visualStyleId, message + " style");
        }
    }

    private static void assertDoubleBits(double expected, double actual, String message) {
        assertEquals(Double.doubleToLongBits(expected),
                Double.doubleToLongBits(actual), message);
    }

    private static TerrainDocumentRepository emptyRepository() {
        return new TerrainDocumentRepository() {
            @Override public StructureDocument findStructure(String id) { return null; }
            @Override public LevelDocument findLevel(String id) { return null; }
        };
    }
}
