package com.example.game3d.terrain.io;

import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TerrainJsonCodecTest {
    private final TerrainJsonCodec codec = new TerrainJsonCodec();

    @Test public void roundTripIsDeterministicAndSortsParameterKeys() throws Exception {
        java.util.LinkedHashMap<String, Double> parameters = new java.util.LinkedHashMap<>();
        parameters.put("height", 1.2);
        parameters.put("baseOffset", 0.1);
        StructureDocument source = new StructureDocument(1, "structure.test", GridMode.ADVANCED,
                Collections.singletonList(new TileRecord(
                        "00000000-0000-0000-0000-000000000001", true, 3, 7, 0,
                        "DEFAULT", 0.8, 1.1)),
                Collections.singletonList(new AddonReservation(
                        "00000000-0000-0000-0000-000000000002", AddonKind.DEATH_SPIKE,
                        Placement.normalized("00000000-0000-0000-0000-000000000001", .5, .25),
                        null, parameters)));

        String first = codec.encode(source);
        String second = codec.encode(codec.decode(first));

        assertEquals(first, second);
        org.junit.Assert.assertTrue(first.indexOf("baseOffset") < first.indexOf("height"));
    }

    @Test(expected = CodecException.class)
    public void rejectsUnknownFields() throws Exception {
        codec.decode("{\"documentType\":\"catalog\",\"formatVersion\":1,\"id\":\"x\","
                + "\"entries\":[],\"typo\":true}");
    }

    @Test public void readerAndWriterOverloadsMatchStringApi() throws Exception {
        StructureDocument source = new StructureDocument(1, "reader-writer", GridMode.BASIC,
                Collections.emptyList(), Collections.emptyList());
        StringWriter writer = new StringWriter();
        codec.encode(source, writer);
        StructureDocument decoded = (StructureDocument) codec.decode(new StringReader(writer.toString()));
        assertEquals(codec.encode(source), codec.encode(decoded));
    }

    @Test public void semanticErrorsAreSeparateFromDecoding() throws Exception {
        String json = "{\"documentType\":\"structure\",\"formatVersion\":1,\"id\":\"draft\","
                + "\"gridMode\":\"BASIC\",\"tiles\":[{"
                + "\"sourceId\":\"00000000-0000-0000-0000-000000000001\",\"solid\":true,"
                + "\"turnDeltaDegrees\":0,\"absoluteSlopeDegrees\":95,\"liftBefore\":0,"
                + "\"surfaceKind\":\"DEFAULT\",\"alpha\":2,\"brightness\":1}],\"addons\":[]}";
        StructureDocument decoded = codec.decodeStructure(json);
        assertFalse(new TerrainValidator().validate(decoded).isValid());
    }
}
