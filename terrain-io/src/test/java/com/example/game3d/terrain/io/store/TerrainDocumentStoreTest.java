package com.example.game3d.terrain.io.store;

import com.example.game3d.terrain.io.TerrainEncodingException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TerrainDocumentStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void encodingFailureCannotReplaceExistingFile() throws Exception {
        Path target = temporary.newFile("terrain.json").toPath();
        Files.write(target, "last-good".getBytes(StandardCharsets.UTF_8));
        StructureDocument invalid = new StructureDocument(1, "invalid", GridMode.ADVANCED,
                Collections.singletonList(new TileRecord(
                        "00000000-0000-0000-0000-000000000001", true,
                        0, Double.POSITIVE_INFINITY, 0, "NORMAL", 1, 1)),
                Collections.emptyList());

        try {
            new TerrainDocumentStore(new TerrainJsonCodec(), new AtomicFileStore())
                    .save(target, invalid);
            fail("Expected encoding failure");
        } catch (TerrainEncodingException expected) {
            // Expected before AtomicFileStore is entered.
        }

        assertEquals("last-good", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
    }
}
