package com.example.game3d.terrain.io.validation;

import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TerrainContentLimitsTest {
    @Test
    public void oversizedStructureIsAValidationErrorBeforeCommandCapture() {
        List<TileRecord> tiles = new ArrayList<TileRecord>();
        for (int i = 0; i <= TerrainContentLimits.MAX_STRUCTURE_TILES; i++) {
            tiles.add(new TileRecord(
                    String.format("00000000-0000-0000-0000-%012d", i + 1L),
                    true, 0.0, 0.0, 0.0, "NORMAL", 1.0, 1.0));
        }
        ValidationResult result = new TerrainValidator().validate(
                new StructureDocument(1, "too-large", GridMode.ADVANCED,
                        tiles, Collections.emptyList()));

        assertFalse(result.isValid());
        assertTrue(result.problems().get(0).message().contains(
                Integer.toString(TerrainContentLimits.MAX_STRUCTURE_TILES)));
    }
}
